# RKern 후속 게임 로직 최적화 기술 보고서

- 작성일: 2026-11-27
- 기준: [0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d](https://github.com/choeyeonggeol16-sketch/1234/tree/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d) (`rkern/full-source-20261127`)
- 범위: 1. 시간제 던전 / 2. 인벤토리 자동 저장 / 3. 몬스터 도감 / 6. 함정
- 상태: 정적 코드 검토 및 구현 계획. 생산 코드·DB·설정은 수정하지 않음.

## 0. 목표와 우선순위

CWorld의 라이브 공간/맵 인덱스, CGameTime, CIO Timing Wheel, CInstanceWork/CWorldWork, CacheD, 기존 트랜잭션과 ProtoWriter를 재사용한다. 새 전용 스레드·공통 실행 큐·무의미한 호환 래퍼를 만들지 않는다. 자료구조 변경은 실제 핫패스 비용을 줄이는 데 집중한다. 리니지 1의 동작, 저장 형식 및 패킷 호환성을 우선한다.

| 번호 | 영역 | 확인 사항 | 구현 우선순위 |
|---|---|---|---|
| 1 | 시간제 던전 | 매초 전체 접속자 조회, 지연 시 시간/초기화 경계 문제 | P1 |
| 2 | 인벤토리 자동 저장 | 모든 소유 아이템의 persisted image 비교 | P1 (정합성 우선) |
| 3 | 몬스터 도감 | 박싱·중첩 데이터 객체, 전체 blob 저장 및 재파싱 | P2 |
| 6 | 함정 | 이진 검색의 정렬 불변식 깨짐, 감지 및 중복 발동 위험 | P0 (결함 우선) |

번호는 요청 원문 순서를 유지한다. 전체 소스 트리 1,715 Java 파일의 구성과 위 영역의 관련 구현을 확인했으나 전체 파일의 모든 메서드를 감사한 것은 아니다. NC 원본과의 일치 여부, 실서버 부하 성능은 이번 보고서에서 주장하지 않는다. P0/P1/P2는 제안 우선순위이며 실측 성능 순위가 아니다.

## 1. 시간제 던전 — DungeonTimeUpdator

### 확인된 구현

- [DungeonTimeUpdator.java:224](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/world/instance/dungeon/time/updator/DungeonTimeUpdator.java#L224): `do_update()`가 전체 접속자를 순회하고 eligibility 및 맵의 던전 정보를 검사한 후 `dec_dungeon_progress(dtInfo)`를 호출한다.
- [DungeonTimeUpdator.java:170](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/world/instance/dungeon/time/updator/DungeonTimeUpdator.java#L170): 기존 CInstanceWork consumer로 제출하며 대기 tick은 최대 2개다. 거부 시 남은 tick을 보존하지만 2개를 초과한 지연을 복원하지 않는다.
- [DungeonTimeUpdator.java:119](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/world/instance/dungeon/time/updator/DungeonTimeUpdator.java#L119): 초기화 조건이 설정된 시·분·초 0과 정확히 일치해야 한다. 거부된 초기화의 재시도는 있으나 최초 경계를 건너뛴 경우에는 보장되지 않는다.
- [CGameTime.java:188](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/gametime/CGameTime.java#L188): 한 번의 advance에서 지나간 각 초를 전부 재생하지 않고 현재 시각의 경계 이벤트를 단 한 번 전달한다.
- [DungeonTimeUserInformation.java:75](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/world/instance/dungeon/time/DungeonTimeUserInformation.java#L75): 캐릭터/계정별 progress 조회, 1초 감소, 60초 경계 저장, 만료 시 귀환 및 메시지를 수행한다. [DungeonTimeProgressLoader.java:134](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/world/instance/dungeon/time/loader/DungeonTimeProgressLoader.java#L134)는 CacheD 순서 보장 저장 경로를 사용한다.
- [CWorld.java:656](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/CWorld.java#L656): 이미 맵 ID 또는 실제 L1Map 객체 기준 유저 조회를 지원한다. [DungeonTimeInformationLoader.java:37](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/world/instance/dungeon/time/loader/DungeonTimeInformationLoader.java#L37)에는 timer ID와 맵 ID 매핑이 존재한다.

### 제안

1. 설정된 시간제 맵 ID를 중복 없는 immutable primitive 목록으로 발행하고, 기존 CWorld 맵별 roster를 이용해 해당 맵의 유저만 대상으로 한다. reload는 완전한 새 설정을 발행하고 이전 snapshot의 사용 중 수명을 보장한다. 기존 loader의 dispose/reload와 경쟁하지 않게 한다.
2. 조회 후 실제 맵 참조, 세션/등록 세대, eligibility를 재검사한다. 한 tick에 맵을 옮긴 유저의 중복 차감 및 지연 작업이 재접속 세션에 적용되는 현상을 방지한다. 기존 소유 실행 경로를 활용한다.
3. 1차는 기존 초 단위 차감과 저장·귀환 규칙을 보존한 채 대상만 축소한다. 이후 단조 시간에 근거한 체류 구간/만료 계산을 별도로 검증한다. 단순히 지연 시간 전체를 현재 맵 기준으로 소급 차감하면 이동 전 시간이 잘못 차감될 수 있으므로 금지한다.
4. 초기화는 KST 기준 예정 시각의 통과 여부와 날짜/기간 키를 사용한다. 동일 기간 초기화는 중복 확정 금지, 실패는 재시도, 서버 재시작과 장기 지연 정책도 명시한다. 기존 reset의 전 서버 공지 의도는 유지하고 맵 53/54 강제 이동만 별도 대상 roster로 좁힌다.
5. 계정 공유 시간, 시간 충전/입장/퇴장, reset·logout 저장 순서 및 CacheD lane을 유지한다. 현재 사용 중인 60초 저장 주기는 근거 없이 변경하지 않는다.

### 비용과 검증

N=전체 접속자, A=시간제 던전 대상 맵의 접속자 합계, M=설정 맵 수. 현재 정기 검색 비용 O(N), 개선 목표 O(M+A). 예시 N=5,000, A=500이면 유저 검사 수가 5,000회에서 약 500회/초로 감소할 수 있다(맵 순회와 snapshot 비용 별도, 가정값).

- 동일 timer의 복수 맵 이동, 서로 다른 timer 이동, 맵 ID 재사용, logout/reconnect, 계정 공유, 시간 충전 및 60초 저장 경계.
- 0초 만료/귀환, worker 3~10초 지연, reset 초 건너뜀, KST 자정/초기화 경계, clock 역보정, 큐 거부, DB 장애/재시도.
- 5,000 접속자 중 0/500/5,000명 던전 부하에서 방문 유저 수, tick CPU, allocation, p99 실행 지연 및 실제 차감 결과를 비교한다.

## 2. 인벤토리 자동 저장 — Dirty index

### 확인된 구현

- [L1PcInstance.java:4540](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/Instance/L1PcInstance.java#L4540): `saveInventoryIfReady()`는 CItemList의 멤버십 snapshot 전체를 순회한다. 실패 또는 deferred item은 다음 시도에 재확인한다.
- [CItemList.java:784](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/Instance/CItemList.java#L784): 기존 snapshot은 멤버십 변경 후 다시 만들어지고 재사용된다. 매 autosave마다 전체 배열을 새로 생성한다는 의미는 아니다.
- [CUserInventory.java:716](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/inventory/CUserInventory.java#L716): `saveDirtyItemIfReady`는 persisted image 변경 여부 및 실제 소유 여부를 확인하고 CacheD에 owner/epoch 및 전체 dirty image 저장을 요청한다.
- [L1ItemInstance.java:925](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/Instance/L1ItemInstance.java#L925) 및 [L1ItemInstance.java:938](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/Instance/L1ItemInstance.java#L938): LastStatus는 DB에 저장된 이미지다. lazy 생성 후 snapshot monitor에서 18개 저장 필드에 해당하는 값(수량·아이템 ID·장착·시간·속성 등)을 비교한다.
- [CacheD.java:2019](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/storage/CacheD.java#L2019): 기존 owner/epoch 확인 저장 경로. 이 경로의 저장 확정과 preimage 정책을 유지해야 한다.

### 제안

1. 소유 인벤토리 내부의 primitive ID/참조 배열 기반 dirty 집합 또는 기존 멤버십 인덱스에 붙는 dirty bit를 검토한다. clean→dirty 전이만 등록하고 같은 아이템의 중복 등록을 막는다. 변경 없는 유저에 추가 대형 배열을 선할당하지 않는다.
2. 모든 persisted 필드 쓰기 경로(직접 필드 쓰기, setter, 강화, 장비 교환, 수량 병합, 시간 변경, 복원, 거래, CacheD apply)를 전수 추적한다. dirty bit의 의미는 저장 요청 여부가 아니라 재확인이 필요한 변경으로 정의한다.
3. 저장 요청과 ACK 사이에 추가 변경/원복이 발생해도 최신 값을 놓치지 않도록 변경 세대 또는 현재 persisted image 비교를 사용한다. 제출 성공만으로 dirty 등록을 해제하지 않는다. 이전 저장의 완료가 새로운 변경을 지우지 않게 한다.
4. 거래·창고·바닥 이동/소멸·동일 ID 재사용·로그아웃·재로그인·pending transfer와 연동한다. 참조/소유자/session epoch를 재검사하고 stale 항목을 제거한다. 인벤토리/LastStatus/CacheD 사이 기존 락 순서를 보존한다.
5. 1차에는 기존 saveDirtyItemIfReady와 CacheD full dirty image 동작을 그대로 재사용한다. debug/test에서 전체 스캔 결과와 dirty 집합을 대조하고, 누락 0을 검증한 후 정기 full scan을 제거한다. logout/강제 저장 계약 및 복구 경로는 별도로 검증한다. 새 DB 컬럼이나 트랜잭션 경로가 필수라고 가정하지 않는다.

### 비용과 검증

전체 아이템 I개, dirty D개일 때 현재 비교 횟수는 O(I), 목표 O(D). 5,000명×200아이템이면 전체 자동 저장 1회 기준 최대 1,000,000개 비교 대상이다. 각 5개만 dirty라면 약 25,000개를 검사하는 예시가 된다(실제 유저 아이템 수/변경률이 아닌 가정; CPU 향상률 아님).

- 전체 필드 mutation coverage, 변경→원복→ACK, 연속 ACK 순서, 저장 실패/rejection/retry, 양도·삭제·병합·동일 ID/다른 객체, logout/reconnect epoch, DB restart/ACK loss.
- 원본/개선본 DB 최종 이미지와 receipt/preimage 검증 결과가 동일해야 한다. 전체 순회 대비 dirty 누락/오탐을 집계한다.
- 0/1/5/50/100% dirty 조건에서 CPU, allocation, lock wait, SQL 요청 수, 큐 깊이 및 p99 저장 지연 비교. dirty 등록 비용 때문에 쓰기 위주 부하가 악화되지 않는지 확인한다.

## 3. 몬스터 도감 — BQSCharacterData

### 확인된 구현

- [BQSCharacterData.java:57](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/BookQuest/UserSide/BQSCharacterData.java#L57) 및 [BQSCharacterData.java:363](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/BookQuest/UserSide/BQSCharacterData.java#L363): 진행 상태와 완료 업적은 HashMap<Integer, ...>에 저장한다. [BQSCharacterCriteriaProgress.java:25](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/BookQuest/UserSide/BQSCharacterCriteriaProgress.java#L25)에는 별도의 CriteriaProgress 객체가 중첩된다.
- [BQSCharacterData.java:120](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/BookQuest/UserSide/BQSCharacterData.java#L120): ProtoWriter.borrow()로 이미 body-only 직렬화를 수행한다. 진행, 업적, 덱, 덱 버전 전체를 byte[]로 만든다. 기존 프로토 개선은 유지한다.
- [BQSCharacterData.java:192](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/BookQuest/UserSide/BQSCharacterData.java#L192) 및 [BQSCharacterData.java:283](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/BookQuest/UserSide/BQSCharacterData.java#L283): 덱 완료/업적 완료 시 저장 요청. 일반 몬스터 처치마다 전체 SQL이 실행된다고 단정할 수 없다.
- [BQSCharacterDataLoader.java:89](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/BookQuest/Loader/BQSCharacterDataLoader.java#L89): caller에서 blob을 만들고 CacheD character lane에 저장을 제출한다. [BQSCharacterDataLoader.java:135](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/BookQuest/Loader/BQSCharacterDataLoader.java#L135): DB worker가 incoming blob을 재파싱하고 INSERT 및 row lock 후 기존 저장본/덱 버전/보상 확정 상태를 검사해 저장한다.
- [BQSCharacterDataLoader.java:101](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/BookQuest/Loader/BQSCharacterDataLoader.java#L101) 및 [WeeklyMonsterBookRewards.java:85](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/BookQuest/WeeklyMonsterBookRewards.java#L85): 주간 보상은 기존 인벤토리 트랜잭션에서 snapshot의 버전 및 완료 상태를 검증하고 보상 상태를 확정한다. 단순 비동기 저장과 보상 확정을 섞지 않는다.

### 제안

1. 먼저 기존 BQSCharacterData의 progress/achievement 인덱스를 primitive key 기반 자료구조로 전환하는 최소 변경부터 평가한다. 실제 criterion ID 분포가 조밀할 경우 template dense index+primitive 배열을 검토하되, 희소한 외부 ID를 그대로 대형 배열 인덱스로 쓰지 않는다.
2. 반복 접근되는 progress count/level/flags, achievement reward flag/time을 구조적으로 분리하고, wire/DB 경계에서 기존 필드 번호, 존재 여부, 정수 부호·범위 및 순서를 검증한다. 현재 덱 버전과 card remap 규칙, 완료/획득 기준은 보존한다.
3. 일반 진행 상태와 보상/주간 갱신의 확정 경로를 분리한다. 일반 저장은 dirty version을 이용한 동일 owner의 안전한 snapshot coalescing 후보로 검토하되 기존 DB blob 형식부터 유지한다. 전체 blob을 매번 저장하는 한 변경 추적만으로 SQL 전송 크기가 줄어든다고 주장하지 않는다. DB delta schema는 별도 호환성 및 이전 계획을 갖춘 후속 작업으로만 고려한다.
4. `stageWeeklyReward`, receipt/재시도, 기존 row lock, `preserveWeeklyClaims`, logout 및 접속 시 snapshot 복원 정책을 보존한다. 오래된 일반 snapshot이 확정된 보상 상태/덱 버전을 되돌리지 않아야 한다.
5. 기존 `[MBOOK COMPLETE]` 정상 완료 stdout 로그([BQSCharacterData.java:258](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/BookQuest/UserSide/BQSCharacterData.java#L258))는 운영 요구가 없으면 debug 전환 후보. 신규 worker를 추가하지 않는다.

### 검증

- 실제 구형 DB blob fixture 왕복 복원, 기준 프로토 메시지의 필드/값/존재 여부 비교, 희소/중복 ID, 덱 remap, 임계값 정확히 도달, 동시 킬/주간 리셋/보상 클릭.
- 보상 commit 성공·실패·ACK 유실·재접속·서버 재시작·동일 주/덱 중복 청구·오래된 snapshot 제출에서 보상/재화 유실 및 복제 0.
- JFR로 진행 상태의 retained heap, boxing/node/DTO 할당, kill update CPU, save 호출 수/bytes, DB worker 역직렬화 CPU 및 p99를 원본과 비교한다. 전체 SQL 경로 변경 전에는 row lock을 임의로 제거하지 않는다.

## 6. 함정 — L1WorldTraps

### 확인된 결함 후보 (정적 근거)

- [L1WorldTraps.java:85](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/world/trap/L1WorldTraps.java#L85): 부팅 시 함정과 베이스를 등록하고 93~94행에서 좌표 순으로 정렬한다.
- [L1WorldTraps.java:161](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/world/trap/L1WorldTraps.java#L161): PC 이동 시 전체 함정 리스트를 binarySearch한다. 그러나 [L1WorldTraps.java:244](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/world/trap/L1WorldTraps.java#L244)의 addTrap은 정렬 없이 끝에 추가한다. 기존 순서를 위반하는 좌표 추가 시 이진 검색 누락이 가능하다.
- [L1TrapInstance.java:73](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/Instance/L1TrapInstance.java#L73) 및 [L1WorldTraps.java:232](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/world/trap/L1WorldTraps.java#L232): 랜덤 위치 reset/respawn은 정렬 키를 바꿀 수 있으며 리스트 재정렬/공간 인덱스 재발행이 이 메서드에 없다. 월드 등록 후 위치 변경과 published position 일치 여부도 확인해야 한다.
- [L1WorldTraps.java:181](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/world/trap/L1WorldTraps.java#L181): 탐지 마법이 시전자와 정확히 일치하는 좌표의 함정이 없으면 즉시 반환한다. 일치한다면 양쪽 리스트 전체를 스캔한다. 주변 탐지 의도와 불일치할 수 있다.
- [L1WorldTraps.java:161](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/world/trap/L1WorldTraps.java#L161): isEnable→onTrod→disableTrap이 분리되어 있다. [L1TrapInstance.java:44](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/Instance/L1TrapInstance.java#L44)의 활성 상태는 plain boolean이다. 서로 다른 실행 스레드가 동시에 진입하면 중복 발동 가능성이 있다. 같은 리스트를 읽는 이동 경로와 synchronized add/remove/reset의 동기화도 일관되지 않는다.
- [L1WorldTraps.java:248](https://github.com/choeyeonggeol16-sketch/1234/blob/0b4d35ac99f0160520150f2f6efeb6f41e2c1e8d/rkern/src/l1j/server/server/model/world/trap/L1WorldTraps.java#L248): 개별 removeTrap에는 pending respawn 취소가 명시되어 있지 않다. timer의 identity/stop 검사는 있으나 제거된 함정의 재활성화 방지도 검증 대상이다.

### 제안

1. 기존 CWorld의 실제 L1Map identity, 공간 셀/좌표 검색을 우선 재사용한다. 함정 검색을 위한 별도의 전역 리스트 이진 검색 의존을 없앤다. 필요한 경우 함정 전용 primitive 좌표 인덱스를 최소 범위로 추가하되 빈 맵에 선할당하지 않는다. map-only 등록(현재 addVisibleObject 사용)과 정식 객체 등록의 차이를 확인한다.
2. 이동·탐지에서 동일 좌표 및 주변 셀만 탐색하고, 함정 베이스·비활성 함정·재사용된 인스턴스 맵을 정확히 제외한다. 동일 타일 다중 함정의 발동 우선순위/정책은 기존 의도를 확인 후 명시적으로 보존한다.
3. 활성→발동 예약 상태를 원자적으로 선점(기존 owner 실행 또는 CAS/짧은 lock)하여 한 세대에 한 번만 발동한다. 스킬 효과/브로드캐스트는 구조 락 밖에서 실행한다. timer admission 실패 시의 상태를 명시하고 효과를 무제한 재발동하지 않도록 안전한 재시도 정책을 설계한다.
4. add/remove/respawn/reset/reload/던전 종료의 좌표 인덱스 갱신과 타이머 취소/세대 검증을 하나의 수명 계약으로 묶는다. resetLocation이 기존 맵 인덱스/공개 좌표와 일치하도록 공식 이동 경로를 사용하거나 map-only 전용 안전 경로를 이용한다.
5. 기존 CServerTimer/CWorldWork와 함정 효과, 재등장 시간, 탐지 가능 속성, 클라이언트 known object 제거 메시지를 보존한다. 신규 스레드 금지.

### 검증

- 기존 정렬 중간/앞 위치 addTrap, 랜덤 respawn, 빈 타일 탐지, 서로 다른 map/instance의 동일 좌표, 다중 함정, 두 유저 동시 진입, 이동/탐지 동시 실행.
- 제거/reload/reset 중 이전 timer 실행, 맵 종료·재사용, timer 거부, 발동 예외, 알려진 객체 정리, stale 참조 및 중복 효과/재등장 0.
- 1,000/5,000개 함정, 5,000 유저 이동 및 밀집 부하에서 조회 CPU/lock wait/할당/발동 정확도를 비교. 이진 검색은 현재 O(log T)이며 개선 목표는 공간 제한 검색과 정확성 개선이다. 항상 처리량이 증가한다고 단정하지 않는다.

## 구현 진행 및 합격 기준

- 결함 중심의 함정 P0을 먼저 처리하거나 시간제 던전부터 독립적으로 진행 가능하다. 네 영역은 각각 독립 커밋으로 유지하여 회귀 원인을 분리한다.
- 각 작업 시작 시 브랜치 최신 head를 확인하고 본 보고서 기준 이후 변경을 보존한다.
- Java 21 전체 의존성 컴파일, 관련 기존 회귀, 신규 경계/경쟁 테스트, 실제 MySQL 및 CacheD 장애·재접속 테스트를 통과한 후 채택한다.
- 동일 JVM/CPU/부하에서 워밍업 후 여러 반복 측정으로 CPU/작업, 처리량, p95/p99, allocation rate, retained heap, GC pause, lock contention, DB 큐 및 실패 수를 비교한다. 5,000 접속 시나리오는 검증 목표이며 운영 실적을 뜻하지 않는다.
- 결과와 근거가 나오기 전까지 실제 절감률/동접 상승률을 제시하지 않는다. 이번 보고서 작성 과정에서 컴파일, 회귀, JFR, 인게임, MySQL 테스트는 실행하지 않았다.
- 범위 밖: 무기/방어구/마법검, 네트워크 코어 교체, 파티/혈맹 최적화. 기존 CIO 및 CacheD 안전성 계약을 약화시키지 않는다.
