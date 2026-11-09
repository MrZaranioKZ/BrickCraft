$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$configDir = Join-Path $env:APPDATA '.minecraft\config'
if (Test-Path '.\run\config\brickcraft\complete.zip') { $configDir = (Resolve-Path '.\run\config').Path }
elseif (Test-Path '.\config\brickcraft\complete.zip') { $configDir = (Resolve-Path '.\config').Path }
if (-not (Test-Path (Join-Path $configDir 'brickcraft\complete.zip'))) {
    Write-Host 'ERREUR : complete.zip absent de config/brickcraft/. Installe LDraw.'
    exit 1
}
$out=Join-Path $PSScriptRoot 'out_099_test'
New-Item -Force -ItemType Directory $out | Out-Null
$classes=@(
'src/main/java/fr/brickcraft/core/MaterialFinish.java',
'src/main/java/fr/brickcraft/ldraw/LDrawLibrary.java',
'src/main/java/fr/brickcraft/importer/LDrawProjectReader.java',
'src/main/java/fr/brickcraft/importer/PreciseReferenceMapper.java',
'src/main/java/fr/brickcraft/importer/PreciseWorldModel.java',
'src/main/java/fr/brickcraft/importer/PreciseProjectIO.java',
'src/main/java/fr/brickcraft/importer/PreciseFreeAssembly.java',
'src/main/java/fr/brickcraft/world/PreciseWorldEditMath.java',
'src/main/java/fr/brickcraft/client/WorldPiecePicker.java',
'src/main/java/fr/brickcraft/client/PreciseMeshLods.java',
'src/main/java/fr/brickcraft/client/BrickGpuQualityConfig.java',
'src/main/java/fr/brickcraft/client/PreviewSelectionMath.java',
'src/main/java/fr/brickcraft/guide/BrickGuidePlanner.java',
'tools/WorldEditing099Test.java',
'tools/FreePlacement096Test.java',
'tools/Interaction098Test.java',
'tools/BuildGuide098Test.java'
)
& javac -encoding UTF-8 -d $out @classes
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& java -cp $out WorldEditing099Test $configDir
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& java -cp $out FreePlacement096Test $configDir 'EXEMPLES/Neuschwanstein_21063.bcpx'
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& java -cp $out Interaction098Test $configDir 'EXEMPLES/Neuschwanstein_21063.bcpx'
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& java -cp $out BuildGuide098Test 'EXEMPLES/Neuschwanstein_21063.bcpx'
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
$mock=@(Get-ChildItem 'tools/gui_mock' -Recurse -Filter '*.java' | ForEach-Object {$_.FullName})
$mock+='src/main/java/fr/brickcraft/client/PreciseFreeScreen.java'
$mock+='tools/Interaction098GuiLayoutTest.java'
& javac -encoding UTF-8 -cp $out -d $out @mock
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& java -cp $out Interaction098GuiLayoutTest
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
Write-Host 'Tests Java OK. Ceci ne remplace PAS une compilation Gradle Fabric ni un essai en jeu.'
