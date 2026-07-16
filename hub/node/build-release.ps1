[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$Version = "0.4.0"
$NodeRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$StagingBase = Join-Path $NodeRoot ".dist-staging"
$PackageRoot = Join-Path $StagingBase "FRCTL-Node-$Version"
$Dist = Join-Path $NodeRoot "dist"
$Archive = Join-Path $Dist "FRCTL-Node-$Version-win.zip"

function Remove-ScopedDirectory([string]$Path, [string]$Expected) {
    if (-not (Test-Path -LiteralPath $Path)) { return }
    $Resolved = (Resolve-Path -LiteralPath $Path).Path
    if ($Resolved -ne [IO.Path]::GetFullPath($Expected)) {
        throw "Отказ удаления неожиданного пути: $Resolved"
    }
    Remove-Item -LiteralPath $Resolved -Recurse -Force
}

Remove-ScopedDirectory -Path $StagingBase -Expected $StagingBase
New-Item -ItemType Directory -Path $PackageRoot -Force | Out-Null

$Items = @(
    "frctl_node", "static", "tests", "README.md", "node-config.example.json",
    "run-node.cmd", "install.ps1", "install-plink.ps1", "configure-ssh-target.ps1", "uninstall.ps1"
)
foreach ($Item in $Items) {
    Copy-Item -LiteralPath (Join-Path $NodeRoot $Item) -Destination $PackageRoot -Recurse -Force
}

Get-ChildItem -LiteralPath $PackageRoot -Recurse -Directory -Filter "__pycache__" | ForEach-Object {
    if (-not $_.FullName.StartsWith($PackageRoot + [IO.Path]::DirectorySeparatorChar)) {
        throw "Отказ удаления неожиданного cache path: $($_.FullName)"
    }
    Remove-Item -LiteralPath $_.FullName -Recurse -Force
}

New-Item -ItemType Directory -Path $Dist -Force | Out-Null
if (Test-Path -LiteralPath $Archive) {
    Remove-Item -LiteralPath $Archive -Force
}
Compress-Archive -LiteralPath $PackageRoot -DestinationPath $Archive -CompressionLevel Optimal
$Hash = (Get-FileHash -LiteralPath $Archive -Algorithm SHA256).Hash
$Size = (Get-Item -LiteralPath $Archive).Length
Remove-ScopedDirectory -Path $StagingBase -Expected $StagingBase

Write-Host "Готово: $Archive" -ForegroundColor Green
Write-Host "Размер: $Size байт"
Write-Host "SHA-256: $Hash"
