@echo off
setlocal
cd /d "%~dp0"
where javac >nul 2>&1 || (echo JDK 21 manquant. & pause & exit /b 1)
set "CFG=%~dp0run\config"
set "IO=%~dp0run\config\brickcraft\imports\21063-Neuschwanstein-Castle.io"
if not exist "%CFG%\brickcraft\complete.zip" (
 echo Copie complete.zip dans run\config\brickcraft\ avant ce test.
 pause
 exit /b 1
)
if not exist "%IO%" (
 echo Place 21063-Neuschwanstein-Castle.io dans run\config\brickcraft\imports\
 pause
 exit /b 1
)
if not exist build\v09-tests mkdir build\v09-tests
javac -encoding UTF-8 -d build\v09-tests src\main\java\fr\brickcraft\core\MaterialFinish.java src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java src\main\java\fr\brickcraft\importer\LDrawProjectReader.java src\main\java\fr\brickcraft\importer\PreciseProjectIO.java src\main\java\fr\brickcraft\importer\PreciseLDrawGeometry.java tools\PreciseImportV09Test.java
if errorlevel 1 (pause & exit /b 1)
java -Xmx2G -cp build\v09-tests PreciseImportV09Test "%CFG%" "%IO%"
pause
