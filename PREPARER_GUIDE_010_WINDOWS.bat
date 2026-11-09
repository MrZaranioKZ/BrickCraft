@echo off
setlocal
cd /d "%~dp0"
if "%~1"=="" (
  set "INPUT=EXEMPLES\Neuschwanstein_21063.bcpx"
) else (
  set "INPUT=%~1"
)
if "%~2"=="" (
  set "OUTPUT=GUIDE_PREVIEW_010.json"
) else (
  set "OUTPUT=%~2"
)
if not exist out_010_tests mkdir out_010_tests
javac -encoding UTF-8 -d out_010_tests src\main\java\fr\brickcraft\importer\LDrawProjectReader.java src\main\java\fr\brickcraft\importer\PreciseProjectIO.java src\main\java\fr\brickcraft\guide\BrickGuidePlanner.java tools\BrickGuide010Export.java
if errorlevel 1 goto :failed
java -cp out_010_tests BrickGuide010Export "%INPUT%" "%OUTPUT%"
if errorlevel 1 goto :failed
echo.
echo Apercu genere. Les etapes NE SONT PAS des instructions officielles LEGO.
pause
exit /b 0
:failed
echo ECHEC de la preparation du guide.
pause
exit /b 1
