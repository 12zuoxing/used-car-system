package cn.edu.usedcar.model; // 业务层使用固定的预约状态。

/** 看车预约的处理状态，与数据库字段的取值一致。
 * @author pangzhihao
 */
public enum AppointmentStatus {
    PENDING, // 买家提交，等待工作人员处理。
    CONFIRMED, // 工作人员已确认到访安排。
    REJECTED, // 工作人员退回预约。
    CANCELLED, // 买家或工作人员取消预约。
    COMPLETED // 工作人员登记已完成看车。
}
