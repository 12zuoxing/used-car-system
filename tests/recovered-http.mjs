/**
 * 从恢复后的真实应用读取在售车和图片。
 * @author pangzhihao
 */
import assert from 'node:assert/strict' // 检查恢复后的业务数据。
import { readFile } from 'node:fs/promises' // 读取原证据与样图。
import { join } from 'node:path' // 在证据目录追加实际读取结果。
import { client, origin, save, until } from './http.mjs' // 使用正常公开接口。
import { mysql } from './mysql-tools.mjs' // 找到备份中保留的演示车源。

const api = client(origin()) // 连接恢复库启动的后端。
await until(async () => (await api.call('/api/dictionaries')).ok, '恢复后的应用启动') // 先等待 MySQL 连接和 HTTP 可用。
const id = Number(mysql("SELECT id FROM vehicle WHERE model='卡罗拉 · 直销演示'").trim()) // 定位原始公开样本。
const data = await api.expect('/api/vehicles/' + id) // 从恢复后的后端获取资源。
assert.equal(data.model, '卡罗拉 · 直销演示') // 确认中文字段正确。
const response = await api.call('/api/attachments/' + data.coverPhotoId) // 读取恢复后的实际附件。
assert.equal(response.status, 200) // 确认对应附件可访问。
const expected = await readFile(new URL('../frontend/public/images/sedan.jpg', import.meta.url)) // 比较最初的样图。
assert.deepEqual(Buffer.from(await response.arrayBuffer()), expected) // 核对通过 HTTP 下载的实际字节。
const dir = process.env.ACCEPTANCE_OUTPUT ?? '.runtime/acceptance-evidence' // 追加应用层恢复证据。
const evidence = JSON.parse(await readFile(join(dir, 'recovery.json'), 'utf8')) // 保留先前校验数据。
await save('recovery.json', { ...evidence, applicationRead: { vehicleId: id, coverPhotoId: data.coverPhotoId, photoBytes: expected.byteLength } }) // 记录复测实际行与文件。
console.log('恢复后的应用读出了在售车源及原始照片。') // 报告应用层读取结果。
