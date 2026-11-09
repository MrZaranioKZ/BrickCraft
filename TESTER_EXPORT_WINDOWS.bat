@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if not exist build\export-tests mkdir build\export-tests
javac -encoding UTF-8 -d build\export-tests tools\test_stubs\net\minecraft\util\math\BlockPos.java src\main\java\fr\brickcraft\core\MaterialFinish.java src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java src\main\java\fr\brickcraft\core\BrickKind.java src\main\java\fr\brickcraft\core\BrickPiece.java src\main\java\fr\brickcraft\core\StudioExportLimits.java src\main\java\fr\brickcraft\client\StudioAssembly.java tools\StudioExportLimitsTest.java
if errorlevel 1 (echo ERREUR compilation tests & pause & exit /b 1)
java -cp build\export-tests StudioExportLimitsTest
if errorlevel 1 (pause & exit /b 1)
echo Tests export reussis.
pause
