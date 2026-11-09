@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if not exist build\ldraw-tests mkdir build\ldraw-tests
javac -encoding UTF-8 -d build\ldraw-tests src\main\java\fr\brickcraft\core\MaterialFinish.java src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java src\test\java\fr\brickcraft\ldraw\LDrawLibrarySelfTest.java
if errorlevel 1 (echo ECHEC compilation & pause & exit /b 1)
java -cp build\ldraw-tests fr.brickcraft.ldraw.LDrawLibrarySelfTest
pause
