@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title Game Client Input - Win32 build

echo ===========================================================
echo Game client Win32 DirectInput build

echo This console stays OPEN even if the compiler fails.
echo CMake is NOT required. Visual Studio C++ x86 tools are used.
echo ===========================================================
echo.
if not exist "BUILD_WIN32_TASK.cmd" (
    echo [ERROR] BUILD_WIN32_TASK.cmd is missing.
    echo Extract the WHOLE ZIP to a normal folder before running.
    echo.
)
rem cmd /K is deliberately OUTSIDE the compiler script.
rem Even a fatal parsing error in the worker cannot close this console.
"%ComSpec%" /D /K "call BUILD_WIN32_TASK.cmd"
