@echo off
setlocal
cd /d "%~dp0"
if not exist "run\config\brickcraft" mkdir "run\config\brickcraft"
if not exist "studio_parts.zip" (echo ERREUR: studio_parts.zip introuvable.&pause&exit /b 1)
if exist "run\config\brickcraft\studio_parts.zip" (
    copy /Y "run\config\brickcraft\studio_parts.zip" "run\config\brickcraft\studio_parts.zip.bak" >nul
    echo Ancien supplement sauvegarde en .bak
)
copy /Y "studio_parts.zip" "run\config\brickcraft\studio_parts.zip" >nul
if errorlevel 1 (echo Echec copie&pause&exit /b 1)
echo Installe: run\config\brickcraft\studio_parts.zip
echo Conserve ta bibliotheque complete.zip au meme endroit.
echo Redemarre Minecraft. Aucune reimportation du chateau necessaire.
pause
