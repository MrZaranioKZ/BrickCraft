@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "BC_TARGET=%~1"
if not defined BC_TARGET if exist "%~dp0run\" (
  echo Une instance de developpement Gradle a ete detectee dans :
  echo %~dp0run
  echo.
  echo [1] Installer dans le dossier run de ce projet
  echo [2] Installer dans %%APPDATA%%\.minecraft
  set /p "BC_CHOICE=Choix (1 ou 2) : "
  if "!BC_CHOICE!"=="1" set "BC_TARGET=%~dp0run"
)
if defined BC_TARGET (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0INSTALLER_LDRAW_WINDOWS.ps1" -MinecraftFolder "!BC_TARGET!"
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0INSTALLER_LDRAW_WINDOWS.ps1"
)
if errorlevel 1 echo ECHEC : telecharge complete.zip sur https://library.ldraw.org/updates
pause
