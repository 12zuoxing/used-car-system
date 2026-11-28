# 作者 pangzhihao。将演示附件按数据库 storage_key 复制到后端上传目录。
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string] $Destination, # 传入后端配置 APP_UPLOADS 所指的目录。
    [switch] $IncludeBulk # 导入 bulk-seed.sql 后同时安装 2,000 张批量车源封面。
)

$ErrorActionPreference = 'Stop' # 任一文件校验或复制失败时返回错误。
$assetDirectory = Join-Path $PSScriptRoot 'assets' # 从脚本所在目录读取演示附件。
$files = @(Get-ChildItem -LiteralPath $assetDirectory -File) # 枚举实物附件，保留原名称和扩展名供预览。
if ($files.Count -ne 14) { throw '演示附件应包含 12 张车图和 2 份报告。' }

$uploadDirectory = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Destination) # 转成可用于文件 API 的绝对路径。
$sources = @{} # 以基础附件编号索引原图及报告。
foreach ($file in $files) {
    if ($file.Name -cnotmatch '^(?<id>93\d{4})-[a-z0-9-]+\.(jpg|pdf)$') { throw "演示附件命名有误 $($file.Name)" }
    $id = [int]$Matches.id
    if ($sources.ContainsKey($id)) { throw "演示附件编号重复 $($file.Name)" }
    $sources[$id] = $file.FullName
}

$entries = [ordered]@{} # 每个存储键都关联一份来源文件。
foreach ($id in $sources.Keys) {
    $entries['64656d6f736565640000' + ([string]$id).PadLeft(12, '0')] = $sources[$id] # 与 seed.sql 存储键一致。
}
if ($IncludeBulk) {
    for ($seq = 1; $seq -le 2000; $seq++) {
        $photoId = 930001 + (($seq - 1) % 12) # 与 bulk-seed.sql 的车型轮换顺序一致。
        if (!$sources.ContainsKey($photoId)) { throw "缺少批量封面原图 $photoId" }
        $id = 2100000 + $seq
        $entries['62756c6b746573740000' + ([string]$id).PadLeft(12, '0')] = $sources[$photoId]
    }
}

$sourceHashes = @{} # 复跑时每份原始图片只计算一次校验和。
$copies = foreach ($entry in $entries.GetEnumerator()) {
    $target = Join-Path $uploadDirectory $entry.Key # 后端使用无扩展名存储键查找文件。
    if (Test-Path -LiteralPath $target) {
        if (!$sourceHashes.ContainsKey($entry.Value)) {
            $sourceHashes[$entry.Value] = (Get-FileHash -LiteralPath $entry.Value).Hash
        }
        if (!(Test-Path -LiteralPath $target -PathType Leaf) -or
            (Get-FileHash -LiteralPath $target).Hash -ne $sourceHashes[$entry.Value]) {
            throw "目标存储键已存在且内容不一致 $($entry.Key)" # 保留旧文件，避免覆盖其他资料。
        }
    } else {
        [pscustomobject] @{ Source = $entry.Value; Target = $target } # 等所有文件校验后再写入。
    }
}

[void] [IO.Directory]::CreateDirectory($uploadDirectory) # 首次安装时建立上传目录。
foreach ($copy in $copies) {
    [IO.File]::Copy($copy.Source, $copy.Target, $false) # 原样复制，不覆盖其他会话刚写入的文件。
}
Write-Output "$($entries.Count) 个演示附件已就绪。APP_UPLOADS=$uploadDirectory" # 输出后端应使用的绝对路径。
