@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if not exist "build\v08-tests" mkdir "build\v08-tests"
set "CLASSES=build\v08-tests"
javac -encoding UTF-8 -d "%CLASSES%" ^
 tools\test_stubs\net\minecraft\util\math\BlockPos.java ^
 src\main\java\fr\brickcraft\core\BrickKind.java ^
 src\main\java\fr\brickcraft\core\BrickPiece.java ^
 src\main\java\fr\brickcraft\core\ConnectorSnapper.java ^
 src\main\java\fr\brickcraft\core\MaterialFinish.java ^
 src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java ^
 src\main\java\fr\brickcraft\importer\LDrawProjectReader.java ^
 tools\ConnectorEngineV08Test.java tools\LDrawProjectReaderTest.java tools\InspectorMaterialSnapTest.java
if errorlevel 1 (echo ECHEC de compilation des tests & pause & exit /b 1)
java -cp "%CLASSES%" ConnectorEngineV08Test
if errorlevel 1 (echo Tests d'accrochage echoues & pause & exit /b 1)
java -cp "%CLASSES%" LDrawProjectReaderTest
if errorlevel 1 (echo Tests de projet LDraw echoues & pause & exit /b 1)
java -cp "%CLASSES%" InspectorMaterialSnapTest
if errorlevel 1 (echo Regression 0.7 echouee & pause & exit /b 1)
set "GAME=%~dp0run"
if not exist "%GAME%\config\brickcraft\complete.zip" set "GAME=%APPDATA%\.minecraft"
if exist "%GAME%\config\brickcraft\complete.zip" (
 echo Verification des vrais meshes LDraw...
 java -Xmx2G -cp "%CLASSES%" ConnectorEngineV08Test "%GAME%\config"
 if errorlevel 1 (echo Erreur LDraw & pause & exit /b 1)
)
echo.
echo Tests V0.8 reussis. Ils ne remplacent pas une compilation Fabric complete.
pause
