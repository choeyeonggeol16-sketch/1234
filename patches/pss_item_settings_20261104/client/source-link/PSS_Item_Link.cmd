@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0PSS_Item_Link.ps1" %*
pause
