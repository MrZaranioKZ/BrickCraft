@echo off
echo BrickCraft V0.21.0 - verification guide, textures et langues
if exist src\main\resources\assets\brickcraft\textures\item\guide_book.png (echo OK guide_book texture) else (echo texture manquante & exit /b 1)
if exist src\main\resources\assets\brickcraft\lang\ja_jp.json (echo OK langues) else (echo langues manquantes & exit /b 1)
