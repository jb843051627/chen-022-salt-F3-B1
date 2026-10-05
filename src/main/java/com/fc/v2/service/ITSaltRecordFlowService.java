package com.fc.v2.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TSaltRecordFlow;

/**
 * 跨省经营备案单 Service接口（state-machine 形状：单据流转，不提供增删改查全套）
 *
 * 口径（与 doc/schema/salt-spec.md 同源）：
 *  - 步次只以系统按已过档次数出来的 stage 为准，表单递来的 req_stage 仅作来意，入库/回读都不看它；
 *  - 已封卷落档(2)与收口止住(3)都封口：改不动/删不掉/挪不了；
 *  - 一企一活单：同一企业在办/止住只有一张，重复递单幂等回在办单，不新建；
 *  - 每趟每进/退一档落一段流转段，退档后重走另起一趟，旧段沉底保留不删。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITSaltRecordFlowService {

    /** 按主键查询单据 */
    TSaltRecordFlow selectTSaltRecordFlowById(Long id);

    /** 列表查询（流转台账，只看正常单） */
    List<TSaltRecordFlow> selectTSaltRecordFlowList(QueryWrapper<TSaltRecordFlow> queryWrapper);

    /**
     * 立案递单：四样齐（企业代号/起运/到货/县域个数）+ 总名录在册才收；
     * 该企业在办/止住已有单的，幂等回那张在办单，不新建、不另起段。
     * 被拒（四样不齐/企业不在册）返回 null。
     */
    TSaltRecordFlow accept(TSaltRecordFlow form);

    /** 推进一档：返回更新后的单据；被封口/本档未走完（核验笔、公示钟、缴回回执）被拒返回 null */
    TSaltRecordFlow advance(Long id, String remark);

    /** 回退一档：返回更新后的单据；被封口或已在起点被拒返回 null（回退打断公示钟即清零） */
    TSaltRecordFlow rollback(Long id, String remark);

    /** 材料核验档填一笔核验笔；填全那一刻自动起公示钟。被拒（封口/不在材料核验档）返回 false */
    boolean fillCheck(Long flowId, String itemNo, String itemName);

    /** 旧证缴回回执落一笔。被拒（封口）返回 false */
    boolean certBack(Long id);

    /** 收口：closeType N不必备案 S就地止住；落档封存不留收口说法。被拒（封口/说法不对）返回 null */
    TSaltRecordFlow close(Long id, String closeType, String remark);

    /** 倒推复核：段流倒着数回递单受理，与单的 stage/status 对账；单不存在返回 null */
    Map<String, Object> audit(Long id);

    /** 修改内容：已办结不得再改；同档第二遍不另起一行（改当前段留下的话）。被拒返回 false */
    boolean updateContent(Long id, String remark);

    /** 删除单据：已办结（封卷/止住）不得删除；未封口的单物理删除并释放一企一活单键。被拒返回 false */
    boolean remove(Long id);
}
