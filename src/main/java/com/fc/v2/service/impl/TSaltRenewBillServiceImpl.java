package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TSaltRenewBillMapper;
import com.fc.v2.model.auto.TSaltRenewBill;
import com.fc.v2.service.ITSaltRenewBillService;

/**
 * 延续换证核签单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * <p>口径：档口 0..2（县初核/市复审/省核准）；同档算齐办法 0一名即可、1两名须同判
 * （应落人数以 need_count 为准，缺省两名）；算齐一票挪一档，省核准算齐即已办结；
 * 否决落"已挪回"。受票与否决共用同一处"在核"守卫，两处判断同源。</p>
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
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TSaltRenewBillMapper saltRenewBillMapper;

    @Override
    public TSaltRenewBill selectTSaltRenewBillById(Long id) {
        return this.saltRenewBillMapper.selectById(id);
    }

    /** 在核守卫：approve/reject 同一处判断，已办结/已挪回不再受票 */
    private TSaltRenewBill loadRunning(Long id) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        Integer st = r.getStatus();
        if (st != null && st.intValue() != STATUS_RUNNING) {
            return null;
        }
        return r;
    }

    /** 本档算齐所需印数：一名即可→1；两名须同判→以 need_count 为准（缺省两名） */
    private static int quorumOf(TSaltRenewBill r) {
        Integer mode = r.getSignMode();
        if (mode != null && mode.intValue() == MODE_AND) {
            return r.getNeedCount() != null && r.getNeedCount() > 0 ? r.getNeedCount() : 2;
        }
        return 1;
    }

    @Override
    public TSaltRenewBill approve(Long id, String approver, String comment) {
        if (approver == null || approver.trim().isEmpty()) {
            return null;
        }
        TSaltRenewBill r = loadRunning(id);
        if (r == null) {
            return null;
        }
        int signed = (r.getSignCount() == null ? 0 : r.getSignCount()) + 1;
        if (signed < quorumOf(r)) {
            // 本档未算齐：只落印，不挪档
            r.setSignCount(Integer.valueOf(signed));
            this.saltRenewBillMapper.updateById(r);
            return r;
        }
        // 本档算齐：挪下一档，印数清零重计
        int node = (r.getNodeNo() == null ? 0 : r.getNodeNo()) + 1;
        r.setSignCount(Integer.valueOf(0));
        if (node > MAX_NODE) {
            r.setNodeNo(Integer.valueOf(MAX_NODE));
            r.setStatus(Integer.valueOf(STATUS_PASS));
        } else {
            r.setNodeNo(Integer.valueOf(node));
            r.setStatus(Integer.valueOf(STATUS_RUNNING));
        }
        this.saltRenewBillMapper.updateById(r);
        return r;
    }

    @Override
    public TSaltRenewBill reject(Long id, String approver, String comment) {
        TSaltRenewBill r = loadRunning(id);
        if (r == null) {
            return null;
        }
        // 否决：落"已挪回"，与受票走同一处在核守卫
        r.setStatus(Integer.valueOf(STATUS_VETO));
        this.saltRenewBillMapper.updateById(r);
        return r;
    }

    @Override
    public TSaltRenewBill rollback(Long id, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        if (r.getStatus() != null && r.getStatus() == STATUS_PASS) {
            // 已办结封口，不再挪回
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        r.setNodeNo(Integer.valueOf(Math.max(0, node - 1)));
        // 退回后本档重签，情形回到在核
        r.setSignCount(Integer.valueOf(0));
        r.setStatus(Integer.valueOf(STATUS_RUNNING));
        this.saltRenewBillMapper.updateById(r);
        return r;
    }
}
