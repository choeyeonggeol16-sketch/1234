# RKern 캐릭터 스탯 전면 재검증 보고서 — 2026-11-27

## 1. 기준 및 범위
- 기준 커밋: [8ab6e36a8d8c9082539c73e2fc23e4953227b1c4](https://github.com/choeyeonggeol16-sketch/1234/commit/8ab6e36a8d8c9082539c73e2fc23e4953227b1c4), 누적 서버 브랜치.
- 선행 자료: [PHASE1](PHASE1.md), [CalcStat 정리](CALCSTAT_CLEANUP.md). 선행 보고서의 OFF 참조는 복원본이며 공식 NC 원본 소스 공개물이 아니다. 이번에는 선행 OFF 비교 결과를 참고하고 최신 RKern 소스를 정적으로 확인했다. OFF 전수 재대조는 후속 항목이다.
- 대상: PC/NPC 공통 스탯, 클래스·레벨 기반 파생 능력치, HP/MP, AC/MR, 장비·스킬·영구 성장·재분배, DB 및 클라이언트 표시.
- 원칙: 리니지1 수식·게임 규칙을 보존하고 CCreature/CStats/CPCParamDB/ValidateParameters의 역할 경계를 따른다. 기존 CIO/NpcServer 소유 구조와 CacheD 영속화 보존. 새 스레드·큐·캐릭터별 래퍼/배열/캐시를 근거 없이 추가하지 않는다.
- 이번 결과는 소스 확인 및 시험 계획이다. 생산 코드 수정, 전체 Java 21 컴파일, 실제 DB·게임·성능 시험은 수행하지 않았다. 특히 선행 CALCSTAT_CLEANUP.md에도 일부 회귀가 실제 JVM에서 실행되지 않았다고 명시되어 있으므로 기존 보고서의 미실행 시험을 통과로 간주하지 않는다.

## 2. 현재 구조 (최신 소스 확인)

| 소스 | 책임 / 확인 근거 |
|---|---|
| [L1Character.java](../../src/l1j/server/server/model/L1Character.java#L97) | PC와 NPC 공통. STR/DEX/CON/INT/WIS/CHA 각각 Original/Stat/Added byte 필드 및 packed skill long. HP/MP·AC/MR·전투 보정도 공통 필드로 관리. |
| [L1Character.java:1412–1558](../../src/l1j/server/server/model/L1Character.java#L1412-L1558) | ValidateStat, GetTotalStat, SetSkillPrimaryStats, ResetPrimaryStats, SP. 변경 비트에 따른 VB_HP/MP/MDEFEND/PDEFEND 갱신. |
| [CPCParamDB.java](../../src/l1j/server/server/utils/CPCParamDB.java) | 기존 CalcStat 역할. 공용 byte[] DATA와 offset lookup, 클래스/레벨 파라미터 및 일부 공식. PC별 계산 객체 없이 공유. |
| [L1Character.java:511–559](../../src/l1j/server/server/model/L1Character.java#L511-L559) | 클래스/레벨 기반 기본 보너스는 PC에만 적용. STR/DEX/INT/WIS/CON 기반 공격·방어·회복·소지 무게 조회. |
| [L1NpcInstance.java:394–436](../../src/l1j/server/server/model/Instance/L1NpcInstance.java#L394-L436) | 공유 NPC template을 CopyFrom으로 읽고 CopyBaseParameters, HP/MP 및 공격 보정 초기화. 변신/펫·서먼·컴패니언 생성 경계도 감사 필요. |
| [SkillEffectContainer.java:72–148](../../src/l1j/server/server/model/character/skill/SkillEffectContainer.java#L72-L148), [203–235](../../src/l1j/server/server/model/character/skill/SkillEffectContainer.java#L203-L235) | CCreatureSkillMod 초기화 후 등록 효과를 다시 pump, packed 결과를 owner에 게시. 스킬 원천과 장비 원천을 혼합하지 않는다. |
| [CUserEquipment.java:640–770](../../src/l1j/server/server/model/inventory/CUserEquipment.java#L640-L770) | 장착/해제 ±1, 강화·세트·축복·아이템 스킬·외형/알림 처리. 전체 equip 재실행을 스탯 재계산으로 사용하면 안 된다. |
| [L1PcInstance.java:5321–5358](../../src/l1j/server/server/model/Instance/L1PcInstance.java#L5321-L5358) | 클래스·레벨·DEX 기반 AC, 클래스·레벨·WIS 기반 MR 갱신. |
| [L1PcInstance.java:6680–6748](../../src/l1j/server/server/model/Instance/L1PcInstance.java#L6680-L6748) | onStat의 포인트 검증, 영구 스탯 변경, 동기화, 알림·save. |
| [MySqlCharacterStorage.java:42–80](../../src/l1j/server/server/storage/mysql/MySqlCharacterStorage.java#L42-L80), [211–222](../../src/l1j/server/server/storage/mysql/MySqlCharacterStorage.java#L211-L222), [423–434](../../src/l1j/server/server/storage/mysql/MySqlCharacterStorage.java#L423-L434) | 초기/영구 스탯 12개를 읽고 직접 저장 및 스냅샷에 기록. 장비·스킬 값은 여기에 합산하여 영구 저장하지 않는다. |
| [SC_CHAR_STAT.java](../../src/l1j/server/server/packets/Protocol/MainServer/Proto/SC_CHAR_STAT.java) | 순수 스탯/최종 스탯/UI 표시 및 가상 스탯 계산. 실제 전투·성장 공식과 비교 필요. |

스탯 원천:
Original(초기/영구 원천의 기준) → Stat(순수 영구값) + Added(비스킬 보정) + Skill(버프/디버프) → 범위 제한된 Total → 파생 수치.
Original의 실제 변경 주체(엘릭서/재분배 등)는 전체 호출부로 확정한다. Stat은 Original 이상의 값으로 제한된다. Added와 Skill은 별개이며 현재 getter의 중간 clamp 순서도 보존해야 할 계약인지 검증한다.

Skill packed lanes: STR 0–7, DEX 8–15, INT 16–23, WIS 24–31, CON 32–39, CHA 40–47. long 하나를 읽으면 해당 6개 값의 스냅샷이지만 전체 전투 파라미터의 원자적 스냅샷은 아니다.

## 3. 발견 사항 및 우선순위

### S01 — 높음, 장비 스탯 누적 포화 및 가역성 (조건부 결함)
L1Character:1436–1488의 AddAddedStat*는 매 증분마다 signed byte [-128,127]로 포화한다. 예를 들어 비스킬 원천 합 120에 +20을 적용하면 127, 이후 -20을 적용하면 107이 된다. 기대값 120과 다르다. 실제 가능한 장비·컬렉션·부가효과 조합이 이 범위에 도달하는지 아직 확인하지 않았다. 양·음의 포화와 적용 순서, 장비 교환 및 재접속 후 값 복원을 검증한다. 필요하면 내부 accumulator 폭만 충분히 넓히고 표시/기존 전투 경계에서 clamp한다. 영구 스탯 상한과 wire 규격은 변경하지 않는다.

### S02 — 높음, packed skill 보정의 byte wrap (조건부 결함)
SkillEffectContainer:77–85 addPrimaryLanes는 lane 사이 carry를 막지만 8bit 자체 overflow를 포화하지 않고 버린다. effectivePrimaryStats:140–148도 bonus-penalty를 0xff로 마스크하며 L1Character getter는 signed byte로 해석한다. 예: 양의 총합 130은 signed -126으로 읽힐 수 있다. 실스킬 상한 및 동시 활성 효과를 먼저 열거하고 양수/음수, 127/128/255/256, 해제 후 복원 및 다른 lane 오염을 검증한다. S01과 독립적으로 처리.

### S03 — 중간, DEX 투자 AC 재계산 및 패킷 중복 (실행 경로 확인)
onStat(DEX) → AddStatDex → SetStatDex → ValidateParameters(VB_PDEFEND) → RefreshBasePDefend. 이후 onStat이 resetBaseAc()를 다시 호출해 동일 마스크를 실행한다. AddStatDex와 resetBaseAc 모두 SC_OwnCharAttrDef를 전송한다. AddStatDex의 SC_OwnCharStatus2도 onStat 후반에서 다시 송신한다. 성공 경로에서 값이 중간에 바뀌지 않는지와 상태 패킷 순서를 확인하고 실제 중복만 제거한다.

### S04 — 중간, STR/CON 투자 및 복합 장비/버프 변경 알림 중복
AddStatStr/Con에서 SC_Weight 전송, onStat 후반 isWeightUpdated 경로에서 다시 전송. STR은 SC_OwnCharStatus2도 중복. NotifySkillPrimaryStats는 STR/CON 각각 변경 시 무게 알림을 호출하고 INT/WIS 각각 변경 시 SP/MR을 송신한다. 복합 변경을 dirty mask로 묶어 최종 상태 확정 뒤 동일 의미의 중복 패킷만 제거할 후보. 기존 wire opcode, mode, payload 및 필수 표시 순서는 보존. 장비 스왑, 세트, 강화, 엘릭서, 스탯 초기화 포함. 일괄 적용을 위해 기존 장착 부수효과를 다시 호출하지 않는다.

### S05 — 높음, 스킬 파라미터 부분 게시와 UI 정합성 (검증 필요)
L1Character:1130–1141은 primary, HP/MP clamp, AC/MR, primary notification, elemental/evasion/weapon/combat/SP 순서로 적용한다. 따라서 INT/WIS 등의 알림이 이후 SP/combat 보정 게시보다 앞설 수 있다. SC_SPMR/SC_CHAR_STAT의 실제 필드 읽기와 전송 시점, 다른 샤드의 전투 읽기를 추적한다. 하나의 logical mutation 후 최종 스탯 표시가 맞는지 검증. 단순히 volatile long이 있다는 이유로 전체 상태 원자성을 주장하지 않는다. 다중 writer가 확인되면 기존 소유권/동기화 경계에서 해결.

### S06 — 높음, HP/MP 규칙 및 clamp (검증 필요)
L1Character:1143–1220에서 PC HP는 순수 CON, PC MP는 순수 WIS, NPC MP는 Total WIS 기반 pureHpMpBonus를 더한다. 원본 L1 규칙과 저장 MaxHp/MaxMp 의미, NPC template MP 및 setCurrentMp 호출 순서를 대조. 영구 CON/WIS 하락, 장비/버프 해제, 레벨·직업 변경, 사망/부활/리셋, MP 0, 최대치 경계에서 현재값이 범위 이내이고 불필요하게 회복/감소하지 않는지 검증. 고정/추가/스킬 HP·MP와 성장 보너스의 중복 적용 금지.

### S07 — 높음, DB 로딩 및 저장 스냅샷 (검증 필요)
MySqlCharacterStorage는 스탯 setter를 type/class 설정보다 먼저 호출한다. DEX/WIS setter의 파생 능력치 갱신이 실제 로그인 최종 validate에 의해 정상 복구되는지 CS_EnterWorld와 장비 복원까지 추적. Original/Stat 12개, bonusStats, class/level, 기본 HP/MP, 엘릭서 영속값을 같은 논리 상태로 저장하는지 CacheD·캐릭터 저장 호출부 확인. 저장 지연/실패/재접속/서버 재시작에서 장비·스킬 누적이나 포인트 유실/중복 금지. 새 DB 경로를 만들지 않는다.

### S08 — 높음, NPC 공통 파라미터 초기화 (검증 필요)
CopyFrom→CopyBaseParameters→setMaxHp/Mp→SetDefaultParameters 경로. randomlevel의 span 기반 보너스 등 기존 L1 동작을 변경하지 말고 실제 원래 수식과 대조. NPC 재생성·변신·펫/서먼/컴패니언, template 공유, 기존 효과 해제 순서, PC 전용 class/level 보너스 미적용을 검증한다.

### S09 — 중간, 스탯 재분배/가상 UI 계산 (검증 필요)
StatContext.resolve의 현재값 계산은 Stat+Added를 직접 합산하며 GetTotalStat의 최종 clamp를 호출하지 않는다. returnStat 상태에서 SetStat*를 직접 호출하는 특수 흐름, 가상 UI가 실제 영구 상태를 변경해도 되는 경계인지 검토. L1StatReset/L1ReturnStatTemp, SC_CHAR_STAT, FullStater, 엘릭서·클래스 변경·캐릭터 생성/취소의 모든 호출부 대조. UI 미리보기는 의도된 확정 경계 외에서 실제 스탯을 변경하면 안 된다. 기존 스탯 분배 정책과 포인트 상한 보존.

### S10 — 중간, 파생 능력치 계산 중복 및 allocation (최적화 후보)
CPCParamDB의 공유 테이블은 유지. 공격/명중/치명타/AC/MR/SP/무게/회복 계산이 전투·UI에서 일치하는지 우선 검증. 동일 패킷/공격 내 중복 getter 또는 재계산이 실제 hot path인지 JFR·측정으로 판단. 모든 스탯을 캐릭터별 캐시에 복제하지 않는다. FullStater, StatContext의 배열·clone·문자열 등은 실제 호출 빈도를 확인 후 필요 시 최적화하며 hot path로 가정하지 않는다.

## 4. 순차 검증 계획 및 완료 기준

| 단계 | 시험 | 통과 기준 |
|---|---|---|
| V1 | 전체 참조 조사 + OFF 복원본 ValidateParameters/ValidateSkillMod 재대조 | 각 필드의 writer/reader, 소유 스레드, 원천, 계산/알림/저장 경계 및 기존 L1 수식 목록 확정 |
| V2 | Java 21 실제 컴파일 + 수치 회귀 | 8개 PC 클래스, NPC, 레벨 1/50/51/89/90 및 최대 지원 레벨, 6개 스탯 경계 0/1/45/50/80/127, 보정 -128/127 초과 등. 기준과 후보 값 비교, 정당화된 결함 수정만 차이 허용 |
| V3 | 장착/해제/강화/스왑/세트/축복/컬렉션/복합 버프 | 적용-제거 가역성, 중첩 및 순서, 재적용·만료·디스펠, 사망/부활, NPC 초기화/변신. 6개 스탯과 모든 파생값 대조. 실제 가능한 아이템/스킬 데이터 조합 포함 |
| V4 | 패킷 및 전투 | 동일 의미의 상태 패킷 중복 제거 전후 횟수와 wire 내용, HP/MP/AC/MR/SP, 무게/ER, 명중·피해·치명타와 UI 일치. 타겟/랜덤 전투 항은 원래 계산 유지 |
| V5 | 저장·복구 | 실제 MySQL/CacheD에서 캐릭터 생성, 투자, 엘릭서, 재분배, 로그아웃/재접속, DB 장애 및 ACK 유실, 재시작 시 일관성. 기존 receipt 정책 유지 |
| V6 | 동시성 | CIO/NpcServer 경계에서 장비 스왑과 버프 만료·전투·저장·로그아웃 경합, 중복 효과/유실/오래된 콜백 없음. 상태 변경 권한과 스냅샷 정합성 확인 |
| V7 | 성능 | 기준/후보 같은 JDK21·부하·머신, 5회 이상 교차 반복. 처리량, p50/p99/p99.9, CPU/op, allocation B/op, 상태 패킷/회, 파생 계산/회, GC와 메모리 사용. 실제로 개선되고 정합성 회귀 없는 후보만 채택 |

실제 전체 빌드, 모의 회귀, 통합 회귀, 운영 부하를 서로 구분해 결과를 남긴다. 실측 없이 동접 증가 또는 CPU 절감률을 주장하지 않는다.

## 5. 권장 수정 순서
1. S01/S02/S05/S06/S07/S08/S09의 수치·원천·저장·생명주기 문제를 먼저 검증한다. 실제 재현 결함만 수정.
2. S03/S04의 중복 파생 갱신 및 알림을 통합한다. 기존 ValidateParameters 마스크를 확장할 때에는 L1의 원천별 semantics와 패킷 순서를 보존.
3. S10은 프로파일과 동일조건 A/B에 따라 채택. 이미 제거된 래퍼/중복 캐시의 재도입은 하지 않는다.

## 6. 현 시점 결론
기본 데이터 저장 구조와 공유 계산 테이블은 이미 NC식 책임 분리 방향으로 상당 부분 이관되었다. 전면 재작성보다 현재 구조에서 스탯 원천 가역성, packed 범위, 전체 파라미터 게시 순서, 저장·복구, UI 갱신의 일관성을 먼저 검증하고 중복 실행을 제거하는 것이 우선이다. 이 보고서만 추가하며 생산 Java, SQL, 설정, 기존 게임 동작은 변경하지 않는다.
