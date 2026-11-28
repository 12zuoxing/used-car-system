# 作者 pangzhihao。使用独立 MySQL 和回环端口验证实际部署成品。
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $MySqlHome, # MySQL 安装目录。
    [Parameter(Mandatory)][string] $ChromePath, # 截图使用的 Chrome 程序。
    [string] $BuildRoot, # 可指定独立构建目录，默认使用项目目录。
    [string] $EvidenceDir = '.runtime/acceptance-evidence' # 保存 JSON 与 PNG。
)
$ErrorActionPreference = 'Stop' # 任一验收失败中止后续，finally 清理进程。
$project = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path # 定位项目。
$build = if ($BuildRoot) { (Resolve-Path -LiteralPath $BuildRoot).Path } else { $project } # 从选定的成品目录启动。
$jar = (Resolve-Path -LiteralPath (Join-Path $build 'backend/target/used-car-system-1.0.0.jar')).Path # 验证后端已打包。
$frontend = (Resolve-Path -LiteralPath (Join-Path $build 'frontend')).Path # 读取实测前端构建。
[void](Resolve-Path -LiteralPath (Join-Path $frontend 'dist/index.html')) # 验证前端已打包。
$mysqlHomePath = (Resolve-Path -LiteralPath $MySqlHome).Path # 拿到独立数据库程序路径。
$mysqlBin = Join-Path $mysqlHomePath 'bin' # 从同一安装目录选取 SQL 工具。
$chromeExe = (Resolve-Path -LiteralPath $ChromePath).Path # Chrome 用户可指定位置。
$javaExe = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe).Source } # 优先使用已配置的 Java。
$nodeExe = (Get-Command node.exe).Source # 从 PATH 获取 Node。
$directory = New-Item -ItemType Directory -Path (Join-Path $project '.runtime') -Force # 保存忽略的验收中间产物。
$runId = [Guid]::NewGuid().ToString('N').Substring(0, 12) # 为各次运行隔离目录和数据库。
$run = (New-Item -ItemType Directory -Path (Join-Path $directory.FullName "m9-$runId")).FullName # 日志和备份保存在此。
$tempRoot = (Resolve-Path -LiteralPath ([IO.Path]::GetTempPath())).Path # 临时实例数据使用宿主系统临时目录。
$dbRoot = (New-Item -ItemType Directory -Path (Join-Path $tempRoot "usedcar-m9-$runId")).FullName # 创建新的 MySQL 实例目录。
$allocated = [Collections.Generic.HashSet[int]]::new() # 避免本轮测试内端口冲突。
$workers = @() # 仅清理脚本自身启动的进程。
$oldEnv = @{} # 保留测试之前的环境值。
$server = $null # 没有成功启动时跳过实例关闭。

function New-LocalPort {
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $port = Get-Random -Minimum 20000 -Maximum 39000 # 探测非典型系统临时端口区间。
        if (!$allocated.Add($port)) { continue } # 排除已在本次运行中使用的端口。
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port) # 只绑定本机。
        try {
            $listener.ExclusiveAddressUse = $true # 检查能否独占监听。
            $listener.Start()
            return $port # 返回能实际绑定的端口。
        } catch [Net.Sockets.SocketException] {
            continue # 尝试下一个空闲端口。
        } finally {
            $listener.Stop() # 释放探测连接。
        }
    }
    throw '无法获得空闲回环端口。' # 明确告知启动环境冲突。
}

function Invoke-NodeStep([string] $file) {
    & $nodeExe (Join-Path $PSScriptRoot $file) # 启动仓库的可复跑验收脚本。
    if ($LASTEXITCODE -ne 0) { throw "验收步骤失败 $file" } # 保留实际断言失败。
}

function Stop-Owned($process) {
    if ($process -and !$process.HasExited) {
        $process.Kill($true) # 只结束对应的自有进程。
        [void]$process.WaitForExit(10000) # 等待释放端口和文件。
    }
}

function Set-RunEnv([hashtable] $variables) {
    foreach ($pair in $variables.GetEnumerator()) {
        if (!$oldEnv.ContainsKey($pair.Key)) { $oldEnv[$pair.Key] = [Environment]::GetEnvironmentVariable($pair.Key) } # 保存调用前原值。
        [Environment]::SetEnvironmentVariable($pair.Key, [string]$pair.Value) # 仅改变当前运行及子进程。
    }
}

function Start-Backend([string] $prefix) {
    return Start-Process -FilePath $javaExe -ArgumentList '-jar', ('"' + $jar + '"') -WorkingDirectory $run -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $run "$prefix.log") -RedirectStandardError (Join-Path $run "$prefix.err") # 使用当前环境启动构建产物。
}

Push-Location -LiteralPath $project # 输出路径从应用根目录解析。
try {
    $dbPort = New-LocalPort # MySQL 临时端口。
    $backendPort = New-LocalPort # 后端临时端口。
    $sitePort = New-LocalPort # 前端同源代理端口。
    $debugPort = New-LocalPort # Chrome 本机调试端口。
    $database = "usedcar_acceptance_$runId" # 数据库名确保与用户库隔离。
    $mysql = Join-Path $mysqlBin 'mysql.exe' # 选择实际安装的客户端。
    $mysqld = Join-Path $mysqlBin 'mysqld.exe' # 选择同版服务器。
    $myLog = Join-Path $dbRoot 'mysql.log' # 实例日志。
    $arguments = @('--no-defaults', ('--basedir="' + $mysqlHomePath + '"'),
        ('--datadir="' + (Join-Path $dbRoot 'data') + '"'), ('--log-error="' + $myLog + '"')) # 忽略机器现有实例配置。
    $init = Start-Process -FilePath $mysqld -ArgumentList ($arguments + '--initialize-insecure') -PassThru -WindowStyle Hidden # 为本机专用验收库初始化。
    if (!$init.WaitForExit(45000)) { Stop-Owned $init; throw 'MySQL 初始化超时。' } # 取消卡住的初始化任务。
    if ($init.ExitCode -ne 0) { throw "MySQL 初始化失败，请查看 $myLog" } # 初始化后才能启动。
    $server = Start-Process -FilePath $mysqld -ArgumentList ($arguments + @("--port=$dbPort", '--bind-address=127.0.0.1', '--mysqlx=OFF', '--default-time-zone=+08:00')) -PassThru -WindowStyle Hidden # 仅监听回环并使用北京时间。
    $dbArgs = @('--no-defaults', '--host=127.0.0.1', "--port=$dbPort", '--user=root', '--default-character-set=utf8mb4') # 命令仅访问专用实例。
    $deadline = (Get-Date).AddSeconds(25) # 等待数据库准备好。
    do {
        $version = & $mysql @dbArgs '--connect-timeout=1' '-N' '-e' 'SELECT VERSION()' 2>&1 # 验证实际可连接。
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Milliseconds 250 # 保留准备时间。
    } while ((Get-Date) -lt $deadline -and !$server.HasExited)
    if ($LASTEXITCODE -ne 0) { throw "MySQL 连接失败，请查看 $myLog" } # 连接异常时停在建库前。
    & $mysql @dbArgs '-e' "CREATE DATABASE $database CHARACTER SET utf8mb4" # 创建测试空库。
    if ($LASTEXITCODE -ne 0) { throw '无法创建验收数据库。' } # 校验实际命令结果。
    $dbPassword = [Guid]::NewGuid().ToString('N') # 应用使用仅供此次独立实例的随机口令。
    $credentialSql = "CREATE USER 'usedcar_app'@'localhost' IDENTIFIED BY '$dbPassword'; GRANT ALL ON $database.* TO 'usedcar_app'@'localhost'; GRANT ALL ON $($database)_restore.* TO 'usedcar_app'@'localhost';" # 应用仅访问两套受控数据库。
    $credentialSql | & $mysql @dbArgs # 通过标准输入配置账号，避免口令出现在进程参数。
    if ($LASTEXITCODE -ne 0) { throw '无法准备验收应用账号。' } # 异常时不再尝试启动。
    Write-Host "MySQL $version，验收运行 $runId" # 显示实际实例版本。
    $password = if ($env:DEMO_PASSWORD) { $env:DEMO_PASSWORD } else { [Guid]::NewGuid().ToString('N') } # 自动生成一次性口令时不落库明文。
    $uploads = Join-Path $run 'uploads' # 测试附件的独立目录。
    Set-RunEnv @{
        DEMO_PASSWORD = $password; DEMO_ADMIN_USERNAME = 'demo_admin'; DEMO_ADMIN_PASSWORD = $password # 让演示脚本取得临时凭据。
        APP_ADMIN_USERNAME = 'demo_admin'; APP_ADMIN_PASSWORD = $password; APP_ADMIN_DISPLAY_NAME = '演示管理员' # 空库初始管理员。
        APP_PORT = "$backendPort"; APP_BIND = '127.0.0.1'; APP_SECURE_COOKIE = 'false'; APP_API_DOCS_ENABLED = 'true' # 回环 HTTP 演示。
        SPRING_PROFILES_ACTIVE = ''; SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:$dbPort/$($database)?sslMode=REQUIRED" # 使用真实 MySQL。
        SPRING_DATASOURCE_USERNAME = 'usedcar_app'; SPRING_DATASOURCE_PASSWORD = $dbPassword # 使用独立应用凭据。
        SPRING_SQL_INIT_MODE = 'always'; SPRING_SQL_INIT_DATA_LOCATIONS = 'classpath:demo-data.sql'; APP_UPLOADS = $uploads # 初始化空库和字典。
        VITE_BACKEND = "http://127.0.0.1:$backendPort"; ACCEPTANCE_BASE = "http://127.0.0.1:$sitePort"; ACCEPTANCE_CHROME = "http://127.0.0.1:$debugPort" # 同源浏览器与验收接口。
        ACCEPTANCE_OUTPUT = [IO.Path]::GetFullPath($EvidenceDir, $project) # 用明确的绝对路径保存证据。
        ACCEPTANCE_MYSQL_DB = $database; ACCEPTANCE_MYSQL_BIN = $mysqlBin; ACCEPTANCE_MYSQL_PORT = "$dbPort"; ACCEPTANCE_MYSQL_USER = 'root'; MYSQL_PWD = '' # SQL 操作共享隔离实例。
        ACCEPTANCE_BACKUP_DIR = (Join-Path $run 'backup'); ACCEPTANCE_UPLOADS = $uploads # 单独保存备份及恢复的附件。
    }
    $java = Start-Backend 'backend'; $workers += $java # 从空 MySQL 库启动成品。
    $workers += Start-Process -FilePath $nodeExe -ArgumentList 'node_modules/vite/bin/vite.js', 'preview', '--host', '127.0.0.1', '--port', "$sitePort", '--strictPort' -WorkingDirectory $frontend -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $run 'frontend.log') -RedirectStandardError (Join-Path $run 'frontend.err') # 代理真实前端构建。
    $profile = '--user-data-dir="' + (Join-Path $run 'chrome') + '"' # 浏览器使用隔离的测试会话。
    $chrome = Start-Process -FilePath $chromeExe -ArgumentList '--headless=new', "--remote-debugging-port=$debugPort", '--remote-debugging-address=127.0.0.1', $profile, '--no-first-run', 'about:blank' -PassThru -WindowStyle Hidden # 采集真实界面。
    $workers += $chrome # 验收结束后关闭独立 Chrome。
    Invoke-NodeStep 'demo.mjs' # 创建演示账号和车源。
    Invoke-NodeStep 'acceptance.mjs' # 运行直销、代销以及 20 并发下单。
    Invoke-NodeStep 'browser.mjs' # 保存答辩截图并回归已模拟支付订单取消。
    Stop-Owned $chrome # 负载测试只测部署服务。
    Invoke-NodeStep 'performance.mjs' # 测量万条数据与 100 并发查询。
    Stop-Owned $java # 停写后再对数据库和附件一并备份。
    Invoke-NodeStep 'recovery.mjs' # 恢复到独立空库并校验行及文件。
    $nextPort = New-LocalPort # 为恢复后的服务启动新端口。
    Set-RunEnv @{
        APP_PORT = "$nextPort"; SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:$dbPort/$($database)_restore?sslMode=REQUIRED" # 使用实际恢复库。
        SPRING_SQL_INIT_MODE = 'never'; APP_UPLOADS = (Join-Path $env:ACCEPTANCE_BACKUP_DIR 'uploads') # 只读取恢复的表与附件。
        ACCEPTANCE_BASE = "http://127.0.0.1:$nextPort" # 将应用恢复验证指向新后端。
    }
    $workers += Start-Backend 'restored' # 启动已恢复的数据。
    Invoke-NodeStep 'recovered-http.mjs' # 用 HTTP 验证恢复后的车源和实际图片。
    Write-Host "验收证据位于 $env:ACCEPTANCE_OUTPUT" # 展示输出位置。
} finally {
    foreach ($process in $workers) { Stop-Owned $process } # 关闭本次启动的服务进程。
    if ($server -and !$server.HasExited) {
        & (Join-Path $mysqlBin 'mysqladmin.exe') @dbArgs shutdown 2>$null # 只关闭本次临时数据库。
        if (!$server.WaitForExit(10000)) { Stop-Owned $server } # 若超时再停止独立实例。
    }
    if ($dbRoot.StartsWith($tempRoot.TrimEnd('\') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path $dbRoot -Leaf) -eq "usedcar-m9-$runId") {
        if (Test-Path -LiteralPath (Join-Path $dbRoot 'mysql.log')) { Copy-Item -LiteralPath (Join-Path $dbRoot 'mysql.log') -Destination (Join-Path $run 'mysql.log') } # 保留诊断日志。
        Remove-Item -LiteralPath $dbRoot -Recurse -Force # 清理路径已确认的临时实例。
    }
    foreach ($key in $oldEnv.Keys) { [Environment]::SetEnvironmentVariable($key, $oldEnv[$key]) } # 恢复运行前环境。
    Pop-Location # 还原调用目录。
}
