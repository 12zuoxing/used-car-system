/**
 * 通知接收器随当前登录会话启动，在网络恢复和消息提示时重新读取持久化收件箱。
 * @author pangzhihao
 */
import { reactive, readonly, watch } from 'vue' // 在长连接中维护可响应的未读数和连接状态。
import { notificationApi } from '../../api/operations.js' // 用与列表相同的授权查询刷新未读数。
import { auth } from '../auth/session.js' // 仅为当前已登录账号建立连接。

const state = reactive({ unread: 0, revision: 0, connected: false }) // 为导航和通知列表提供统一状态。
let accountId = null // 确保异步请求只能写入当前账号的通知结果。
let socket // 保存当前活动的 WebSocket。
let retryTimer // 限制同一时刻只能有一个计划重连。
let signalTimer // 合并短时间内重复到达的刷新提示。
let retryCount = 0 // 为失败的连续握手计算退避时间。
let requestVersion = 0 // 防止先发出的旧 HTTP 响应覆盖新的未读数字。

async function refresh() {
  if (accountId == null) return // 退出后没有可查询的个人收件箱。
  const version = ++requestVersion // 记录本次未读数请求。
  state.revision += 1 // 通知已经打开的收件箱重新加载。
  try {
    const result = await notificationApi.list({ page: 1, pageSize: 1 }) // 未读汇总与实际列表使用相同的数据源。
    if (version === requestVersion && accountId != null) state.unread = result.unreadCount // 仅让最新有效响应更新导航。
  } catch {
    // 网络暂时不可达时保留最后一次成功数字，重连或手动刷新会再次读取。
  }
}

function signalRefresh() {
  if (signalTimer != null) return // 短时间内收到多个提示时只刷新一次。
  signalTimer = window.setTimeout(() => {
    signalTimer = undefined // 本次合并窗口结束。
    void refresh() // 通过受保护的 HTTP 请求读取实际通知。
  }, 120) // 让同一批业务变更合并成一次导航与列表更新。
}

function scheduleReconnect() {
  if (accountId == null || !navigator.onLine || retryTimer != null) return // 已登出、离线或已计划重连时停止增加定时任务。
  const delay = Math.min(30_000, 1000 * 2 ** Math.min(retryCount++, 5)) // 连续失败时退避，最长间隔 30 秒。
  retryTimer = window.setTimeout(() => {
    retryTimer = undefined // 当前重连计划已触发。
    connect() // 在同一来源重新尝试认证握手。
  }, delay)
}

function connect() {
  if (accountId == null || socket || !navigator.onLine) return // 保证当前账号只有一个活动连接。
  const url = new URL('/ws/notifications', window.location.href) // 使用所在页面的同源地址与现有 Cookie。
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:' // HTTPS 页面使用加密连接。
  let connection
  try {
    connection = new WebSocket(url) // 浏览器在握手中自动携带登录会话。
  } catch {
    scheduleReconnect() // 连接创建异常也采用同一退避策略。
    return // 等下一次可用的重连。
  }
  socket = connection // 将后续事件限定到此连接。
  connection.addEventListener('open', () => {
    if (socket !== connection) return // 旧账号或旧连接事件不能影响当前界面。
    state.connected = true // 告知通知页实时提醒可用。
    retryCount = 0 // 成功握手后重置退避。
    signalRefresh() // 重连先补读断线期间写入的记录。
  })
  connection.addEventListener('message', (event) => {
    if (socket !== connection) return // 丢弃已经被替换的连接消息。
    try {
      if (JSON.parse(event.data).type === 'NOTIFICATIONS_CHANGED') signalRefresh() // 仅响应服务端的收件箱刷新信号。
    } catch {
      // 非 JSON 或格式意外的内容不会进入用户通知。
    }
  })
  connection.addEventListener('error', () => {
    if (socket === connection) connection.close() // 让失效连接统一由 close 回调处理。
  })
  connection.addEventListener('close', () => {
    if (socket !== connection) return // 主动退出时没有重连旧会话的必要。
    socket = undefined // 释放连接以便创建新的握手。
    state.connected = false // 收件箱继续支持手动查询。
    scheduleReconnect() // 等待网络恢复或按退避计划尝试。
  })
}

function disconnect() {
  const previous = socket // 保存待关闭的旧连接。
  socket = undefined // 让旧连接未来的事件没有作用。
  previous?.close() // 注销或离线时释放浏览器连接。
  window.clearTimeout(retryTimer) // 移除旧账号的计划重连。
  window.clearTimeout(signalTimer) // 移除旧账号的计划刷新。
  retryTimer = undefined // 允许新的账号安排重连。
  signalTimer = undefined // 允许新的账号被实时刷新。
  state.connected = false // 更新通知页面的实时连接状态。
}

function resume() {
  if (accountId == null) return // 无有效登录不恢复连接。
  window.clearTimeout(retryTimer) // 网络主动恢复时无需继续等待旧重连时间。
  retryTimer = undefined // 移除现有重连标记。
  connect() // 确保当前已登录账号具有连接。
  signalRefresh() // 恢复网络或重新聚焦时同步站内通知。
}

export function installNotificationInbox() {
  const stop = watch(() => auth.state.account?.id, (id) => {
    disconnect() // 关闭旧账号的连接与定时器。
    requestVersion += 1 // 旧账号的请求结果失效。
    accountId = id ?? null // 新连接只针对当前已认证账号。
    retryCount = 0 // 各账号分别经历重连退避。
    state.unread = 0 // 防止短时间内显示上一个账号的未读数。
    if (accountId != null) resume() // 加载未读并开启通知通道。
  }, { immediate: true }) // 根组件加载后已有登录也能直接连接。
  const visible = () => {
    if (!document.hidden) resume() // 返回浏览器标签页时校对未读状态。
  }
  window.addEventListener('online', resume) // 网络恢复后立即补读。
  window.addEventListener('offline', disconnect) // 网络离线时释放当前连接。
  document.addEventListener('visibilitychange', visible) // 前台重新聚焦时刷新。
  const fallback = window.setInterval(() => {
    if (!document.hidden && navigator.onLine) signalRefresh() // 定期用权威列表校对空闲连接期间的未读数。
  }, 60_000)
  return () => {
    stop() // 清理对会话状态的订阅。
    accountId = null // 避免卸载后继发的连接请求。
    requestVersion += 1 // 让正在执行的请求作废。
    disconnect() // 关闭套接字并清理临时定时器。
    window.clearInterval(fallback) // 停止空闲状态补读。
    window.removeEventListener('online', resume) // 移除浏览器恢复事件。
    window.removeEventListener('offline', disconnect) // 移除浏览器断开事件。
    document.removeEventListener('visibilitychange', visible) // 移除可见性监听。
  }
}

export const inbox = { state: readonly(state), refresh } // 页面只能通过授权请求刷新通知状态。
