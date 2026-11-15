@echo off
title EasyChat - Stop Services
cd /d "%~dp0"

echo ==================================================
echo         EasyChat - Stop All Services
echo ==================================================
echo Stopping processes listening on ports 8080, 8081, 8082, 8083...

powershell -NoProfile -Command "$ports = @(8080, 8081, 8082, 8083); foreach ($p in $ports) { $conns = Get-NetTCPConnection -LocalPort $p -ErrorAction SilentlyContinue; if ($conns) { $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique; foreach ($procId in $pids) { try { Stop-Process -Id $procId -Force; Write-Host \"Killed process on port $p (PID: $procId)\" } catch {} } } }"

echo ==================================================
echo [OK] Done.
echo ==================================================
pause