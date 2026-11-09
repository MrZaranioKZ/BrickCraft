@echo off
setlocal
cd /d "%~dp0"
echo === BrickCraft V0.24.1 RC2 - Tests statiques ===
where javac >nul 2>nul || (echo Java JDK 21 requis & exit /b 1)
javac -encoding UTF-8 TEST_SYNTAX_V017.java || exit /b 1
java TEST_SYNTAX_V017 || exit /b 1
where python >nul 2>nul
if %errorlevel%==0 (
    python TESTER_LOCALISATION_V0241.py || exit /b 1
    python CONTROLER_TRADUCTIONS_V024.py || exit /b 1
) else (
    echo Python indisponible: controle des fichiers de langue non execute.
)
echo Tests statiques termines. La compilation Fabric reste a effectuer.
pause
