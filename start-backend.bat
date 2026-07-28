@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem  AI Course Platform - Backend One-Click Startup Script
rem  Uses local MySQL 8.4 + local Redis. Docker is only used
rem  as a fallback to start Redis when it is not already up.
rem  Non-interactive: removed blocking "pause" so it can be
rem  launched headless / from automation.
rem ============================================================

echo.
echo ============================================================
echo   Starting AI Course Platform Backend...
echo ============================================================

rem ============================================================
rem Phase 0 - Infrastructure
rem ============================================================
echo.
echo ========================================
echo  Phase 0: Infrastructure Check
echo ============================================================

rem --- 0.1 ensure .env (reference only, for docker-compose) ---
set "ENV_FILE=%~dp0.env"
if not exist "%ENV_FILE%" (
    echo INFO: Creating .env with local dev defaults...
    (
        echo MYSQL_ROOT_PASSWORD=1234
        echo JWT_SECRET=dev-local-jwt-secret-change-in-production-0123456789abcdef
        echo DEEPSEEK_API_KEY=
        echo MAIL_USERNAME=
        echo MAIL_PASSWORD=
    ) > "%ENV_FILE%"
    echo INFO: .env created.
)

rem --- 0.2 local MySQL ---
echo INFO: Checking local MySQL on port 3306...

set "MYSQL_PORT_OPEN="
powershell -NoProfile -Command "try{$t=New-Object System.Net.Sockets.TcpClient('localhost',3306);$t.Close();exit 0}catch{exit 1}" >nul 2>nul
if not errorlevel 1 set "MYSQL_PORT_OPEN=1"

if defined MYSQL_PORT_OPEN goto mysql_ready

rem MySQL port not open - try to start the service
echo INFO: MySQL port 3306 not open. Attempting to start MySQL service...

set "MYSQL_STARTED="
net start MySQL84 >nul 2>&1 && set "MYSQL_STARTED=1"
if not defined MYSQL_STARTED net start MySQL80 >nul 2>&1 && set "MYSQL_STARTED=1"
if not defined MYSQL_STARTED net start MySQL >nul 2>&1 && set "MYSQL_STARTED=1"

if defined MYSQL_STARTED (
    echo INFO: MySQL service start requested. Waiting for port 3306...
    set /a MYSQL_WAIT=0
    :mysql_start_wait
    timeout /t 3 /nobreak >nul
    set /a MYSQL_WAIT+=3
    powershell -NoProfile -Command "try{$t=New-Object System.Net.Sockets.TcpClient('localhost',3306);$t.Close();exit 0}catch{exit 1}" >nul 2>nul
    if not errorlevel 1 goto mysql_ready
    if !MYSQL_WAIT! lss 30 goto mysql_start_wait
)

echo ERROR: Cannot connect to local MySQL on port 3306.
echo        Make sure MySQL 8.x is installed and running.
echo        Try starting it from Windows Services or MySQL Workbench.
pause
exit /b 1

:mysql_ready
echo OK: MySQL is reachable on port 3306.

rem --- 0.3 Initialize MySQL database (local dev password 1234) ---
set "MYSQL_PWD=1234"
echo INFO: Finding mysql client...

set "MYSQL="
where mysql >nul 2>nul && set "MYSQL=mysql"
if not defined MYSQL (
    if exist "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" set "MYSQL=C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
)
if not defined MYSQL (
    if exist "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" set "MYSQL=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
)
if not defined MYSQL (
    echo WARNING: mysql.exe not found. Skipping database initialization.
    echo          Make sure ai_course_platform database exists with all tables.
    goto mysql_init_done
)

echo INFO: Using mysql client: !MYSQL!
echo INFO: Creating database ai_course_platform (if not exists)...
"!MYSQL!" -u root -p!MYSQL_PWD! -e "CREATE DATABASE IF NOT EXISTS ai_course_platform DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;" 2>nul
if errorlevel 1 (
    echo WARNING: Could not create database with password !MYSQL_PWD!.
    echo          If your MySQL root password is different, set MYSQL_PWD below and re-run.
    goto mysql_init_done
)

set "SQL_FILE=%~dp0backend\src\main\resources\index.sql"
if exist "!SQL_FILE!" (
    echo INFO: Running database initialization ^(!SQL_FILE!^)...
    "!MYSQL!" -u root -p!MYSQL_PWD! ai_course_platform < "!SQL_FILE!"
    if errorlevel 1 (
        echo WARNING: Database initialization may have partially failed.
        echo          Tables may already exist ^(this is OK for re-runs^).
    ) else (
        echo OK: Database initialized successfully.
    )
) else (
    echo WARNING: index.sql not found. Tables may be missing.
)

:mysql_init_done

rem --- 0.4 Redis (local preferred; Docker fallback only if needed) ---
echo INFO: Checking Redis on port 6379...
powershell -NoProfile -Command "try{$t=New-Object System.Net.Sockets.TcpClient('localhost',6379);$t.Close();exit 0}catch{exit 1}" >nul 2>nul
if not errorlevel 1 goto redis_ready

rem Redis not on localhost - try Docker as a fallback
echo INFO: Redis not running locally. Attempting to start via Docker...
docker ps >nul 2>nul
if errorlevel 1 (
    echo WARNING: Docker daemon not available; cannot auto-start Redis.
    echo          Please start Redis on port 6379 manually, then re-run.
    echo          Continuing - the backend may fail without Redis.
    goto redis_done
)
set "COMPOSE_FILE=%~dp0docker-compose.yml"
docker compose -f "!COMPOSE_FILE!" up -d redis
if errorlevel 1 (
    echo WARNING: Could not start Docker Redis. The backend may fail without Redis.
    echo          If you have local Redis, make sure it is running on port 6379.
    goto redis_done
)
echo INFO: Waiting for Redis to become ready...
:redis_wait
timeout /t 2 /nobreak >nul
powershell -NoProfile -Command "try{$t=New-Object System.Net.Sockets.TcpClient('localhost',6379);$t.Close();exit 0}catch{exit 1}" >nul 2>nul
if errorlevel 1 goto redis_wait

:redis_ready
echo OK: Redis is reachable on port 6379.
:redis_done

rem ============================================================
rem Phase 1 - JDK 21
rem ============================================================
echo.
echo ========================================
echo  Phase 1: JDK 21 Setup
echo ============================================================

set "JDK21=C:\Program Files\Java\jdk-21"
if not exist "%JDK21%\bin\java.exe" goto no_jdk21
set "JAVA_HOME=%JDK21%"
echo INFO: Using JAVA_HOME=%JAVA_HOME%
goto jdk_done

:no_jdk21
if not defined JAVA_HOME goto java_missing
echo WARNING: JAVA_HOME=%JAVA_HOME%
echo WARNING: Lombok 1.18.36 requires JDK 21. Build may fail.
goto jdk_done

:java_missing
echo ERROR: No JDK 21 found at %JDK21%
echo ERROR: Install JDK 21 or set JAVA_HOME to a JDK 21 directory.
pause
exit /b 1

:jdk_done
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "SPRING_PROFILES_ACTIVE=local"

rem ============================================================
rem Phase 2 - Explicit backend runtime environment
rem ============================================================
rem These match the local MySQL/Redis that are already running.
set "MYSQL_ROOT_PASSWORD=1234"
set "JWT_SECRET=dev-local-jwt-secret-change-in-production-0123456789abcdef"
set "SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/ai_course_platform?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8&useUnicode=true"

rem ============================================================
rem Phase 3 - Start Backend
rem ============================================================
echo.
echo ========================================
echo  Phase 3: Start Backend
echo ============================================================

cd /d "%~dp0backend"
if not errorlevel 1 goto backend_dir_ok
echo ERROR: Cannot find backend directory.
pause
exit /b 1

:backend_dir_ok
set "JAR="
for %%F in (target\ai-course-platform-backend-*.jar) do set "JAR=%%F"
if not defined JAR goto no_jar

echo INFO: Found pre-built jar: !JAR!
echo.
echo ========================================
echo   Backend:  http://localhost:8080
echo   Health:   http://localhost:8080/api/actuator/health
echo   Swagger:  http://localhost:8080/api/swagger-ui.html
echo   Press Ctrl+C to stop the server.
echo ========================================
java -jar "!JAR!" --server.port=8080
goto end

:no_jar
set "MVN=mvnw.cmd"
if exist "%MVN%" goto mvn_ok
where mvn >nul 2>nul
if not errorlevel 1 (
    set "MVN=mvn"
    goto mvn_ok
)
echo ERROR: No Maven found. Place mvnw.cmd in the backend directory.
pause
exit /b 1

:mvn_ok
echo INFO: No pre-built jar. Compiling with !MVN!...
call "!MVN!" compile
if not errorlevel 1 goto compile_ok
echo ERROR: Compilation failed. See errors above.
pause
exit /b 1

:compile_ok
echo.
echo ========================================
echo   Backend:  http://localhost:8080
echo   Health:   http://localhost:8080/api/actuator/health
echo   Swagger:  http://localhost:8080/api/swagger-ui.html
echo   Press Ctrl+C to stop the server.
echo ========================================
call "!MVN!" spring-boot:run -Dserver.port=8080

:end
