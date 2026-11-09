@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if not exist build\new-features-test mkdir build\new-features-test
javac -encoding UTF-8 -d build\new-features-test ^
 tools\test_stubs\net\minecraft\util\math\BlockPos.java ^
 src\main\java\fr\brickcraft\core\MaterialFinish.java ^
 src\main\java\fr\brickcraft\core\ConnectorSnapper.java ^
 src\main\java\fr\brickcraft\core\BrickKind.java ^
 src\main\java\fr\brickcraft\core\BrickPiece.java ^
 src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java ^
 tools\InspectorMaterialSnapTest.java
if errorlevel 1 (echo ERREUR : compilation des tests & pause & exit /b 1)
set "GAME=%~dp0run"
if not exist "%GAME%\config\brickcraft\complete.zip" set "GAME=%APPDATA%\.minecraft"
if exist "%GAME%\config\brickcraft\complete.zip" (
  java -Xmx2G -cp build\new-features-test InspectorMaterialSnapTest "%GAME%\config"
) else (
  echo complete.zip absent du dossier de test : tests geometriques uniquement.
  java -cp build\new-features-test InspectorMaterialSnapTest
)
if errorlevel 1 (echo ECHEC des nouveaux tests & pause & exit /b 1)
echo.
echo Test du cycle de vie du renderer...
call TESTER_RENDERER_WINDOWS.bat
pause
