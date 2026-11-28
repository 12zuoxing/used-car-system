/**
 * 代销和预约统一展示北京时间及业务状态。
 * @author pangzhihao
 */
const businessClock = new Intl.DateTimeFormat('sv-SE', { // 使用稳定的年-月-日顺序生成本地时间控件值。
  timeZone: 'Asia/Shanghai', // 浏览器位于任意时区时，依然采用北京时间。
  dateStyle: 'short', // 输出完整年月日。
  timeStyle: 'short', // 时间控件使用小时和分钟。
})

export function businessInputTime(date = new Date()) {
  return businessClock.format(date).replace(' ', 'T') // 转换为 datetime-local 需要的日期分隔符。
}

export function timeLabel(value) {
  return value ? value.slice(0, 16).replace('T', ' ') : '—' // 显示后端的北京时间，避免被浏览器再次转换。
}

export const consignmentStatuses = {
  PENDING: { label: '待审核', severity: 'warn' }, // 表示工作人员尚未处理。
  APPROVED: { label: '已通过', severity: 'success' }, // 通过后结合生效时间显示实际状态。
  REJECTED: { label: '已退回', severity: 'danger' }, // 卖家可查看说明。
  WITHDRAWN: { label: '已撤回', severity: 'secondary' }, // 卖家已主动结束委托。
  EXPIRED: { label: '已到期', severity: 'secondary' }, // 截止时间已到。
  COMPLETED: { label: '已完成', severity: 'info' }, // 对应代销交易已结案。
}

export const appointmentStatuses = {
  PENDING: { label: '待处理', severity: 'warn' }, // 正在等待工作人员联系。
  CONFIRMED: { label: '已确认', severity: 'success' }, // 到访安排已经确认。
  REJECTED: { label: '已退回', severity: 'danger' }, // 买家可查看退回原因。
  CANCELLED: { label: '已取消', severity: 'secondary' }, // 本次看车安排已取消。
  COMPLETED: { label: '已完成', severity: 'info' }, // 工作人员已经完成登记。
}

export function consignmentLabel(item) {
  if (item.status === 'APPROVED') return item.active ? '已生效' : '已通过 · 未开始' // 区分审核通过和已经生效。
  return consignmentStatuses[item.status].label // 其余状态直接使用统一标签。
}
