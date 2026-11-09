@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"
echo BrickCraft V0.6 - Installer l'archive LDraw fournie
set "SOURCE=%~dp0complete.zip"
if not exist "%SOURCE%" (
 echo Placer complete.zip a cote de ce script avant de continuer.
 pause
 exit /b 1
)
set "GAME=%APPDATA%\.minecraft"
echo Chemin du dossier d'instance Minecraft contenant mods et config :
set /p "CHOICE=Chemin [ENTREE = %GAME%] : "
if not "%CHOICE%"=="" set "GAME=%CHOICE%"
if not exist "%GAME%" (
 echo Dossier introuvable : "%GAME%"
 pause
 exit /b 1
)
if not exist "%GAME%\config\brickcraft" mkdir "%GAME%\config\brickcraft"
copy /Y "%SOURCE%" "%GAME%\config\brickcraft\complete.zip" >nul
if errorlevel 1 (
 echo Copie echouee.
 pause
 exit /b 1
)
echo Archive installee : "%GAME%\config\brickcraft\complete.zip"
echo Redemarre Minecraft ou utilise Recharger LDraw depuis le Studio.
pause
