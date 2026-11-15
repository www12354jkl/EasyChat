@echo off
title EasyChat - Build
echo ==================================================
echo         EasyChat - Maven Build
echo ==================================================

cd /d "%~dp0"

echo [1/2] Packaging all modules (skip tests)...
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ==================================================
    echo [ERROR] Build failed! Check error logs above.
    echo ==================================================
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ==================================================
echo [SUCCESS] Build completed successfully!
echo.
echo Output Jars:
echo 1. Gateway (8080) : easychat-gateway\target\easychat-gateway-1.0-SNAPSHOT.jar
echo 2. User    (8081) : easychat-user\target\easychat-user-1.0-SNAPSHOT.jar
echo 3. Chat    (8082) : easychat-chat\target\easychat-chat-1.0-SNAPSHOT.jar
echo ==================================================
pause