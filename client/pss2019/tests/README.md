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


V7 presentation alignment and animation

- ActivityLayoutTest compiles the exact shared production PssActivityLayout.h. It covers an 800x600 crop from a larger 1024x768 allocation, nonzero source origin, translated windows, 2:1 stretch and its inverse mapping, negative screen coordinates and excluding separate HUD rectangles. NativeDrawTest now verifies actual Blt source/destination forwarding and legacy surface-description fallback. The layered window and source-buffer artwork use the same screen-space target.
- ActivityArtTest calls actual PssActivityArtFrame for all 48 cached frames. It verifies premultiplied ARGB channel bounds, stable text and play triangle, moving light, transparent corners and exact cyclic closure. Font rasterization uses the real Windows GDI path only at initialization and is not simulated in this test.
- UiThreadTest and ImportHarness were rerun against V7. Final build uses -Wall -Wextra -Werror and only system DLL imports. There are no new server changes.
- The supplied 14.133-second 30fps reference video shows a stationary play triangle, rotating bright ring segment and small glowing text. The helper artwork is a local reconstruction; it is not extracted NC artwork. Actual game positioning and visual quality remain subject to a Windows trial. The cached animation occupies 1,658,880 bytes and the presentation path selects a sprite row without allocating application GDI objects.
- Build ActivityLayoutTest with tests/activity_layout_contract.c -I. -luser32 -lkernel32. Build ActivityArtTest with tests/activity_art_contract.c PssActivityArt.c -I. using the same PE32 C compiler. The separate windowed fixture additionally links PssActivityArt.c.


V8 read-only HP/MP HUD trial

- vitals_contract.c compiles actual PssVitals.c with bounded read-only memory fixtures. It checks the exact supplied HP/MP condition signatures, indirect/XOR current values, word and movzx getters, changed/dead values, zero max MP, invalid world/null pointers, code mismatch, torn descriptors, pointer overflow and rejecting complex code. The actual live getter instruction bytes were not included in the supplied selected pages; production accepts only inspected simple ECX load/lea/ret shapes, otherwise hides the trial HUD and logs the unsupported bytes. No native getter or initializer is called.
- vitals_art_contract.c verifies 64-bit-safe bar fill at zero, half, full, over-max and INT_MAX boundaries, separate HP/MP colors and alpha. Actual font rasterization is not simulated.
- ui_thread_contract.c now exercises owner-thread initialization for both widgets and the shared renderer. The actual main loop activates the HUD while PSS is unpatched/not running and hides it outside world. native_draw_contract.c verifies separate activity/HUD presentation timestamps including HUD-only output; the layer-window graphics backend still requires a game trial.
- All seven PE32 test programs passed with the final production DLL import check. V7 crop geometry and rotating artwork were rerun. There are no server Java or wire-protocol changes. The original HUD remains for direct comparison. Server source hashes are unchanged from V7.
- Native evidence in evidence_v8/hp.asm.txt and mp.asm.txt comes from the provided 2019 code pages at 0x679120 and 0x679940. The database RTTI maps these to BotHitPointCondition and BotMagicPointCondition. Their call graph uses 0xEB19E0 and maximum getters 0xEB1A00/0xEB1A20; current descriptors are 0x16E2FB0 and 0x16E2FBC. The original condition sign-extends AX for maxima, which the adapter preserves. Condition signatures are checked before reading data.
- Build vitals_contract.c with -I. -luser32 -lkernel32; it includes production PssVitals.c behind its test read boundary. Build vitals_art_contract.c with PssVitalsArt.c. Main build scripts include all three new PssVitals*.c modules.


V9 bottom anchoring

Only PssVitalsHud.c placement/configuration and the version log in Pss2019.c changed. Native HP/MP reads, artwork, renderer, input and server sources are unchanged from V8. The default uses client.bottom - Bottom - widget_height (Bottom=128). Legacy Top is used only with explicit Anchor=top. Existing V8 numeric synchronization is user-reported; the supplied later video is used as a lower-HUD placement reference. V9 was type-compiled with warnings as errors and its final production DLL was imported by ImportHarness; new game positioning is not yet verified. No mirror test was added for this reversible placement-only change. V8 evidence describes the earlier revision, not a new V9 live-game test.
