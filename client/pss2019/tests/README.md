# PSS validation

Current evidence is in evidence_v5. Earlier evidence directories record prior versions.

- Original supplied RoamTests.cpp runs against the modified core with default one-tile behavior under host ASan/UBSan. It covers bounds, wall-side movement, target/death/movement preemption, deadlines, frozen movement and map reset. LeakSanitizer is disabled for the traced executor; this is not a whole-client leak audit.
- continuous_core_test.cpp tests asynchronous 3-tile progress and L-corner routing, no repeated commands during intermediate movement, and excluding the first adjacent edge after a stalled segment.
- coverage_core_test.cpp compares V4 and V5 on the same 128x128 open synthetic map, seed and 2,000 ticks. V4 covers 16 sectors with 89 immediate command-direction reversals; V5 covers 64 sectors with zero. The rolling horizon change contributes to the improvement. These are not real hunting-ground benchmarks.
- native_adapter_test.c is a controlled PE32 fixture. Compile PssRoaming.c with PSS_ROAM_TEST and link the actual PssNavigation.c/imported RoamCore.cpp. Set the fixture PE SizeOfImage to 28504064. The fake native functions use the observed x86 calling conventions and raw coordinate layout. It covers actual vtable dispatch, odd raw X, fresh route validation, asynchronous positions, tree address reuse, target-preserving attack suppression after a server observation, deadline expiry, stop/map/world changes, and shared point/list cleanup. Production uses real clock/ReadProcessMemory and has no test macros.
- bridge_fixture exercises the actual PE32 worker with real local TCP sockets. It checks bootstrap framing, fragmented v2 blocked-shot fields and OPEN ordering, and rejection of v2 by a legacy peer followed by v1 fallback with the same token. PostMessage and endpoint enumeration are test adapters.
- Server TransportContract exercises actual NIO2 authentication, owner/IP binding, nonce expiry/replay, connection bounds, concurrent OPEN requests, v2 frames and v1 compatibility. Server test_pss2019.py covers actual dispatch/session code with boundary fixtures, direct read-only item opening without inventory consumption, native-only LOS notification eligibility/throttling and 300 session-claim races. The complete 1,711 Java sources also type-compile with Java 21 preview.
- ImportHarness imports the final production DLL and checks the init export and refusal to create helper/status windows in an unsupported executable. Only system DLL imports are allowed.

activity_indicator_test.c attempts actual Win32 show/hide/position/input-pass-through and resource lifecycle checks. It was BLOCKED before creating the fixture window because Wine could not start its explorer/graphics backend. It is NOT a passing visual test. Font/position, native PSS traversal and first predicted arrow behavior still need a Windows game trial. The indicator is a separate owned layered window for windowed mode, not an extracted NC UI resource or an in-game renderer hook.


V6 UI initialization and full-screen presentation trial

- ui_thread_contract.c includes actual Pss2019.c and calls PssAttach from a worker thread. It asserts zero activity initialization there, one initialization from the owner window WM_TIMER, and the same behavior after detach/re-attach. The activity component is a boundary double; this tests the real startup path, not graphics.
- native_draw_contract.c links actual PssNativeDraw.c with controlled DirectDraw surface COM tables. It verifies inactive bypass, filtering of primary full-frame copies, skipping small rectangles/scroll copies/offscreen copies, Flip backbuffer reference ownership, GetDC/ReleaseDC cleanup including failure, and restoration with in-flight callbacks and re-attachment. This does not verify the real client's COM tables or driver.
- ImportHarness loads the final V6 production PE32 DLL and checks refusal to attach to an unsupported executable. The DLL imports only Windows system libraries; MSIMG32 is used for AlphaBlend/TransparentBlt. DirectDrawCreateEx is resolved only from the already loaded ddraw.dll.
- Actual imported client renderer evidence: supplied pe-directories-imports.json lists ddraw.dll!DirectDrawCreateEx. PssNativeDraw uses public COM presentation slots on surface interfaces captured from a temporary system-memory offscreen surface; it does not change display mode or create a primary surface. Matching the game's actual tables and exclusive full-screen visibility still require a game trial.
- The layered window remains a windowed fallback. The V5 visual test was blocked by the Wine graphics backend; the full-screen V6 drawing is NOT live-game verified. The orange play artwork is locally drawn, not extracted NC artwork, and uses the center of the presented surface plus INI offsets, not a native character anchor.
- There are no new server-source changes in V6. Latest uploaded 1,688 Java files with the four prior V5 edits are preserved, including the uploaded daily quota/config lifecycle. See the full archive's server validation. The older 1,711-source trial tests above describe V5 history, not the latest daily quota implementation.

Build the headless tests from this directory's parent with 32-bit MinGW:

    i686-w64-mingw32-gcc -std=c11 -Wall -Wextra -Werror -O2 -static -I. tests/ui_thread_contract.c -o ../UiThreadTest.exe -luser32 -lkernel32
    i686-w64-mingw32-gcc -std=c11 -Wall -Wextra -Werror -O2 -static -I. -DPSS_NATIVE_DRAW_TEST tests/native_draw_contract.c PssNativeDraw.c -o ../NativeDrawTest.exe -ldxguid -luser32 -lkernel32

The separate windowed activity_indicator_test.c now supplies native-draw boundary doubles. Link it with PssActivity.c, -lmsimg32 -lgdi32 -luser32 -lkernel32 on a working Windows desktop. It is not part of the passing headless tests.
