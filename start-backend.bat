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
echo [1/4] Loading environment variables...
if not exist "%ENV_FILE%" goto err_no_env
for /f "usebackq delims=" %%a in (`type "%ENV_FILE%" ^| findstr /v "^#" ^| findstr /v "^$"`) do (
    set "%%a" 2>nul
)
if not defined MYSQL_ROOT_PASSWORD set "MYSQL_ROOT_PASSWORD=REDACTED"
if not defined JWT_SECRET set "JWT_SECRET=REDACTED"

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
echo [2/4] Checking JDK 21...
set JAVA_HOME_21=C:\Program Files\Java\jdk-21
if not exist "%JAVA_HOME_21%\bin\java.exe" goto err_no_jdk
set "JAVA_HOME=%JAVA_HOME_21%"
set "Path=%JAVA_HOME%\bin;%Path%"
"%JAVA_HOME%\bin\java" -version >%TEMP%\java_ver.txt 2>&1
findstr "21" %TEMP%\java_ver.txt >nul
if errorlevel 1 goto warn_jdk_ver
echo [OK] JDK 21 ready
del %TEMP%\java_ver.txt 2>nul
goto step3_check_services

:warn_jdk_ver
type %TEMP%\java_ver.txt
echo [WARN] JDK version check failed, expected 21. Continuing...
del %TEMP%\java_ver.txt 2>nul

:step3_check_services
echo [3/4] Checking local MySQL and Redis services...

:: MySQL on port 3306 is REQUIRED
netstat -ano | findstr ":3306 " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 goto err_no_mysql
echo [OK] MySQL is listening on port 3306

:: Redis on port 6379 is OPTIONAL (backend degrades gracefully without it)
netstat -ano | findstr ":6379 " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 goto warn_no_redis
echo [OK] Redis is listening on port 6379
goto step4_start_backend

:warn_no_redis
echo [WARN] Redis is not listening on port 6379. Backend can still run,
echo        but caching will be disabled. To start Redis run:
echo          net start redis
echo        or: docker run -d -p 6379:6379 redis:7

:step4_start_backend
echo [4/4] Starting Spring Boot backend...
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

:err_no_env
echo [ERROR] .env file not found: %ENV_FILE%
echo Please copy .env.example to .env and configure it.
pause
exit /b 1

:err_no_jdk
echo [ERROR] JDK 21 not found at %JAVA_HOME_21%
echo Please install JDK 21. NOTE: JDK 25+ is incompatible with the current Lombok version.
pause
exit /b 1

:err_no_mysql
echo [ERROR] MySQL is not listening on port 3306.
echo        This project uses a LOCAL MySQL instance, not Docker.
echo        Please start the MySQL Windows service:
echo          net start MySQL84
echo        Or install MySQL 8.0+ and import the schema:
echo          mysql -u root -p ai_course_platform ^< backend\src\main\resources\index.sql
pause
exit /b 1
