@echo off
chcp 65001 >nul
cd /d "%~dp0"
java -jar app/CinemaServer.jar
if errorlevel 1 pause
