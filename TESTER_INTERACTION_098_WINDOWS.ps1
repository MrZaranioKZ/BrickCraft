$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$configDir = Join-Path $env:APPDATA '.minecraft\config'
if (Test-Path '.\run\config\brickcraft\complete.zip') { $configDir = (Resolve-Path '.\run\config').Path }
elseif (Test-Path '.\config\brickcraft\complete.zip') { $configDir = (Resolve-Path '.\config').Path }
if (-not (Test-Path (Join-Path $configDir 'brickcraft\complete.zip'))) {
    Write-Host 'ERREUR : LDraw complete.zip manquant dans config/brickcraft/.'
    exit 1
}

$out = Join-Path $PSScriptRoot 'out_interaction_098_test'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$core = @(
  'src/main/java/fr/brickcraft/core/MaterialFinish.java',
  'src/main/java/fr/brickcraft/ldraw/LDrawLibrary.java',
  'src/main/java/fr/brickcraft/importer/LDrawProjectReader.java',
  'src/main/java/fr/brickcraft/importer/PreciseReferenceMapper.java',
  'src/main/java/fr/brickcraft/importer/PreciseWorldModel.java',
  'src/main/java/fr/brickcraft/importer/PreciseProjectIO.java',
  'src/main/java/fr/brickcraft/importer/PreciseFreeAssembly.java',
  'src/main/java/fr/brickcraft/client/PreviewSelectionMath.java',
  'src/main/java/fr/brickcraft/guide/BrickGuidePlanner.java',
  'tools/FreePlacement096Test.java',
  'tools/Interaction098Test.java',
  'tools/BuildGuide098Test.java'
)
& javac -encoding UTF-8 -d $out @core
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp $out FreePlacement096Test $configDir 'EXEMPLES/Neuschwanstein_21063.bcpx'
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp $out Interaction098Test $configDir 'EXEMPLES/Neuschwanstein_21063.bcpx'
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp $out BuildGuide098Test 'EXEMPLES/Neuschwanstein_21063.bcpx'
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
# Compile the new free-placement Screen against minimal Minecraft API stubs.
# This catches Java errors but is not a full Fabric build.
$mock = @(Get-ChildItem 'tools/gui_mock' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$mock += 'src/main/java/fr/brickcraft/client/PreciseFreeScreen.java'
$mock += 'tools/Interaction098GuiLayoutTest.java'
& javac -encoding UTF-8 -cp $out -d $out @mock
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp $out Interaction098GuiLayoutTest
exit $LASTEXITCODE
