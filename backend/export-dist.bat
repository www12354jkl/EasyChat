@echo off
title EasyChat - Export Dist
cd /d "%~dp0"

echo ==================================================
echo         EasyChat - Copy Jars to dist/
echo ==================================================

if not exist "dist" mkdir "dist"

echo Copying latest jars to dist...
copy /y "easychat-gateway\target\easychat-gateway-1.0-SNAPSHOT.jar" "dist\" >nul
copy /y "easychat-user\target\easychat-user-1.0-SNAPSHOT.jar" "dist\" >nul
copy /y "easychat-chat\target\easychat-chat-1.0-SNAPSHOT.jar" "dist\" >nul

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Copy failed! Make sure you ran build.bat first.
    pause
    exit /b 1
)

echo.
echo ==================================================
echo [SUCCESS] dist folder is ready!
echo You can now send the entire 'dist' folder to any PC.
echo ==================================================
pause