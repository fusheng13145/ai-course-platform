@echo off
chcp 65001 >nul
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
echo [1/4] Loading environment variables...
if not exist "%ENV_FILE%" goto warn_no_env
for /f "usebackq delims=" %%a in (`type "%ENV_FILE%" ^| findstr /v "^#" ^| findstr /v "^$"`) do (
    set "%%a" 2>nul
)
goto after_load_env

:warn_no_env
echo [WARN] .env file not found: %ENV_FILE%
echo        Using built-in defaults (MYSQL_ROOT_PASSWORD=1234, built-in JWT key).
echo        The built-in JWT key is FOR LOCAL DEVELOPMENT ONLY - do NOT deploy
echo        with it; create .env from .env.example with strong random values.
echo.

:after_load_env
if not defined MYSQL_ROOT_PASSWORD set "MYSQL_ROOT_PASSWORD=1234"
if not defined JWT_SECRET set "JWT_SECRET=oZQ-8qgNx7hVMFMAdvLxb4NsXhGOOp0Qva9WsOr1CLLdpov-4mddh7by2qA0vhlM"

set "SPRING_DATASOURCE_PASSWORD=%MYSQL_ROOT_PASSWORD%"
set "SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/ai_course_platform?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8&useUnicode=true"
set "SPRING_DATA_REDIS_HOST=localhost"
set "SPRING_DATA_REDIS_PORT=6379"
rem Redis 认证：仅当 .env 显式配置非空 REDIS_PASSWORD 时透传（空值会触发 AUTH 导致本机无密码 Redis 连不上）
if defined REDIS_PASSWORD if not "%REDIS_PASSWORD%"=="" set "SPRING_DATA_REDIS_PASSWORD=%REDIS_PASSWORD%"
set "COOKIE_SECURE=false"
set "REQUIRE_HTTPS=false"
set "CSRF_ENABLED=false"
echo [OK] Environment variables loaded

:step2_check_jdk
echo [2/4] Checking JDK 21...
set JAVA_HOME_21=C:\Program Files\Java\jdk-21
if not exist "%JAVA_HOME_21%\bin\java.exe" goto err_no_jdk
set "JAVA_HOME=%JAVA_HOME_21%"
set "Path=%JAVA_HOME%\bin;%Path%"
"%JAVA_HOME%\bin\java" -version >%TEMP%\java_ver.txt 2>&1
findstr /c:"21." %TEMP%\java_ver.txt >nul
if errorlevel 1 goto warn_jdk_ver
echo [OK] JDK 21 ready
del %TEMP%\java_ver.txt 2>nul
goto step3_check_services

:warn_jdk_ver
type %TEMP%\java_ver.txt
echo [WARN] JDK version check failed, expected 21. Continuing...
del %TEMP%\java_ver.txt 2>nul

:step3_check_services
echo [3/4] Checking MySQL and Redis (local first, docker as fallback)...

rem ---- MySQL: local service (3306) first, docker container (3307) as fallback ----
netstat -ano | findstr ":3306 " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 goto try_docker_mysql
echo [OK] MySQL: local service on port 3306
goto check_redis

:try_docker_mysql
netstat -ano | findstr ":3307 " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 goto err_no_mysql
echo [OK] MySQL: docker container on port 3307 (local MySQL not running, using docker fallback)
set "SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/ai_course_platform?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8&useUnicode=true"
goto check_redis

:err_no_mysql
echo [ERROR] No MySQL listening on port 3306 (local) or 3307 (docker).
echo        Preferred: start local MySQL service:
echo          net start MySQL84
echo        Fallback:  start docker MySQL + Redis (infrastructure only):
echo          docker compose up -d
pause
exit /b 1

:check_redis
netstat -ano | findstr ":6379 " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 goto err_no_redis
echo [OK] Redis: local service on port 6379
goto step4_start_backend

:err_no_redis
echo [ERROR] Redis is not listening on port 6379.
echo        Redis is REQUIRED (cache / rate-limit / AI quota / verification code).
echo        The backend will fail to start (RedisStartupValidator, fail-fast).
echo        Preferred: start local Redis service:
echo          net start Redis
echo        Fallback:  start docker Redis:
echo          docker compose up -d redis
pause
exit /b 1

:step4_start_backend
echo [4/4] Starting Spring Boot backend...

rem ---- Verify backend directory (own English message instead of OS localized one) ----
if exist "%BACKEND_DIR%\pom.xml" goto port_check
echo [ERROR] backend directory not found: %BACKEND_DIR%
echo        Run this script from the project root; do not move it elsewhere.
pause
exit /b 1

:port_check
rem ---- Port 8080 pre-check: warn if an old backend instance may be running ----
netstat -ano | findstr ":8080 " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 goto port_free
echo [WARN] Port 8080 is already in use (an old backend instance may still be running).
echo        Starting another instance will fail with "Port 8080 was already in use".
echo        Close the old window or end the java process first if you want to restart.
choice /c YN /n /m "Continue anyway? [Y/N]: "
if errorlevel 2 goto abort_port

:port_free
cd /d "%BACKEND_DIR%"
echo.
echo Backend URL: http://localhost:8080/api/actuator/health
echo Swagger UI: http://localhost:8080/api/swagger-ui.html
echo.
call mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8"

echo.
echo [INFO] Backend service stopped.
pause
exit /b 0

:abort_port
echo [INFO] Aborted. Stop the old backend instance first (Ctrl+C in its window or task manager), then rerun.
pause
exit /b 1

:err_no_jdk
echo [ERROR] JDK 21 not found at %JAVA_HOME_21%
echo Please install JDK 21 (e.g. Adoptium Temurin 21).
pause
exit /b 1
