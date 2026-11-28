package cn.edu.usedcar.model; // 定义工单表和 API 共用的状态。

/** 工单从申请到完工或取消的办理状态。
 * @author pangzhihao
 */
public enum RepairStatus {
    PENDING, // 申请已提交，等待工作人员受理。
    PROCESSING, // 工作人员已接单并开始安排。
    COMPLETED, // 工作人员登记了完工结果。
    CANCELLED // 申请人撤回或工作人员登记取消。
}
