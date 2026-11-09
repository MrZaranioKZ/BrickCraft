@echo off
setlocal
cd /d "%~dp0"
if not exist "build\test_v020" mkdir "build\test_v020"
where javac >nul 2>nul
if errorlevel 1 (
  echo Java 21 JDK absent. Installe Java 21 JDK avant les tests.
  pause
  exit /b 1
)
javac -encoding UTF-8 -d "build\test_v020" "src\main\java\fr\brickcraft\core\BrickPalette.java" "src\main\java\fr\brickcraft\BrickCommandPalette.java" "src\main\java\fr\brickcraft\world\BrickCellCoordinates.java" "tools\BrickCreativeCommandsV020Test.java"
if errorlevel 1 (pause & exit /b 1)
java -cp "build\test_v020" BrickCreativeCommandsV020Test
if errorlevel 1 (pause & exit /b 1)
echo.
echo Tests V0.20 reussis. Pour compiler le mod : CONSTRUIRE_WINDOWS.bat
pause
