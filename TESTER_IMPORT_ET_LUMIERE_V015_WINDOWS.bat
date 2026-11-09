@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
  echo JDK 21 requis : https://adoptium.net/
  pause
  exit /b 1
)
if not exist "build\tests_v015" mkdir "build\tests_v015"
javac -encoding UTF-8 -d build\tests_v015 src\main\java\fr\brickcraft\client\BrickLightingMath.java src\main\java\fr\brickcraft\client\BrickModelOcclusion.java TEST_ECLAIRAGE_015.java
if errorlevel 1 goto error
java -cp build\tests_v015 TEST_ECLAIRAGE_015
if errorlevel 1 goto error
call TESTER_UI_GPU_V014_WINDOWS.bat
if errorlevel 1 goto error
echo Tests autonomes V0.15 OK. La compilation Minecraft necessite CONSTRUIRE_WINDOWS.bat.
pause
exit /b 0
:error
echo Echec des tests V0.15.
pause
exit /b 1
