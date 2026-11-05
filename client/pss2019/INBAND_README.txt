RKern 2019 PSS -- CIO GAME SESSION, NO TCP 5001 -- TEST BUILD 2026-11-06

This package combines the server FULL_SRC and one native Win32 Pss2019.dll.
2019 client 19.02.15.1002 only. It is a trial build, not live-game validated.

Server:
  Rebuild and restart using the src/ folder in this archive.
  Existing CIO/CacheD/2019 PSS starting and stopping remain untouched.
  GM command .psswindow and inventory item 900000901 now send an in-band
  game packet on the already-authenticated CIO client session.

Client:
  1) Shut down BOTH launcher and game.
  2) Copy client/Pss2019.dll, client/Pss2019.dll.sha256,
     client/Pss2019AppCenter.html, client/Pss2019ClassicHud.dat, and the
     three PSS_Client_Apply.* files into the REAL game .bin/.exe directory.
  3) Run PSS_Client_Apply.cmd once while game is closed. The tool checks
     exact client version, DLL hash, and creates the original backup.
     If game binary is already patched it says "Already applied".
  4) Start the game normally and log in as GM.
  5) GM chat: .psswindow. The same command is used by Play Supporter item.
  6) Verify that native PSS settings window opens.
     Test re-login and changing GamePort (both client and server must use
     the new game port, but NO PSS INI/WindowPort setting is necessary).

No separate PSS TCP listener, no 5001 port, no manual launcher/game PID,
no PSS registration token, and no PssBridge.ini network settings.
The existing AppCenter URL settings in Pss2019.ini remain supported.

Server proof: search for [PSS-INBAND] window command queued player=...
Client proof: %%TEMP%%\RKern_PSS2019_<game PID>.log has:
  [PSS-INBAND] native game packet 2102 decoder hook installed
  [PSS-INBAND] native 2102 marker received; settings OPEN queued
  PSS UI show requested

WARNING: If the game executable is replaced by a new launcher/update, a
previous import patch may be lost. The version-locked installer rejects
unrecognized binaries rather than patching unknown client code. In that case
first resolve launcher/client compatibility; no DLL can auto-load into an
executable that no longer imports it.

PROTOCOL: 2019 native response 2102, result 3 (not a start approval), plus
field-15 marker RKERNPSSOPEN2026. It is intercepted and consumed by the
native client hook before default ACK dispatch. All normal 2102 ACKs remain
native and are forwarded unchanged.

Test scope: 32-bit Windows cross-build, native signature checks based on
captured 19.02.15.1002 code, and Java real source packet serialization
regression. Windows live client remains to be verified.
