package com.fc.v2.controller.admin;

import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TSaltRecordFlow;
import com.fc.v2.service.ITSaltRecordFlowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 跨省经营备案单 Controller（state-machine 形状：流转入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "跨省经营备案单")
@Controller
@RequestMapping("/saltRecordFlow")
public class SaltRecordFlowController extends BaseController {

    private final String prefix = "admin/saltRecordFlow";

    @Autowired
    private ITSaltRecordFlowService saltRecordFlowService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("saltRecordFlow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "跨省经营备案单流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("saltRecordFlow:list")
    @ResponseBody
    public ResultTable list(TSaltRecordFlow record) {
        QueryWrapper<TSaltRecordFlow> queryWrapper = new QueryWrapper<TSaltRecordFlow>();
        startPage();
        com.github.pagehelper.PageInfo<TSaltRecordFlow> page =
                new com.github.pagehelper.PageInfo<TSaltRecordFlow>(saltRecordFlowService.selectTSaltRecordFlowList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "跨省经营备案单立案", action = "accept")
    @ApiOperation(value = "立案递单", notes = "四样齐+总名录在册才收；一企一活单，重复递单幂等回在办单")
    @PostMapping("/accept")
    @RequiresPermissions("saltRecordFlow:accept")
    @ResponseBody
    public AjaxResult accept(TSaltRecordFlow record) {
        return toAjax(saltRecordFlowService.accept(record) != null ? 1 : 0);
    }

    @Log(title = "跨省经营备案单推进", action = "advance")
    @ApiOperation(value = "推进一档", notes = "推进一档")
    @PostMapping("/advance")
    @RequiresPermissions("saltRecordFlow:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark) {
        return toAjax(saltRecordFlowService.advance(id, remark) != null ? 1 : 0);
    }

    @Log(title = "跨省经营备案单回退", action = "rollback")
    @ApiOperation(value = "回退一档", notes = "回退一档")
    @PostMapping("/rollback")
    @RequiresPermissions("saltRecordFlow:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        return toAjax(saltRecordFlowService.rollback(id, remark) != null ? 1 : 0);
    }

    @Log(title = "跨省经营备案单核验笔", action = "check")
    @ApiOperation(value = "填一笔核验笔", notes = "材料核验档填核验笔，填全一刻自动起公示钟")
    @PostMapping("/check")
    @RequiresPermissions("saltRecordFlow:advance")
    @ResponseBody
    public AjaxResult check(Long flowId, String itemNo, String itemName) {
        return toAjax(saltRecordFlowService.fillCheck(flowId, itemNo, itemName) ? 1 : 0);
    }

    @Log(title = "跨省经营备案单旧证缴回", action = "certBack")
    @ApiOperation(value = "旧证缴回回执", notes = "缴回封卷档前把旧证缴回回执落上")
    @PostMapping("/certBack")
    @RequiresPermissions("saltRecordFlow:advance")
    @ResponseBody
    public AjaxResult certBack(Long id) {
        return toAjax(saltRecordFlowService.certBack(id) ? 1 : 0);
    }

    @Log(title = "跨省经营备案单收口", action = "close")
    @ApiOperation(value = "收口", notes = "N不必备案 S就地止住；收口后单即封口")
    @PostMapping("/close")
    @RequiresPermissions("saltRecordFlow:close")
    @ResponseBody
    public AjaxResult close(Long id, String closeType, String remark) {
        return toAjax(saltRecordFlowService.close(id, closeType, remark) != null ? 1 : 0);
    }

    @Log(title = "跨省经营备案单倒推复核", action = "audit")
    @ApiOperation(value = "倒推复核", notes = "段流倒着数回递单受理，与单的步次/情形对账")
    @GetMapping("/audit")
    @RequiresPermissions("saltRecordFlow:audit")
    @ResponseBody
    public AjaxResult audit(Long id) {
        Map<String, Object> result = saltRecordFlowService.audit(id);
        return result == null ? error() : retobject(0, result);
    }
}
