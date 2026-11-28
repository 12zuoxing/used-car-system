/**
 * 贷款登记复用项目的同源请求、分页参数和 CSRF 保护。
 * @author pangzhihao
 */
import { http } from './http.js' // 让所有贷款请求使用现有会话。
import { withQuery } from './vehicles.js' // 为列表和补充请求编码查询参数。

const key = (id) => encodeURIComponent(String(id)) // 业务编号只能组成一个路径片段。
const byOrder = (orderId) => '/orders/' + key(orderId) + '/loan' // 基于授权订单查询或创建登记。
const detail = (id) => '/loans/' + key(id) // 根据申请编号访问独立资源。

export const loanApi = {
  forOrder: (id) => http.request(byOrder(id)), // 查询本人订单的原登记，尚未提交时返回空结果。
  create: (id, body) => http.request(byOrder(id), { method: 'POST', body }), // 为订单首次提交贷款登记。
  mine: (query) => http.request(withQuery('/loans', query)), // 筛选和分页读取本人申请。
  supplement: (id, revision, body) => http.request(withQuery(detail(id), { revision }), { method: 'PUT', body }), // 按已读版本更新原申请。
  queue: (query) => http.request(withQuery('/admin/loans', query)), // 由工作人员按状态加载经办列表。
  staffDetail: (id) => http.request('/admin' + detail(id)), // 读取办理所需的详细字段和版本。
  updateStatus: (id, body) => http.request('/admin' + detail(id) + '/status', { method: 'POST', body }), // 提交下一状态与人工说明。
}
