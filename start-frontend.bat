@echo off
chcp 65001 >nul
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

REM Prefer node from the system PATH - works for teammates and CI, no dependency on a specific user directory
where node >nul 2>&1
if not errorlevel 1 (
    for /f "delims=" %%i in ('where node') do set NODE_EXE=%%i
    echo [OK] Using system Node.js at !NODE_EXE!
) else (
    REM Fallback: WorkBuddy-managed Node; the path varies with the user profile, so derive it from USERPROFILE, never hardcode a username
    set "MANAGED_NODE_DIR=%USERPROFILE%\.workbuddy\binaries\node\versions\22.22.2"
    if exist "!MANAGED_NODE_DIR!\node.exe" (
        set NODE_EXE=!MANAGED_NODE_DIR!\node.exe
        echo [OK] Using WorkBuddy managed Node.js at !MANAGED_NODE_DIR!
    ) else (
        echo [ERROR] Node.js not found. Please install Node.js v20+ or use the WorkBuddy managed runtime.
        pause
        exit /b 1
    )
)

if "!NODE_EXE!"=="" (
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
    REM Lockfile timestamp is a hint only: after editing the lockfile an incremental install may not pick it up, and this script never touches the dependency tree
    for %%f in ("package-lock.json") do set "LOCK_MTIME=%%~tf"
    echo [INFO] package-lock.json last change: !LOCK_MTIME!
    echo        If you just pulled or edited the lockfile, run: npm ci
)

REM ---- Step 3: Check backend health ----
REM Probe the orchestration group /probe - composite /health always answers 503 without DEEPSEEK_API_KEY, which is expected rather than a fault (manual sec.25 / DR-28).
REM Judging liveness from that 503 would report a healthy backend as down.
echo [3/4] Checking backend health...
set HEALTH_CODE=000
for /f "usebackq delims=" %%c in (`curl -s -o nul -w "%%{http_code}" --max-time 3 "http://127.0.0.1:8080/api/actuator/health/probe"`) do set "HEALTH_CODE=%%c"
if not "%HEALTH_CODE%"=="000" goto backend_ready
echo [INFO] Backend http://127.0.0.1:8080 is not answering.
echo        Please start start-backend.bat first. The frontend will still launch,
echo        but every API call will fail until the backend is up.
goto step4_pick_port

:backend_ready
echo [OK] Backend is reachable (health/probe HTTP %HEALTH_CODE%)
REM Explain the composite endpoint separately, so a live demo does not read the expected 503 as a failure
for /f "usebackq delims=" %%c in (`curl -s -o nul -w "%%{http_code}" --max-time 3 "http://127.0.0.1:8080/api/actuator/health"`) do set "COMPOSITE_CODE=%%c"
if defined COMPOSITE_CODE if not "!COMPOSITE_CODE!"=="200" echo [INFO] Composite /health returned !COMPOSITE_CODE! - expected when DEEPSEEK_API_KEY is missing; AI answers degrade to extractive mode.

:step4_pick_port
REM ---- Step 4: Pick a free dev port, then start Vite ----
REM vite.config.ts sets strictPort:true - when another app holds 5173 Vite exits instead of drifting to a free port.
REM The backend CORS whitelist in application.yaml opens only 5173/5174/5175, so on 5176 or above the proxied
REM login is rejected with 403. The candidates stay fixed to those three and the first free one wins.
echo [4/4] Picking a free dev port (5173 / 5174 / 5175)...
set DEV_PORT=
for %%p in (5173 5174 5175) do (
    if not defined DEV_PORT (
        netstat -ano | findstr ":%%p " | findstr "LISTENING" >nul 2>&1
        if errorlevel 1 set "DEV_PORT=%%p"
    )
)
if defined DEV_PORT goto port_found
echo [ERROR] All of 5173 / 5174 / 5175 are in use (netstat LISTENING).
echo        Close the other dev servers, or use one of these three ports for the backend too.
echo        Do NOT pick 5176+ - the backend CORS whitelist would reject the login with 403.
pause
exit /b 1

:port_found
if not "!DEV_PORT!"=="5173" echo [INFO] 5173 is occupied by another app, using !DEV_PORT! instead.
echo ============================================================
echo  Dev server:    http://localhost:!DEV_PORT!
echo  API proxy:     http://localhost:!DEV_PORT!/api -^> http://127.0.0.1:8080/api
echo  Press Ctrl+C to stop
echo ============================================================
echo.

REM Use node + npm-cli.js directly for dev script; --port keeps vite on the port we just probed
"%NODE_EXE%" "%NPM_CLI_JS%" run dev -- --port !DEV_PORT!

echo.
echo [INFO] Vite dev server stopped.
pause
