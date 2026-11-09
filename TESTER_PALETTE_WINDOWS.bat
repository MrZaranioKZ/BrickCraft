@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
set "GAME=%APPDATA%\.minecraft"
set /p "GAMEX=Dossier Minecraft [ENTREE = %GAME%] : "
if not "%GAMEX%"=="" set "GAME=%GAMEX%"
if not exist "%GAME%\config\brickcraft\complete.zip" (
 echo Bibliotheque non trouvee dans %GAME%\config\brickcraft\complete.zip
 pause
 exit /b 1
)
if not exist build\palette-test mkdir build\palette-test
javac -encoding UTF-8 -d build\palette-test tools\test_stubs\net\minecraft\util\math\BlockPos.java src\main\java\fr\brickcraft\core\MaterialFinish.java src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java src\main\java\fr\brickcraft\core\BrickKind.java src\main\java\fr\brickcraft\core\BrickPiece.java src\main\java\fr\brickcraft\core\BrickPalette.java src\main\java\fr\brickcraft\core\BrickCatalog.java src\main\java\fr\brickcraft\client\BrickSelection.java src\main\java\fr\brickcraft\client\StudioAssembly.java src\main\java\fr\brickcraft\client\StudioProjectIO.java tools\PaletteV06Test.java
if errorlevel 1 (echo Echec compilation des tests & pause & exit /b 1)
java -Xmx2G -cp build\palette-test PaletteV06Test "%GAME%\config"
pause
