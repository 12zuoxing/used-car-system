/**
 * 验收账号各有一套 Cookie，写请求使用服务端真实 CSRF 令牌。
 * @author pangzhihao
 */
import assert from 'node:assert/strict' // 改变业务规则时让验收立即失败。
import { setTimeout as pause } from 'node:timers/promises' // 轮询本机应用启动与状态落库。
import { mkdir, writeFile } from 'node:fs/promises' // 保存可引用的验收结果。
import { join } from 'node:path' // 使用宿主系统的路径分隔符。

export function origin(value = process.env.ACCEPTANCE_BASE ?? 'http://127.0.0.1:5173') {
  const url = new URL(value) // 接受同源前端入口或直连本机后端。
  assert.ok(['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname), '验收目标须为本机回环地址') // 将写入和负载限定到演示实例。
  return url.origin // 去掉路径，统一从服务根读取 API。
}

export function client(base) {
  let cookie = '' // 登录、切换身份和退出都使用独立的 cookie。
  async function call(path, { method = 'GET', body, headers = {} } = {}) {
    const requestHeaders = new Headers(headers) // 保留幂等头等业务参数。
    if (cookie) requestHeaders.set('Cookie', cookie) // 将账号与请求绑定。
    const multipart = body instanceof FormData // 同时支持车辆图片和 JSON 资料。
    if (body !== undefined && !multipart) requestHeaders.set('Content-Type', 'application/json') // 让浏览器库自动生成 multipart 边界。
    const response = await fetch(base + path, { method, headers: requestHeaders,
      body: body === undefined || multipart ? body : JSON.stringify(body), signal: AbortSignal.timeout(30_000) }) // 给每次请求设置明确的等待上限。
    for (const header of response.headers.getSetCookie()) {
      const [entry] = header.split(';', 1) // 只保存会话 Cookie 的名称和值。
      if (entry.startsWith('JSESSIONID=')) cookie = entry // 登录时的会话轮换覆盖之前的 Cookie。
    }
    return response // 允许调用方按场景检查状态或附件字节。
  }

  async function expect(path, status = 200, options) {
    const response = await call(path, options) // 模拟真实用户在 HTTP 层的请求。
    const data = await response.text() // 错误信息可用于定位失败步骤。
    assert.equal(response.status, status, `${options?.method ?? 'GET'} ${path} ${data.slice(0, 200)}`) // 逐项验证状态码。
    return data ? JSON.parse(data) : null // 204 响应保持为空。
  }

  async function permission() {
    const result = await expect('/api/auth/csrf') // 获取当前会话的有效令牌。
    return { [result.headerName]: result.token } // 返回可直接交给写请求的请求头。
  }

  async function write(path, body, { method = 'POST', status = 200, headers = {} } = {}) {
    return expect(path, status, { method, body, headers: { ...await permission(), ...headers } }) // 写操作都通行同一 CSRF 检查。
  }

  return { call, expect, permission, write, cookie: () => cookie } // 暴露验收需要的查询、操作和握手信息。
}

export async function until(test, label, timeoutMs = 30_000) {
  const deadline = Date.now() + timeoutMs // 为轮询设定截止时间。
  while (Date.now() < deadline) {
    const result = await Promise.resolve().then(test).catch(() => null) // 对瞬时连接失败继续等待。
    if (result) return result // 返回实际取得的结果。
    await pause(120) // 降低本机服务启动期间的轮询负担。
  }
  throw new Error('等待超时 ' + label) // 在失败报告里保留未达到的验收节点。
}

export async function save(name, data) {
  const dir = process.env.ACCEPTANCE_OUTPUT ?? '.runtime/acceptance-evidence' // 默认将重复验收的产物留在忽略目录。
  await mkdir(dir, { recursive: true }) // 按需创建证据目录。
  await writeFile(join(dir, name), JSON.stringify(data, null, 2) + '\n') // 结构化输出便于计算复核值。
}

export function businessTime(offset = 0) {
  const time = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', dateStyle: 'short', timeStyle: 'medium' }) // 用秒级北京时间作为工单和委托输入。
  return time.format(new Date(Date.now() + offset)).replace(' ', 'T') // 转成后端接受的本地时间格式。
}
