@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0TESTER_INTERACTION_098_WINDOWS.ps1"
if errorlevel 1 (
  echo.
  echo ECHEC DES TESTS V0.9.8. Voir erreurs ci-dessus.
  pause
  exit /b 1
)
echo.
echo Tous les tests locaux V0.9.8 sont passes.
pause
