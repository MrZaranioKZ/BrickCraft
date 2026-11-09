@echo off
setlocal
cd /d "%~dp0"
if not exist build\rule-tests mkdir build\rule-tests
javac -encoding UTF-8 -d build\rule-tests tools\test_stubs\net\minecraft\util\math\BlockPos.java src\main\java\fr\brickcraft\core\MaterialFinish.java src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java src\main\java\fr\brickcraft\core\BrickKind.java src\main\java\fr\brickcraft\core\BrickPiece.java tools\BrickRulesTest.java
if errorlevel 1 (echo ECHEC compilation des tests & pause & exit /b 1)
java -cp build\rule-tests BrickRulesTest
pause
