@echo off
pushd "%~dp0build"
RKernCIOTransport_SelfTest.exe
set "RKERN_TEST_EXIT=%errorlevel%"
popd
exit /b %RKERN_TEST_EXIT%
