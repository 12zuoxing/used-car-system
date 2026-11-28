package cn.edu.usedcar.entity; // 定义持久层使用的表实体。

import java.time.LocalDateTime; // 与业务时区的数据库时间映射。
import cn.edu.usedcar.model.AppointmentStatus; // 将数据库状态映射成枚举。
import com.baomidou.mybatisplus.annotation.FieldStrategy; // 显式处理可清除的经办说明。
import com.baomidou.mybatisplus.annotation.IdType; // 选择数据库的自增主键策略。
import com.baomidou.mybatisplus.annotation.TableId; // 标注实体主键。
import com.baomidou.mybatisplus.annotation.TableField; // 配置字段更新规则。
import com.baomidou.mybatisplus.annotation.TableName; // 绑定数据库表。

/** 看车预约表实体，供 MyBatis-Plus 读写现有表。
 * @author pangzhihao
 */
@TableName("appointment") // 指定对应的数据表。
public class AppointmentEntity {
    @TableId(type = IdType.AUTO) // 让数据库分配新记录的编号。
    private Long id; // 预约记录的自增主键。
    private Long vehicleId; // 买家希望查看的车辆编号。
    private Long buyerId; // 提交预约的买家编号。
    private Long handlerId; // 最后处理预约的工作人员编号。
    private LocalDateTime visitAt; // 买家预计到访的北京时间。
    private String note; // 买家填写的看车需求。
    private AppointmentStatus status; // 当前预约处理状态。
    @TableField(updateStrategy = FieldStrategy.ALWAYS) // 后续经办留空时清除上一条说明。
    private String handlingNote; // 工作人员留下的处理说明。
    private LocalDateTime createdAt; // 买家首次提交预约的时间。

    public Long getId() {
        return id; // 返回预约记录的自增主键。
    }

    public void setId(Long id) {
        this.id = id; // 写入预约记录的自增主键。
    }

    public Long getVehicleId() {
        return vehicleId; // 返回买家希望查看的车辆编号。
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId; // 写入买家希望查看的车辆编号。
    }

    public Long getBuyerId() {
        return buyerId; // 返回提交预约的买家编号。
    }

    public void setBuyerId(Long buyerId) {
        this.buyerId = buyerId; // 写入提交预约的买家编号。
    }

    public Long getHandlerId() {
        return handlerId; // 返回最后处理预约的工作人员编号。
    }

    public void setHandlerId(Long handlerId) {
        this.handlerId = handlerId; // 写入最后处理预约的工作人员编号。
    }

    public LocalDateTime getVisitAt() {
        return visitAt; // 返回买家预计到访的北京时间。
    }

    public void setVisitAt(LocalDateTime visitAt) {
        this.visitAt = visitAt; // 写入买家预计到访的北京时间。
    }

    public String getNote() {
        return note; // 返回买家填写的看车需求。
    }

    public void setNote(String note) {
        this.note = note; // 写入买家填写的看车需求。
    }

    public AppointmentStatus getStatus() {
        return status; // 返回当前预约处理状态。
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status; // 写入当前预约处理状态。
    }

    public String getHandlingNote() {
        return handlingNote; // 返回工作人员留下的处理说明。
    }

    public void setHandlingNote(String handlingNote) {
        this.handlingNote = handlingNote; // 写入工作人员留下的处理说明。
    }

    public LocalDateTime getCreatedAt() {
        return createdAt; // 返回买家首次提交预约的时间。
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt; // 写入买家首次提交预约的时间。
    }
}
