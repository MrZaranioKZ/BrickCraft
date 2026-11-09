@echo off
setlocal
cd /d "%~dp0"
if not exist out_manage_test mkdir out_manage_test
javac -encoding UTF-8 -d out_manage_test src\main\java\fr\brickcraft\world\PreciseManageRules.java src\test\java\fr\brickcraft\world\PreciseManageRulesSelfTest.java
if errorlevel 1 goto fail
java -cp out_manage_test fr.brickcraft.world.PreciseManageRulesSelfTest
if errorlevel 1 goto fail
echo.
echo Le test des regles est OK. Compiler Fabric ensuite avec CONSTRUIRE_WINDOWS.bat.
pause
exit /b 0
:fail
echo Echec des tests.
pause
exit /b 1
