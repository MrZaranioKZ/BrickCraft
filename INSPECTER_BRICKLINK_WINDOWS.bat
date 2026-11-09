@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if "%~1"=="" (
 echo Glisse un projet .io, .ldr ou .mpd sur ce fichier .bat
 echo ou lance : INSPECTER_BRICKLINK_WINDOWS.bat "C:\Projets\MonProjet.io"
 pause
 exit /b 1
)
if not exist "build\bricklink-test" mkdir "build\bricklink-test"
javac -encoding UTF-8 -d "build\bricklink-test" ^
 src\main\java\fr\brickcraft\importer\LDrawProjectReader.java ^
 src\main\java\fr\brickcraft\importer\StudioProjectAnalysis.java ^
 src\main\java\fr\brickcraft\core\MaterialFinish.java ^
 src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java ^
 tools\InspectBricklinkProject.java
if errorlevel 1 (echo Compilation impossible & pause & exit /b 1)
set "MC_CONFIG=%~dp0run\config"
if not exist "%MC_CONFIG%\brickcraft\complete.zip" set "MC_CONFIG=%APPDATA%\.minecraft\config"
java -cp "build\bricklink-test" InspectBricklinkProject "%~1" "%MC_CONFIG%"
if errorlevel 1 (echo Analyse echouee & pause & exit /b 1)
pause
