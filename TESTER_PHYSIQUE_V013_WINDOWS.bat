@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0TESTER_PHYSIQUE_V013_WINDOWS.ps1"
if errorlevel 1 (
 echo.
 echo ERREUR dans les tests V0.13
 pause
 exit /b 1
)
pause
