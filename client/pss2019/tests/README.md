# Imported-core validation

`vendor/roam/RoamTests.cpp` tests the exact supplied CPP with a synthetic grid host: reachable steps, target preemption, stun/death, movement gate, 2,000 wall-side ticks, no-route retry, accepted-but-frozen movement, stuck recovery, map change and invalid state. It runs on host G++ with AddressSanitizer/UndefinedBehaviorSanitizer. LeakSanitizer is disabled because the managed executor traces processes; it is not a whole-client leak test.

`navigation_test.c` tests the actual fixed-size route cache and native/logical conversion, including odd raw X rounding, bounds, directed edges, map isolation, invalidation, TTL and DWORD clock rollover. Build with PssNavigation.c and a host C11 compiler with ASan/UBSan.

`native_adapter_test.c` is a controlled PE32 fixture. Compile its C source and PssRoaming.c with the production PssNavigation.c and imported RoamCore.cpp. Only PssRoaming.c receives PSS_ROAM_TEST. Link statically with G++. Set the fixture PE SizeOfImage to 28504064 so it reserves the native address range. It supplies fake native functions with the observed x86 calling conventions. The fake actor has an odd raw X; native path inputs are asserted to be raw while goal commands use logical tiles. It covers reversed adjacent routes, malformed/partial/empty routes, dynamic blockers, query budgets, native shared ownership/list cleanup, combat/loot/fixed/path/escape priority, UI thread confinement, movement blocks, death, Stop, map change, detach and unsupported signatures.

The fixture substitutes its clock and checked guest reads because Wine/QEMU cannot use the host memory API for private guest pages. Production uses GetTickCount64/GetTickCount and ReadProcessMemory, and is compiled without PSS_ROAM_TEST. The fixture is not distributed as a game executable.

`ImportHarness.c` imports the final production DLL and checks PssClientInit and refusal to attach to an unsupported executable. The PE import table is checked for system DLLs only.

Current logs are in tests/evidence_v3_imported_core (and validation in the download). Historical evidence_v2_draft is from the replaced controller and does not validate this build. No fixture executes the protected game or actual native pathfinder; live collision/camera validation remains pending.
