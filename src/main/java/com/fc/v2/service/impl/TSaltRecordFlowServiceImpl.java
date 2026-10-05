package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltRecordFlowMapper;
import com.fc.v2.model.auto.TSaltRecordFlow;
import com.fc.v2.service.ITSaltRecordFlowService;

/**
 * 跨省经营备案单 Service业务层处理（state-machine 形状：单据流转）
 *
 * <p>口径（与 doc/schema/salt.sql 同源）：
 * 步次 0..3（递单受理/材料核验/生效确认/缴回封卷），一次只挪一档；
 * 情形 0未起 1在办 2已封卷落档 3收口止住——落档与止住都封口：改不动/删不掉/挪不了。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltRecordFlowServiceImpl implements ITSaltRecordFlowService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;
    private static final int STATUS_CLOSED = 3;

    @javax.annotation.Resource
    private TSaltRecordFlowMapper saltRecordFlowMapper;

    @Override
    public TSaltRecordFlow selectTSaltRecordFlowById(Long id) {
        return this.saltRecordFlowMapper.selectById(id);
    }

    @Override
    public List<TSaltRecordFlow> selectTSaltRecordFlowList(QueryWrapper<TSaltRecordFlow> queryWrapper) {
        return this.saltRecordFlowMapper.selectList(queryWrapper);
    }

    /** 封口（已封卷落档/收口止住）判定：advance/rollback/updateContent/remove 共用同一处 */
    private static boolean sealed(TSaltRecordFlow r) {
        Integer st = r.getStatus();
        return st != null && (st.intValue() == STATUS_TERMINAL || st.intValue() == STATUS_CLOSED);
    }

    @Override
    public TSaltRecordFlow advance(Long id, String remark) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (r == null || sealed(r)) {
            // 已办结（落档/止住）的单子挡在原地，环节不再往前挪
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        if (st >= MAX_STAGE) {
            // 缴回封卷走完：落档封存，此后改不动/删不掉/挪不了
            r.setStage(MAX_STAGE);
            r.setStatus(STATUS_TERMINAL);
        } else {
            // 一次只挪一档，走完哪档记哪档
            r.setStage(st + 1);
            r.setStatus(STATUS_ACTIVE);
        }
        r.setLastAction(remark);
        this.saltRecordFlowMapper.updateById(r);
        return r;
    }

    @Override
    public TSaltRecordFlow rollback(Long id, String remark) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (r == null || sealed(r)) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        if (st <= 0) {
            // 已在递单受理，无档可退
            return null;
        }
        // 只退一档，不直接回起点
        r.setStage(st - 1);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.saltRecordFlowMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (r == null || sealed(r)) {
            // 已办结不得再改
            return false;
        }
        r.setContent(remark);
        return this.saltRecordFlowMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (r == null || sealed(r)) {
            // 已办结不得删除，留档备查
            return false;
        }
        return this.saltRecordFlowMapper.deleteById(id) > 0;
    }

}
