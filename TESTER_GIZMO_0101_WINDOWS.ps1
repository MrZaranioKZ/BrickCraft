$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'JDK 21 requis : javac introuvable' }
$out = Join-Path $PSScriptRoot 'out_0101_tests'
New-Item -Force -ItemType Directory $out | Out-Null
$files = @(
'src/main/java/fr/brickcraft/importer/LDrawProjectReader.java',
'src/main/java/fr/brickcraft/world/PreciseStudSnapper.java',
'src/main/java/fr/brickcraft/client/WorldGizmoMath.java',
'TEST_GIZMO_0101.java',
'TEST_SMART_SNAP_010.java'
)
& javac -encoding UTF-8 -d $out $files
if ($LASTEXITCODE -ne 0) { throw 'Echec de compilation des tests Java' }
& java -cp $out TEST_GIZMO_0101
if ($LASTEXITCODE -ne 0) { throw 'Echec des nouveaux tests 0.10.1' }
& java -cp $out TEST_SMART_SNAP_010
if ($LASTEXITCODE -ne 0) { throw 'Regression Snap 0.10.0' }
Write-Host 'OK - Tests gizmo + snap. Lancer CONSTRUIRE_WINDOWS.bat ensuite.' -ForegroundColor Green
