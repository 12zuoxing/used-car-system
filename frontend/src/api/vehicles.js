/**
 * 车源和字典的同源 API。序列化时只发送填写过的筛选项。
 * @author pangzhihao
 */
import { http } from './http.js'

function withQuery(path, values = {}) {
  const params = new URLSearchParams()
  for (const [name, value] of Object.entries(values)) {
    if (value !== '' && value != null) params.set(name, String(value))
  }
  return params.size ? path + '?' + params : path
}

export const vehicleApi = {
  catalog: () => http.request('/dictionaries'),
  search: (filter) => http.request(withQuery('/vehicles', filter)),
  detail: (id) => http.request('/vehicles/' + id),
  mine: (query) => http.request(withQuery('/vehicles/mine', query)),
  myDetail: (id) => http.request('/vehicles/mine/' + id),
  publish: (body) => http.request('/vehicles', { method: 'POST', body }),
  resubmit: (id, body) => http.request('/vehicles/' + id, { method: 'PUT', body }),
  offline: (id) => http.request('/vehicles/' + id + '/offline', { method: 'POST' }),
  queue: (query) => http.request(withQuery('/admin/vehicles', query)),
  review: (id, body) => http.request('/admin/vehicles/' + id + '/review', { method: 'POST', body }),
}

export const dictionaryApi = {
  all: () => http.request('/admin/dictionaries'),
  save: (kind, id, body) => http.request('/admin/dictionaries/' + kind + (id ? '/' + id : ''),
    { method: id ? 'PUT' : 'POST', body }),
}
