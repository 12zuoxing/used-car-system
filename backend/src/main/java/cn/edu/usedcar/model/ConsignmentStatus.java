package cn.edu.usedcar.model; // 业务层使用固定的委托状态。

/** 代销委托状态，与数据库字段的取值一致。
 * @author pangzhihao
 */
public enum ConsignmentStatus {
    PENDING, // 等待工作人员审核。
    APPROVED, // 已通过审核，是否可用还要检查起止时间。
    REJECTED, // 审核退回并保留原因。
    WITHDRAWN, // 车主主动撤回。
    EXPIRED, // 已到委托截止时间。
    COMPLETED // 对应代销交易已完成。
}
