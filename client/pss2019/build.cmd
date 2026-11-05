@echo off
setlocal
cd /d "%~dp0"
if not exist "build" mkdir "build"
for %%S in (Pss2019 PssBridge PssRoaming PssNavigation PssActivity) do (
  i686-w64-mingw32-gcc -std=c11 -Wall -Wextra -Werror -O2 -c "%%S.c" -o "build\%%S.o"
  if errorlevel 1 goto fail
)
i686-w64-mingw32-g++ -std=c++11 -Wall -Wextra -Werror -O2 -c "vendor\roam\RoamCore.cpp" -o "build\RoamCore.o"
if errorlevel 1 goto fail
i686-w64-mingw32-g++ -shared -static -static-libstdc++ -static-libgcc -Wl,--add-stdcall-alias -o "..\Pss2019.dll" build\Pss2019.o build\PssBridge.o build\PssRoaming.o build\PssNavigation.o build\PssActivity.o build\RoamCore.o -lgdi32 -luser32 -lkernel32 -lws2_32 -liphlpapi -lbcrypt
if errorlevel 1 goto fail
powershell.exe -NoProfile -Command "((Get-FileHash -Algorithm SHA256 '..\Pss2019.dll').Hash.ToLowerInvariant() + '  Pss2019.dll') | Set-Content -LiteralPath '..\Pss2019.dll.sha256' -Encoding ASCII"
exit /b %errorlevel%
:fail
exit /b 1
