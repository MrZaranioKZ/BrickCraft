@echo off
setlocal
cd /d "%~dp0"
if not exist "build\lod-tests" mkdir "build\lod-tests"
javac -encoding UTF-8 -d "build\lod-tests" "src\main\java\fr\brickcraft\client\PreciseLodPlanner.java" "TEST_LOD_PRIORITY.java"
if errorlevel 1 (echo ERREUR de compilation du test & pause & exit /b 1)
java -cp "build\lod-tests" TEST_LOD_PRIORITY
if errorlevel 1 (echo ERREUR dans les tests & pause & exit /b 1)
pause
