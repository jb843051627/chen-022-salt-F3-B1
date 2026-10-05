package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltSpotRowMapper;
import com.fc.v2.model.auto.TSaltSpotRow;
import com.fc.v2.service.ITSaltSpotRowService;

/**
 * 抽检结果报送核收行 Service业务层处理（batch-process 形状：整批提交）
 *
 * <p>口径：批次号以入参 batchNo 作准；同一批次重复递（含并发并着递）不重复入库，
 * 返回该批已收下行数；行数超过单批上限返回 -1 且整批不入库；空批次返回 0。</p>
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
    public int submitBatch(String batchNo, List<TSaltSpotRow> rows) {
        if (batchNo == null || batchNo.trim().isEmpty() || rows == null || rows.isEmpty()) {
            // 空批次
            return 0;
        }
        if (rows.size() > MAX_ROWS) {
            // 超过单批上限：整批不入库
            return -1;
        }
        // 同一批次并着递两回也只落一回：按批次号串行，先到的先落
        String lock = ("SPOT_BATCH_" + batchNo).intern();
        synchronized (lock) {
            Integer accepted = this.saltSpotRowMapper.selectCount(new QueryWrapper<TSaltSpotRow>()
                    .eq("batch_no", batchNo).eq("status", STATUS_OK));
            Integer rejected = this.saltSpotRowMapper.selectCount(new QueryWrapper<TSaltSpotRow>()
                    .eq("batch_no", batchNo).eq("status", STATUS_FAIL));
            boolean alreadyFiled = (accepted != null && accepted > 0) || (rejected != null && rejected > 0);
            if (alreadyFiled) {
                // 幂等：该批已核收过，不重复入库，返回已收下行数
                return accepted == null ? 0 : accepted;
            }
            int ok = 0;
            int seq = 0;
            for (TSaltSpotRow r : rows) {
                seq++;
                r.setBatchNo(batchNo);
                if (r.getRowNo() == null) {
                    // 保留原始行号；未报的按递交顺位补，行号与操作次数对得上
                    r.setRowNo(Integer.valueOf(seq));
                }
                boolean valid = r.getItemCode() != null && !r.getItemCode().trim().isEmpty()
                        && r.getQty() != null
                        && r.getQty().compareTo(java.math.BigDecimal.ZERO) > 0;
                if (valid) {
                    r.setStatus(STATUS_OK);
                    ok++;
                } else {
                    // 非法行记入失败明细，不拖垮整批
                    r.setStatus(STATUS_FAIL);
                }
                this.saltSpotRowMapper.insert(r);
            }
            return ok;
        }
    }

    @Override
    public List<TSaltSpotRow> listErrors(String batchNo) {
        return this.saltSpotRowMapper.selectList(new QueryWrapper<TSaltSpotRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL)
                .orderByAsc("row_no"));
    }
}
