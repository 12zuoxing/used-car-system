/**
 * 报告审核状态在卖家详情和后台列表统一展示。
 * @author pangzhihao
 */
export const reportStatuses = {
  PENDING: { label: '待审核', severity: 'warn' },
  APPROVED: { label: '已通过', severity: 'success' },
  REJECTED: { label: '已退回', severity: 'danger' },
}
