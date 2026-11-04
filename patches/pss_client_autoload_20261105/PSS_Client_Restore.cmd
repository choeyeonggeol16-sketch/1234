@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0PSS_Client_Apply.ps1" -Restore %*
pause
