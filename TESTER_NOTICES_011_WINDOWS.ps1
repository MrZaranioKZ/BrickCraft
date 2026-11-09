$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'JDK 21 requis : javac introuvable' }
$out = Join-Path $PSScriptRoot 'out_011_tests'
New-Item -Force -ItemType Directory $out | Out-Null
$files = @(
 'src/main/java/fr/brickcraft/importer/LDrawProjectReader.java',
 'src/main/java/fr/brickcraft/guide/BrickGuidePlan.java',
 'src/main/java/fr/brickcraft/guide/BrickGuideGpuBatches.java',
 'src/main/java/fr/brickcraft/guide/BrickSourceStepReader.java',
 'TEST_NOTICE_011.java'
)
& javac -encoding UTF-8 -d $out @files
if ($LASTEXITCODE -ne 0) { throw 'Compilation des tests de notices echouee' }
$original = Join-Path $env:APPDATA '.minecraft\config\brickcraft\imports\21063-Neuschwanstein-Castle.io'
if (Test-Path $original) {
  & java -cp $out TEST_NOTICE_011 $original
} else {
  & java -cp $out TEST_NOTICE_011
  Write-Host 'Source du chateau non trouvee dans .minecraft\config\brickcraft\imports ; tests generiques realises.' -ForegroundColor Yellow
}
if ($LASTEXITCODE -ne 0) { throw 'Un test de notices a echoue' }
Write-Host 'OK - Notices, regroupement et cache GPU (tests Java purs).' -ForegroundColor Green
Write-Host 'Pour construire le mod Fabric : CONSTRUIRE_WINDOWS.bat' -ForegroundColor Cyan
