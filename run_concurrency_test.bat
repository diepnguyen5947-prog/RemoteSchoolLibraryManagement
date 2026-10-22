@echo off
setlocal enabledelayedexpansion
title Concurrency Test - SQL & RMI Concurrency Control
cd /d "%~dp0"

echo ============================================================
echo   KIEM THU TRANH CHAP DONG THOI (CONCURRENCY CONTROL)
echo   Hoc phan: CMP180 - Lap trinh mang may tinh
echo ============================================================
echo.
echo Chon che do kiem thu:
echo   [1] Kiem thu truc tiep qua Java RMI (RmiConcurrencyTest - Khuyen nghi)
echo   [2] Kiem thu qua Web Gateway HTTP (ConcurrencyTest)
echo.

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

set /p CHOICE="Nhap lua chon (1 hoac 2, mac dinh 1): "
if "%CHOICE%"=="" set CHOICE=1

if "%CHOICE%"=="1" (
    echo.
    echo Khoi chay RmiConcurrencyTest voi 2 Client dong thoi...
    %JAVA_CMD% -Dfile.encoding=UTF-8 -cp "bin;lib/*" com.library.test.RmiConcurrencyTest 2 1
) else (
    echo.
    echo Khoi chay HTTP ConcurrencyTest (Yeu cau Web Server dang chay tren cong 8080)...
    %JAVA_CMD% -Dfile.encoding=UTF-8 -cp "bin;lib/*" ConcurrencyTest
)

echo.
pause
