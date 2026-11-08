$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$configPath = Join-Path $root 'local-signing.properties'
if (-not (Test-Path $configPath)) { throw "Missing $configPath" }

$settings = @{}
foreach ($line in Get-Content -LiteralPath $configPath) {
    if ($line -match '^([^#=]+)=(.*)$') { $settings[$matches[1].Trim()] = $matches[2].Trim() }
}
$keyPath = $settings['storeFile']
$password = $settings['storePassword']
if (-not (Test-Path $keyPath) -or -not $password) { throw 'Keystore or password missing from local-signing.properties.' }

$gh = (Get-Command gh -ErrorAction Stop).Source
& $gh auth status *> $null
if ($LASTEXITCODE -ne 0) { throw 'Run gh auth login first, then rerun this script.' }

function Set-RepositorySecret([string]$Name, [string]$Value) {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $gh
    foreach ($argument in @('secret', 'set', $Name, '--repo', 'rteats/mpei-lite')) {
        [void]$start.ArgumentList.Add($argument)
    }
    $start.UseShellExecute = $false
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($start)
    try {
        $process.StandardInput.Write($Value)
        $process.StandardInput.Close()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "Uploading $Name failed." }
    } finally {
        $process.Dispose()
    }
}

Set-RepositorySecret 'MPEI_STABLE_KEYSTORE_B64' ([Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath)))
Set-RepositorySecret 'MPEI_STABLE_PASSWORD' $password
Write-Host 'GitHub signing secrets updated. Keep a separate private backup of the keystore and password.'
