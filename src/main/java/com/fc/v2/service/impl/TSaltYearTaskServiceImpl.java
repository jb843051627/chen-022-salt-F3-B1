package com.fc.v2.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TSaltYearTaskMapper;
import com.fc.v2.model.auto.TSaltYearTask;
import com.fc.v2.service.ITSaltYearTaskService;

/**
 * 年检到期催办单 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * 到期口径（listDue 与 runOnce 同源，只有 listDue 一处算）：
 *  条目可处理 ⟺ 正常未删 + 待派(0) + 已进入开口窗口。
 *  开口窗口 = [应开口止点(due_at) − 提前量, 应开口止点]；提前量取 amount（最可提前几日开口），
 *  空按 WIN_FROM，并夹在 [WIN_FROM, WIN_TO] 天内。过了止点还待派的不自动派，归人工处置。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltYearTaskServiceImpl implements ITSaltYearTaskService {

    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;
    private static final long DAY_MS = 24L * 3600L * 1000L;
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TSaltYearTaskMapper saltYearTaskMapper;

    @Override
    public TSaltYearTask selectTSaltYearTaskById(Long id) {
        return this.saltYearTaskMapper.selectById(id);
    }

    @Override
    public List<TSaltYearTask> listDue(Date at) {
        List<TSaltYearTask> due = new java.util.ArrayList<TSaltYearTask>();
        if (at == null) {
            return due;
        }
        // 只捞正常待派的，窗口在内存里按墙钟判（JDBC 时区与本机不对称，日期参数直接下库会整体偏移）
        List<TSaltYearTask> all = this.saltYearTaskMapper.selectList(new QueryWrapper<TSaltYearTask>()
                .eq("del_flag", 0).eq("status", STATUS_WAIT).isNotNull("due_at")
                .orderByAsc("due_at"));
        for (TSaltYearTask r : all) {
            if (inWindow(r, at)) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        List<TSaltYearTask> due = listDue(at);
        if (due.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TSaltYearTask r : due) {
            try {
                // 派出：待派→已派出；乐观守卫，并发跑批不重复派
                UpdateWrapper<TSaltYearTask> uw = new UpdateWrapper<TSaltYearTask>()
                        .eq("id", r.getId()).eq("status", STATUS_WAIT);
                uw.set("status", STATUS_DONE);
                uw.set("update_time", new Date());
                if (this.saltYearTaskMapper.update(null, uw) > 0) {
                    ok++;
                }
            } catch (Exception e) {
                // 单条失败跳过继续，不拖垮整批
            }
        }
        return ok;
    }

    /** 开口窗口：[止点 − 提前量, 止点]；提前量取 amount 并夹在 [WIN_FROM, WIN_TO] 天 */
    private boolean inWindow(TSaltYearTask r, Date at) {
        Date dueAt = r.getDueAt();
        if (dueAt == null || at.after(dueAt)) {
            return false;
        }
        int early = WIN_FROM;
        if (r.getAmount() != null) {
            early = r.getAmount().intValue();
        }
        early = Math.max(WIN_FROM, Math.min(WIN_TO, early));
        return at.getTime() >= dueAt.getTime() - early * DAY_MS;
    }
}
