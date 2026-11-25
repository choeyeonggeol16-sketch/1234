# RKern — NC creature parameter audit, phase 1 (2026-11-25)

## Baseline and evidence
RKern base: 43b4eae3c0b8633ba1e13af4b594f07f89a47dd9.
Reference: Open_LU4_L2OFF restoration, not an official NC source release.
Phase 1 is read-only production analysis. No combat formula, field, public API, owner/thread or persistence changes.

## OFF execution contract
- CreatureCommon.cpp: CCreature constructor obtains shared record through CCreatureFactory::Inst().Add(this); initial CStats and resistance data.
- Shared/src/SharedFactory.h: CStats m_OriginalStat, m_Stat; int32 m_nBaseParam[16], m_nOrgParam[16]; double m_dParam[16]. Creature.h: embedded CCreatureSkillMod with Mod { m_Per, m_Diff } and 74 modifier slots.
- UserWorld.cpp:3485 User::EnterWorld; EnterWorldImpl, ValidateHenna, set-item checks, ValidateChanged(VB_MAX), pledge skills then another VB_MAX mark. Exact PC consumer of login marks not yet located. UserWorld.cpp:3184 requests item loading.
- CreatureServer.cpp:7873 ValidateChanged ORs flags; 8318 ValidateParameters locks creature and dispatches base, attribute, level, weapon/armor, item skills, skill list, skill mod, HP/MP/CP, SIM, speed and level-up validation.
- 8190 ValidateSkillMod collects passive/toggle/aura/infected/option effects, calls InitializeSkillMod (3144), then pumps valid effects. Percent resets to 1, diff to 0.
- 9445 ValidateAllOnSIM invokes weight/storage, original attacks, defense, speeds, critical, hit/avoid and range/extra parameters. 8921 ValidateOrgPAttack uses equipped weapon+armor, falls back to base if zero; 9120 ValidatePAttack applies L2 level/stat/modifier formula and publishes m_dParam. 8991 ValidateOrgPHit similarly builds equipment total; 8835 ValidatePHit computes DEX/level/modifiers. 13831 EquipedWeaponValidate reads item template value and enchant add.
- UserItem.cpp:2074 calls ValidateParameters with weapon/armor, item skill, skill list/mod, vitals, SIM and speed flags on successful equip, then sends status.
- NewAction.cpp GetHitRatio / CheckIsHit take physical hit/avoid parameters; position, height, target and random rolls remain combat-time concerns.
- Preserve L1 formulas, not L2 numerical balance. OFF ValidateSkillMod uses a temporary vector; do not import allocation pattern unnecessarily.

## RKern source map (verified on base)
| Source | Current role | NC correspondence / next action |
| --- | --- | --- |
| model/L1Character.java:47,163,1048,1125,1367 | Actual common PC/NPC base; owns Ability, Resistance, AC, vitals, packed skill projections and hit/damage/critical fields | CCreature; central owner validation boundary |
| model/Ability.java:20-45,96 | base, permanent, added and packed skill STR/DEX/CON/INT/WIS/CHA; SP | CStats; retain existing byte/clamp semantics until equivalence proven |
| model/Resistance.java:17-41,124-144 | base/additive/skill MR, elements, special resistance/pierce, PvP and potion stats | common resistance sources; not same as legacy L1Character MR |
| model/AC.java | current AC, non-skill AC, skill AC and derived DG/ER | common parameter validation |
| model/Instance/L1PcInstance.java:727,5444 | Two separate PcBaseAbilityCache instances for same class/level formulas. getBaseAbility refreshes, legacy getters read other cache. setType invalidates only first, resetBaseAll refreshes second | First concrete consolidation candidate; retain getters and login/reset semantics; validate class change and all callers |
| model/character/Utill/PcBaseAbilityCache.java | class/level tables; cached BaseAbility allocated on refresh; PC-only | retain formulas, unify lifetime/publication before broader move |
| model/Instance/L1NpcInstance.java:406-421 | template-level initialization, AC, hit/damage bonus via inherited fields | NPC uses shared base, but its stat input differs from PC; no PC growth rules on NPC |
| model/character/skill/SkillEffectContainer.java:55,87,203 | lazy SkillModifiers; reset and replay migrated effects; publishes packed values into common and PC-only fields | existing CCreatureSkillMod-like engine; do not duplicate or eagerly allocate for every NPC |
| model/item/ItemStat.java | equipment option dispatch, PC typed, mixes common and PC-only stats | split responsibility at common boundary, keep tooltip IDs; do not blindly widen all enum entries |
| model/L1EquipmentSlot.java, EquipmentEnchantOptions.java, item/ItemEffectRegistry.java | incremental equipment apply/remove and enchant delta; registry effects | existing equipment sources; separate pure stat contribution from timers, item skill, visual, packet, exp, shape effects before any rebuild |
| model/character/action/CAttackAction.java:1042,1492,1685 | STR/DEX formula, weapon fields, level/class and additive stats plus per-target/random terms | move only invariant derived contributions after tracing hit/magic/UI consumers |
| utils/CalcStat.java | L1 numerical formulas | keep unchanged |

## Findings requiring verification / correction
1. Confirmed duplicate per-PC base caches and inconsistent invalidation/read paths. Merge to one existing cache in phase 2, check level/class/reset/load callers and publication under concurrent refresh. Do not assume the old stale values should be preserved.
2. L1Character has legacy _mr/_trueMr and _sp paths independent of Resistance and Ability SP/MR. Audit actual PC/NPC/template/call sites before choosing a canonical owner; no blanket merge because NPC and magic/combat semantics can differ.
3. L1PcInstance.resetBaseMr computes newMr from class/WIS/level but only sends SC_SPMR; no storage write in that method. SC_SPMR sends effective minus base MR. Trace login base MR initialization and all magic/packet consumers before deciding exact repair; do not double apply client-side derived stats.
4. SkillModifiers already resets and re-pumps; several PC-only projections live separately. Preserve lazy allocation, skill owner execution, source identity, signed lane and clamp behavior. Only migrated skill effects use that engine; audit legacy effects before a full rebuild.
5. Equipment callbacks can issue packets, transform, invoke timers and apply persistent side effects. A new ValidateParameters must not repeatedly invoke full equip/unequip methods. Audit effect purity and extract only stat contributions; maintain receipt replay and current ownership gate.
6. Common getter/setter names do not guarantee same semantics (base, raw, effective, client-visible). Avoid a second parallel stats cache or per-creature arrays merely to imitate OFF layout.
7. Full tree reference enumeration, production compile and in-game parity are not established in this phase. Prior inventory CI results remain unverified.

## Ordered implementation
- Phase 2: consolidate duplicate PcBaseAbilityCache; verify all eleven fields, class/level changes and invalidation; preserve existing API. No new per-player object.
- Phase 3: map/resolve overlapping SP/MR/AC/primary stat stores and PC/NPC consumers; establish common parameter owner and exact source boundaries.
- Phase 4: consolidate existing skill modifiers and equipment contributions under a common ValidateParameters-style bitmask; keep existing CIO execution and separate notifications/one-time effects.
- Phase 5: use cached invariant hit/damage/critical components in common combat paths; preserve target-size, weapon selection, attribute, random, shield, reduction and PvP/PvE semantics.
- Phase 6: Java 21 compile, deterministic before/after numerical tests for PC/NPC, login/relogin, level and class change, equip/unequip/enchant, buff apply/reapply/expiry/dispel, death/revive; stress ownership and allocation. No claim of performance until measured.
