package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TSaltEntMapper;
import com.fc.v2.mapper.auto.TSaltRecordCheckMapper;
import com.fc.v2.mapper.auto.TSaltRecordFlowMapper;
import com.fc.v2.mapper.auto.TSaltRecordNoteMapper;
import com.fc.v2.model.auto.TSaltEnt;
import com.fc.v2.model.auto.TSaltRecordCheck;
import com.fc.v2.model.auto.TSaltRecordFlow;
import com.fc.v2.model.auto.TSaltRecordNote;
import com.fc.v2.service.ITSaltRecordFlowService;

/**
 * 跨省经营备案单 Service业务层处理（state-machine 形状：单据流转）
 *
 * 状态机（与 doc/schema/salt-spec.md 同源，改一处必须同步另一处）：
 *  stage  0递单受理 1材料核验 2生效确认 3缴回封卷，系统按已过档次数出来，一次只挪一档；
 *  status 0未起 1在办 2已封卷落档 3收口止住；落档与止住都封口（改不动/删不掉/挪不了）。
 *  各档守卫：1→2 核验笔填全（填全一刻自动起公示钟）；2→3 公示够钟；3→封卷 旧证缴回回执已落。
 *  联动：受理与挪步的门槛以总名录为准（在册才收/才挪）；单的环节与封卷结果以单为准，
 *        名录变动不反向级联改单——摘牌企业的在办单不能再推进，只能收口止住。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltRecordFlowServiceImpl implements ITSaltRecordFlowService {

    /** 步次：0递单受理 1材料核验 2生效确认 3缴回封卷 */
    private static final int MAX_STAGE = 3;
    /** 段记载里落档封存段的步次记号 */
    private static final int NOTE_STAGE_SEALED = 4;

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_SEALED = 2;
    private static final int STATUS_CLOSED = 3;

    private static final String ACTION_ACCEPT = "accept";
    private static final String ACTION_ADVANCE = "advance";
    private static final String ACTION_ROLLBACK = "rollback";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_SEAL = "seal";

    /** 收口说法：N不必备案 S就地止住；落档封存则留空 */
    private static final String CLOSE_NONEED = "N";
    private static final String CLOSE_STOP = "S";

    private static final int DEFAULT_CHECK_REQUIRED = 3;

    @javax.annotation.Resource
    private TSaltRecordFlowMapper saltRecordFlowMapper;
    @javax.annotation.Resource
    private TSaltRecordCheckMapper saltRecordCheckMapper;
    @javax.annotation.Resource
    private TSaltRecordNoteMapper saltRecordNoteMapper;
    @javax.annotation.Resource
    private TSaltEntMapper saltEntMapper;

    @Override
    public TSaltRecordFlow selectTSaltRecordFlowById(Long id) {
        return this.saltRecordFlowMapper.selectById(id);
    }

    @Override
    public List<TSaltRecordFlow> selectTSaltRecordFlowList(QueryWrapper<TSaltRecordFlow> queryWrapper) {
        if (queryWrapper == null) {
            queryWrapper = new QueryWrapper<TSaltRecordFlow>();
        }
        // 台账口径：只看正常单（封卷落档的仍在台账留档可见）
        queryWrapper.eq("del_flag", 0);
        return this.saltRecordFlowMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TSaltRecordFlow accept(TSaltRecordFlow form) {
        if (form == null) {
            return null;
        }
        // 四样齐：企业代号/起运/到货/县域个数，缺一样不收；表单递来的 stage/status 仅作来意，不看
        if (isBlank(form.getEntNo()) || form.getDepartAt() == null || form.getArriveAt() == null
                || form.getRegionCount() == null || form.getRegionCount().intValue() <= 0
                || form.getDepartAt().after(form.getArriveAt())) {
            return null;
        }
        // 联动：受理门槛以总名录为准——企业在册（未摘牌、未删除）才收
        TSaltEnt ent = this.saltEntMapper.selectOne(new QueryWrapper<TSaltEnt>()
                .eq("site_no", form.getEntNo()).eq("del_flag", 0));
        if (ent == null || ent.getStatus() == null || ent.getStatus().intValue() != 0) {
            return null;
        }
        // 一企一活单：该企业在办/止住已有单的，幂等回那张，不新建、不另起段
        TSaltRecordFlow active = findActiveByEnt(form.getEntNo());
        if (active != null) {
            return active;
        }

        TSaltRecordFlow r = new TSaltRecordFlow();
        r.setBizNo(form.getBizNo());
        r.setEntNo(form.getEntNo());
        r.setDepartAt(form.getDepartAt());
        r.setArriveAt(form.getArriveAt());
        r.setRegionCount(form.getRegionCount());
        r.setCheckRequired(form.getCheckRequired() != null ? form.getCheckRequired() : DEFAULT_CHECK_REQUIRED);
        r.setPublicityHours(form.getPublicityHours());
        r.setCertReturned(0);
        r.setStage(0);
        r.setStatus(STATUS_ACTIVE);
        r.setContent(form.getRemark());
        r.setLastAction(ACTION_ACCEPT);
        r.setDelFlag(0);
        r.setRemark(form.getRemark());
        try {
            this.saltRecordFlowMapper.insert(r);
        } catch (DuplicateKeyException e) {
            // 并发同递撞 uk_active_ent：回那张在办单，幂等了结
            return findActiveByEnt(form.getEntNo());
        }
        // 受理落定落第一段（递单受理档，头趟）
        writeNote(r.getId(), 0, 1, ACTION_ACCEPT, form.getRemark());
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TSaltRecordFlow advance(Long id, String remark) {
        TSaltRecordFlow r = loadOpen(id);
        if (r == null || r.getStatus() == null || r.getStatus().intValue() != STATUS_ACTIVE) {
            // 不存在/未起/已封口（封卷、止住）都挪不了
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        // 联动：名录已摘牌的在办单不能再往前推进，只能收口止住（不级联改单，挪步当下才核）
        if (!entInRank(r.getEntNo())) {
            return null;
        }
        Date now = new Date();
        boolean sealing = st >= MAX_STAGE;
        if (!sealing && !stageGuardPassed(r, st, now)) {
            // 一步没走完，不许跳到下一格
            return null;
        }
        if (sealing && (r.getCertReturned() == null || r.getCertReturned().intValue() != 1)) {
            // 缴回封卷档：旧证缴回回执没落，不许封卷
            return null;
        }

        UpdateWrapper<TSaltRecordFlow> uw = new UpdateWrapper<TSaltRecordFlow>()
                .eq("id", r.getId()).eq("status", STATUS_ACTIVE);
        // 乐观守卫：并发挪步只有一个落得下去；存量空步次按 isNull 守
        if (r.getStage() == null) {
            uw.isNull("stage");
        } else {
            uw.eq("stage", st);
        }
        if (sealing) {
            uw.set("status", STATUS_SEALED);
        } else {
            uw.set("stage", st + 1);
        }
        uw.set("content", remark);
        uw.set("last_action", sealing ? ACTION_SEAL : ACTION_ADVANCE);
        uw.set("update_time", now);
        if (r.getPublicityStart() != null) {
            // 已连着公示的钟点数与判定同一次算出，不落二手数据
            uw.set("publicity_done_hours", hoursBetween(r.getPublicityStart(), now));
        }
        if (this.saltRecordFlowMapper.update(null, uw) <= 0) {
            // 并发挪步被乐观守卫挡住
            return null;
        }
        if (sealing) {
            writeNote(r.getId(), NOTE_STAGE_SEALED, 1, ACTION_SEAL, remark);
        } else {
            writeNote(r.getId(), st + 1, nextWalk(r.getId(), st + 1), ACTION_ADVANCE, remark);
        }
        return this.saltRecordFlowMapper.selectById(r.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TSaltRecordFlow rollback(Long id, String remark) {
        TSaltRecordFlow r = loadOpen(id);
        if (r == null || r.getStatus() == null || r.getStatus().intValue() != STATUS_ACTIVE) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        if (st <= 0) {
            // 已在递单受理，退无可退
            return null;
        }
        Date now = new Date();
        UpdateWrapper<TSaltRecordFlow> uw = new UpdateWrapper<TSaltRecordFlow>()
                .eq("id", r.getId()).eq("stage", st).eq("status", STATUS_ACTIVE);
        // 只退一档，不回起点；回退打断公示钟即清零
        uw.set("stage", st - 1);
        uw.set("publicity_start", null);
        uw.set("publicity_done_hours", null);
        uw.set("content", remark);
        uw.set("last_action", ACTION_ROLLBACK);
        uw.set("update_time", now);
        if (this.saltRecordFlowMapper.update(null, uw) <= 0) {
            return null;
        }
        // 退档后重走另起一趟，旧段沉底保留不删
        writeNote(r.getId(), st - 1, nextWalk(r.getId(), st - 1), ACTION_ROLLBACK, remark);
        return this.saltRecordFlowMapper.selectById(r.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean fillCheck(Long flowId, String itemNo, String itemName) {
        TSaltRecordFlow r = loadOpen(flowId);
        if (r == null || r.getStatus() == null || r.getStatus().intValue() != STATUS_ACTIVE
                || r.getStage() == null || r.getStage().intValue() != 1) {
            // 核验笔只在材料核验档填
            return false;
        }
        if (isBlank(itemNo)) {
            return false;
        }
        // 同一笔第二遍空递不重复落
        Integer dup = this.saltRecordCheckMapper.selectCount(new QueryWrapper<TSaltRecordCheck>()
                .eq("flow_id", flowId).eq("item_no", itemNo).eq("filled", 1).eq("del_flag", 0));
        if (dup == null || dup.intValue() == 0) {
            TSaltRecordCheck check = new TSaltRecordCheck();
            check.setFlowId(flowId);
            check.setItemNo(itemNo);
            check.setItemName(itemName);
            check.setFilled(1);
            check.setFillAt(new Date());
            check.setDelFlag(0);
            this.saltRecordCheckMapper.insert(check);
        }
        // 填全一刻自动起公示钟
        int required = r.getCheckRequired() == null ? 0 : r.getCheckRequired();
        Integer filled = this.saltRecordCheckMapper.selectCount(new QueryWrapper<TSaltRecordCheck>()
                .eq("flow_id", flowId).eq("filled", 1).eq("del_flag", 0));
        if (filled != null && filled.intValue() >= required && r.getPublicityStart() == null) {
            UpdateWrapper<TSaltRecordFlow> uw = new UpdateWrapper<TSaltRecordFlow>()
                    .eq("id", r.getId()).isNull("publicity_start");
            uw.set("publicity_start", new Date());
            uw.set("publicity_done_hours", BigDecimal.ZERO);
            this.saltRecordFlowMapper.update(null, uw);
        }
        return true;
    }

    @Override
    public boolean certBack(Long id) {
        TSaltRecordFlow r = loadOpen(id);
        if (r == null || r.getStatus() == null || r.getStatus().intValue() != STATUS_ACTIVE) {
            return false;
        }
        if (r.getCertReturned() != null && r.getCertReturned().intValue() == 1) {
            // 回执已落，重复落不重复记
            return true;
        }
        UpdateWrapper<TSaltRecordFlow> uw = new UpdateWrapper<TSaltRecordFlow>()
                .eq("id", r.getId()).eq("status", STATUS_ACTIVE);
        uw.set("cert_returned", 1);
        uw.set("update_time", new Date());
        return this.saltRecordFlowMapper.update(null, uw) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TSaltRecordFlow close(Long id, String closeType, String remark) {
        if (!CLOSE_NONEED.equals(closeType) && !CLOSE_STOP.equals(closeType)) {
            return null;
        }
        TSaltRecordFlow r = loadOpen(id);
        if (r == null || r.getStatus() == null || r.getStatus().intValue() != STATUS_ACTIVE) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        Date now = new Date();
        UpdateWrapper<TSaltRecordFlow> uw = new UpdateWrapper<TSaltRecordFlow>()
                .eq("id", r.getId()).eq("status", STATUS_ACTIVE);
        uw.set("status", STATUS_CLOSED);
        uw.set("close_type", closeType);
        uw.set("content", remark);
        uw.set("last_action", ACTION_CLOSE);
        uw.set("update_time", now);
        if (this.saltRecordFlowMapper.update(null, uw) <= 0) {
            return null;
        }
        // 收口段记止住时所在档；收口后单即封口，该档这趟不会再有别的段
        writeNote(r.getId(), st, nextWalk(r.getId(), st), ACTION_CLOSE, remark);
        return this.saltRecordFlowMapper.selectById(r.getId());
    }

    @Override
    public Map<String, Object> audit(Long id) {
        TSaltRecordFlow flow = this.saltRecordFlowMapper.selectById(id);
        if (flow == null) {
            return null;
        }
        List<TSaltRecordNote> segments = this.saltRecordNoteMapper.selectList(
                new QueryWrapper<TSaltRecordNote>().eq("flow_id", id).eq("del_flag", 0)
                        .orderByAsc("seq").orderByAsc("id"));
        List<String> problems = new ArrayList<String>();
        // 段流倒推复核：段是唯一来源，stage/status 都要能从段流推回来
        int cur = 0;
        int expectSeq = 1;
        boolean sealSeen = false;
        boolean closeSeen = false;
        Map<Integer, Integer> walkByStage = new HashMap<Integer, Integer>();
        for (TSaltRecordNote n : segments) {
            if (n.getSeq() == null || n.getSeq().intValue() != expectSeq) {
                problems.add("段次断档：应为" + expectSeq + "实为" + n.getSeq());
            }
            expectSeq = n.getSeq() == null ? expectSeq + 1 : n.getSeq() + 1;
            String action = n.getAction();
            int stageNo = n.getStageNo() == null ? -1 : n.getStageNo();
            if (ACTION_ACCEPT.equals(action) || ACTION_ADVANCE.equals(action) || ACTION_ROLLBACK.equals(action)) {
                int expectWalk = walkByStage.get(stageNo) == null ? 1 : walkByStage.get(stageNo) + 1;
                if (n.getWalkNo() == null || n.getWalkNo().intValue() != expectWalk) {
                    problems.add("第" + n.getSeq() + "段步次" + stageNo + "趟次不对：应为" + expectWalk + "实为" + n.getWalkNo());
                }
                walkByStage.put(stageNo, expectWalk);
            }
            if (ACTION_ACCEPT.equals(action)) {
                if (stageNo != 0) {
                    problems.add("受理段步次应为0实为" + stageNo);
                }
                cur = 0;
            } else if (ACTION_ADVANCE.equals(action)) {
                if (stageNo != cur + 1) {
                    problems.add("第" + n.getSeq() + "段跳档：应由" + cur + "到" + (cur + 1) + "实为" + stageNo);
                }
                cur = stageNo;
            } else if (ACTION_ROLLBACK.equals(action)) {
                if (stageNo != cur - 1) {
                    problems.add("第" + n.getSeq() + "段回退越档：应由" + cur + "到" + (cur - 1) + "实为" + stageNo);
                }
                cur = stageNo;
            } else if (ACTION_SEAL.equals(action)) {
                if (stageNo != NOTE_STAGE_SEALED || cur != MAX_STAGE) {
                    problems.add("第" + n.getSeq() + "段封卷落档位置不对");
                }
                sealSeen = true;
            } else if (ACTION_CLOSE.equals(action)) {
                if (stageNo != cur) {
                    problems.add("第" + n.getSeq() + "段收口未记止住时所在档");
                }
                closeSeen = true;
            }
        }
        int stage = flow.getStage() == null ? 0 : flow.getStage();
        if (stage != cur) {
            problems.add("单上步次" + stage + "与段流推回的" + cur + "对不上");
        }
        int status = flow.getStatus() == null ? -1 : flow.getStatus();
        if (sealSeen && status != STATUS_SEALED) {
            problems.add("段流已封卷落档但单上情形为" + status);
        }
        if (closeSeen && status != STATUS_CLOSED) {
            problems.add("段流已收口止住但单上情形为" + status);
        }
        if (!sealSeen && !closeSeen && status != STATUS_DRAFT && status != STATUS_ACTIVE) {
            problems.add("段流未封口但单上情形为" + status);
        }
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("flow", flow);
        out.put("segments", segments);
        out.put("consistent", problems.isEmpty());
        out.put("problems", problems);
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateContent(Long id, String remark) {
        TSaltRecordFlow r = loadOpen(id);
        if (r == null || isSealed(r)) {
            // 已办结（封卷/止住）改不动
            return false;
        }
        UpdateWrapper<TSaltRecordFlow> uw = new UpdateWrapper<TSaltRecordFlow>().eq("id", r.getId());
        uw.set("content", remark);
        uw.set("update_time", new Date());
        this.saltRecordFlowMapper.update(null, uw);
        // 同档第二遍不另起一行：改当前段留下的话，改了什么段里看得出来
        TSaltRecordNote latest = this.saltRecordNoteMapper.selectOne(new QueryWrapper<TSaltRecordNote>()
                .eq("flow_id", r.getId()).eq("del_flag", 0)
                .orderByDesc("seq").orderByDesc("id").last("limit 1"));
        if (latest != null) {
            UpdateWrapper<TSaltRecordNote> nw = new UpdateWrapper<TSaltRecordNote>().eq("id", latest.getId());
            nw.set("said", remark);
            nw.set("update_time", new Date());
            this.saltRecordNoteMapper.update(null, nw);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        TSaltRecordFlow r = loadOpen(id);
        if (r == null || isSealed(r)) {
            // 已封卷/已止住的单禁止删除，留档
            return false;
        }
        // 未封口的单物理删除（连带核验笔与流转段），释放一企一活单键
        this.saltRecordCheckMapper.delete(new QueryWrapper<TSaltRecordCheck>().eq("flow_id", id));
        this.saltRecordNoteMapper.delete(new QueryWrapper<TSaltRecordNote>().eq("flow_id", id));
        return this.saltRecordFlowMapper.deleteById(id) > 0;
    }

    /**
     * 本档守卫：一步没走完不许跳到下一格。
     * 0递单受理→1材料核验：受理时四样齐已验，无附加守卫；
     * 1→2：核验笔填全（填全一刻自动起公示钟）；
     * 2→3：公示够钟（已连着公示的钟点数与判定同一次算出）。
     */
    private boolean stageGuardPassed(TSaltRecordFlow r, int stage, Date now) {
        if (stage == 1) {
            int required = r.getCheckRequired() == null ? 0 : r.getCheckRequired();
            Integer filled = this.saltRecordCheckMapper.selectCount(new QueryWrapper<TSaltRecordCheck>()
                    .eq("flow_id", r.getId()).eq("filled", 1).eq("del_flag", 0));
            if (filled == null || filled.intValue() < required) {
                return false;
            }
            if (r.getPublicityStart() == null) {
                // 核验笔早已填全但钟未起（存量兼容）：此刻补起钟
                UpdateWrapper<TSaltRecordFlow> uw = new UpdateWrapper<TSaltRecordFlow>()
                        .eq("id", r.getId()).isNull("publicity_start");
                uw.set("publicity_start", now);
                uw.set("publicity_done_hours", BigDecimal.ZERO);
                this.saltRecordFlowMapper.update(null, uw);
                r.setPublicityStart(now);
            }
            return true;
        }
        if (stage == 2) {
            BigDecimal need = r.getPublicityHours() == null ? BigDecimal.ZERO : r.getPublicityHours();
            if (r.getPublicityStart() == null) {
                return need.compareTo(BigDecimal.ZERO) <= 0;
            }
            return hoursBetween(r.getPublicityStart(), now).compareTo(need) >= 0;
        }
        return true;
    }

    /** 该档这一趟是第几趟：到达段（受理/推进/回退）数 + 1，退档后重走另起一趟 */
    private int nextWalk(Long flowId, int stageNo) {
        Integer arrivals = this.saltRecordNoteMapper.selectCount(new QueryWrapper<TSaltRecordNote>()
                .eq("flow_id", flowId).eq("stage_no", stageNo)
                .in("action", ACTION_ACCEPT, ACTION_ADVANCE, ACTION_ROLLBACK));
        return (arrivals == null ? 0 : arrivals.intValue()) + 1;
    }

    /** 落一段流转段：段次本单内连续递增；同秒两笔各记各的（event_ms 相同也保留两行） */
    private void writeNote(Long flowId, int stageNo, int walkNo, String action, String said) {
        Integer cnt = this.saltRecordNoteMapper.selectCount(new QueryWrapper<TSaltRecordNote>()
                .eq("flow_id", flowId));
        TSaltRecordNote n = new TSaltRecordNote();
        n.setFlowId(flowId);
        n.setStageNo(stageNo);
        n.setWalkNo(walkNo);
        n.setSeq((cnt == null ? 0 : cnt.intValue()) + 1);
        n.setAction(action);
        n.setSaid(said);
        n.setEventMs(System.currentTimeMillis());
        n.setDelFlag(0);
        this.saltRecordNoteMapper.insert(n);
    }

    /** 未删除的单；已逻辑删除的视同不存在 */
    private TSaltRecordFlow loadOpen(Long id) {
        if (id == null) {
            return null;
        }
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag().intValue() == 1)) {
            return null;
        }
        return r;
    }

    /** 封口：已封卷落档(2)/收口止住(3) 改不动/删不掉/挪不了 */
    private boolean isSealed(TSaltRecordFlow r) {
        return r.getStatus() != null
                && (r.getStatus().intValue() == STATUS_SEALED || r.getStatus().intValue() == STATUS_CLOSED);
    }

    /** 一企一活单：在办/止住的活单占用键 */
    private TSaltRecordFlow findActiveByEnt(String entNo) {
        return this.saltRecordFlowMapper.selectOne(new QueryWrapper<TSaltRecordFlow>()
                .eq("ent_no", entNo).in("status", STATUS_ACTIVE, STATUS_CLOSED).eq("del_flag", 0)
                .last("limit 1"));
    }

    /** 联动：企业在册（未摘牌、未删除）才算数 */
    private boolean entInRank(String entNo) {
        if (isBlank(entNo)) {
            return false;
        }
        TSaltEnt ent = this.saltEntMapper.selectOne(new QueryWrapper<TSaltEnt>()
                .eq("site_no", entNo).eq("del_flag", 0));
        return ent != null && ent.getStatus() != null && ent.getStatus().intValue() == 0;
    }

    /** 两段时刻之间连着的钟点数（2位小数） */
    private static BigDecimal hoursBetween(Date from, Date to) {
        long ms = to.getTime() - from.getTime();
        if (ms <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(ms).divide(BigDecimal.valueOf(3600000L), 2, RoundingMode.HALF_UP);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
