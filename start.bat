@echo off
chcp 65001 >nul
title AI Course Platform - Launcher
setlocal enabledelayedexpansion

REM One launcher for the whole stack. All decisions live in scripts\start-all.py;
REM this file only finds a Python interpreter and forwards the subcommand and flags.
REM Subcommands: all (default) | backend | frontend | status.   Flags: --no-color
REM Examples:  start.bat            start.bat backend       start.bat status

set PROJECT_ROOT=%~dp0
set SUB=all
set ARGS=

if not "%~1"=="" (
    set SUB=%~1
    shift /1
)

:collect
if "%~1"=="" goto collected
set "ARGS=!ARGS! %~1"
shift
goto collect

:collected
echo ============================================================
echo  AI Course Platform - unified launcher
echo  subcommand: !SUB!
echo  ports: Neo4j 7687 / KG 8001 / RAG 8002 / API 8080 / Web 5173-5175
echo  services already listening are adopted, never restarted, never killed.
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
call %PY_EXE% "%PROJECT_ROOT%scripts\start-all.py" !SUB! !ARGS!
set EXIT_CODE=!ERRORLEVEL!
echo.
echo [INFO] Launcher exited with code !EXIT_CODE!
if not "!EXIT_CODE!"=="0" echo        Subcommand usage: start.bat [all^|backend^|frontend^|status] [--no-color]
pause
exit /b !EXIT_CODE!

:no_python
echo [ERROR] No python interpreter on PATH - this launcher is a thin wrapper and cannot run.
echo        Install Python 3.11 (the engines and the launcher both need it), then rerun.
echo        Prerequisites and manual bring-up steps: see the deployment doc, section 3.
pause
exit /b 1
