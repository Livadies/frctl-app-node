[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$Version = "0.84"
$ExpectedSha256 = "E5621FFE4879F0EC39ED40F688DB9399C2D43054D41EF14472FA335C4693B915"
$Uri = "https://the.earth.li/~sgtatham/putty/$Version/w64/plink.exe"
$ToolsDir = Join-Path $env:LOCALAPPDATA "FRCTL\tools"
$Destination = Join-Path $ToolsDir "plink.exe"
$Download = Join-Path $ToolsDir "plink.exe.download"

New-Item -ItemType Directory -Path $ToolsDir -Force | Out-Null
Invoke-WebRequest -Uri $Uri -OutFile $Download

$Hash = (Get-FileHash -LiteralPath $Download -Algorithm SHA256).Hash
if ($Hash -ne $ExpectedSha256) {
    throw "SHA-256 Plink не совпал. Получено: $Hash"
}
$Signature = Get-AuthenticodeSignature -LiteralPath $Download
if ($Signature.Status -ne "Valid" -or $Signature.SignerCertificate.Subject -notlike "CN=Simon Tatham,*") {
    throw "Цифровая подпись Plink недействительна или принадлежит неизвестному издателю"
}
$FileVersion = (Get-Item -LiteralPath $Download).VersionInfo.FileVersion
if ($FileVersion -notlike "Release $Version*") {
    throw "Получена неожиданная версия Plink: $FileVersion"
}

Move-Item -LiteralPath $Download -Destination $Destination -Force
Write-Host "Plink $Version установлен: $Destination" -ForegroundColor Green
Write-Host "SHA-256: $Hash"
