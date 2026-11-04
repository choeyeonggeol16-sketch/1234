@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0PSS_Open_Now.ps1" %*
pause
