@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if not exist build\ldraw-integration mkdir build\ldraw-integration
javac -encoding UTF-8 -d build\ldraw-integration tools\test_stubs\net\minecraft\util\math\BlockPos.java src\main\java\fr\brickcraft\core\MaterialFinish.java src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java src\main\java\fr\brickcraft\core\BrickKind.java src\main\java\fr\brickcraft\core\BrickPiece.java src\main\java\fr\brickcraft\client\StudioAssembly.java src\test\java\fr\brickcraft\ldraw\LDrawBrickPieceSelfTest.java
if errorlevel 1 (echo ECHEC compilation & pause & exit /b 1)
java -cp build\ldraw-integration fr.brickcraft.ldraw.LDrawBrickPieceSelfTest
pause
