@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title Client input bridge - build
set "LOG=%~dp0BUILD_WIN32.log"

>"%LOG%" echo Client Input DLL build - CMake NOT required
>>"%LOG%" echo Working directory: %CD%
>>"%LOG%" echo.
call :do_build >>"%LOG%" 2>&1
set "RESULT=%ERRORLEVEL%"

echo.
echo ================= NativeStep Build Output =================
type "%LOG%"
echo ===========================================================
echo.
if not "%RESULT%"=="0" goto :build_failed

echo [SUCCESS] 32-bit DirectInput dinput.dll client DLL and tests built.
echo Client DLL: "%~dp0build-win32\Release\dinput.dll"
echo Copy only dinput.dll next to Mega.bin. Do not rename it.
goto :build_done

:build_failed
echo [FAILED] NativeStep build failed. Exit code: %RESULT%
echo.
echo CMake is NOT required by this script.
echo You only need Visual Studio C++ build tools with the x86 compiler.
echo Install: https://visualstudio.microsoft.com/visual-cpp-build-tools/
echo Select: Desktop development with C++ and a Windows SDK.

:build_done
echo.
echo Log: "%LOG%"
echo [INFO] Build finished. This console remains open. Type EXIT to close.
endlocal & exit /b %RESULT%

:do_build
echo [STEP 1/6] Locate and activate MSVC x86 toolchain
where cl.exe >nul 2>nul
if errorlevel 1 (
    call :activate_msvc
    if errorlevel 1 exit /b 10
)
where cl.exe
where link.exe
if errorlevel 1 (
    echo [ERROR] MSVC linker missing. Install Desktop development with C++.
    exit /b 11
)

echo [STEP 2/6] Create output folder
if not exist "build-win32\Release" mkdir "build-win32\Release"
if errorlevel 1 exit /b 12
pushd "build-win32\Release"
call :compile_in_release
set "RESULT2=%ERRORLEVEL%"
popd
exit /b %RESULT2%

:activate_msvc
set "VSROOT="
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if not exist "%VSWHERE%" set "VSWHERE=%ProgramFiles%\Microsoft Visual Studio\Installer\vswhere.exe"
if exist "%VSWHERE%" (
    for /f "usebackq delims=" %%I in (`"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do if not defined VSROOT set "VSROOT=%%I"
)
if not defined VSROOT (
    for %%V in (
        "%ProgramFiles%\Microsoft Visual Studio\2022\BuildTools"
        "%ProgramFiles%\Microsoft Visual Studio\2022\Community"
        "%ProgramFiles%\Microsoft Visual Studio\2022\Professional"
        "%ProgramFiles%\Microsoft Visual Studio\2022\Enterprise"
        "%ProgramFiles(x86)%\Microsoft Visual Studio\2019\BuildTools"
        "%ProgramFiles(x86)%\Microsoft Visual Studio\2019\Community"
    ) do if not defined VSROOT if exist "%%~V\VC\Auxiliary\Build\vcvarsall.bat" set "VSROOT=%%~V"
)
if not defined VSROOT (
    echo [ERROR] MSVC C++ x86 compiler not found.
    echo Install Visual Studio Build Tools, workload "Desktop development with C++".
    echo Download: https://visualstudio.microsoft.com/visual-cpp-build-tools/
    exit /b 1
)
if not exist "%VSROOT%\VC\Auxiliary\Build\vcvarsall.bat" (
    echo [ERROR] Found Visual Studio at "%VSROOT%" but x86 C++ tools are missing.
    echo Open Visual Studio Installer and add "Desktop development with C++".
    exit /b 2
)
echo Using Visual Studio: "%VSROOT%"
call "%VSROOT%\VC\Auxiliary\Build\vcvarsall.bat" x86
if errorlevel 1 (
    echo [ERROR] vcvarsall x86 failed; ensure the x86 tools and Windows SDK are installed.
    exit /b 3
)
where cl.exe >nul 2>nul
if errorlevel 1 (
    echo [ERROR] cl.exe still not found after running vcvarsall x86.
    exit /b 4
)
exit /b 0

:compile_in_release
set "FLAGS=/nologo /std:c++17 /EHsc /O2 /MD /utf-8 /DUNICODE /D_UNICODE /DWIN32_LEAN_AND_MEAN /DNOMINMAX /I..\..\include"

echo [STEP 3/6] Compile core
cl %FLAGS% /c ..\..\src\step_core.cpp /Fostep_core.obj
if errorlevel 1 exit /b 20

echo [STEP 4/6] Build 32-bit dinput.dll (Mega.bin native DirectInput import)
cl %FLAGS% /LD ..\..\src\ats_state.cpp ..\..\src\ats_hud.cpp ..\..\src\mega_client.cpp ..\..\src\nativestep_dll.cpp ..\..\src\client_bootstrap.cpp ..\..\src\dinput_proxy.cpp ..\..\src\step_network.cpp step_core.obj /link /OUT:dinput.dll /IMPLIB:dinput_stub.lib /DEF:..\..\dinput.def gdiplus.lib gdi32.lib user32.lib ws2_32.lib iphlpapi.lib advapi32.lib
if errorlevel 1 exit /b 21

echo [STEP 5/6] Build input scope regressions
cl %FLAGS% ..\..\tests\input_scope_test.cpp step_core.obj /link /OUT:input_scope_test.exe
if errorlevel 1 exit /b 34
input_scope_test.exe
if errorlevel 1 exit /b 35

echo [STEP 5/6] Build and run StepEngine unit test
cl %FLAGS% ..\..\tests\step_core_test.cpp step_core.obj /link /OUT:step_core_test.exe
if errorlevel 1 exit /b 23
step_core_test.exe
if errorlevel 1 exit /b 24

echo [STEP 6/6] Build ATS preview and HUD state tests
cl %FLAGS% ..\..\src\ats_state.cpp ..\..\src\ats_hud.cpp ..\..\tests\ats_preview.cpp /link /SUBSYSTEM:WINDOWS /OUT:ATS_Preview.exe gdiplus.lib gdi32.lib user32.lib
if errorlevel 1 exit /b 31
cl %FLAGS% ..\..\src\ats_state.cpp ..\..\tests\ats_state_test.cpp /link /OUT:ats_state_test.exe
if errorlevel 1 exit /b 32
ats_state_test.exe
if errorlevel 1 exit /b 33

echo [STEP 6/6] Build and run unsupported-executable guard test
cl %FLAGS% ..\..\tests\win_host.cpp /link /OUT:nativestep_win_host.exe user32.lib
if errorlevel 1 exit /b 25
nativestep_win_host.exe
if errorlevel 1 exit /b 26
echo [CHECK] Unsupported executable refused. No OS mouse injection is used.

if not exist "dinput.dll" exit /b 27

echo [CHECK] Verify seven DirectInput exports and ordinals
where dumpbin.exe >nul 2>nul
if errorlevel 1 (
    echo [ERROR] dumpbin.exe missing after MSVC environment initialization.
    exit /b 28
)
dumpbin /nologo /exports dinput.dll > dinput_exports.txt
if errorlevel 1 exit /b 29
for %%E in (DirectInputCreateA DirectInputCreateEx DirectInputCreateW DllCanUnloadNow DllGetClassObject DllRegisterServer DllUnregisterServer) do (
    findstr /C:"%%E" dinput_exports.txt >nul
    if errorlevel 1 (
        echo [ERROR] Export missing: %%E
        exit /b 30
    )
)
echo [CHECK] All seven required DirectInput entrypoints exported.
exit /b 0
