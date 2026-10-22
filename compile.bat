@echo off
setlocal enabledelayedexpansion
title Bien Dich Du An - Remote School Library

cd /d "%~dp0"

echo ============================================================
echo   BIEN DICH MA NGUON JAVA - REMOTE SCHOOL LIBRARY
echo   Hoc phan: CMP180 - Lap trinh mang may tinh
echo ============================================================

:: 1. Tim trinh bien dich javac
set JAVAC_CMD=javac
where javac >nul 2>nul
if %errorlevel% neq 0 (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\javac.exe" (
            set JAVAC_CMD="%JAVA_HOME%\bin\javac.exe"
        )
    )
)

if "%JAVAC_CMD%"=="javac" (
    where javac >nul 2>nul
    if %errorlevel% neq 0 (
        if exist "C:\Program Files\Apache NetBeans\jdk\bin\javac.exe" (
            set JAVAC_CMD="C:\Program Files\Apache NetBeans\jdk\bin\javac.exe"
        ) else (
            for /d %%D in ("C:\Program Files\Java\jdk*") do (
                if exist "%%D\bin\javac.exe" set JAVAC_CMD="%%D\bin\javac.exe"
            )
        )
    )
)

echo Su dung trinh bien dich: %JAVAC_CMD%
echo.

:: 2. Don dep thu muc bin de dam bao clean build
if exist "bin" (
    echo Dang lam sach thu muc bin/...
    rmdir /s /q bin
)
mkdir bin

:: 3. Quet toan bo file .java trong src va cac file test goc
echo Quet toan bo cac file .java trong thu muc src...
dir /s /b src\*.java > sources.txt
if exist "ConcurrencyTest.java" (
    echo Quet file kiem thu dong thoi ConcurrencyTest.java...
    echo %cd%\ConcurrencyTest.java >> sources.txt
)

echo Dang bien dich toan bo ma nguon va bo kiem thu...
%JAVAC_CMD% -encoding UTF-8 -cp ".;lib/*" -d bin @sources.txt
set COMPILE_STATUS=%errorlevel%

if exist sources.txt del sources.txt

:: 4. Sao chep thu muc static sang bin/static neu can
if exist "src\main\resources\static" (
    if not exist "bin\static" mkdir "bin\static"
    xcopy /s /e /y /q "src\main\resources\static" "bin\static" >nul 2>nul
)

if %COMPILE_STATUS% equ 0 (
    echo ============================================================
    echo [THANH CONG] BIEN DICH HOAN TAT TOAN BO DU AN!
    echo Cac file .class da duoc luu tai thu muc: bin/
    echo ============================================================
) else (
    echo ============================================================
    echo [THAT BAI] CO LOI TRONG QUA TRINH BIEN DICH!
    echo ============================================================
)

echo.
if "%1"=="--no-pause" goto :eof
pause
