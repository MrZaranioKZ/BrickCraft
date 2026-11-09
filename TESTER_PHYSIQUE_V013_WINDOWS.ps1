$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$out = Join-Path $PSScriptRoot 'out_v013_tests'
New-Item -ItemType Directory -Path $out -Force | Out-Null
& javac -encoding UTF-8 -d $out `
  'src/main/java/fr/brickcraft/core/BrickCapacityConfig.java' `
  'src/main/java/fr/brickcraft/importer/StudioIoArchive.java' `
 'src/main/java/fr/brickcraft/importer/LDrawProjectReader.java' `
 'src/main/java/fr/brickcraft/world/PreciseMeshCollisionPlan.java' `
 'src/main/java/fr/brickcraft/world/ExplorationStepRules.java' `
 'TEST_PHYSIQUE_V012.java' `
 'TEST_PHYSIQUE_V013.java'
if ($LASTEXITCODE -ne 0) { throw 'Compilation des tests collision echouee' }
& java -cp $out TEST_PHYSIQUE_V013
if ($LASTEXITCODE -ne 0) { throw 'Echec des tests collisions V0.13' }
Write-Host 'Tests V0.13 OK. Compiler Fabric, puis tester dans une COPIE du monde.' -ForegroundColor Green
