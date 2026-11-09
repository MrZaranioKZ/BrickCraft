@echo off
setlocal
cd /d "%~dp0"
echo BrickCraft V0.25.2 RC3 - Corrections et audit Codex
py -3 -c "import sys; sys.exit(sys.version_info < (3, 8))" >nul 2>nul
if errorlevel 1 (
  python -c "import sys; sys.exit(sys.version_info < (3, 8))" >nul 2>nul
  if errorlevel 1 (
    echo Python 3.8 ou plus recent est requis.
    goto :failed
  )
  set "PY=python"
) else (
  set "PY=py -3"
)
%PY% tools\TEST_JAVA_SYNTAX_V0251.py || goto :failed
%PY% tools\TEST_UI_LOCALISATION_V0251.py || goto :failed
%PY% tools\TEST_CORRECTIFS_V0252.py || goto :failed
%PY% tools\AUDIT_I18N_LAYOUT_V0252.py || goto :failed
%PY% tools\AUDIT_RUN_TESTS_V025.py || goto :failed
echo.
echo OK. Rapports AUDIT_I18N_RESTANT_V0252.csv et .json disponibles.
echo Les tests ne remplacent pas la compilation Fabric ou un essai en jeu.
pause
exit /b 0
:failed
echo Echec des tests. Voir les messages ci-dessus.
pause
exit /b 1
