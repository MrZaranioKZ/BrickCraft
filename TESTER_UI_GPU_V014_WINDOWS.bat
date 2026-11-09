@echo off
setlocal
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
    echo Java JDK 21 requis pour ces tests.
    pause
    exit /b 1
)
if not exist build\tests_v014 mkdir build\tests_v014
javac -encoding UTF-8 -d build\tests_v014 src\main\java\fr\brickcraft\client\StudioLayout.java src\main\java\fr\brickcraft\client\PreviewPerfPolicy.java TEST_UI_GPU_014.java
if errorlevel 1 goto error
java -cp build\tests_v014 TEST_UI_GPU_014
if errorlevel 1 goto error
pause
exit /b 0
:error
echo ECHEC DES TESTS UI / GPU.
pause
exit /b 1
