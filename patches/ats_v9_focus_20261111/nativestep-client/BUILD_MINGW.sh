#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
compiler=${CXX:-i686-w64-mingw32-g++}
mkdir -p build-win32/Release
flags=(-std=c++17 -Wall -Wextra -Werror -Wno-cast-function-type -O2 -static -static-libgcc -static-libstdc++ -I include)
"$compiler" "${flags[@]}" -shared src/ats_state.cpp src/ats_hud.cpp src/step_core.cpp src/mega_client.cpp src/nativestep_dll.cpp src/client_bootstrap.cpp \
  src/dinput_proxy.cpp src/step_network.cpp dinput.mingw.def -o build-win32/Release/dinput.dll \
  -lgdiplus -lgdi32 -luser32 -lws2_32 -liphlpapi -ladvapi32
"$compiler" "${flags[@]}" src/step_core.cpp tests/step_core_test.cpp -o build-win32/Release/step_core_test.exe
"$compiler" "${flags[@]}" tests/win_host.cpp -o build-win32/Release/nativestep_win_host.exe -luser32

"$compiler" "${flags[@]}" -mwindows src/ats_state.cpp src/ats_hud.cpp tests/ats_preview.cpp -o build-win32/Release/ATS_Preview.exe -lgdiplus -lgdi32 -luser32
"$compiler" "${flags[@]}" src/ats_state.cpp tests/ats_state_test.cpp -o build-win32/Release/ats_state_test.exe

"$compiler" "${flags[@]}" src/step_core.cpp tests/input_scope_test.cpp -o build-win32/Release/input_scope_test.exe
"$compiler" "${flags[@]}" src/step_core.cpp src/nativestep_dll.cpp tests/background_adapter_test.cpp -o build-win32/Release/background_adapter_test.exe -luser32

echo 'Built PE32 DLL, HUD preview and test executables. Windows runtime tests must be run separately.'
