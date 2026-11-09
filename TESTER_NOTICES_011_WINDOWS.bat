@echo off
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0TESTER_NOTICES_011_WINDOWS.ps1"
if errorlevel 1 (
 echo.
 echo Echec des tests. Lis le message au-dessus.
 pause
 exit /b 1
)
echo.
echo Tests termines.
pause
