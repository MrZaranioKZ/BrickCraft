@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
echo ====================================================
echo BrickCraft 0.25.0 RC1 - Audit de pre-version 1.0
echo ====================================================
where javac >nul 2>nul || (echo Java 21 JDK requis & pause & exit /b 1)
where java >nul 2>nul || (echo Java introuvable & pause & exit /b 1)
where py >nul 2>nul
if not errorlevel 1 (
    set "PYTHON=py -3"
) else (
    where python >nul 2>nul || (echo Python 3 necessaire pour l'audit complet & pause & exit /b 1)
    set "PYTHON=python"
)
set "AUDIT_CLASS=%TEMP%\brickcraft_audit_v025_syntax"
if not exist "%AUDIT_CLASS%" mkdir "%AUDIT_CLASS%"
echo.
echo [1/4] Analyse syntaxique Java 21
javac -encoding UTF-8 -d "%AUDIT_CLASS%" TEST_SYNTAX_V017.java || goto fail
java -cp "%AUDIT_CLASS%" TEST_SYNTAX_V017 || goto fail
echo.
echo [2/4] Controle de traduction FR/EN et messages
%PYTHON% TESTER_LOCALISATION_V0241.py || goto fail
echo.
echo [3/4] 11 groupes de tests autonomes
%PYTHON% tools\AUDIT_RUN_TESTS_V025.py || goto fail
echo.
echo [4/4] Audit complet statique des fichiers, ressources, interfaces
%PYTHON% tools\AUDIT_COMPLET_V025.py || goto fail
echo.
echo Tests PASS. Voir AUDIT_PRE_V1_0_V025.md et AUDIT_COMPLET_V025.json.
echo ATTENTION : une compilation Fabric et un essai Minecraft restent obligatoires.
pause
exit /b 0
:fail
echo.
echo Echec d'un controle. Ne remplace pas l'ancien JAR BrickCraft.
pause
exit /b 1
