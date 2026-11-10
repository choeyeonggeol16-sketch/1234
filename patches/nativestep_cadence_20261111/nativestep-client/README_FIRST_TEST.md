# Mega.bin native tile input V5 (x86)

Use the current Korean guide in the package root (`APPLY_FIX_KO.md`) or the repository patch README.
A prebuilt `dinput.dll` is in `client-ready`. Back up the existing client DLL and copy the new one beside Mega.bin.
Apply the three accompanying server Java files and rebuild/restart the server, then replace the DLL beside Mega.bin. Start/stop with item 49027. No cursor/feet calibration or INI changes are required.

This adapter is pinned to the uploaded Mega.bin SHA-256 and native code signatures. It computes the next actual world tile using the client's own routines, checks both coordinate roundtrips, and sends game window messages on the UI thread. OS mouse position/buttons are untouched. The existing DirectInput exports still forward to Windows normally.

V5 waits for the original walk-goal function's three readiness conditions before sending a click. Successful server steps use the WALK handler's sprite interval; failure recovery spacing remains 600ms. Offline replay passed 192 coordinate/goal cases and 7 readiness cases. Server cadence tests cover 7 sprite intervals and strict approval/recovery. These tests do not establish live Windows movement smoothness or camera tracking. See validation/mega-native-replay-v5.json for scope. Windows unsupported-host guard is compiled, but was not run on Linux.

Windows/MSVC: BUILD_WIN32.bat. MinGW: BUILD_MINGW.sh. The supplied executable is not redistributed.
