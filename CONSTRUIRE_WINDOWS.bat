@echo off
chcp 65001 >nul
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_windows.ps1" build
if errorlevel 1 (
  echo.
  echo ECHEC : regarde le message ci-dessus.
  pause
  exit /b 1
)
echo.
echo Compilation terminee.
pause
