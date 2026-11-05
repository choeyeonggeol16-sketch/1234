@echo off
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars32.bat" >nul
cd /d "%~dp0"
cl /nologo /std:c++14 /O2 /EHsc /MT /LD RoamCore.cpp /Fe:RoamCore32.dll >roam-build.log 2>&1
if errorlevel 1 exit /b 1
cl /nologo /std:c++14 /O2 /EHsc /MT RoamCore.cpp RoamTests.cpp /Fe:RoamTests.exe >>roam-build.log 2>&1
if errorlevel 1 exit /b 1
RoamTests.exe >roam-tests.log 2>&1
