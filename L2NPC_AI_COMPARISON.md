# L2NPC.exe와 현재 RKern NPC AI 비교

분석일: 2026-10-27. 비교 대상은 최신 NPC AI 업로드를 보존한 **v5 소스**다. 이번 작업은 분석이며 게임 소스·DB·배포 ZIP은 수정하지 않았다.

## 판단

**현재 구현은 L2OFF의 Desire 우선순위 처리 핵심을 실제로 옮기고, L1 전투·이동·이벤트 실행기에 연결한 형태다. 기본 몬스터 전투 AI의 흐름은 구현되어 있다. 다만 이 EXE에 있는 범용 NPC 스크립트 엔진과 전체 행동 집합까지 복원된 상태는 아니다.**

이름이나 주석만 보고 내린 판단이 아니다. PE 함수 경계, 실행 추적용 함수명 문자열의 코드 참조, 호출 관계, 실제 x64 명령을 함께 확인했다. Java에서는 API 선언과 실제 호출 경로를 구분했고, 배포 소스로 컴파일된 큐 클래스의 동작도 별도로 관찰했다.

기능 개수나 코드 줄 수를 전체 구현률로 환산할 수 없으므로 “몇 % 복원”이라는 수치는 제시하지 않는다. 아래의 구현 상태는 원본과의 대응 범위이며 성능·동접·실서버 안정성 등급이 아니다.

## 분석 대상과 검증 범위

- 원본: 업로드한 `L2NPC.exe`, 3,098,624바이트, x64 PE.
- 원본 SHA-256: `cad2f7ac1b30db386906359ba0978a9ba9d92d65114b5e4f5f640e3401c7314b`.
- PE 헤더 시간: 2011-07-26 14:03:05 UTC. 헤더 기록이며 설계·유출 시점을 증명하지 않는다.
- 비교 소스: [`RKern_full_src_NPC_AI_network_v5_20261027.zip`](downloads/RKern_full_src_NPC_AI_network_v5_20261027.zip).
- ZIP SHA-256: `e24f534a9939570adc234dbe7f831cb5663a08b24caad93eb5cf21a304ddd159`.
- 아래 원본 주소는 ImageBase `0x400000` 기준 VA다.
- EXE는 실행하지 않았다. 원본 동작에 관한 결론은 정적 분석이다. 원본 EXE·덤프·전체 역어셈블리는 저장소에 게시하지 않았다.

## 기능별 상태

| 영역 | 현재 상태 | 확인 근거와 한계 |
|---|---|---|
| Desire 값 누적·상한 | 핵심 규칙 대응 | 동일 키의 값 합산, 합산 시 `3.3e30f` 제한. 원본 `AddValue`와 Java 구현에서 확인 |
| 즉시 Desire | 핵심 저장·선택 규칙 대응 | `-1.0f`는 일반 힙과 별도 슬롯에 저장. 원본은 별도 슬롯 우선, Java에는 추가적인 L1 공격 가능 여부 필터가 있음 |
| Desire 큐 크기 | 핵심 규칙 대응 | 50을 넘으면 낮은 항목을 강제 퇴출하는 구현이 아님. 원본은 증가량에 따른 경고 경로, Java는 배열 확장 |
| SEE·ATTACKED·CLAN_ATTACKED | 실제 전투로 연결 | `AiRuntime` 이벤트 처리에서 공격 Desire를 만들고 대상 선택에 사용. 계수까지 원본 NPC 스크립트와 같다는 판정은 보류 |
| 공격·추격·줍기·주인/그룹 따라가기 | L1 행동 경로 구현 | `CCreatureController` 및 개별 Action에서 실행. 모든 행동이 원본과 같은 범용 Desire 인터페이스를 통해 경쟁하는 구조는 아님 |
| 스킬 Desire | 저장·선택·실행 경로는 존재, 생성 연결 미완 | `MakeUseSkill → addUseSkillDesire`, `AiRuntime → CSkillAction.onDesireTick`은 존재하지만 제공된 전체 Java 소스에서 `MakeUseSkill` 호출자가 없음 |
| 배회 | 관찰 기반 L1 규칙으로 구현 | 현재 3~6칸 이동 후 6초 휴식. 이 EXE의 `dMoveAround` 분기를 그대로 옮긴 동작은 아님 |
| 감쇠·Boost 설정 | 공격 기본형으로 축소 | 공격 감쇠 6.6·Boost 300 고정. 원본의 NPC별·상태별 파라미터 조회는 없음 |
| 범용 스크립트 VM·행동 집합 | 원본 대응 구현 확인 안 됨 | 원본의 NASC/`ai.obj` 실행기, WayPoint·SuperPoint·Freeway·Formation 등 범용 Desire 경로와 동등한 구현은 제공 소스에서 확인되지 않음 |
| 실행 소유권·할당 관리 | L1 서버에 맞춘 별도 설계 | primitive 배열, stateless Action, 4개 NPC owner shard, 이벤트 큐·공유 타이머 사용. 원본과 같은 성능이라는 뜻은 아님 |

## 실제 대응하는 부분

### 1. CDesirePQ의 기본 의미

원본 `CDesirePQ::AddValue` (`0x41ED70`)에서 확인한 내용:

- `0x41EE63` 이후 값이 `-1.0f`이면 별도 Desire에 복사한다. 큐 내부의 `+0x48` 플래그와 `+0x50` 저장소를 사용한다.
- 중복 Desire는 `CDesire::operator==` (`0x41A8F0`)로 찾는다.
- `0x41EF5E` 이후 기존 값 `+0xA0`에 새 값을 합산한다.
- `0x41EF86` 이후 합산값을 `0x5A5D70`의 float와 비교한다. 이 상수는 Java의 `3.3e30f`와 일치한다.
- 중복 등록에서는 값과 `+0x80` 플래그를 갱신한다. 전체 payload를 새 것으로 교체하지 않는다.
- `0x41F0D5` 부근의 `Too large desire size...`는 경고 경로다. 50개를 넘겼다고 최하위 항목을 제거하는 코드로 해석하면 안 된다.

원본 `_GetTopDesire` (`0x41F7AC`)는 즉시 슬롯을 먼저 보고, 없으면 일반 큐의 첫 항목을 반환한다. Java `CDesirePQ`는 primitive 배열과 지연 힙 재구성으로 같은 우선순위 처리의 핵심을 구현한다. 단, 막힌 공격을 잠시 제외하는 L1 필터 등 추가 정책까지 원본과 동일하다고 보지는 않는다.

### 2. 이벤트에서 실제 공격까지의 연결

현재 소스의 호출 경로는 다음과 같다.

```text
인지/피격/동족 지원 사건
  → NpcEventFactory / HateEvent
  → AiRuntime의 owner 이벤트 처리
  → CDesirePQ.addSightAttack / addAttacked / addClanAttacked
  → topState / topAttackTarget
  → CCreatureController
  → 추격 또는 공격 Action
```

`AiRuntime.java:688,768,807`에서 세 가지 이벤트의 Desire 등록을 확인했다. `selectAction()`은 선택된 공격 대상을 실제 `_target`에 반영한다. 따라서 Desire 큐가 선언만 있고 전투에서는 사용되지 않는 상태는 아니다.

`CIOThreadCallback.submitNpcAction()`이라는 이름과 일부 주석만 보면 별도 CIO 스레드가 행동을 실행한다고 오해할 수 있다. 현재 구현은 `NpcServer.submitAction()`에 위임하며, 이미 해당 NPC owner라면 inline 실행하고 다른 스레드라면 해당 owner에 게시한다. 이 실행 정책은 원본 프로세스 구조를 그대로 복사한 것이 아니라 현재 서버에 맞춘 것이다.

## 원본과 다른 부분

### A. 범용 파라미터가 공격 상수로 축소됨

원본 `Decay` (`0x41E5A4`)는 각 항목의 상태를 `CAIParameter::GetDesireDecayValue` (`0x4F7490`)에 전달한다. 함수는 NPC의 파라미터를 조회하고 캐시한다. 원본에는 `Attack_DecayRatio`뿐 아니라 `UseSkill_DecayRatio`, `Follow_DecayRatio` 등의 이름도 존재한다.

Boost도 `CAIParameter::GetDesireBoostValue` (`0x4F7598`)를 통해 상태별 값을 읽는다. 예를 들어 `CNPC::AddAttackDesireEx`의 `0x485CF1..0x485D32`는 공격 Boost를 읽어 `MakeAttack`에 전달한다. 그 값은 Desire `+0x74`에 저장되고 `_BoostUp`의 `0x41DAAA..0x41DAB7`에서 실제 값에 더해진다. **따라서 Java가 `auxiliaryFloat` 또는 `effectPoint`로 부르는 이 위치는 이 경로에서 우선순위 Boost로 쓰인다는 근거가 있다.**

현재 Java는 `decayFor()` (`CDesirePQ.java:1673`)와 `boostFor()` (`:1679`)에서 공격만 6.6 / 300, 다른 상태는 0을 반환한다. 공격·스킬 payload에 저장된 float를 이 계산에 사용하지 않는다. 특정 warrior 기본형과 맞을 수는 있으나, NPC마다 다른 설정이나 스킬 우선순위까지 복원한 것은 아니다.

원본 타이머 `0x4ABFB0`은 마지막 감쇠 이후 **5000ms 초과**를 검사한다. Java는 **5000ms 이상**에서 한 번 감쇠한다. 5초 단위의 기본 정책은 가깝지만 경계까지 완전히 같지는 않다.

### B. 스킬 Desire 생성과 실행 옵션이 실제 게임 경로에서 분리됨

제공된 Java 1,670개 전체를 검색한 결과:

- `MakeUseSkill(`: `CDesireFactory.java:47`의 선언만 존재.
- `addUseSkillDesire(`: `CDesirePQ.java:550`의 선언과 `CDesireFactory.java:73`의 내부 위임만 존재.
- 공격 payload getter 네 개 `activeAttackMoveType/Flag/AuxFloat/AuxInt`: 선언만 존재.

일반 전투의 스킬 실행은 `CAttackAction.tryCombatAbility → CSkillAction.tryUse → L1MobSkillUse.skillUse` 경로다. 스킬을 못 쓰는 서버라는 뜻은 아니다. **현재 제공 코드에서는 일반 전투 스킬이 새 Desire API로 생성·선택되는 흐름으로 전환되어 있지 않다는 뜻이다.** 외부 JAR/플러그인의 호출 여부는 별도 확인이 필요하다.

또한 스킬 payload의 move type·flag·Boost·extra는 배열에 저장되지만 `CSkillAction.onDesireTick`은 skill ID와 target을 중심으로 `skillUseSelected()`를 호출한다. 나머지 옵션의 원본 실행 의미는 이어져 있지 않다.

### C. 스킬 중복 등록·실패 처리의 의미가 다름

원본 `operator==`의 상태 10 분기는 target `+0x18`과 skill `+0x50`를 비교한다. Java의 target+skill 키는 이 부분과 대응한다.

하지만 동일 키를 다시 등록할 때:

| 항목 | 원본 AddValue | Java addSkillValue (`:1053`) |
|---|---|---|
| Desire 값 | 합산·상한 적용 | 합산·상한 적용 |
| flag | 새 값으로 갱신 | 새 값으로 갱신 |
| move type / Boost / extra | 기존 값 보존 | 새 값으로 덮어씀 |

분리 프로브에서 동일 스킬을 `move=1, Boost=17, extra=41`로 등록한 뒤 `move=2, Boost=29, extra=99`로 다시 등록하면 Java는 뒤의 옵션으로 바뀌었다. 원본 `AddValue`의 중복 경로는 그렇게 갱신하지 않는다.

원본 타이머의 스킬 분기 `0x4ADE6C..0x4AE0EB`에는 실패 조건에 따라 Desire를 유지·Boost하거나, 실패 횟수 등 조건을 확인한 뒤 제거하는 경로가 있다. `use skill failed too many times, remove desire` 로그와 50회 초과 검사도 확인했다. Java `CSkillAction.onDesireTick`은 `skillUseSelected()`가 false여도 한 번의 시도 뒤 해당 스킬 Desire를 제거한다.

이는 스킬 Desire를 실제 이벤트에 연결하기 전에 정리할 차이다. 현재 일반 전투 경로에서 즉시 발생한다고 단정하는 운영 장애 판정은 아니다.

### D. 배회는 같은 동작으로 볼 수 없음

원본 `CNPC::dMoveAround` (`0x4959C0`)에는 영역 검사, 대기 카운터, 무작위 재평가, 속도·좌표 기반 목적지 계산 등의 분기가 있다. 예를 들어 `0x495C23..0x495CA1`은 카운터 범위를 확인하고, 조건에 따라 `rand()%400 + 1200`ms 대기 후 카운터를 증가시킨다. 영역·상태에 따라 이 대기를 건너뛰는 경로도 있다.

현재 `CMoveAroundAction`의 정책은 첫 이동을 즉시 시작하고 3~6칸 구간 종료 후 6000ms 쉬는 방식이다. 막히면 300ms 재시도, 통로·근접 자리 예약을 활용한 공격 복귀 등 L1용 정책도 추가돼 있다. 원본과 닮은 외형을 만들 수는 있지만 이 EXE의 분기·타이밍을 그대로 재현한 구현은 아니다. L1 타일 이동과 L2 연속 좌표 이동의 차이도 있어 원본 수치를 그대로 복사하는 것이 자동으로 개선을 뜻하지는 않는다.

### E. 상태 선언과 전체 행동 지원 범위가 다름

원본에서 직접 확인한 뒤쪽 상태 번호:

| 원본 상태 | 원본 factory / VA | 현재 CDesirePQ |
|---|---|---|
| 12 EffectAction | `MakeEffectAction`, `0x41B6E0` | 상수 존재, 별도 Desire dispatch 미확인 |
| 13 EffectAction2 | `MakeEffectAction2`, `0x41B7C8` | 해당 상수 없음 |
| 14 PetDefault | `MakePetDefault`, `0x41C098` | `PS_PET_DEFAULT`는 13으로 선언됨 |
| 15 MoveToTarget | `MakeMoveToTarget`, `0x41C208` | 별도의 L1 추격 Action은 있으나 이 범용 Desire 상수·경로는 없음 |
| 16 MoveSuperPoint | `MakeMoveSuperPoint`, `0x41C2F0` | 동등한 범용 Desire 경로 미확인 |
| 17 MoveFreeway | `MakeMoveFreeway`, `0x41C3C8` | 동등한 범용 Desire 경로 미확인 |
| 18 MoveFormation | `MakeMoveFormation`, `0x41C4A0` | 동등한 범용 Desire 경로 미확인 |

`PS_PET_DEFAULT=13`은 이 EXE 기준 enum 불일치다. 현재 핵심 dispatch가 ATTACK / MOVE_AROUND / USE_SKILL 중심이고 PetDefault를 이 번호로 실행하는 경로를 확인하지 못했으므로, 당장 펫 장애라고 단정하지 않는다. 원본 호환 API나 추가 행동을 붙일 때 수정할 사항이다. `CCreatureController.ACTION_*`는 별도 L1 실행 ID이므로 이 표의 PS 번호와 비교하면 안 된다.

원본 factory는 추적용 함수명에서 오버로드 포함 21개, 이름 기준 19종을 확인했다. 이 수를 현재 구현의 완성도 분모로 삼지는 않았다. Java의 레거시 follow·item pickup 같은 기능을 factory 부재만으로 미구현 처리하면 부정확하기 때문이다.

## EXE만으로 확정할 수 없는 부분

원본은 `..\script\ai.obj`를 읽으며, `CScriptEngine::RunNPCScript` (`0x501DE0`), reloadable event handler, NASC 버전 검사 등을 포함한다. `CDesirePQ::SendNoDesireEvent` (`0x41C620`)도 고정 배회 항목을 넣는 대신 NoDesire 이벤트를 만들어 NPC handler로 보낸다.

현재 Java는 NoDesire에서 MOVE_AROUND를 직접 보충하고, SEE/피격/동족 지원을 고정된 Java 규칙으로 처리한다. 원본의 범용 스크립트 런타임과는 범위가 다르다. 현재 서버에 별도 퀘스트·보스·이벤트 기능이 없다는 뜻은 아니다.

`6.6`, `300`, 시야 공격 `200`, 피격 `damage/maxHP*2000`, 동족 지원 `*600`처럼 Java 주석이 warrior 규칙이라고 설명한 수치는 **이 EXE만으로 해당 NPC 스크립트의 설정값까지 검증할 수 없다.** 더 정확한 개별 NPC 비교에는 이 EXE와 버전이 맞는 `ai.obj`, `npcdata`와 관련 스킬·영역 정의가 필요하다. 현재 업로드에는 이 EXE와 함께 제공되지 않았다.

## 이번 검증 기록

- 배포 v5 전체 Java 1,670개는 앞선 작업에서 컴파일 성공했다. 이번에는 그 빌드 manifest와 현재 소스의 SHA-256을 전부 대조해 변경이 없음을 확인했다. 전체 재컴파일을 새로 수행했다고 표현하지 않는다.
- 그 빌드의 **실제 `CDesirePQ.class`**를 사용해 게임/DB 서버를 시작하지 않는 분리 프로브를 실행했다.
- 10개 관찰 확인: 중복 합산, 합산 상한, 별도 즉시 슬롯, 61개 공격 키 보존, 감쇠 전후, 서로 다른 skill 키, 스킬 중복 옵션 변경, 고정 Boost, enum 값. 이 중에는 원본과의 불일치 확인도 포함되므로 “원본 정합성 테스트 10개 통과”라는 뜻이 아니다.
- 원본 EXE 실행, ai.obj 로딩, 동일 사건을 양쪽 서버에 재생한 비교, 실제 클라이언트 전투·보스 통합, 대규모 NPC 부하는 수행하지 않았다.

## 다음 작업의 우선순위

1. 현재 일반 전투가 계속 L1 스킬 테이블을 사용할지, 스킬 선택도 Desire로 통합할지 결정하고 실제 호출 경로를 연결한다.
2. 중복 스킬 payload 보존과 실패 시 재시도/종료 계약을 정리한다.
3. NPC·행동별 감쇠/Boost 값을 지원하고 저장한 옵션을 실제 선택·실행까지 연결한다.
4. 필요한 행동부터 확장하고 enum을 정리한다. 모든 L2 행동을 L1에 이식할 필요가 있는지는 따로 판단한다.
5. 동일 버전 ai.obj/npcdata가 있으면 공격 전환·스킬 실패·배회 사건을 입력으로 삼아 결정 순서와 타이밍을 비교한다.

현재 평가를 가장 잘 표현하면 **“Desire 핵심과 기본 몬스터 전투는 연결된 L1 이식 구현, 범용 L2NPC 엔진의 전체 복원은 진행 중”**이다.
