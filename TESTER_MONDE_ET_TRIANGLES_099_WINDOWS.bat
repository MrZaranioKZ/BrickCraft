@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0TESTER_MONDE_ET_TRIANGLES_099_WINDOWS.ps1"
if errorlevel 1 (
 echo ECHEC DES TESTS V0.9.9.
 pause
 exit /b 1
)
echo Tests autonomes V0.9.9 OK.
pause
