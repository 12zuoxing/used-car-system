/**
 * 同源上传与受控下载。JSON 部件带有自己的媒体类型，浏览器生成 multipart 边界。
 * @author pangzhihao
 */
import { http } from './http.js'

export const acceptedPhotos = 'image/jpeg,image/png,image/webp'
export const acceptedReports = acceptedPhotos + ',application/pdf'
export const attachmentUrl = (id) => '/api/attachments/' + encodeURIComponent(String(id))

export function withAttachment(part, data, file, filePart = 'file') {
  if (!file) return data
  const body = new FormData()
  body.append(part, new Blob([JSON.stringify(data)], { type: 'application/json' }))
  body.append(filePart, file)
  return body
}

export function withVehicleMedia(vehicle, photo, report, reportFile) {
  if (!photo && !report) return vehicle
  const body = new FormData()
  body.append('vehicle', new Blob([JSON.stringify(vehicle)], { type: 'application/json' }))
  if (photo) body.append('photo', photo)
  if (report) body.append('report', new Blob([JSON.stringify(report)], { type: 'application/json' }))
  if (reportFile) body.append('reportFile', reportFile)
  return body
}

const vehiclePath = (id) => '/vehicles/' + id

export const mediaApi = {
  photos: (id) => http.request(vehiclePath(id) + '/attachments'),
  reports: (id) => http.request(vehiclePath(id) + '/reports'),
  submitReport: (id, data, file) => http.request(vehiclePath(id) + '/reports',
    { method: 'POST', body: withAttachment('report', data, file) }),
  upload: (vehicleId, file, reportId) => {
    const body = new FormData()
    body.append('file', file)
    if (reportId != null) body.append('reportId', String(reportId))
    return http.request(vehiclePath(vehicleId) + '/attachments', { method: 'POST', body })
  },
  queue: ({ status, page, pageSize }) => {
    const query = new URLSearchParams({ page, pageSize })
    if (status) query.set('status', status)
    return http.request('/admin/reports?' + query)
  },
  review: (id, data) => http.request('/admin/reports/' + id + '/review', { method: 'POST', body: data }),
}
