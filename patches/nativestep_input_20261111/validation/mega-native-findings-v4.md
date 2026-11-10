# Supplied Mega.bin input evidence

Input SHA-256: `09804d9579446fb9a630c39c1f1b2d08083718261d2f3e57b2e36f80ba865490` (2,220,568 bytes, x86 PE).

These findings come from code recovered by offline loader emulation. No live Windows client was run. The executable and recovered image are kept outside the repository and release ZIPs.

| Address | Observed operation |
|---|---|
| 0x425476 | DirectInputCreateA, version 0x500; CreateDevice uses GUID_SysKeyboard (6f1d2b61-d5a0-11cf-bfc7-444553540000). |
| 0x463801 / 0x463854 / 0x46390c | Mouse UP / DOWN / MOVE take client coordinates from the message lParam and call the active input dispatcher. DOWN has a 102ms suppression gate. |
| 0x45e2b0 | Window-to-logical point transform; modes repeat 2x, 1x, 2/3, 1/2 in a jump table. |
| 0x49f3f0 / 0x49f810 | Actual native heading table initializer and next-world-tile function. Native X is doubled relative to server X. |
| 0x49f4c0 / 0x49f590 | World-to-logical and logical-to-world conversion. The tile interior is (24,12) from its projected left vertex; original inverse verifies the target and ±2px corners. |
| 0x471a70 | Normal walk-goal selection from a logical point, subject to client state. |
| 0x4cc490 | Tries actual native neighboring candidates and selects a distance minimum. Replay pauses after those calculations, before unrelated encoded game-stat reads. |
| 0x4cc830 | Native stop-target routine: clears pending walk request and target action state. |
| 0x4cd340 | Existing client movement serialization uses the selected heading and converts native doubled X back to server coordinates. V4 does not invoke the serializer directly. |

Replay: 192 cases (8 headings × 8 mode values / 4 scale ratios × 3 camera fixtures). Idle-state and neighbor-collision boundaries are fixture stubs. Coordinate routines, table initialization, goal assignment, candidate selection and cancellation are original x86 instructions. This does not exercise full map data, Windows message delivery, actual network emission, animations or live camera tracking.

Runtime checks pin the input file hash, loaded base, critical code signatures, world input dispatcher and heading table. Each step checks both native coordinate roundtrips. No fallback to the earlier screen heuristics is enabled.
