/**
 * 验证浏览器刷新、请求重试和完成下单时的请求标识行为。
 * @author pangzhihao
 */
import { test } from 'node:test' // 使用仓库现有的 Node 测试运行器。
import assert from 'node:assert/strict' // 验证生成和值复用规则。
import { orderRequestKey, forgetOrderRequest } from '../src/modules/orders/requestKeys.js' // 调用页面使用的真实逻辑。

test('order retries keep the same key across page reload until success', async (context) => {
  const values = new Map() // 模拟同一标签页下的会话存储。
  const original = globalThis.sessionStorage // 保存测试前的运行时对象。
  globalThis.sessionStorage = {
    getItem: (name) => values.get(name) ?? null, // 读取同一标签页保存的标识。
    setItem: (name, value) => values.set(name, value), // 在重试前保留请求标识。
    removeItem: (name) => values.delete(name), // 正常响应后清理标识。
  }
  context.after(() => { globalThis.sessionStorage = original }) // 测试结束时恢复原存储对象。
  const initial = orderRequestKey(10, 20) // 为买家和车源创建首次下单标识。
  assert.match(initial, /^[a-f0-9]{32}$/) // 核对生成内容符合后端允许的字符和长度。
  assert.equal(orderRequestKey(10, 20), initial) // 同一页重试沿用原标识。
  assert.notEqual(orderRequestKey(11, 20), initial) // 不同买家不会共享标识。
  assert.notEqual(orderRequestKey(10, 21), initial) // 不同车辆不会共享标识。
  const reloaded = await import('../src/modules/orders/requestKeys.js?reload') // 以全新内存模块模拟浏览器页面刷新。
  assert.equal(reloaded.orderRequestKey(10, 20), initial) // 新页面从会话存储恢复旧标识。
  forgetOrderRequest(10, 20, 'obsolete') // 模拟旧响应尝试清理同车的现有请求。
  assert.equal(reloaded.orderRequestKey(10, 20), initial) // 只有同一请求的成功响应可以清除标识。
  reloaded.forgetOrderRequest(10, 20, initial) // 在服务端确认后释放请求意图。
  assert.notEqual(reloaded.orderRequestKey(10, 20), initial) // 下一次主动下单使用独立标识。
})
