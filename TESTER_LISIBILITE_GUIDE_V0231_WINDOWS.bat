@echo off
setlocal
cd /d "%~dp0"
where javac >nul 2>&1 || (echo Java 21 JDK requis. & exit /b 1)
set "OUT=%TEMP%\brickcraft_guide_layout_v0231"
if not exist "%OUT%" mkdir "%OUT%"
javac -encoding UTF-8 -d "%OUT%" src\main\java\fr\brickcraft\client\BrickGuideLayout.java TEST_GUIDE_LAYOUT_V0231.java
if errorlevel 1 exit /b 1
java -cp "%OUT%" TEST_GUIDE_LAYOUT_V0231
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -Command "$s = Get-Content -Raw 'src/main/java/fr/brickcraft/client/BrickGuideBookScreen.java'; if ($s.Contains('drawTextWithShadow')) { throw 'Ombres de texte encore presentes' }; if (-not $s.Contains('drawText(textRenderer')) { throw 'Rendu texte manquant' }; Write-Host 'PASS: rendu guide sans ombre noire'"
if errorlevel 1 exit /b 1
echo Tests autonomes du Guidebook termines.
pause
