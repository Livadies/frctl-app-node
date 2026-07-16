[CmdletBinding(SupportsShouldProcess)]
param(
    [switch]$RemoveData
)

$ErrorActionPreference = "Stop"
$ShortcutPath = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\FRCTL Node.lnk"
$DataPath = Join-Path $env:LOCALAPPDATA "FRCTL"

if (Test-Path -LiteralPath $ShortcutPath) {
    Remove-Item -LiteralPath $ShortcutPath -Force
}

if ($RemoveData -and (Test-Path -LiteralPath $DataPath)) {
    $Resolved = (Resolve-Path -LiteralPath $DataPath).Path
    $Expected = [System.IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA "FRCTL"))
    if ($Resolved -ne $Expected) { throw "Отказ удаления неожиданного пути: $Resolved" }
    if ($PSCmdlet.ShouldProcess($Resolved, "Удалить конфигурацию и аудит FRCTL")) {
        Remove-Item -LiteralPath $Resolved -Recurse -Force
    }
}

Write-Host "Ярлык FRCTL Node удалён." -ForegroundColor Green
if (-not $RemoveData) {
    Write-Host "Локальные настройки и аудит сохранены: $DataPath"
}

