@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if not exist build\render-tests mkdir build\render-tests
javac -encoding UTF-8 -d build\render-tests -sourcepath tools\render_mock tools\render_mock\fr\brickcraft\client\RenderLifecycleTest.java src\main\java\fr\brickcraft\client\BrickCellRenderer.java src\main\java\fr\brickcraft\client\BrickRenderDistanceConfig.java
if errorlevel 1 (
 echo ERREUR : compilation du test du renderer.
 pause
 exit /b 1
)
java -cp build\render-tests fr.brickcraft.client.RenderLifecycleTest
if errorlevel 1 (
 echo ECHEC : le renderer a utilise un buffer termine.
 pause
 exit /b 1
)
echo TEST RENDERER OK : gestion des buffers de rendu simules.
echo Ce test ne remplace pas une execution dans Minecraft.
pause
