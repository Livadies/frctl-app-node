[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$Source = Join-Path $PSScriptRoot "FRCTL-Windows-0.4.0.exe"
$InstallDir = Join-Path $env:LOCALAPPDATA "Programs\FRCTL"
$Target = Join-Path $InstallDir "FRCTL.exe"
$StartMenu = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs"
$ShortcutPath = Join-Path $StartMenu "FRCTL.lnk"

if (-not (Test-Path -LiteralPath $Source -PathType Leaf)) { throw "Не найден $Source" }
New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
Copy-Item -LiteralPath $Source -Destination $Target -Force

foreach ($Helper in "install-plink.ps1", "configure-ssh-target.ps1") {
    $HelperSource = Join-Path $PSScriptRoot $Helper
    if (Test-Path -LiteralPath $HelperSource) { Copy-Item -LiteralPath $HelperSource -Destination $InstallDir -Force }
}

$Shell = New-Object -ComObject WScript.Shell
$Shortcut = $Shell.CreateShortcut($ShortcutPath)
$Shortcut.TargetPath = $Target
$Shortcut.WorkingDirectory = $InstallDir
$Shortcut.Description = "FRCTL — приложения, ИИ-модели и защищённые подключения"
$Shortcut.Save()

Write-Host "FRCTL для Windows установлен." -ForegroundColor Green
Write-Host "Ярлык: $ShortcutPath"
Write-Host "Данные: $env:LOCALAPPDATA\FRCTL"
