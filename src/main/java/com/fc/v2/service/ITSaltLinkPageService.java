package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TSaltLinkPage;

import java.util.List;

/**
 * 企业品种挂接簿页 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITSaltLinkPageService {

    /** 按主键查询 */
    TSaltLinkPage selectTSaltLinkPageById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TSaltLinkPage> selectTSaltLinkPageList(Wrapper<TSaltLinkPage> queryWrapper);

    /** 新增 */
    int insertTSaltLinkPage(TSaltLinkPage record);

    /** 修改 */
    int updateTSaltLinkPage(TSaltLinkPage record);

    /** 批量删除 */
    int deleteTSaltLinkPageByIds(String ids);

    /** 推进一态（待核对→已核对→已冻住） */
    int advanceTSaltLinkPage(Long id);

    /** 退回一态（已核对→待核对；旧记录沉底） */
    int revertTSaltLinkPage(Long id);

    /** 按主键删除 */
    int deleteTSaltLinkPageById(Long id);
}
