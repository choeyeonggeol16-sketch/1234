#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
cc=${PSS_CC:-i686-w64-mingw32-gcc}
cxx=${PSS_CXX:-i686-w64-mingw32-g++}
mkdir -p build
for src in Pss2019 PssBridge PssRoaming PssNavigation PssActivity PssActivityArt PssNativeDraw PssAppCenter; do
  "$cc" -std=c11 -Wall -Wextra -Werror -O2 -c "$src.c" -o "build/$src.o"
done
"$cxx" -std=c++11 -Wall -Wextra -Werror -O2 -c vendor/roam/RoamCore.cpp -o build/RoamCore.o
"$cxx" -shared -static -static-libstdc++ -static-libgcc -Wl,--add-stdcall-alias -o ../Pss2019.dll build/Pss2019.o build/PssBridge.o build/PssRoaming.o build/PssNavigation.o build/PssActivity.o build/PssActivityArt.o build/PssNativeDraw.o build/PssAppCenter.o build/RoamCore.o -ldxguid -lmsimg32 -lgdi32 -luser32 -lkernel32 -lws2_32 -liphlpapi -lbcrypt
(cd .. && sha256sum Pss2019.dll > Pss2019.dll.sha256)
