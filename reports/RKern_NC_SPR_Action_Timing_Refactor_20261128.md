# RKern — NC식 SPR / Action 시간 처리 구조 이관 설계 보고서

작성일: 2026-11-28 (KST)  
대상: Java 21 RKern / L1J  
RKern 기준: `rkern/full-source-20261127`, `0c91cf2de427ffc5bc38c151352e9bdc36074df0` (Isolate operational logging in LogD)  
NC 참고: 공개 Open_LU4_L2OFF 복원 소스, `abbb2e5f474d40cf12539a8e97fddc52474ed95e`  
상태: 소스 비교 및 구현 설계. Java/SQL 수정, 컴파일, 성능 측정은 하지 않았다.

## 1. 결론

리니지1의 클라이언트 SPR 프레임 데이터를 유지하고, NC의 **캐릭터 파라미터 계산과 Action 실행 분리**를 도입한다. 공격·이동·마법 실행 때마다 프레임 조회, 레벨 구간 계산, 속도 보정을 다시 하지 않도록 상태 변경 시 계산한 최종 시간을 재사용하는 방향이다.

이는 L2OFF의 계산식을 그대로 포팅하거나 NC에서 동일한 SPR 캐시를 발견했다는 뜻이 아니다. L2는 3D 애니메이션을 사용하는 게임이며, 확인된 NC 구조를 L1의 모션 데이터에 맞추는 설계다. 특히 **기존 즉시 타격/시전 시점, 패킷, 모션 길이, 자동공격 주기, 이동 판정은 변경하지 않는다.** NC식 지연 타격을 도입하려면 별도의 게임플레이 승인과 검증이 필요하다.

## 2. NC 실행 구조 — 확인된 사실

| 실제 함수/데이터 | 동작 |
|---|---|
| CSharedCreatureData의 m_dAttackTime | 캐릭터 데이터에 보관된 전체 공격 시간(초) |
| CAttackAction::GetHitTimeFactor | PC의 공격 유형 및 무기 슬롯을 기준으로 타격 비율을 선택; NPC는 m_dHitTimeFactor 사용 |
| CAttackAction::GetTimeData | 전체 시간 × 1000, 타격/후딜 및 이도류 두 번째 타격 구간 계산 |
| CAttackAction::OnStart | 공격 방송 이후 ATK_HITTIME으로 전환하고 타이머 등록 |
| CAttackAction::OnHitTimeNotDual / OnFirstHitTimeForDual / OnSecondHitTimeForDual | 실제 타격 단계별 실행 |
| CSkillAction2::CalcTimeFactor | 물리/마법/기타 스킬 구분, 캐릭터 속도 파라미터 및 시전 보너스 반영 |
| CSkillAction2::CalcSkillTiming | skill_hit_time, skill_cool_time, skill_hit_cancel_time 및 속도 계수를 이용하여 실행 구간 계산 |
| ParserSkill.y / LexerSkill.re | 스킬 정의의 시간, 취소 시간, 재사용 대기시간을 파싱 |

일반 공격은 기본적으로 fullTime = m_dAttackTime × 1000, hitTime = fullTime × factor, coolTime = fullTime의 정수 변환값 - hitTime이며 이도류는 hitTime을 다시 분할한다. 스킬은 일반 분기에서 skill_hit_time × 1000 / timeFactor - cancelTime을 0 이상으로 제한하고, 별도로 취소 시간과 coolTime을 산출한다. 채널링과 특수 조건에는 별도 분기가 있다. NC의 타이머 및 액션 상태 분리는 확인했으나, m_dAttackTime의 전체 갱신 호출 그래프와 NC의 모든 캐시 정책까지 확인한 것은 아니다.

근거:
- [NewAction.cpp:645–688](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/NewAction.cpp#L645-L688)
- [NewAction.cpp:1368, 1483, 1574](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/NewAction.cpp#L1368)
- [NewActionSkill.cpp:910–987](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/NewActionSkill.cpp#L910-L987)
- [Skill.h:695](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Skill.h#L695)
- [ParserSkill.y:2561](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/ParserSkill.y#L2561)
- [SharedFactory.h](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/Shared/src/SharedFactory.h)

## 3. RKern 현재 구현 — 최신 기준 직접 확인

### 3.1 데이터 흐름

```text
spr_info / spr_action / tb_magicdelay + polyframerate.xml
  -> SpriteInformationLoader
  -> SpriteInformation / ActionInformation / FrameElement
  -> EActionCodes + ActionIntervalDecoratorFactory
  -> PC/NPC Action 및 SkillDelayPolicy
```

SPR 데이터는 이미 시작 시 메모리에 적재된다. 매 공격마다 SQL을 실행하는 구조가 아니다. SpriteInformation은 공유되는 가변 길이 ActionInformation[]으로 O(1) 조회한다. 따라서 자료구조를 맵에서 배열로 바꾸는 일을 반복할 필요가 없다.

FrameElement는 기본 모션 길이(밀리초)를 미리 계산하지만 Double 필드를 갖고, 레벨별 모션 길이는 필요할 때 프레임 수와 레벨별 프레임 계수를 곱한다. ActionInformation은 Double[] userFrames를 보유한다. getFramePerSecond라는 이름과 달리 소비 경로에서 이 값은 시간(ms)이다.

### 3.2 실제 호출 경로

| 파일/라인 | 현재 동작 |
|---|---|
| [L1Character.java:2391](https://github.com/choeyeonggeol16-sketch/1234/blob/0c91cf2de427ffc5bc38c151352e9bdc36074df0/rkern/src/l1j/server/server/model/L1Character.java#L2391) | getCurrentSpriteInterval -> 현재 SpriteInformation.getInterval |
| [L1PcInstance.java:6890](https://github.com/choeyeonggeol16-sketch/1234/blob/0c91cf2de427ffc5bc38c151352e9bdc36074df0/rkern/src/l1j/server/server/model/Instance/L1PcInstance.java#L6890) | PC 오버라이드에서 PC 전용 모션 계산 |
| [SpriteInformation.java:77–131](https://github.com/choeyeonggeol16-sketch/1234/blob/0c91cf2de427ffc5bc38c151352e9bdc36074df0/rkern/src/l1j/server/server/model/character/animation/SpriteInformation.java#L77-L131) | 누락 액션 fallback, 특수 SPR 보정, 레벨 구간, decorator |
| [CMoveAction.java:84](https://github.com/choeyeonggeol16-sketch/1234/blob/0c91cf2de427ffc5bc38c151352e9bdc36074df0/rkern/src/l1j/server/server/model/character/action/CMoveAction.java#L84) | 무기 기반 이동 액션의 최종 interval을 cadence에 전달 |
| [CAttackAction.java:151–213](https://github.com/choeyeonggeol16-sketch/1234/blob/0c91cf2de427ffc5bc38c151352e9bdc36074df0/rkern/src/l1j/server/server/model/character/action/CAttackAction.java#L151-L213) | 무기+1 공격 액션의 interval, nextAttackTimeMs, 즉시 공격, 자동공격 타이머 |
| [SkillDelayPolicy.java:28–82](https://github.com/choeyeonggeol16-sketch/1234/blob/0c91cf2de427ffc5bc38c151352e9bdc36074df0/rkern/src/l1j/server/server/model/character/skill/core/SkillDelayPolicy.java#L28-L82) | 공격 대상 스킬(18 제외)은 spell_dir, 그 외 spell_nodir; SPR 계산 후 network tolerance, 최소 100ms, int 상한 |
| [SpriteInformationLoader.java:313–318](https://github.com/choeyeonggeol16-sketch/1234/blob/0c91cf2de427ffc5bc38c151352e9bdc36074df0/rkern/src/l1j/server/server/model/character/animation/loader/SpriteInformationLoader.java#L313-L318) | tb_magicdelay 별도 조회 및 PC 공격속도 보정 |
| [NPC CAttackAction.java](https://github.com/choeyeonggeol16-sketch/1234/blob/0c91cf2de427ffc5bc38c151352e9bdc36074df0/rkern/src/l1j/server/server/model/npc/ai/action/CAttackAction.java) | 공격 tick -> creature.attackTarget(target); effect/skill 우선순위 유지 |
| [NPC CSkillAction2.java:245](https://github.com/choeyeonggeol16-sketch/1234/blob/0c91cf2de427ffc5bc38c151352e9bdc36074df0/rkern/src/l1j/server/server/model/npc/ai/action/CSkillAction2.java#L245) | NPC 스킬 모션에서도 SPR 시간 조회 |

### 3.3 반드시 보존할 세부 분기

- PC 특수 SPR 12015, 5641, 11685, 14491은 이동/공격/피격/호흡을 기본 액션으로 매핑한다.
- SprBoundary에 따른 레벨 경계 강제, polyframerate.xml의 레벨 구간 및 예외를 유지한다.
- EActionCodes의 무기별 및 특수 액션, 기본 24fps fallback, 지원하지 않는 액션의 현행 처리까지 그대로 유지한다. 숫자 액션 ID 중 별칭이 있으므로 enum과 ID 의미를 감사하기 전 단순한 배열 통합을 하지 않는다.
- ActionIntervalDecoratorFactory의 PC/NPC별 계산 순서와 부동소수점 반올림/절삭을 보존한다. PC haste/slow 우선순위, 2단 가속, 엘븐 가속, 3단 가속, focus wave 레벨, fruit 및 DARK_HORSE 패시브, WIND_SHACKLE, 컴패니언/DogFight의 이동·공격 감소율이 실제 입력이다. PC 이동과 공격의 보정식은 동일하지 않다.
- tb_magicdelay의 스킬별 시간, 방향/비방향 마법 gate, skill reuse/cooldown은 서로 다른 개념이다. 강제 이동, 피격, 연속 타격, 특수 NPC 액션에 공통 공격 시간을 잘못 적용하지 않는다.

추가 근거:
- [ActionInformation.java](https://github.com/choeyeonggeol16-sketch/1234/blob/0c91cf2de427ffc5bc38c151352e9bdc36074df0/rkern/src/l1j/server/server/model/character/animation/ActionInformation.java)
- [EActionCodes.java](https://github.com/choeyeonggeol16-sketch/1234/blob/0c91cf2de427ffc5bc38c151352e9bdc36074df0/rkern/src/l1j/server/server/model/character/animation/EActionCodes.java)
- [ActionIntervalDecoratorFactory.java](https://github.com/choeyeonggeol16-sketch/1234/blob/0c91cf2de427ffc5bc38c151352e9bdc36074df0/rkern/src/l1j/server/server/model/character/animation/intervaldecorator/ActionIntervalDecoratorFactory.java)
- [SpriteInformationLoader.java](https://github.com/choeyeonggeol16-sketch/1234/blob/0c91cf2de427ffc5bc38c151352e9bdc36074df0/rkern/src/l1j/server/server/model/character/animation/loader/SpriteInformationLoader.java)
- [ActionSpriteMapper.java](https://github.com/choeyeonggeol16-sketch/1234/blob/0c91cf2de427ffc5bc38c151352e9bdc36074df0/rkern/src/l1j/server/server/model/npc/ai/ActionSpriteMapper.java)

## 4. 목표 구조 — L1 적응안

```text
기존 SPR 정의 (변경하지 않음)
      |
      v
기본 프레임 시간 + 현재 캐릭터 파라미터
      |
      v
캐릭터 소유 실행 컨텍스트에서 최종 행동 시간 계산/무효화
      |
      v
기존 PC/NPC Controller와 Action이 해당 시간 재사용
      |
      v
기존 CIO Timing Wheel / 이동 cadence / 스킬 gate
```

초기 구현에서는 기존 L1Character 또는 이미 존재하는 캐릭터 파라미터 소유부를 사용한다. 새로운 스레드, 스케줄러, 큐, 캐릭터별 Action 객체 또는 별도 캐시 매니저를 만들지 않는다. NC 이름은 실제 역할이 일치하는 경우에만 적용한다.

### 4.1 저장 방식

예시: 현재 무기에 대응하는 이동·일반공격·피격 시간 및 방향/비방향 마법 시간을 primitive 필드로 보관할 수 있다. 필드는 의미 예시이며 최종 개수·타입은 호출 경로 감사 결과로 확정한다. 현행 API는 long을 반환하므로 int 압축은 범위/절삭 동등성이 검증된 경우에만 한다. 자주 쓰지 않는 액션은 기존 경로로 계산한다. 캐릭터마다 256칸 배열을 만들지 않는다.

먼저 공용 불변 기본 프레임 시간 및 레벨별 시간을 사전 계산할 수 있는지 확인하고, 최종 캐릭터별 캐시가 이득인지 별도로 측정한다. 5개 int만으로도 객체 정렬 및 상속 필드 배치에 따라 실제 retained heap 증가가 달라지므로 JOL/JFR 등으로 측정한다. 캐시를 구현하더라도 모든 특수 액션을 다섯 값에 강제로 대응시키지 않는다.

### 4.2 갱신과 동시성

필수 무효화 후보: 변신/해제, sprite 참조 갱신, 무기 착탈 및 스왑, 레벨 구간 변화, haste/slow 및 모든 가속 버프 시작·종료·해제, focus wave 레벨, passive 취득/삭제, fruit 상태, WIND_SHACKLE, NPC/컴패니언 감소율 변화, 로그인/재접속 및 NPC 재사용, SPR/XML 재로딩. 정확한 setter와 직접 필드 쓰기 경로는 구현 전에 전수 추적한다.

기존 CIO/NPC 소유 실행 경로에서 관련 상태 변경과 시간 갱신을 일관되게 완료한다. 다른 스레드가 상태를 변경하는 경로를 조사해 적절한 기존 소유 실행 경로로 연결하거나 정확한 publication을 보장한다. 단순히 volatile 필드 여러 개를 추가한다고 일관성이 생기는 것은 아니다. 소유권 증명이 어려운 경로는 현행 계산을 유지한다. 기존 Action에서 이미 확정한 타이머와 nextAttackTimeMs를 임의로 앞당기거나 재등록하지 않는다.

SPR reload는 현재 loader 교체 후 oldInstance.clear()를 호출한다. 살아 있는 캐릭터의 _currentSpriteInfo 참조, 구 세대 테이블 수명, static 레벨 구간 배열, tb_magicdelay 독립 reload를 별도로 감사해야 한다. 새 캐시가 세대가 바뀐 뒤 낡은 시간 값을 사용하면 안 된다. 새 테이블 완전 구축/공개와 소유 측 갱신을 보장하고, 기존 reload 의미와 오류 처리를 비교한다. 지금 확인한 소스만으로 reload 결함을 확정하지 않는다.

## 5. 구현 단계

1. **의존성 및 호출 감사.** 모든 getCurrentSpriteInterval/getInterval/getUseSpellInterval 호출, PC/NPC 이동·공격·피격·마법·특수 액션, decorator 입력 getter 및 setter/직접 쓰기, reload 경로를 인벤토리화한다. 고빈도 호출과 동적 상태를 구분하고 실제 CIO 소유권을 확인한다.
2. **공용 데이터 최적화.** 기본 시간과 레벨별 기본 시간의 primitive 표현/사전 계산을 검토한다. 현행 double 연산 순서, enum alias, fallback, reload를 보존한다. 전체 캐릭터별 캐시보다 먼저 저위험 변경을 비교한다.
3. **PC 행동 시간 캐시.** 기존 소유 구조에서 고빈도 이동·공격·마법 시간을 무효화/갱신한다. 기존 계산과 결과가 정확히 일치할 때만 해당 읽기 경로를 교체한다. SkillDelayPolicy의 최소/최대값, 네트워크 tolerance, null/예외 fallback과 tb_magicdelay 독립 경로 유지.
4. **NPC 확장.** NPC/컴패니언/DogFight의 계산 분기, ActionSpriteMapper와 특수 모션, 이동 점유·추격·타겟 변경·랜덤워크, 상태 재사용을 보존한다. PC와 같은 공식으로 강제 통합하지 않는다.
5. **회귀·부하 검증 후 채택.** 실측 이점이 있는 변경만 기본 경로에 반영한다. 불필요한 실험용 JVM 옵션을 운영에 남기지 않는다.

## 6. 검증 기준

- **결과 동등성:** 원본/수정본 모든 지원 SPR/액션, 무기, 레벨 경계, 특수 SPR, 버프 조합, 중첩 패시브, 컴패니언 감소율, 누락 액션, 스킬 ID 18, tb_magicdelay 존재/미존재에서 최종 시간 및 gate 비교. 유효한 값은 원본 long/int 결과와 정확히 일치.
- **상태 전환:** 변신/해제, 무기 스왑, 버프 만료, 레벨업, 사망/부활, 텔레포트, logout/relogin, NPC respawn/reuse, reload 도중 실행. stale Action 및 타이머 중복/누락 없음.
- **게임 동작:** 기존 공격·피격 대미지 발생 시점, 이동 제한, 타겟 유효성, 패킷 내용 및 순서, 스킬 소모와 재사용 제한 동일. NPC 1대1 통로, 타겟 변경, 랜덤워크 3~6칸 후 6초 휴식 유지.
- **성능:** 동일 JDK21 및 동일 게임 데이터로 warmup 후 반복 A/B. interval 조회 CPU ns/op, 할당 B/op, 캐시 갱신 CPU, GC/JFR, retained heap, CIO tick p95/p99, 처리량을 기록. 고빈도 읽기만 있는 시험과 버프/무기 변경이 빈번한 혼합 시험을 분리. 동접 5000에 준하는 게임 부하 및 실제 인게임 검증은 별도로 수행.
- **승인 조건:** 모든 동등성 테스트 통과, 전체 타입 컴파일 및 통합 테스트, 동시성 검토, 실제 성능 개선. 효과가 없거나 캐시 갱신 비용이 크거나 stale 상태 위험이 제거되지 않으면 채택하지 않는다.

## 7. 수정 범위 및 미검증 사항

이 보고서만 추가한다. 서버 Java, DB 스키마, XML/SPR 데이터, 패킷, 네트워크, 거래, CacheD는 변경하지 않는다. NC식 타격 분할과 시전 취소 모델은 게임 동작을 바꾸므로 이번 구현 범위에서 제외한다. 변경 대상은 전체 호출 감사 후 결정하며, 클래스명 변경만을 목적으로 새 클래스나 중복 API를 만들지 않는다.

현재까지 확인한 것은 NC 복원 소스의 시간 산출/Action 실행 구조, RKern의 주요 SPR 조회 및 속도 보정 경로다. 전체 setter 호출 그래프, JIT 최적화 및 실제 할당률, reload 동시성, 전체 게임 성능은 미검증이다. boxing이 소스에 존재해도 실제 hot-path 할당이 발생한다고 단정하지 않는다. 정량적 성능 개선 수치는 아직 없다.
