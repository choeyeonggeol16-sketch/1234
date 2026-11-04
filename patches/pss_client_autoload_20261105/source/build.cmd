@echo off
cd /d "%~dp0"
i686-w64-mingw32-gcc -std=c11 -Wall -Wextra -Werror -O2 -shared -static-libgcc -Wl,--add-stdcall-alias -o "..\Pss2019.dll" Pss2019.c PssBridge.c -luser32 -lkernel32 -lws2_32 -liphlpapi -lbcrypt
if errorlevel 1 exit /b %errorlevel%
powershell.exe -NoProfile -Command "((Get-FileHash -Algorithm SHA256 '..\Pss2019.dll').Hash.ToLowerInvariant() + '  Pss2019.dll') | Set-Content -LiteralPath '..\Pss2019.dll.sha256' -Encoding ASCII"
exit /b %errorlevel%
