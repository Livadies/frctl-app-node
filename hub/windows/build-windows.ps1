[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$Version = "0.4.0"
$Root = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$NodeRoot = (Resolve-Path -LiteralPath (Join-Path $Root "..\node")).Path
$Venv = Join-Path $Root ".venv"
$Dist = Join-Path $Root "dist"
$Work = Join-Path $Root ".build"

if (-not (Test-Path -LiteralPath (Join-Path $Venv "Scripts\python.exe"))) {
    python -m venv $Venv
}
$Python = Join-Path $Venv "Scripts\python.exe"
& $Python -m pip install --disable-pip-version-check -r (Join-Path $Root "requirements-build.txt")
if ($LASTEXITCODE -ne 0) { throw "Не удалось установить build dependencies" }

New-Item -ItemType Directory -Path $Dist -Force | Out-Null
& $Python -m PyInstaller `
    --noconfirm --clean --onefile --windowed `
    --name "FRCTL-Windows-$Version" `
    --distpath $Dist --workpath $Work `
    --specpath $Work `
    --paths $NodeRoot `
    --add-data "$(Join-Path $NodeRoot 'static');static" `
    --collect-all webview `
    (Join-Path $Root "frctl_windows.py")
if ($LASTEXITCODE -ne 0) { throw "PyInstaller build failed" }

$PackageRoot = [System.IO.Path]::GetFullPath((Join-Path $Work "FRCTL-Windows-$Version"))
$WorkPrefix = $Work.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
if (-not $PackageRoot.StartsWith($WorkPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Небезопасный путь временного пакета: $PackageRoot"
}
if (Test-Path -LiteralPath $PackageRoot) { Remove-Item -LiteralPath $PackageRoot -Recurse -Force }
New-Item -ItemType Directory -Path $PackageRoot -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $Dist "FRCTL-Windows-$Version.exe") -Destination $PackageRoot
Copy-Item -LiteralPath (Join-Path $Root "install-windows.ps1") -Destination $PackageRoot
Copy-Item -LiteralPath (Join-Path $NodeRoot "install-plink.ps1") -Destination $PackageRoot
Copy-Item -LiteralPath (Join-Path $NodeRoot "configure-ssh-target.ps1") -Destination $PackageRoot
Copy-Item -LiteralPath (Join-Path $Root "README.md") -Destination $PackageRoot
$Archive = Join-Path $Dist "FRCTL-Windows-$Version.zip"
if (Test-Path -LiteralPath $Archive) { Remove-Item -LiteralPath $Archive -Force }
Compress-Archive -LiteralPath $PackageRoot -DestinationPath $Archive -CompressionLevel Optimal

$Exe = Join-Path $Dist "FRCTL-Windows-$Version.exe"
Write-Host "EXE: $Exe" -ForegroundColor Green
Write-Host "ZIP: $Archive" -ForegroundColor Green
Write-Host "SHA-256 EXE: $((Get-FileHash -LiteralPath $Exe -Algorithm SHA256).Hash)"
Write-Host "SHA-256 ZIP: $((Get-FileHash -LiteralPath $Archive -Algorithm SHA256).Hash)"
