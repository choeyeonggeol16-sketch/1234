# Development validation

The core tests run on a host C11 compiler with AddressSanitizer and UndefinedBehaviorSanitizer. LeakSanitizer is disabled because the managed executor traces processes. Core state uses fixed-size arrays.

`roam_navigation_test.c` supplies an independent grid BFS route oracle. It checks a wall with a remote doorway, a U-shaped dead end, straight segments that must not cut corners, lateral jitter, history across combat pauses, missing navigation and bounded unsuccessful queries. These maps validate the roaming controller; they are not the game's map implementation.

`native_adapter_test.c` is a controlled PE32 fixture. Build with PSS_ROAM_TEST, PssRoaming.c and PssRoamCore.c. Its PE SizeOfImage is set to 28504064 to reserve the native game's address range. It provides fake native functions with the x86 calling conventions observed in the captured native callers, including the eight-byte std::list header and its sixteen-byte nodes. It tests full, reversed, empty and partial paths, cleanup, combat/loot priority, fixed hunting, recorded routes, pending actions, Stop, map changes, detach and unsupported signatures. The fixture substitutes its clock and checked guest reads because Wine/QEMU cannot use the host memory API for private guest image pages. Production uses GetTickCount and ReadProcessMemory and is compiled without PSS_ROAM_TEST.

`ImportHarness.c` imports the final production DLL and checks the existing PssClientInit export and refusal to attach to an unsupported executable.

None of these tests execute the protected game binary or the actual native pathfinder. Actual client movement, map boundaries, dynamic collision and camera behavior need a live trial. V1 walking was user-confirmed; that does not establish V2 path-query behavior.
