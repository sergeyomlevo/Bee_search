param(
    [Parameter(Mandatory)]
    [string]$Serial,
    [string]$Adb = 'adb',
    [string]$FixtureRoot = 'C:\App\Bee_search_test_maps\pmtiles',
    [string]$DebugApk = 'app\build\outputs\apk\debug\app-debug.apk',
    [string]$AndroidTestApk = 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
)

$ErrorActionPreference = 'Stop'

# This is deliberately not parameterized: it recreates ONLY the isolated development
# container. It must never install, clear, stage, or launch the field package.
$DevelopmentPackage = 'org.beesearch.app.dev'
$DevelopmentTestPackage = 'org.beesearch.app.dev.test'
$RemoteStagingRoot = '/data/local/tmp/beesearch-dev-bootstrap'
$RemoteFixtureDirectory = 'files/dev-bootstrap'
$PmtilesFileName = 'territory-benchmark-v1.pmtiles'
$ManifestFileName = 'package.manifest.json'
$ManifestSource = Join-Path $PSScriptRoot 'map-poc\fixtures\territory-benchmark-v1.pmtiles.manifest.json'
$PmtilesSource = Join-Path $FixtureRoot $PmtilesFileName
$DebugMetadata = Join-Path (Split-Path -Parent $DebugApk) 'output-metadata.json'
$AndroidTestMetadata = Join-Path (Split-Path -Parent $AndroidTestApk) 'output-metadata.json'

function Require-Success([string]$Operation) {
    if ($LASTEXITCODE -ne 0) { throw "$Operation failed." }
}

function Assert-ArtifactApplicationId([string]$MetadataPath, [string]$ExpectedApplicationId) {
    if (-not (Test-Path -LiteralPath $MetadataPath -PathType Leaf)) {
        throw "APK metadata is missing: $MetadataPath. Build the requested debug artifacts first."
    }
    $metadata = Get-Content -LiteralPath $MetadataPath -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne $ExpectedApplicationId) {
        throw "Refusing to install an APK with applicationId '$($metadata.applicationId)'; expected '$ExpectedApplicationId'."
    }
}

function Wait-ForInstalledPackage([string]$PackageName) {
    foreach ($attempt in 1..20) {
        $packagePath = & $Adb -s $Serial shell pm path $PackageName
        if ($LASTEXITCODE -eq 0 -and $packagePath -match [regex]::Escape($PackageName)) {
            return
        }
        Start-Sleep -Milliseconds 250
    }
    throw "Installed package $PackageName was not registered on $Serial."
}

if (-not (Get-Command $Adb -ErrorAction SilentlyContinue)) {
    throw "ADB executable '$Adb' was not found. Pass -Adb with its full path."
}
foreach ($path in @($DebugApk, $AndroidTestApk, $ManifestSource, $PmtilesSource)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required development bootstrap input is missing: $path"
    }
}
Assert-ArtifactApplicationId $DebugMetadata $DevelopmentPackage
Assert-ArtifactApplicationId $AndroidTestMetadata $DevelopmentTestPackage

# Recreate only development state. This is intentionally destructive for .dev so each
# run is idempotent; no field-package identifier appears in any ADB operation below.
& $Adb -s $Serial install -r -t $DebugApk
Require-Success 'Debug APK install'
Wait-ForInstalledPackage $DevelopmentPackage
& $Adb -s $Serial install -r -t $AndroidTestApk
Require-Success 'Debug androidTest APK install'
Wait-ForInstalledPackage $DevelopmentTestPackage
& $Adb -s $Serial shell pm clear $DevelopmentPackage
Require-Success 'Development container clear'

Wait-ForInstalledPackage $DevelopmentPackage
foreach ($permission in @(
    'android.permission.ACCESS_COARSE_LOCATION',
    'android.permission.ACCESS_FINE_LOCATION'
)) {
    & $Adb -s $Serial shell pm grant $DevelopmentPackage $permission
    Require-Success "Development location permission grant: $permission"
}

try {
    & $Adb -s $Serial shell "mkdir -p $RemoteStagingRoot"
    Require-Success 'Temporary ADB staging directory creation'
    & $Adb -s $Serial push $ManifestSource "$RemoteStagingRoot/$ManifestFileName"
    Require-Success 'D065 manifest push'
    & $Adb -s $Serial push $PmtilesSource "$RemoteStagingRoot/$PmtilesFileName"
    Require-Success 'PMTiles fixture push'
    & $Adb -s $Serial shell "run-as $DevelopmentPackage sh -c 'mkdir -p $RemoteFixtureDirectory && cp $RemoteStagingRoot/$ManifestFileName $RemoteFixtureDirectory/$ManifestFileName && cp $RemoteStagingRoot/$PmtilesFileName $RemoteFixtureDirectory/$PmtilesFileName && test -s $RemoteFixtureDirectory/$ManifestFileName && test -s $RemoteFixtureDirectory/$PmtilesFileName'"
    Require-Success 'Development-private fixture staging'

    foreach ($fixture in @(
        @{ Source = $ManifestSource; Name = $ManifestFileName },
        @{ Source = $PmtilesSource; Name = $PmtilesFileName }
    )) {
        $hostHash = (Get-FileHash -LiteralPath $fixture.Source -Algorithm SHA256).Hash.ToLowerInvariant()
        $deviceHash = ((& $Adb -s $Serial shell "run-as $DevelopmentPackage sha256sum $RemoteFixtureDirectory/$($fixture.Name)") -split '\s+')[0].ToLowerInvariant()
        Require-Success "SHA-256 verification for $($fixture.Name)"
        if ($hostHash -ne $deviceHash) {
            throw "SHA-256 mismatch after development-private staging: $($fixture.Name)"
        }
    }

    & $Adb -s $Serial shell am instrument -w -r `
        -e beeSearchDevBootstrap true `
        -e class org.beesearch.app.DevBootstrapInstrumentedTest#bootstrapDevelopmentState `
        "$DevelopmentTestPackage/androidx.test.runner.AndroidJUnitRunner"
    Require-Success 'Development bootstrap instrumentation'
} finally {
    & $Adb -s $Serial shell rm -rf $RemoteStagingRoot | Out-Null
}

& $Adb -s $Serial shell am start -n "$DevelopmentPackage/org.beesearch.app.MainActivity"
Require-Success 'Development app launch'
Write-Host "Bee Search DEV bootstrap complete. The active test observation is ready for manual review."
