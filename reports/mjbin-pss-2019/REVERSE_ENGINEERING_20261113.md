# MJBin (`mjlin.bin`) 2019 PSS 자동사냥 정적 역분석 보고서

작성 기준: 2026-11-13. 사용자 업로드 원본과 실행 메모리에서 덤프한 복호화 코드에 대한 정적 분석. **실행 테스트·후킹 동적 추적은 수행하지 않았음.**

## 0. 분석 자료 및 신뢰 구분

| 자료 | 실제 확인 | SHA-256 |
|---|---:|---|
| `mjlin.zip` 내부 `mjlin.bin` | 8,354,488 bytes | `9c82129efed1d93981782069e82de79869920769aa0f67bc1183ca75cf621823` |
| `mjlin_loaded_code_00400000_0180D000.bin` | 21,024,768 bytes | `256f7886e98d7d2c573ab76917f78e30dc4f34cb723a002d604602cdb2f6cbdb` |
| `mjlin.bin.3860.dmp` | 46,149,837 bytes | `07051a7b13a72224f8cad942328533f14bc9a79b66aeb6c7271a67ddfe9aa881` |

- PE32 / Intel i386, IMAGE_BASE `0x00400000`, PE TimeDateStamp **2019-02-15 04:25:14 UTC**.
- 메모리 이미지 파일은 코드/데이터의 앞쪽 일부(파일 크기 0x140D000)를 담고 있다. PE의 SizeOfImage는 `0x01B2F000`이므로 **이미지 전체가 아니다**. 특히 외부 설정 JSON/CFG와 리소스는 별도로 필요하다.
- 용어: **[확정]** 실제 Protobuf 역직렬화와 코드 주소·RTTI·디스어셈블리에서 확인. **[구조적 해석]** 이름·인접 호출 및 설정 필드로 뒷받침되는 동작 해석. **[미확인]** 실제 분기·수치·연결 관계를 증명하지 못한 부분.

## 1. 전체 구조: PSS는 단순 패킷 호출기가 아니라 클라이언트 내부의 행동 그래프 [확정]

```
PlaySupportUI (사용자 설정 / 시작·종료)
       │
       ├─ PSS 설정: PlaySupportConfig (10개 영역)
       ├─ 서버 승인·잔여시간: MainServer_Client_PlaySupport.proto
       │
       ▼
BotConductor (행동 진행기)
       ├─ BotBattleStep
       ├─ BotExpVillageStep
       ├─ BotExpRecoverStep
       ├─ BotToFieldStep / BotToWarehouseStep / BotVillageStep
       │
       ▼
BotNode/IBotStep (행동 노드 팩토리 및 실행 단계)
       ├─ 주변 몬스터 탐색 → 타겟 이동 → 일반 공격/스킬 공격
       ├─ 아이템 탐색 → 아이템 이동 → 획득
       ├─ NPC 탐색 → 대화 → 구매/창고/이동
       └─ 탈출/귀환/재시작/종료/사망
```

- `BotConductor::Next` 텍스트 xref `0x00669B31`; 진입 함수 `0x00669B20`. 노드 존재 여부 체크, 단계 갱신, vtable `+8`의 가상 실행 함수 호출, `NextStep %s, Run %d` 로그, 다음 단계 진행 함수를 실행하고 반환 값에 따라 반복/중단한다.
- `BotNode` 이름별 팩토리의 실제 문자열 매칭 및 생성 분기: `0x0067D5FA` 부근부터 `0x0067DDxx`. 실제 PSS가 **노드별 행동 구현**을 갖는 근거다.
- `playSupport.json` 파일명을 로딩하는 코드 xref는 `0x0067E0BC`. 파일 내용은 현재 보유한 세 바이너리에 평문 JSON으로 존재하지 않는다. 실제 그래프의 모든 전이 순서를 단정하지 않는 이유.

## 2. 실제 확보한 행동 노드 27개 [확정 RTTI / 팩토리 문자열]

| 분류 | 노드 | 역할에 대한 해석 |
|---|---|---|
| 몬스터 탐색 | `BotSearchMonsterNode` | 몬스터 후보 선택 |
| 타겟 이동 | `BotWalkTargetNode` | 지정 타겟으로 접근 |
| 공격 조건 | `BotCanAttackNode`, `BotMonsterAttackCheckNode` | 공격 가능성/전투 상태 확인 |
| 공격 | `BotAttackNode`, `BotSpellAttackNode`, `BotCustomSpellNode` | 일반 공격, 일반 스킬, 조건부 커스텀 스킬 |
| 아이템 루팅 | `BotSearchItemNode`, `BotWalkToItemNode`, `BotGetNode` | 획득할 대상 탐색, 접근, 획득 |
| 사냥터 이동 | `BotPlayPath`, `BotTeleportNode`, `BotTeleportBookNode`, `BotAdenTelNode` | 지정 경로, 텔레포트 수단 |
| NPC 서비스 | `BotWalkNPCNode`, `BotSearchNpcNode`, `BotDialogNode`, `BotDialogActionNode`, `BotAnswerNode` | NPC 찾기·이동·대화·응답 |
| 보급 | `BotBuyNode`, `BotDepositNode`, `BotRetrieveNode` | 구매, 맡기기, 찾기 |
| 수명/예외 | `BotReturnNode`, `BotEscapeNode`, `BotRestartNode`, `BotEndNode`, `BotDieNode` | 귀환·탈출·재시작·종료·사망 대응 |

`BotBuyNode`는 문자열 테이블에 2회 등록되어 있어 이름은 27종, 팩토리 레이블은 28회다.

## 3. 실제 코드 진입점 / 가상 함수 주소 [확정]

주소는 메모리 이미지 기준 **VA**(ImageBase=0x00400000). 이름은 RTTI 클래스 이름으로 분류했고, 해당 가상 함수의 정확한 소스 메서드 이름까지 확보한 것은 아니다.

| 클래스 / 기능 | 메서드 진입 VA | 정적 관찰 |
|---|---|---|
| `BotConductor::Next` | `0x00669B20` | 행동을 순차 진행하고 다음 노드로 넘김 |
| 노드 문자열 팩토리 | `0x0067D5FA` 이후 | 각 문자열과 대조해 노드 생성 |
| `BotSearchMonsterNode` | `0x00686560` | 대상 상태·월드 컨텍스트 조건·목표 ID 등을 확인 |
| `BotWalkTargetNode` | `0x0068DA50` | 대상 ID 조회 후 이동/목적지 처리 |
| `BotAttackNode` | `0x00657460` | 공격 설정에서 플래그 확인 및 대상 참조 |
| `BotSpellAttackNode` | `0x00688520` | 스킬 사용 설정 플래그 확인 및 현재 목표 ID 참조 |
| `BotGetNode` | `0x00678DF0` | 아이템 획득 활성 상태와 현재 목표 참조 |
| `BotEscapeNode` | `0x00676550` | 탈출 설정 활성화 플래그 확인 |
| `BotReturnNode` | `0x006836A0` | 귀환 설정 활성화와 월드 상태 검사 |
| `BotEndNode` | `0x00674AD0` | 조건 목록과 종료 상태 검사 |
| `PlaySupport UI` RTTI | `.data` VA `0x01577DA8` | UI 클래스 존재 |
| `AttackControler` RTTI | `.data` VA `0x0156FEE8` | 공격 관련 Controller 존재 |
| `HuntingControler` RTTI | `.data` VA `0x01578218` | 사냥 관련 Controller 존재 |

특히 코드에서 `BotSearchMonsterNode`는 몬스터 선택 결과가 없을 때/유효하지 않을 때 분기하고(`0x006865E5`~), 하위 후보 수집 함수 `0x006867A0`에서 설정 객체의 플래그/값을 읽되 미설정 시 `mov ebx, 0xA`(`0x00686815`)로 수치 **10을 기본값으로 사용하는 것까지 확인**되었다.  `BotWalkTargetNode`는 목표 ID가 `-1`일 때 즉시 다른 반환 경로로 빠진다(`0x0068DAB0`~). `BotAttackNode` 및 `BotSpellAttackNode`는 설정 플래그 미활성화 시 실행을 진행하지 않는 분기를 가진다(`0x006574C0`~, `0x00688584`~).

## 4. PSS 세부 설정 [확정; `PlaySupport.proto` 전체 복원]

### 4-1. 사냥 `HuntingCfg`
- `enable`(bool), `activateCustomMove`(bool), `selectIndex`(int), `pathRecords`(repeated), `teleportSlotList`(repeated).
- `activateHoldHunting`(bool, default false) — 고정 위치 사냥 설정.
- `activateTeleport`(bool, default false), `teleportSec`(int, default 0) — 텔레포트 옵션.
- `searchRange`(int, default **10**) — 탐색 범위 수치. **단위는 스키마만으로 확정할 수 없다**.
- `exceptHuntedNpc`, `priorQuestNpc`, `priorAggresiveNpc`(각 bool, default false) — 사냥 중인 NPC 제외/퀘스트 NPC 우선/선공 NPC 우선 옵션으로 해석.

### 4-2. 공격 `AttackCfg`
- `activateBasicAttack`(required bool), `skillList`, `customSkillList`.
- `activateSkillAttack`, `activateCustomSkillAttack`, `activateGetItem` (각 default false).
- `tryCount`(int, default 0), `giveUpItem`(bool, default false). **`tryCount`가 타격 재시도인지 루팅 재시도인지 현재 단계에서 단정하지 않음**.
- `AutoUsingInfo`는 **사용 조건 `UsingCondition` 1개와 아이템/스킬 슬롯 목록**으로 구성됨.

### 4-3. 버프·회복
- `BuffCfg`: 자동 사용, 자동 변신, 변신 슬롯, 타이머 사용, 반복 `autoUsingList`, `timerUsingList`.
- `RecoverCfg`: HP 및 MP 회복에 각각 `AutoUsingInfo` 설정.
- 실제 C++ RTTI에서 `BotHitPointConditionTriggerGenerator`, `BotMagicPointConditionTriggerGenerator`, `BotStatusEffectConditionTriggerGenerator`, `BotPolymorphConditionTriggerGenerator`, `BotDetoxConditionTriggerGenerator`, `BotTimerConditionTriggerGenerator`, `BotSinkConditionTriggerGenerator` 확인.

### 4-4. 위험 시 도망·귀환·중지
- `EscapeCfg`: `enable`, `usingSlot`, `conditionList`.
- `ReturnCfg`: `activateReturn`, `conditionList`, `activateCustomReturn`, `customReturnIndex`, `returnSlot`.
- `StopCfg`: `enable`, `conditionList`, `activateMobile`.
- 설정 조건(`UsingCondition`)이 서로 다른 행동(자동 사용, 귀환, 종료)에 재사용되는 공통 포맷.

### 4-5. 출발 전 준비 / 보급 `PrepareCfg`
- 준비 활성화, 설정 적용/상점/출발 조건/버프 옵션.
- `PrepareOption` 각각 `itemInfo`, `retrieveCount`, `needToBuy`, `needToMelting`, `buyingCount` 보유.
- `StartOption`에는 시작 HP/MP 두 숫자.
- 길찾기 경로는 `PathRecord`의 `name`과 `PathInfo[]`로 구성.
- `PathInfo.PathType`: `PATH(0)`, `TELEPORT(1)`, `DIALOG(2)`, `HACTION(3)`, `ITEM(4)`, `MAX(5)`.
- `PathInfo`에는 맵 ID, 2D 좌표, 문자열 값, ID, 축복 상태 코드가 존재. 따라서 사냥터까지 걸어가거나 NPC 대화·텔레포트·아이템 행동을 섞는 **복합 경로 형식**을 지원.

### 4-6. 공통 조건 19종
| 수치 | `UsingCondition.StateType` | 의미에 대한 해석 |
|---:|---|---|
| 0 | HP | 체력 |
| 1 | MP | 마나 |
| 2 | DETOX | 해독 |
| 3 | SINK | 특정 상태 |
| 4 | BUFF | 버프 |
| 5 | NONCOMBAT | 비전투 시간/상태 |
| 6 | COMBATTIME | 전투 시간 |
| 7 | PVP | PvP |
| 8 | ATTACKNUM | 공격 횟수 |
| 9 | ITEM | 소모품/아이템 |
| 10 | PSSPLAY | PSS 실행 |
| 11 | WEIGHT | 무게 |
| 12 | INVENSIZE | 인벤토리 공간 |
| 13 | DIE | 사망 |
| 14 | TIME | 시간 |
| 15 | RETURN | 귀환 |
| 16 | COUNT | 횟수 |
| 17 | PSSSTOP | PSS 중지 |
| 18 | NONE | 미설정 |

- `UsingCondition`: `state`, `value`(uint32), `activate`(bool), `operator`, `strValue[]`, `itemInfo[]`.
- `Operator`: `GREATER_EQUAL(0)`, `EQUAL(1)`, `LESS_EQUAL(2)`.
- 원본의 `state` 철자와 enum 수치는 **실제 추출한 Protobuf descriptor에 근거**.

## 5. 서버-클라이언트 PSS 프로토콜 [확정; `MainServer_Client_PlaySupport.proto`]

- `CS_START_PLAY_SUPPORT_REQ`: 본문 없음.
- `SC_START_PLAY_SUPPORT_ACK`: `result=VALID(0), INVALID_MAP(1), INVALID_LEVEL(2), TIME_EXPIRE(3), UNKNOWN_ERROR(99)`.
- `CS_FINISH_PLAY_SUPPORT_REQ`: 본문 없음.
- `SC_FINISH_PLAY_SUPPORT_ACK`: `remain_time`(uint32).
- `SC_FORCE_FINISH_PLAY_SUPPORT_NOTI`: `reason=INVALID_MAP(1), INVALID_LEVEL(2), TIME_EXPIRE(3), USER_DEAD(4), MAP_TIME_EXPIRED(5)` + `remain_time`.
- `CS_PSS_TIME_CHECK_REQ`: 본문 없음.
- `SC_PSS_TIME_CHECK_ACK`: `remain_time`(uint32).
- `SC_UPDATE_PLAY_SUPPORT_TIME_NOTI`: `type=CHARGE_BY_EVENT(1), CHARGE_BY_ITEM(2)` + `remain_time`.

**역할 분담:** 시작 허용, 레벨·맵·잔여시간 검사는 서버 ACK/강제 종료 프로토콜에 반영되어 있으며, 이후 **클라이언트 내부 BotConductor+Node들**이 움직임·공격·스킬/회복을 실행하도록 설계되어 있다. 실제 게임 서버의 모든 조건 검사와 패킷 ID는 이 클라이언트 정보만으로 완전히 증명할 수 없음.

## 6. 원본 자동사냥 실행 흐름 — 확인 수준 표시

```
UI / PSS 설정 로딩                    [확정]
  ↓
서버 PSS 시작 요청·승인                [프로토콜 확정]
  ├─ 지도/레벨/시간 제한 서버 결과     [프로토콜 확정]
  ↓
BotConductor가 행동 단계 실행          [어셈블리 확정]
  ↓
사냥터까지 Path/NPC/Teleport 경로      [노드/설정으로 구조 확정, 실제 전이 순서 미확인]
  ↓
BotSearchMonsterNode → BotWalkTargetNode
  → BotCanAttackNode/MonsterAttackCheckNode
  → BotAttackNode 또는 BotSpellAttackNode / BotCustomSpellNode
                                      [노드 실존 확정, 정확한 전이 순서 미확인]
  ↓
BotSearchItemNode → BotWalkToItemNode → BotGetNode
                                      [노드 실존 확정, 구체적 획득 조건 미확인]
  ↓
Escape/Return/Stop/Recover/Buff 등 조건에 따라 분기
                                      [설정·Controller·노드 존재 확정, 우선순위 순서 미확인]
  ↓
서버 시간 소진·잘못된 맵·사망 등 강제 종료 통지
                                      [프로토콜 확정]
```

## 7. 원본에서 추가로 발견한 중요한 구현 특징

1. **공통 동작의 분리**: `AttackControler`, `BuffControler`, `EscapeControler`, `HuntingControler`, `PrepareControler`, `RecoverControler`, `ReturnControler`, `StopControler` 등 UI/설정 측 controller가 분리되어 있음.
2. **노드와 단계가 별개**: RTTI에 `BotNode`, `IBotStep`, `BotBattleStep`, `BotExpRecoverStep`, `BotExpVillageStep`, `BotFieldCheckStep`, `BotToFieldStep`, `BotToWarehouseStep` 등의 서로 다른 타입이 존재.
3. **공통 조건식**: 종류(`state`)+임계값(`value`)+비교자(`operator`)+사용 여부(`activate`)로 수많은 자동 사용 정책을 설정형으로 처리.
4. **별도 PSS 구성 파일**: `PSS%d%s.cfg`, `playSupport.json`, `PSSRecommendTel.json`, `PSClassRecommend.json` 참조 및 로딩 코드/문자열 확인.
5. **객체 ID 유효성 검사**: 몬스터 탐색/이동/스킬 실행 코드에서 공유 컨텍스트 목표 ID의 `-1`/없음 분기와 NPC 조회 결과 NULL 검사 확인.
6. **반복 단계 진행기**: `0x00669B20`에서 현재 단계의 가상 `Run` 호출 후 성공/종료 여부에 따른 다음 단계 전환을 처리.

## 8. 확인하지 못한 항목 — 성능/버그 분석 시 매우 중요

아래는 파일을 확인했더라도 **실행 의미를 끝까지 복원했다고 말할 수 없는 부분**이다.

- `playSupport.json` 본문이 없어 **노드의 정확한 연결 그래프와 분기 우선순위**는 확정 불가.
- 몬스터 후보 수집의 실제 **점수 공식, 선공/퀘스트 우선 순서, 공격 중인 NPC 배제 기준**과 시간 제한은 추가 함수 호출 역추적 필요.
- `tryCount`가 **공격 실패 횟수인지 루팅 획득 시도 횟수인지**, 실패 후 몇 ms에 재시도하는지 미확정.
- 길막 감지의 **이동 재시도 횟수, 탐색 알고리즘, 블랙리스트 TTL**, 자사 경로 복귀 시점 미확정.
- 버프/물약/스킬 간 **실제 우선순위, 시간 간격, 대상 선택 세부** 미확정.
- JSON/CFG 실제 기본 설정 프리셋 및 서비스 NPC 목록 미확보.
- PSS 네트워크 Protobuf의 **게임 서버 opcode 정수 매핑**은 descriptor에 들어 있지 않으므로 이 분석만으로 특정 숫자와 1:1 확정 불가.
- PE 전체 이미지/리소스/실행시 메모리 상태를 모두 캡처한 덤프가 아니라서 최종 패킷 핸들러와 모든 외부 호출의 완전 증명은 아직 불가.

## 9. L1J 서버팩 복원에 유용한 결론

- 원본 MJ PSS는 '가장 가까운 몬스터 → 공격'만 있는 장치가 아니라 **설정형 조건 엔진 + 행동 단계 진행기 + 행동 노드 + 서버 사용량 승인**의 복합 구조다.
- L1J에서 재구현하려면 서버가 이미 가진 `L1PcInstance` 공격·스킬·이동·아이템 경로를 재활용하고, `PlaySupportConfig` 및 노드의 **판단 순서**만 복원하는 게 정확한 출발점이다.
- 하지만 이 분석으로 **NC 원본의 모든 타겟 선택·이동·조건 코드가 복원됐다고 주장하지 않는다**. 구조·스키마·특정 함수 주소는 확인되지만 정확한 전체 상태 전이는 별도 실험/설정자료가 필요하다.

---

## 부록 A — 원본에서 추출한 `PlaySupport.proto` 스키마

다음은 메모리 이미지의 FileDescriptorProto (`RVA 0x00DADB88`부터 4094 bytes)를 원본 필드 수/유형/기본값 그대로 읽어 **사람이 볼 수 있게 재구성한 것**이다.

```proto
// EXTRACTED FROM BINARY: PlaySupport.proto
syntax = "proto2";
package PSS;

enum BlessCode {
  BLESSED = 0;
  NORMAL = 1;
  CURSED = 2;
  UNIDENTIFIED = 3;
}

message Point {
  required int32 x = 1;
  required int32 y = 2;
}

message PathInfo {
  enum PathType {
    PATH = 0;
    TELEPORT = 1;
    DIALOG = 2;
    HACTION = 3;
    ITEM = 4;
    MAX = 5;
  }
  required int32 map = 1;
  required PSS.Point pos = 2;
  required PSS.PathInfo.PathType type = 3;
  optional string strValue = 4;
  optional int32 id = 5;
  optional PSS.BlessCode bless = 6;
}

message PathRecord {
  required string name = 1;
  repeated PSS.PathInfo pathlist = 2;
}

message SlotInfo {
  enum SlotType {
    UNKNOWN = 0;
    ITEM = 1;
    SKILL = 2;
  }
  required string desc = 1;
  required int32 icon = 2 [default = -1];
  required PSS.SlotInfo.SlotType slotType = 3 [default = UNKNOWN];
  optional int32 count = 4;
  optional PSS.BlessCode bless = 5 [default = NORMAL];
}

message UsingCondition {
  enum StateType {
    HP = 0;
    MP = 1;
    DETOX = 2;
    SINK = 3;
    BUFF = 4;
    NONCOMBAT = 5;
    COMBATTIME = 6;
    PVP = 7;
    ATTACKNUM = 8;
    ITEM = 9;
    PSSPLAY = 10;
    WEIGHT = 11;
    INVENSIZE = 12;
    DIE = 13;
    TIME = 14;
    RETURN = 15;
    COUNT = 16;
    PSSSTOP = 17;
    NONE = 18;
  }
  enum Operator {
    GREATER_EQUAL = 0;
    EQUAL = 1;
    LESS_EQUAL = 2;
  }
  required PSS.UsingCondition.StateType state = 1 [default = NONE];
  required uint32 value = 2;
  required bool activate = 3;
  optional PSS.UsingCondition.Operator operator = 4 [default = GREATER_EQUAL];
  repeated string strValue = 5;
  repeated PSS.SlotInfo itemInfo = 6;
}

message AutoUsingInfo {
  required PSS.UsingCondition condition = 1;
  repeated PSS.SlotInfo slots = 2;
}

message PlaySupportConfig {
  enum ClassID {
    PRINCE = 0;
    KNIGHT = 1;
    ELF = 2;
    MAGICIAN = 3;
    DARKELF = 4;
    DRAGON_KNIGHT = 5;
    ILLUSIONIST = 6;
    WARRIOR = 7;
    UNKNOWN = 8;
  }
  message RecommendConfig {
    required bool usingForClass = 1 [default = false];
    required bool useAll = 2 [default = false];
    required bool usePart = 3 [default = false];
    required bool useHuntingArea = 4 [default = false];
    optional int32 selectIndex = 5 [default = -1];
  }
  message PrepareConfig {
    enum ItemArrange {
      Default = 0;
      Ascending = 1;
    }
    message PrepareOption {
      required PSS.SlotInfo itemInfo = 1;
      required uint32 retrieveCount = 2 [default = 0];
      required bool needToBuy = 3 [default = false];
      required bool needToMelting = 4 [default = false];
      optional uint32 buyingCount = 5;
    }
    message StartOption {
      required uint32 hp = 1 [default = 0];
      required uint32 mp = 2 [default = 0];
    }
    required bool enable = 1 [default = false];
    required bool activateSetting = 2 [default = false];
    required bool activateStore = 3 [default = false];
    required bool activateStartOption = 4 [default = false];
    required bool activateBuff = 5 [default = false];
    repeated PSS.PlaySupportConfig.PrepareConfig.PrepareOption prepareItems = 6;
    optional PSS.PlaySupportConfig.PrepareConfig.StartOption startOption = 7;
  }
  message HuntingConfig {
    required bool enable = 1;
    required bool activateCustomMove = 2;
    required int32 selectIndex = 3;
    repeated PSS.PathRecord pathRecords = 4;
    repeated PSS.SlotInfo teleportSlotList = 5;
    optional bool activateHoldHunting = 6 [default = false];
    optional bool activateTeleport = 7 [default = false];
    optional int32 teleportSec = 8 [default = 0];
    optional int32 searchRange = 9 [default = 10];
    optional bool exceptHuntedNpc = 10 [default = false];
    optional bool priorQuestNpc = 11 [default = false];
    optional bool priorAggresiveNpc = 12 [default = false];
  }
  message AttackConfig {
    required bool activateBasicAttack = 1;
    repeated PSS.SlotInfo skillList = 2;
    repeated PSS.AutoUsingInfo customSkillList = 3;
    optional bool activateSkillAttack = 4 [default = false];
    optional bool activateCustomSkillAttack = 5 [default = false];
    optional bool activateGetItem = 6 [default = false];
    optional int32 tryCount = 7 [default = 0];
    optional bool giveUpItem = 8 [default = false];
  }
  message RecoveryConfig {
    required bool enable = 1;
    required PSS.AutoUsingInfo hp1 = 2;
    required PSS.AutoUsingInfo mp = 3;
  }
  message BuffConfig {
    required bool enable = 1;
    required bool activateAutoUsing = 2;
    required bool activatePolymoprh = 3;
    required PSS.AutoUsingInfo polymorphList = 4;
    required bool activateTimerUsing = 5;
    repeated PSS.AutoUsingInfo autoUsingList = 6;
    repeated PSS.AutoUsingInfo timerUsingList = 7;
  }
  message EscapeConfig {
    required bool enable = 1;
    repeated PSS.SlotInfo usingSlot = 2;
    repeated PSS.UsingCondition conditionList = 3;
  }
  message ReturnConfig {
    required bool activateReturn = 1;
    repeated PSS.UsingCondition conditionList = 2;
    optional bool activateCustomReturn = 3 [default = false];
    optional int32 customReturnIndex = 4 [default = 0];
    optional PSS.SlotInfo returnSlot = 5;
  }
  message StopConfig {
    required bool enable = 1;
    repeated PSS.UsingCondition conditionList = 2;
    optional bool activateMobile = 3;
  }
  required PSS.PlaySupportConfig.ClassID Class = 1 [default = UNKNOWN];
  required PSS.PlaySupportConfig.BuffConfig BuffCfg = 2;
  required PSS.PlaySupportConfig.RecoveryConfig RecoverCfg = 3;
  required PSS.PlaySupportConfig.EscapeConfig EscapeCfg = 4;
  required PSS.PlaySupportConfig.AttackConfig AttackCfg = 5;
  required PSS.PlaySupportConfig.ReturnConfig ReturnCfg = 6;
  optional PSS.PlaySupportConfig.RecommendConfig RecommendCfg = 7;
  optional PSS.PlaySupportConfig.HuntingConfig HuntingCfg = 8;
  optional PSS.PlaySupportConfig.PrepareConfig PrepareCfg = 9;
  optional PSS.PlaySupportConfig.StopConfig StopCfg = 10;
}

message ClassRecommends {
  repeated PSS.PlaySupportConfig PSClassDatas = 1;
}

```

## 부록 B — 서버 PSS `MainServer_Client_PlaySupport.proto` 스키마

복원한 descriptor 시작 `RVA 0x00D7D180`, 길이 794 bytes.

```proto
// EXTRACTED FROM BINARY: MainServer_Client_PlaySupport.proto
syntax = "proto2";
package LIN;

message CS_START_PLAY_SUPPORT_REQ {
}

message SC_START_PLAY_SUPPORT_ACK {
  enum eResult {
    VALID = 0;
    INVALID_MAP = 1;
    INVALID_LEVEL = 2;
    TIME_EXPIRE = 3;
    UNKNOWN_ERROR = 99;
  }
  required LIN.SC_START_PLAY_SUPPORT_ACK.eResult result = 1;
}

message CS_FINISH_PLAY_SUPPORT_REQ {
}

message SC_FINISH_PLAY_SUPPORT_ACK {
  required uint32 remain_time = 1;
}

message SC_FORCE_FINISH_PLAY_SUPPORT_NOTI {
  enum eReason {
    INVALID_MAP = 1;
    INVALID_LEVEL = 2;
    TIME_EXPIRE = 3;
    USER_DEAD = 4;
    MAP_TIME_EXPIRED = 5;
  }
  required LIN.SC_FORCE_FINISH_PLAY_SUPPORT_NOTI.eReason reason = 1;
  required uint32 remain_time = 2;
}

message CS_PSS_TIME_CHECK_REQ {
}

message SC_PSS_TIME_CHECK_ACK {
  required uint32 remain_time = 1;
}

message SC_UPDATE_PLAY_SUPPORT_TIME_NOTI {
  enum eType {
    CHARGE_BY_EVENT = 1;
    CHARGE_BY_ITEM = 2;
  }
  required LIN.SC_UPDATE_PLAY_SUPPORT_TIME_NOTI.eType type = 1;
  required uint32 remain_time = 2;
}

```