# NC L2Server 스킬 효과 엔진 역분석 및 RKern 핸들러 이관 기준

2026-11-20. 원본 바이너리 정적 분석 결과.
- 대상: L2Server(10).exe, PE32+ x86-64, 13,215,232 bytes, image base 0x400000.
- SHA-256: 9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588.
- 분석: objdump 전체 .text 역어셈블(1,905,667 출력 행), 직접 CALL/JMP 역참조, .pdata 함수 경계, MSVC RTTI Complete Object Locator 및 실제 VTable 포인터 검사, 진단 함수명 문자열과 명령어 교차 확인. 실행 계측 없음. 주소는 이 EXE의 VA이며 다른 NC 버전 함수 주소와 혼용하지 않는다.
- RKern 기준: [546983b5](https://github.com/choeyeonggeol16-sketch/1234/commit/546983b5fbf735d767e7b2730cb2214cef603c52).
- 사용자 요구: 기존 실행 구조는 그대로 두고 SkillHandlers / SkillStopHandlers의 효과 처리만 NC 방식으로 복원. 새 스레드·큐·DB 스키마·전역 실행 엔진 추가 금지. 게임 수치, L1 정책, 기존 패킷 동작을 유지. 본 커밋은 문서만 추가.

## 1. 바이너리에서 증명한 핵심

NC는 스킬 ID별 on/off 핸들러 테이블을 대응시키는 것이 아니라 CSkillInfo가 효과 객체 목록을 보유하고 가상 함수를 순회 호출한다. 능력치 효과는 활성 효과 목록으로부터 스킬 보정량을 초기화/재구성하는 경로가 존재한다. 이동 제한 등은 시작/종료 카운터를 갖는다. 즉시 효과, 지속 효과의 시작, 지속 효과 재계산, 종료는 서로 다른 실행 단계이다. 모든 효과에 대칭적인 종료 로직이 필요한 것은 아니다.

### 실제 VTable 전수 인덱싱

.rdata에서 코드 포인터 9개 이상을 가진 RTTI 연결 VTable을 검사하여 CSkillEffect 계열 366개 고유 이름/테이블을 확보했다. 이 필터에서 i_ 159개, p_ 178개, 기타 29개이다. 조건 계열 RTTI raw 이름은 83개(기반 클래스 포함)로 확인했다. 전체 효과 정의의 실제 게임 사용 여부나 모든 메서드의 의미를 확인한 수치는 아니다.

공통 기본 함수 0x53A8FC = mov al,1; ret. 각 슬롯별 기본 포인터와 다른 대상 수(동일 override 공유 포함):

| 슬롯 | i_ 159 | p_ 178 |
|---|---:|---:|
| +08 Instant |159|0|
| +10 별도 즉시 경로, 정확한 함수명 일부 미확인 |5|0|
| +18 미확인 |0|0|
| +28 CheckPumpCondition |0|62|
| +30 Pump |0|137|
| +38 PumpStart |0|51|
| +40 PumpEnd |0|49|
| +48 미확인 |0|0|

메서드 의미는 호출부/진단 이름을 확인한 슬롯만 확정했다. 기본 VTable 0xD3F948: +00 0x56DAA8, +08/+10/+18/+28/+30/+38/+40/+48 0x53A8FC, +20 0x933684. +00/+20/+10/+18/+48 전체 시그니처를 임의로 확정하지 않는다.

| RTTI 클래스 접미사 | VTable | Instant +08 | Check +28 | Pump +30 | Start +38 | End +40 |
|---|---|---|---|---|---|---|
| i_heal | E481D8 | 8E0574 | - | - | - | - |
| i_m_attack | E48388 | 8FCA60 | - | - | - | - |
| i_resurrection | E48898 | 8E10C4 | - | - | - | - |
| i_dispel_all | E49D98 | 8E4070 | - | - | - | - |
| p_physical_defence | D601A8 | - | 8DB710 | 8DB788 | - | - |
| p_magic_speed | D60628 | - | 8DB710 | 8DB788 | - | - |
| p_max_hp | D60748 | - | 8DB710 | 8EAC64 | 8EAB2C | - |
| p_max_mp | D607D8 | - | 8DB710 | 8EAE54 | 8EAD2C | - |
| p_max_mp_add | D60868 | - | 8DB888 | 8EAF1C | - | - |
| p_block_move | E1CCB8 | - | - | - | 8EA558 | 8EA610 |
| p_block_act | E1CD48 | - | - | 8FE6B8 | 8EA4F8 | - |
| p_block_controll | E1CDD8 | - | - | - | 8EA694 | 8EA6FC |
| p_block_spell | E1CE68 | - | - | 8EA780 | - | - |
| p_block_getdamage | D500E8 | - | - | 8EA890 | - | - |
| p_block_chat | E4D458 | - | - | - | 8EF110 | 8EF1D8 |

주소 모두 VA hex. -는 0x53A8FC 기본 함수. NC 클래스명 오타 controll은 원문 유지.

## 2. 실제 스킬 정의와 즉시 효과 실행

CSkillDB::Load 0x8CEAF4 -> LoadSkillData 0x8CAAB4(0x8CEBDB call). Add 0x8D0268, PostLoad 0x8D0560, GetSkillInfo 0x8CEC4C, GetSkillInfoByName 0x8CF884. 스킬 정의 읽기/등록과 실행 경계는 확인. 전체 스킬 스크립트 파서 문법·정의 생성 로직은 미복원.

CSkillAction2::Activate 0x7909E4 -> CSkillInfo::ActivateSkill 0x8CD038 (call 0x790EDC, 0x79123C, 0x791337). 아이템, 이벤트, 트리거에서도 같은 ActivateSkill로 직접 진입하는 xref 확인(UseETCItem 0x582ED1, CItem::ActivateMultiSkillList 0x7129EB, AtomicCastBuff 0x448B59 등).

ActivateSkill 내부 주요 호출:
- 0x8CD0EC CanActivateSkill(0x8C9178)
- 0x8CD12A / 0x8CD2C4 IsIgnoreSkill(0x581728)
- 0x8CD2E8 CheckOperateCond(0x8CC654); 조건 객체 가상 함수 +0x00 호출부 0x8CC737, 0x8CC7FE, 0x8CC8C0.
- 0x8CD324/336/34A/450/468 ActivateDuraionSkill(0x8CB0E0; NC 진단 문자열 원문 오타 Duraion).
- 0x8CD3CE _FindSameBuff(0x59AA58).
- 0x8CD4A4 SkillAvoidOrCounter(0x8C94B0).
- 0x8CD4D6 ActivateSkillEffects(0x8CC3FC).
- 0x8CD5EB AttachedAbnormalStatusByChannelling(0x5A5E34).
분기가 다수 존재하므로 위 목록을 무조건 연속 실행한다고 간주하지 않는다.

ActivateSkillEffects의 타이밍 목록:
0x8C8F78: timing unsigned <=7이면 this + 0x238 + timing * 0x20을 반환; 아니면 NULL.
0x8CC47C: 타이밍별 벡터 선택.
0x8CC4D5: effect = begin[index]; 0x8CC4FD: call [effect.vtable+08].
0x8CC5F1: caster가 NULL인 분기에서는 +10 호출.
0x8CC51D에서 timing==0 및 대상 유효 분기 -> target vtable+418의 결과에 따라 timing 6(0x8CC56E) 또는 7(0x8CC59F)을 재귀 실행.
즉시 효과는 순서 있는 목록, 타이밍별 분기, 후속 효과를 갖는다. 스킬 ID별 switch로 환원하거나 종료에 모든 즉시 효과를 다시 호출하면 안 된다. timing enum의 이름/모든 값 의미는 미확정.

## 3. 지속 효과 등록 및 시작

실제 호출:

ActivateDuraionSkill 0x8CB0E0
 -> GetDurationSkillProb 0x8CAE48 (call 0x8CB22E)
 -> InfectedSkillBy 0x5A5B8C (0x8CB3A2, 0x8CB3CB)
    -> InfectedInstantDurationSkillBy 0x584708 (특수 분기)
    -> CanBeBuffed 0x5849C0
    -> _InfectedSkillBy 0x5A5764 (0x5A5DAE)
       -> _FindSameBuff 0x59AA58 (0x5A5832)
       -> _DeleteAbnormalStatus 0x59C7F8 (0x5A58BD, 조건부 기존 효과 제거)
       -> _FindReplacableBuffAndRemove 0x5A0280 (조건부)
       -> CreateInfectedSkillType 0x593870 (0x5A5A6A)
       -> _AddAbnormalStatus 0x57139C (0x5A5AE6)
          -> CSharedCreatureData::AddAbnormalStatus 0x8BB9C8 (0x57145C)
          -> 상태 변경의 가상 호출 경로 존재.

NPC/User _OnAbnormalStatusChanged 각각 0x79AB28/0x94CDF0 -> CCreature::_OnAbnormalStatusChanged 0x58FE7C (직접 call 0x79ABA8/0x94CE6D).
0x58FF26 메시지 구성 호출 0x8AF390, 0x58FF32 PushSerializedMessage 0x58B77C.
CSerializedOnInfectedSkillByMessage::OnHandle 0x8AD894 -> 0x8AD908 CSkillInfo::PumpStart(0x8CC07C) -> 0x8AD919 CCreature::OnInfectedSkillBy(0x584B8C).

PumpStart:
- 0x8CC0FE: caster가 있는 경우 CanUsedBy 검사.
- 0x8CC11A: skill +0x24 == 4 분기에서 SetPassiveSkillPumpStart(0x58524C), false면 조기 종료.
- 0x8CC150: 효과 포인터 벡터 +0x220/+0x228 순회, 0x8CC1AF call [vtable+38].
- 0x8CC1CC: 두 번째 벡터 +0x340/+0x348 순회, 0x8CC22B call [vtable+38].
- 반환값을 AND 누적한다. 실패시 즉시 loop break하는 형태가 아니다.
- PumpEnd는 +0x220 벡터만 확인됨. 두 번째 목록의 정확한 의미/별도 정리 경로는 미확정. 임의 대칭화 금지.
- PumpStartAllPumpEffects 0x5A03E8 -> PumpStart 0x5A051D의 직접 호출도 확인(복원/전부 시작 경로의 전체 조건은 미확인).

OnInfectedSkillBy:
0x584C06 vtable+538; 0x584C28 ValidateAllOnSIM(0x572A38); 0x584C3C ValidateHP(0x8BBFD0); 0x584C97 ValidateCP(0x8BC274); 0x584CD7 CheckAbnormalVisualEffect(0x584194). 모든 분기에서 동일하게 수행된다고 단정하지 않는다.

## 4. 종료·해제·만료

CCreature::TimerExpired 0x59D3E8:
- 0x59D4DD HandleSerializedMessages 0x587928.
- 0x59DAB4/0x59DC5F PushEffectsIfInTime 0x58A5A0.
- 0x59DD3E/0x59DED1 _DeleteAbnormalStatus 0x59C7F8.

DispelBySlotProb 0x59EDBC, DispelByIdLevelMySelf 0x59F024, DispelByBuffLevel 0x59F3B0, DispelByCategory 0x59F578, DispelAll 0x59F8A0, DispelAllByGM 0x59FB18, DispelByNames 0x59FCF0, DispelBySlot 0x5A0048.
_DeleteAbnormalStatus는 타이머/해제/대체 경로에서 호출됨. 0x59CBF0 CSharedCreatureData::DeleteAbnormalStatus(0x8BB89C), 이후 가상 상태 갱신 및 시각 효과 갱신. 모든 종료 원인의 분기별 처리 순서는 미복원.

NPC/User _OnAbnormalStatusDeleted 각각 0x79AC20/0x94CF08 -> CCreature::_OnAbnormalStatusDeleted 0x58FF94 (0x79ACA0/0x94CF8A).
0x59002E 메시지 구성 0x8ADA44, 0x59003D PushSerializedMessage.
CSerializedPumpEndMessage::OnHandle 0x8ADAFC -> 0x8ADB70 CSkillInfo::PumpEnd 0x8CC294.
PumpEnd: skill+0x24==4에서 SetPassiveSkillPumpEnd 0x585594; +0x220/+0x228 포인터 범위 순회; 0x8CC3A0 call [vtable+40].
종료 이유 숫자 1 또는 12일 때 0x590042/47 비교 -> 0x59006B에서 timing 4 -> 0x59007A PostActivateSkillEffects(0x8CB530).
PostActivateSkillEffects는 0x8CB5AB CheckActivateSkillEffects(0x8CB418), 0x8CB5F7 PushSerializedMessage 호출.
CSerializedActivateSkillEffectsMessage::OnHandle 0x8AE194 -> 0x8AE208 CheckActivateSkillEffects -> 0x8AE234 ActivateSkillEffects.
따라서 지속 효과 종료에는 단순 해제 외 조건부 후속 즉시 효과가 있을 수 있다. 종료 이유 1/12의 enum 이름은 미확정.

CSkillAction2::OnEnd 0x790384는 0x79052F PostActivateSkillEffects, 0x79054B DetachedAbnormalStatusByChannelling(0x5A0878), 취소 방송 경로 0x7904E2를 포함한다. 시전 행동 종료와 지속 버프 만료를 동일 개념으로 합치지 않는다.

## 5. 실제 보정량 재계산

CCreature::ValidateSkillMod 0x58BA30~0x58C0EA:
0x58BB8C/0x58BF6E CanUsedBy, 0x58BBA5 CheckOperateCond, 여러 경로에서 0x8D0014를 통해 효과 항목 수집. 0x58BDEA IsLockedBuff, 여러 잠금 획득/해제 경로 확인.
0x58BFC1 call InitializeSkillMod(0x583348).
0x58C02D effect pointer = [record+0]; 0x58C037 call [vtable+28], false면 skip.
0x58C045 r9d=[record+0x10]; 0x58C04A r8=[record+0x08]; 0x58C052 call [vtable+30].
0x58C05C record offset += 0x18.
즉 임시 효과 레코드는 24바이트 stride로 순회하며 effect pointer, CSkillInfo로 전달되는 pointer, int 인자가 있다. int 의미는 추가 확인 필요. 재계산 때 CheckPumpCondition을 검사하고 Pump만 호출. PumpStart나 Instant를 반복 호출하는 경로가 아니다.

InitializeSkillMod의 실제 호출처: 생성/복사, EnterWorldImpl, User::InitAtConstructor, User::ValidateSubJobParameters, ValidateSkillMod. ValidateSkillMod 직접 호출처: ValidateAllOnWeapon, ValidateAllOnArmors, CalcDamageWithMPShield, CNPC::EnterWorld, User::ValidateAllOnWeapon, User::SummonCreature 및 SSQ 관련 경로. 가상 호출 경로 전부와 각 종료 원인별 재계산 트리거는 미확정.

CSkillEffect_p_physical_defence와 p_magic_speed 모두 CheckPumpCondition 0x8DB710 및 Pump 0x8DB788 공유. 0x8DB788 함수의 진단 이름 CSkillEffectAddBonusByArmorType::Pump; 0x8DB7E3~0x8DB7EA에서 effect +0x20/+0x24 int 및 +0x18 double 읽고 0x8D9CE8 호출. 즉 종류/값 데이터와 공통 구현 조합의 직접 증거. 해당 파라미터의 공식 enum 전체는 미확정.

p_max_hp의 PumpStart(0x8EAB2C)는 HP 변경 메시지 0x8DB038 호출(0x8EAC22)을 포함; Pump(0x8EAC64)는 공통 Pump 0x8DB788 호출(0x8EACD3), 대상 가상 함수 0x8EACDE, 조건부 플래그 +0x1F74 설정. PumpEnd는 기본 함수. p_max_mp도 PumpStart/Pump 각각 별도, PumpEnd 기본 함수. 실제 최대 HP/MP 종료시 이전 적용량을 반대로 가산하는 클래스별 handler가 아닌 재계산과 별도 수명 단계가 핵심.

## 6. 상태 제한은 카운터 방식도 사용

p_block_move VTable 0xE1CCB8:
PumpStart 0x8EA558 -> 0x8EA5BC add DWORD PTR [rdx+0xF50],1.
PumpEnd 0x8EA610:
0x8EA660 mov eax,[rdx+0xF50]
0x8EA666 test eax,eax
0x8EA668 jg 0x8EA67A
0이면 false 반환. 양수에서 0x8EA67A add eax,-1, 0x8EA67D mov [rdx+0xF50],eax, true 반환.
p_block_controll Start 0x8EA694 -> 0x8EA6E4 add DWORD PTR [rdx+0xF5C],1; End 0x8EA6FC. p_block_spell은 Pump만 구현, p_block_act는 Pump+Start. 모든 상태 효과를 무조건 카운터로 바꾸는 것은 NC 복원이 아니다.

## 7. 실행 소유권 경계: 참고 사실만, 이관 제외

PushSerializedMessage 0x58B77C는 [creature+0xA40] 관련 보호 구간 진입 후 +0x24 byte와 +0x20 dword 검사. 둘 다 0일 때 +0xA50 목록 삽입 0x58B87B; 다른 분기는 메시지 가상 +0x68 및 +0x10 호출 0x58B890/0x58B8B1. HandleSerializedMessages 0x587928는 메시지 호출 및 정리 가상 함수를 사용. 해당 필드 의미, 모든 소비 조건/스레드 및 가상 +0x68 의미는 여기서 단정하지 않는다.
중요: NC의 serialized message 사실을 이유로 RKern에 새 worker, queue, serializer를 추가하지 말 것. 기존 CCreatureController, CIO, NpcServer 및 timing wheel을 수정하는 작업이 아니다.

## 8. RKern 이관 지시 — 두 핸들러에 집중

기준 SkillHandlers 6,958줄, 305개 ID 등록, 205개 on 메서드. SkillStopHandlers 3,329줄, 297개 ID 등록, 214개 off 메서드. 양쪽 등록 ID 196, 적용만 109, 종료만 101. 이 숫자는 등록 상수 이름 기준이며 실제 int alias 중복/전체 부여 경로를 별도로 확인할 것.

정확한 NC 대응:
- SkillHandlers: 기존 호출 시점/인자 및 반환 의미를 유지하면서 효과 중심 구현으로 이관. 즉시 효과는 Instant, 지속 효과의 순수 보정은 Pump, 시작 부수 효과는 PumpStart로 역할을 구분.
- SkillStopHandlers: 동일한 효과 정의의 PumpEnd와 필요 시 보정 제거/재계산 연결로 이관. 무조건 반대 수치 적용이나 모든 효과에 off 메서드를 만드는 방식 지양.
- 동일 효과 종류에 속하는 기존 기능은 적절하게 묶는다. NC의 class 명칭 및 실제 의미를 우선하되 366개 NC 클래스 전체를 L1에 복제하지 말 것. 존재하지 않는 L2 효과 구현 금지.
- 시전 정의/사용 조건/타겟/마법 공식/액션/타이밍/소유권 및 DB를 임의 변경 금지. 기존 L1Skills, SkillsTable, SkillExecutor, TargetSkillProcessor, L1SkillUse, RecastPolicy, SkillEffectContainer, CCreatureController, CIO Timing Wheel, NpcServer 유지. 필요 연결부 변경은 두 핸들러 이관에 필수인 호출만.
- NC의 능력치 전면 재계산을 다른 기존 능력치 구조까지 뜯어고치는 근거로 삼지 않는다. 기존 능력치 변경 API에 공통 Pump/종료 대응을 연결할 수 있는지 먼저 확인. 실제 전체 재계산 기반이 없으면 기존 원복값/공식 유지로 1차 이관하고 전체 stat 재구축은 별도 승인. 이것은 L1 적응이며 NC 원본 그대로라고 주장하지 않는다.
- 고정값 버프, 동적 HP/MP 버프, 조건부 상태, 즉시 회복/공격/해제, 시각 효과와 종료 UI, 보스/소환/특수 기능을 기존 동작별로 이관. 스킬마다 클래스 생성 금지. 시전마다 효과 정의 객체/임시 컬렉션 생성하지 말 것.
- 실행 중 스킬 정의는 기존 등록 구조에서 재사용. 구체 자료구조는 기존 코드에서 최소 변경으로 선택하며 NC가 Java primitive 배열을 썼다고 표현하지 않는다.
- NC의 timing4/6/7, 종료 이유 1/12, AbnormalSlot을 L1 게임 규칙에 임의 도입하지 않는다. 기존 UI/강제 해제/타이머 kill/clear와 stop 호출 차이 보존.

## 9. 이관 순서 및 검증

S01 전체 등록 ID 및 호출부 인벤토리. 196/109/101 분류 및 아이템/로그인/GM/장비 등 종료 전용 효과 부여 경로 확인.
S02 즉시 효과 공통화(기존 heal family, attack, dispel 등). 기존 HandlerResult의 NOT_HANDLED/HANDLED/CONTINUE/RETURN과 damage override 보존.
S03 정적 능력치 보정 적용/해제 공통화. 원래 한 번만 적용된 값만 원복. 직접 영향 없는 stat 계산부 보존.
S04 동적 HP/MP, 지속시간·중첩·재시전의 실제 계산과 알림 순서 보존. PRIDE/GIGANTIC/ADVANCE_SPIRIT과 별도 legacy ID를 함부로 합치지 않음.
S05 시작/종료 부수 효과(이동·행동 제한, 상태 시각효과 및 아이콘). PC/NPC 기존 차이 보존. 타겟 소유권과 큐 거부 등 기존 경계에서 적용/제거 불일치가 확인되는 경우만 보고하고 범위 확대 금지.
S06 특수 효과 및 외부 부여·종료 전용 효과. 기존 실제 분기 보존. 더 이상 쓰이지 않는 핸들러/중복 레지스트리 제거.
S07 Java 21 전체 타입 컴파일, 기존/신규 스킬 ID별 원본 대조 회귀. 같은 입력과 난수로 시전 결과, HP/MP, 효과 목록, 능력치, 패킷 순서·내용 비교. 만료/캔슬레이션/재시전/중첩/사망/로그아웃/복원/아이템 버프/무효 대상/PC-NPC/PvP/복수 타겟 확인. JFR 할당 및 처리 시간 비교. 실제 인게임 검증 별도. 현재 문서 작성 단계에서 실행 테스트는 하지 않음.

## 10. 분석 완료 범위와 미확인 영역

원본 파일 SHA, 366개 효과 VTable, 즉시 효과 타이밍별 순회, 지속 등록·시작·종료 Message 호출 경로, 재계산 및 선택적 PumpEnd, 이동 제한 카운터를 실제 명령어로 확인했다. 이 수준은 두 핸들러 역할 분리의 직접 설계 근거다.
모든 366개 클래스의 메서드 내부, 모든 스킬 정의 파서 분기, enum 값 전체, 모든 삭제 원인별 가상 경로, 시전 종료와 지속 종료 후속 효과 전수, 전체 스레드 동작까지 완전히 복원한 것은 아니다. 추가 분석 없이 미확인 NC 기능을 추정 구현하지 않는다.
