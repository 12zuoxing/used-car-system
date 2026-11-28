/**
 * 将一致性 MySQL 导出恢复到另一个空库，复核记录、外键与附件字节。
 * @author pangzhihao
 */
import assert from 'node:assert/strict' // 比较恢复前后的业务数据。
import { createHash } from 'node:crypto' // 核对附件内容而非只看文件名。
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises' // 保留恢复用 SQL 和附件副本。
import { join } from 'node:path' // 组合本地文件路径。
import { database, dump, mysql } from './mysql-tools.mjs' // 使用本机临时测试实例。
import { save } from './http.mjs' // 保存最终恢复证据。

const source = database() // 恢复前数据都来自独立验收数据库。
const target = source + '_restore' // 用新名称写入另一个空库。
const workspace = process.env.ACCEPTANCE_BACKUP_DIR // 备份文件放在独立验收运行目录。
const uploads = process.env.ACCEPTANCE_UPLOADS // 源附件与数据库快照来自同一个测试应用。
assert.ok(workspace && uploads, '请配置验收备份目录和附件目录') // 确保数据库与文件备份能同时验证。
assert.match(target, /^usedcar_acceptance_[a-z0-9_]{1,38}$/) // 在拼接 SQL 前验证新库名。
const exists = mysql(`SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='${target}'`, false).trim() // 确认目标库尚不存在。
assert.equal(exists, '0', '恢复目标必须是新空库') // 防止覆盖先前数据。
await mkdir(workspace, { recursive: true }) // 备份只写入指定工作目录。
const sql = dump(source) // 应用停写后读取一致性快照。
await writeFile(join(workspace, 'usedcar.sql'), sql) // 将 SQL 留作可实际恢复的文件。
mysql(`CREATE DATABASE \`${target}\` CHARACTER SET utf8mb4`, false) // 创建单独的恢复库。
mysql(await readFile(join(workspace, 'usedcar.sql')), target) // 从真实导出文件写入空库。
const tableNames = mysql('SHOW TABLES', source).trim().split(/\r?\n/) // 读取现有的全部业务表。
assert.equal(tableNames.length, 16) // 核对计划中的 16 张表。
assert.ok(tableNames.every((table) => /^[a-z_]+$/.test(table))) // 确认后续 SQL 标识符来自正常应用表。
assert.deepEqual(mysql('SHOW TABLES', target).trim().split(/\r?\n/), tableNames) // 目标库拥有相同表结构集合。
const checksums = 'CHECKSUM TABLE ' + tableNames.join(',') + ' EXTENDED' // 让 MySQL 对实际行内容计算校验和。
const original = mysql(checksums, source).trim().split(/\r?\n/).map((row) => row.slice(row.indexOf('.') + 1)) // 保留不依赖源库名的结果。
const restored = mysql(checksums, target).trim().split(/\r?\n/).map((row) => row.slice(row.indexOf('.') + 1)) // 用相同方法校验恢复库。
assert.ok(original.every((row) => !row.endsWith('\tNULL'))) // 每张表都应产生实际校验和。
assert.deepEqual(restored, original) // 确认结构导入与数据内容完全对应。
const counts = Object.fromEntries(mysql(tableNames.map((name) => `SELECT '${name}', COUNT(*) FROM ${name}`).join(' UNION ALL '), target)
  .trim().split(/\r?\n/).map((row) => row.split('\t')).map(([name, total]) => [name, Number(total)])) // 记录恢复库各表行数。
const relationships = {
  vehicleLocks: 'vehicle_lock l JOIN trade_order o ON o.id=l.order_id AND o.vehicle_id=l.vehicle_id',
  consignmentOrders: 'trade_order o JOIN consignment c ON c.id=o.consignment_id AND c.vehicle_id=o.vehicle_id',
  loanOrders: 'loan_application a JOIN trade_order o ON o.id=a.order_id AND o.buyer_id=a.applicant_id',
  afterSaleOrders: 'repair_order r JOIN trade_order o ON o.id=r.trade_order_id AND o.vehicle_id=r.vehicle_id AND o.buyer_id=r.applicant_id',
  reportFiles: 'attachment a JOIN vehicle_report r ON r.id=a.report_id AND r.vehicle_id=a.vehicle_id',
} // 复核跨模块关键业务关系，既看条数也检查外键匹配。
const relations = {}
for (const [name, from] of Object.entries(relationships)) {
  const matching = Number(mysql('SELECT COUNT(*) FROM ' + from, target).trim()) // 比较恢复后的实际关联数。
  assert.ok(matching > 0) // 每条关联路径必须有真实数据参与验证。
  assert.equal(matching, Number(mysql('SELECT COUNT(*) FROM ' + from, source).trim())) // 与导出前保持一致。
  relations[name] = matching // 保存关系数量作为证据。
}
const keys = mysql('SELECT storage_key FROM attachment ORDER BY id', target).trim().split(/\r?\n/) // 从恢复库读取附件引用。
assert.ok(keys.length > 0 && keys.every((key) => /^[a-f0-9]{32}$/.test(key))) // 文件键必须与已有存储约定一致。
const restoredFiles = join(workspace, 'uploads') // 与 SQL 备份一起保留实际附件副本。
await mkdir(restoredFiles, { recursive: true }) // 为文件恢复准备独立目录。
const hash = (buffer) => createHash('sha256').update(buffer).digest('hex') // 同一算法比较二进制内容。
for (const key of keys) {
  await copyFile(join(uploads, key), join(restoredFiles, key)) // 逐个恢复数据库真正引用的文件。
  assert.equal(hash(await readFile(join(restoredFiles, key))), hash(await readFile(join(uploads, key)))) // 比较实际字节。
}
await save('recovery.json', { tables: tableNames.length, rowCounts: counts, matchingChecksums: original.length,
  relations, attachmentHashesVerified: keys.length, dumpBytes: Buffer.byteLength(sql) }) // 仅提交结果，运行目录保留备份文件。
console.log(`恢复验证通过，${tableNames.length} 张表和 ${keys.length} 个附件一致。`) // 显示实际校验数量。
