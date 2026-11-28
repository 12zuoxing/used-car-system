/**
 * 贷款状态和后台操作统一使用相同的业务名称。
 * @author pangzhihao
 */
export const loanStatuses = {
  PENDING: { label: '待处理', severity: 'warn' }, // 买家已提交，等待工作人员读取。
  SUPPLEMENT: { label: '待补充资料', severity: 'warn' }, // 申请人需要根据说明修改。
  PROCESSING: { label: '办理中', severity: 'info' }, // 工作人员正在办理。
  FINISHED: { label: '已办理完成', severity: 'success' }, // 可以查看人工办理结果。
  CANCELLED: { label: '已关闭', severity: 'secondary' }, // 已因人工处理或订单取消结束。
}

export const loanActions = {
  PENDING: { SUPPLEMENT: '退回补充', PROCESSING: '开始办理', CANCELLED: '关闭申请' }, // 待处理阶段可以初审或关闭。
  PROCESSING: { SUPPLEMENT: '退回补充', FINISHED: '登记完成', CANCELLED: '关闭申请' }, // 办理中可以完成、补件或关闭。
  SUPPLEMENT: { CANCELLED: '关闭申请' }, // 申请人重新提交前，工作人员只能关闭。
}
