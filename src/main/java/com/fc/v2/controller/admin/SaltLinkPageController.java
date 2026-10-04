package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TSaltLinkPage;
import com.fc.v2.service.ITSaltLinkPageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 企业品种挂接簿页 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "企业品种挂接簿页")
@Controller
@RequestMapping("/SaltLinkPageController")
public class SaltLinkPageController extends BaseController {

    private final String prefix = "admin/saltLinkPage";

    @Autowired
    private ITSaltLinkPageService saltLinkPageService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("salt:saltLinkPage:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "企业品种挂接簿页集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("salt:saltLinkPage:list")
    @ResponseBody
    public ResultTable list(TSaltLinkPage record) {
        QueryWrapper<TSaltLinkPage> queryWrapper = new QueryWrapper<TSaltLinkPage>();
        startPage();
        com.github.pagehelper.PageInfo<TSaltLinkPage> page =
                new com.github.pagehelper.PageInfo<TSaltLinkPage>(saltLinkPageService.selectTSaltLinkPageList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "企业品种挂接簿页新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("salt:saltLinkPage:add")
    @ResponseBody
    public AjaxResult add(TSaltLinkPage record) {
        return toAjax(saltLinkPageService.insertTSaltLinkPage(record));
    }

    @Log(title = "企业品种挂接簿页修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("salt:saltLinkPage:edit")
    @ResponseBody
    public AjaxResult editSave(TSaltLinkPage record) {
        return toAjax(saltLinkPageService.updateTSaltLinkPage(record));
    }

    @Log(title = "企业品种挂接簿页删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("salt:saltLinkPage:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(saltLinkPageService.deleteTSaltLinkPageByIds(ids));
    }
}
