@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0TESTER_SNAP_010_WINDOWS.ps1"
if errorlevel 1 (
  echo.
  echo ECHEC des tests. Regarde la sortie ci-dessus.
  pause
  exit /b 1
)
echo.
echo Tests V0.10.0 termines. La compilation Fabric reste a faire via CONSTRUIRE_WINDOWS.bat.
pause
