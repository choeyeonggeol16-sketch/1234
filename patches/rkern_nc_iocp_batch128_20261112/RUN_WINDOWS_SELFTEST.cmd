@echo off
pushd "%~dp0build"
CIONcTransport_SelfTest.exe
set "NC_TEST_EXIT=%errorlevel%"
popd
exit /b %NC_TEST_EXIT%
