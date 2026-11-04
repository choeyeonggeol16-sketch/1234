@echo off
cd /d "%~dp0"
i686-w64-mingw32-gcc -std=c11 -Wall -Wextra -Werror -DPSS_BRIDGE_TEST -Wl,--image-base,0x400000 -Wl,--disable-dynamicbase -I"..\..\client\source" -o BridgeHarness.exe BridgeHarness.c "..\..\client\source\PssBridge.c" -static-libgcc -lws2_32 -liphlpapi -lbcrypt -luser32
exit /b %errorlevel%
