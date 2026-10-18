@echo off
chcp 65001 >nul
title AI Course Platform - All In One
setlocal enabledelayedexpansion

set PROJECT_ROOT=%~dp0

echo ============================================================
echo  AI Course Platform - single window launcher
echo  Neo4j 7687 / KG 8001 / RAG 8002 / API 8080 / Web 5173-5175
echo  All five streams print in THIS window with a service prefix
echo  and are mirrored to logs\service-name.log (gitignored).
echo  Already-listening services are adopted, never restarted, never killed.
echo  Ctrl+C terminates only the processes this launcher started.
echo  Local MySQL 3306 and Redis 6379 must already run (manual services).
echo ============================================================
echo.

set PY_EXE=
where python >nul 2>&1 && set "PY_EXE=python"
if defined PY_EXE goto run
where py >nul 2>&1 && set "PY_EXE=py -3"

:run
if not defined PY_EXE goto no_python
call %PY_EXE% "%PROJECT_ROOT%scripts\start-all.py" %*
set EXIT_CODE=!ERRORLEVEL!
echo.
echo [INFO] Launcher exited with code %EXIT_CODE%.
echo        Stepwise launchers still work: start-backend.bat / start-frontend.bat
pause
exit /b %EXIT_CODE%

:no_python
echo [ERROR] No python interpreter on PATH - the unified launcher cannot run.
echo        Install Python 3.11 (the engines need it), or use the stepwise launchers:
echo          start-backend.bat   then   start-frontend.bat
pause
exit /b 1
