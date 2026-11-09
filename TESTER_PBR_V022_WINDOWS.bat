@echo off
setlocal
where javac >nul 2>nul || (echo Java 21 / javac requis & exit /b 1)
if not exist build_pbr_test mkdir build_pbr_test
javac -encoding UTF-8 -d build_pbr_test src\main\java\fr\brickcraft\core\BrickPbrProfiles.java TEST_PBR_PROFILES_V022.java || exit /b 1
java -cp build_pbr_test TEST_PBR_PROFILES_V022 || exit /b 1
powershell -NoProfile -Command "$ErrorActionPreference='Stop';Add-Type -AssemblyName System.Drawing;foreach($name in @('lego_materials','lego_materials_n','lego_materials_s')){$p='src/main/resources/assets/brickcraft/textures/entity/'+$name+'.png'; $img=[System.Drawing.Image]::FromFile((Resolve-Path $p));if($img.Width -ne 160 -or $img.Height -ne 16){throw $p};$img.Dispose()};Write-Host 'OK: trois textures LabPBR (160x16)'" || exit /b 1
echo OK: tests de base passes. Compiler ensuite avec CONSTRUIRE_WINDOWS.bat
