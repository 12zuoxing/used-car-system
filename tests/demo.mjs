/**
 * 用实际业务接口准备演示账号、字典、在售车源和待审车源。
 * @author pangzhihao
 */
import assert from 'node:assert/strict' // 验证演示账号与所需角色。
import { readFile } from 'node:fs/promises' // 从项目已交付的图片中读取演示附件。
import { pathToFileURL } from 'node:url' // 区分直接运行脚本和被联调脚本调用。
import { businessTime, client, origin, until } from './http.mjs' // 复用会话、超时与业务时间。

const day = 86_400_000 // 用毫秒表达一个自然日的时长。
export const accounts = { seller: 'demo_seller', buyer: 'demo_buyer', staff: 'demo_staff' } // 演示身份可重复登录。

export async function prepare(base) {
  const password = process.env.DEMO_PASSWORD // 口令由操作者为当前演示环境设置。
  const adminName = process.env.DEMO_ADMIN_USERNAME ?? 'demo_admin' // 默认与部署说明的初始化账号一致。
  assert.ok(password, '请设置 DEMO_PASSWORD') // 避免把项目固定口令写进代码。
  assert.ok(!Object.values(accounts).includes(adminName), '管理员须使用独立账号') // 保持所有演示角色独立。
  const publicApi = client(base) // 车源字典和市场无需登录。
  await until(async () => (await publicApi.call('/api/dictionaries')).ok, '演示应用启动') // 等待数据库准备完成。
  const admin = client(base) // 为管理员保留独立会话。
  await admin.write('/api/auth/login', { username: adminName, password: process.env.DEMO_ADMIN_PASSWORD ?? password }) // 仅用于管理角色和字典。
  const roles = Object.fromEntries(await Promise.all([
    membership(base, accounts.seller, '演示卖家', password).then((entry) => ['seller', entry]),
    membership(base, accounts.buyer, '演示买家', password).then((entry) => ['buyer', entry]),
    membership(base, accounts.staff, '演示工作人员', password).then((entry) => ['staff', entry]),
  ])) // 三个账号可以各自完成对应业务。
  if (!roles.staff.profile.roles.includes('STAFF')) {
    await admin.write('/api/admin/users/' + roles.staff.profile.id + '/roles', { roles: ['STAFF'] }, { method: 'PUT' }) // 用正常后台接口授予经办权限。
  }
  const catalog = await publicApi.expect('/api/dictionaries') // 优先使用已导入且启用的字典。
  const brand = catalog.brands.find((item) => item.name === '丰田')
    ?? await admin.write('/api/admin/dictionaries/BRAND', { name: '丰田', active: true }, { status: 201 }) // 空库也可自动准备品牌。
  const city = catalog.cities.find((item) => item.name === '杭州')
    ?? await admin.write('/api/admin/dictionaries/CITY', { name: '杭州', active: true }, { status: 201 }) // 保留已有的启用城市。
  return { publicApi, admin, seller: roles.seller.api, buyer: roles.buyer.api, staff: roles.staff.api,
    sellerId: roles.seller.profile.id, buyerId: roles.buyer.profile.id, staffId: roles.staff.profile.id, brandId: brand.id, cityId: city.id } // 返回后续步骤所需的会话和业务标识。
}

async function membership(base, username, displayName, password) {
  const api = client(base) // 每个账号独立保存会话。
  const response = await api.call('/api/auth/register', {
    method: 'POST', headers: await api.permission(), body: { username, displayName, password },
  }) // 允许首次创建或登录此前创建的演示账号。
  assert.ok([201, 409].includes(response.status), '创建演示账号失败 ' + username) // 保留其他真实服务错误。
  await response.text() // 消耗响应体以复用连接。
  const profile = await api.write('/api/auth/login', { username, password }) // 只有已有账号口令一致时才继续。
  return { api, profile } // 返回当前会话与角色信息。
}

export async function publish(ctx, model, mode, withMedia = false) {
  const vehicle = { model, brandId: ctx.brandId, cityId: ctx.cityId, registrationYear: 2021,
    mileage: 34000, price: 68000.50, transmission: 'AUTO', fuel: 'PETROL', saleMode: mode,
    description: '平台演示使用的构造车源。' } // 通过正式发布接口保存业务数据。
  if (!withMedia) return ctx.seller.write('/api/vehicles', vehicle, { status: 201 }) // 此样本只需发布车源。
  const photo = await readFile(new URL('../frontend/public/images/sedan.jpg', import.meta.url)) // 使用仓库自带的样图。
  const form = new FormData() // 验证浏览器采用的 multipart 接口。
  form.append('vehicle', new Blob([JSON.stringify(vehicle)], { type: 'application/json' })) // 车辆资料为 JSON 部件。
  form.append('photo', new Blob([photo], { type: 'image/jpeg' }), 'sample-vehicle.jpg') // 样图用于真实车源封面与备份恢复。
  form.append('report', new Blob([JSON.stringify({ source: '教学演示检测', reportDate: businessTime(-day).slice(0, 10),
    summary: '演示报告用于说明人工登记与审核过程。' })], { type: 'application/json' })) // 用构造数据展示报告功能。
  form.append('reportFile', new Blob([photo], { type: 'image/jpeg' }), 'sample-report.jpg') // 报告附件使用项目样图。
  return ctx.seller.write('/api/vehicles', form, { status: 201 }) // 通过业务事务保存车源与附件。
}

export async function approveVehicle(ctx, vehicle) {
  return ctx.staff.write('/api/admin/vehicles/' + vehicle.id + '/review', { revision: vehicle.revision,
    decision: 'APPROVE', note: '演示资料已核对。' }) // 审核使用当前服务端版本。
}

export async function approveReport(ctx, vehicle) {
  const reports = await ctx.seller.expect('/api/vehicles/' + vehicle.id + '/reports') // 从授权详情读取审核版本。
  const report = reports[0] // 演示车源通过同一次表单登记一份报告。
  assert.ok(report, '应存在演示报告') // 验证报告已经随车源提交。
  if (report.status === 'APPROVED') return report // 重跑时保留已通过的报告。
  return ctx.staff.write('/api/admin/reports/' + report.id + '/review',
    { decision: 'APPROVE', revision: report.revision, note: '已确认演示报告。' }) // 完成后台报告审核。
}

export async function consignment(ctx, vehicle) {
  const agreement = await ctx.seller.write('/api/vehicles/' + vehicle.id + '/consignments',
    { startsAt: businessTime(-day), expiresAt: businessTime(14 * day), fee: 1200, terms: '构造的代销约定。' }, { status: 201 }) // 正常提交当前有效期。
  return ctx.staff.write('/api/admin/consignments/' + agreement.id + '/review',
    { decision: 'APPROVE', note: '已核对委托条款。' }) // 使委托成为上架依据。
}

async function seed() {
  const ctx = await prepare(origin()) // 创建或恢复可登录的演示账号。
  const owned = await ctx.seller.expect('/api/vehicles/mine?pageSize=100') // 读取已创建样本，便于重复启动。
  const known = new Map(owned.items.map((row) => [row.model, row])) // 相同演示标题使用同一条已存在的车源。
  await finishSample(ctx, known, '卡罗拉 · 直销演示', 'DIRECT') // 准备或恢复被中断的直销样本。
  await finishSample(ctx, known, '凯美瑞 · 代销演示', 'CONSIGN') // 准备或恢复被中断的代销样本。
  if (!known.has('雷凌 · 待审核演示')) await publish(ctx, '雷凌 · 待审核演示', 'DIRECT', true) // 给答辩保留待处理队列。
  console.log('演示数据就绪。卖家 demo_seller、买家 demo_buyer、工作人员 demo_staff。') // 只展示账号名。
}

async function finishSample(ctx, known, model, mode) {
  const car = known.get(model) ?? await publish(ctx, model, mode, true) // 保留已经存在的演示样本编号。
  if (car.status !== 'PENDING') return // 已上架或已经演示过交易的样本保持原业务状态。
  if (mode === 'CONSIGN') {
    const agreements = (await ctx.seller.expect('/api/vehicles/' + car.id + '/consignments')).items // 读取已经提交的委托。
    if (!agreements.some((entry) => entry.active)) {
      const pending = agreements.find((entry) => entry.status === 'PENDING') // 优先继续上次未完成的审核。
      if (pending) await ctx.staff.write('/api/admin/consignments/' + pending.id + '/review', { decision: 'APPROVE' }) // 继续原委托。
      else await consignment(ctx, car) // 首次运行时提交并审核委托。
    }
  }
  await approveReport(ctx, car) // 审核报告，允许此前已完成。
  await approveVehicle(ctx, car) // 将尚待审的样本上架。
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await seed() // 直接运行时执行演示数据准备。
