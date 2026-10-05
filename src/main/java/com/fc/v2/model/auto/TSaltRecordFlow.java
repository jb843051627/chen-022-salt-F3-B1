package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 跨省经营备案单对象 t_salt_record_flow
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_salt_record_flow")
@ApiModel(value = "TSaltRecordFlow", description = "跨省经营备案单")
public class TSaltRecordFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 跨省经营备案单代号 */
    @TableField("biz_no")
    @ApiModelProperty(value = "跨省经营备案单代号")
    private String bizNo;

    /** 企业代号（四样齐之一） */
    @TableField("ent_no")
    @ApiModelProperty(value = "企业代号（四样齐之一）")
    private String entNo;

    /** 承运那一路起运那一刻（四样齐之二） */
    @TableField("depart_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "承运那一路起运那一刻（四样齐之二）")
    private Date departAt;

    /** 到货那一刻（四样齐之三） */
    @TableField("arrive_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "到货那一刻（四样齐之三）")
    private Date arriveAt;

    /** 拟销到的县域个数（四样齐之四） */
    @TableField("region_count")
    @ApiModelProperty(value = "拟销到的县域个数（四样齐之四）")
    private Integer regionCount;

    /** 材料核验档应填全的核验笔数 */
    @TableField("check_required")
    @ApiModelProperty(value = "材料核验档应填全的核验笔数")
    private Integer checkRequired;

    /** 材料齐那一刻起钟（回退打断即清零） */
    @TableField("publicity_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "材料齐那一刻起钟（回退打断即清零）")
    private Date publicityStart;

    /** 当地定的应连着公示的钟点数 */
    @TableField("publicity_hours")
    @ApiModelProperty(value = "当地定的应连着公示的钟点数")
    private java.math.BigDecimal publicityHours;

    /** 已连着公示的钟点数（与起钟一刻同一次算出） */
    @TableField("publicity_done_hours")
    @ApiModelProperty(value = "已连着公示的钟点数（与起钟一刻同一次算出）")
    private java.math.BigDecimal publicityDoneHours;

    /** 旧证缴回回执落没落 0没落 1已落 */
    @TableField("cert_returned")
    @ApiModelProperty(value = "旧证缴回回执落没落 0没落 1已落")
    private Integer certReturned;

    /** 收口说法 N不必备案 S就地止住；落档封存则留空 */
    @TableField("close_type")
    @ApiModelProperty(value = "收口说法 N不必备案 S就地止住；落档封存则留空")
    private String closeType;

    /** 当前步次 0..3（递单受理/材料核验/生效确认/缴回封卷） */
    @TableField("stage")
    @ApiModelProperty(value = "当前步次 0..3（递单受理/材料核验/生效确认/缴回封卷）")
    private Integer stage;

    /** 备案单情形 0未起 1在办 2已封卷落档 3收口止住；落档与止住都封口 */
    @TableField("status")
    @ApiModelProperty(value = "备案单情形 0未起 1在办 2已封卷落档 3收口止住")
    private Integer status;

    /** 一段一记 */
    @TableField("content")
    @ApiModelProperty(value = "一段一记")
    private String content;

    /** 最近一次挪步动作 */
    @TableField("last_action")
    @ApiModelProperty(value = "最近一次挪步动作")
    private String lastAction;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getEntNo() {
        return entNo;
    }

    public void setEntNo(String entNo) {
        this.entNo = entNo;
    }

    public Date getDepartAt() {
        return departAt;
    }

    public void setDepartAt(Date departAt) {
        this.departAt = departAt;
    }

    public Date getArriveAt() {
        return arriveAt;
    }

    public void setArriveAt(Date arriveAt) {
        this.arriveAt = arriveAt;
    }

    public Integer getRegionCount() {
        return regionCount;
    }

    public void setRegionCount(Integer regionCount) {
        this.regionCount = regionCount;
    }

    public Integer getCheckRequired() {
        return checkRequired;
    }

    public void setCheckRequired(Integer checkRequired) {
        this.checkRequired = checkRequired;
    }

    public Date getPublicityStart() {
        return publicityStart;
    }

    public void setPublicityStart(Date publicityStart) {
        this.publicityStart = publicityStart;
    }

    public java.math.BigDecimal getPublicityHours() {
        return publicityHours;
    }

    public void setPublicityHours(java.math.BigDecimal publicityHours) {
        this.publicityHours = publicityHours;
    }

    public java.math.BigDecimal getPublicityDoneHours() {
        return publicityDoneHours;
    }

    public void setPublicityDoneHours(java.math.BigDecimal publicityDoneHours) {
        this.publicityDoneHours = publicityDoneHours;
    }

    public Integer getCertReturned() {
        return certReturned;
    }

    public void setCertReturned(Integer certReturned) {
        this.certReturned = certReturned;
    }

    public String getCloseType() {
        return closeType;
    }

    public void setCloseType(String closeType) {
        this.closeType = closeType;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLastAction() {
        return lastAction;
    }

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
