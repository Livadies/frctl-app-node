[CmdletBinding()]
param(
    [switch]$SkipPlink
)

$ErrorActionPreference = "Stop"
$NodeRoot = $PSScriptRoot
$Launcher = Join-Path $NodeRoot "run-node.cmd"
$ConfigDir = Join-Path $env:LOCALAPPDATA "FRCTL"
$ConfigPath = Join-Path $ConfigDir "node-config.json"
$StartMenu = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs"
$ShortcutPath = Join-Path $StartMenu "FRCTL Node.lnk"

if (-not (Test-Path -LiteralPath $Launcher -PathType Leaf)) {
    throw "Не найден launcher: $Launcher"
}

$Python = Get-Command py.exe -ErrorAction SilentlyContinue
if ($Python) {
    & $Python.Source -3 -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)"
} else {
    $Python = Get-Command python.exe -ErrorAction SilentlyContinue
    if (-not $Python) { throw "Установите Python 3.11 или новее" }
    & $Python.Source -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)"
}
if ($LASTEXITCODE -ne 0) { throw "Требуется Python 3.11 или новее" }

New-Item -ItemType Directory -Path $ConfigDir -Force | Out-Null
if (-not (Test-Path -LiteralPath $ConfigPath)) {
    Copy-Item -LiteralPath (Join-Path $NodeRoot "node-config.example.json") -Destination $ConfigPath
}

if (-not $SkipPlink) {
    & (Join-Path $NodeRoot "install-plink.ps1")
}

$Shell = New-Object -ComObject WScript.Shell
$Shortcut = $Shell.CreateShortcut($ShortcutPath)
$Shortcut.TargetPath = $Launcher
$Shortcut.WorkingDirectory = $NodeRoot
$Shortcut.Description = "Локальный доверенный узел FRCTL"
$Shortcut.Save()

Write-Host "FRCTL Node установлен." -ForegroundColor Green
Write-Host "Ярлык: $ShortcutPath"
Write-Host "Конфигурация: $ConfigPath"
Write-Host "Автозапуск не включён. Запустите FRCTL Node из меню Пуск."
