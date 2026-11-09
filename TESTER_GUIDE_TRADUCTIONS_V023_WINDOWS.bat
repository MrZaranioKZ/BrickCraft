@echo off
setlocal
cd /d "%~dp0"
echo === BrickCraft V0.23 / controle guide et localisations ===
where javac >nul 2>nul
if errorlevel 1 (
  echo ERREUR : JDK 21 requis pour la verification syntaxique.
  pause
  exit /b 1
)
javac -encoding UTF-8 TEST_SYNTAX_V017.java
if errorlevel 1 goto :fail
java TEST_SYNTAX_V017
if errorlevel 1 goto :fail
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0TESTER_GUIDE_TRADUCTIONS_V023_WINDOWS.ps1"
if errorlevel 1 goto :fail
echo SUCCES : verifications statiques terminees.
pause
exit /b 0
:fail
echo ECHEC : consulter les erreurs ci-dessus.
pause
exit /b 1
