package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TSaltRenewBillMapper;
import com.fc.v2.model.auto.TSaltRenewBill;
import com.fc.v2.service.ITSaltRenewBillService;

/**
 * 延续换证核签单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * 复核链口径（三处判断同源，都走 requiredSigns/isTerminal，不许各算各的）：
 *  档口 0县初核 1市复审 2省核准；情形 0在核 1已办结 2已挪回。
 *  本档算齐：sign_count 落够 requiredSigns（need_count 优先，缺省按 sign_mode 一名即可/两名须同判）才进下一档；
 *  省核准档算齐即办结；否决落已挪回；退回退一档、本档印数清零重签；已办结/已挪回都拦截。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltRenewBillServiceImpl implements ITSaltRenewBillService {

    private static final int MAX_NODE = 2;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_BACK = 2;

    @javax.annotation.Resource
    private TSaltRenewBillMapper saltRenewBillMapper;

    @Override
    public TSaltRenewBill selectTSaltRenewBillById(Long id) {
        return this.saltRenewBillMapper.selectById(id);
    }

    @Override
    public TSaltRenewBill approve(Long id, String approver, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty() || isTerminal(r)) {
            // 已办结/已挪回的单挡住，不能再签
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        int signed = (r.getSignCount() == null ? 0 : r.getSignCount()) + 1;
        UpdateWrapper<TSaltRenewBill> uw = new UpdateWrapper<TSaltRenewBill>()
                .eq("id", r.getId()).eq("status", STATUS_RUNNING);
        if (signed >= requiredSigns(r)) {
            // 本档算齐：省核准档算齐即办结，否则进下一档、本档印数清零
            if (node >= MAX_NODE) {
                uw.set("status", STATUS_PASS);
            } else {
                uw.set("node_no", node + 1);
            }
            uw.set("sign_count", 0);
        } else {
            uw.set("sign_count", signed);
        }
        if (this.saltRenewBillMapper.update(null, uw) <= 0) {
            return null;
        }
        return this.saltRenewBillMapper.selectById(id);
    }

    @Override
    public TSaltRenewBill reject(Long id, String approver, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (r == null || isTerminal(r)) {
            return null;
        }
        // 否决即挪回：情形落"已挪回"，不能什么都不落
        UpdateWrapper<TSaltRenewBill> uw = new UpdateWrapper<TSaltRenewBill>()
                .eq("id", r.getId()).eq("status", STATUS_RUNNING);
        uw.set("status", STATUS_BACK);
        if (this.saltRenewBillMapper.update(null, uw) <= 0) {
            return null;
        }
        return this.saltRenewBillMapper.selectById(id);
    }

    @Override
    public TSaltRenewBill rollback(Long id, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (r == null || r.getStatus() == null || r.getStatus().intValue() == STATUS_PASS) {
            // 已办结退不动
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        UpdateWrapper<TSaltRenewBill> uw = new UpdateWrapper<TSaltRenewBill>()
                .eq("id", r.getId()).ne("status", STATUS_PASS);
        // 退一档（不到起点以下）、本档印数清零重签、情形回在核
        uw.set("node_no", Math.max(0, node - 1));
        uw.set("sign_count", 0);
        uw.set("status", STATUS_RUNNING);
        if (this.saltRenewBillMapper.update(null, uw) <= 0) {
            return null;
        }
        return this.saltRenewBillMapper.selectById(id);
    }

    /** 本档算齐应落人数：need_count 优先；缺省按同档算齐办法（0一名即可 1两名须同判） */
    private int requiredSigns(TSaltRenewBill r) {
        if (r.getNeedCount() != null && r.getNeedCount().intValue() > 0) {
            return r.getNeedCount().intValue();
        }
        return r.getSignMode() != null && r.getSignMode().intValue() == MODE_AND ? 2 : 1;
    }

    /** 终端：已办结(1)/已挪回(2) 都拦截 */
    private boolean isTerminal(TSaltRenewBill r) {
        return r.getStatus() != null
                && (r.getStatus().intValue() == STATUS_PASS || r.getStatus().intValue() == STATUS_BACK);
    }
}
