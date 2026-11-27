/**
 * 车源枚举和计量单位在市场、个人中心及后台统一展示。
 * @author pangzhihao
 */
export const saleModes = { DIRECT: '直销', CONSIGN: '代销' }
export const transmissions = { AUTO: '自动', MANUAL: '手动' }
export const fuels = { PETROL: '汽油', HYBRID: '混动', ELECTRIC: '纯电', DIESEL: '柴油' }
export const statuses = {
  PENDING: { label: '待审核', severity: 'warn' },
  REJECTED: { label: '已退回', severity: 'danger' },
  LISTED: { label: '在售', severity: 'success' },
  LOCKED: { label: '已锁定', severity: 'info' },
  SOLD: { label: '已成交', severity: 'info' },
  OFFLINE: { label: '已下架', severity: 'secondary' },
}
const currency = new Intl.NumberFormat('zh-CN', {
  style: 'currency', currency: 'CNY', minimumFractionDigits: 0, maximumFractionDigits: 2,
})
const units = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 })

export const formatPrice = (value) => currency.format(value)
export const formatMileage = (value) => value >= 10000
  ? units.format(value / 10000) + ' 万公里'
  : units.format(value) + ' 公里'
export const vehicleTitle = (vehicle) => vehicle.brandName + ' ' + vehicle.model
export const canEdit = (vehicle) => ['OFFLINE', 'REJECTED'].includes(vehicle.status)
