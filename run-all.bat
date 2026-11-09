@echo off
echo Starting SwiftDrop Backend and Frontend...
start "SwiftDrop Backend" "%~dp0run-backend.bat"
timeout /t 3 /nobreak >nul
start "SwiftDrop Frontend" "%~dp0run-frontend.bat"
echo Both servers have been launched in separate windows!

