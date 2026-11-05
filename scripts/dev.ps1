param(
    [ValidateSet('check', 'device', 'all')]
    [string]$Mode = 'check',
    [string]$DeviceSerial,
    [switch]$AllowDataReset
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root

function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Program exited with code $LASTEXITCODE"
    }
}

if (-not (Test-Path '.\gradlew.bat')) {
    throw 'Gradle Wrapper is missing. Restore gradlew.bat and gradle/wrapper from version control.'
}

if ($Mode -in @('check', 'all')) {
    $python = if (Get-Command py -ErrorAction SilentlyContinue) { 'py' } else { 'python3' }
    [string[]]$pythonPrefix = if ($python -eq 'py') { @('-3') } else { @() }
    Invoke-Checked $python ($pythonPrefix + @('scripts/check_architecture.py'))
    Invoke-Checked $python ($pythonPrefix + @('scripts/check_material3.py'))
    Invoke-Checked $python ($pythonPrefix + @('scripts/check_material3_screen_inventory.py'))
    Invoke-Checked $python ($pythonPrefix + @('-m', 'unittest', 'discover', '-s', 'scripts', '-p', 'test_*.py', '-q'))
    Invoke-Checked '.\gradlew.bat' @('lintDebug', 'testDebugUnitTest')
}

if ($Mode -in @('device', 'all')) {
    $sdkAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
    $adbProgram = if (Test-Path $sdkAdb) { $sdkAdb } else { 'adb' }
    if (-not (Get-Command $adbProgram -ErrorAction SilentlyContinue)) {
        throw 'adb is not on PATH. Add Android SDK platform-tools to PATH.'
    }
    $devices = @(& $adbProgram devices | Select-String '^(\S+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
    if ($DeviceSerial) {
        if ($DeviceSerial -notin $devices) { throw "Device $DeviceSerial is not ready in adb devices." }
    } elseif ($devices.Count -eq 1) {
        $DeviceSerial = $devices[0]
    } else {
        throw "Expected one ready adb device; found $($devices.Count). Pass -DeviceSerial to select one."
    }
    $env:ANDROID_SERIAL = $DeviceSerial
    try {
        $installed = @(& $adbProgram shell pm path com.rteats.mpeilite)
        if (-not $AllowDataReset -and ($installed | Where-Object { $_ -like 'package:*' })) {
            throw 'MPEI Lite is installed on this device. Connected tests may remove its data. Use a separate emulator or pass -AllowDataReset.'
        }
        $testStart = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        Invoke-Checked '.\gradlew.bat' @(
            'connectedDebugAndroidTest',
            '-Pandroid.testInstrumentationRunnerArguments.class=com.rteats.mpeineo.ui.Material3AccessibilityUiTest,com.rteats.mpeineo.BarsTaskFlowUiTest'
        )
        $python = if (Get-Command py -ErrorAction SilentlyContinue) { 'py' } else { 'python3' }
        [string[]]$pythonPrefix = if ($python -eq 'py') { @('-3') } else { @() }
        Invoke-Checked $python ($pythonPrefix + @('scripts/check_android_test_report.py', '--since', "$testStart"))
    } finally {
        Remove-Item Env:ANDROID_SERIAL -ErrorAction SilentlyContinue
    }
}
