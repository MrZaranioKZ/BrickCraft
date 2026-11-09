@echo off
setlocal
cd /d "%~dp0"
if not exist EXEMPLES\Neuschwanstein_21063.bcpx (
  echo Le projet exemple est absent.
  pause
  exit /b 1
)
if not exist run\config\brickcraft\imports mkdir run\config\brickcraft\imports
copy /Y EXEMPLES\Neuschwanstein_21063.bcpx run\config\brickcraft\imports\Neuschwanstein_21063.bcpx
if errorlevel 1 (
  echo Echec de la copie.
  pause
  exit /b 1
)
echo Exemple installe : run\config\brickcraft\imports\Neuschwanstein_21063.bcpx
pause
