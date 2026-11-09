@echo off
setlocal
cd /d "%~dp0"
if not exist build\layout-tests mkdir build\layout-tests
javac -encoding UTF-8 -d build\layout-tests src\main\java\fr\brickcraft\client\StudioLayout.java tools\StudioLayoutTest.java
if errorlevel 1 (echo ERREUR compilation des tests de mise en page & pause & exit /b 1)
java -cp build\layout-tests StudioLayoutTest
pause
