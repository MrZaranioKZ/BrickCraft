@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if not exist "build\import-tests" mkdir "build\import-tests"
javac -encoding UTF-8 -d "build\import-tests" ^
 tools\test_stubs\net\minecraft\util\math\BlockPos.java ^
 src\main\java\fr\brickcraft\core\MaterialFinish.java ^
 src\main\java\fr\brickcraft\core\BrickKind.java ^
 src\main\java\fr\brickcraft\core\BrickPiece.java ^
 src\main\java\fr\brickcraft\client\StudioAssembly.java ^
 src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java ^
 src\main\java\fr\brickcraft\importer\LDrawProjectReader.java ^
 src\main\java\fr\brickcraft\importer\StudioProjectAnalysis.java ^
 src\main\java\fr\brickcraft\importer\BrickCraftProjectConverter.java ^
 tools\ImportProjectSelfTest.java
if errorlevel 1 (echo Erreur pendant la compilation & pause & exit /b 1)
set "MC_CONFIG=%~dp0run\config"
if not exist "%MC_CONFIG%\brickcraft\complete.zip" set "MC_CONFIG=%APPDATA%\.minecraft\config"
java -cp "build\import-tests" ImportProjectSelfTest "%MC_CONFIG%"
if errorlevel 1 (echo ECHEC & pause & exit /b 1)
pause
