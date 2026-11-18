@echo off
cd /d "%~dp0"
java Build.java %*
if errorlevel 1 pause
