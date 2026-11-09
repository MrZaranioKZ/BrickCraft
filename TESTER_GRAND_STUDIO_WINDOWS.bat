@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if not exist build\large-studio-tests mkdir build\large-studio-tests
javac -encoding UTF-8 -d build\large-studio-tests tools\test_stubs\net\minecraft\util\math\BlockPos.java src\main\java\fr\brickcraft\core\MaterialFinish.java src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java src\main\java\fr\brickcraft\core\BrickKind.java src\main\java\fr\brickcraft\core\BrickPiece.java src\main\java\fr\brickcraft\client\StudioAssembly.java src\main\java\fr\brickcraft\client\StudioLayout.java src\main\java\fr\brickcraft\client\StudioProjectIO.java tools\StudioLargeWorkspaceTest.java tools\StudioProjectIOTest.java
if errorlevel 1 (echo ECHEC compilation tests & pause & exit /b 1)
java -cp build\large-studio-tests StudioLargeWorkspaceTest
if errorlevel 1 (pause & exit /b 1)
java -cp build\large-studio-tests StudioProjectIOTest
if errorlevel 1 (pause & exit /b 1)
echo Tests grand Studio OK !
pause
