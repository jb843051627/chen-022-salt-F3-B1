package com.fc.v2.service.impl;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TSaltLinkPageMapper;
import com.fc.v2.mapper.auto.TSaltEntMapper;
import com.fc.v2.model.auto.TSaltLinkPage;
import com.fc.v2.model.auto.TSaltEnt;
import com.fc.v2.service.ITSaltLinkPageService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 品种挂接页Service业务层处理
 *
 * <p>与总名录的联动口径（与 doc/schema/salt.sql 同源）：
 * 企业代号一律以总名录作准——表单递来的 site_no 仅作来意，写入时被总名录值覆盖；
 * 名录已摘牌(status=1)的，新挂与改挂一律拦截；联动只落在写入口，
 * 总名录改动不反向级联改写存量挂接页。</p>
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TSaltLinkPageServiceImpl extends ServiceImpl<TSaltLinkPageMapper, TSaltLinkPage> implements ITSaltLinkPageService {

    /** 挂接进展：0待挂 1已挂讫 2压页（终态，不再往前推） */
    private static final int STATUS_MAX = 2;

    @Autowired
    private TSaltEntMapper saltEntMapper;

    @Override
    public TSaltLinkPage selectTSaltLinkPageById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TSaltLinkPage>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TSaltLinkPage> selectTSaltLinkPageList(Wrapper<TSaltLinkPage> queryWrapper) {
        // 列表与导出同一口径：沿用调用方条件，只统一剔除已删行；分页由调用方处理
        QueryWrapper<TSaltLinkPage> wrapper = queryWrapper instanceof QueryWrapper
                ? (QueryWrapper<TSaltLinkPage>) queryWrapper
                : new QueryWrapper<TSaltLinkPage>();
        wrapper.eq("del_flag", 0);
        return this.baseMapper.selectList(wrapper);
    }

    /**
     * 总名录联动（新增/修改同一处，两头同口径）：
     * 命中在册名录则以名录代号覆盖来值；名录不存在或已摘牌返回 null（调用方按拦截处理）。
     */
    private TSaltEnt checkEntLinkage(TSaltLinkPage record) {
        if (record.getSiteId() == null) {
            return null;
        }
        TSaltEnt refArch = saltEntMapper.selectOne(new QueryWrapper<TSaltEnt>()
                .eq("id", record.getSiteId()).eq("del_flag", 0));
        if (refArch == null) {
            return null;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            // 已摘牌：拦截
            return null;
        }
        // 总名录作准：覆盖表单递来的企业代号
        record.setSiteNo(refArch.getSiteNo());
        return refArch;
    }

    @Override
    public int insertTSaltLinkPage(TSaltLinkPage record) {
        if (record == null) {
            return 0;
        }

        if (checkEntLinkage(record) == null) {
            return 0;
        }
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSaltLinkPage>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTSaltLinkPage(TSaltLinkPage record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSaltLinkPage>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        // 联动另一头：改挂同样过总名录校验，企业代号以名录作准
        if (record.getSiteId() != null && checkEntLinkage(record) == null) {
            return 0;
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TSaltLinkPage>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int advanceTSaltLinkPage(Long id) {
        if (id == null) {
            return 0;
        }
        TSaltLinkPage cur = this.baseMapper.selectById(id);
        if (cur == null || cur.getDelFlag() == null || cur.getDelFlag().intValue() == 1) {
            return 0;
        }
        // 存量兼容：状态为空按待挂(0)计
        int st = cur.getStatus() == null ? 0 : cur.getStatus().intValue();
        if (st >= STATUS_MAX) {
            // 跳态守卫：压页为终态，不再往前推
            return 0;
        }
        int next = st + 1;
        UpdateWrapper<TSaltLinkPage> uw = new UpdateWrapper<TSaltLinkPage>()
                .eq("id", id).eq("del_flag", 0);
        uw.set("status", next);
        // 推进生成核验串，走过哪态留哪态的凭据
        uw.set("content", "CHK-" + id + "-" + next);
        uw.set("update_time", new Date());
        return this.baseMapper.update(null, uw);
    }

    @Override
    public int revertTSaltLinkPage(Long id) {
        if (id == null) {
            return 0;
        }
        TSaltLinkPage cur = this.baseMapper.selectById(id);
        if (cur == null || cur.getDelFlag() == null || cur.getDelFlag().intValue() == 1) {
            return 0;
        }
        int st = cur.getStatus() == null ? 0 : cur.getStatus().intValue();
        if (st <= 0) {
            // 退回范围守卫：待挂已到底，无可退
            return 0;
        }
        int next = st - 1;
        UpdateWrapper<TSaltLinkPage> uw = new UpdateWrapper<TSaltLinkPage>()
                .eq("id", id).eq("del_flag", 0);
        uw.set("status", next);
        // 旧记录沉底：核验串留档，不随退回抹掉
        uw.set("update_time", new Date());
        return this.baseMapper.update(null, uw);
    }

    @Override
    public int deleteTSaltLinkPageByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTSaltLinkPageById(Long id) {
        return this.baseMapper.deleteById(id);
    }
}
