@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_windows.ps1" runClient
if errorlevel 1 echo Le lancement a echoue : consulte la console.
pause
