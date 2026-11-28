# RKern 군주 오라 전면 개편 지시서 — NC OFF Aura Skill Pump 구조

작성일: 2026-11-29 (KST)
RKern 조사 기준: [rkern/gameplay-optimization-20261129](https://github.com/choeyeonggeol16-sketch/1234/tree/rkern/gameplay-optimization-20261129)
NC 기준: [Open_LU4_L2OFF abbb2e5](https://github.com/luannbr/Open_LU4_L2OFF/tree/abbb2e5f474d40cf12539a8e97fddc52474ed95e) (공개 OFF 복원 소스)
본 커밋은 보고서만 추가한다. 실제 구현은 최신 작업 브랜치 및 진행 중인 스킬 습득/변신 등 개편을 확인하여 기존 수정사항을 보존한다.

## 1. 목표 및 필수 구조

전역 AuraController에서 모든 군주를 2초마다 조회하는 방식을 폐기한다. 군주가 가진 활성 오라를 기존 캐릭터 스킬 효과 소유 구조로 이관하고, NC의 스킬 PumpStart/Pump/PumpEnd 및 ActivateAuraSkill 실행 책임을 적용한다.

- 클래스명/메서드명은 NC에서 실제 확인된 이름을 사용하고 오라 관련 구현을 전용 패키지에 집중한다. 제안 경로: model.character.skill.aura. 기존 스킬 효과 패키지와 동일 책임 클래스가 있으면 통합하고 빈 래퍼/중복 관리자를 추가하지 않는다.
- CSkillEffect_p_aura: 활성 오라 등록/실행/제거의 역할. Java 명칭은 NC 이름을 사용하되 프로젝트의 기존 패키지/명명 규칙에 맞춘다.
- CCreatureSkillMod: 캐릭터별 스킬 보정 및 활성 오라 소유 역할. 이미 존재하는 SkillEffectContainer 및 pumped modifier 구조와 통합한다. 같은 효과를 중복 소유하는 신규 클래스/자료구조 생성 금지.
- CSkillInfo::ActivateAuraSkill, CCreature::AuraSkillBySkill, GatherNeighborParty에 대응하는 책임은 기존 Java 스킬 정의, 캐릭터, 파티 API에 이관한다.
- L1의 실제 군주 오라 대상/18칸 범위/2초 검사 주기/효과 수치/아이콘 및 패킷을 보존한다. L2 전용 오라 기능을 추가하지 않는다.
- 기존 CIO owner 실행 및 timing wheel/스킬 효과 heartbeat를 활용한다. 신규 스레드/전역 스캔/중복 큐 금지.
- AuraController에 섞여 있는 혈맹 축복 만료 처리는 오라와 분리하되 기존 혈맹 저장, generation 및 receipt 처리 정책을 절대 손상시키지 않는다.

## 2. NC 실제 실행 근거

[SkillFxFuncAura.cpp L31–82](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/SkillFxFuncAura.cpp#L31-L82):
CSkillEffect_p_aura는 OnPostLoad에서 스킬 정의를 조회하고 PumpStart에서 m_SkillMod.m_SkillAuraMap에 등록한다. Pump는 활성 플래그를 켜고 PumpEnd는 해당 항목을 제거한다. m_SkillMod.m_Lock으로 보호한다.
[Creature.h L546–563](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Creature.h#L546-L563), [L707–755](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Creature.h#L707-L755): 캐릭터 SkillAuraInfo와 오라 자료구조.
[CreatureServer.cpp L4797–4821](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/CreatureServer.cpp#L4797-L4821): AuraSkillBySkill은 락으로 활성 오라 목록을 확보하고 활성화된 항목에 대해 CSerializedAuraSkillMessage를 게시한다.
[SerializedMessage.cpp L190–206](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/SerializedMessage.cpp#L190-L206): 메시지 처리 시 CSkillInfo::ActivateAuraSkill을 호출한다. NC의 참조 수명/serializer 역할은 기존 Java owner 경로에 맞춰 적응한다.
[Skill.cpp L1531–1583](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Skill.cpp#L1531-L1583):
- STGT_MY_PARTY: GatherNeighborParty로 범위 내 파티원 수집.
- STGT_MY_PLEDGE: GatherNeighborPledge.
- STGT_ENEMY: GatherNeighborCreature.
- STGT_SELF: 자기 자신.
- 수집된 대상별로 ActivateSkill을 실행한다.

**NC에서 다른 파티원에게 오라가 적용되는 실제 코드까지 확인했다.** 단, NC의 주기 호출 상위 경로, GatherNeighborParty의 전체 세부 조건, 수신 효과 만료·중첩 정책 전체는 미확인이다. 아래 2초 주기 및 소스별 중첩 제어는 L1 동작을 보존하기 위한 구현 지시이며 NC의 미확인 내부 정책으로 주장하지 않는다.

## 3. 현재 RKern에서 확인한 문제

[AuraController.java](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/Controller/AuraController.java), blob 8079de9606b168370781f09423ddb280c8669da7.
- L30–89: 2초 fixedDelaySignal 및 CWorldWork worker 제출, 전역 trackedCrownIds Set<Integer>, 여러 실행 가드.
- L92–165: 군주 등록/삭제, 2초마다 ID→월드 객체 재조회, 파티 확인 및 파티원 반복 순회. 군주가 파티를 잃거나 유효하지 않을 때 수신 대상 전체의 출처별 정리 여부를 확인해야 한다.
- L175–189: 동일 파티·맵, 수신자 생존, x/y 각각 ±18칸 검사. 현행 범위는 사각형이며 원형/맨해튼 거리로 변경하지 않는다. 군주 자신도 포함될 수 있다.
- L191–267: BRAVE_AVATAR_1ST/2ND/3RD 및 Pbavatar 상태로 MR +10, 전체 특수저항 +2, STR/DEX/INT +1을 직접 반영/차감하고 패킷을 갱신한다. 스킬 효과 소유 구조와 직접 스탯 갱신이 겹치는지 확인한다.
- 복수 군주가 한 파티에 있는 경우 각 군주가 독립적으로 ensureAuraOn/safeEnd를 수행한다. 한 군주의 범위 밖이나 탈퇴가 다른 군주의 유효한 오라를 제거할 가능성이 있다. 수신 상태에 출처가 명시되지 않은 구조적 위험이며 실제 인게임 재현은 아직 하지 않았다.
- L269–317: 혈맹 축복 카운트다운 및 CacheD 확정/owner 적용이 오라 클래스에 혼재되어 있다. 해당 경로는 반드시 보존·분리한다.

[CParty.java L214, L243–244, L258, L392–394](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/CParty.java): 가입/탈퇴 시 AuraController 직접 호출, 기존 roster 및 읽기용 view 존재.
[L1PcInstance.java L687–704](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/Instance/L1PcInstance.java#L687-L704): Pbavatar 및 Pbavataron 플래그.
[SkillEffectContainer.java](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/character/skill/SkillEffectContainer.java), blob cb4d59518fd6d38b6f99e53c937db31ecebfa27b: 기존 source 기반 pumped modifier 계산 및 owner 전용 timed effect heartbeat 존재. tryArmHeartbeat는 timedCount가 양수일 때만 arm한다. 무제한 오라가 기존 heartbeat를 자동으로 깨운다고 가정하면 안 된다.

## 4. 구현 지시

### P1. 전체 호출 및 소유 관계 감사
```sh
rg -n 'AuraController|BRAVE_AVATAR|[Pp]bavatar|brave_end|allbrave_end|trackedCrownIds|SkillEffectContainer|AuraSkillBySkill|ActivateAuraSkill|tickClanBuff|beginBlessingExpiry' rkern/src
```
스킬 습득/사용, 군주 오라 활성화 조건, 파티 가입/탈퇴/해산, 사망/부활, 로그아웃/재접속, 텔레포트, 직업 변경, 효과 해제, 초기 로드 및 스탯 재계산을 조사한다. BRAVE_AVATAR 단계별 실제 효과와 PvP/파티 조건, 기존 비활성 플래그 의미를 확인한다. 단순히 등록된 군주 전체가 영구히 오라를 발산하는 신규 정책을 만들지 않는다.

### P2. 캐릭터 소유 활성 오라
- PumpStart/Pump/PumpEnd에 대응하는 기존 skill lifecycle에서 출처 캐릭터의 오라 등록/활성/해제 상태를 한 번만 변경한다.
- 활성 오라 소유자에만 기존 CIO 타이밍 실행을 예약하거나 기존 owner heartbeat에 안전하게 통합하여 2초 주기를 유지한다. 무제한 효과 및 timedCount=0에서도 활성 오라 주기가 유지되는지 검증한다. 오라 해제 시 추가 예약을 중단한다. 전역 군주 ID 집합과 월드 전체 조회/별도 CWorldWork 주기 작업 제거.
- 기존 파티 roster snapshot/view를 사용하고 현재 party identity, map instance, 범위, 생존 및 실제 대상 유효성을 검사한다. 파티 규모를 넘어서는 월드 전체 탐색 금지. 이동하지 않은 상태에서 동일 효과 패킷을 반복 발송하지 않는다.
- NC의 CSerializedAuraSkillMessage 역할은 기존 CIO owner 직렬화 경로에 적응한다. 새 메시지 큐/worker 생성 금지. 서로 다른 CIO owner의 PC 스탯을 직접 수정하지 않으며 대상 owner에서 session/world identity 및 파티·오라 세대를 재검증한다.

### P3. 수신자 효과 정확성
- 수신자의 기존 스킬 효과 소유 구조에 오라 출처(실제 caster/세션 또는 world identity/활성 세대)를 연결한다. 동일 스킬의 여러 유효한 군주 중 하나가 해제되어도 다른 출처가 유효하면 보정을 유지한다.
- 기존 군주 오라는 중첩해서 능력치가 증가하지 않는 정책을 보존한다. 출처별 등록/해제와 최종 효과 on/off를 구분한다. 첫 유효 출처에서만 기존 수치 반영, 마지막 유효 출처가 사라질 때만 실제 반영분 해제. 기존 1/2/3단계 우선순위 및 충돌 규칙은 전체 호출 확인 후 유지한다.
- 군주가 사망/로그아웃/직업 변경/탈퇴하거나 오라 종료 시 더 이상 현재 파티 roster에 없는 과거 수신자까지 출처를 정확히 회수한다. 수신자 탈퇴/맵 이동/거리 이탈도 기존 2초 검사 또는 이미 존재하는 안전한 lifecycle 경로에서 정리한다.
- stale apply/end, 세션 재사용, 파티 객체 변경, 대상 owner queue 거절, 오라 재활성화 경쟁을 처리한다. 단순히 effect ID 하나를 지우는 방식 금지. 중복 해제/스탯 차감 금지.
- 기존 SkillEffectContainer 및 pumped modifier 경로에서 실제 MR/특수저항/STR/DEX/INT 보정을 한 번만 계산한다. 수동 증감 경로와 중복되지 않도록 기존 효과 정의와 스탯 저장/갱신 계약을 확인한다. SC_SPMR, SC_OwnCharStatus2, SC_InventoryIcon은 최종 상태 변경 시 기존 L1 규격으로 동기화한다.
- 자료구조는 실제 파티 최대 인원 및 오라 소스 수에 맞는 primitive/fixed-size/재사용 구조를 우선한다. 파티원마다 임시 List/Set/박싱 객체를 생성하지 않는다. 없는 활성 오라 때문에 모든 PC/NPC에 큰 배열을 미리 할당하지 않는다.

### P4. 혈맹 축복 만료 분리 (필수)
- AuraController의 tickClanBuff/expireBlessing을 현재 혈맹 소유 구조로 이관한다. 기존 NC 이름을 사용하려면 실제 동일 책임을 확인한다. 임의로 새로운 스케줄러 또는 쓰레드를 만들지 않는다.
- 현재 활성 혈맹 목록, 감소 단위와 주기(현행 2초 tick에서 1 감소), 기존 만료 판정, generation, pendingOwnerApply, CacheD ordered job, persistBlessingExpiry, completeBlessingExpiry 및 세션 검증 후 회원 효과 제거를 보존한다. 단위를 추정해서 1초/2초 정책을 변경하지 않는다.
- SQL 실패/ACK 유실/재시작/축복 재구매/혈맹 해산·재생성 경쟁을 검증한다. 오라 리팩토링 명목으로 축복 저장 정책 자체를 변경하지 않는다.
- 혈맹 만료는 기존 소유자 실행 체계에서 처리하며 군주 오라 타이머와 독립적으로 작동해야 한다.

### P5. 호출부 이관 및 레거시 제거
- CParty 가입/탈퇴/해산, 기존 스킬 효과 시작/종료, 로그인/로그아웃 및 텔레포트 등 전체 참조를 새 구조로 이관한다.
- AuraController 전체 책임 이관과 검증 완료 후 클래스, trackedCrownIds, 전역 2초 군주 스캔, 전용 start/stop lifecycle 및 불필요한 wrapper 제거.
- Pbavatar/Pbavataron 등 중복 플래그는 실제 독립 의미가 있는지 확인하여 단일 활성/수신 상태를 사용하고 불필요한 필드/호환 메서드를 삭제한다.
- 기존 CIO/timing wheel/스킬 컨테이너/파티/혈맹/CacheD의 공통 구조는 유지한다. 다른 스킬 효과나 전투 공식, 일반 네트워크, NPC AI, 점유 시스템 변경 금지.

## 5. 검증 및 종료 기준

- Java 21 전체 의존성 타입 컴파일 및 기존 스킬/파티/혈맹 회귀.
- 군주 1명, 군주 2명 이상, 동일/상이한 파티, 동일/상이한 맵, 18칸 경계 및 대각선, 자기 자신, 수신자 사망 및 부활.
- 파티 가입/탈퇴/추방/해산, 거리 이탈/복귀, 텔레포트, 오라 시작/해제/재시작, 군주 직업 변경/로그아웃/재접속.
- 복수 군주 중 한 명만 유효한 상태에서 다른 군주의 거리 이탈/해제가 유효 효과를 제거하지 않는지. 최종 출처 해제 후 능력치가 기존 상태로 복귀하는지.
- 동일 효과 반복 적용/해제 1,000회 후 MR/특수저항/STR/DEX/INT 누적 오차 0. 다른 장비/버프/패시브 변화는 보존.
- 서로 다른 CIO owner, stale callback, timer/queue rejection, 대상 session identity 재사용, 파티/스킬 generation 변경의 경쟁 검증. 효과가 영구히 남거나 잘못 해제되지 않도록 한다.
- 혈맹 축복 만료의 기존 DB 및 사용자 알림, 실패/retry/재구매/서버 재시작 테스트.
- 동일 5,000 PC 목표 부하에서 활성 군주 수와 파티 인원 변화에 따른 CPU, 할당률, world lookup, 파티 순회 수, 타이머/owner 이벤트 수, p95/p99 및 효과 지연을 A/B 측정한다. 전역 군주 ID→월드 객체 조회 제거 여부 확인. 실측하지 않은 수치를 결과로 기록하지 않는다.
- 최종 조건: 전역 AuraController 제거, 기존 군주 오라 18칸/2초 및 효과/패킷 유지, 캐릭터 소유 실행, 출처 중복/해제 안전성, 혈맹 축복 유지, NC 명칭 및 전용 패키지. 전체 참조 이관 완료 및 불필요한 호환 코드 0.

## 6. 확인 범위

NC의 활성 오라 등록/게시/ActivateAuraSkill과 실제 파티원 대상 처리, RKern 최신 게임 브랜치의 AuraController/CParty/SkillEffectContainer/PC 상태를 확인했다. NC의 상위 주기 호출 및 만료/중첩 세부 정책, RKern 전체 호출자, 실게임 및 성능 테스트는 미확인이다. 구현 단계에서 최신 전체 checkout으로 검증하고 확인되지 않은 NC 로직을 임의 복원하지 않는다. 이번 커밋은 보고서만 추가한다.
