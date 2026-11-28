/**
 * 一个买家对一辆车的一次下单意图，直到获知成功前始终使用同一个请求标识。
 * @author pangzhihao
 */
const memory = new Map() // 当浏览器拒绝存储时，在当前页面继续保留标识。
const slot = (buyerId, vehicleId) => 'usedcar:order:' + buyerId + ':' + vehicleId // 隔离不同买家和车辆的重试记录。

function fromSession(name) {
  try {
    return globalThis.sessionStorage?.getItem(name) // 刷新页面或后退后尝试读取原来的重试标识。
  } catch {
    return null // 浏览器存储被限制时，使用当前页面内存。
  }
}

export function orderRequestKey(buyerId, vehicleId) {
  const name = slot(buyerId, vehicleId) // 计算本次提交的存储位置。
  const previous = memory.get(name) || fromSession(name) // 优先复用已生成的标识。
  if (previous) return previous // 响应丢失后的再次提交不会生成新订单。
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16)) // 生成跨页面和跨客户端均适用的随机标识。
  const key = Array.from(bytes, (part) => part.toString(16).padStart(2, '0')).join('') // 编码为后端接受的十六进制字符。
  memory.set(name, key) // 保留当前页面的重试标识。
  try {
    globalThis.sessionStorage?.setItem(name, key) // 保存到当前标签页以便刷新后恢复。
  } catch {
    // 存储不可用时，当前页面仍然可以使用内存中的标识重试。
  }
  return key // 将固定标识交给本次 API 请求。
}

export function forgetOrderRequest(buyerId, vehicleId, key) {
  const name = slot(buyerId, vehicleId) // 只清除这个买家和车源的下单意图。
  if (memory.get(name) === key) memory.delete(name) // 清除与这次成功请求匹配的内存标识。
  try {
    if (fromSession(name) === key) globalThis.sessionStorage.removeItem(name) // 确认服务端成功后允许下一次独立购买。
  } catch {
    // 存储不可用时，仍保留已经清理的内存结果。
  }
}
