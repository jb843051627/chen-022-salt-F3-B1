package com.fc.v2.service.impl;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TSaltLinkPageMapper;
import com.fc.v2.mapper.auto.TSaltEntMapper;
import com.fc.v2.model.auto.TSaltLinkPage;
import com.fc.v2.model.auto.TSaltEnt;
import com.fc.v2.service.ITSaltLinkPageService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 品种挂接页Service业务层处理
 *
 * 口径（与 doc/schema/salt-spec.md 同源）：
 *  列表：尊重调用方条件，只统一补 del_flag=0，分页由调用方统一处理；
 *  进展：0待挂 1已挂讫 2压页（冻住），一次只挪一态，压页后改不动/删不掉/挪不了；
 *  推进落定生成核验串写入 content，退回不清核验串；
 *  挂接的企业代号以总名录为准（按 site_id 回查在册企业取 site_no）。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TSaltLinkPageServiceImpl extends ServiceImpl<TSaltLinkPageMapper, TSaltLinkPage> implements ITSaltLinkPageService {

    /** 挂接进展：0待挂 1已挂讫 2压页（冻住） */
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FROZEN = 2;

    @Autowired
    private TSaltEntMapper saltEntMapper;

    @Override
    public TSaltLinkPage selectTSaltLinkPageById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TSaltLinkPage>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TSaltLinkPage> selectTSaltLinkPageList(Wrapper<TSaltLinkPage> queryWrapper) {
        // 列表口径与调用方同源：尊重递来的条件，只统一补正常标记；分页由调用方统一处理
        QueryWrapper<TSaltLinkPage> wrapper = queryWrapper instanceof QueryWrapper
                ? (QueryWrapper<TSaltLinkPage>) queryWrapper : new QueryWrapper<TSaltLinkPage>();
        wrapper.eq("del_flag", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTSaltLinkPage(TSaltLinkPage record) {
        if (record == null) {
            return 0;
        }

        // 联动：所属企业以总名录为准——在册（未摘牌、未删除）才挂得上，企业代号回查名录落
        TSaltEnt refArch = saltEntMapper.selectOne(new QueryWrapper<TSaltEnt>()
                .eq("id", record.getSiteId()).eq("del_flag", 0));
        if (refArch == null) {
            return 0;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            return 0;
        }
        record.setSiteNo(refArch.getSiteNo());
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSaltLinkPage>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        if (record.getStatus() == null) {
            record.setStatus(STATUS_WAIT);
        }
        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTSaltLinkPage(TSaltLinkPage record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        // 压页（冻住）的页改不动
        TSaltLinkPage cur = this.baseMapper.selectById(record.getId());
        if (cur == null || (cur.getStatus() != null && cur.getStatus().intValue() == STATUS_FROZEN)) {
            return 0;
        }

        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSaltLinkPage>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        // 进展是状态机字段，只经推进/退回挪，表单改单不许跳态
        record.setStatus(null);
        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TSaltLinkPage>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int advanceTSaltLinkPage(Long id) {
        if (id == null) {
            return 0;
        }
        TSaltLinkPage cur = this.baseMapper.selectById(id);
        if (cur == null || cur.getDelFlag() == null || cur.getDelFlag().intValue() == 1) {
            return 0;
        }
        // 存量兼容：状态为空按待挂(0)算
        int st = cur.getStatus() == null ? STATUS_WAIT : cur.getStatus().intValue();
        if (st >= STATUS_FROZEN) {
            // 已压页（冻住）挪不了
            return 0;
        }
        int next = st + 1;
        // 推进落定生成核验串（退回不清，跟着页走）
        String checkStr = "CHK-" + (StringUtils.isNotEmpty(cur.getBillNo()) ? cur.getBillNo() : String.valueOf(id))
                + "-" + next + "-" + System.currentTimeMillis();
        UpdateWrapper<TSaltLinkPage> uw = new UpdateWrapper<TSaltLinkPage>()
                .eq("id", id).eq("del_flag", 0);
        // 乐观守卫：并发推进只有一个落得下去；存量空状态按 isNull 守
        if (cur.getStatus() == null) {
            uw.isNull("status");
        } else {
            uw.eq("status", st);
        }
        uw.set("status", next);
        uw.set("content", checkStr);
        uw.set("update_time", new Date());
        return this.baseMapper.update(null, uw);
    }

    @Override
    public int revertTSaltLinkPage(Long id) {
        if (id == null) {
            return 0;
        }
        TSaltLinkPage cur = this.baseMapper.selectById(id);
        if (cur == null || cur.getDelFlag() == null || cur.getDelFlag().intValue() == 1) {
            return 0;
        }
        int st = cur.getStatus() == null ? STATUS_WAIT : cur.getStatus().intValue();
        if (st <= STATUS_WAIT) {
            // 待挂退无可退
            return 0;
        }
        int next = st - 1;
        UpdateWrapper<TSaltLinkPage> uw = new UpdateWrapper<TSaltLinkPage>()
                .eq("id", id).eq("del_flag", 0).eq("status", st);
        uw.set("status", next);
        // 退回只退一态，核验串留着不清
        uw.set("update_time", new Date());
        return this.baseMapper.update(null, uw);
    }

    @Override
    public int deleteTSaltLinkPageByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        if (idArr == null || idArr.length == 0) {
            return 0;
        }
        List<Long> idList = Arrays.asList(idArr);
        // 压页（冻住）的页删不掉；一批里夹着冻住的，整批不删
        Integer frozen = this.baseMapper.selectCount(new QueryWrapper<TSaltLinkPage>()
                .in("id", idList).eq("status", STATUS_FROZEN));
        if (frozen != null && frozen > 0) {
            return 0;
        }
        return this.baseMapper.deleteBatchIds(idList);
    }

    @Override
    public int deleteTSaltLinkPageById(Long id) {
        if (id == null) {
            return 0;
        }
        TSaltLinkPage cur = this.baseMapper.selectById(id);
        if (cur == null || (cur.getStatus() != null && cur.getStatus().intValue() == STATUS_FROZEN)) {
            return 0;
        }
        return this.baseMapper.deleteById(id);
    }
}
