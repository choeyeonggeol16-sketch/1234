# NC L2Server 장비 슬롯·세트 아이템 복원 지시서 (RKern)

2026-11-20 — Codex 구현용 보고서

## 0. 사용자 지정 범위 (필수)

이번 작업은 **L1EquipmentSlot 및 L1ArmorSet의 장착·탈착, 장비 슬롯 관리, 장비 착용 능력치 옵션, 세트 아이템 적용·해제**를 NC L2Server 구조에 맞게 개선한다.

- **무기 발동 마법, 마법검 처리, 확률 발동 스킬은 전부 제외한다.** L1WeaponSkill, WeaponSkillTable, CAttackAction의 무기 발동 및 대미지 계산 경로는 수정하지 않는다. ItemOptionHandler/ItemSkillHelper의 발동 처리도 이번에 구현하지 않는다.
- 무기 자체의 장착·탈착, 기본 옵션, 강화로 올라가는 상시 장착 보정은 대상이다. 공격 시 발동하는 옵션과 혼동하지 않는다.
- 아이템 표기/툴팁/패킷 조립의 전면 리팩토링은 별도 작업이다. 기존 ItemPresentatorFactory, SC_ItemStatus, SC_EquipmentWindow 및 장비 상태 통지 동작을 보존한다. 장착 효과와 이미 공유되는 정의는 재사용한다.
- 기존 L1PcInventory, CIO/CreatureController, NpcServer, Timing Wheel, CacheD, DB, 네트워크, 스킬 등록·제거 구조는 유지한다. 장착·탈착 호출부에서 필요한 최소 연결 변경 외 범위 확대 금지. 사용자 요청 없는 새 스레드·큐·캐시·전역 실행 구조 금지.
- 실제 NC 클래스명과 함수명을 가능한 한 우선 사용한다. 이미 동일 책임의 클래스가 있으면 중복 생성하지 않고 해당 구현을 정리하거나 이름을 이관한다. 모든 아이템별 클래스를 생성하거나 기존 구현을 단순 래핑하는 가짜 계층을 남기지 않는다.
- 기존 L1 장비 수치, 장착 조건, 강화 조건, 슬롯 수/양손/이도류/반지 규칙, 세트 구성, 클래스별 규칙, 상태 효과/아이콘/패킷 순서 보존. L2의 게임 정책을 L1에 그대로 덮어쓰지 않는다.
- 구현 완료 후 별도 보고와 회귀 검증. 본 커밋은 구현 지시서이며 생산 소스 변경 없음.

RKern 기준: 브랜치 `rkern/npc-action-followup-20261120`, 문서 작성 직전 HEAD `fdcef1471bb18fccba5295ad70b0dd18640162da`; 생산 코드 조사 기준 `546983b5fbf735d767e7b2730cb2214cef603c52`. 구현 시점 최신 HEAD와 스킬 이관의 선행 변경을 보존할 것. 기존 [스킬 효과 엔진 보고서](NC_L2Server_SkillEffect_Pump_Lifecycle_RKern_20261120.md) 참고. 장비 작업을 명목으로 스킬 엔진 이관까지 동시에 진행하지 않는다.

## 1. NC 바이너리 근거

분석 대상: L2Server(10).exe, PE32+ x86-64, 13,215,232 bytes, image base 0x400000, SHA-256 9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588. 이전 대화에서 확보한 원본 정적 역분석 결과를 정리한 것이다. 주소는 이 바이너리의 VA이다. 본 보고서 작성 단계에서 다시 역어셈블하거나 실행 검증한 것은 아니다.

### 실제 클래스 및 함수 이름

| NC 실제 클래스 | 실제 함수/역할 | VA (확보된 경우) |
|---|---|---|
| User | OnUseItemPacket | 0x973FF8 |
| User | UseItem | 0x9B2370 |
| CCreature | UseItem | 0x5972C0 |
| User | EquipItem | 0x9B0048 |
| CCreature | EquipItem | 0x5A2840 |
| User | _OnEquipItem | 0x9C20E4 |
| User | OnUnequipItemPacket | 0x9B2BB0 |
| User | UnequipItem | 0x9B1470 |
| CCreature | UnequipItem | 0x5970D8 |
| User | _OnUnequipItem | 0x9C1A1C |
| CCreature | ValidateParameters | 0x590864 |
| CCreature | ValidateBaseData | 0x576424 |
| CCreature | ValidateBaseAttributeData | 0x57691C |
| CCreature | ValidateAllOnWeapon | 0x5905F4 |
| CCreature | ValidateAllOnArmors | 0x59079C |
| CCreature | ValidateSkillMod | 0x58BA30 |
| CCreature | ValidateAllOnSIM | 0x572A38 |
| CItem | ApplyStatBonus | 내부 call sites 0x70B882, 0x70B89A |
| CItem | CheckItemEquipCondition | 장착 조건 |
| CItemConstInfo | CanEquip | 정의상 조건 |
| CItemEquipCondition | CanEquip | 가상 함수 +0x08 호출 0x71C1D2 |
| CObjectDB | IsItSet | 세트 판정 |
| CSetItem | GetSlotSetItemId | 슬롯의 세트 정의 |
| CSetItem | SlotToSetItemClassID | 슬롯→아이템 클래스 ID 대응 |
| CCreature | ValidateSetItem | 세트 검증 |
| CCreature | _ValidateSetItemOnEquipItem | 장착후 세트 검증 |
| CCreature | SetItemSkillEnDisable | 세트 변경 |
| CCreature | EnableSetItemSkill | 세트 스킬 활성 |
| CCreature | DisableSetItemSkill | 세트 스킬 비활성 |
| User | ValidateItemSkills | 현재 장비의 부여 스킬 검증 |
| CEnchantOptionDB | GetEnchantOption, SetEnchantOption | 강화 옵션 조회/연결 |
| CItemEnchantOption | GetEnchantOption | 단계별 옵션 조회 |
| CEnchantOption | 강화 옵션 정의 | 상시 보정만 이번 범위 |

SaveUnEquipedItemAndSetNull은 교체 대상 슬롯의 아이템 인덱스를 임시 맵에 보관하고 슬롯을 -1로 초기화하는 함수로 확인되었다. 원본 철자를 그대로 적는다. 함수명은 기존 분석에서 식별된 이름이며 원본 C++ 전체 선언, 인자 타입, 가시성을 모두 복원했다는 의미가 아니다.

## 2. NC 실제 장착·탈착 구조

UseItem -> EquipItem 경로에서 장착 요청 및 플레이어 전용 검증과 공통 장비 슬롯 변경을 분리한다.

CCreature::EquipItem 내부:
1. 기존 슬롯/교체 대상 정리.
2. 새 아이템 슬롯 등록.
3. 실제 교체된 아이템에 대한 _OnUnequipItem 후처리.
4. 신규 장비에 대한 _OnEquipItem 후처리.
5. _ValidateSetItemOnEquipItem을 통한 세트 검증.

이 후처리 순서를 0x5A3741, 0x5A3772, 0x5A3789에서 확인한 이전 분석 결과가 있다. 모든 착용 분기가 반드시 같은 순서를 거치는지는 아직 전수 확인하지 않았다.

NC 장비 슬롯은 sharedData + 0xB8 + slot*4의 아이템 인덱스로 접근하며 분석된 UseItem/ValidateBaseData 분기에 29개 슬롯을 순회하는 경로가 존재한다. RKern에 NC의 29개 슬롯을 복제하지 않는다. 현재 L1 슬롯 규칙과 실제 장착 장비 상태를 사용한다.

장착 조건: User::CanEquip -> CItem::CheckItemEquipCondition -> CItemConstInfo::CanEquip -> CItemEquipCondition 조건 가상 호출. 이전 분석에는 23개 파생 조건 클래스가 식별되었다. NC의 L2 전용 조건을 복제하는 것이 아니라 현재 L1의 클래스/레벨/슬롯/기타 장착 조건을 기존 검사부에 통합해 재사용한다. 이미 검증 기능이 존재하면 그 클래스를 유지한다.

## 3. NC 장비 능력치 재계산

CCreature::ValidateBaseData 안에서:
- 0x57661C: CItem::ApplyStatBonus 호출
- 0x576621: add rbx,4
- 0x576625: cmp rbx,0x74
- 0x576629: jb 0x576579
인덱스 stride 4, 범위 0x74(116 bytes)의 장착 슬롯 순회 경로다.

CItem::ApplyStatBonus는 기본 보정과 연결된 강화 보정에 대해 같은 합산 경로(호출 지점 0x70B882/0x70B89A)를 활용한다. 강화 단계 옵션 포인터 CItem+0x138의 재사용 경로도 이전 분석에서 확인. 세트 보정도 ValidateBaseData의 별도 합산 경로에 들어간다.

ValidateParameters에서 관찰한 순서:
ValidateBaseData → ValidateBaseAttributeData → ValidateLevel → ValidateOrgSpeed → ValidateAllOnMotionEx → ValidateOrgHPMP → ValidateAllOnWeapon → ValidateAllOnArmors → ValidateItemSkills → ValidateAllOnWeapon → ValidateAllOnArmors → ValidateAllOnSIM → ValidateCarryWeight.

이는 NC의 상태 기반 보정 합산 설계 근거다. RKern에서 이 전체 함수를 새로 복제하거나 무조건 전체 장비/버프/캐릭터 스탯을 재계산하라는 의미는 아니다. **기존 능력치를 다른 공급원(레벨/장비/스킬/아이템 버프 등)과 구분할 수 있는지 먼저 조사한다.** 이번 작업에서는 장비와 세트 효과에 한해 기존 API로 안전하게 적용할 수 있는 공통 ApplyStatBonus 및 ValidateSetItem을 구현한다. 장비 전용 합산값의 차이 반영이 가능한 경우 이전 적용량과 새 합산량의 차이를 적용하고, 그렇지 않은 효과는 기존 정확한 적용·해제 경로를 공통화한다. 전역 스탯 초기화 금지. NC의 전체 재계산을 구현했다고 과장하지 않는다.

장착 HP 증가량 재계산 과정에서 기존 HP가 일시적으로 줄어 클램프되는 문제, WIS/MR 재계산, 클래스별 부여 효과, 강화/축복/랜덤 옵션 변동 후 원복, 장착 중 아이템 변경을 검사한다.

## 4. NC 세트 아이템 처리

CObjectDB::IsItSet / CSetItem::GetSlotSetItemId / CSetItem::SlotToSetItemClassID를 통해 슬롯별 구성 조건을 검증한다.
CCreature::ValidateSetItem 및 SetItemSkillEnDisable에서 기존/새 세트 상태를 비교한다.
EnableSetItemSkill / DisableSetItemSkill을 통해 세트 부여 스킬을 관리하고, 수치 보정은 ValidateBaseData에서 합산한다.

User::ValidateItemSkills에서 관찰한 호출:
- 0x9C255A / 0x9C2570 GetSkillsFromItem
- 0x9C25B5 GetOptionSkillsFromItem
- 0x9C262E DeleteAcquireSkill
- 0x9C26AD AcquireSkillByItem
- 0x9C280D ValidateParameters (변동 분기)

이번 범위에서는 **장착 자체가 부여하는 세트 효과 및 상시 장비 스킬의 기존 동작만 보존/통합**한다. 기존 스킬 보유 및 등록/삭제 API를 사용하며 버프, 학습 스킬, 다른 세트와의 공유 등 기존 효과 공급원을 임의 제거하지 않는다. 무기 확률 발동 스킬 실행은 제외한다. 현재 L1ArmorSet의 변신·에바 아이콘·세트 아이템 표기·스탯·속성 보정 등 기존 비수치 효과도 동작을 유지한다.

## 5. RKern 현재 확인된 구현 및 문제

생산 경로: rkern/src/l1j/server/server/

| 기존 파일 | 확인 사항 |
|---|---|
| model/L1EquipmentSlot.java | 12,513줄. set/remove, setWeapon/removeWeapon, setArmor/removeArmor에 장착·해제 보정 코드 대량 중복. normalizeWeaponSlots, addWeaponToSlots, removeWeaponFromSlots, canUseSecondWeaponBySlayer 등 기존 슬롯 구조 존재 |
| model/L1ArmorSet.java | 362줄. L1ArmorSetImpl, giveEffect/cancelEffect, isValid, isPartOfSet, isEquippedRingOfArmorSet. 스탯, 속성, 변신, 에바 아이콘, 아이템 표기 관련 효과 존재 |
| datatables/ArmorSetTable.java, templates/L1ArmorSets.java | 기존 세트 정의 로드/자료구조 확인 후 재사용 |
| model/L1PcInventory.java | 기존 인벤토리 장착 상태 및 변경 경로, 수명/트랜잭션 경계 유지 |
| model/item/ItemStat.java | 보정 적용 및 툴팁 opcode 연결. 공통 apply(pc,value) 재사용 검토 |
| model/item/ItemEffectRegistry.java | 기존 아이템 ID/클래스/인챈트 정의 기반 적용·해제. 중복 기존 스탯 처리 여부 확인 |
| model/item/function/ItemBonusHooks.java | 장착/탈착 후처리 유지 |
| MJTemplate/ItemPresentator/ItemPresentatorFactory.java | 기존 do_equip/do_unequip 유지 |
| model/item/function/EquipmentItemActions.java | 기존 장착 요청 및 조건 검사 유지 |
| model/L1WeaponSkill.java | 1,497줄. **완전 제외** |

L1EquipmentSlot.set/remove의 실제 공통 처리: 아인 소모량, HP/MP, STR/CON/DEX/INT/WIS/CHA, 크리티컬, MR/SP, 헤이스트 장비 카운터, 마법 투구 처리, 무기/방어구 분기, ItemEffectRegistry.Def.apply(+1/-1), ItemBonusHooks.onEquipChanged, ItemPresentatorFactory.do_equip/do_unequip. 이를 각각 중복 적용하지 않도록 단일 책임으로 정리한다.

L1ArmorSet의 giveEffect/cancelEffect에서 실제 AC, HP/MP 및 여러 보정의 대칭 가감이 관찰되었다. setArmor의 armorSet.giveEffect 호출 및 기존 발동 조건을 이관할 때 기존 세트 중복 처리/반지 조건/부분 해제 의미를 보존한다. 단순 중복 제거 과정에서 기존 적용값을 다른 시점의 수치로 다시 계산하지 않는다.

## 6. 구현 클래스 및 함수 이름 (NC 명칭 우선)

| 기존 책임 | 이관 시 우선할 NC 명칭 | 구현 지시 |
|---|---|---|
| L1EquipmentSlot.set/remove | EquipItem / UnequipItem | 기존 슬롯 소유 클래스에서 장착 상태 관리. 새 CCreature 클래스 추가 금지. 실제 이름 이관 시 호출부 일괄 정리 |
| set/remove의 능력치 공통부 | ApplyStatBonus | 기본·인챈트 등 공통 보정 정의를 공유. 기존 ItemStat/ItemEffectRegistry를 이용하고 새 레지스트리 중복 생성 금지 |
| 플레이어 전용 후처리 | _OnEquipItem / _OnUnequipItem | 실제 슬롯·효과 처리와 분리하되 기존 패킷 및 후처리 순서 유지 |
| 장비 조건 | CanEquip / CheckItemEquipCondition | 기존 조건 검사 재사용. 필요할 경우에만 CItemEquipCondition 명칭 적용 |
| L1ArmorSet | CSetItem | 세트 정의/판정 역할을 기존 구현에서 이관하고 기존 L1ArmorSet wrapper를 중복 유지하지 않음 |
| 세트 구성/슬롯 매칭 | IsItSet / GetSlotSetItemId / SlotToSetItemClassID | 실제 L1 세트 구조상 필요한 함수만 사용. NC 이름을 붙이려고 의미 없는 어댑터 생성 금지 |
| 장착·탈착에 따른 세트 비교 | ValidateSetItem / _ValidateSetItemOnEquipItem | 이전/이후 활성 세트 상태 비교. 기존 반지/복수 슬롯 처리 유지 |
| 세트 활성화·비활성화 | SetItemSkillEnDisable / EnableSetItemSkill / DisableSetItemSkill | 스킬로 부여되는 효과에 한해 기존 스킬 API 연결; 수치/시각 효과는 기존 책임 보존 |
| 상시 장비 스킬 검증 | ValidateItemSkills | 필요 시 기존 효과 부여/해제 로직을 공통화. 일반 공격 발동 스킬은 제외 |
| 강화 보정 조회 | CItemEnchantOption / CEnchantOption / GetEnchantOption | 기존 ItemEffectRegistry의 단계별 정의가 같은 책임이면 그대로 사용. 새 DB/테이블 도입 금지 |

클래스명이 꼭 필요한 경우 기존 관련 클래스의 역할과 실제 NC 이름에 맞춘다. 특히 L1EquipmentSlot에 대응하는 NC의 슬롯 조작 책임은 CCreature에 있지, 별도 NC CEquipmentSlot 클래스가 확인된 것은 아니다. Java의 기존 L1EquipmentSlot을 선호하며 가짜 NC 클래스명을 만들어내지 않는다. CSetItem으로 이름을 이관할 경우 파일, imports, 타입 참조 전체를 포함한 컴파일을 수행한다.

## 7. 개발 단계

E01. 최신 전체 소스에서 장착/탈착 경로, 기존 슬롯 상태, 모든 스탯 가감, 효과 등록/삭제, 세트 정의 및 반지 예외, 장착 상태에서 강화/축복/옵션 변경하는 호출부를 인벤토리화한다. 각 적용/해제 쌍과 원래 실행 순서를 기록한다. 무기 발동 경로를 별도로 보호 목록으로 지정한다.

E02. EquipItem / UnequipItem 및 _OnEquipItem / _OnUnequipItem 책임을 정리한다. 기존 set/remove 호출 경계, 다중 장비 교체, 인벤토리/CacheD 확정성 및 실패 동작을 그대로 보존한다. 불필요한 전면 인벤토리 스캔을 추가하지 않는다.

E03. ApplyStatBonus 중심으로 기본 옵션, 강화/축복 등 장착 보정 중복을 제거한다. 기존 정의 재사용, 조건별 고정값 및 실제 계산값을 구분한다. 장착마다 신규 DTO, Map, 람다, 컬렉션, 스탯별 객체 생성 금지. 스탯 계산 및 UI 순서의 변화는 검증하고 승인 없는 정책 변경 금지.

E04. 기존 L1ArmorSet을 CSetItem 책임에 맞춰 정리하고 ValidateSetItem 등을 연결한다. 활성 세트 전이에서만 필요한 보정/스킬을 변경하며, 기존처럼 전혀 다른 세트나 동일 효과를 공유하는 장비의 보정 및 스킬을 지우지 않는다. 데이터 로드/세트 정의 유지. 최소 필요 연결부 외 변경 금지.

E05. 기존 장착 부여 스킬과 ItemBonusHooks/ItemPresentator 후처리 경로 보존 확인. 무기 발동 및 마법검 동작 변경 없이 네트워크 패킷 내용과 순서를 확인한다. 기존 사용 조건과 아이템 표기는 수정하지 않는다.

E06. 전체 Java 21 타입 컴파일 및 실제 소스 대조 회귀 후 불필요한 중복 함수 제거. 보고서에 변경 파일 목록과 원본 대비 실측 검증 결과를 기록한다.

## 8. 필수 검증 항목

- 일반 장착/탈착 반복, 같은 장비 재요청, 장착 실패, 양손 무기·방패·이도류/슬레이어 예외, 보조 슬롯/반지 복수 착용·교체·정리.
- 기본/강화/축복/랜덤 옵션, 레벨/클래스/스탯 조건, 장착 중 강화 성공·실패 및 아이템 변경/이동/삭제 등 기존 지원 경로. 기존 착용 가능/불가 결과 동일.
- HP/MP 최대치·현재치, AC/MR/SP/WIS/STR 등 스탯, 중첩된 장착 효과, 세트 구성 성립/해제/교체/다른 동일 세트 장비, 변신/헤이스트/부여 스킬/아이콘. 적용·원복 후 스탯 누적 오차 없음.
- 장착/탈착과 버프, 로그인 복원/재접속/사망/클래스 변경 등 기존 경계. 적용되지 않은 효과가 제거되거나 같은 효과가 두 번 적용되는지 확인.
- 기존 패킷 전송 순서, 장비 창/인벤토리/툴팁 내용, 파티 상태 및 시각 효과 동일. 아이템 패킷 조립 자체 리팩토링 금지.
- 실제 원본/수정 소스 결과 비교, 전체 타입 컴파일, 가능한 JFR allocation/latency 비교. 테스트 fixture만 통과한 것을 전체 게임 검증으로 표현하지 말 것.
- L1WeaponSkill.java, WeaponSkillTable.java 및 CAttackAction의 발동/마법검 관련 코드 변경 0건을 diff로 검증. 스킬 엔진과 네트워크/DB 등 범위 외 변경 0건.
- 게임 동작을 손대지 않은 구조 개선과 실제 발견된 기존 오류를 분리한다. 새로운 오류 수정이 게임 정책을 바꾸면 사용자 승인 필요. 실게임 검증 미실시 항목은 보고서에 표시한다.

## 9. 설계 판단 및 분석 한계

NC는 장비 슬롯 변경, 장착 조건, 능력치 공급원 합산, 세트 성립 판정, 세트 스킬 수명, 아이템 표기를 책임별로 분리했다. 장비가 늘어나도 같은 보정 실행부와 세트 검증을 재사용할 수 있다. 특히 전/후 상태를 기준으로 계산하면 장착·탈착의 대량 복제 코드와 보정 누적 오류 위험을 줄일 수 있다. 성능 수치는 실제 구현/계측 전까지 보장하지 않는다.

이번 보고서는 확보된 정적 분석의 핵심 호출 관계를 RKern 개발 지시로 정리했다. NC의 모든 슬롯별 특수 분기, 모든 강화/세트 조건, 보정公式, 전체 패킷 필드 및 실행 소유권까지 완전히 복원한 것은 아니다. 미확인 NC 동작을 임의로 추가하지 않는다.

**최종 완료 기준: L1EquipmentSlot과 세트 아이템 관리만 NC 명칭 및 역할 분리 방식으로 정비하고, 기존 L1 동작과 실행 구조를 유지한다. 무기 발동·마법검·아이템 표기 전면 개편은 다음 별도 작업으로 남긴다.**
