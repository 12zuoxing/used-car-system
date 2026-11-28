# 作者 pangzhihao。将演示附件按数据库 storage_key 复制到后端上传目录。
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string] $Destination # 传入后端配置 APP_UPLOADS 所指的目录。
)

$ErrorActionPreference = 'Stop' # 任一文件校验或复制失败时返回错误。
$assetDirectory = Join-Path $PSScriptRoot 'assets' # 从脚本所在目录读取演示附件。
$files = @(Get-ChildItem -LiteralPath $assetDirectory -File) # 枚举实物附件，保留原名称和扩展名供预览。
if ($files.Count -ne 14) { throw '演示附件应包含 12 张车图和 2 份报告。' }

$uploadDirectory = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Destination) # 转成可用于文件 API 的绝对路径。
$keys = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal) # 防止多个文件映射到同一个存储键。
$copies = foreach ($file in $files) {
    if ($file.Name -cnotmatch '^(?<id>93\d{4})-[a-z0-9-]+\.(jpg|pdf)$') { throw "演示附件命名有误 $($file.Name)" }
    $key = '64656d6f736565640000' + $Matches.id.PadLeft(12, '0') # 与 seed.sql 的 storage_key 表达式一致。
    if (!$keys.Add($key)) { throw "演示附件编号重复 $($file.Name)" }
    $target = Join-Path $uploadDirectory $key # 后端使用无扩展名存储键查找文件。
    if (Test-Path -LiteralPath $target) {
        if (!(Test-Path -LiteralPath $target -PathType Leaf) -or
            (Get-FileHash -LiteralPath $target).Hash -ne (Get-FileHash -LiteralPath $file.FullName).Hash) {
            throw "目标存储键已存在且内容不一致 $key" # 保留旧文件，避免覆盖其他资料。
        }
    } else {
        [pscustomobject] @{ Source = $file.FullName; Target = $target } # 等所有文件校验后再写入。
    }
}

[void] [IO.Directory]::CreateDirectory($uploadDirectory) # 首次安装时建立上传目录。
foreach ($copy in $copies) {
    [IO.File]::Copy($copy.Source, $copy.Target, $false) # 原样复制，不覆盖其他会话刚写入的文件。
}
Write-Output "14 个演示附件已就绪。APP_UPLOADS=$uploadDirectory" # 输出后端应使用的绝对路径。
