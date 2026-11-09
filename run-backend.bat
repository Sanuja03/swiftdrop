@echo off
title SwiftDrop Backend (Spring Boot)
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "SUPABASE_DB_PASSWORD=BEgu3krwzeC9cpnK"

:: Load backend .env if present
if exist "%~dp0backend\.env" (
    for /f "usebackq tokens=1* delims==" %%A in ("%~dp0backend\.env") do (
        set "%%A=%%B"
    )
)
if exist "%~dp0.env" (
    for /f "usebackq tokens=1* delims==" %%A in ("%~dp0.env") do (
        set "%%A=%%B"
    )
)
cd /d "%~dp0backend"
echo ========================================================
echo  Starting SwiftDrop Spring Boot Backend (Port 8080)
echo ========================================================
mvn spring-boot:run
pause

