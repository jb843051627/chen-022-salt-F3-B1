package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltSpotRowMapper;
import com.fc.v2.model.auto.TSaltSpotRow;
import com.fc.v2.service.ITSaltSpotRowService;

/**
 * 抽检结果报送核收行 Service业务层处理（batch-process 形状：整批提交）
 *
 * 批次口径（写入侧与 listErrors 读取侧同源）：
 *  整批提交逐行校验，合法的收下(1)、非法的记失败明细(2)并保留原始行号，返回成功行数；
 *  同一批次重复提交不得重复入库（先查重，并发撞 uk_batch_row 兜底幂等）；
 *  行数超过单批上限返回 -1 且整批不入库；空批次返回 0。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltSpotRowServiceImpl implements ITSaltSpotRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TSaltSpotRowMapper saltSpotRowMapper;

    @Override
    public TSaltSpotRow selectTSaltSpotRowById(Long id) {
        return this.saltSpotRowMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TSaltSpotRow> rows) {
        if (rows == null || rows.isEmpty()) {
            // 空批次
            return 0;
        }
        if (rows.size() > MAX_ROWS) {
            // 超单批上限：整批不入库
            return -1;
        }
        // 批次号以入参为准，入参空取首行；两头都空没法归批，整批不入
        String no = isBlank(batchNo) ? rows.get(0).getBatchNo() : batchNo;
        if (isBlank(no)) {
            return 0;
        }
        // 同一批次重复提交不得重复入库
        Integer exists = this.saltSpotRowMapper.selectCount(new QueryWrapper<TSaltSpotRow>()
                .eq("batch_no", no).eq("del_flag", 0));
        if (exists != null && exists.intValue() > 0) {
            return 0;
        }
        int ok = 0;
        int seq = 0;
        try {
            for (TSaltSpotRow r : rows) {
                seq++;
                boolean valid = r.getItemCode() != null && !r.getItemCode().trim().isEmpty()
                        && r.getQty() != null
                        && r.getQty().compareTo(java.math.BigDecimal.ZERO) > 0;
                r.setRowNo(Integer.valueOf(seq));
                r.setBatchNo(no);
                r.setStatus(valid ? STATUS_OK : STATUS_FAIL);
                r.setDelFlag(0);
                this.saltSpotRowMapper.insert(r);
                if (valid) {
                    ok++;
                }
            }
        } catch (DuplicateKeyException e) {
            // 并发同批并递撞 uk_batch_row：整批回滚，幂等了结
            org.springframework.transaction.interceptor.TransactionAspectSupport
                    .currentTransactionStatus().setRollbackOnly();
            return 0;
        }
        return ok;
    }

    @Override
    public List<TSaltSpotRow> listErrors(String batchNo) {
        // 与写入侧同源：同一张表、同一个失败态、同一个正常标记，按行号升序
        return this.saltSpotRowMapper.selectList(new QueryWrapper<TSaltSpotRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL).eq("del_flag", 0)
                .orderByAsc("row_no"));
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
