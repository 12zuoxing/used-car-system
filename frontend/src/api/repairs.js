/**
 * 工单请求经过项目现有的会话保护、CSRF 和分页参数序列化。
 * @author pangzhihao
 */
import { http } from './http.js' // 共用同源的 HTTP 客户端。
import { withQuery } from './vehicles.js' // 对工单列表的筛选字段统一编码。

const path = (id) => '/repairs/' + encodeURIComponent(String(id)) // 让编号作为单个路径片段。

export const repairApi = {
  create: (body) => http.request('/repairs', { method: 'POST', body }), // 创建售前或售后工单。
  mine: (query) => http.request(withQuery('/repairs', query)), // 读取当前账号的工单历史。
  detail: (id) => http.request(path(id)), // 读取本人授权的工单详情。
  cancel: (id) => http.request(path(id) + '/cancel', { method: 'POST' }), // 取消尚未受理的本人申请。
  queue: (query) => http.request(withQuery('/admin/repairs', query)), // 工作人员按阶段与状态获取队列。
  staffDetail: (id) => http.request('/admin' + path(id)), // 工作人员读取经办所需资料。
  handle: (id, body) => http.request('/admin' + path(id) + '/status', { method: 'POST', body }), // 提交下一状态和办理说明。
}
