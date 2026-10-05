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
 * 碘含量分档判定线 Service业务层处理（rule-eval 形状）
 *
 * <p>口径："at 时刻可用"只有一处判定（availableWrapper）：未删 + 在场 +
 * 生效窗口（立线之日含、让位之日不含）。列表、条数、单条可用、定位求值全部同源；
 * 输入越界判断 evaluate 与 evaluateTop 也走同一处。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltIodLineServiceImpl implements ITSaltIodLineService {

    /** 线的情形：0在场（已停用为 1） */
    private static final int STATUS_ON = 0;

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

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）+ 字符串绑定：
     * JDBC 的 serverTimezone 与本机时区不对称，直接把 java.util.Date 作参数会整体偏移，
     * 使边界判定在"切换当日"这类用例上随机错档（实测踩到）。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /**
     * "at 时刻可用"唯一判定来源：未删 + 在场 + 生效窗口内。
     * 列表/角标条数/单条可用/规则定位一律走这里，口径不再各写一份。
     */
    private QueryWrapper<TSaltIodLine> availableWrapper(Date at) {
        String now = ts(at);
        QueryWrapper<TSaltIodLine> w = new QueryWrapper<TSaltIodLine>();
        w.eq("del_flag", 0);
        w.and(x -> x.isNull("status").or().eq("status", STATUS_ON));
        // 立线之日（含）起可用；让位之日（不含）起让位
        w.and(x -> x.isNull("eff_start").or().le("eff_start", now));
        w.and(x -> x.isNull("eff_end").or().gt("eff_end", now));
        return w;
    }

    /** 输入越界判断：evaluate 与 evaluateTop 同一处 */
    private static boolean outOfRange(BigDecimal input) {
        return input.compareTo(BigDecimal.ZERO) < 0
                || input.compareTo(new BigDecimal("100")) > 0;
    }

    @Override
    public TSaltIodLine selectTSaltIodLineById(Long id) {
        return this.saltIodLineMapper.selectById(id);
    }

    @Override
    public List<TSaltIodLine> listAvailable(Date at) {
        if (at == null) {
            return new java.util.ArrayList<TSaltIodLine>();
        }
        List<TSaltIodLine> list = this.saltIodLineMapper.selectList(availableWrapper(at));
        list.sort(PRIORITY_THEN_CODE_DESC);
        return list;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        if (outOfRange(input)) {
            return 0;
        }
        TSaltIodLine rule = findRule(ruleCode, at);
        if (rule == null) {
            // 无生效版本
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (outOfRange(input)) {
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
        Integer cnt = this.saltIodLineMapper.selectCount(availableWrapper(at).eq("id", id));
        return cnt != null && cnt > 0;
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        Integer cnt = this.saltIodLineMapper.selectCount(availableWrapper(at));
        return cnt == null ? 0 : cnt;
    }

    /** 定位 ruleCode 在 at 时刻的生效版本：同一可用口径内，优先级高者在先，并列取最新立线者 */
    private TSaltIodLine findRule(String ruleCode, Date at) {
        List<TSaltIodLine> hit = this.saltIodLineMapper.selectList(
                availableWrapper(at).eq("rule_code", ruleCode));
        if (hit == null || hit.isEmpty()) {
            return null;
        }
        hit.sort(new Comparator<TSaltIodLine>() {
            @Override
            public int compare(TSaltIodLine a, TSaltIodLine b) {
                int pa = a.getPriority() == null ? 0 : a.getPriority();
                int pb = b.getPriority() == null ? 0 : b.getPriority();
                if (pa != pb) {
                    return pb - pa;
                }
                Date ea = a.getEffStart() == null ? new Date(0L) : a.getEffStart();
                Date eb = b.getEffStart() == null ? new Date(0L) : b.getEffStart();
                return eb.compareTo(ea);
            }
        });
        return hit.get(0);
    }

    /** 阈值解析：档案为主、明细覆盖；两者皆缺返回 null（调用方按不可用处理），精度统一 2 位 */
    private BigDecimal resolveLimit(TSaltIodLine r, int tier) {
        if (tier == 1) {
            return r.getTh1Max();
        }
        if (tier == 2) {
            return r.getTh2Max();
        }
        return r.getTh3Max();
    }

    /** 档案级默认阈值（明细未维护时的回退来源） */
    private BigDecimal defaultThreshold(TSaltIodLine r, int tier) {
        return r.getTh1Max();
    }

    /** 分档：等于上限取高一档 */
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
