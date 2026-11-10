# NativeStep - Mega.bin automatic load test (32-bit)

**2026-11-11 centered-anchor V3 revision:** Follow
`../APPLY_FIX_KO.md` first. A prebuilt PE32 DLL is in `../client-ready`.
Existing ground-Y calibration can be retained. With no calibration or after
resizing, turn hunting OFF, point at the player's feet and press Ctrl+Alt+F9.
V3 retains screen-compass headings and the final absolute move/down batch.
V3 keeps the V2 heading layout and defaults to a horizontally centered player
anchor. The reported 960x720/saved-X=289 setting now uses X=480 while keeping
measured ground Y. `CenterAnchorX=0` preserves manual X for a different viewport.
The new marker is `centered-anchor-v3-20261111`. These are input-coordinate
changes; live camera tracking and single-step movement still need validation.
The sections below describe earlier revisions and do not override that guide.

This package fixes a confirmed loader mismatch. The provided import scan shows
`Mega.bin` loads **dinput.dll**, NOT dinput8.dll. The former dinput8.dll could
never be expected to load automatically for that client.

## Installation

1. Build this archive's `nativestep-client/BUILD_WIN32.bat` on Windows with
   Visual Studio 2022 C++ Build Tools. CMake and an external attach EXE are NOT needed.
2. In `nativestep-client/build-win32/Release`, find **dinput.dll**.
3. In the directory containing **Mega.bin**, back up any existing `dinput.dll`
   before copying ours. Do **not** replace `C:\Windows\System32` or `SysWOW64` files.
4. Remove the old `dinput8.dll` **only if it is the one previously installed
   for this NativeStep test**. Do not delete third-party client dependencies.
5. Restart the client completely. DLL initialization occurs automatically;
   the movement engine remains **OFF until item 49027 is used**.
6. First confirm `client_io.log` next to `Mega.bin` contains `module loaded`,
   `ui hook installed`, and later `network thread started` / `paired to server`.
   The log intentionally contains no server IP address.
7. Press item **49027** once to enable auto hunting and again to disable it.
   It should generate `item START received` then `first direction received`
   after the server-DLL control channel is paired.

## How it works

The replacement `dinput.dll` exports the 7 legacy DirectInput functions with
matching calling conventions and Windows ordinals (1-7). It delegates all
functions to the genuine Windows system `dinput.dll` loaded through the full
system directory path. Normal mouse/keyboard input behavior is preserved by
forwarding instead of replacing the DirectInput APIs.

Current movement synchronization differs from the earlier hold-click test:
**one server move authorization produces one bounded left-button hold** (up to
300ms or until the server's `CANCEL`, whichever happens first), then waits for
the ordered `CANCEL` before allowing another press.
The server no longer retransmits directions at 75ms intervals. The click targets a nearby tile around the **estimated feet position**.
The previous HUD heuristic placed clicks about 27 pixels too high in the
provided 507x380 client screenshot; this version uses 42% client height.
That remains a version/layout-specific approximation, not a verified camera
coordinate for all resolutions.
This reduces client-ahead-of-server
movement and incorrect ranged attack effect positions. Server-side screen
sector calibration is applied only after a move is actually applied, and
failed click attempts never trigger a random sweep of screen sectors.

If the client is already ahead by repeated wrong-origin packets, the server
stops automatic hunting and requests an ordinary authoritative teleport
handshake back to the server position. This is a safety recovery, NOT normal
walking behavior. Physical manual clicks are sent as a tiny `U` control
command so the server stops 49027 auto-hunting, instead of guessing from
late extra C_MoveChar packets.

**Rebuild both the server Java sources and the Windows DLL for this revision.**
The earlier dinput.dll will not contain the one-click-per-step fix.

 The game **must actually load the DLL from the client
folder**; some anti-cheat/launchers may alter the search order. If the game
stops launching, restore the backed up DLL or remove this test DLL.

## Known limitations

- Actual x86 Windows build and in-game run have NOT been verified in this
  environment. The previously completed F8 input probe is not a substitute
  for this automatic loading test.
- TCP control is a separate connection on port 18841 to the existing game
  server on game port 5000; no IP in the DLL settings is required.
- This test channel still uses IP/port association, not secure authentication.
  Restrict control port 18841 at the firewall; don't deploy it publicly.
- The program is not cryptographically able to hide its network destination
  from the owner of the computer; it simply avoids exposing it in the UI/log.
- If `module loaded` appears but the UI doesn't attach, analyze the hook
  logs before modifying pathfinding or server movement code.
- If no `module loaded` appears, check DLL location, client process bitness,
  and whether the launcher loads `Mega.bin` from the expected directory.

## This revision: input-only regression recovery

This build changes only the C++ input anchor, click hold cap, and its tests.
The Java route reservation, one-step ACK gating, and heading/origin validation
are deliberately unchanged. Rebuild and replace only the client `dinput.dll`;
**do not need to rebuild the server** if the latest 2026-11-11 SRC is running.

It is verified by native C++ model tests, **not** by live Mega.bin movement.
If headings still differ, do not relax server heading checks or add arbitrary
calibration learning; the true client viewport/player ground anchor is the
missing evidence. Keep the old DLL backed up for immediate rollback.


## Physical mouse input correction (2026-11-11)

Previous versions used `PostMessage(WM_MOUSEMOVE / WM_LBUTTONDOWN / WM_LBUTTONUP)` with synthetic client-area coordinates. That does not relocate the Windows cursor or set the OS button state. A game client that reads `GetCursorPos`, `GetAsyncKeyState`, or DirectInput could interpret a different direction from the server command.

The DLL now moves the real Windows cursor to the computed destination with `SetCursorPos`, sends button press/release through `SendInput`, and never injects a press unless the game window is foreground. **Keep Mega.bin in the foreground during auto-hunt.** The OS cursor will visibly move. The physical button state generated by the DLL is excluded from manual-click detection, and a manual pointer move of more than 24px while held stops automation. Stop/CANCEL or losing focus releases the button.

The client writes `physical SendInput control initialized` and `physical click down direction=... screen=(...)` to `client_io.log`; these diagnostics prove the new DLL is actually loaded. The Windows host input test is compiled but **not run automatically**, as it deliberately clicks the OS mouse. It may be launched manually from `build-win32/Release/nativestep_win_host.exe` when the desktop is available and no other important work is in progress. The server Java route reservation and one-step ACK policy are unchanged.

**Limitation:** Exact game-client interpretation of the input coordinates still requires an in-game Windows test. No simulated test can prove the game client's C_MoveChar heading.

## 2026-11-11: World-tile to isometric-screen direction correction

The server sends world-tile directions using X={0,1,1,1,0,-1,-1,-1},
Y={-1,-1,0,1,1,1,0,-1}. The prior DLL mistakenly treated the
world-direction integer as the same-numbered screen compass sector. That
was inconsistent with the game's isometric tile grid, e.g. world heading 1
(+1,-1) is projected vertically upward, not diagonally upper-right.

`direction_point()` now projects the *one next world tile* to screen space:
`screenX = centerX + 36*(tileDX+tileDY)` and
`screenY = feetY + 23*(tileDY-tileDX)`. Existing input timing,
SendInput, window focus guard, CANCEL ordering, and server-side strict
heading/origin checking are unchanged. `client_io.log` prints the
new revision string `physical SendInput iso-step-20261111 control initialized`
when the DLL actually initializes; the server-side `step-v4` message is
not a DLL version marker.

The geometry and deterministic input-state tests passed in this workspace,
but Mega.bin's internal cursor-to-tile mapping has **not** been executed
here; true in-game correctness is not established. Keep the previous DLL
backed up. Rebuilding the server is unnecessary if the latest server is
already installed.
