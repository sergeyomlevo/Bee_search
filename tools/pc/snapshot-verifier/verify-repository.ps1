param(
    [Parameter(Mandatory=$true,Position=0)][string]$RepositoryRoot,
    [Parameter(Mandatory=$true,Position=1)][string]$SnapshotFile
)
$ErrorActionPreference = 'Stop'
$classpathFile = Join-Path $PSScriptRoot 'build/launch-classpath.txt'
if (-not (Test-Path -LiteralPath $classpathFile)) {
    throw 'Run the documented offline test command first to compile the verifier.'
}
$launchClasspath = Get-Content -LiteralPath $classpathFile -Raw
& java -cp $launchClasspath pcsnapshot.RepositoryMainKt $RepositoryRoot $SnapshotFile
exit $LASTEXITCODE
