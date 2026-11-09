@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0TESTER_PHYSIQUE_ET_TAILLE_V012_WINDOWS.ps1"
if errorlevel 1 (echo ECHEC : verifie les messages) else (echo TESTS MATHEMATIQUES REUSSIS)
pause
