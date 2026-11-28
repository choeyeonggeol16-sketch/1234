# RKern 변신 시스템 전면 교체 지시서 — NC OFF CTransform 구조

작성일: 2026-11-29 (KST)
RKern 확인 기준: [main 98ef99ee](https://github.com/choeyeonggeol16-sketch/1234/commit/98ef99ee8af13210b5d193dbf15f67a5ceaa7f37)
NC 비교 기준: [Open_LU4_L2OFF abbb2e5](https://github.com/luannbr/Open_LU4_L2OFF/tree/abbb2e5f474d40cf12539a8e97fddc52474ed95e) (공개 OFF 복원 소스)
이번 커밋은 구현 지시서만 추가한다. 실제 수정은 최신 작업 브랜치 기준으로 진행하며 기존 스킬 습득 개편과 다른 변경을 되돌리지 않는다.

## 1. 필수 구조 및 이름

**기존 PolyTable/L1PolyMorph 변신 관리 구조를 전면 교체한다. 변신 전용 패키지를 신설하고, 변신 관련 클래스명은 확인된 NC 이름으로 통일한다. 이름만 바꾸거나 호환 래퍼를 남기는 작업은 완료로 인정하지 않는다.**

Java 패키지 제안: `l1j.server.server.model.character.transform` (현재 model.character 하위 구조를 기준으로 선정. 기존 동일 책임 패키지가 있다면 중복 생성하지 말고 이 위치에 통합).

```text
model/character/transform/
    CTransformDB.java
    CTransform.java
    CTransformCommonData.java
    CTransformCombatData.java
    CTransformForCombat.java
    CTransformForNonCombat.java
    CTransformForModeChange.java
    TransformType.java
```

위 명칭은 NC Transform.h에서 확인한 이름이다. L1의 실제 기존 변신 분류와 책임을 확인하고 사용하는 클래스만 구현한다. 공통/전투 데이터의 분리 구조는 유지하되 의미 없는 빈 클래스 또는 중복 wrapper 금지. NC의 CTransformForRiding, CTransformForCursed, CTransformForFlying, CTransformForPureStat도 실제 L1의 동등한 역할이 있을 경우 같은 이름을 사용한다. 승마, 비행, L2 전용 전투 능력치 등 기존에 없는 기능을 이관 명목으로 새로 만들지 않는다. 기존 PolyTable/L1PolyMorph 및 legacy 변신 전용 클래스들은 전 호출 이관 후 삭제한다. 근거 없는 임의 이름의 Manager/Service/Engine 추가 금지.

- CTransformDB: 전역 공유 변신 정의, ID/이름/기존 조건별 인덱스, 로딩/리로드. 기존 polymorph 관련 DB 데이터 보존.
- CTransform: 변신 정의 및 CanTransform, SetTransformData, CanUseItem, 행동 제한, 적용/해제 공통 정책. 캐릭터별 가변 상태를 공유 정의에 저장하지 않는다.
- CTransformCommonData: 기존 L1의 변신 외형, 장비 허용 및 행동 관련 공통 정의.
- CTransformCombatData: 기존 L1 변신에 실제 존재하는 능력치/속도 보정 자료만 관리. 기존 공식과 데이터 유지.
- User 역할(현 L1PcInstance와 기존 공통 CCreature/L1Character 소유 관계): 현재 변신 상태 및 Transform/CancelTransform 등 단일 실행 진입점. 이름 때문에 별도 User 클래스나 새로운 상속 계층을 추가하지 않는다.
- NPC 변신은 기존 몬스터의 공통 상태/타이머 경로로 통합하고 NC의 플레이어 전용 분기를 NPC에 강제하지 않는다.

## 2. NC 원본 구조 근거

### 2.1 정의 및 조회

[Transform.h L38–120](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Transform.h#L38-L120)
[Transform.h L129–293](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Transform.h#L129-L293)

NC는 CTransformDB, CTransform, CTransformCommonData, CTransformCombatData와 변신 타입별 클래스를 구분한다. 전역 데이터는 각 변신 정의에 연결하며 CTransformDB는 transform ID와 성별로 조회한다. L1은 성별에 따른 정의가 실제 필요한지 확인하여 기존 ID 체계를 보존한다. NC map의 C++ 구현을 Java 박싱 TreeMap으로 직역하지 않는다.

[Transform.cpp L293–325](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Transform.cpp#L293-L325): transform.txt 로딩 및 PostLoad.
[Transform.cpp L448–477](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Transform.cpp#L448-L477): 정의 등록/조회와 준비된 행동 목록 송신.
[Transform.cpp L590–625](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Transform.cpp#L590-L625): 행동 검증, 행동 목록 선조립 및 아이템 사용 가능 여부.

NC CTransformDB::Find는 map의 operator[]를 사용한다. Java에서는 미등록 ID 조회가 자료구조를 변경하지 않는 get을 사용하도록 적응한다. L2 행동 목록 패킷을 복사하지 말고 기존 L1 클라이언트가 필요로 하는 데이터만 준비한다.

### 2.2 변신 적용과 해제

[Transform.cpp L367–385](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Transform.cpp#L367-L385): CanTransform은 위치/공간 및 수중 조건을 검사한다.
[Transform.cpp L479–542](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Transform.cpp#L479-L542): SetTransformData는 _SetCommonData 성공 후 _SetCombatData를 실행한다. 공통 데이터는 충돌 검사 성공 후 기본 공격 정보 등을 변경하며 전투형은 별도의 전투 데이터 적용을 수행한다.

[User.h L2708–2713](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/User.h#L2708-L2713)에서 실제 확인한 이름:
- Transform
- CancelTransform
- _OnTransform
- _OnCancelTransform
- PumpStartTransformSkill
- PumpEndTransformSkill

[User.h L3836](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/User.h#L3836): m_TransformLock.
[User.h L4099](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/User.h#L4099): m_nTransformId.
[SkillFxFuncPump.cpp L2710–2731](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/SkillFxFuncPump.cpp#L2710-L2731): CSkillEffect_p_transform의 PumpStart는 PC 확인 후 User::Transform을 호출하고 PumpEnd는 대상 변신 ID를 넘겨 CancelTransform을 호출한다. 따라서 적용/해제 책임을 User 진입점에 집중한다.

User::Transform/CancelTransform의 내부 전체 구현과 락 범위는 이번 조사에서 확보하지 못했다. 아래 L1 실행 정책은 안전한 이관 지시이며 NC의 미확인 내부 순서를 확정 원본으로 주장하지 않는다.

## 3. RKern 실제 문제

[L1PolyMorph.java L214–333](https://github.com/choeyeonggeol16-sketch/1234/blob/98ef99ee8af13210b5d193dbf15f67a5ceaa7f37/rkern/src/l1j/server/server/model/L1PolyMorph.java#L214-L333)
[L1PolyMorph.java L334–443](https://github.com/choeyeonggeol16-sketch/1234/blob/98ef99ee8af13210b5d193dbf15f67a5ceaa7f37/rkern/src/l1j/server/server/model/L1PolyMorph.java#L334-L443)
[PolyTable.java](https://github.com/choeyeonggeol16-sketch/1234/blob/98ef99ee8af13210b5d193dbf15f67a5ceaa7f37/rkern/src/l1j/server/server/datatables/PolyTable.java)

1. doPoly는 일부 기본 조건 검사 후 기존 SHAPE_CHANGE 타이머를 해제하고 새 효과를 등록한 다음 변신 정의를 검증한다. 이후 정의 누락/유효하지 않은 ID/옵션 실패로 반환하면 기존 상태에 영향을 줄 수 있다. 최종 변신 ID와 모든 선행 조건 검증이 먼저여야 한다.
2. PC에서는 timeSecs=-1로 강제하는 현행 정책이 있다. 기존 영구/무제한 변신의 실제 의미와 타이머 API의 음수 처리부터 확인한다. 리팩토링 과정에서 일방적으로 유한 지속시간으로 변경하지 않는다.
3. poly null 검사에는 특정 PC 플래그 조건이 걸려 있다. 특수 계정/봇/GM/옵션 해석 경로를 구분하고 null 및 최종 ID 검증을 보완한다.
4. 변신 반지 POLY_RING_MASTER의 HP +200 및 여섯 스탯 +1/해제 -1 코드가 여러 곳에 중복된다. 기존 효과 등록/취소 callback까지 조사하여 중복 적용·차감·해제 위험을 제거한다.
5. doPolyPraivateShop은 호출마다 8개 int 배열을 생성하고 polyIndex-1을 직접 인덱싱한다. 전역 공유 정의/상수로 이동하고 배열 범위를 검증한다.
6. undoPoly에서 용기사 인벤토리 전체 순회, items.getItem().setType1(83), 현재 무기 해제/재착용이 반복된다. getItem이 공유 템플릿을 반환하는지 반드시 확인하고, 공유 정의를 캐릭터 변신 해제에서 수정하지 않는다. 실제 장비/무기 상태의 인스턴스별 파생 값으로 처리하되 기존 type1 의미와 장착 효과를 보존한다.
7. 일반 변신, 개인상점 변신, 자동혈맹 가입 외형 복원, NPC 변신이 별도 static 경로에 분산되어 있다. 일부는 단순 클래스 외형으로 복귀하므로 기존 중첩 변신/장비 변신 복원 정책을 조사해야 한다. 기존 정책과 다른 우선순위를 임의로 신설하지 않는다.
8. 일반 PC 경로에서는 외형 변경 및 패킷 전송 후 takeoffEquip을 수행한다. 장비 해제가 실패할 수 있는지, 패킷과 실제 장비/능력치 상태가 불일치하는지 조사하여 상태 확정 후 알림 구조로 정리한다.

위는 기준 main에서 직접 확인한 내용이다. 실제 최신 별도 작업 브랜치에서 이미 수정된 부분은 중복 수정하지 않는다.

## 4. 구현 지시

### P1. 정의 이관 및 패키지 정리
- 최신 전체 SRC에서 PolyTable, L1PolyMorph, 변신 관련 데이터 모델, 아이템 옵션, 인벤토리 장비 판정, NPC 및 클라이언트 패킷 참조를 찾는다.
- CTransformDB에 기존 polymorph 데이터 로딩/이벤트/랭킹 변신/이름·ID 조회를 이관한다. 원래 데이터와 조건을 빠짐없이 유지한다. 스프라이트 ID가 희소하면 기존 primitive TIntObjectMap 등을 활용한다. 조회 시 new/박싱/전체 목록 순회 금지.
- CTransform 및 실제 필요한 NC 명칭의 데이터/타입 클래스를 전용 transform 패키지에 구성한다. 캐릭터별 상태는 소유 캐릭터에 두고 공유 정의는 읽기 전용으로 유지한다. 데이터 리로드 실패 시 이전 유효 스냅샷 유지.
- 기존 L1의 sprite/action/equipment 규칙을 유지한다. L2의 3D 충돌·비행·CP 등 규칙은 가져오지 않는다. 위치/점유는 기존 L1 2D 규칙을 사용한다.

### P2. 적용 및 해제 단일화
- 기존 소유 캐릭터 구조와 CIO 실행 순서를 이용하여 Transform, CancelTransform을 단일 진입점으로 만든다. _OnTransform/_OnCancelTransform 및 PumpStartTransformSkill/PumpEndTransformSkill은 기존 실제 역할이 대응되는 경우 같은 이름으로 사용한다.
- 권장 L1 적응 순서: 요청 및 상태 검증 → 공유 정의 조회 → 최종 ID/조건/장비 가능 여부 검증 → 필요한 기존 타이머/트랜잭션 admission 확인 → 소유 실행 경로에서 효과·외형·장비·스탯 변경 → 상태 확정 → 클라이언트 패킷/방송. 처리 실패/거부 시 기존 정상 변신을 보존한다.
- 변신 해제는 대상 변신 및 generation/기존 효과 식별자를 확인한다. 이전 변신의 만료 callback이 새 변신을 취소하지 않도록 한다. 동일 변신 반복, 변신 중 재변신, 중복 해제, 사망/부활, 로그아웃/재접속에서 정확히 한 번 적용·해제.
- 기존 CIO 타이밍 휠과 스킬 효과 lifecycle을 사용하고 새 타이머 스레드·큐를 만들지 않는다. PC/NPC 기존 지속시간과 타이머 거부 시 동작을 보존한다.
- 변신 상태, 현재 스프라이트, 영구 스킬 보유 상태, 활성 버프 상태를 혼용하지 않는다. 스킬 습득 개편 작업과 공통 API가 겹치면 최신 변경을 확인하고 충돌 없이 통합한다.
- 반지 보정은 기존 스탯/효과 소유 구조에서 한 번만 반영·해제한다. HP/MP와 능력치 표시 갱신은 최종 상태 기준으로 수행한다.

### P3. 장비 및 외형 일관성
- 장비 가능 여부와 무기 공격 거리/형태는 현재 착용 슬롯 및 변신 정의를 이용한다. 단순 변신/해제에서 인벤토리 전체 순회 금지. 과거 전체 순회에서 처리하던 미장착 아이템의 type1 의미가 있는지 확인하여 동등한 인스턴스 계산으로 이관한다.
- 공유 아이템 템플릿에 캐릭터별 변신 값을 쓰지 않는다. 용기사 무기 판정, 장착 효과, 현재 무기 및 클라이언트 외형을 동일 상태로 유지한다. 모든 아이템 인벤토리의 구조를 갈아엎지 말고 변신 관련 호출부만 수정한다.
- 기존 장비 트랜잭션/CacheD 확정 정책을 유지한다. 변신이 실제 아이템 소비를 수반하는 경우에는 기존 receipt와 결합된 성공/실패 순서를 보존한다. 장비 저장 및 경제 작업 실패 후 변신 성공 메시지를 내보내지 않는다.
- 개인상점/자동혈맹 가입/장비/마법서/NPC/일반 변신의 현재 정책을 표로 정리하고 적용·해제 공통 경로에 연결한다. 상점에서 나올 때의 복원 외형 및 기존 상태 우선순위를 명시한다.
- SC_ChangeShape, SC_CharVisualUpdate, SC_SkillIconGFX, SC_PacketBox, HP/MP/스탯 패킷은 기존 L1 프로토콜과 투명/GM/ghost 방송 필터를 유지한다. 같은 최종 상태의 packed 값 재사용은 기존 송신 API로만 수행한다. 공통 네트워크 및 브로드캐스트 코어 수정 금지.

### P4. 전 호출부 이관과 삭제
```sh
rg -n 'L1PolyMorph|PolyTable|doPoly|undoPoly|MagicBookPoly|isEquipableWeapon|isEquipableArmor|isMatchCause|SHAPE_CHANGE|POLY_RING_MASTER|setCurrentSprite|PolyOption|getPolyIdByEquip' rkern
```
각 호출의 실제 역할을 확인한다. 변신과 무관한 일반 sprite 설정 및 스킬 효과 API는 무차별 변경하지 않는다. 로그인 복원, 자동 변신, 아이템 사용, 스킬 시전/해제, 맵 이동, 상점, 사망/부활, GM, NPC/보스, 직업 변경, 장비 조건, 이벤트 변신 전부 검사한다. 기존 데이터 로더와 모든 참조를 새 패키지로 이관하고 L1PolyMorph/PolyTable 제거. 최종 결과에 예전 이름의 변신 전용 클래스, 구형 호환 wrapper, 중복 구현을 남기지 않는다.

## 5. 검증 및 완료 조건

- Java 21 전체 의존성 타입 컴파일, 실제 테스트.
- 잘못된 ID/옵션/상점 index/제한 맵/변신 불가 상태에서 타이머·장비·능력치·외형이 변하지 않는지.
- 무제한 및 유한 지속시간, 재변신, 만료와 재변신 경쟁, 타이머 등록 실패, 이전 callback, 사망/부활, 로그아웃/재접속, 동시 장비 해제, 반지 교체 및 해제.
- 반지 반복 적용/해제 1,000회 후 HP와 여섯 스탯 누적 오차 0. 기본값 변경, 다른 장비/버프 동시 변경 상태에서도 실제 적용된 보정만 해제.
- 용기사의 장착/미장착 무기, 동일 아이템 정의를 사용하는 다른 PC에 영향 없음, 공유 템플릿 불변성, 인벤토리 큰 경우에도 정상 동작. 장비/패킷/스탯 결과 비교.
- 개인상점/자동혈맹 가입/NPC/장비 변신 기존 동작, 중첩 시 복원 우선순위, 투명/GM/ghost의 방송 제한, 사망 상태의 전송 정책 비교.
- 5,000 PC 목표 기준 CPU, 할당률, retained heap, 타이머 수, 장비 탐색 횟수 및 p99 비교. 실측하지 않은 결과를 수치로 제시하지 않는다.
- 모든 변신 관련 클래스명은 실제 NC 명칭을 사용하고 전용 패키지에 집중되었는지 확인. 구형 클래스 참조 0. 미사용 클래스를 단지 NC에 존재한다는 이유로 추가하지 않는다.
- 범위 제한: 변신 시스템과 직접 호출부만 수정. 스킬 습득 개편, 일반 전투 공식, NPC AI, 네트워크, CIO 및 CacheD 공통 구조에는 임의 변경 금지.

## 6. 조사 한계

NC CTransform 계열 정의/적용 메서드와 스킬 PumpStart/PumpEnd, User 선언은 직접 확인했다. User 내부 변신 실행 전체, RKern 최신 별도 브랜치 및 전체 호출 그래프, 실제 DB/인게임 테스트는 미확인이다. 구현자는 최신 전체 checkout에서 이를 검증한다. 본 보고서는 구조 교체 지시서이며 생산 코드 변경이나 테스트 완료를 주장하지 않는다.
