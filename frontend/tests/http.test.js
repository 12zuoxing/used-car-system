/**
 * 使用浏览器请求接口的替身验证会话请求的关键约定。
 * @author pangzhihao
 */
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { ApiError, createHttpClient } from '../src/api/http.js'

const json = (body, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { 'Content-Type': 'application/json' },
})

test('writes include a CSRF token while reads use only same-origin cookies', async () => {
  const calls = []
  const client = createHttpClient(async (url, options) => {
    calls.push({ url, options })
    if (url.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'first' })
    return json({ username: 'member' })
  })
  await client.request('/auth/me')
  await client.request('/auth/register', { method: 'POST', body: { username: 'member' } })
  assert.deepEqual(calls.map(({ url }) => url), ['/api/auth/me', '/api/auth/csrf', '/api/auth/register'])
  assert.equal(calls[0].options.credentials, 'same-origin')
  assert.equal(calls[0].options.headers.get('X-CSRF-TOKEN'), null)
  assert.equal(calls[2].options.headers.get('X-CSRF-TOKEN'), 'first')
  assert.equal(calls[2].options.body, '{"username":"member"}')
})

test('a stale CSRF token is refreshed once before repeating a rejected write', async () => {
  let tokens = 0
  const writes = []
  const client = createHttpClient(async (url, { headers }) => {
    if (url.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: String(++tokens) })
    writes.push([headers.get('X-CSRF-TOKEN'), headers.get('Idempotency-Key')]) // 同时检查两次请求的认证和幂等头。
    if (writes.length === 1) return json({ code: 'CSRF_INVALID' }, 403)
    return new Response(null, { status: 204 })
  })
  await client.request('/orders', { method: 'POST', body: { vehicleId: 1 }, headers: { 'Idempotency-Key': 'same-order' } }) // 模拟下单过程中 CSRF 令牌过期。
  assert.deepEqual(writes, [['1', 'same-order'], ['2', 'same-order']]) // 令牌刷新后请求标识不变。
})

test('a business permission denial is reported without repeating the action', async () => {
  let writes = 0
  const client = createHttpClient(async (url) => {
    if (url.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'first' })
    writes += 1
    return json({ code: 'FORBIDDEN', message: '权限不足' }, 403)
  })
  await assert.rejects(client.request('/admin/users/1/active', { method: 'PUT' }),
    (error) => error instanceof ApiError && error.status === 403 && error.message === '权限不足')
  assert.equal(writes, 1)
})

test('multipart writes preserve file parts and let the browser set its boundary', async () => {
  const form = new FormData()
  form.append('file', new Blob(['content']), 'example.png')
  const client = createHttpClient(async (url, options) => {
    if (url.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'media' })
    assert.equal(options.body, form)
    assert.equal(options.headers.has('Content-Type'), false)
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'media')
    return json({ id: 1 }, 201)
  })
  assert.deepEqual(await client.request('/vehicles/1/attachments', { method: 'POST', body: form }), { id: 1 })
})
