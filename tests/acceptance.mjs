/**
 * 从真实会话入口覆盖直销、代销、贷款、送修、通知和抢车竞争。
 * @author pangzhihao
 */
import assert from 'node:assert/strict' // 验证重要业务状态与权限。
import { randomUUID } from 'node:crypto' // 为每次运行和下单生成独立标识。
import { businessTime, client, origin, save } from './http.mjs' // 使用独立会话和统一证据输出。
import { approveReport, approveVehicle, consignment, prepare, publish } from './demo.mjs' // 共用真实演示数据创建流程。
import { mysql } from './mysql-tools.mjs' // 在独立库中验证锁、过期和事务结果。

const base = origin() // 同一站点提供前端和受保护 API。
const ctx = await prepare(base) // 确保买卖双方、工作人员、管理员已经就绪。
const runId = randomUUID().slice(0, 8) // 标识本轮车源和订单。
const day = 86_400_000 // 所有预约时间均使用未来的北京时间。
const paths = [] // 记录通过的业务步骤，方便核对证据。

function step(name) {
  paths.push(name) // 只有这一流程的断言通过后才登记。
  console.log('通过 ' + name) // 保留运行时进度反馈。
}

async function order(vehicleId, key = randomUUID()) {
  return ctx.buyer.write('/api/orders', { vehicleId }, { status: 201, headers: { 'Idempotency-Key': key } }) // 通过买家登录发起真实占用。
}

async function repair(actor, stage, vehicleId, tradeOrderId = null) {
  const item = await actor.write('/api/repairs', { stage, vehicleId, tradeOrderId,
    visitAt: businessTime(day), description: '演示整备与送修流程，检查轮胎和转向。' }, { status: 201 }) // 创建真实工单。
  const path = '/api/admin/repairs/' + item.id + '/status' // 用后台接口推进合法状态。
  await ctx.staff.write(path, { status: 'PROCESSING', resultNote: '已受理。' }) // 工作人员接单。
  const result = await ctx.staff.write(path, { status: 'COMPLETED', resultNote: '检查及复核已完成。' }) // 登记办理结果。
  assert.equal(result.status, 'COMPLETED') // 纠正或返修的描述不改变此处完工状态。
  return item.id // 为恢复校验保留真实编号。
}

const direct = await publish(ctx, '卡罗拉 · 直销联调 ' + runId, 'DIRECT', true) // 通过 multipart 同时提交车源和报告。
await ctx.publicApi.expect('/api/vehicles/' + direct.id, 404) // 待审车源不进入公开市场。
const report = await approveReport(ctx, direct) // 回填报告审核版本对应的结果。
await approveVehicle(ctx, direct) // 审核后允许市场公开。
const published = await ctx.publicApi.expect('/api/vehicles/' + direct.id) // 记录真实封面和车源编号。
const reportResponse = await ctx.publicApi.call('/api/attachments/' + report.attachments[0].id) // 验证审核通过附件可访问。
assert.equal(reportResponse.status, 200) // 下载使用正常受控接口。
assert.ok((await reportResponse.arrayBuffer()).byteLength > 0 && published.coverPhotoId) // 确认为可读取附件。
step('车源发布、照片及报告审核') // 覆盖公开与权限门槛。

const visit = await ctx.buyer.write('/api/vehicles/' + direct.id + '/appointments',
  { visitAt: businessTime(day), note: '预约到店看车。' }, { status: 201 }) // 在公开车辆发起预约。
const confirmed = await ctx.staff.write('/api/admin/appointments/' + visit.id + '/handle',
  { status: 'CONFIRMED', note: '可按时到店。' }) // 后台确认到店安排。
assert.equal(confirmed.status, 'CONFIRMED') // 确认状态实际更新。
const preRepair = await repair(ctx.seller, 'PRE_SALE', direct.id) // 从售前工单验证受理和完工。
step('看车预约和售前整备') // 保存独立服务流程的结果。

const requestKey = randomUUID() // 第一次创建与网络重试共用同一个标识。
const directOrder = await order(direct.id, requestKey) // 由服务端保存价格并锁车。
const replay = await ctx.buyer.write('/api/orders', { vehicleId: direct.id }, { headers: { 'Idempotency-Key': requestKey } }) // 同一请求再次执行。
assert.equal(replay.id, directOrder.id) // 找回原订单而不是生成第二笔。
const loan = await ctx.buyer.write('/api/orders/' + directOrder.id + '/loan',
  { amount: 35000, months: 24, note: '教学演示贷款申请。' }, { status: 201 }) // 使用真实成交金额限制贷款。
const loanPath = '/api/admin/loans/' + loan.id + '/status' // 管理端处理同一条申请。
const need = await ctx.staff.write(loanPath, { status: 'SUPPLEMENT', revision: loan.revision, resultNote: '请补充期数说明。' }) // 标记补件。
const revised = await ctx.buyer.write('/api/loans/' + loan.id + '?revision=' + need.revision,
  { amount: 33000, months: 24, note: '已补充说明。' }, { method: 'PUT' }) // 更新原申请而不是重复创建。
const processing = await ctx.staff.write(loanPath, { status: 'PROCESSING', revision: revised.revision }) // 工作人员开始办理。
const closed = await ctx.staff.write(loanPath, { status: 'FINISHED', revision: processing.revision, resultNote: '人工登记完成。' }) // 保存办理结果。
assert.equal(closed.id, loan.id) // 补充资料与办理结果使用同一申请编号。
assert.equal(closed.status, 'FINISHED') // 办理完成已持久化。
step('贷款退回、补充、重提与办结') // 确认贷款状态不依赖模拟付款。

await ctx.buyer.write('/api/orders/' + directOrder.id + '/pay') // 买家模拟付款。
const sale = await ctx.staff.write('/api/admin/orders/' + directOrder.id + '/complete', { note: '过户资料核对完成。' }) // 工作人员确认过户。
assert.equal(sale.status, 'COMPLETED') // 买家订单进入已成交。
const afterRepair = await repair(ctx.buyer, 'AFTER_SALE', direct.id, directOrder.id) // 用已成交订单发起售后。
const inbox = await ctx.buyer.expect('/api/notifications?unreadOnly=true&pageSize=100') // 从独立 HTTP 会话读取实际持久化通知。
const notice = inbox.items.find((item) => item.subjectType === 'ORDER' && item.subjectId === directOrder.id && item.message.includes('已完成')) // 定位实际成交事件。
assert.ok(notice) // 验证业务提交后产生通知。
await ctx.buyer.write('/api/notifications/' + notice.id + '/read', undefined, { status: 204 }) // 标为已读。
const remaining = await ctx.buyer.expect('/api/notifications?unreadOnly=true&pageSize=100') // 查询未读状态。
assert.equal(remaining.unreadCount, inbox.unreadCount - 1) // 未读汇总随标记更新。
step('直销模拟支付、过户、售后和通知') // 保存买家完整路径结果。

const agentCar = await publish(ctx, '凯美瑞 · 代销联调 ' + runId, 'CONSIGN') // 建立一辆独立代销车。
const agreement = await consignment(ctx, agentCar) // 先完成有效委托。
await approveVehicle(ctx, agentCar) // 在委托生效期间发布。
const prior = await order(agentCar.id) // 在委托到期前创建交易。
mysql(`UPDATE consignment SET starts_at='${businessTime(-2 * day).replace('T', ' ')}', expires_at='${businessTime(-day).replace('T', ' ')}' WHERE id=${Number(agreement.id)}`) // 在独立库推进时间分支，无需等待到期。
await ctx.buyer.write('/api/orders/' + prior.id + '/cancel') // 测试到期后取消已存在订单。
assert.equal((await ctx.seller.expect('/api/vehicles/mine/' + agentCar.id)).status, 'OFFLINE') // 取消后无法恢复公开在售。
await ctx.publicApi.expect('/api/vehicles/' + agentCar.id, 404) // 核对市场可见范围。
await ctx.buyer.write('/api/orders', { vehicleId: agentCar.id }, { status: 409, headers: { 'Idempotency-Key': randomUUID() } }) // 到期后不接受新交易。
step('代销委托生效与到期下架') // 记录业务边界。

const agentSold = await publish(ctx, '亚洲龙 · 代销成交 ' + runId, 'CONSIGN') // 单独验证代销成交不影响到期样例。
const saleAgreement = await consignment(ctx, agentSold) // 记录订单使用的真实委托。
await approveVehicle(ctx, agentSold) // 完成上架审核。
const agentOrder = await order(agentSold.id) // 保存绑定有效委托的订单。
assert.equal(agentOrder.consignmentId, saleAgreement.id) // 核对代销订单外键。
await ctx.buyer.write('/api/orders/' + agentOrder.id + '/pay') // 完成模拟付款。
assert.equal((await ctx.staff.write('/api/admin/orders/' + agentOrder.id + '/complete', { note: '代销演示已过户。' })).status, 'COMPLETED') // 走完代销交易。
step('代销支付与成交') // 两种销售方式均已完整验证。

const raceCar = await publish(ctx, '并发抢车 ' + runId, 'DIRECT') // 用单独车源测量真实事务竞争。
await approveVehicle(ctx, raceCar) // 仅允许抢占真实在售车辆。
const contestants = await Promise.all(Array.from({ length: 20 }, async (_, index) => {
  const api = client(base) // 每个买家独立验收会话。
  const username = `load_${runId}_${index}` // 同一轮中不共享身份或请求标识。
  await api.write('/api/auth/register', { username, password: process.env.DEMO_PASSWORD, displayName: '并发买家' }, { status: 201 }) // 通过应用建立账号。
  await api.write('/api/auth/login', { username, password: process.env.DEMO_PASSWORD }) // 取得自己的登录 Cookie。
  return { api, headers: { ...await api.permission(), 'Idempotency-Key': randomUUID() } } // 提前准备 CSRF，计时只覆盖下单请求。
}))
const raceStarted = performance.now() // 从并发下单开始记录端到端时长。
const attempts = await Promise.all(contestants.map(async ({ api, headers }) => {
  const response = await api.call('/api/orders', { method: 'POST', headers, body: { vehicleId: raceCar.id } }) // 同时竞争一辆车。
  const result = await response.json() // 提取业务返回便于核对成功记录。
  return { status: response.status, orderId: result.id, code: result.code, ms: Math.round(performance.now() - raceStarted) } // 保存每个请求证据。
}))
assert.equal(attempts.filter((result) => result.status === 201).length, 1) // 只接受一个成功订单。
assert.equal(attempts.filter((result) => result.status === 409).length, 19) // 失败请求全部得到受控冲突。
const [orders, locks, linkedLocks] = mysql(`SELECT
  (SELECT COUNT(*) FROM trade_order WHERE vehicle_id=${raceCar.id}),
  (SELECT COUNT(*) FROM vehicle_lock WHERE vehicle_id=${raceCar.id}),
  (SELECT COUNT(*) FROM vehicle_lock l JOIN trade_order o ON o.id=l.order_id AND o.vehicle_id=l.vehicle_id WHERE l.vehicle_id=${raceCar.id} AND o.status='LOCKED')`)
  .trim().split('\t').map(Number) // 用数据库复核订单和当前锁的对应关系。
assert.deepEqual([orders, locks, linkedLocks], [1, 1, 1]) // 排除失败事务遗留记录。
await save('concurrency.json', { requests: 20, created: 1, conflict: 19, orders, locks, linkedLocks, attempts }) // 持久化并发证据。
step('真实 MySQL 20 个买家抢同一辆车') // 区分业务冲突与异常。

const dashboard = await ctx.staff.expect('/api/admin/dashboard') // 查看当前待办统计。
assert.ok(dashboard.lockedOrders >= 1) // 至少计入刚刚成功占用的车源。
const audit = await ctx.admin.expect('/api/admin/audit-logs?subjectType=ORDER&action=COMPLETE_ORDER') // 查询实际业务留痕。
assert.ok(audit.items.some((item) => item.subjectId === directOrder.id)) // 确认直销成交进入审计。
await save('acceptance.json', { passed: paths, screenshots: { completedOrder: directOrder.id },
  records: { directVehicle: direct.id, directOrder: directOrder.id, agentOrder: agentOrder.id, appointment: visit.id,
    loan: loan.id, preRepair, afterRepair, coverPhotoId: published.coverPhotoId }, dashboard }) // 记录业务样本和截图定位。
