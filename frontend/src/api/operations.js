/**
 * 站内通知、统计与审计查询使用现有的同源 HTTP 请求和分页格式。
 * @author pangzhihao
 */
import { http } from './http.js' // 复用登录会话和写请求的 CSRF 校验。
import { withQuery } from './vehicles.js' // 对分页和检索条件进行统一 URL 编码。

export const notificationApi = {
  list: (query) => http.request(withQuery('/notifications', query)), // 查询本人通知与全量未读数。
  markRead: (id) => http.request('/notifications/' + encodeURIComponent(String(id)) + '/read', { method: 'POST' }), // 标记指定通知为已读。
}

export const operationsApi = {
  dashboard: () => http.request('/admin/dashboard'), // 读取与实际业务队列对应的统计。
  audit: (query) => http.request(withQuery('/admin/audit-logs', query)), // 按权限和具体业务对象查操作记录。
}
