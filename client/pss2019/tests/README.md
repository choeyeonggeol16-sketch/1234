# Development checks

`roam_core_test.c` runs on a host C11 compiler with `PssRoamCore.c`. The completed run used AddressSanitizer and UndefinedBehaviorSanitizer. LeakSanitizer was disabled because the managed executor uses tracing; the core contains no dynamic allocations.

`native_adapter_test.c` is a controlled PE32 fixture, not the game. Build with `-DPSS_ROAM_TEST`, `PssRoaming.c`, `PssRoamCore.c`, and the Windows user32/kernel32 libraries. Its PE SizeOfImage was increased to 28504064 before execution so it owns the native client's fixed address range. It supplies fake native entry points with the real x86 calling conventions and fake Battle objects. The `mismatch` argument verifies that an unsupported code signature is refused.

The fixture uses a simulated clock and checked guest-memory reads because Wine under QEMU cannot read the guest's private image pages through the host process-memory API. The production DLL does not define PSS_ROAM_TEST and uses GetTickCount and ReadProcessMemory. These tests verify adapter dispatch, state transitions and ownership; they do not execute the real game movement/pathfinder or establish live collision/camera behavior.

`ImportHarness.c` imports the final production Pss2019.dll and verifies the existing PssClientInit export and refusal to auto-attach to the wrong executable.

Validation logs and the exact production DLL hash are included in the release.
