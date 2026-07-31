@echo off
title AI Course Platform - Frontend
setlocal enabledelayedexpansion

set PROJECT_ROOT=%~dp0
set FRONTEND_DIR=%PROJECT_ROOT%frontend

echo ============================================================
echo  AI Course Platform - Frontend Launcher
echo  Project: %PROJECT_ROOT%
echo ============================================================
echo.

REM ---- Step 1: Locate Node.js ----
echo [1/4] Locating Node.js...

set NODE_EXE=
set NPM_CLI_JS=

set MANAGED_NODE_DIR=C:\Users\ASUS\.workbuddy\binaries\node\versions\22.22.2
if exist "%MANAGED_NODE_DIR%\node.exe" (
    set NODE_EXE=%MANAGED_NODE_DIR%\node.exe
    set NPM_CLI_JS=%MANAGED_NODE_DIR%\node_modules\npm\bin\npm-cli.js
    echo [OK] Using managed Node.js at %MANAGED_NODE_DIR%
) else (
    where node >nul 2>&1
    if errorlevel 1 (
        echo [ERROR] Node.js not found. Please install Node.js v20+.
        pause
        exit /b 1
    )
    for /f "delims=" %%i in ('where node') do set NODE_EXE=%%i
    echo [OK] Using system Node.js at %NODE_EXE%
)

if "%NODE_EXE%"=="" (
    echo [ERROR] node.exe not found.
    pause
    exit /b 1
)

REM Get Node.js version via absolute path (safe - no npm.cmd involved)
"%NODE_EXE%" -v >"%TEMP%\node_ver.txt" 2>&1
if errorlevel 1 (
    type "%TEMP%\node_ver.txt"
    echo [ERROR] node -v failed.
    del "%TEMP%\node_ver.txt" 2>nul
    pause
    exit /b 1
)
set /p NODE_VER=<"%TEMP%\node_ver.txt"
del "%TEMP%\node_ver.txt" 2>nul
echo [OK] Node.js %NODE_VER%

REM Locate npm-cli.js to confirm npm is available (NO execution, just file check)
if "%NPM_CLI_JS%"=="" (
    REM For system Node.js, derive npm-cli.js from node.exe location
    for %%i in ("%NODE_EXE%") do set NODE_DIR=%%~dpi
    set NPM_CLI_JS=!NODE_DIR!node_modules\npm\bin\npm-cli.js
)

if not exist "%NPM_CLI_JS%" (
    echo [ERROR] npm-cli.js not found at: %NPM_CLI_JS%
    echo         Please reinstall Node.js or run 'npm install -g npm'.
    pause
    exit /b 1
)
echo [OK] npm available at %NPM_CLI_JS%

REM ---- Step 2: Install dependencies ----
echo [2/4] Installing dependencies...
cd /d "%FRONTEND_DIR%"
if not exist "node_modules" (
    echo Running npm install, first time may take 2-5 minutes...
    REM Use node + npm-cli.js directly (avoids npm.cmd hang)
    "%NODE_EXE%" "%NPM_CLI_JS%" install
    if errorlevel 1 (
        echo [ERROR] npm install failed.
        pause
        exit /b 1
    )
    echo [OK] Dependencies installed
) else (
    echo [OK] node_modules exists, skipping install
)

REM ---- Step 3: Check backend health ----
echo [3/4] Checking backend health...
curl -s -o nul http://127.0.0.1:8080/api/actuator/health 2>nul
if errorlevel 1 (
    echo [INFO] Backend http://127.0.0.1:8080 is not ready yet.
    echo         Please ensure start-backend.bat is running.
    echo         Frontend can still start but API calls will fail.
) else (
    echo [OK] Backend is running
)

REM ---- Step 4: Start Vite ----
echo [4/4] Starting Vite dev server...
echo ============================================================
echo  Dev server:    http://localhost:5173
echo  API proxy:     http://localhost:5173/api -^> http://127.0.0.1:8080/api
echo  Press Ctrl+C to stop
echo ============================================================
echo.

REM Use node + npm-cli.js directly for dev script
"%NODE_EXE%" "%NPM_CLI_JS%" run dev

echo.
echo [INFO] Vite dev server stopped.
pause
