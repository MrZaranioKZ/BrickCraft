@echo off
setlocal
cd /d "%~dp0"
echo BrickCraft 0.9.1 - Tests precision
if not exist run\config\brickcraft\complete.zip (
  echo Place complete.zip dans run\config\brickcraft\ avant de tester.
  pause
  exit /b 1
)
set "MODEL=%~1"
if "%~1"=="" set "MODEL=EXEMPLES\Neuschwanstein_21063.bcpx"
if not exist "%MODEL%" (
 echo Fichier introuvable: %MODEL%
 pause
 exit /b 1
)
if not exist .local-test-classes mkdir .local-test-classes
javac -encoding UTF-8 -d .local-test-classes src\main\java\fr\brickcraft\core\MaterialFinish.java src\main\java\fr\brickcraft\core\BrickCapacityConfig.java src\main\java\fr\brickcraft\importer\StudioIoArchive.java src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java src\main\java\fr\brickcraft\importer\LDrawProjectReader.java src\main\java\fr\brickcraft\importer\PreciseProjectIO.java src\main\java\fr\brickcraft\importer\PreciseReferenceMapper.java src\main\java\fr\brickcraft\importer\PreciseWorldModel.java TEST_PRECISION_MONDE.java
if errorlevel 1 goto failed
java -cp .local-test-classes TEST_PRECISION_MONDE run\config "%MODEL%"
if errorlevel 1 goto failed
echo Tests reussis.
pause
exit /b 0
:failed
echo ECHEC des tests.
pause
exit /b 1
