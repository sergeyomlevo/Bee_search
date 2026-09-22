param(
    [Parameter(Mandatory = $true)] [string]$PackageDirectory,
    [Parameter(Mandatory = $true)] [string]$PackageId,
    [Parameter(Mandatory = $true)] [string]$Serial,
    [Parameter(Mandatory = $true)] [ValidateSet('Stable', 'Beta', 'Dev')] [string]$Variant,
    [string]$Adb = 'C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe',
    [string]$Python = 'python'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()

foreach ($requiredFile in @($Adb, (Join-Path $PSScriptRoot 'map_package_tool.py'))) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Required local tool is missing: $requiredFile"
    }
}
$packageTool = Join-Path $PSScriptRoot 'map_package_tool.py'
& $Python $packageTool validate-package-id --package-id $PackageId | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'PackageId validation failed; nothing was transferred.' }

$packageDirectoryItem = Get-Item -LiteralPath $PackageDirectory -ErrorAction Stop
$pmtiles = Join-Path $packageDirectoryItem.FullName "$PackageId.pmtiles"
$manifest = "$pmtiles.manifest.json"
if (-not (Test-Path -LiteralPath $pmtiles -PathType Leaf)) { throw "PMTiles artifact is missing: $pmtiles" }
if (-not (Test-Path -LiteralPath $manifest -PathType Leaf)) { throw "D065 manifest is missing: $manifest" }

& $Python (Join-Path $PSScriptRoot 'map_package_tool.py') validate-package --artifact $pmtiles --manifest $manifest | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Local PMTiles/manifest validation failed; nothing was transferred.' }

$deviceState = (& $Adb -s $Serial get-state 2>&1).Trim()
if ($LASTEXITCODE -ne 0 -or $deviceState -ne 'device') { throw "ADB device is not ready: $Serial" }
$model = (& $Adb -s $Serial shell getprop ro.product.model 2>&1).Trim()
$remoteDirectory = "/sdcard/Download/BeeSearch/$Variant/Exchange/OfflineMaps"
& $Adb -s $Serial shell mkdir -p $remoteDirectory
if ($LASTEXITCODE -ne 0) { throw 'Could not create the user-accessible Download directory.' }
& $Adb -s $Serial push $manifest "$remoteDirectory/"
if ($LASTEXITCODE -ne 0) { throw 'Manifest transfer failed.' }
& $Adb -s $Serial push $pmtiles "$remoteDirectory/"
if ($LASTEXITCODE -ne 0) { throw 'PMTiles transfer failed.' }

$localHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $pmtiles).Hash.ToLowerInvariant()
$remotePmtiles = "$remoteDirectory/$PackageId.pmtiles"

# `adb shell` joins the remote command for a shell to parse. Quote the whole
# user-derived file name as one POSIX shell word; D083 permits spaces, Unicode,
# apostrophes and other ordinary filename characters.
function ConvertTo-RemoteShellLiteral([string]$Value) {
    $singleQuoteEscape = "'`"'`"'"
    return "'" + $Value.Replace("'", $singleQuoteEscape) + "'"
}

$remoteHashCommand = "sha256sum $(ConvertTo-RemoteShellLiteral $remotePmtiles)"
$remoteHash = ((& $Adb -s $Serial shell $remoteHashCommand 2>&1) -split '\s+')[0].ToLowerInvariant()
if ($LASTEXITCODE -ne 0 -or $remoteHash -ne $localHash) {
    throw 'Transferred PMTiles SHA-256 does not match the local artifact.'
}

# Make the newly pushed user files visible to Android's system document picker
# immediately. The import still happens only after the user selects both files
# in Bee Search; this broadcast does not touch app-private package storage.
foreach ($remoteFile in @(
    "$remoteDirectory/$PackageId.pmtiles.manifest.json",
    "$remoteDirectory/$PackageId.pmtiles"
)) {
    $remoteUri = [Uri]::new("file://$remoteFile").AbsoluteUri
    & $Adb -s $Serial shell am broadcast `
        -a android.intent.action.MEDIA_SCANNER_SCAN_FILE `
        -d $remoteUri | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Transferred file could not be registered with Android media storage: $remoteFile"
    }
}

Write-Host "Delivered to $model ($Serial): $remoteDirectory"
Write-Host "Select $PackageId.pmtiles.manifest.json first, then $PackageId.pmtiles in Bee Search $Variant."
Write-Host 'The files remain in shared Download storage; this script never writes app-private map storage.'
