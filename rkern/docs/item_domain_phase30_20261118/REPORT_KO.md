# RKern item Phase 22–30 verification report

## Changes
- Removed 7 pass-through classes only; no new production class, worker, or queue.
- Preserved all existing durable receipt/callback implementation files unchanged.
- Strengthened ItemEffectRegistry: same per-class ordered bonus application and tooltip aggregation, fewer per-definition objects for universal bonuses.
- Kept source level declaration and order for effect and item-delivery operations; single read type2.

## Verified
- Production 1,926 files JDK21 syntax parsed successfully (no source-wide dependency type compile).
- 30th registry typed differential: 259,200 inputs, digest b3ef450036cae4857198dd6e34defa747f245282206bc824b0e7f6b6db3dbd76, matched prior source.
- Registry allocation probe isolated: 928 B -> 456 B per universal-only Def, 70,000 iterations measured after warm-up.
- Java21 enchant typed comparison: 9,600, digest 65541946d65d53f8722e1026305c5e1d2864e34b75bb4071c620a5e8dcf2f807.
- Java21 currency/reward package typed differential: 31,488, digest 5c4e80ff5d10d6b3323d3c0689d99194aaf867341af0b9918a0bfaeede25751d.
- Phase20 rune 41,472, tower 10,112, nearby 2,310 (distance calls 158,456 -> 91,140 from phase20).
- Phase19 ice 960, movement 17,280, starter 1,024, string 1,232.
- Phase16 Ain blessing 7,168, phase11 weapon typed test.
- Broadcast phase114 failure injection and 5,000-recipient synthetic stress test PASS (not 5,000 actual connected players).
- Source delta exact-match guard: 7 modified and 7 removed with no remaining references to deleted production classes.
- Extra message differential with Korean/US/Japan/ROOT locales: 127,936 cases.

## Limits and known risks
- Full dependency resolution/game-server integration compile not performed. No real MySQL or CacheD ACK-loss/restart tests performed.
- Phases 22-26 used static source-equivalence guard for routing rather than an end-to-end game connection.
- Formatter output may differ under unusual non-ASCII digit locales; server locale tests cover Korean, US, Japan, ROOT.
- Prior historical phase21 raw-source-body equality guard is obsolete due to the phase28 string-concatenation rewrite; executable enchant test passed.
- The original starter item consumption-before-reward and other preexisting durability issues were not altered under this performance-focused task.
