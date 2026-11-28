/**
 * 售前整备与售后送修共用状态、操作文案和前端入口条件。
 * @author pangzhihao
 */
export const repairStages = {
  PRE_SALE: '售前整备', // 由车主为尚未锁定或售出的车辆发起。
  AFTER_SALE: '售后送修', // 由已成交订单的买方发起。
}

export const repairStatuses = {
  PENDING: { label: '待受理', severity: 'warn' }, // 工单已提交，等待后台接单。
  PROCESSING: { label: '处理中', severity: 'info' }, // 工作人员已受理。
  COMPLETED: { label: '已完工', severity: 'success' }, // 已登记办理结果和完工时间。
  CANCELLED: { label: '已取消', severity: 'secondary' }, // 保留记录以供查询。
}

export const repairActions = {
  PENDING: { PROCESSING: '受理工单', CANCELLED: '取消工单' }, // 待受理记录可接单或取消。
  PROCESSING: { COMPLETED: '登记完工', CANCELLED: '取消工单' }, // 已接单后可登记结果或说明取消。
}

export function canRequestPreSale(vehicle) {
  return Boolean(vehicle && !['LOCKED', 'SOLD'].includes(vehicle.status)) // 隐藏已进入交易和已成交车源的售前入口。
}

export function canRequestAfterSale(order) {
  return order?.side === 'BUY' && order.status === 'COMPLETED' // 仅成交订单买家可选择售后送修。
}
