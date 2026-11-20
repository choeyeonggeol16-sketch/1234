# RKern NC 스킬 효과 엔진 전체 이관 — 남은 작업 구현 명세 (필수)
2026-11-20 | 최신 통합 브랜치: rkern/unified-jproring-game-20261120

## 0. 기준 및 지시 우선순위
- 최신 기준 [146ef598efdcc7e2721042bddbbf5dc650e3e7f5](https://github.com/choeyeonggeol16-sketch/1234/commit/146ef598efdcc7e2721042bddbbf5dc650e3e7f5). 이미 반영된 스킬 작업 [b69a58b149869fd0bf3ea4b97b5e731f6953cec8](https://github.com/choeyeonggeol16-sketch/1234/commit/b69a58b149869fd0bf3ea4b97b5e731f6953cec8) 위에서 이어서 수정. 과거 SRC로 돌아가지 말 것.
- 원본 NC 정적 역분석 보고서: [NC_L2Server_SkillEffect_Pump_Lifecycle_RKern_20261120.md](https://github.com/choeyeonggeol16-sketch/1234/blob/146ef598efdcc7e2721042bddbbf5dc650e3e7f5/reports/NC_L2Server_SkillEffect_Pump_Lifecycle_RKern_20261120.md). 주소·VTable·호출 근거는 그대로 사용한다.
- 본 명세는 구현 범위 및 완료 기준에 관해 이전 보고서의 8~9절 및 이관 완료 기준을 보완·대체한다. 특히 기존 원복 방식으로 1차 완료 처리, 기존 핸들러 유지 자체를 목표로 삼는 지시를 폐기한다. 사용자 요구는 NC 효과 중심 실행 구조 전체의 L1 이관이다.
- 현재 브랜치의 코드 및 변경 diff를 확인한 후 작성한 구현 명세이다. 여기서 생산 코드를 수정하거나 새 런타임 테스트를 수행하지 않았다.

## 1. 이미 수행한 작업 — 재작업 금지
b69a58b에서 신규 CSkillEffect, SkillHandlers, SkillStopHandlers 및 TestSkillEffectLifecycle를 반영했다.
- 기존 cast/stop의 별도 registry를 paged table(32슬롯, ID 32768 미만) + sparse map으로 통합했고 boot 이후 freeze한다. 이 저장소를 재사용·확장한다. 다시 별도 평면 배열이나 이중 registry를 만들지 않는다.
- 현재 등록 상수 이름 기준 cast 305, stop 297개이며 원본 대비 누락/추가 0개를 정적으로 확인했다. 이것은 실제 int alias 및 실행 동등성 검증과 다르다.
- 즉시 효과 일부 분류(heal family, resurrection, cancellation 등), PRIDE/GIGANTIC 공통 최대 HP 계산, ADVANCE_SPIRIT HP/MP, DIAMOND_GOLEM/SHINING_AURA/EARTH_DRAGON 방어·마방 적용/해제 공통 함수가 존재한다. 기존 공식과 메시지 순서를 재사용한다. 중복 메서드 공통화 등 현재 커밋의 유효한 변경을 되돌리지 않는다.
- 기존 6개 버프 lifecycle 테스트 소스도 유지한다. 해당 테스트의 실제 실행 결과는 이 감사에서 확인되지 않았다.
- 남은 작업은 이것들을 최종 효과 엔진에 연결·편입하는 것이지, 완료한 통합을 다시 구현하는 것이 아니다.

현재 부족한 사실: CSkillEffect는 skillId별 instant/pumpStart/pumpEnd 콜백 필드 3개를 가진 registry entry이고, activate는 instant 또는 pumpStart 중 하나만 호출한다. registerLegacyCast는 pumpStart로 그대로 위임한다. 공유 함수가 일부 있어도 NC CSkillInfo의 순서 있는 효과 목록, 독립 CheckPumpCondition/Pump, 활성 효과 기반 InitializeSkillMod/ValidateSkillMod는 이 커밋에 구현되지 않았다. 등록부 통합으로 완료 판정 금지.

## 2. 최종 구조 — 반드시 구현
    기존 SkillsTable / L1Skills (메타데이터·공식 보존)
        → CSkillInfo (skillId별 불변·순서 있는 효과 정의)
            → CSkillEffect (공통 효과 실행 계약)
              Instant / CheckPumpCondition / Pump / PumpStart / PumpEnd
            → 종류별 공유 효과 구현 + 불변 파라미터
    기존 SkillExecutor / TargetSkillProcessor (시전·타겟·결과)
        → CSkillInfo.ActivateSkillEffects / PumpStart
    기존 SkillEffectContainer (활성 ID·수명 / CIO owner)
        → CSkillInfo.PumpEnd (실제 종료만)
        → InitializeSkillMod / ValidateSkillMod (필요한 전이에서 재계산)

역할이 동일하면 기존 클래스 안에 구현해도 되지만 실제 기능 및 호출 구조는 생략 불가. NC name을 우선 사용. CSkillInfo가 기존 L1Skills/SkillsTable의 DB 정의를 복제하여 두 개의 DB/스킬 카탈로그를 만들지 않는다. 기존 paged/sparse registry를 CSkillInfo 조회로 확장하고 ID당 한 정의만 등록. 효과 정의와 파라미터는 부팅 시 작성·동결. 각 스킬/시전별 새 효과 클래스, per-cast 객체·람다·임시 List/Map 생성 금지. primitive arrays, compact immutable effect descriptors 등으로 구현 가능하며 실제 고정 footprint/간접 호출 비용 비교.

NC 원본에서 확인된 것은 효과 순회 및 재계산 의미이며, L1의 구체 스탯 필드·자료구조·호출 타이밍은 아래의 L1 적용 명세다. NC에 실제 없는 것으로 확인되지 않은 이름이나 임의 공식을 원본 사실처럼 쓰지 않는다.

## 3. R01 — CSkillInfo 및 효과 목록 실행 (필수)
NC 근거: CSkillInfo::ActivateSkillEffects 0x8CC3FC, effect.vtable+08 호출 0x8CC4FD; timing별 vector 0x8C8F78. PumpStart 0x8CC07C, PumpEnd 0x8CC294.
- 기존 CSkillEffect의 one-start/one-stop registry entry 역할을 CSkillInfo 정의로 발전시킨다. 스킬 ID는 효과 정의의 lookup key이고 효과 구현 타입과 분리한다.
- 복합 스킬이 여러 효과를 순서대로 보유·실행해야 한다. 예: ADVANCE_SPIRIT의 최대 HP 보정, 최대 MP 보정 및 기존 적용/종료 패킷. EARTH_DRAGON의 AC/MR 조합. 이미 작성된 계산 함수/정의는 재사용하되 실제 효과 목록을 실행해야 한다. 단일 compound 콜백을 리스트 하나에 넣고 종료하는 식의 위장 이관 금지.
- 하나의 공유 효과 구현이 여러 skillId에서 서로 다른 데이터로 사용되어야 한다. 기존의 고유한 알고리즘이 있는 공격/특수 효과는 전용 effect 구현을 허용한다. 스킬 ID마다 새 클래스나 종전 핸들러를 그대로 복제하지 않는다.
- 기존 특정 버프의 타이머 등록/아이콘/부수 효과의 상대 순서는 정확히 보존한다. NC의 timing 숫자 4/6/7, 종료 이유 1/12, AbnormalSlot 정책을 L1에 임의 도입하지 않는다.
- 기존 TargetSkillProcessor에서 쓰는 HD_NOT_HANDLED/HANDLED/CONTINUE/RETURN, hd damage/drain/heal override 및 복수 대상 stop/continue 의미를 보존한다. 다중 effect 중 어느 단계에서 outcome을 바꿀 수 있는지 기존 동작을 기반으로 정의한다. 모든 복합 효과를 무조건 실행하는 것으로 대체하지 않는다.
수용: 적어도 복합 스킬 여러 종류의 효과 목록 구성, 순회 호출 추적, 같은 효과 타입의 서로 다른 skillId 재사용을 테스트로 입증. registerLegacyCast 대량 위임이 일반 실행 경로로 남아 있으면 미완료.

## 4. R02 — 효과 실행 계약을 5단계로 분리
NC VTable slots: Instant +08, CheckPumpCondition +28, Pump +30, PumpStart +38, PumpEnd +40.
- Instant: 실제 시전의 즉시 공격/회복/부활/해제 등. 재계산/만료 시 재발동 금지.
- CheckPumpCondition: 활성 지속 효과의 적용 조건 검사. 원래 무조건 효과는 항상 true 가능. 기존 무기/방어구/직업/상태 조건에 따라 보정이 달라지는 부분만 실제 조건 재검사. 조건이 변경될 때 기존 owner에서 재계산.
- Pump: 현재 유효한 효과에 근거한 순수 보정량 계산/적용. repeat/recalc에도 회복, 데미지, 메시지, 타이머 등록, 카운터 증가, DB 접근 등을 재실행하지 않는다.
- PumpStart: 실제 신규 등록 시 시작 부수 효과/필요한 적용값 기록. refresh에서 반복 호출하지 않는다. 기존 재시전 정책이 제거→신규 적용이면 이전 효과를 실제 종료한 뒤 새 시작. 기존 등록/시전 순서가 다른 특수 효과는 해당 경로에서 보존.
- PumpEnd: 실제 만료·해제·대체에서 필요한 부수 효과 정리. Pump 보정 분은 재계산 경로에서 제거하며 같은 능력치를 PumpEnd에서 다시 빼지 않는다. 원래 종료 전용 효과/즉시 효과의 비대칭성 유지.
모든 효과에 5개 별도 객체/가상 구현을 강제로 만들지 않는다. 공통 기본 no-op/true를 허용하되 각 단계의 실행 책임과 실제 호출은 분리. 기존 on 메서드 전체를 이름만 PumpStart로 바꾸는 작업은 불합격.

## 5. R03 — InitializeSkillMod / ValidateSkillMod 실구현 (필수)
NC 근거: CCreature::ValidateSkillMod 0x58BA30–0x58C0EA → InitializeSkillMod 0x583348 (call 0x58BFC1) → 효과 레코드 순회 CheckPumpCondition 0x58C037, Pump 0x58C052. p_max_hp/p_max_mp는 PumpEnd 기본 함수; p_physical_defence와 p_magic_speed는 공통 Pump 0x8DB788.
- L1Character/L1PcInstance의 기존 스탯 getter/setter와 실제 소스별(기본/장비/패시브/스킬/기타) 저장 위치 전수 조사. 기존 분리된 skill-modifier 필드를 우선 활용. 없으면 필요한 최소 primitive modifier 영역만 도입하고 기존 스탯 접근 API에 연결한다.
- InitializeSkillMod는 이관 대상 스킬 효과의 보정 영역만 초기화한다. 기본 능력치, 장비, 세트, 패시브, 영구 스탯, 음식/이벤트/아이템 등 별도 원천까지 0으로 만들면 안 된다. 동일 스탯을 수정하는 non-migrated source가 누락되지 않도록 source ownership ledger 작성.
- ValidateSkillMod는 기존 활성 효과 컨테이너의 안정적인 owner-side 상태에서 유효 정의를 조회해 CheckPumpCondition → Pump 순회. 모든 적용값을 확정한 뒤 필요한 derived stats/HP·MP clamp 및 알림을 기존 계약에 맞게 반영. 중간 reset 값이 다른 스레드/패킷에서 관측되거나 HP/MP가 일시 clamp되지 않도록 기존 publication 방식에 맞춰 commit한다.
- 매 tick/매 공격/매 패킷마다 재계산하지 않는다. 효과 추가/실제 삭제/교체, 관련 조건 또는 base stats 변화, 기존 복원 등 실제 invalidation 경계에서만 계산. refresh-only는 기존 의미에 따라 duration만 갱신. batch 변경은 관측·패킷 순서를 깨지 않는 범위에서 coalesce. 소유권 전환 및 재계산 재진입 금지.
- 이미 적용된 dynamic HP/MP 공식, 스탯 변화 시 갱신 여부, rounding, 원래 적용 당시 값을 유지하는 L1 동작을 기준으로 한다. 필요 시 실제 활성 효과의 applied parameter를 기존 컨테이너에 compact primitive 상태로 기록해 재계산에 사용. 현재 적용값을 매번 새 base로 재산정해서 L1 동작을 바꾸지 말 것. 적용 당시 값 vs 현재 조건 기반 값의 구분을 효과별 명시한다.
- 이전 +/− delta 방식과 새 Pump 재계산이 같은 스킬 보정에 동시 적용되지 않도록 한 효과군 단위로 start/stop/restore 경로를 함께 이관. 외부 코드가 직접 적용하는 효과의 source ledger를 완성한 뒤 수정. 전면 능력치 시스템 교체 또는 새 전역 stat engine 생성 금지.
- 실제 기존 게임 보정 원복 방식만 남겨두고 ValidateSkillMod라는 새 이름을 붙이는 작업 금지. 기존 순수 스탯 API의 필수 연결부 변경은 승인된 범위다. 전수 이관 중 충돌이나 조건 미확인 지점은 보고하고 분석을 이어가되, 단순 이전 방식 유지로 완료 처리하지 않는다.
수용: 같은 활성 목록을 반복 재계산해도 결과 동일(idempotent), 제거 후 다른 출처 능력치 정확히 유지, A/B 스킬 독립 제거, 기본 스탯/장비 변경, HP/MP clamp 및 packet values 차이 없는 회귀. 이미 수정된 6개 버프도 실제 Pump로 연결하고 중복 delta를 제거한 뒤 기존 결과를 재검증한다.

## 6. R04 — 상태 제한/중첩 수명
NC 근거: p_block_move Start 0x8EA558(+0xF50 증가), End 0x8EA610(양수일 때 감소); p_block_controll Start 0x8EA694(+0xF5C 증가), End 0x8EA6FC. p_block_spell은 Pump, p_block_act는 Pump+Start로 서로 다르다.
- L1 기존 이동/행동/조작/스킬/피격 제한의 set/clear 및 boolean 사용처, 원인별 해제 순서 전수 확인. 독립 제한 효과가 공존하는 종류만 source count 또는 활성 Pump 보정으로 관리. 중첩 스킬 A/B 중 하나 제거 후 B 제한 유지. 재시전 duration refresh가 카운터를 늘리지 않고 미등록/중복 제거가 감소시키지 않음.
- 일반 스탯 Pump와 마찬가지로 카운터 및 상태의 소유권은 기존 CCreature/CIO/NpcServer owner 경로 유지. 단지 boolean을 int로 일괄 교체하거나 새로운 atomic/lock/worker를 추가하지 말 것. 스킬 외 마비/장비/맵/특수 기능의 제한 원천을 보존.
- 상태 복원, clear-only, 사망, 강제 해제, 로그인, PC/NPC/companion별 의미와 client visual/icon을 비교한다. clear timer only에서 원래 stop을 안 했던 정책은 무조건 PumpEnd 호출하는 것으로 바꾸지 않는다. 카운터/보정 상태 초기화가 필요한 기존 경계는 분석해 정합성 유지.
수용: 겹친 제한, refresh, 두 종료 원인, 중복 remove, underflow, 비스킬 원천과 공존, 사망/복원 및 실제 이동/행동 차단 테스트.

## 7. R05 — 기존 호출부에 실제 연결 (최소 변경 허용)
기존 production 근거:
- core/TargetSkillProcessor.java:122–162에서 RecastPolicy.refreshAndSkipFurther 후 SkillHandlers.dispatch, HD outcome, dmg/drain/heal override 처리.
- core/MagicEffectApplier.java:12–33에서 duration/skip 정책 후 cha.setSkillEffect 및 icon. L1SkillUse.exAddMagicList(916)가 위임.
- SkillEffectContainer.java:113 put, 161 remove(stop flag), 191 clear, 246 heartbeat, 332 expired L1SkillStop.stopSkill. 기존 owner-only mutation 및 seqlock style 외부 읽기. 새 활성 effect container 또는 별도 timer 금지.
- L1SkillStop.stopSkill: companion.on_buff_stopped 및 CookingEffectHandler 우선 분기 후 SkillStopHandlers.dispatch, PC stop message/SC_OwnCharStatus. 우선 분기 및 packet order 보존.
- core/SkillExecutor, TargetRules, SkillCalc, SkillConsumeService, SkillUsabilityPolicy, DurationResolver, RecastPolicy, CounterMagicService 등 기존 시전/타겟/소모/공식 유지.
필수: 위 실행부가 실제 CSkillInfo/effect lists를 사용하도록 최소 호출부 연결을 허용한다. 기존 핸들러 facade를 유지하는 기간에도 runtime은 새 엔진이어야 한다. 등록/만료뿐 아니라 아이템·장비·GM·로그인·NPC·사망·복원 등 bypass 경로까지 확인. 가동 중인 기존 타이밍 휠, CCreatureController, CIO 및 NpcServer의 스레드/큐 배치는 그대로 둔다. 스킬 등록·제거 외 게임 실행 구조 재작성 금지.

## 8. R06 — 남은 전체 기존 핸들러 이관 및 삭제
- 현재 cast 305개/stop 297개 상수 이름 inventory에서 시작. 양쪽 196/시작만 109/종료만 101은 이전 보고서의 이름 기준 값이며 numeric alias, duplicate register, external grant를 다시 확인. 실제 int ID별 최종 등록 결과를 비교.
- 각 ID별 기존 effect kinds, 조건, 순서, 결과/override, 지속시간, 스탯 원천, 시작·종료 부수 효과, packet sequence, PC/NPC/특수 차이, 타이머 및 종료 전용을 ledger에 기록. 이관 전후 등록 매핑과 분기별 행동이 일치해야 한다. 이미 공통화한 6개는 신규 공통 함수 재작성 제외.
- 같은 계산·처리 책임은 하나의 effect implementation과 파라미터로 이관. HP/MP, AC/MR, STR/DEX/CON/WIS/INT/CHA, 공격/명중/회피, 내성/적중, 데미지 감소, 이동·공격 속도, 상태 제한, 회복·해제·부활·공격, 시각/아이콘 등 실제 존재하는 효과군을 순차적으로 완결. L2에만 있는 효과나 L1에 없는 스킬은 새로 구현하지 않는다.
- Legacy handlers의 스킬별 중복 body와 registerLegacyCast를 제거. 완전히 고유한 스킬만 의미 있는 전용 effect로 남겨 이유를 기록한다(특수한 행동, 조건부 상태, 복수 대상 결과 등). 단순 이름 변경이나 공통 registry 뒤에 305/297 on/off를 그대로 유지하는 방식 금지.
- SkillHandlers / SkillStopHandlers는 외부 호환이 필요하면 최소 dispatch facade만 남긴다. 실제 미사용 메서드/중복 테이블/등록 함수 삭제 후 모든 호출부를 갱신. 필요없는 새 중간 클래스/위임 제거. 실패를 숨기는 universal fallback 금지; 기존 NOT_HANDLED의 실제 의미는 보존.
- 같은 책임을 엔진 데이터로 옮긴 후 기존 switch fallback에 같은 로직이 남는지 확인. 이전 fallback 자체에 게임 공식 등 독립 책임이 있으면 유지.
완료 판정: 전수 ledger의 모든 기존 지원 경로가 이관/의도적 비이관(효과 엔진 범위 밖, 구체 근거)으로 분류되고 기존 중복 on/off 경로가 제거되어야 한다. 단순 ID 등록 수 일치만으로 완료 아님.

## 9. R07 — 검증 및 완료 게이트
1. 전체 Java 21 production dependency/type compile. 단순 syntax 검사로 대체 금지. 신규 클래스 인수/가시성/초기화 순서 및 등록 freeze를 검사.
2. 원본/최신 baseline과 동일 입력/난수, 전수 numeric ID 분류 및 실제 실행 회귀. 현재 TestSkillEffectLifecycle(6 buff) 보존·확장, 각 효과군 대표뿐 아니라 특수/종료 전용/alias 분기 확인.
3. 타이머/재시전: refresh-only, remove+apply, 만료/캔슬레이션, 중첩, 교체, stop-only, kill/clear(호출 여부 차이), 사망/로그아웃/복원. PC/NPC/companion/cooking/GM/item/복수 타겟.
4. 능력치: 기본/장비/패시브/타 효과와 공존, source별 제거, 재계산의 순수성·idempotence, HP/MP clamp, 동적 값과 rounding, 동시 읽기/기존 owner 순서. 기존 네트워크 packet IDs/bytes/order 및 결과/메시지 일치.
5. 상태 제한: 겹친 제한 및 refresh/cancel/expire/restore, underflow 없이 기존 외부 제한 유지.
6. 메모리/성능: 시전·Pump·heartbeat 별 per-cast allocation 및 CPU/JFR 비교, owner 긴 정지/불필요한 lock/queue 없음. 생산 telemetry 새로 추가 금지. 데이터 지향 구조. 성능 수치 미측정이면 미측정 명시.
7. 실제 게임/서버 검증할 수 없는 항목은 명확히 미검증 표시. 컴파일 실패, 회귀 실패, 미이관 ID 또는 no-op ValidateSkillMod가 있으면 전체 완료 주장 금지.

## 10. 작업 실행 지시
R01→R07을 하나의 연속된 전체 이관 과제로 진행한다. 1개 단계 또는 6개 버프 처리 후 전체 완료로 종료하지 않는다. 각 그룹의 적용/종료/회귀를 함께 진행하며 매 단계는 직전 최신본을 기준으로 한다. 이미 끝낸 b69a58b 작업을 재수정하는 것이 아닌 최종 효과 엔진에 편입하는 목적만 허용. 수정 파일/제거 코드/전수 ledger/실행한 검증과 미검증 항목을 마지막에 제출한다.
범위: 기존 L1 게임 공식, 스킬 발동/타겟/등록 제거 실행 경계, CIO timing wheel, CCreatureController, NpcServer, 네트워크, DB, PSS, 장비/세트 리팩토링은 유지. 능력치 연결부는 R03/R04에 필수한 최소 변경만 허용. 새 전역 실행 엔진/스레드/큐/DB 스키마 금지. NC 바이너리의 미확인 기계적 세부사항을 추정 복제하지 않는다.

**최종 완료 정의:** 스킬 ID 조회 → 순서 있는 효과 정의 → Instant / CheckPumpCondition / Pump / PumpStart / PumpEnd의 실제 호출 → 기존 활성 효과 기반 InitializeSkillMod / ValidateSkillMod → 중첩 수명 및 전수 기존 기능 보존. 이 실행 구조가 완성되어야 한다. 클래스 이름 정리, paged table 및 기존 handler 연결만으로는 완료가 아니다.

## 11. 소스 근거
- [CSkillEffect.java](https://github.com/choeyeonggeol16-sketch/1234/blob/146ef598efdcc7e2721042bddbbf5dc650e3e7f5/rkern/src/l1j/server/server/model/character/skill/CSkillEffect.java)
- [SkillHandlers.java](https://github.com/choeyeonggeol16-sketch/1234/blob/146ef598efdcc7e2721042bddbbf5dc650e3e7f5/rkern/src/l1j/server/server/model/character/skill/SkillHandlers.java)
- [SkillStopHandlers.java](https://github.com/choeyeonggeol16-sketch/1234/blob/146ef598efdcc7e2721042bddbbf5dc650e3e7f5/rkern/src/l1j/server/server/model/character/skill/SkillStopHandlers.java)
- [SkillEffectContainer.java](https://github.com/choeyeonggeol16-sketch/1234/blob/146ef598efdcc7e2721042bddbbf5dc650e3e7f5/rkern/src/l1j/server/server/model/character/skill/SkillEffectContainer.java)
- [L1SkillStop.java](https://github.com/choeyeonggeol16-sketch/1234/blob/146ef598efdcc7e2721042bddbbf5dc650e3e7f5/rkern/src/l1j/server/server/model/character/skill/L1SkillStop.java)
- [L1SkillUse.java](https://github.com/choeyeonggeol16-sketch/1234/blob/146ef598efdcc7e2721042bddbbf5dc650e3e7f5/rkern/src/l1j/server/server/model/character/skill/L1SkillUse.java)
- [core/TargetSkillProcessor.java](https://github.com/choeyeonggeol16-sketch/1234/blob/146ef598efdcc7e2721042bddbbf5dc650e3e7f5/rkern/src/l1j/server/server/model/character/skill/core/TargetSkillProcessor.java)
- [core/MagicEffectApplier.java](https://github.com/choeyeonggeol16-sketch/1234/blob/146ef598efdcc7e2721042bddbbf5dc650e3e7f5/rkern/src/l1j/server/server/model/character/skill/core/MagicEffectApplier.java)
- [core/RecastPolicy.java](https://github.com/choeyeonggeol16-sketch/1234/blob/146ef598efdcc7e2721042bddbbf5dc650e3e7f5/rkern/src/l1j/server/server/model/character/skill/core/RecastPolicy.java)
- [core/SkillExecutor.java](https://github.com/choeyeonggeol16-sketch/1234/blob/146ef598efdcc7e2721042bddbbf5dc650e3e7f5/rkern/src/l1j/server/server/model/character/skill/core/SkillExecutor.java)
