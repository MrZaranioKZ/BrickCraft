@echo off
setlocal
cd /d "%~dp0"
if not exist build\v031-tests mkdir build\v031-tests
javac -encoding UTF-8 -d build\v031-tests tools\test_stubs\net\minecraft\util\math\BlockPos.java src\main\java\fr\brickcraft\core\MaterialFinish.java src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java src\main\java\fr\brickcraft\core\BrickKind.java src\main\java\fr\brickcraft\core\BrickPalette.java src\main\java\fr\brickcraft\core\BrickCatalog.java src\main\java\fr\brickcraft\core\BrickPiece.java src\main\java\fr\brickcraft\client\BrickSelection.java src\main\java\fr\brickcraft\client\StudioAssembly.java tools\Upgrade031Test.java
if errorlevel 1 (echo ECHEC compilation des tests & pause & exit /b 1)
java -cp build\v031-tests Upgrade031Test
pause
