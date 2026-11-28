/**
 * 订单接口统一复用会话 Cookie、CSRF 令牌和分页查询。
 * @author pangzhihao
 */
import { http } from './http.js' // 使用现有同源 HTTP 客户端。
import { withQuery } from './vehicles.js' // 将非空的分页筛选序列化到 URL。

const path = (id) => '/orders/' + encodeURIComponent(String(id)) // 将业务编号放入单个路径片段。
const post = (url, body) => http.request(url, { method: 'POST', body }) // 复用订单状态变更请求。

export const orderApi = {
  create: (vehicleId, key) => http.request('/orders', {
    method: 'POST', // 创建交易并占用当前车源。
    body: { vehicleId }, // 报价与交易双方由后端读取，只发送车辆编号。
    headers: { 'Idempotency-Key': key }, // 同一请求和所有重试使用相同的标识。
  }),
  mine: (query) => http.request(withQuery('/orders', query)), // 读取自己的买入和卖出历史。
  detail: (id) => http.request(path(id)), // 从个人交易范围查询订单。
  cancel: (id) => post(path(id) + '/cancel'), // 撤销未支付的订单。
  pay: (id) => post(path(id) + '/pay'), // 触发演示用模拟支付。
  queue: (query) => http.request(withQuery('/admin/orders', query)), // 工作人员查询不同状态的订单。
  staffDetail: (id) => http.request('/admin' + path(id)), // 读取后台可见的订单详情。
  complete: (id, note) => post('/admin' + path(id) + '/complete', { note }), // 附上过户说明并登记完成。
  cancelPaid: (id, note) => post('/admin' + path(id) + '/cancel', { note }), // 由工作人员提交取消原因并释放车源。
}
