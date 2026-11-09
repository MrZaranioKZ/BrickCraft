@echo off
setlocal
cd /d "%~dp0"
if not exist "tools\_geometry_test" mkdir "tools\_geometry_test"
javac --release 21 -encoding UTF-8 -d "tools\_geometry_test" ^
 "src\main\java\fr\brickcraft\core\MaterialFinish.java" ^
 "src\main\java\fr\brickcraft\ldraw\LDrawLibrary.java" ^
 "src\main\java\fr\brickcraft\importer\LDrawProjectReader.java" ^
 "src\main\java\fr\brickcraft\importer\PreciseReferenceMapper.java" ^
 "src\main\java\fr\brickcraft\importer\PreciseProjectIO.java" ^
 "src\main\java\fr\brickcraft\importer\PreciseWorldModel.java" ^
 "tools\StudioGeometryTest.java"
if errorlevel 1 (echo ECHEC COMPILATION DU TEST&pause&exit /b 1)
java -Xmx2G -cp "tools\_geometry_test" StudioGeometryTest "run\config" "EXEMPLES\Neuschwanstein_21063.bcpx"
if errorlevel 1 (echo ECHEC TEST GEOMETRIES&pause&exit /b 1)
echo SUCCESS - Test des 9 pieces Studio et des 3245 matrices.
pause
