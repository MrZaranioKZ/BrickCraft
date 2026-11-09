@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if "%~1"=="" (
 echo Glisse un fichier .io, .ldr ou .mpd sur ce script pour le copier dans l'instance de test.
 echo Tu peux fournir le dossier de l'instance en second argument.
 pause
 exit /b 1
)
if not exist "%~1" (echo Fichier inexistant & pause & exit /b 1)
set "INSTANCE=%~dp0run"
if not "%~2"=="" set "INSTANCE=%~2"
if not exist "%INSTANCE%\config\brickcraft\imports" mkdir "%INSTANCE%\config\brickcraft\imports"
copy /Y "%~1" "%INSTANCE%\config\brickcraft\imports\"
echo.
echo Dans le jeu : B puis Pos. puis Importer (ou Ctrl+I dans le Studio).
echo Dossier : %INSTANCE%\config\brickcraft\imports
pause
