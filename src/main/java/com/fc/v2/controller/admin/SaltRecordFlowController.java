package com.fc.v2.controller.admin;

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
}
