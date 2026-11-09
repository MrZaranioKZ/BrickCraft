@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
where javac >nul 2>nul || (echo JDK 21 requis & pause & exit /b 1)
if not exist "build\tests_v017" mkdir "build\tests_v017"
javac -encoding UTF-8 -d build\tests_v017 ^
 src\main\java\fr\brickcraft\core\BrickCapacityConfig.java ^
 src\main\java\fr\brickcraft\core\MaterialFinish.java ^
 src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java ^
 src\main\java\fr\brickcraft\importer\PreciseReferenceMapper.java ^
 src\main\java\fr\brickcraft\importer\StudioIoArchive.java ^
 src\main\java\fr\brickcraft\importer\LDrawProjectReader.java ^
 src\main\java\fr\brickcraft\importer\PreciseWorldModel.java ^
 src\main\java\fr\brickcraft\importer\PreciseProjectIO.java ^
 src\main\java\fr\brickcraft\importer\StudioProjectAnalysis.java ^
 src\main\java\fr\brickcraft\importer\MissingPartsReport.java ^
 src\main\java\fr\brickcraft\importer\StudioCustomPartsInstaller.java ^
 src\main\java\fr\brickcraft\world\PreciseMeshCollisionPlan.java ^
 TEST_GRANDS_SETS_V017.java
if errorlevel 1 goto fail
java -Xmx1g -cp build\tests_v017 TEST_GRANDS_SETS_V017 %*
if errorlevel 1 goto fail
echo Tests V0.17 OK. Lance CONSTRUIRE_WINDOWS.bat pour compiler Fabric.
pause
exit /b 0
:fail
echo Tests V0.17 echoues.
pause
exit /b 1
