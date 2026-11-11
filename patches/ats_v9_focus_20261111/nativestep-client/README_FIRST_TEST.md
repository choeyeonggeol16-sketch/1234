# Mega.bin native tile input V6 (x86)

Use the current Korean guide in the package root (`APPLY_FIX_KO.md`) or the repository patch README.
A prebuilt `dinput.dll` is in `client-ready`. Back up the existing client DLL and copy the new one beside Mega.bin.
With the V5 sprite-cadence server already applied, replace only the DLL beside Mega.bin and restart the game. Start/stop with item 49027. No cursor/feet calibration or INI changes are required. Full source packages retain V5 server Java; older servers still require the V5 three-file server update.

This adapter is pinned to the uploaded Mega.bin SHA-256 and native code signatures. It computes the next actual world tile using the client's own routines, checks both coordinate roundtrips, and sends game window messages on the UI thread. OS mouse position/buttons are untouched. The existing DirectInput exports still forward to Windows normally.

V6 checks 10 points in the same authorized tile to avoid other hover targets and item labels. It confirms authorization immediately before DOWN and logs distinct stop reasons, state and millisecond ticks. Offline replay covers 1,920 candidate roundtrips and 8 reproduced native label-conflict cases avoided with another point in the same tile. Object/rendering/actuator boundaries are fixture stubs; these results do not establish live Windows gameplay or camera tracking. See validation/mega-click-replay-v6.json and the Korean guide for limits. Windows unsupported-host guard is compiled, but was not run on Linux.

Windows/MSVC: BUILD_WIN32.bat. MinGW: BUILD_MINGW.sh. The supplied executable is not redistributed.
