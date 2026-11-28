/**
 * 从真实 Chrome 渲染结果采集答辩截图。
 * @author pangzhihao
 */
import assert from 'node:assert/strict' // 确认页面数据与设定角色一致。
import { randomUUID } from 'node:crypto' // 为浏览器回归准备独立的下单标识。
import { mkdir, readFile, writeFile } from 'node:fs/promises' // 从业务证据定位页面并输出 PNG。
import { join } from 'node:path' // 将截图放到同一验收证据目录。
import { origin, until } from './http.mjs' // 复用隔离环境和超时轮询。
import { approveReport, approveVehicle, prepare, publish } from './demo.mjs' // 用真实业务接口准备可操作的演示车源。

const site = origin() // 从同一站点访问前端和 API。
const debug = origin(process.env.ACCEPTANCE_CHROME) // 使用本机的临时 Chrome 调试端口。
const output = process.env.ACCEPTANCE_OUTPUT ?? '.runtime/acceptance-evidence' // 收集无需浏览器插件的截图。
const result = JSON.parse(await readFile(join(output, 'acceptance.json'), 'utf8')) // 使用本轮真实交易编号。
const page = await until(async () => (await (await fetch(debug + '/json/list')).json()).find((item) => item.type === 'page'), 'Chrome 调试页面') // 等待可用窗口。
const socket = new WebSocket(page.webSocketDebuggerUrl) // 通过 CDP 操作真实浏览器。
await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject }) // 等待协议连接。

const pending = new Map() // CDP 响应按请求编号匹配。
let seq = 0 // 为每次协议方法分配编号。
socket.onmessage = ({ data }) => {
  const msg = JSON.parse(data) // 协议事件与响应共享同一连接。
  const callback = pending.get(msg.id) // 只处理已经发出的请求。
  if (!callback) return // 忽略无须监听的浏览器事件。
  pending.delete(msg.id) // 响应已到达，释放当前回调。
  if (msg.error) callback.reject(new Error(msg.error.message)) // 将截图或脚本错误转回执行者。
  else callback.resolve(msg.result) // 返回真实协议结果。
}

function send(method, params = {}) {
  const id = ++seq // 请求时使协议编号唯一。
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject }) // 保留这次调用的等待者。
    socket.send(JSON.stringify({ id, method, params })) // 发送浏览器命令。
  })
}

async function evaluate(expression) {
  const state = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true, userGesture: true }) // 从真实渲染树读取当前状态。
  if (state.exceptionDetails) throw new Error(state.exceptionDetails.exception?.description || state.exceptionDetails.text) // 遇到页面脚本错误时中止。
  return state.result.value // 将浏览器中的表达式结果传回测试。
}

async function go(path, text) {
  await send('Page.navigate', { url: site + path }) // 打开实际 Vue 页面。
  await until(() => evaluate('location.pathname === ' + JSON.stringify(path) + ' && document.body?.innerText.includes(' + JSON.stringify(text) + ')'), text) // 等待路由和业务内容真正显示。
}

async function login(name) {
  await go('/login', '用户名') // 使用应用的正常登录表单。
  await evaluate(`(() => {
    for (const [selector, value] of [['#login-username', ${JSON.stringify(name)}],
      ['#login-password', ${JSON.stringify(process.env.DEMO_PASSWORD)}]]) {
      const input = document.querySelector(selector);
      input.value = value;
      input.dispatchEvent(new Event('input', {bubbles:true}));
    }
    document.querySelector('button[type=submit]').click()
  })()`) // 通过页面提交真实会话凭据，截图时不会显示口令。
  await until(() => evaluate('location.pathname !== "/login"'), '账号登录') // 登录后再进入受保护页面。
}

async function shot(name) {
  await evaluate("document.fonts.ready.then(() => true)") // 在字体准备完成后截图。
  const data = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }) // 采集可用于答辩的浏览器画面。
  await writeFile(join(output, name), Buffer.from(data.data, 'base64')) // 保存浏览器输出的原始 PNG。
}

async function verifyPaidCancellation() {
  const ctx = await prepare(site) // 复用隔离部署中的买家、卖家和工作人员会话。
  const vehicle = await publish(ctx, '已支付订单取消回归', 'DIRECT', true) // 独立车源保证回归可重复执行。
  await approveReport(ctx, vehicle) // 审核样本车况报告。
  await approveVehicle(ctx, vehicle) // 让车源可正常下单。
  const order = await ctx.buyer.write('/api/orders', { vehicleId: vehicle.id },
    { status: 201, headers: { 'Idempotency-Key': randomUUID() } }) // 走真实接口下单并取得车辆锁。
  await ctx.buyer.write('/api/orders/' + order.id + '/pay') // 模拟支付后进入工作人员办理队列。
  await go('/admin/orders/' + order.id, '待过户确认') // 使用已登录的工作人员访问后台详情。
  await evaluate('[...document.querySelectorAll("button")].find(button => button.textContent.includes("取消已支付订单")).click()') // 打开取消对话框。
  await until(() => evaluate('Boolean(document.querySelector(\'[role="dialog"] textarea\'))'), '取消原因输入框') // 等到弹窗中的输入框可用。
  const confirm = '[...document.querySelectorAll(\'[role="dialog"] button\')].find(button => button.textContent.includes("取消已支付订单")).click()' // 仅点击弹窗内的确认按钮。
  await evaluate(confirm) // 空原因时，点击仍停留在弹窗。
  await until(() => evaluate('document.querySelector(\'[role="dialog"]\').innerText.includes("请填写取消原因。")'), '取消必填校验') // 实测必填反馈。
  const reason = '双方确认本次无法办理过户' // 使用可在个人订单详情复核的原因。
  await evaluate(`(() => {
    const input = document.querySelector('[role="dialog"] textarea');
    input.value = ${JSON.stringify(reason)};
    input.dispatchEvent(new Event('input', { bubbles: true }));
  })()`) // 触发实际页面的输入事件。
  await evaluate(confirm) // 从网页完成取消并等待真实接口返回。
  await until(() => evaluate('document.body.innerText.includes("已取消订单并通知买卖双方。")'), '取消成功') // 确认实际提交生效。
  await evaluate('[...document.querySelectorAll("button")].find(button => button.textContent.includes("退出登录")).click()') // 结束后台会话以检查买家视角。
  await until(() => evaluate('location.pathname === "/"'), '退出工作人员账号') // 等待后台会话退出。
  await login('demo_buyer') // 使用实际买方登录。
  await go('/account/orders/' + order.id, '取消原因 ' + reason) // 订单详情显示工作人员填写的原因。
  assert.ok(await evaluate('document.body.innerText.includes("模拟支付时间")')) // 取消后仍显示原模拟支付时间。
  await go('/account/notifications', '工作人员已取消本笔订单') // 验证取消通知能在买家收件箱读取。
}

try {
  await mkdir(output, { recursive: true }) // 确保证据目录存在。
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1020, deviceScaleFactor: 1, mobile: false }) // 固定实测截图的分辨率。
  await go('/vehicles', '卡罗拉 · 直销演示') // 确认市场的演示车源真实可见。
  assert.ok(await until(() => evaluate('[...document.querySelectorAll(".vehicle-card-photo")].some(img => img.complete && img.naturalWidth > 0)'), '真实封面解码')) // 使用真实已加载车图。
  await shot('market.png') // 保存公开车源市场。
  await login('demo_buyer') // 买家专页使用买方身份。
  await go('/account/orders/' + result.screenshots.completedOrder, '已成交') // 定位经过直销和模拟付款的实际订单。
  await shot('order-completed.png') // 保存订单结果和过户说明。
  await go('/account/notifications', '工作人员已确认过户') // 验证实际业务通知正常渲染。
  await shot('notifications.png') // 保存站内消息与未读状态。
  await evaluate('[...document.querySelectorAll("button")].find(button => button.textContent.includes("退出登录")).click()') // 明确释放买家的浏览器会话。
  await until(() => evaluate('location.pathname === "/"'), '退出登录') // 防止角色切换使用旧身份。
  await login('demo_staff') // 使用授权经办账号查看统计。
  await go('/admin', '整备与送修待办') // 等待真实统计卡片加载。
  await shot('dashboard.png') // 保存管理工作台。
  console.log('Chrome 截图 4 张，分辨率 1440×1020。') // 只打印已经生成的交付件数。
  await verifyPaidCancellation() // 截图采集完成后检验取消页面，避免影响原有截图的样本状态。
  console.log('Chrome 已验证后台取消、原因校验和买家订单详情与通知。') // 只在完整流程通过后输出结果。
} finally {
  socket.close() // 无论截图是否成功，都关闭调试连接。
}
