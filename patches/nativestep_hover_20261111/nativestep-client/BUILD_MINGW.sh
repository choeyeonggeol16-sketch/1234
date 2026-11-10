#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
compiler=${CXX:-i686-w64-mingw32-g++}
mkdir -p build-win32/Release
flags=(-std=c++17 -Wall -Wextra -Werror -Wno-cast-function-type -O2 -static -static-libgcc -static-libstdc++ -I include)
"$compiler" "${flags[@]}" -shared src/step_core.cpp src/mega_client.cpp src/nativestep_dll.cpp src/client_bootstrap.cpp \
  src/dinput_proxy.cpp src/step_network.cpp dinput.mingw.def -o build-win32/Release/dinput.dll \
  -luser32 -lws2_32 -liphlpapi -ladvapi32
"$compiler" "${flags[@]}" src/step_core.cpp tests/step_core_test.cpp -o build-win32/Release/step_core_test.exe
"$compiler" "${flags[@]}" tests/win_host.cpp -o build-win32/Release/nativestep_win_host.exe -luser32
echo 'Built PE32 DLL and test executables. Windows runtime tests must be run separately.'
