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
 * 跨省经营备案单流转段记载对象 t_salt_record_note
 * 每趟每进/退一档落一段；退档后重走另起一趟，旧段沉底保留不删；
 * 同一步第二遍空递不产生新段；同秒两笔各记各的。
 *
 * @author fuce
 * @date 2026-10-04
 */
@TableName("t_salt_record_note")
@ApiModel(value = "TSaltRecordNote", description = "跨省经营备案单流转段记载")
public class TSaltRecordNote implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属备案单 */
    @TableField("flow_id")
    @ApiModelProperty(value = "所属备案单")
    private Long flowId;

    /** 这一段对的步次 0..3；收口段记止住时所在档；4=落档封存段 */
    @TableField("stage_no")
    @ApiModelProperty(value = "这一段对的步次 0..3；收口段记止住时所在档；4=落档封存段")
    private Integer stageNo;

    /** 这一档走到第几趟（头趟1，退回复走累加） */
    @TableField("walk_no")
    @ApiModelProperty(value = "这一档走到第几趟（头趟1，退回复走累加）")
    private Integer walkNo;

    /** 段次（本单内连续递增） */
    @TableField("seq")
    @ApiModelProperty(value = "段次（本单内连续递增）")
    private Integer seq;

    /** 这一段的动作 accept/advance/rollback/close/seal */
    @TableField("action")
    @ApiModelProperty(value = "这一段的动作 accept/advance/rollback/close/seal")
    private String action;

    /** 哪一步写了什么话 */
    @TableField("said")
    @ApiModelProperty(value = "哪一步写了什么话")
    private String said;

    /** 落在哪一刻（毫秒；同秒两笔不并成一笔也不少记一笔） */
    @TableField("event_ms")
    @ApiModelProperty(value = "落在哪一刻（毫秒）")
    private Long eventMs;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间（该段落下那一刻） */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间（该段落下那一刻）")
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

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public Integer getStageNo() {
        return stageNo;
    }

    public void setStageNo(Integer stageNo) {
        this.stageNo = stageNo;
    }

    public Integer getWalkNo() {
        return walkNo;
    }

    public void setWalkNo(Integer walkNo) {
        this.walkNo = walkNo;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getSaid() {
        return said;
    }

    public void setSaid(String said) {
        this.said = said;
    }

    public Long getEventMs() {
        return eventMs;
    }

    public void setEventMs(Long eventMs) {
        this.eventMs = eventMs;
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
