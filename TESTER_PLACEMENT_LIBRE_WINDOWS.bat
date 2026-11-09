@echo off
setlocal
cd /d "%~dp0"
set "TEST_CONFIG=%APPDATA%\.minecraft\config"
if exist "run\config\brickcraft\complete.zip" set "TEST_CONFIG=%CD%\run\config"
if exist "config\brickcraft\complete.zip" set "TEST_CONFIG=%CD%\config"
if not exist "%TEST_CONFIG%\brickcraft\complete.zip" echo ATTENTION : installe complete.zip dans run/config/brickcraft ou dans .minecraft/config/brickcraft
if not exist "out_free_test" mkdir out_free_test
javac -encoding UTF-8 -d out_free_test src\main\java\fr\brickcraft\core\MaterialFinish.java src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java src\main\java\fr\brickcraft\importer\LDrawProjectReader.java src\main\java\fr\brickcraft\importer\PreciseReferenceMapper.java src\main\java\fr\brickcraft\importer\PreciseWorldModel.java src\main\java\fr\brickcraft\importer\PreciseProjectIO.java src\main\java\fr\brickcraft\importer\PreciseFreeAssembly.java tools\FreePlacement096Test.java
if errorlevel 1 goto fail
java -cp out_free_test FreePlacement096Test "%TEST_CONFIG%" "EXEMPLES\Neuschwanstein_21063.bcpx"
if errorlevel 1 goto fail
echo.
echo Tests OK
pause
exit /b 0
:fail
echo Erreur pendant les tests, voir ci-dessus.
pause
exit /b 1
