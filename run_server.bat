@echo off
setlocal enabledelayedexpansion
title RMI Server - He Thong Quan Ly Thu Vien
cd /d "%~dp0"

echo ============================================================
echo   KHOI CHAY RMI SERVER - HE THONG QUAN LY THU VIEN
echo   Cong RMI Registry: 1099 - Service: LibraryService
echo ============================================================

set JAVA_CMD=java
where java >nul 2>nul
if %errorlevel% neq 0 (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\java.exe" set JAVA_CMD="%JAVA_HOME%\bin\java.exe"
    )
)

if "%JAVA_CMD%"=="java" (
    where java >nul 2>nul
    if %errorlevel% neq 0 (
        if exist "C:\Program Files\Apache NetBeans\jdk\bin\java.exe" (
            set JAVA_CMD="C:\Program Files\Apache NetBeans\jdk\bin\java.exe"
        ) else (
            for /d %%D in ("C:\Program Files\Java\jdk*") do (
                if exist "%%D\bin\java.exe" set JAVA_CMD="%%D\bin\java.exe"
            )
        )
    )
)

%JAVA_CMD% -Dfile.encoding=UTF-8 -cp "bin;lib/*" com.library.server.ServerMain %*
pause
