@echo off
setlocal
cd /d "%~dp0"
if not exist "build\shape-tests" mkdir "build\shape-tests"
set "SRC=src\main\java\fr\brickcraft"
javac -encoding UTF-8 -d "build\shape-tests" "%SRC%\core\MaterialFinish.java" "%SRC%\ldraw\LDrawLibrary.java" "%SRC%\client\PreciseMeshLods.java" "TEST_LOD_FORMES.java"
if errorlevel 1 (echo Echec compilation tests V0.9.3 & pause & exit /b 1)
echo.
echo [TEST] Reduction de maillage LDraw
java -cp "build\shape-tests" TEST_LOD_FORMES
if errorlevel 1 (echo Echec tests V0.9.3 & pause & exit /b 1)
echo.
echo Tous les tests V0.9.3 sont passes.
pause
