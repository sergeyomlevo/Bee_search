param(
    [string]$BaseUrl = 'http://10.0.2.2:8080/field/v1/central-russia-poc-20260830/style-v3',
    [string]$Java = 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe'
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$toolDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$workDir = Join-Path $toolDir 'work'
$outDir = Join-Path $toolDir 'out'
$planetilerVersion = '0.10.0'
$planetilerJar = Join-Path $workDir "planetiler-$planetilerVersion.jar"
$fontZip = Join-Path $workDir 'noto-open-sans-v2.0.zip'
$mergedPbf = Join-Path $workDir 'central-russia-poc.osm.pbf'
$mbtiles = Join-Path $workDir 'bee-search-field.mbtiles'
$areas = Get-Content -LiteralPath (Join-Path $toolDir 'areas.json') -Raw | ConvertFrom-Json

New-Item -ItemType Directory -Force -Path $workDir | Out-Null

if (-not (Test-Path -LiteralPath $planetilerJar)) {
    Invoke-WebRequest `
        -Uri "https://github.com/onthegomap/planetiler/releases/download/v$planetilerVersion/planetiler.jar" `
        -OutFile $planetilerJar
}
if (-not (Test-Path -LiteralPath $fontZip)) {
    Invoke-WebRequest `
        -Uri 'https://github.com/openmaptiles/fonts/releases/download/v2.0/noto-open-sans.zip' `
        -OutFile $fontZip
}

$osmInputs = @()
foreach ($area in $areas.areas) {
    $target = Join-Path $workDir "$($area.id).osm"
    if (-not (Test-Path -LiteralPath $target)) {
        Invoke-WebRequest -Uri $area.osmApiMapUrl -Headers @{'User-Agent'='BeeSearchMapPoC/1.0'} -OutFile $target
    }
    $osmInputs += $target
}
foreach ($object in $areas.diagnosticObjects) {
    $target = Join-Path $workDir "$($object.id).osm"
    if (-not (Test-Path -LiteralPath $target)) {
        Invoke-WebRequest -Uri $object.osmApiFullUrl -Headers @{'User-Agent'='BeeSearchMapPoC/1.0'} -OutFile $target
    }
    $osmInputs += $target
}

& uv run (Join-Path $toolDir 'merge_osm.py') $mergedPbf @osmInputs
if ($LASTEXITCODE -ne 0) { throw 'OSM merge failed' }

& $Java -jar $planetilerJar verify (Join-Path $toolDir 'field-profile.yml')
if ($LASTEXITCODE -ne 0) { throw 'Planetiler profile verification failed' }

if (Test-Path -LiteralPath $mbtiles) { Remove-Item -LiteralPath $mbtiles -Force }
& $Java -Xmx2g -jar $planetilerJar generate-custom `
    "--schema=$(Join-Path $toolDir 'field-profile.yml')" `
    "--osm-path=$mergedPbf" `
    "--output=$mbtiles" `
    '--force' `
    '--bounds=42.4960,56.0615,42.7940,56.2365' `
    '--download=false'
if ($LASTEXITCODE -ne 0) { throw 'Planetiler generation failed' }

& python (Join-Path $toolDir 'build_static_resources.py') `
    --mbtiles $mbtiles `
    --font-zip $fontZip `
    --output-root $outDir `
    --base-url $BaseUrl
if ($LASTEXITCODE -ne 0) { throw 'Static resource build failed' }

& uv run (Join-Path $toolDir 'inspect_tiles.py') `
    (Join-Path $outDir 'field/v1/central-russia-poc-20260830/style-v3/tiles')
if ($LASTEXITCODE -ne 0) { throw 'Tile inspection failed' }
