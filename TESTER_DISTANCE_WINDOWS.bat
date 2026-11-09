@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if not exist "build\test_render_distance" mkdir "build\test_render_distance"
javac -encoding UTF-8 -d "build\test_render_distance" "src\main\java\fr\brickcraft\client\BrickRenderDistanceConfig.java" "tools\RenderDistanceConfigTest.java"
if errorlevel 1 goto failed
java -cp "build\test_render_distance" RenderDistanceConfigTest
if errorlevel 1 goto failed
echo Tests de distance OK.
pause
exit /b 0
:failed
echo Tests echoues.
pause
exit /b 1
