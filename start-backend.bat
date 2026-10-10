@echo off
chcp 65001 >nul
title AI Course Platform - Backend
setlocal enabledelayedexpansion

set PROJECT_ROOT=%~dp0
set BACKEND_DIR=%PROJECT_ROOT%backend
set ENV_FILE=%PROJECT_ROOT%.env

echo ============================================================
echo  AI Course Platform - Backend Launcher  (local services only)
echo  Project: %PROJECT_ROOT%
echo ============================================================
echo.

:step1_load_env
echo [1/6] Loading environment variables...
if not exist "%ENV_FILE%" goto warn_no_env
for /f "usebackq delims=" %%a in (`type "%ENV_FILE%" ^| findstr /v "^#" ^| findstr /v "^$"`) do (
    set "%%a" 2>nul
)
goto after_load_env

:warn_no_env
echo [WARN] .env file not found: %ENV_FILE%
echo        MYSQL_ROOT_PASSWORD / JWT_SECRET are REQUIRED (no built-in weak defaults).
if defined JWT_SECRET goto after_load_env
echo.
echo [ERROR] JWT_SECRET is not defined.
echo        JWT_SECRET is REQUIRED (signing key for authentication tokens).
echo        Create .env from .env.example with a strong random value, e.g.:
echo          copy .env.example .env
echo        Then edit JWT_SECRET (openssl rand -base64 48).
pause
exit /b 1

:after_load_env
rem MYSQL_ROOT_PASSWORD is security-sensitive: no built-in fallback (weak default 1234 is guessable)
if not defined MYSQL_ROOT_PASSWORD goto err_no_mysql_pw
rem JWT_SECRET is security-sensitive: no built-in fallback (a default key allows JWT forgery). Missing -> fail-fast.
if not defined JWT_SECRET goto err_no_jwt

set "SPRING_DATASOURCE_PASSWORD=%MYSQL_ROOT_PASSWORD%"
rem Least privilege: the backend connects as aicourse_app instead of root (local MySQL has this user; password reuses MYSQL_ROOT_PASSWORD)
set "SPRING_DATASOURCE_USERNAME=aicourse_app"
set "SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/ai_course_platform?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8&useUnicode=true"
set "SPRING_DATA_REDIS_HOST=localhost"
set "SPRING_DATA_REDIS_PORT=6379"
rem Redis auth probe: the .env REDIS_PASSWORD targets Docker containers (requirepass), while local Redis often runs with no password.
rem If a plain PING on local 6379 returns PONG, drop the passthrough: sending AUTH to a password-less Redis
rem trips RedisStartupValidator fail-fast and the app never starts (observed). Keep it when 6379 does require a password.
set "REDIS_PROBE="
where redis-cli >nul 2>&1 && set "REDIS_PROBE=1"
if defined REDIS_PROBE (
    redis-cli -h 127.0.0.1 -p 6379 ping 2>nul | findstr /c:"PONG" >nul 2>&1
    if not errorlevel 1 (
        echo [INFO] Local Redis on 6379 has NO password - skipping REDIS_PASSWORD passthrough.
        set "REDIS_PASSWORD="
    )
)
rem Redis auth: pass REDIS_PASSWORD through only when .env defines it non-empty (an empty value still sends AUTH and breaks local Redis)
if defined REDIS_PASSWORD if not "%REDIS_PASSWORD%"=="" set "SPRING_DATA_REDIS_PASSWORD=%REDIS_PASSWORD%"
set "COOKIE_SECURE=false"
set "REQUIRE_HTTPS=false"
set "CSRF_ENABLED=false"
echo [OK] Environment variables loaded

:step2_check_jdk
echo [2/6] Checking JDK 21...

REM Prefer an already configured JAVA_HOME (portable across machines, no hardcoded install path)
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA_HOME_21=%JAVA_HOME%"
    goto jdk_found
)

REM Otherwise look for javac on PATH
where javac >nul 2>&1
if not errorlevel 1 (
    for /f "delims=" %%i in ('where javac') do (
        set "JAVA_HOME_21=%%~dpi.."
        goto jdk_found
    )
)

REM Last resort: common default install path (differs per machine)
set "JAVA_HOME_21=C:\Program Files\Java\jdk-21"
if not exist "%JAVA_HOME_21%\bin\java.exe" goto err_no_jdk

:jdk_found
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
echo [3/6] Checking MySQL and Redis (local services only)...

rem ---- MySQL ----
netstat -ano | findstr ":3306 " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 goto mysql_missing
echo [OK] MySQL listening on local 3306
goto redis_check

:mysql_missing
echo [ERROR] No MySQL listening on port 3306.
echo        Start the local service:  net start MySQL84
echo        If the service name differs, list it with:  sc query type=service ^| findstr /i mysql
echo        If no local MySQL is available on this machine, tell the project owner -
echo        do NOT start a container: Docker is shelved for this phase (manual sec.24.2).
pause
exit /b 1

:redis_check
netstat -ano | findstr ":6379 " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 goto redis_missing
echo [OK] Redis listening on local 6379
goto step4_check_ai_key

:redis_missing
echo [ERROR] Redis is not listening on port 6379.
echo        Redis is REQUIRED (cache / rate-limit / AI quota / verification code).
echo        The backend will fail to start (RedisStartupValidator, fail-fast).
echo        Start the local service:  net start Redis
echo        If no local Redis is available on this machine, tell the project owner -
echo        do NOT start a container: Docker is shelved for this phase (manual sec.24.2).
pause
exit /b 1

:step4_check_ai_key
rem Required keys already fail-fast in step1; a missing AI credential is a documented degradation, not a demo blocker.
echo [4/6] Checking optional AI credentials...
if defined DEEPSEEK_API_KEY goto ai_key_present
echo [INFO] DEEPSEEK_API_KEY not set - AI question answering degrades to extractive answers.
echo        This is expected without a key and does NOT block the demo (manual sec.40).
goto step5_check_engines

:ai_key_present
rem Shape check only - never print the value. A placeholder left in .env (for example an empty
rem value plus a trailing comment) is "defined" but useless, and it makes the composite /health
rem read 503 while the launcher still claims the credential is present.
if not "!DEEPSEEK_API_KEY:~0,3!"=="sk-" goto ai_key_suspect
echo [OK] DEEPSEEK_API_KEY present and shaped like a DeepSeek key (value not printed)
goto step5_check_engines

:ai_key_suspect
echo [WARN] DEEPSEEK_API_KEY is defined but does not start with sk- - treat it as NOT configured.
echo        AI answers stay extractive and /api/actuator/health stays 503 (probe stays 200).
echo        Paste a real key into .env; this script never prints the value.

:step5_check_engines
rem One-click engine bring-up. start-engines.py only ADDS what is not listening (it never restarts
rem a running engine - two instances share MySQL/Redis but split rate limits, DR-36), it starts
rem Neo4j in a console window (the zip install is not a Windows service, so "neo4j.bat start" fails),
rem and it exports what the engines cannot read themselves: NEO4J_URI, DEEPSEEK_API_KEY, HF_HUB_OFFLINE
rem (both FastAPI services have no dotenv loader - DR-39 - and without the offline flag retrieval hangs).
rem Engines are OPTIONAL: no branch below can exit this script, Java keeps its local fallback providers.
echo [5/6] Starting python engines (Neo4j 7687 / KG 8001 / RAG 8002, local only)...

set "PY_EXE="
where python >nul 2>&1 && set "PY_EXE=python"
if defined PY_EXE goto engines_launch
where py >nul 2>&1 && set "PY_EXE=py -3"

:engines_launch
if not defined PY_EXE goto engines_no_python
call %PY_EXE% "%PROJECT_ROOT%scripts\start-engines.py"
echo [INFO] Switches now re-read from the ports the launcher just verified.
goto engines_probe

:engines_no_python
echo [INFO] No python interpreter on PATH - engines were NOT started (optional, not fatal).
echo        Start them by hand in two windows if you need AI answers and the graph store:
echo          cd python-services\kg_service   ^&^& python -m uvicorn kg_service:app  --host 127.0.0.1 --port 8001
echo          cd python-services\rag_service  ^&^& python -m uvicorn rag_service:app --host 127.0.0.1 --port 8002

:engines_probe
rem Auto-set the switches for THIS process only. Anything already in .env (KG_ENABLED / RAG_ENABLED,
rem including an explicit false) always wins - the launcher honours the same rule.
netstat -ano | findstr ":8001 " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 goto kg_absent
if defined KG_ENABLED (
    echo [INFO] KG engine on 8001 - KG_ENABLED=!KG_ENABLED! ^(taken from .env^)
    goto rag_check
)
set "KG_ENABLED=true"
echo [OK]   KG engine on 8001 - KG_ENABLED auto-set to true for this run
goto rag_check
:kg_absent
echo [INFO] KG engine not on 8001 - Java uses its local graph provider.
echo        To enable: cd python-services\kg_service ^&^& uvicorn kg_service:app --host 127.0.0.1 --port 8001
echo        then rerun this script (KG_ENABLED is then auto-set, or set it in .env).

:rag_check
netstat -ano | findstr ":8002 " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 goto rag_absent
if defined RAG_ENABLED (
    echo [INFO] RAG engine on 8002 - RAG_ENABLED=!RAG_ENABLED! ^(taken from .env^)
    goto neo4j_check
)
set "RAG_ENABLED=true"
echo [OK]   RAG engine on 8002 - RAG_ENABLED auto-set to true for this run
goto neo4j_check
:rag_absent
echo [INFO] RAG engine not on 8002 - AI answers come from locally extracted passages.
echo        To enable: cd python-services\rag_service ^&^& uvicorn rag_service:app --host 127.0.0.1 --port 8002
echo        The python engines read the process environment only (no .env loader), so a real
echo        sk- DEEPSEEK_API_KEY must be set in that shell before generated answers can work.

:neo4j_check
netstat -ano | findstr ":7687 " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 goto neo4j_absent
echo [OK]   Neo4j bolt on 7687 - KG persists to the graph store
echo        Java-side sync: python scripts/sync-kg-to-python.py ^<courseId^>
goto step6_start_backend
:neo4j_absent
echo [INFO] Neo4j not on 7687 - kg_service falls back to an in-memory store ^(graph lost on restart^).

:step6_start_backend
echo [6/6] Starting Spring Boot backend...

rem ---- Verify backend directory (own English message instead of OS localized one) ----
if exist "%BACKEND_DIR%\pom.xml" goto port_check
echo [ERROR] backend directory not found: %BACKEND_DIR%
echo        Run this script from the project root; do not move it elsewhere.
pause
exit /b 1

:port_check
rem ---- Port 8080 pre-check ----
rem Default is N: two instances share MySQL/Redis but split rate-limit counters and logs (DR-36 measured) - fatal at a live demo.
netstat -ano | findstr ":8080 " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 goto port_free
echo [WARN] Port 8080 is already in use - a backend instance is most likely still running.
echo        Two instances share one MySQL/Redis but split rate limits and logs.
echo        Recommended: stop the old window (Ctrl+C) first, then rerun this script.
echo        Note: src/main/resources has no application-local.* and main sources have no
echo        @Profile bean - the 'local' profile only avoids accidentally activating 'prod'
echo        (prod excludes the demo seed directory). The test-side application-local.yml is
echo        not read at startup. Do not look for a local config file.
choice /c YN /n /m "Start a second instance anyway? [Y/N]: "
if errorlevel 2 goto abort_port

:port_free
cd /d "%BACKEND_DIR%"
echo.
echo Backend URL: http://localhost:8080/api
echo Health probe (orchestration group, not affected by a missing AI key):
echo              http://localhost:8080/api/actuator/health/probe
echo              The composite /api/actuator/health returns 503 without DEEPSEEK_API_KEY - by design.
echo Swagger UI:  http://localhost:8080/api/swagger-ui.html
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

:err_no_jwt
echo [ERROR] JWT_SECRET is not defined in environment or %ENV_FILE%.
echo        JWT_SECRET is REQUIRED (authentication token signing key).
echo        Fix: copy .env.example to .env, then set JWT_SECRET to a strong
echo        random value (e.g. openssl rand -base64 48). Never use a default key.
pause
exit /b 1

:err_no_mysql_pw
echo [ERROR] MYSQL_ROOT_PASSWORD is not defined in environment or %ENV_FILE%.
echo        MYSQL_ROOT_PASSWORD is REQUIRED (database root credential; no weak default).
echo        Fix: copy .env.example to .env, then set a strong random value.
pause
exit /b 1

:err_no_jdk
echo [ERROR] JDK 21 not found at %JAVA_HOME_21%
echo Please install JDK 21 (e.g. Adoptium Temurin 21).
pause
exit /b 1
