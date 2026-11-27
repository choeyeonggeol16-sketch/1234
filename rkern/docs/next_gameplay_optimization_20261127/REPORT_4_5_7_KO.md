# RKern 후속 게임 로직 최적화 기술 보고서 — 4·5·7번

- 작성일: 2026-11-28 (KST)
- 검토 기준: [ecc50c4323750cb54b737d200548d1e0f55164fd](https://github.com/choeyeonggeol16-sketch/1234/tree/ecc50c4323750cb54b737d200548d1e0f55164fd), 브랜치 `rkern/full-source-20261127`
- 기존 1·2·3·6번 보고서: [REPORT_KO.md](./REPORT_KO.md)
- 범위: 4. 파티 / 5. 혈맹 버프 / 7. 캐릭터 이름 검색
- 상태: 관련 생산 소스 정적 검토 및 구현 계획. 이번 커밋에서는 보고서만 추가한다.

## 0. 목표와 우선순위

| 번호 | 영역 | 주요 비용/위험 | 권장 우선순위 |
|---|---|---|---|
| 4 | 파티 | 매번 멤버 배열 복사, boxed map, 멤버십 전이 정합성 | P1 |
| 5 | 혈맹 버프 | 비활성 혈맹까지 반복 락/조회, 시간 단위 및 만료 저장 경계 | P1 (정합성 우선) |
| 7 | 캐릭터 이름 검색 | 차단 요청마다 전체 캐릭터 배열 생성 및 문자열 비교 | P1 (작고 독립적인 변경) |

기존 CWorld/CacheD/ProtoWriter/CIO 타이머와 소유 실행 구조를 우선 활용한다. 새 스레드·공통 큐를 추가하지 않는다. 리니지1의 파티 분배·혈맹 효과·차단 정책과 기존 저장/패킷 호환성을 유지한다. 우선순위와 예상 비용은 정적 분석에 따른 제안이며 실측 성능 순위가 아니다.

## 4. 파티 — CParty

### 확인된 구현

- [CParty.java:37](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/CParty.java#L37): 멤버는 CopyOnWriteArrayList, 표식은 ConcurrentHashMap<Integer,Integer>, 전체 리스트 송신 쿨다운은 ConcurrentHashMap<Integer,Long>으로 관리한다.
- [CParty.java:351](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/CParty.java#L351): getMembers()는 호출마다 `_membersList.toArray(L1PcInstance[]::new)`로 새 배열을 만든다. broadcastRaw/broadcastOwned(75~80행), 파티 주기 상태 동기화(268~272행), 상태 갱신, 분배 등 여러 경로에서 호출한다.
- [CParty.java:112](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/CParty.java#L112): 정원 확인→중복 확인→리더 지정→표식 등록→멤버 추가→PC party 참조 설정이 별도 단계로 수행된다. removeMember(166행), breakup 경로(286행), passLeader(311행)도 복합 변경이다. COW list는 개별 연산에 안전하지만 전체 멤버십 전이의 원자성을 보장하지 않는다. 상위 호출부가 충분히 직렬화하는지 아직 확인되지 않았으므로 실제 경쟁 재현 전에는 확정 버그라고 단정하지 않는다.
- [CParty.java:409](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/CParty.java#L409): Route는 사망 당시 roster를 보존하고 현재 파티/맵/거리/기여도 조건을 재검사한다. 417~423행에서 작업 배열을 제자리 압축한다. 이 경로에 공유된 배열을 그대로 반환하면 roster가 훼손된다.
- [CParty.java:64](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/CParty.java#L64): 전체 LIST 쿨다운은 get/put이 분리돼 있으며 현재 wall clock 기반이다. 멤버별 primitive deadline 후보이지만 시간 정책과 동시성 조건을 검토해야 한다.
- [CParty.java:367](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/CParty.java#L367): getList()가 실제 멤버 리스트를 노출한다. 외부 변경 호출부를 찾기 전에는 반환 타입 또는 동작을 바꾸면 안 된다.
- [SC_PARTY_SYNC_PERIODIC_INFO.java:58](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/SC_PARTY_SYNC_PERIODIC_INFO.java#L58) 및 [SC_PARTY_MEMBER_LIST.java:137](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/SC_PARTY_MEMBER_LIST.java#L137): 이미 ProtoWriter를 통해 멤버 필드를 직접 직렬화하며 CSharedBroadcast의 공유 송신을 사용한다. DTO/개별 패킷 객체를 재도입하지 않는다.
- [L1ChatParty.java:16](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/L1ChatParty.java#L16): 채팅 파티에는 volatile 멤버 배열 및 membership 직렬화가 이미 존재한다. 일반 파티 전환 시 참고할 내부 구현 사례이며 채팅 파티 자체를 다시 수정할 필요는 없다.

### 개선 설계

1. 멤버 추가/탈퇴/추방/리더 변경 시에만 새 roster를 발행하는 immutable snapshot 또는 volatile 배열을 검토한다. 읽기 경로에서 같은 배열을 재사용하고 내부 계약은 읽기 전용으로 제한한다. 공개 getMembers()/getList()의 호출부를 전수 조사하고 실제 mutable 복사가 필요한 경로만 별도 복사한다.
2. 작은 파티의 특성에 맞춰 멤버 index와 primitive mark/cooldown 배열을 검토한다. 정원은 Config.MAX_PT 및 GM 예외를 유지하며 확장/축소 시 멤버·표식·deadline의 연관성을 보존한다. 단순히 최대 정원만큼 배열을 모든 파티에 과다 선할당하지 않는다. 표식 변경 시 매번 전체 roster를 복사하는 비용도 평가한다.
3. 가입/탈퇴/리더/PC party 참조를 같은 멤버십 전이에서 확정한다. 동일 PC의 다른 파티 동시 가입도 막을 수 있는 기존 실행 소유권/락 순서를 확인한다. 구조 락 밖에서 상태 알림, 스킬 제거, 아우라 변경, 송신을 실행하되 이전 roster의 지연 알림이 새 소속을 훼손하지 않도록 세션/파티 정체성을 검증한다.
4. 분배는 사망 시점의 party identity/roster 및 기여도 snapshot, 현재 유효성 재검사를 유지한다. 읽기 전용 roster는 제자리 수정하지 않는다. 기존 bounded scratch를 사용할 수 있는지 검토하되 동기·비동기 경계에서 참조가 누출되지 않게 한다. 아데나 분배·일반 아이템·아이템 수용 실패 및 바닥 회수 경로를 변경하지 않는다.
5. full list, 변경 알림, 주기 상태, 표식, 리더 위임, 아우라, 큐브 효과 및 파티 해산 UI의 패킷 의미를 보존한다. ProtoWriter의 현재 direct fields 경로를 유지하고 primitive mark를 읽는 overload 또는 기존 메서드의 최소 변경을 검토한다.

### 비용과 검증

현재 멤버 수 P, roster 조회 Q회라면 Q개의 배열 할당 및 O(Q×P) 참조 복사가 발생한다. 개선은 변경 횟수 M에 비례하는 roster 복사와 실제 작업용 scratch로 제한하는 것이 목표다. 조회 자체의 비용 감소이며 파티원에게 전송하는 O(P) 작업이 없어지는 것은 아니다.

- 파티 1→2→정원, GM 정원 예외, 동시에 초대 수락/탈퇴/추방, 서로 다른 파티 동시 가입, 리더 변경/해산, logout/reconnect, 이전 세션의 지연 작업.
- 파티 mark/cooldown 멤버 연관성, 패킷 필드/순서, 기존 공유 SEND, 아우라·큐브 적용/해제와 남아 있는 stale roster.
- 동시 몬스터 사망, 분배 중 멤버 변경/맵 이동/사망, 원본 roster 비변경, 분배 총량 일치 및 거래·CacheD receipt 회귀.
- 파티원 2/4/8명(실제 Config 및 GM 예외 별도), 동일 상태 갱신·분배 부하에서 CPU, 배열/박싱 할당, p99, lock wait 비교.

## 5. 혈맹 버프 — AuraController의 clan blessing

### 확인된 구현

- [AuraController.java:30](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/Controller/AuraController.java#L30): TICK_MS=2000. CServerTimer.fixedDelaySignal에서 CWorldWork로 작업을 제출하며 중복 실행을 coalesce한다(49~79행). run()은 tickAura() 후 tickClanBuff()를 호출한다(106~119행).
- [AuraController.java:268](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/Controller/AuraController.java#L268): 모든 혈맹을 순회하며 각 clan monitor를 획득하고 CacheD pending owner apply를 검사한 뒤 bless가 0인 혈맹을 건너뛴다. 실제 활성 혈맹만 처리하는 인덱스가 없다.
- [AuraController.java:281](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/Controller/AuraController.java#L281): 2초 주기의 성공한 tick마다 활성 슬롯의 남은 값을 1 감소시킨다. coalesce/거부/pending apply로 tick을 놓칠 수 있다. [CS_Craft.java:224](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/network/core/cs/packet/CS_Craft.java#L224)는 기본값 172800을 설정하고 240~241행에서 스킬 지속시간에 전달한다. 정확한 게임 설계 시간 단위(초인지 기존 tick 단위인지) 및 스킬 타이머와의 일치 여부는 검증 대상이다. 자동으로 2배속 보정하면 기존 밸런스를 바꿀 수 있으므로 무단 변경 금지.
- [AuraController.java:283](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/Controller/AuraController.java#L283): 만료 시 clan 락을 보유한 상태에서 온라인 멤버 알림, 스킬 제거, bless=0 변경, DB 저장 제출을 수행한다. 예외를 격리하지만 stale 만료/새 활성화 경합 및 락 보유 시간 검증이 필요하다.
- [L1Clan.java:749](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/L1Clan.java#L749): 4개 잔여 시간 슬롯과 활성 bless를 관리한다. getBuffTime()은 내부 배열을 직접 노출한다. 활성 슬롯만 감소하며 나머지 슬롯의 잔여 시간을 보존하는 정책이다.
- [CS_Craft.java:212](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/network/core/cs/packet/CS_Craft.java#L212): 혈맹 버프 활성화/전환은 권한 확인, 포인트 차감, ClanBuffImage, L1InventoryTransaction을 사용한다. 227~244행에서 commit 후 메모리 상태와 온라인 멤버 효과를 반영한다. [CS_Craft.java:268](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/network/core/cs/packet/CS_Craft.java#L268)은 DB row lock 및 preimage 검사를 수행한다. 잔여 시간 슬롯 자체는 SELECT preimage에 없고 UPDATE에는 포함된다.
- [ClanTable.java:110](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/datatables/ClanTable.java#L110): 부팅 시 bless와 4개 시간을 로드한다. [ClanTable.java:893](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/datatables/ClanTable.java#L893)의 updateBless는 CacheD ordered job에서 bless만 갱신하고, 907행 이후 updateBuffTime은 4개 시간 슬롯을 별도 SQL로 갱신한다. 만료 후 이전 시간 값이 DB에 남거나 오래된 저장이 뒤늦게 적용되는지 전체 저장 호출부/ordered lane 계약을 확인한다. 만료 시 두 필드군이 반드시 원자적으로 저장된다고 단정하지 않는다.
- [CS_EnterWorld.java:746](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/network/core/cs/packet/CS_EnterWorld.java#L746): 로그인 시 활성 bless 및 저장된 잔여 시간으로 스킬을 적용한다. [L1Clan.java:546](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/L1Clan.java#L546)의 현재 세션 검증 및 595행 온라인 멤버 조회를 그대로 활용할 수 있다.
- 혈맹 주문서 효과(PcClanBuffEffects/ClanBuffScrollTransactions), 아인하사드 선택형 버프(EinhasadBlessBuff) 및 군주 아우라는 다른 실행 책임이다. 현재 4슬롯 clan blessing 최적화와 혼동하거나 전체 시스템을 강제로 합치지 않는다.

### 개선 설계

1. 첫 단계는 현재 시간 단위와 차감 정책을 보존하면서 활성 bless가 존재하는 혈맹만 등록/순회하는 primitive clanId/identity registry로 대상 축소. CClanRegistry 또는 기존 소유 구조에 결합 가능한지 검토하고 별도 worker는 추가하지 않는다.
2. 등록/해제는 commit 후 활성화·전환, 부팅 복원, 강제 만료, 혈맹 해산·reload·동일 ID 교체, stop/restart와 함께 유지한다. id만으로 만료하지 않고 실제 clan identity 및 activation generation을 검증한다.
3. 두 번째 단계로 현재 게임 규칙이 확인되면 단조 시간 기반 만료 또는 기존 CIO Timing Wheel의 활성 버프 예약을 검토한다. 활성 슬롯만 소진하고 비활성 슬롯의 시간은 멈춘다는 기존 의미를 보존한다. 오프라인 혈맹, 서버 중단 기간, 시간 변경, pause/resume 정책을 명확히 정의한다. CGameTime의 6배속 게임 시간은 실제 지속시간과 혼용하지 않는다.
4. 잔여 시간/활성 번호의 DB 확정과 runtime 상태를 하나의 일관된 만료 전이로 설계한다. 기존 CacheD owner lane/receipt/pending apply를 우회하지 않는다. 이전 만료가 새 버프를 지우지 않도록 generation/preimage 및 저장 순서를 검사한다. DB rejection·실패 시 만료를 잃거나 무한 중복 알림을 하지 않는다.
5. 스킬 제거/패킷 발송은 가능한 한 짧은 clan 상태 락에서 분리하고 기존 PC owner 실행 경로를 사용한다. 알림 대상의 현재 세션 및 소속, buff generation을 재검사한다. 지연된 만료 작업이 새로 부여된 동일 스킬을 제거하지 않게 한다.
6. 로그인, 재접속, 혈맹 이동, 포인트 사용, GM 설정, 버프 변경, 서버 종료 저장을 전수 추적한다. getBuffTime 내부 배열 노출과 직접 변경 호출부를 제거/제한하는 작업은 호출 계약을 확인한 뒤 진행한다.

### 비용과 검증

전체 혈맹 C, 활성 혈맹 B일 때 정기 검사 비용 O(C) → O(B) 목표. deadline 방식은 실제 만료/전환 수에 비례하게 설계할 수 있다. 예를 들어 혈맹 1,000개 중 50개 활성이라면 주기 검사 횟수는 1,000→50으로 줄 수 있다(가정, 실측 아님).

- 활성 4종 전환, 남은 시간 0/1/최대값, 부팅·종료·복원, 비활성 슬롯 보존, 워커 지연/거부/pending owner apply, CGameTime 변경과 독립적인 시간 차감.
- 포인트 중복 차감, 만료와 재활성화 동시 실행, 동일 clan ID 교체/해산, logout/login, ACK loss/DB 재시작, stale SQL 및 member callback.
- 효과 지속시간, 로그인 버프, 아이콘/알림, 실제 능력치 적용/제거를 원본과 비교하고 정책 변경 시 명시 승인.
- 0/5/100% 활성 조건에서 clan monitor 진입 횟수, CPU, p99 tick delay, SQL 요청 및 정확한 만료율 측정.

## 7. 캐릭터 이름 검색 — CharacterTable / CS_Craft

### 확인된 구현

- [CS_Craft.java:362](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/network/core/cs/packet/CS_Craft.java#L362): 차단 목록 요청은 자신 차단을 equalsIgnoreCase로 거부하고, 기존 차단 여부를 검사한 후 신규 대상 검색을 진행한다.
- [CS_Craft.java:392](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/network/core/cs/packet/CS_Craft.java#L392): getCharNameList()를 호출해 저장된 전체 캐릭터 이름을 equalsIgnoreCase로 순회한다. 찾으면 정식 이름과 ID로 차단 정보를 등록한다. 접속자만 검색하는 기능이 아니므로 CWorld 온라인 이름 조회로 대체할 수 없다.
- [CharacterTable.java:295](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/datatables/CharacterTable.java#L295): getCharNameList()는 매 호출마다 전체 값 배열을 생성한다.
- [CharacterTable.java:40](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/datatables/CharacterTable.java#L40): 이미 이름→L1CharName ConcurrentHashMap 및 primitive ID→L1CharName 인덱스를 보유한다. [CharacterTable.java:129](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/datatables/CharacterTable.java#L129)의 getCharName은 exact-case 문자열 조회다. 기존 equalsIgnoreCase 정책을 그대로 대체하지 못한다.
- [CharacterTable.java:55](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/datatables/CharacterTable.java#L55): 생성/저장 경로, 73행의 commit 이후 등록, 108행 삭제가 두 인덱스를 갱신한다. [CharacterTable.java:241](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/datatables/CharacterTable.java#L241)는 완전한 임시 인덱스 구축 후 clear/putAll로 발행한다. name map의 일반 읽기와 갱신이 겹치면 중간 상태를 볼 수 있으므로 runtime reload가 가능할 경우 검증한다.
- [CS_Craft.java:818](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/network/core/cs/packet/CS_Craft.java#L818): 차단 INSERT/DELETE는 이미 CacheD 캐릭터 ordered lane에 위임한다. packet worker에서 직접 SQL을 실행하는 구조로 오판하지 않는다.
- [CWorldUsers.java:49](https://github.com/choeyeonggeol16-sketch/1234/blob/ecc50c4323750cb54b737d200548d1e0f55164fd/rkern/src/l1j/server/server/model/CWorldUsers.java#L49): 온라인 이름은 이미 Locale.ROOT 정규화된 키로 직접 조회한다. 오프라인 저장 캐릭터 이름 레지스트리는 별도 책임이다.

### 개선 설계

1. 기존 CharacterTable 내부에서 대소문자 무시 조회를 제공한다. 후보는 case-insensitive ordered map 또는 기존 이름 정책에 맞는 정규화된 key index다. String.CASE_INSENSITIVE_ORDER/equalsIgnoreCase와 Unicode upper/lower-case 및 DB collation이 모든 경우 동일하지 않음을 고려한다. 이름 정책·허용 문자·DB 고유 제약을 확인하기 전에는 Locale.ROOT 변환만으로 의미가 동일하다고 가정하지 않는다.
2. exact-case 조회를 사용하는 로그인/생성/삭제/친구/우편 등의 기존 계약은 유지한다. 대소문자 무시 검색만 필요한 차단 기능에 명확한 메서드를 적용한다. 가능하다면 중복 문자열/인덱스 추가에 따른 retained heap을 비교하여 최소 구조를 채택한다.
3. 이름 충돌(대소문자만 다른 이름, Unicode, DB collation), 이름 변경·생성·삭제·ID 재사용 및 reload 중 lookup의 결정적 동작을 정의한다. DB가 이미 충돌을 거부하는지 확인하고 충돌이 존재하면 임의로 대상 캐릭터를 고르거나 덮어쓰지 않는다.
4. 기존 생성 commit 이후 등록과 삭제 성공 이후 제거를 보존한다. 두 인덱스의 갱신 순서 및 동시 reload/생성에서 새 등록이 유실되지 않도록 검증한다. L1CharName이 mutable인 점(templates/L1CharName.java)을 고려해 published key와 값의 이름/ID 불일치 방지.
5. CS_Craft.handleExclude의 전체 배열+for loop를 직접 조회로 대체하되 기존 정식 이름, 대상 ID, self-block, subtype, toggle, 패킷 메시지와 CacheD lane을 유지한다. 전체 이름 목록이 실제로 필요한 관리 기능까지 제거하지 않는다.
6. 차단 목록 메모리 갱신과 비동기 SQL admission/실패의 일치 여부는 검증한다. 실패 시 잘못된 클라이언트 성공 응답이나 DB/메모리 불일치가 발생하지 않도록 기존 정책을 확인한다. 이번 변경은 차단 시스템 전체 재작성이나 DB 스키마 변경이 아니다.

### 비용과 검증

저장 캐릭터 N명 기준 현재 매 신규 차단 검색은 O(N) 배열 생성 및 최악 O(N) 문자열 비교. 직접 인덱스 조회는 해시 기반 평균 O(1) 또는 ordered index O(log N) 후보. 예시 100,000명의 전체 참조 배열은 compressed oops 4B 가정 시 참조부만 약 400KB(헤더/정렬 제외)이며, 기존 구조의 실제 캐릭터 수·JVM 설정은 미측정이다. 정상/없는 이름 조회의 allocation 감소를 직접 검증한다.

- 정확한 이름, 대소문자 변형, 한글 및 허용 Unicode, 없는 캐릭터, 동일 이름 충돌, 본인 차단, 차단 해제, 오프라인 대상, 정식 표시 이름/ID.
- 생성 성공·실패, 삭제, 이름 변경, 인덱스 재로딩 실패/성공, 동시 갱신, DB 저장 거부 및 재접속.
- 10k/100k/1M 이름의 성공·실패 lookup과 갱신, CPU/조회, allocation, retained heap, p99 비교. 전체 서버 처리량 개선율로 외삽하지 않는다.

## 구현 순서 및 합격 기준

- 7번은 범위가 작고 독립적이므로 먼저 적용 가능하다. 이후 4번 파티, 5번 혈맹 버프를 권장한다. 5번에서 시간/저장 결함이 재현되면 해당 수정은 우선한다.
- 각 작업 착수 시 최신 branch head를 다시 확인한다. 기존 1·2·3·6번 작업 및 이후 커밋을 보존한다. 각 영역을 독립 커밋으로 유지한다.
- Java 21 전체 의존성 컴파일, 관련 회귀 및 신규 동시성 테스트, 실제 MySQL/CacheD 장애·ACK loss·재접속 검증을 수행한다.
- 원본과 동일 부하/워밍업/반복 횟수 조건에서 JFR 및 CPU/작업, allocation, retained heap, p99, lock contention, SQL/큐 지표를 비교한다. 5,000명 부하 검증은 목표이며 실측 동접 기록이 아니다.
- 장비/인챈트, 무기·방어구·마법검, 마법 피해·확률, 네트워크 코어는 범위 밖이다.
- 이번 보고서에서는 관련 소스만 정적으로 확인했으며 모든 외부 호출부 전수 감사, 컴파일, 회귀 실행, 성능 측정 및 NC 원본 대조는 하지 않았다. 구현 전 호출 경로와 게임 정책 확인이 필요하다.
