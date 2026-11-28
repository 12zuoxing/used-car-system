/**
 * 代销和预约接口复用现有的同源会话和 CSRF 请求处理。
 * @author pangzhihao
 */
import { http } from './http.js' // 保持请求通过已有的认证、序列化和错误处理。
import { withQuery } from './vehicles.js' // 复用分页和状态查询参数编码。

const key = (id) => encodeURIComponent(String(id)) // 将业务编号编码为单个路径段。
const vehiclePath = (id) => '/vehicles/' + key(id) // 在明确的车源子路径下发起申请。
const send = (path, body) => http.request(path, { method: 'POST', body }) // 所有写入复用同一提交方式。

export const consignmentApi = {
  mine: (id, query) => http.request(withQuery(vehiclePath(id) + '/consignments', query)), // 读取卖家自己车辆的委托历史。
  create: (id, body) => send(vehiclePath(id) + '/consignments', body), // 绑定车辆和卖家提交委托。
  withdraw: (id) => send('/consignments/' + key(id) + '/withdraw'), // 由车主申请撤回指定委托。
  queue: (query) => http.request(withQuery('/admin/consignments', query)), // 按状态分页读取后台队列。
  review: (id, body) => send('/admin/consignments/' + key(id) + '/review', body), // 提交审核结果与说明。
}

export const appointmentApi = {
  create: (id, body) => send(vehiclePath(id) + '/appointments', body), // 提交目标车辆的预约日期和备注。
  mine: (query) => http.request(withQuery('/appointments', query)), // 查询登录买家自己的预约进度。
  cancel: (id) => send('/appointments/' + key(id) + '/cancel'), // 买家主动取消指定预约。
  queue: (query) => http.request(withQuery('/admin/appointments', query)), // 工作人员查询全部预约。
  handle: (id, body) => send('/admin/appointments/' + key(id) + '/handle', body), // 经办人更新处理结果。
}
