@echo off
setlocal
cd /d "%~dp0"
if not exist build\studio-tests mkdir build\studio-tests
javac -encoding UTF-8 -d build\studio-tests tools\test_stubs\net\minecraft\util\math\BlockPos.java src\main\java\fr\brickcraft\core\MaterialFinish.java src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java src\main\java\fr\brickcraft\core\BrickKind.java src\main\java\fr\brickcraft\core\BrickPiece.java src\main\java\fr\brickcraft\client\StudioAssembly.java tools\StudioAssemblyTest.java
if errorlevel 1 (echo ECHEC compilation tests Studio & pause & exit /b 1)
java -cp build\studio-tests StudioAssemblyTest
pause
