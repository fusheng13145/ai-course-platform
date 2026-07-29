@echo off
title AI Course Platform - Backend
setlocal enabledelayedexpansion

set PROJECT_ROOT=%~dp0
set BACKEND_DIR=%PROJECT_ROOT%backend
set ENV_FILE=%PROJECT_ROOT%.env

echo ============================================================
echo  AI Course Platform - Backend Launcher
echo  Project: %PROJECT_ROOT%
echo ============================================================
echo.

:step1_load_env
echo [1/5] Loading environment variables...
if not exist "%ENV_FILE%" (
    echo [ERROR] .env file not found: %ENV_FILE%
    echo Please copy .env.example to .env and configure it.
    pause
    exit /b 1
)
for /f "usebackq delims=" %%a in (`type "%ENV_FILE%" ^| findstr /v "^#" ^| findstr /v "^$"`) do (
    set "%%a" 2>nul
)
if not defined MYSQL_ROOT_PASSWORD set "MYSQL_ROOT_PASSWORD=1234"
if not defined JWT_SECRET set "JWT_SECRET=oZQ-8qgNx7hVMFMAdvLxb4NsXhGOOp0Qva9WsOr1CLLdpov-4mddh7by2qA0vhlM"

:: NOTE: Must use quotes to prevent & in URL from being treated as command separator
set "SPRING_DATASOURCE_PASSWORD=%MYSQL_ROOT_PASSWORD%"
set "SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/ai_course_platform?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8&useUnicode=true"
set "SPRING_DATA_REDIS_HOST=localhost"
set "SPRING_DATA_REDIS_PORT=6379"
set "COOKIE_SECURE=false"
set "REQUIRE_HTTPS=false"
set "CSRF_ENABLED=false"
echo [OK] Environment variables loaded

:step2_check_jdk
echo [2/5] Checking JDK 21...
set JAVA_HOME_21=C:\Program Files\Java\jdk-21
if not exist "%JAVA_HOME_21%" (
    echo [ERROR] JDK 21 not found at %JAVA_HOME_21%
    echo Please install JDK 21.
    pause
    exit /b 1
)
set "JAVA_HOME=%JAVA_HOME_21%"
set "Path=%JAVA_HOME%\bin;%Path%"
"%JAVA_HOME%\bin\java" -version >%TEMP%\java_ver.txt 2>&1
findstr "21" %TEMP%\java_ver.txt >nul
if errorlevel 1 (
    type %TEMP%\java_ver.txt
    echo [WARN] JDK version check failed, expected 21. Continuing...
) else (
    echo [OK] JDK 21 ready
)
del %TEMP%\java_ver.txt 2>nul

:step3_start_docker
echo [3/5] Starting Docker containers...
docker info >nul 2>&1
if errorlevel 1 (
    echo [INFO] Docker Desktop not running, attempting to start...
    start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    timeout /t 30 /nobreak >nul
)
docker info >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Failed to start Docker Desktop. Please start it manually.
    pause
    exit /b 1
)
echo [OK] Docker Desktop is running

cd /d "%PROJECT_ROOT%"
docker compose down --remove-orphans >nul 2>&1

:: Check if port 3306 is already in use and try to free it
netstat -ano | findstr ":3306 " >%TEMP%\port_check.txt 2>nul
set PORT_BUSY=0
findstr "LISTENING" %TEMP%\port_check.txt >nul && set PORT_BUSY=1
del %TEMP%\port_check.txt 2>nul
if !PORT_BUSY! EQU 1 (
    echo [WARN] Port 3306 is already in use. Attempting to stop conflicting processes...
    for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":3306 " ^| findstr "LISTENING"') do (
        echo Stopping PID: %%p
        taskkill /f /pid %%p >nul 2>&1
    )
    timeout /t 3 /nobreak >nul
)

docker compose up -d mysql redis
if errorlevel 1 (
    echo [ERROR] Failed to start Docker containers.
    echo Possible causes:
    echo   1. Port 3306 is still in use - run: netstat -ano ^| findstr :3306
    echo   2. Docker Desktop engine not fully started - restart Docker Desktop
    pause
    exit /b 1
)
echo [OK] MySQL and Redis containers started

:step4_wait_mysql
echo [4/5] Waiting for MySQL to be ready (up to 90s)...
set max_attempts=30
set attempt=0
:wait_mysql_loop
set /a attempt+=1
if !attempt! gtr !max_attempts! (
    echo [ERROR] MySQL startup timeout. Check docker logs:
    docker compose logs mysql --tail 20
    pause
    exit /b 1
)
docker compose exec -T mysql mysqladmin ping --silent >nul 2>&1
if errorlevel 1 (
    echo Waiting for MySQL... (attempt !attempt!/!max_attempts!)
    timeout /t 3 /nobreak >nul
    goto wait_mysql_loop
)
echo [OK] MySQL is ready

:step5_start_backend
echo [5/5] Starting Spring Boot backend...
cd /d "%BACKEND_DIR%"
echo.
echo Backend URL: http://localhost:8080/api/actuator/health
echo Swagger UI: http://localhost:8080/api/v3/api-docs
echo.
call mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8"

echo.
echo [INFO] Backend service stopped.
pause
exit /b 0
