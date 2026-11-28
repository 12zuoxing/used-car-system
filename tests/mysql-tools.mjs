/**
 * 对独立回环 MySQL 实例执行测试写入、导出与恢复。
 * @author pangzhihao
 */
import assert from 'node:assert/strict' // 先验证测试实例的参数。
import { execFileSync } from 'node:child_process' // 用参数数组直接调用数据库程序。
import { join } from 'node:path' // 组合跨平台的 MySQL 客户端路径。

const databasePattern = /^usedcar_acceptance_[a-z0-9_]{1,38}$/ // 验收脚本只处理明确命名的独立数据库。
export const database = () => checked(process.env.ACCEPTANCE_MYSQL_DB) // 读取已确认的源库名称。

function checked(value) {
  assert.match(value ?? '', databasePattern, '验收数据库名须以 usedcar_acceptance_ 开头') // 数据库名可安全用作 SQL 标识符。
  return value // 返回已经限制过字符的名称。
}

export function mysql(source, schema = database()) {
  return binary('mysql', ['--batch', '--raw', '--skip-column-names', ...(schema ? [checked(schema)] : [])], source) // 执行 UTF-8 SQL 并输出可机器读取的数据。
}

export function dump(schema = database()) {
  return binary('mysqldump', ['--single-transaction', '--no-tablespaces', '--set-gtid-purged=OFF', checked(schema)]) // 用一致性快照导出当前源库。
}

function binary(name, args, input) {
  const port = Number(process.env.ACCEPTANCE_MYSQL_PORT) // 必须显式指定临时 MySQL 监听端口。
  assert.ok(Number.isInteger(port) && port >= 1024 && port <= 65535, '请明确设置独立 MySQL 测试端口') // 缺少参数时不会连接默认服务。
  const suffix = process.platform === 'win32' ? '.exe' : '' // Windows 上使用 MySQL 可执行文件。
  const executable = process.env.ACCEPTANCE_MYSQL_BIN ? join(process.env.ACCEPTANCE_MYSQL_BIN, name + suffix) : name + suffix // 支持安装目录带空格。
  return execFileSync(executable, ['--no-defaults', '--host=127.0.0.1', `--port=${port}`,
    '--default-character-set=utf8mb4', `--user=${process.env.ACCEPTANCE_MYSQL_USER ?? 'root'}`, ...args],
  { input, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024, timeout: 120_000, windowsHide: true }) // 通过 stdin 传递 SQL，保留 UTF-8 和实际退出状态。
}
