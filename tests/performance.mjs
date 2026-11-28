/**
 * 在独立 MySQL 中建立 10,000 条构造车源，实测分页接口的端到端延迟。
 * @author pangzhihao
 */
import assert from 'node:assert/strict' // 让延迟门槛和 HTTP 错误成为验收条件。
import { availableParallelism, cpus, release, totalmem } from 'node:os' // 记录本轮采样环境。
import { origin, save } from './http.mjs' // 输出可以重复比较的证据。
import { mysql } from './mysql-tools.mjs' // 使用隔离库准备数据并执行 EXPLAIN。

const marker = 'module9-performance-fixture' // 区分负载数据与少量业务合成记录。
const count = Number(mysql(`SELECT COUNT(*) FROM vehicle WHERE description='${marker}'`).trim()) // 允许在同一验收库重复压测。
assert.ok([0, 10000].includes(count), '压测数据必须为空或整批存在') // 防止把不完整的数据误用为基准。
const seed = () => mysql(`
  INSERT INTO brand (name, active) VALUES ('验收负载 A', TRUE), ('验收负载 B', TRUE)
  ON DUPLICATE KEY UPDATE name=brand.name;
  INSERT INTO city (name, active) VALUES ('验收区域 A', TRUE), ('验收区域 B', TRUE)
  ON DUPLICATE KEY UPDATE name=city.name;
  INSERT INTO vehicle (owner_id, brand_id, city_id, model, registration_year, mileage, price,
    transmission, fuel, sale_mode, description, status, reviewer_id, reviewed_at, listed_at)
  WITH digits AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9),
    source AS (SELECT a.n + b.n*10 + c.n*100 + d.n*1000 AS n FROM digits a CROSS JOIN digits b CROSS JOIN digits c CROSS JOIN digits d)
  SELECT seller.id, brand.id, city.id, CONCAT('验收车辆 ', LPAD(source.n,4,'0')),
    2018+MOD(source.n,7), 15000+MOD(source.n*23,90000), 42000+MOD(source.n*37,110000),
    'AUTO','PETROL','DIRECT','${marker}','LISTED',staff.id,CURRENT_TIMESTAMP,
    TIMESTAMPADD(SECOND,-source.n,CURRENT_TIMESTAMP)
  FROM source
  JOIN app_user seller ON seller.username='demo_seller'
  JOIN app_user staff ON staff.username='demo_staff'
  JOIN brand ON brand.name=IF(MOD(source.n,2)=0,'验收负载 A','验收负载 B')
  JOIN city ON city.name=IF(MOD(source.n,5)<2,'验收区域 A','验收区域 B');
`) // 用单条批量插入构造，不把造数时间计入 HTTP 采样。
if (!count) seed() // 独立库仅需要生成一次完整数据。
assert.equal(Number(mysql(`SELECT COUNT(*) FROM vehicle WHERE description='${marker}'`).trim()), 10000) // 验证数据集真实行数。
mysql('ANALYZE TABLE vehicle;') // 让 MySQL 根据当前数据量更新优化器统计。
const [brandId, cityId] = mysql("SELECT (SELECT id FROM brand WHERE name='验收负载 A'),(SELECT id FROM city WHERE name='验收区域 A')")
  .trim().split('\t').map(Number) // 联合过滤用真实字典编号。
const url = origin() // 通过实际同源 HTTP 路径测量。
const scenarios = [
  { name: 'market', params: {} }, // 公开车源列表与排序。
  { name: 'combined', params: { brandId, cityId, minPrice: 55000, maxPrice: 115000, minYear: 2020, maxMileage: 95000, saleMode: 'DIRECT' } }, // 联合筛选。
]
const totalByScenario = new Map() // 在并发前记录各场景稳定的总数。

function path(params, page) {
  return url + '/api/vehicles?' + new URLSearchParams({ ...params, page, pageSize: 12 }) // 分别读取前五页。
}

for (const scenario of scenarios) {
  const result = await fetch(path(scenario.params, 1)).then((response) => response.json()) // 建立每个筛选场景的预计总数。
  assert.ok(result.total > 60, '每种场景须有超过五页数据') // 测试始终读取真实分页结果。
  totalByScenario.set(scenario.name, result.total) // 后续校验响应总数与分页字段。
}

async function hit(index) {
  const scenario = scenarios[index % scenarios.length] // 让两类请求数量相同。
  const page = (index % 5) + 1 // 在第 1 至 5 页中轮询。
  const started = performance.now() // 包含请求调度、后端、网络、读取和 JSON 解析。
  try {
    const response = await fetch(path(scenario.params, page), { signal: AbortSignal.timeout(15_000) }) // 对挂起的请求作超时处理。
    const json = await response.json() // 计入响应体解析时间。
    const ok = response.ok && json.page === page && json.pageSize === 12 && json.items.length === 12 && json.total === totalByScenario.get(scenario.name) // 同时检查正确性与响应速度。
    return { scenario: scenario.name, status: response.status, ok, ms: performance.now() - started } // 保存实际 HTTP 返回。
  } catch {
    return { scenario: scenario.name, status: 0, ok: false, ms: performance.now() - started } // 将网络和超时记为错误样本。
  }
}
await Promise.all(Array.from({ length: 20 }, (_, i) => hit(i))) // 预热连接池和常用 SQL，预热样本不合并。
const samples = [] // 保存全部正式样本以便独立复算分位数。
for (let round = 1; round <= 5; round++) {
  const results = await Promise.all(Array.from({ length: 100 }, (_, i) => hit(i))) // 每轮同时发出 100 个分页请求。
  samples.push(...results.map((item) => ({ round, ...item, ms: Number(item.ms.toFixed(2)) }))) // 保留每个样本，而非只保存汇总。
}
function summarize(results) {
  const values = results.map((item) => item.ms).sort((left, right) => left - right) // 排序得到分位数。
  const errors = results.filter((item) => !item.ok).length // 业务格式错误与 HTTP 错误均计入。
  return { requests: results.length, errors, errorRate: errors / results.length,
    p50ms: values[Math.ceil(values.length * 0.5) - 1], p95ms: values[Math.ceil(values.length * 0.95) - 1],
    p99ms: values[Math.ceil(values.length * 0.99) - 1], maxMs: values.at(-1) } // 使用 nearest-rank 算法。
}
const inMarket = "v.status='LISTED' AND (v.sale_mode='DIRECT' OR EXISTS (SELECT 1 FROM consignment c WHERE c.vehicle_id=v.id AND c.owner_id=v.owner_id AND c.status='APPROVED' AND c.starts_at<=CURRENT_TIMESTAMP AND c.expires_at>CURRENT_TIMESTAMP))" // 保留市场 SQL 的委托约束。
const explain = (where) => mysql(`EXPLAIN SELECT v.id,v.model,b.name,c.name,
  (SELECT MIN(a.id) FROM attachment a WHERE a.vehicle_id=v.id AND a.kind='PHOTO') AS cover_photo_id
  FROM vehicle v JOIN brand b ON b.id=v.brand_id JOIN city c ON c.id=v.city_id
  WHERE ${inMarket} ${where} ORDER BY v.listed_at DESC, v.id DESC LIMIT 12`).trim().split(/\r?\n/).map((row) => row.split('\t')) // 保存与分页同等过滤和排序的关键查询计划。
const summary = summarize(samples) // 计算 500 个正式请求的合并结果。
const cases = Object.fromEntries(scenarios.map((scenario) => [scenario.name,
  { total: totalByScenario.get(scenario.name), ...summarize(samples.filter((item) => item.scenario === scenario.name)) }])) // 比较简单和联合筛选两类场景。
await save('performance.json', { environment: { platform: release(), node: process.version, cpu: cpus()[0].model,
  logicalProcessors: availableParallelism(), memoryGiB: Math.round(totalmem() / 1024 ** 3),
  mysql: mysql('SELECT VERSION()').trim() }, dataset: 10000, pageSize: 12, concurrency: 100, rounds: 5,
  summary, cases, sample: samples, reason: '本机 HTTP 测试，包含两类公开分页请求',
  explainColumns: ['id', 'select_type', 'table', 'partitions', 'type', 'possible_keys', 'key', 'key_len', 'ref', 'rows', 'filtered', 'Extra'], // 提供原始执行计划的列定义。
  plans: { market: explain(''), combined: explain(`AND v.sale_mode='DIRECT' AND v.brand_id=${brandId} AND v.city_id=${cityId} AND v.price BETWEEN 55000 AND 115000 AND v.registration_year>=2020 AND v.mileage<=95000`) },
  indexes: mysql('SHOW INDEX FROM vehicle').trim().split(/\r?\n/).map((row) => row.split('\t')[2]).filter((key, index, values) => values.indexOf(key) === index)
}) // 输出原始样本、索引列表和计划便于复跑核对。
console.log(JSON.stringify(summary)) // 在运行日志上输出压测结论。
assert.ok(summary.errors === 0 && summary.p95ms <= 2000 && Object.values(cases).every((item) => item.p95ms <= 2000),
  '正式采样须零错误，所有查询场景 P95 不高于 2 秒') // 超标时保留结果供诊断。
