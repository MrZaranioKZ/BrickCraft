$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'JDK 21 requis : javac introuvable' }
$out = Join-Path $PSScriptRoot 'out_010_tests'
New-Item -Force -ItemType Directory $out | Out-Null
& javac -encoding UTF-8 -d $out 'src/main/java/fr/brickcraft/importer/LDrawProjectReader.java' 'src/main/java/fr/brickcraft/world/PreciseStudSnapper.java' 'TEST_SMART_SNAP_010.java'
if ($LASTEXITCODE -ne 0) { throw 'Echec javac Snap' }
& java -cp $out TEST_SMART_SNAP_010
if ($LASTEXITCODE -ne 0) { throw 'Echec tests Snap' }
Write-Host 'OK - Tests Snap geometrie et securite. Construire ensuite avec CONSTRUIRE_WINDOWS.bat.' -ForegroundColor Green
