[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Target,
    [ValidateRange(1, 65535)][int]$Port = 22,
    [Parameter(Mandatory)][string]$User,
    [Parameter(Mandatory)][string]$IdentityFile,
    [Parameter(Mandatory)][string]$HostKey
)

$ErrorActionPreference = "Stop"
$ConfigPath = Join-Path $env:LOCALAPPDATA "FRCTL\node-config.json"

if ($Target -notmatch '^[A-Za-z0-9.-]+$' -or $Target.Contains('..')) {
    throw "Target должен быть DNS-именем или IPv4-адресом без схемы, credentials и query string"
}
if ($User -notmatch '^[A-Za-z0-9._-]{1,64}$') {
    throw "Недопустимое имя SSH-пользователя"
}
if ($HostKey -notmatch '^[A-Za-z0-9@._+-]+\s+\d{2,5}\s+SHA256:[A-Za-z0-9+/=]{20,100}$') {
    throw "HostKey должен иметь вид: ssh-rsa 2048 SHA256:..."
}
if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
    throw "Сначала выполните .\install.ps1: не найден $ConfigPath"
}

$Identity = (Resolve-Path -LiteralPath $IdentityFile).Path
if ([IO.Path]::GetExtension($Identity) -ne '.ppk') {
    throw "Для автоматических workflow требуется PPK-файл"
}

$Config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
foreach ($Name in 'allowed_targets', 'allowed_identity_files', 'ssh_host_keys', 'ssh_users') {
    if (-not $Config.PSObject.Properties[$Name]) {
        $Value = if ($Name -in 'ssh_host_keys', 'ssh_users') { [pscustomobject]@{} } else { @() }
        $Config | Add-Member -NotePropertyName $Name -NotePropertyValue $Value
    }
}

$Config.allowed_targets = @($Config.allowed_targets + $Target | Sort-Object -Unique)
$Config.allowed_identity_files = @($Config.allowed_identity_files + $Identity | Sort-Object -Unique)
$Profile = "${Target}:$Port"
$Config.ssh_host_keys | Add-Member -NotePropertyName $Profile -NotePropertyValue $HostKey -Force
$Config.ssh_users | Add-Member -NotePropertyName $Profile -NotePropertyValue $User -Force

$Temporary = "$ConfigPath.tmp"
$Config | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $Temporary -Encoding utf8
Move-Item -LiteralPath $Temporary -Destination $ConfigPath -Force

Write-Host "SSH-профиль FRCTL добавлен: $User@$Profile" -ForegroundColor Green
Write-Host "Перезапустите FRCTL Node и проверьте план перед выполнением."
