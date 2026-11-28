package cn.edu.usedcar.mapper; // 负责后台工作台统计查询。

import org.apache.ibatis.annotations.Select; // 在同一个数据库语句中读取各队列当前计数。

/** 统计条件与已实现的后台列表一致，数据来自原业务表。
 * @author pangzhihao
 */
public interface DashboardMapper {
    // 每列对应一个可直接打开的工作队列状态，所有计数来自同一条 SQL。
    @Select("""
            SELECT
                (SELECT COUNT(*) FROM vehicle WHERE status = 'PENDING') AS pending_vehicles,
                (SELECT COUNT(*) FROM trade_order WHERE status = 'LOCKED') AS locked_orders,
                (SELECT COUNT(*) FROM trade_order WHERE status = 'PAID') AS paid_orders,
                (SELECT COUNT(*) FROM loan_application WHERE status = 'PENDING') AS pending_loans,
                (SELECT COUNT(*) FROM loan_application WHERE status = 'PROCESSING') AS processing_loans,
                (SELECT COUNT(*) FROM repair_order WHERE status = 'PENDING') AS pending_repairs,
                (SELECT COUNT(*) FROM repair_order WHERE status = 'PROCESSING') AS processing_repairs
            """)
    Counts snapshot(); // 读取服务端实际待办，避免与列表状态规则产生偏差。

    /** 工作台待办拆分到各个可核对的状态。 */
    record Counts(
            long pendingVehicles, // 等待车源审核的车辆数。
            long lockedOrders, // 买家尚待支付的订单数。
            long paidOrders, // 已支付且待过户的订单数。
            long pendingLoans, // 等待经办的贷款数。
            long processingLoans, // 已接单的贷款数。
            long pendingRepairs, // 等待受理的工单数。
            long processingRepairs // 正在处理的工单数。
    ) { }
}
