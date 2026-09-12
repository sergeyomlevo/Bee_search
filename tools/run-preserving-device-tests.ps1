<#
.SYNOPSIS
Runs Bee Search instrumentation without deleting the persistent dev app.

.DESCRIPTION
Builds and updates only org.beesearch.app.dev and org.beesearch.app.dev.test,
then invokes AndroidJUnitRunner directly. Unlike connectedDebugAndroidTest,
this workflow does not uninstall or clear the app-under-test after the run.

.EXAMPLE
.\tools\run-preserving-device-tests.ps1 -Serial RFCY90MBYVZ

.EXAMPLE
.\tools\run-preserving-device-tests.ps1 -Serial RFCY90MBYVZ `
  -Class 'org.beesearch.app.ui.settings.SettingsScreenImeTest'
#>
param(
    [Parameter(Mandatory)]
    [string]$Serial,
    [string]$Adb = 'adb',
    [string]$Class,
    [switch]$SkipBuild,
    [string]$DebugApk = 'app\build\outputs\apk\debug\app-debug.apk',
    [string]$AndroidTestApk = 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
)

$ErrorActionPreference = 'Stop'

$DevelopmentPackage = 'org.beesearch.app.dev'
$DevelopmentTestPackage = 'org.beesearch.app.dev.test'
$Runner = "$DevelopmentTestPackage/androidx.test.runner.AndroidJUnitRunner"
$DebugMetadata = Join-Path (Split-Path -Parent $DebugApk) 'output-metadata.json'
$AndroidTestMetadata = Join-Path (Split-Path -Parent $AndroidTestApk) 'output-metadata.json'
$Gradle = Join-Path $PSScriptRoot '..\gradlew.bat'

function Require-Success([string]$Operation) {
    if ($LASTEXITCODE -ne 0) { throw "$Operation failed." }
}

function Assert-ArtifactApplicationId([string]$MetadataPath, [string]$ExpectedApplicationId) {
    if (-not (Test-Path -LiteralPath $MetadataPath -PathType Leaf)) {
        throw "APK metadata is missing: $MetadataPath."
    }
    $metadata = Get-Content -LiteralPath $MetadataPath -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne $ExpectedApplicationId) {
        throw "Refusing APK applicationId '$($metadata.applicationId)'; expected '$ExpectedApplicationId'."
    }
}

if (-not (Get-Command $Adb -ErrorAction SilentlyContinue)) {
    throw "ADB executable '$Adb' was not found."
}
if (-not $SkipBuild) {
    & $Gradle :app:assembleDebug :app:assembleDebugAndroidTest
    Require-Success 'Debug APK build'
}
Assert-ArtifactApplicationId $DebugMetadata $DevelopmentPackage
Assert-ArtifactApplicationId $AndroidTestMetadata $DevelopmentTestPackage

$deviceState = (& $Adb devices | Select-String "^$([regex]::Escape($Serial))\s+device$")
Require-Success 'ADB device query'
if (-not $deviceState) { throw "Device '$Serial' is not connected and ready." }

# Direct installs update the isolated development packages in place. Unlike the
# connectedDebugAndroidTest installer, this workflow never uninstalls or clears them.
& $Adb -s $Serial install -r -t $DebugApk
Require-Success 'Development APK install'
& $Adb -s $Serial install -r -t $AndroidTestApk
Require-Success 'Development test APK install'

$instrumentArguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r')
if ($Class) { $instrumentArguments += @('-e', 'class', $Class) }
$instrumentArguments += $Runner
& $Adb @instrumentArguments
Require-Success 'Development instrumentation'

& $Adb -s $Serial shell pm path $DevelopmentPackage | Out-Null
Require-Success 'Post-test development package check'
Write-Host "Instrumentation complete; $DevelopmentPackage remains installed and its app data was not cleared."
