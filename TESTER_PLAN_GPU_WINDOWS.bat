@echo off
setlocal
cd /d "%~dp0"
if not exist "tools\_gpu_test" mkdir "tools\_gpu_test"
javac -encoding UTF-8 -d "tools\_gpu_test" "src\main\java\fr\brickcraft\client\PreciseGpuPlan.java" "tools\GpuBatchPlanTest.java"
if errorlevel 1 (echo ECHEC de compilation du plan GPU& pause & exit /b 1)
java -cp "tools\_gpu_test" GpuBatchPlanTest
pause
