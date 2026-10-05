package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltIodLineMapper;
import com.fc.v2.model.auto.TSaltIodLine;
import com.fc.v2.service.ITSaltIodLineService;

/**
 * 碘含量分档判定线 Service业务层处理（rule-eval 形状：规则求值）
 *
 * 口径（列表/角标/旁路复用同源，只有 availableAt 一处算）：
 *  可用 ⟺ 正常未删 + 在场(0) + eff_start<=at<eff_end（让位之日不含，空端不限）；
 *  排序：优先级降序，并列按 ruleCode 降序；
 *  分档：等于上限取高一档（严格小于才落低档）。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltIodLineServiceImpl implements ITSaltIodLineService {

    /** 优先级降序；并列按 ruleCode 降序（确定性） */
    private static final Comparator<TSaltIodLine> PRIORITY_THEN_CODE_DESC = new Comparator<TSaltIodLine>() {
        @Override
        public int compare(TSaltIodLine a, TSaltIodLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TSaltIodLineMapper saltIodLineMapper;

    @Override
    public TSaltIodLine selectTSaltIodLineById(Long id) {
        return this.saltIodLineMapper.selectById(id);
    }

    @Override
    public List<TSaltIodLine> listAvailable(Date at) {
        List<TSaltIodLine> out = new java.util.ArrayList<TSaltIodLine>();
        if (at == null) {
            return out;
        }
        // 日期不下库当参数（JDBC 时区与本机不对称会整体偏移），捞正常行回内存按墙钟判
        List<TSaltIodLine> all = this.saltIodLineMapper.selectList(
                new QueryWrapper<TSaltIodLine>().eq("del_flag", 0));
        for (TSaltIodLine r : all) {
            if (availableAt(r, at)) {
                out.add(r);
            }
        }
        out.sort(PRIORITY_THEN_CODE_DESC);
        return out;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        // 输入越界一律返回 0（与 evaluateTop 同一把尺子）
        if (input.compareTo(BigDecimal.ZERO) < 0
                || input.compareTo(new BigDecimal("100")) > 0) {
            return 0;
        }
        // 与列表同源：在 at 时刻可用版本里按同一排序取该 ruleCode 的第一条
        for (TSaltIodLine r : listAvailable(at)) {
            if (ruleCode.equals(r.getRuleCode())) {
                return levelOf(r, input);
            }
        }
        return 0;
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (input.compareTo(BigDecimal.ZERO) < 0
                || input.compareTo(new BigDecimal("100")) > 0) {
            return 0;
        }
        List<TSaltIodLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        // 旁路复用与定位/列表同一个谓词，不另起口径
        return availableAt(this.saltIodLineMapper.selectById(id), at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    /** 可用性唯一来源：正常未删 + 在场 + eff_start<=at<eff_end（空端不限） */
    private boolean availableAt(TSaltIodLine r, Date at) {
        if (r == null || at == null) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag().intValue() == 1) {
            return false;
        }
        if (r.getStatus() != null && r.getStatus().intValue() != 0) {
            return false;
        }
        if (r.getEffStart() != null && r.getEffStart().after(at)) {
            return false;
        }
        // 让位之日（不含）
        return r.getEffEnd() == null || r.getEffEnd().after(at);
    }

    /** 分档：等于上限取高一档（严格小于才落低档）；缺档的线跳过 */
    private int levelOf(TSaltIodLine rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) < 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) < 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }
}
