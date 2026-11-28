package cn.edu.usedcar.entity; // 定义 MyBatis-Plus 使用的工单实体。

import java.time.LocalDateTime; // 保存业务本地日期和时间。
import cn.edu.usedcar.model.RepairStage; // 限定持久化工单阶段。
import cn.edu.usedcar.model.RepairStatus; // 限定持久化工单状态。
import com.baomidou.mybatisplus.annotation.FieldStrategy; // 允许在更新时清空可选字段。
import com.baomidou.mybatisplus.annotation.IdType; // 选择工单主键的生成方式。
import com.baomidou.mybatisplus.annotation.TableField; // 声明字段写入策略。
import com.baomidou.mybatisplus.annotation.TableId; // 配置实体主键。
import com.baomidou.mybatisplus.annotation.TableName; // 绑定已有的数据表。

/** 售前与售后复用同一张工单表。
 * @author pangzhihao
 */
@TableName("repair_order") // 绑定数据库已定义的工单表。
public class RepairOrderEntity {
    @TableId(type = IdType.AUTO) // 由数据库分配工单编号。
    private Long id; // 保存工单自增编号。
    private Long vehicleId; // 保存工单对应的车辆编号。
    private Long tradeOrderId; // 保存售后工单的成交订单编号。
    private Long applicantId; // 保存申请人账号编号。
    private Long handlerId; // 保存最近办理工单的工作人员编号。
    private RepairStage stage; // 保存售前整备或售后送修的业务阶段。
    private LocalDateTime visitAt; // 保存预计到店的北京时间。
    private String description; // 保存申请人提交的整备要求或故障描述。
    private RepairStatus status; // 保存工单当前办理状态。
    @TableField(updateStrategy = FieldStrategy.ALWAYS) // 状态修改时允许保存为空值。
    private String resultNote; // 保存工作人员说明或申请人撤回原因。
    @TableField(updateStrategy = FieldStrategy.ALWAYS) // 状态修改时允许保存为空值。
    private LocalDateTime completedAt; // 保存确认完工的北京时间。
    private LocalDateTime createdAt; // 保存工单提交时间。

    public Long getId() {
        return id; // 返回工单自增编号。
    }

    public void setId(Long id) {
        this.id = id; // 写入工单自增编号。
    }

    public Long getVehicleId() {
        return vehicleId; // 返回工单对应的车辆编号。
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId; // 写入工单对应的车辆编号。
    }

    public Long getTradeOrderId() {
        return tradeOrderId; // 返回售后工单的成交订单编号。
    }

    public void setTradeOrderId(Long tradeOrderId) {
        this.tradeOrderId = tradeOrderId; // 写入售后工单的成交订单编号。
    }

    public Long getApplicantId() {
        return applicantId; // 返回申请人账号编号。
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId; // 写入申请人账号编号。
    }

    public Long getHandlerId() {
        return handlerId; // 返回最近办理工单的工作人员编号。
    }

    public void setHandlerId(Long handlerId) {
        this.handlerId = handlerId; // 写入最近办理工单的工作人员编号。
    }

    public RepairStage getStage() {
        return stage; // 返回售前整备或售后送修的业务阶段。
    }

    public void setStage(RepairStage stage) {
        this.stage = stage; // 写入售前整备或售后送修的业务阶段。
    }

    public LocalDateTime getVisitAt() {
        return visitAt; // 返回预计到店的北京时间。
    }

    public void setVisitAt(LocalDateTime visitAt) {
        this.visitAt = visitAt; // 写入预计到店的北京时间。
    }

    public String getDescription() {
        return description; // 返回申请人提交的整备要求或故障描述。
    }

    public void setDescription(String description) {
        this.description = description; // 写入申请人提交的整备要求或故障描述。
    }

    public RepairStatus getStatus() {
        return status; // 返回工单当前办理状态。
    }

    public void setStatus(RepairStatus status) {
        this.status = status; // 写入工单当前办理状态。
    }

    public String getResultNote() {
        return resultNote; // 返回工作人员说明或申请人撤回原因。
    }

    public void setResultNote(String resultNote) {
        this.resultNote = resultNote; // 写入工作人员说明或申请人撤回原因。
    }

    public LocalDateTime getCompletedAt() {
        return completedAt; // 返回确认完工的北京时间。
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt; // 写入确认完工的北京时间。
    }

    public LocalDateTime getCreatedAt() {
        return createdAt; // 返回工单提交时间。
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt; // 写入工单提交时间。
    }
}
