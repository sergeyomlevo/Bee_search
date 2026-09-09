param(
    [Parameter(Mandatory)]
    [string]$Serial,
    [string]$FixtureRoot = 'C:\App\Bee_search_test_maps\pmtiles',
    [string]$Adb = 'adb'
)

$ErrorActionPreference = 'Stop'

# This script intentionally has no package parameter. It must never stage files
# into the protected field package org.beesearch.app.
$DevelopmentPackage = 'org.beesearch.app.dev'
$RemoteStagingRoot = '/data/local/tmp/beesearch-dev-pmtiles'
$fixtures = @(
    'forest-cutlines-v1.pmtiles',
    'sapunovo-fields-water-v1.pmtiles',
    'territory-benchmark-v1.pmtiles'
)

if (-not (Get-Command $Adb -ErrorAction SilentlyContinue)) {
    throw "ADB executable '$Adb' was not found. Pass -Adb with its full path."
}

$packagePath = & $Adb -s $Serial shell pm path $DevelopmentPackage
if ($LASTEXITCODE -ne 0 -or -not ($packagePath -match [regex]::Escape($DevelopmentPackage))) {
    throw "Development package $DevelopmentPackage is not installed on $Serial. Install debug first."
}

foreach ($fixture in $fixtures) {
    $source = Join-Path $FixtureRoot $fixture
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Required PMTiles fixture is missing: $source"
    }

    $remoteStage = "$RemoteStagingRoot/$fixture"
    try {
        & $Adb -s $Serial shell "mkdir -p $RemoteStagingRoot"
        if ($LASTEXITCODE -ne 0) { throw "Could not create temporary ADB staging directory." }

        & $Adb -s $Serial push $source $remoteStage
        if ($LASTEXITCODE -ne 0) { throw "ADB push failed for $fixture." }

        & $Adb -s $Serial shell "run-as $DevelopmentPackage sh -c 'mkdir -p files/map-poc && cp $remoteStage files/map-poc/$fixture && test -s files/map-poc/$fixture'"
        if ($LASTEXITCODE -ne 0) { throw "Could not install $fixture into $DevelopmentPackage private storage." }

        $hostHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
        $deviceHash = ((& $Adb -s $Serial shell "run-as $DevelopmentPackage sha256sum files/map-poc/$fixture") -split '\s+')[0].ToLowerInvariant()
        if ($hostHash -ne $deviceHash) {
            throw "SHA-256 mismatch after staging $fixture."
        }

        Write-Host "Staged $fixture in $DevelopmentPackage/files/map-poc (SHA-256 verified)."
    } finally {
        & $Adb -s $Serial shell rm -f $remoteStage | Out-Null
    }
}
