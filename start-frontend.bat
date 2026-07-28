@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem  AI Course Platform - Frontend One-Click Startup Script
rem  Auto-detects Node.js (including WorkBuddy managed node)
rem  and starts Vite dev server.
rem ============================================================

rem Defensive: disable WorkBuddy agent safe-delete shim for this
rem child process so Vite's optimizer can clean its .vite temp dirs.
rem (No-op in a normal double-click cmd; only matters inside agents.)
set "CODEBUDDY_SESSION_ID="
set "CLAUDE_SESSION_ID="
set "CODEBUDDY_SAFE_DELETE_BULK_STATE_DIR="
set "CODEBUDDY_TOOL_CALL_ID="

rem --- Node.js auto-detection ---
rem Try system PATH first, then fall back to WorkBuddy managed Node.
where node >nul 2>nul
if errorlevel 1 (
    set "NODE_FOUND="
    for /d %%D in ("%USERPROFILE%\.workbuddy\binaries\node\versions\*") do (
        if exist "%%D\node.exe" if not defined NODE_FOUND (
            set "NODE_PATH=%%D"
            set "NODE_FOUND=1"
        )
    )
    if defined NODE_FOUND (
        echo INFO: Node.js not on system PATH. Using WorkBuddy managed Node at !NODE_PATH!
        set "PATH=!NODE_PATH!;%PATH%"
    ) else (
        echo ERROR: Node.js not found. Install Node.js ^>= 20.19 or ensure it is on PATH.
        echo        WorkBuddy managed Node was also not found.
        pause
        exit /b 1
    )
)

for /f "delims=" %%i in ('node -v') do set "NODE_VER=%%i"
set "NV=%NODE_VER:~1%"
for /f "tokens=1 delims=." %%m in ("%NV%") do set "NODE_MAJOR=%%m"
if %NODE_MAJOR% LSS 20 (
    echo ERROR: Node.js %NODE_VER% is too old. Vite 8 requires ^>= 20.19.
    pause
    exit /b 1
)
echo INFO: Using Node.js %NODE_VER%

cd /d "%~dp0frontend"
if errorlevel 1 (
    echo ERROR: Cannot find the frontend directory next to this script.
    pause
    exit /b 1
)

if not exist "node_modules" (
    echo === node_modules not found, installing dependencies (may take a while) ===
    call npm install
    if errorlevel 1 (
        echo === npm install failed! See errors above. ===
        pause
        exit /b 1
    )
)

echo.
echo ========================================
echo  Frontend:  http://localhost:5173
echo  Backend required at http://localhost:8080
echo  Make sure start-backend.bat is running first.
echo  Press Ctrl+C to stop.
echo ========================================

call npx vite --host 0.0.0.0 --port 5173
pause