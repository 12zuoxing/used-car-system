/**
 * 平台统一展示交易角色和各个订单阶段。
 * @author pangzhihao
 */
export const orderStatuses = {
  LOCKED: { label: '待支付', severity: 'warn' }, // 车源由本订单占用，等待买家模拟支付。
  PAID: { label: '待过户确认', severity: 'info' }, // 已模拟支付，等待工作人员办理。
  COMPLETED: { label: '已成交', severity: 'success' }, // 已登记过户，车辆标记已售。
  CANCELLED: { label: '已取消', severity: 'secondary' }, // 交易已终止，保留历史信息。
}

export const orderSides = { ALL: '全部订单', BUY: '买入订单', SELL: '卖出订单' } // 提供个人列表的三种角色筛选。
export const orderSaleMode = (order) => order.consignmentId == null ? '直销' : '代销' // 使用下单时保存的委托关联区分交易方式。
