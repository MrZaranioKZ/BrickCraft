@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if "%~1"=="" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFIER_LDRAW_WINDOWS.ps1"
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFIER_LDRAW_WINDOWS.ps1" -MinecraftFolder "%~1"
)
pause
