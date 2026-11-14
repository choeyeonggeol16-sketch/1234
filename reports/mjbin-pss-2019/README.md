# MJBin / mjlin PSS (2019) static research archive

The user identified this as a **MJ private-server** binary. This archive does not assert that it is the official NC retail client.

## Available source analyses

- [MainServer 연동·수정 작업 기준](MAIN_SERVER_WORKSPACE_20261114.md) — 복원된 PSS 메시지 계약, 원본 바이너리 목록, MainServer 원본 부재와 업로드 상태. 
- [AutoSummonMagicDoll reverse engineering](AUTO_SUMMON_MAGIC_DOLL_REVERSE_ENGINEERING_20261113.md) — UI toggle, event 0x13/0x95, remembered item ID, item type gate 0x49, existing item-use function and unverified wire opcode.
- [Additional legacy-client DLL feature candidates](CLIENT_FEATURES_FOR_LEGACY_DLL_20261113.md) — one-click continuous attack toggle and gates, auto magic doll, auto FoeSlayer, smart casting, companion potion, equipment switching and UI.
- [Ctrl double-press auto-attack DLL porting full report](LEGACY_CLIENT_CTRL_DOUBLE_AUTOATTACK_DLL_FULL_REPORT_20261113.md) — x86 input timing, persistent mode flags, PvP target filters, target-event dispatch, legacy-client integration requirements and validation matrix.
- [Full MJBin PSS reverse-engineering report](REVERSE_ENGINEERING_20261113.md) — BotConductor, 27 action nodes, config and packet formats, address inventory.
- [Pathfinding, BotPlayPath and A*-family notes](PATHFINDING_AND_PLAYPATH_20261113.md) — disassembly-based follow-up.
- [Reconstructed PlaySupport.proto](schemas/PlaySupport.reconstructed.proto).
- [Reconstructed MainServer_Client_PlaySupport.proto](schemas/MainServer_Client_PlaySupport.reconstructed.proto).
- [Input binary inventory / SHA-256](BINARY_INVENTORY_SHA256.md).

## Raw binary upload status

The original ZIP, in-memory code snapshot and process dump have **not yet been uploaded to this GitHub repo**. They are retained as part of the research inputs. This is an analysis-only snapshot, not a claim that all execution behavior was dynamically validated.

Source artifacts used for analysis:

| Name | Bytes |
|---|---:|
| `mjlin.zip` (contains `mjlin.bin`) | 8,073,673 |
| `mjlin_loaded_code_00400000_0180D000.bin` | 21,024,768 |
| `mjlin.bin.3860.zip` (contains memory dump) | 18,510,781 |

The duplicate `mjlin(1).zip` is byte-identical to `mjlin.zip`. Memory dumps should be checked for credentials/private data before public redistribution.

All addresses are 32-bit virtual addresses from the captured image; complete class lifecycles, behavior transitions and dynamic execution remain subject to verification.
