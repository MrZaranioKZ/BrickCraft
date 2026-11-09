@echo off
setlocal
cd /d "%~dp0"
if "%~1"=="" (
    echo Glisse un projet BrickLink Studio .io sur ce .bat
    pause
    exit /b 1
)
where py >nul 2>&1
if not errorlevel 1 (
    py -3 tools\extract_studio_parts.py "%~1" "run\config\brickcraft\studio_parts.zip"
) else (
    python tools\extract_studio_parts.py "%~1" "run\config\brickcraft\studio_parts.zip"
)
if errorlevel 1 (echo EXTRACTION ECHOUEE&pause&exit /b 1)
echo Extraction terminee. Relance le jeu.
pause
