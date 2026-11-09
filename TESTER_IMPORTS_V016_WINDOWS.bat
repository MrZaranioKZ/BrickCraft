@echo off
rem Archive V0.17 : tests V0.16 historiques remplacés par le test compatible capacités dynamiques.
call "%~dp0TESTER_GRANDS_SETS_V017_WINDOWS.bat" %*
exit /b %errorlevel%
