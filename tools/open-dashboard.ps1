<#
.SYNOPSIS
Opens the UMA Auto+ dashboard for a MuMu player in your PC's browser.

.DESCRIPTION
Finds adb (MuMu's own copy first), connects to MuMu's ADB port, forwards the dashboard port to your PC and
opens http://localhost:<Port>/. It runs only the adb connect and forward commands. Nothing is downloaded or
installed, and no other command is sent to the device.

Before you run it, turn on Enable Remote Log Viewer (Settings, Debug Settings) in UMA Auto+ and press Start.
The page asks for the access code shown on the Debug Settings page.

.PARAMETER Endpoint
MuMu's ADB address as host:port. Default 127.0.0.1:16384. Use the ADB port from MuMu's settings if it differs.

.PARAMETER Port
The Server Port from Debug Settings (9000 by default). The same port is used on the PC and on the device.

.PARAMETER AdbPath
Full path to adb.exe, used instead of searching for it.

.PARAMETER DryRun
Print what would run and run nothing.

.EXAMPLE
.\open-dashboard.ps1

.EXAMPLE
.\open-dashboard.ps1 -Endpoint 127.0.0.1:7555 -Port 9100
#>
[CmdletBinding()]
param(
    [string]$Endpoint = '127.0.0.1:16384',
    [int]$Port = 9000,
    [string]$AdbPath,
    [switch]$DryRun
)

function Stop-WithError([string]$Message, [int]$Code) {
    Write-Host "Error: $Message" -ForegroundColor Red
    exit $Code
}

function Find-Adb {
    if ($AdbPath) {
        if (Test-Path -LiteralPath $AdbPath -PathType Leaf) { return $AdbPath }
        return $null
    }

    $candidates = @()
    $programRoots = @($env:ProgramFiles, $env:ProgramW6432, ${env:ProgramFiles(x86)}) | Where-Object { $_ } | Select-Object -Unique
    foreach ($root in $programRoots) {
        $netease = Join-Path $root 'Netease'
        if (Test-Path -LiteralPath $netease -PathType Container) {
            foreach ($dir in Get-ChildItem -LiteralPath $netease -Directory -Filter 'MuMu*' -ErrorAction SilentlyContinue) {
                $candidates += Join-Path $dir.FullName 'shell\adb.exe'
            }
        }
    }

    $sdkRoots = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    if ($env:LOCALAPPDATA) { $sdkRoots += Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    foreach ($sdk in $sdkRoots | Where-Object { $_ }) {
        $candidates += Join-Path $sdk 'platform-tools\adb.exe'
    }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }

    $onPath = Get-Command adb.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($onPath) { return $onPath.Source }
    return $null
}

if ($Endpoint -notmatch '^[A-Za-z0-9._-]+:[0-9]{1,5}$') {
    Stop-WithError "-Endpoint must look like 127.0.0.1:16384 (host:port), not '$Endpoint'." 5
}
if ($Port -lt 1 -or $Port -gt 65535) {
    Stop-WithError "-Port must be between 1 and 65535, not $Port." 5
}

$adb = Find-Adb
if (-not $adb) {
    Stop-WithError 'adb was not found. Install MuMu Player or the Android platform-tools, or pass -AdbPath with the full path to adb.exe.' 2
}

$url = "http://localhost:$Port/"

if ($DryRun) {
    Write-Host 'Dry run: nothing is executed.'
    Write-Host "adb found at: $adb"
    Write-Host "Would run: `"$adb`" connect $Endpoint"
    Write-Host "Would run: `"$adb`" -s $Endpoint forward tcp:$Port tcp:$Port"
    Write-Host "Would open: $url"
    exit 0
}

$connectOutput = (& $adb connect $Endpoint 2>&1 | ForEach-Object { "$_" } | Out-String) -replace '\s+', ' '
if ($LASTEXITCODE -ne 0 -or $connectOutput.Trim() -notmatch '^(?i)(already )?connected to') {
    Stop-WithError "could not connect to $Endpoint ($($connectOutput.Trim())). Start MuMu, check the ADB port in MuMu's settings, and pass it with -Endpoint host:port." 3
}

$forwardOutput = (& $adb -s $Endpoint forward "tcp:$Port" "tcp:$Port" 2>&1 | ForEach-Object { "$_" } | Out-String) -replace '\s+', ' '
if ($LASTEXITCODE -ne 0) {
    Stop-WithError "could not forward port $Port to $Endpoint ($($forwardOutput.Trim()))." 4
}

try {
    Start-Process $url
} catch {
    Stop-WithError "the port is forwarded, but the browser could not be opened. Open $url yourself." 6
}

Write-Host "Opened $url"
Write-Host 'Enter the access code shown under Remote Log Viewer on the Debug Settings page. If the page does not load, turn on Enable Remote Log Viewer and press Start in UMA Auto+.'
