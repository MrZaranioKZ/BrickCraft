$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$out = Join-Path $PSScriptRoot 'out_v012_tests'
New-Item -ItemType Directory -Path $out -Force | Out-Null
& javac -encoding UTF-8 -d $out `
  'src/main/java/fr/brickcraft/core/BrickCapacityConfig.java' `
  'src/main/java/fr/brickcraft/importer/StudioIoArchive.java' `
  'src/main/java/fr/brickcraft/importer/LDrawProjectReader.java' `
  'src/main/java/fr/brickcraft/world/PreciseMeshCollisionPlan.java' `
  'TEST_PHYSIQUE_V012.java'
if ($LASTEXITCODE -ne 0) { throw 'Compilation tests geometrie echouee' }
& java -cp $out TEST_PHYSIQUE_V012
if ($LASTEXITCODE -ne 0) { throw 'Tests geometrie echoues' }
Write-Host 'Tests mathematiques OK. Compiler maintenant Fabric, puis essayer sur une COPIE de monde.' -ForegroundColor Green
