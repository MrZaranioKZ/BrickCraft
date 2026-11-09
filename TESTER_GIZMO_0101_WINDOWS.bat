@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0TESTER_GIZMO_0101_WINDOWS.ps1"
if errorlevel 1 (
    echo.
    echo ECHEC TESTS GIZMO 0.10.1
    pause
    exit /b 1
)
echo.
echo Tous les tests du gizmo sont passes.
pause
