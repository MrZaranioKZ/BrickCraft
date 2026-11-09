@echo off
setlocal
cd /d "%~dp0"
if not exist "tools\_gpu_stability_test" mkdir "tools\_gpu_stability_test"
javac -encoding UTF-8 -d "tools\_gpu_stability_test" "src\main\java\fr\brickcraft\client\PreciseGpuPlan.java" "src\main\java\fr\brickcraft\client\GpuLocalSpace.java" "tools\GpuStabilityTest.java"
if errorlevel 1 (echo ERREUR TEST GPU& pause & exit /b 1)
java -cp "tools\_gpu_stability_test" GpuStabilityTest
pause
