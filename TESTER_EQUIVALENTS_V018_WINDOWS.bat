@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
where javac >nul 2>nul || (echo JDK 21 requis & pause & exit /b 1)
if not exist "build\tests_v018" mkdir "build\tests_v018"
javac -encoding UTF-8 -d build\tests_v018 ^
 src\main\java\fr\brickcraft\importer\StudioIoArchive.java ^
 src\main\java\fr\brickcraft\importer\LDrawProjectReader.java ^
 src\main\java\fr\brickcraft\importer\PreciseProjectIO.java ^
 src\main\java\fr\brickcraft\importer\PartReplacementMatcher.java ^
 src\main\java\fr\brickcraft\importer\PartReplacementEditor.java ^
 TEST_EQUIVALENTS_V018.java
if errorlevel 1 goto fail
java -cp build\tests_v018 TEST_EQUIVALENTS_V018 %*
if errorlevel 1 goto fail
echo.
echo Tests des remplacements OK. Lance CONSTRUIRE_WINDOWS.bat pour compiler le mod Fabric.
pause
exit /b 0
:fail
echo Les tests ont echoue.
pause
exit /b 1
