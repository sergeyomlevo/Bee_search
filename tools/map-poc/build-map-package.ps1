param(
    [Nullable[double]]$West,
    [Nullable[double]]$South,
    [Nullable[double]]$East,
    [Nullable[double]]$North,
    [string]$AreaJson,
    [string[]]$SourceCoveragePolygon,
    [Parameter(Mandatory = $true)] [string]$PackageId,
    [string]$SourcePbf,
    [string]$DatasetVersion,
    [string]$OutputDirectory,
    [string]$Java = 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe',
    [string]$Python = 'python',
    [int]$MaxHeapGb = 4,
    [switch]$MergedSourceCoverageVerified,
    [string]$SourceCoverageEvidence,
    [switch]$PlanOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()

$toolDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$workDir = Join-Path $toolDir 'work'
$profile = Join-Path $toolDir 'field-profile.yml'
$planetilerJar = Join-Path $workDir 'planetiler-0.10.0.jar'
$packageTool = Join-Path $toolDir 'map_package_tool.py'
if (-not $SourcePbf) { $SourcePbf = Join-Path $workDir 'volga-fed-district-260830.osm.pbf' }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $workDir 'packages' }
$sourceItem = Get-Item -LiteralPath $SourcePbf -ErrorAction Stop
if (-not $DatasetVersion) { $DatasetVersion = $sourceItem.BaseName }

$areaInfo = $null
$sourceCoverageValidation = $null
if ($AreaJson) {
    if ($null -ne $West -or $null -ne $South -or $null -ne $East -or $null -ne $North) {
        throw 'Use either -AreaJson or -West/-South/-East/-North, not both.'
    }
    $areaItem = Get-Item -LiteralPath $AreaJson -ErrorAction Stop
    $areaInfoText = & $Python $packageTool area-info --area-json $areaItem.FullName
    if ($LASTEXITCODE -ne 0) { throw 'Area JSON validation failed.' }
    $areaInfo = $areaInfoText | ConvertFrom-Json
    $selectedWest = [double]$areaInfo.generationBounds.west
    $selectedSouth = [double]$areaInfo.generationBounds.south
    $selectedEast = [double]$areaInfo.generationBounds.east
    $selectedNorth = [double]$areaInfo.generationBounds.north
    if (-not $SourceCoveragePolygon -or $SourceCoveragePolygon.Count -eq 0) {
        throw '-SourceCoveragePolygon is required with -AreaJson; a PBF header bbox is not proof that every Area bounds is covered.'
    }
    $coverageArguments = @($packageTool, 'validate-source-coverage', '--area-json', $areaItem.FullName)
    foreach ($polygon in $SourceCoveragePolygon) {
        $polygonItem = Get-Item -LiteralPath $polygon -ErrorAction Stop
        $coverageArguments += @('--source-coverage-polygon', $polygonItem.FullName)
    }
    $sourceCoverageText = & $Python @coverageArguments
    if ($LASTEXITCODE -ne 0) { throw 'One or more Area bounds are outside the selected source coverage.' }
    $sourceCoverageValidation = $sourceCoverageText | ConvertFrom-Json
} else {
    if ($null -eq $West -or $null -eq $South -or $null -eq $East -or $null -eq $North) {
        throw 'Explicit generation requires -West, -South, -East and -North, or use -AreaJson.'
    }
    $selectedWest = [double]$West
    $selectedSouth = [double]$South
    $selectedEast = [double]$East
    $selectedNorth = [double]$North
}

if ($DatasetVersion.Trim().Length -eq 0) { throw 'DatasetVersion must not be blank.' }
if ($MaxHeapGb -lt 2) { throw 'MaxHeapGb must be at least 2.' }
foreach ($requiredFile in @($profile, $planetilerJar, $packageTool, $Java)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Required local tool/input is missing: $requiredFile"
    }
}
& $Python $packageTool validate-package-id --package-id $PackageId | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'PackageId validation failed.' }
if (-not (Select-String -LiteralPath $profile -Pattern '^version:\s*1\s*$' -Quiet)) {
    throw 'The existing bee-search-field profile version is not the D065-compatible version 1.'
}

$invariant = [Globalization.CultureInfo]::InvariantCulture
function Format-Coordinate([double]$Value) { $Value.ToString('0.#######', $invariant) }
$westText = Format-Coordinate $selectedWest
$southText = Format-Coordinate $selectedSouth
$eastText = Format-Coordinate $selectedEast
$northText = Format-Coordinate $selectedNorth

$preflightArguments = @(
    $packageTool, 'source-info', '--source', $sourceItem.FullName,
    '--west', $westText, '--south', $southText, '--east', $eastText, '--north', $northText
)
$preflightText = & $Python @preflightArguments
if ($LASTEXITCODE -ne 0) { throw 'Map package preflight failed. See the error above.' }
$preflight = $preflightText | ConvertFrom-Json
$sourceHeaderContains = $preflight.sourceHeaderContainsSelectedBounds
$sourceCoverageCheckBasis = if ($AreaJson) {
    'Every Area bounds passed source coverage polygon validation'
} else {
    'OSM PBF header bbox'
}
if (-not $AreaJson -and $null -eq $sourceHeaderContains) {
    if (-not $MergedSourceCoverageVerified) {
        throw 'The merged OSM PBF header has no bbox. Verify its component extract polygons, then pass -MergedSourceCoverageVerified and -SourceCoverageEvidence.'
    }
    if ([string]::IsNullOrWhiteSpace($SourceCoverageEvidence)) {
        throw '-SourceCoverageEvidence is required with -MergedSourceCoverageVerified.'
    }
    $sourceCoverageCheckBasis = $SourceCoverageEvidence.Trim()
}
$areaKm2 = [double]$preflight.selectedMetrics.areaKm2
$complexity = if ($areaKm2 -le 4000) {
    'moderate: suitable for the first large local package run'
} elseif ($areaKm2 -le 10000) {
    'large: expect a longer Planetiler run and a substantially larger artifact'
} else {
    'very large: allowed, but check free disk/RAM before starting Planetiler'
}

Write-Host "Selected bbox W/S/E/N: $westText / $southText / $eastText / $northText"
Write-Host ('Size: {0:N1} x {1:N1} km; area: {2:N1} km2' -f $preflight.selectedMetrics.widthKm, $preflight.selectedMetrics.heightKm, $areaKm2)
Write-Host "Source OSM PBF: $($sourceItem.FullName)"
Write-Host "Source header bbox contains selected bbox: $sourceHeaderContains"
Write-Host "Preliminary complexity: $complexity"
Write-Host "Coverage check basis: $sourceCoverageCheckBasis"
if ($AreaJson) {
    foreach ($result in $sourceCoverageValidation.bounds) {
        Write-Host "Source coverage bounds[$($result.index)]: PASS"
    }
}

if ($PlanOnly) {
    $preflight | Add-Member -NotePropertyName preliminaryComplexity -NotePropertyValue $complexity
    if ($AreaJson) {
        $preflight | Add-Member -NotePropertyName area -NotePropertyValue $areaInfo
        $preflight | Add-Member -NotePropertyName sourceCoverageValidation -NotePropertyValue $sourceCoverageValidation
    }
    $preflight | ConvertTo-Json -Depth 8
    return
}

$finalDirectory = Join-Path $OutputDirectory $PackageId
if (Test-Path -LiteralPath $finalDirectory) {
    throw "Immutable package output already exists: $finalDirectory"
}
$stagingRoot = Join-Path $OutputDirectory '.staging'
$stage = Join-Path $stagingRoot "$PackageId-$([Guid]::NewGuid().ToString('N'))"
$tempDir = Join-Path $stage 'tmp'
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null
$pmtiles = Join-Path $stage "$PackageId.pmtiles"
$manifest = "$pmtiles.manifest.json"
$buildLog = Join-Path $stage 'planetiler-build.log'
$buildReport = Join-Path $stage 'build-report.json'
$buildSucceeded = $false
$totalStopwatch = [Diagnostics.Stopwatch]::StartNew()

try {
    & $Java -jar $planetilerJar verify $profile
    if ($LASTEXITCODE -ne 0) { throw 'Planetiler rejected the existing bee-search-field profile.' }

    $planetilerStopwatch = [Diagnostics.Stopwatch]::StartNew()
    & $Java "-Xmx${MaxHeapGb}g" -jar $planetilerJar generate-custom `
        "--schema=$profile" `
        "--osm-path=$($sourceItem.FullName)" `
        "--output=$pmtiles" `
        '--force' `
        "--bounds=$westText,$southText,$eastText,$northText" `
        "--tmpdir=$tempDir" `
        '--download=false' 2>&1 | Tee-Object -FilePath $buildLog
    $planetilerStopwatch.Stop()
    if ($LASTEXITCODE -ne 0) { throw 'Planetiler PMTiles generation failed.' }
    if (-not (Test-Path -LiteralPath $pmtiles -PathType Leaf)) {
        throw 'Planetiler completed without creating the requested PMTiles artifact.'
    }

    # A malformed merged PBF can still contain nodes while silently losing all
    # ways and relations. Planetiler then exits successfully and emits a tiny,
    # superficially valid PMTiles file. Reject that source before manifest
    # generation so it can never become a D065 package candidate.
    $wayCounts = @(
        Select-String -LiteralPath $buildLog -Pattern 'Finished ways:\s+([0-9\u00a0\u202f ]+)' -AllMatches |
            ForEach-Object { $_.Matches } |
            ForEach-Object { [long](($_.Groups[1].Value -replace '[^0-9]', '')) }
    )
    if ($wayCounts.Count -eq 0 -or ($wayCounts | Measure-Object -Maximum).Maximum -le 0) {
        throw 'Planetiler read no OSM ways. The source PBF is incomplete or was merged incorrectly; refusing to create a package manifest.'
    }

    $manifestArguments = @(
        $packageTool, 'create-manifest',
        '--artifact', $pmtiles,
        '--manifest', $manifest,
        '--package-id', $PackageId,
        '--dataset-version', $DatasetVersion
    )
    if ($AreaJson) {
        $manifestArguments += @('--area-json', $areaItem.FullName)
    } else {
        $manifestArguments += @('--west', $westText, '--south', $southText, '--east', $eastText, '--north', $northText)
    }
    $manifestText = & $Python @manifestArguments
    if ($LASTEXITCODE -ne 0) { throw 'D065 manifest generation failed.' }
    $validatedText = & $Python $packageTool validate-package --artifact $pmtiles --manifest $manifest
    if ($LASTEXITCODE -ne 0) { throw 'Generated PMTiles and D065 manifest failed local validation.' }
    $validated = $validatedText | ConvertFrom-Json
    $artifactAreaValidation = $null
    if ($AreaJson) {
        $artifactAreaText = & $Python $packageTool validate-artifact-area --area-json $areaItem.FullName --artifact $pmtiles
        if ($LASTEXITCODE -ne 0) {
            throw 'Generated PMTiles lacks usable tile data in one or more Area bounds.'
        }
        $artifactAreaValidation = $artifactAreaText | ConvertFrom-Json
    }
    # The package is about to move atomically from staging to this immutable
    # directory; keep the durable report path useful after that move.
    $validated.artifact.path = Join-Path $finalDirectory "$PackageId.pmtiles"
    $totalStopwatch.Stop()

    $report = [ordered]@{
        packageId = $PackageId
        datasetVersion = $DatasetVersion
        selectedBounds = $preflight.selectedBounds
        selectedMetrics = $preflight.selectedMetrics
        source = $preflight.source
        sourceHeaderContainsSelectedBounds = $sourceHeaderContains
        sourceCoverageCheckBasis = $sourceCoverageCheckBasis
        sourceCoverageValidation = $sourceCoverageValidation
        artifactAreaValidation = $artifactAreaValidation
        preliminaryComplexity = $complexity
        planetilerVersion = '0.10.0'
        profileId = 'bee-search-field'
        profileVersion = 'v1'
        styleVersion = 'vector-pmtiles-v1'
        maxHeapGb = $MaxHeapGb
        planetilerBuildSeconds = [Math]::Round($planetilerStopwatch.Elapsed.TotalSeconds, 3)
        totalBuildSeconds = [Math]::Round($totalStopwatch.Elapsed.TotalSeconds, 3)
        peakResourceObservation = $null
        artifact = $validated.artifact
        manifest = $validated.manifest
    }
    $report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $buildReport -Encoding utf8

    if (Test-Path -LiteralPath $tempDir) { Remove-Item -LiteralPath $tempDir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
    Move-Item -LiteralPath $stage -Destination $finalDirectory
    $buildSucceeded = $true

    Write-Host "Package built and validated: $finalDirectory"
    Write-Host ('Planetiler time: {0:N1} s' -f $planetilerStopwatch.Elapsed.TotalSeconds)
    Write-Host "PMTiles bytes: $($validated.artifact.byteLength)"
    Write-Host "Tiles / entries / contents: $($validated.artifact.addressedTiles) / $($validated.artifact.tileEntries) / $($validated.artifact.tileContents)"
    Write-Host "Zoom: $($validated.artifact.minZoom)-$($validated.artifact.maxZoom)"
    Write-Host "SHA256: $($validated.artifact.sha256)"
    if ($AreaJson) {
        foreach ($result in $artifactAreaValidation.bounds) {
            Write-Host "PMTiles tile data bounds[$($result.index)]: PASS ($($result.populatedCells)/$($result.cellGrid * $result.cellGrid) cells)"
        }
    }
    Write-Host "Manifest: $(Join-Path $finalDirectory ([IO.Path]::GetFileName($manifest)))"
} finally {
    if (-not $buildSucceeded -and (Test-Path -LiteralPath $stage)) {
        Remove-Item -LiteralPath $stage -Recurse -Force
    }
}
