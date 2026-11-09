$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$out = Join-Path $PSScriptRoot 'out_0111_tests'
New-Item -ItemType Directory -Path $out -Force | Out-Null
$files = @(
 'src/main/java/fr/brickcraft/core/MaterialFinish.java',
 'src/main/java/fr/brickcraft/ldraw/LDrawLibrary.java',
 'src/main/java/fr/brickcraft/importer/LDrawProjectReader.java',
 'src/main/java/fr/brickcraft/importer/PreciseProjectIO.java',
 'src/main/java/fr/brickcraft/importer/PreciseReferenceMapper.java',
 'src/main/java/fr/brickcraft/importer/PreciseWorldModel.java',
 'src/main/java/fr/brickcraft/guide/BrickGuidePlan.java',
 'src/main/java/fr/brickcraft/guide/BrickGuideGpuBatches.java',
 'src/main/java/fr/brickcraft/guide/BrickSourceStepReader.java',
 'src/main/java/fr/brickcraft/world/PreciseCollisionPlan.java',
 'tools/CollisionGuide0111Test.java', 'TEST_NOTICE_011.java'
)
& javac -encoding UTF-8 -d $out @files
if ($LASTEXITCODE -ne 0) { throw 'Compilation des tests Java purs echouee' }
& java -cp $out CollisionGuide0111Test
if ($LASTEXITCODE -ne 0) { throw 'Plan physique incorrect' }
& java -cp $out TEST_NOTICE_011
if ($LASTEXITCODE -ne 0) { throw 'Notices incorrectes' }
Write-Host 'OK - geometrie physique et notices (tests Java purs).' -ForegroundColor Green
Write-Host 'Reste indispensable: CONSTRUIRE_WINDOWS.bat + essai du monde COPIE.' -ForegroundColor Yellow
