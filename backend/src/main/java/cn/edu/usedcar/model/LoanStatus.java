package cn.edu.usedcar.model; // 定义贷款领域中稳定的状态枚举。

/** 贷款登记状态，与已有数据表的状态约束一致。
 * @author pangzhihao
 */
public enum LoanStatus {
    PENDING, // 买家提交或补充后，等待工作人员查看。
    SUPPLEMENT, // 工作人员已提出补充资料的要求。
    PROCESSING, // 工作人员正在登记与核对办理信息。
    FINISHED, // 该申请的人工办理已完成。
    CANCELLED // 申请被关闭或关联订单已取消。
}
