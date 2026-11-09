@echo off
setlocal
cd /d "%~dp0"
echo BrickCraft V0.24 RC1 - Syntaxe Java et controle des langues
where javac >nul 2>&1 || (echo Java 21 JDK requis & exit /b 1)
javac TEST_SYNTAX_V017.java || exit /b 1
java TEST_SYNTAX_V017 || exit /b 1
where py >nul 2>&1 && (py -3 CONTROLER_TRADUCTIONS_V024.py) || (echo Python facultatif : ouvrir AUDIT_TRADUCTIONS_V024.json)
echo Test statique termine - La compilation Fabric reste necessaire.
pause
