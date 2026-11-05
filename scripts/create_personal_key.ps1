param([int]$VersionCode = 84)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$keyDirectory = Join-Path $env:USERPROFILE 'Documents/MPEI-Lite-Keys'
$keyPath = Join-Path $keyDirectory 'stable-2026.p12'
$propertiesPath = Join-Path $root 'local-signing.properties'

if ($VersionCode -lt 1) { throw 'VersionCode must be positive.' }
if (Test-Path $keyPath) { throw "Keystore already exists: $keyPath" }
if (Test-Path $propertiesPath) { throw "Local signing configuration already exists: $propertiesPath" }

New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null
$password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
$env:MPEI_NEW_KEY_PASSWORD = $password
try {
    & keytool -genkeypair -keystore $keyPath -storetype PKCS12 `
        -storepass:env MPEI_NEW_KEY_PASSWORD -keypass:env MPEI_NEW_KEY_PASSWORD `
        -alias mpei-lite-stable-2026 -keyalg RSA -keysize 3072 `
        -validity 10957 -dname 'CN=MPEI Lite Developer' -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed; no signing configuration was written.' }

    $portablePath = $keyPath.Replace('\', '/')
    @(
        "storeFile=$portablePath"
        'storeType=PKCS12'
        "storePassword=$password"
        'keyAlias=mpei-lite-stable-2026'
        "keyPassword=$password"
        "versionCode=$VersionCode"
        "versionName=local.$VersionCode"
    ) | Set-Content -LiteralPath $propertiesPath -Encoding utf8
} finally {
    Remove-Item Env:MPEI_NEW_KEY_PASSWORD -ErrorAction SilentlyContinue
}

Write-Host "Keystore: $keyPath"
Write-Host "Local configuration: $propertiesPath (ignored by Git)"
Write-Host 'Back up both files in an encrypted password manager or secure offline storage.'
