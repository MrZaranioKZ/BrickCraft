@echo off
setlocal
cd /d "%~dp0"
echo.
echo BrickCraft V0.25.1 RC2 - correctifs UI / traduction
where py >nul 2>nul
if %errorlevel%==0 (
  set "PY=py -3"
) else (
  set "PY=python"
)
%PY% tools\TEST_JAVA_SYNTAX_V0251.py
if errorlevel 1 goto :fail
%PY% tools\TEST_UI_LOCALISATION_V0251.py
if errorlevel 1 goto :fail
%PY% tools\AUDIT_COMPLET_V025.py
if errorlevel 1 goto :fail
%PY% tools\AUDIT_RUN_TESTS_V025.py
if errorlevel 1 goto :fail
echo.
echo Les tests autonomes ont reussi. Compile ensuite avec CONSTRUIRE_WINDOWS.bat
pause
exit /b 0
:fail
echo.
echo ECHEC: regarde les erreurs ci-dessus.
pause
exit /b 1
