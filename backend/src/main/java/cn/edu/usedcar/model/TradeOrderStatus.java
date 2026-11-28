package cn.edu.usedcar.model; // 使接口、服务和数据库共享固定的订单状态。

/** 订单流转状态，对应 trade_order 表的状态约束。
 * @author pangzhihao
 */
public enum TradeOrderStatus {
    LOCKED, // 下单成功且车辆已锁定，等待买家模拟支付。
    PAID, // 买家已完成模拟支付，等待后台确认过户。
    COMPLETED, // 工作人员完成过户并登记成交。
    CANCELLED // 买卖一方在付款前取消，保留订单历史。
}
