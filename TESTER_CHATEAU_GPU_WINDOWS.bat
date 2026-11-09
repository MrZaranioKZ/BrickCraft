@echo off
setlocal
cd /d "%~dp0"
if not exist "run\config\brickcraft\complete.zip" (
  echo Bibliotheque manquante: run\config\brickcraft\complete.zip
  pause
  exit /b 1
)
set "CHATEAU=EXEMPLES\Neuschwanstein_21063.bcpx"
if not exist "%CHATEAU%" (
  echo Exemple de chateau introuvable.
  pause
  exit /b 1
)
if not exist "tools\_gpu_castle_test" mkdir "tools\_gpu_castle_test"
javac -encoding UTF-8 -d "tools\_gpu_castle_test" "src\main\java\fr\brickcraft\core\MaterialFinish.java" "src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java" "src\main\java\fr\brickcraft\importer\LDrawProjectReader.java" "src\main\java\fr\brickcraft\importer\PreciseProjectIO.java" "src\main\java\fr\brickcraft\importer\PreciseReferenceMapper.java" "src\main\java\fr\brickcraft\importer\PreciseWorldModel.java" "src\main\java\fr\brickcraft\client\PreciseGpuPlan.java" "tools\GpuCastleCoverageTest.java"
if errorlevel 1 (echo ECHEC compilation test& pause & exit /b 1)
java -cp "tools\_gpu_castle_test" GpuCastleCoverageTest "run\config" "%CHATEAU%"
pause
