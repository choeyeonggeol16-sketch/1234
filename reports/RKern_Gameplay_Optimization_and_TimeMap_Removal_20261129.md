# RKern 후속 게임 로직 최적화 및 구형 TimeMap 제거 작업 지시서

- 작성일: 2026-11-29 (KST)
- 대상: RKern / Java 21, 리니지 1
- 기준: [3546a7e744b9f9070284897caea5f61c8ec59e52](https://github.com/choeyeonggeol16-sketch/1234/tree/3546a7e744b9f9070284897caea5f61c8ec59e52) — `rkern/full-source-20261127`
- 범위: 출석 보상, 인벤토리 Dirty Index, 버프 저장, 아지트·경매, 구형 TimeMap 제거
- 제외: 공성전, 타일 점유, 일반 네트워크 코어, 이미 완료한 별개 기능의 반복 리팩토링
- 상태: 최신 생산 코드 정적 검토 및 구현·삭제 지시. 이번 커밋은 보고서만 추가한다. Java, SQL, 설정, 테스트는 아직 수정·실행하지 않았다.

## 0. 우선순위 및 공통 원칙

| 작업 | 우선순위 | 목표 |
| --- | --- | --- |
| T00 구형 TimeMap 제거 | 즉시 | 불필요한 컨트롤러, 데이터 모델 및 전용 등록 경로 폐기 |
| T01 출석 보상 | P1 | 매초 전체 접속자 순회 제거, 계정별 보상 시각 기반 실행 |
| T02 인벤토리 Dirty Index | P1 | 감사용 후보 인덱스를 안전한 생산 저장 경로로 완성 |
| T03 캐릭터 버프 저장 | P1 | 활성 효과 직접 수집, 불필요한 DB 쓰기 감소 |
| T04 아지트·경매 | P2 | 전체 주기 검색을 만료 시각 기반 예약으로 교체 |

작업 순서는 T00 → T01 → T02 → T03 → T04로 한다. 각 단계는 직전 최신 소스를 기준으로 독립적으로 검증하고 커밋한다. 우선순위는 정적 코드 검토에 따른 것이며 실측 성능 순위가 아니다.

- 기존 CIO 소유 실행 구조, CServerTimer, CGameTime, CWorldWork, CacheD의 owner lane, receipt 및 메모리 반영 순서를 재사용한다.
- 새 전용 스레드, 중복 큐, 신규 공용 스케줄러를 만들지 않는다. primitive, 재사용 자료구조, 짧은 락 구간을 우선한다.
- 새 JVM 옵션을 사용자가 설정해야 정상 동작하는 구조로 만들지 않는다. 실측 및 검증을 거쳐 기본 실행 경로를 결정한다.
- 리니지 1의 정상 게임 규칙, DB 정합성 및 패킷 형식은 유지한다. NC 원본의 세부 구현을 그대로 복원했다고 주장하지 않는다. NC식 책임·수명·실행 분리의 적용 설계이며 NC 직접 대조는 이번 범위가 아니다.
- 타일 점유와 공성전의 생산 코드, DB, 설정을 변경하지 않는다.

## T00. 구형 TimeMapController 완전 제거 (사용자 명시 지시)

### 확인된 코드

- [TimeMapController.java](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/Controller/TimeMapController.java#L13-L92): 구형 타임맵 목록과 주기 카운트다운을 보유한다. 만료 시 전체 유저를 검색하여 지정 좌표로 강제 귀환시키고 문을 닫는다. 컨트롤러 monitor 안에서 실행한다.
- [L1TimeMap.java](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/templates/L1TimeMap.java): 구형 타임맵 ID, 남은 시간 및 문 ID를 담는 모델이며 count()가 시간을 감소시킨다.
- 최신 트리에서 이 두 생산 파일의 존재를 확인했다. GameServer.java, CS_Door.java, L1DoorInstance.java, CS_ActionUi.java, OneTimeController.java의 조사에서는 직접 이름 참조를 발견하지 못했다. 모든 호출부 및 설정을 전수 검색한 결과는 아니며 현재 가동 중이라고 단정하지 않는다.

### 필수 삭제 지시

**TimeMapController를 최적화하거나 대체 구현하지 말고 관련 구형 기능과 전용 자료를 제거한다.**

1. 최신 전체 SRC와 배포 설정·SQL·스크립트·테스트에서 TimeMapController, L1TimeMap, 정적 접근자, 생성자, 등록·취소·주기 실행 참조를 전수 검색한다. import, fully qualified name, reflection 문자열, GM/스크립트 등록, 초기화 및 종료 경로까지 포함한다.
2. `Controller/TimeMapController.java`, `templates/L1TimeMap.java`를 삭제한다. 해당 기능 전용 타이머 등록 및 호출, 생성·등록 분기, 상수, 호환 래퍼, 죽은 메서드, 전용 테스트 및 설정을 함께 제거한다. 참조 0건으로 종료하며 비어 있는 호환 클래스를 남기지 않는다.
3. 구형 타임맵 전용 DB 테이블·컬럼·설정 데이터·XML·SQL 설치/마이그레이션 자료가 있다면 실제 참조와 데이터 소유권을 확인한 후 함께 제거한다. 전용 자료라는 근거가 있는 것만 대상이며 존재하지 않는 테이블명을 임의로 만들지 않는다. 운영 DB 삭제는 백업·의존성 검증 후 별도 명시적 마이그레이션으로 제공한다.
4. 기존 코드의 맵 ID(72~74, 460~466, 470~474, 475~478), 문 ID는 이 컨트롤러의 처리 대상이라는 뜻일 뿐이다. 공용 맵, 문 스폰, NPC, 퀘스트 및 다른 던전에서 사용되는 데이터는 삭제하지 않는다. 기존 도어 시스템과 귀환 시스템의 공용 기능도 보존한다.
5. **DungeonTimeUpdator, DungeonTimeProgress, CInstantZone, 레이드 등 현재 사용 중인 시간제 던전 및 인스턴스 시스템은 삭제하지 않는다.** 명칭이 비슷하다고 범위를 확장하지 않는다. 다른 기능이 구형 컨트롤러에 의존하고 있다면 호출 흐름과 의존성을 먼저 보고하고, 무단으로 대체 시스템을 만들거나 공용 데이터를 폐기하지 않는다.
6. 제거 후 전체 Java 21 타입 컴파일, 서버 시작·종료, 기존 문 열기/닫기 및 현재 시간제 던전 입장·퇴장·만료를 검증한다. 전용 예약이나 실행 작업이 남아 있지 않아야 한다.

## T01. 출석 보상 — 계정/세션 기반 시간 정산

### 현재 상태

- [AttendanceScheduler.java:74](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/model/Attendance/AttendanceScheduler.java#L74): 매초 CGameTime 이벤트를 받아 기존 CWorldWork 작업으로 제출하고 pending tick을 병합한다.
- [AttendanceScheduler.java:151](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/model/Attendance/AttendanceScheduler.java#L151): 전체 접속자 스트림을 방문하며 로그아웃·무인상점·온라인 상태·출석 데이터를 검사한다. 이미 일일 경계 전후 tick 처리 및 DB 실패 재시도 구조가 있다.
- [SC_ATTENDANCE_USER_DATA_EXTEND.java:307](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/SC_ATTENDANCE_USER_DATA_EXTEND.java#L307): 출석 그룹을 갱신하고 변경이 있을 때만 동일 snapshot을 CacheD와 패킷에 전달한다.
- [UserAttendanceDataGroup.java:78](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/Attend/UserAttendanceDataGroup.java#L78): 활성 그룹에서 경과 시간을 누적하고 임계치 충족 시 COMPLETE 및 다음 슬롯/일일 한도 상태를 갱신한다. 매초 DB 저장 또는 패킷 송신을 한다는 의미는 아니다.

5,000명 모두가 접속자로 등록된 정상 1초 tick의 이론 방문량은 5,000회/초, 1,800만 회/시간이다. 실제 실행량은 coalescing 등에 달려 있고 CPU 절감률은 측정 전 미확정이다.

### 구현 지시

- 계정 단위 출석 데이터 및 현재 PC session stamp/CacheD epoch를 기준으로 정산 소유권을 명확히 한다. 로그인, 로그아웃, 무인상점 전환, PC방 eligibility 변경, 보상 수령, 일일 rollover에 맞추어 진행/정지를 등록한다.
- 경과 시간은 단조 시간으로 측정하고 실제 출석 자격이 유지된 구간만 반영한다. 다음 활성 그룹의 보상 임계치를 계산해 기존 CIO 타이머에서 필요한 시점에 실행한다. 완료 그룹 또는 한도 도달 계정은 반복 처리 대상에서 제외한다.
- UI 조회 및 보상 요청, 로그아웃 저장 직전에 경과 시간을 동기 정산한다. UI가 관찰하는 시간 및 슬롯 상태와 기존 wire/blob 호환성을 보존한다.
- 일일 초기화는 기존 영속 달력과 설정 시간대를 유지하고 기간 키로 중복 적용을 막는다. reset 이전·이후 구간을 분리한다. 지연된 callback이 접속 전 시간 또는 무인상점 시간을 소급 지급하지 않게 한다.
- 기존 출석 보상 receipt 및 계정별 ordered save와 claim 상태 병합을 유지한다. DB 완료 전 지급/중복 지급 금지. 예약 거부와 DB 실패 시 진행 상태를 유실하지 않고 기존 실행 경로로 재시도한다.

### 검증

정상 출석, 다중 그룹/임계치, PC방, 일일 한도, 재접속·캐릭터 교체, 무인상점, UI 조회·보상 요청 경합, 일일 경계 및 지연·재시작·ACK 유실을 비교한다. 기존 보상 지급 횟수와 blob/wire 의미를 보존한다. 5,000명 부하에서 방문 수, CPU, allocation, 타이머 작업 수, DB 요청 수 및 p99를 측정한다.

## T02. 인벤토리 자동 저장 — Dirty Index 생산 경로 완성

### 현재 상태

- [CItemList.java:50](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/model/Instance/CItemList.java#L50): `rkern.inventory.auditDirty`로 선택하는 PersistenceAudit, TIntHashSet 후보 및 revision을 갖는다. 아직 생산 저장 인덱스로 사용하지 않는다.
- [L1PcInstance.java:4540](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/model/Instance/L1PcInstance.java#L4540): saveInventoryIfReady()가 전체 멤버십 snapshot을 순회한다. logout saveInventory()도 전체 순회한다.
- [CUserInventory.java:718](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/model/inventory/CUserInventory.java#L718): recording columns와 정확한 소유권 확인 후 CacheD에 저장을 제출한다. in-flight SQL 동안 값이 원복되는 경우를 보존하기 위해 dirty 아이템의 전체 저장 이미지를 사용한다.

### 구현 지시

1. 기존 dirty audit의 변경 추적을 먼저 전수 검증한다. 모든 persisted 필드의 setter 및 직접 쓰기, 객체 ID 교체, 로드/초기화, 소유권 이전, 강화·축복·속성·수량·착용·만료·lastUsed와 receipt의 메모리 반영을 포함한다. 후보 누락 0건이 입증되기 전 전체 순회를 제거하지 않는다.
2. 기존 CItemList의 소유권·멤버십 인덱스에 primitive dirty 후보를 통합한다. 첫 clean→dirty 전환 때만 등록한다. 아이템마다 새 래퍼·Runnable·큐 노드를 만들지 않는다. 빈 인벤토리 및 변경 없는 인벤토리에 추가 할당을 최소화한다.
3. 후보에는 실제 객체 identity 및 소유 세대/변경 revision을 검증할 수 있어야 한다. objectId 재사용 및 창고·바닥·거래 이전을 구분한다. 후보 snapshot은 실행 도중 소유권이 바뀔 수 있으므로 실제 저장 직전 재검증한다.
4. CacheD 제출 성공만으로 dirty를 지우지 않는다. COMMIT/확정 receipt와 persisted image를 확인한 후 해당 revision만 retire한다. 같은 아이템의 동시 변경·원복·재변경 및 저장 중 삭제/이동이 유실되지 않아야 한다. 지연·거부·실패·불확정은 재시도/격리 정책을 유지한다.
5. 기존 순회를 shadow oracle로 사용해 dirty 인덱스와 저장 이미지의 일치 여부를 확인한다. 검증 통과 후 autosave는 후보만 순회하게 기본 경로를 전환한다. logout의 확정성 보장을 유지하고 필요하면 최종 검증 sweep을 별도 유지한다. 기존 검증된 receipt 전체 이미지 및 DB 쓰기 순서를 임의로 축소하지 않는다.

### 검증

0/1/다수 dirty, 200~300개 아이템 및 5,000명 가상 소유자, 저장 중 같은 필드 원복, 저장 거부, DB rollback/ACK 유실, 소유권 변경·객체 ID 재사용, 강화/거래/창고/장비/로그아웃 회귀. 기존 전체 순회 기준과 최종 DB 내용이 일치해야 한다. 방문 아이템 수, CPU, 할당, SQL 수, p99와 retained heap을 비교한다.

## T03. 캐릭터 버프 저장 — 활성 효과 수집 및 확정된 변경만 저장

### 현재 상태

- [CharBuffTable.java:110](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/datatables/CharBuffTable.java#L110): CacheD session ordered capture 안에서 buffSkill 전체 whitelist를 돌며 hasSkillEffect 및 getSkillEffectTimeSec를 호출하고 최대 크기 int 배열을 생성한다.
- [SkillEffectContainer.java:253](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/model/character/skill/SkillEffectContainer.java#L253): primitive skillIds/remainingSeconds와 revision으로 일관된 읽기를 구현한다. contains/getRemainingTimeSec는 활성 항목을 선형 검색한다. 따라서 whitelist 길이 W, 활성 효과 수 A에 대해 기존 조회 비용은 최악 O(W×A)이다.
- [CharBuffTable.java:125](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/datatables/CharBuffTable.java#L125): 한 SQL 트랜잭션 안에서 기존 행을 삭제하고 현재 효과를 batch INSERT한다. 원자적 저장과 rollback은 이미 있다.
- [L1PcInstance.java:2479](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/model/Instance/L1PcInstance.java#L2479): 로그아웃에서 SaveBuff를 호출한다. [BuffRestoreHandler.java](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/model/character/login/skill/BuffRestoreHandler.java)는 기존 복원 인터페이스다.

### 구현 지시

- 기존 SkillEffectContainer의 안정된 revision을 활용하여 활성 ID·남은 시간을 일관된 primitive snapshot으로 수집한다. 저장 whitelist를 primitive lookup으로 사전 구축하고 활성 효과만 필터한다. 기존 buffSkill의 저장/복원 순서, 중복 ID 처리 및 저장 제외 정책을 확인해 동일하게 유지한다.
- 남은 시간, 무한 지속 효과의 기존 표현, 변신/폴리링의 spriteId를 함께 capture하고 필요한 경우 revision을 재확인한다. 기존 restoration registry와 스킬 적용 순서를 보존한다. 임의로 모든 활성 스킬을 영속화하지 않는다.
- CacheD의 동일 캐릭터/session lane 예약과 paid buff receipt 순서를 유지한다. 기존 DeleteBuff 호출부 및 직접 JDBC 실행 여부를 조사하여 필요한 경우 동일 owner lane으로 정리한다. 공유 buff DB API를 함부로 삭제하지 않는다.
- 1차는 snapshot 비용만 줄이고 기존 DELETE+batch INSERT 원자성을 유지한다. 2차는 DB persisted image, 만료/남은 시간 의미, 유료 버프 및 logout 정책을 검토한 뒤 변경이 없는 저장의 생략 및 안전한 INSERT/UPDATE/DELETE 차분을 비교한다. 절대 만료 시각과 기존 남은 시간의 의미를 혼동하지 않으며 DB 쓰기 감소가 입증되지 않으면 무리하게 diff 방식으로 교체하지 않는다.
- 기존 SQL schema의 키/제약을 확인하고 필요 시 별도 호환 마이그레이션을 작성한다. COMMIT ACK 유실·rollback·재로그인·동시 지급에서 효과 유실/복제를 막는다.

### 검증

일반/유료 버프, 다중 효과, 만료 직전, 무한 지속, 변신, 제거 후 재등록, logout·relogin, 동시 receipt, DB 장애 및 기존 복원 순서를 검증한다. 활성 효과가 적은 캐릭터와 다량 버프 캐릭터의 CPU, snapshot bytes, SQL rows/round trips를 동일 조건에서 측정한다.

## T04. 아지트·경매 — 예정 시각 기반 예약

### 현재 상태

- [CWorldAgitTimer.java:18](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/model/world/residence/CWorldAgitTimer.java#L18): 기존 CWorldWork를 이용해 매분 wall-clock 경계에서 주기 작업을 수행한다.
- [CWorldAgitTimer.java:30](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/model/world/residence/CWorldAgitTimer.java#L30): 전체 경매와 아지트 목록의 마감·세금 기한을 검사한다.
- [L1AuctionTransaction.java:253](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/model/world/residence/L1AuctionTransaction.java#L253) 및 [287행](https://github.com/choeyeonggeol16-sketch/1234/blob/3546a7e744b9f9070284897caea5f61c8ec59e52/rkern/src/l1j/server/server/model/world/residence/L1AuctionTransaction.java#L287): 압류/재등록/낙찰/이전 소유자 정산은 이미 CacheD receipt, SQL preimage 및 메모리 검증으로 처리한다. 이를 새로 재구현하지 않는다.

### 구현 지시

- HouseTable과 AuctionBoardTable 로드/reload 완료 시 저장된 기한을 기존 타이머에 등록한다. 기한 변경이 확정된 세금 납부·경매 등록·재등록·낙찰·압류 시에만 예약을 갱신한다.
- 항목 종류, houseId, 실제 객체 identity, 저장된 기한 및 generation을 검증한다. 이전 예약·삭제된 항목·reload 전 객체의 callback은 무효 처리한다. 동일 ID의 새 객체와 오래된 예약을 구분한다.
- 기존 분 단위 정산의 strict deadline 비교 및 시간대 정책을 유지한다. 정확히 분 경계에서 마감되는 항목의 기존 처리 시각을 검증하고 경제 규칙 변경 없이 예약 시각을 계산한다. 서버 재시작 시 경과한 기한을 안전하게 catch-up한다.
- 새 전용 스레드를 추가하지 않는다. 기존 CWorldWork 및 Timer를 이용하고 실제 DB 처리와 receipt는 L1AuctionTransaction의 기존 CacheD global auction lane에 맡긴다. 준비/커밋/메모리 반영이 불확정이면 임의 재실행하지 않는다.
- 한꺼번에 많은 만료가 발생할 때 bounded admission과 재시도를 보장한다. 한 항목의 오류가 다른 만료를 영구 차단하지 않게 한다. 유효한 경매/세금 예약만 유지하고 shutdown/reload 시 정리한다.

### 검증

입찰 마감 직전/직후, 세금 납부와 압류 경합, 무입찰 재등록, 낙찰 및 구소유자 지급, DB rollback/ACK 유실, restart/reload, 시계 이동 및 동일 ID 재사용. 기존 정산 결과와 정확히 일치해야 한다. 전체 목록 방문 감소와 예약 유지 비용을 함께 측정한다. 아지트 수가 적으면 성능 효과도 작을 수 있다.

## 완료 기준

- T00 전용 자료 제거 및 전체 참조 감사, T01~T04 실제 생산 코드 반영 여부를 각각 커밋 SHA와 파일 목록으로 증명한다.
- Java 21 전체 타입 컴파일 및 기존 관련 회귀 시험, 신규 실패·경합·복구 시험을 수행한다. 실제 DB 기반 검증과 5,000명 부하 시험의 범위/미실시 항목을 분리 기록한다.
- 기존 게임 정상 동작, 소유권, 저장 정합성, 패킷 호환을 우선한다. 벤치마크 결과와 실제 운영 환경 검증 없이 성능 향상이나 완전 해결을 확정하지 않는다.
- 공성 및 점유 코드는 작업 범위 밖이다. 보고서 커밋이 곧 구현 완료를 뜻하지 않는다.
