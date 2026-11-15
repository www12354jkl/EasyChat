@echo off
title EasyChat - Start Services
cd /d "%~dp0"

echo ==================================================
echo         EasyChat - Start All Services
echo ==================================================
echo Notice: Ensure Nacos(8848), MySQL(3306), Redis(6379), Kafka(9092) are running!
echo.

if not exist "easychat-user\target\easychat-user-1.0-SNAPSHOT.jar" (
    echo [Notice] Jar files not found, starting build...
    call mvn clean package -DskipTests
    if errorlevel 1 (
        echo [ERROR] Build failed.
        pause
        exit /b 1
    )
)

echo [1/3] Starting easychat-user (Port: 8081)...
start "EasyChat - User Service [8081]" java -Xms256m -Xmx512m -jar easychat-user\target\easychat-user-1.0-SNAPSHOT.jar

echo Waiting 5 seconds...
ping 127.0.0.1 -n 6 >nul

echo [2/3] Starting easychat-chat (HTTP: 8082, WS: 8083)...
start "EasyChat - Chat Service [8082/8083]" java -Xms256m -Xmx512m -jar easychat-chat\target\easychat-chat-1.0-SNAPSHOT.jar

echo Waiting 5 seconds...
ping 127.0.0.1 -n 6 >nul

echo [3/3] Starting easychat-gateway (Gateway Port: 8080)...
start "EasyChat - Gateway Service [8080]" java -Xms128m -Xmx256m -jar easychat-gateway\target\easychat-gateway-1.0-SNAPSHOT.jar

echo.
echo ==================================================
echo [SUCCESS] All services launched in separate windows!
echo Run stop-all.bat to stop them.
echo ==================================================
pause
