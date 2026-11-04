@echo off
cd /d "%~dp0"
where i686-w64-mingw32-gcc >nul 2>nul
if errorlevel 1 (
  echo Install a 32-bit MinGW-w64 compiler and put i686-w64-mingw32-gcc on PATH.
  exit /b 1
)
i686-w64-mingw32-gcc -std=c11 -Wall -Wextra -Werror -O2 -shared -static-libgcc -o "..\Pss2019.dll" Pss2019.c PssBridge.c -luser32 -lkernel32 -lws2_32 -liphlpapi -lbcrypt
exit /b %errorlevel%
