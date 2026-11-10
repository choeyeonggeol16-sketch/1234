# Mega.bin native tile input V4 (x86)

Use the current Korean guide in the package root (`APPLY_FIX_KO.md`) or the repository patch README.
A prebuilt `dinput.dll` is in `client-ready`. Back up the existing client DLL and copy the new one beside Mega.bin.
Start/stop with item 49027. No cursor/feet calibration, INI changes or server rebuild is required.

This adapter is pinned to the uploaded Mega.bin SHA-256 and native code signatures. It computes the next actual world tile using the client's own routines, checks both coordinate roundtrips, and sends game window messages on the UI thread. OS mouse position/buttons are untouched. The existing DirectInput exports still forward to Windows normally.

Offline replay of actual x86 coordinate/goal routines passed 192 fixture cases. This does not establish live Windows message delivery, gameplay or camera tracking. See validation/mega-native-replay-v4.json for scope. Windows unsupported-host guard is compiled, but was not run on Linux.

Windows/MSVC: BUILD_WIN32.bat. MinGW: BUILD_MINGW.sh. The supplied executable is not redistributed.
