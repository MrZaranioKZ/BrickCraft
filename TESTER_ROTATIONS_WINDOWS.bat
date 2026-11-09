@echo off
setlocal
cd /d "%~dp0"
if not exist build\rotation-tests mkdir build\rotation-tests
javac -encoding UTF-8 -d build\rotation-tests tools\test_stubs\net\minecraft\util\math\BlockPos.java src\main\java\fr\brickcraft\core\MaterialFinish.java src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java src\main\java\fr\brickcraft\core\BrickKind.java src\main\java\fr\brickcraft\core\BrickPalette.java src\main\java\fr\brickcraft\core\BrickPiece.java src\main\java\fr\brickcraft\client\BrickSelection.java src\main\java\fr\brickcraft\client\StudioAssembly.java tools\RotationAndCatalogTest.java
if errorlevel 1 (echo ECHEC compilation tests & pause & exit /b 1)
java -cp build\rotation-tests RotationAndCatalogTest
pause
