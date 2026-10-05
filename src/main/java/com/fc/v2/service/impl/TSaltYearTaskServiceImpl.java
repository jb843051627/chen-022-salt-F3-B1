package com.fc.v2.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltYearTaskMapper;
import com.fc.v2.model.auto.TSaltYearTask;
import com.fc.v2.service.ITSaltYearTaskService;

/**
 * 年检到期催办单 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltYearTaskServiceImpl implements ITSaltYearTaskService {

    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TSaltYearTaskMapper saltYearTaskMapper;

    @Override
    public TSaltYearTask selectTSaltYearTaskById(Long id) {
        return this.saltYearTaskMapper.selectById(id);
    }

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public List<TSaltYearTask> listDue(Date at) {
        if (at == null) {
            return new java.util.ArrayList<TSaltYearTask>();
        }
        // 只取尚未处理的正常条目：已派出/分不出的不再重复开口
        List<TSaltYearTask> all = this.saltYearTaskMapper.selectList(new QueryWrapper<TSaltYearTask>()
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT));
        List<TSaltYearTask> due = new java.util.ArrayList<TSaltYearTask>();
        for (TSaltYearTask r : all) {
            if (r.getDueAt() != null && ts(r.getDueAt()).compareTo(ts(at)) < 0) {
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
            // 单条失败跳过继续：提前天数缺失/为负的条目分不出，不拖垮整趟
            if (r.getAmount() == null || r.getAmount().signum() < 0) {
                continue;
            }
            r.setStatus(Integer.valueOf(STATUS_DONE));
            if (this.saltYearTaskMapper.updateById(r) > 0) {
                ok++;
            }
        }
        return ok;
    }
}
