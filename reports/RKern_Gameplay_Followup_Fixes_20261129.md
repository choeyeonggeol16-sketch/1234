# RKern 후속 검수 — 남은 4개 수정 지시

- 작성일: 2026-11-29 (KST)
- 기준: [32d5d64c6bf721861bc20a9dec1893da4fc5fba4](https://github.com/choeyeonggeol16-sketch/1234/tree/32d5d64c6bf721861bc20a9dec1893da4fc5fba4), `rkern/gameplay-optimization-20261129`
- 범위: 공성 소유권 원자성, 출석 전체 초기화의 저장 경합, 출석 설정 리로드, 버프 DB 쓰기 최적화
- 상태: 생산 코드 정적 검수 및 수정 지시. 이번 커밋은 보고서만 추가한다.

| ID | 수정 대상 | 우선순위 |
| --- | --- | --- |
| F01 | 성 점령 시 소유권·성 메타데이터 원자적 저장 | P0 |
| F02 | GM 출석 전체 초기화와 기존 저장·재시도·보상 receipt의 격리 | P0 |
| F03 | 출석 설정 리로드 시 초기화 경계 즉시 재계산 | P1 |
| F04 | 캐릭터 버프 DB 불필요한 삭제·재삽입 제거 | P2 |

기존 CIO 소유 실행 경로, CInstanceWork/CWorldWork, CacheD의 ordered lane과 receipt를 활용한다. 새로운 전용 스레드·중복 큐·공용 실행기 없이 필요한 부분만 수정한다. 완료한 TimeMap 삭제, 인벤토리 Dirty Index, 출석의 전체 순회 제거, 아지트/경매 예약 구조, 공성 클래스 개편 자체를 반복 작업하지 않는다. 타일 점유 및 일반 네트워크 코어는 범위 밖이다.

## F01. 성 점령 소유권의 원자성과 확정 후 월드 반영

### 확인된 문제

- [CCastle.java:146–210](https://github.com/choeyeonggeol16-sketch/1234/blob/32d5d64c6bf721861bc20a9dec1893da4fc5fba4/rkern/src/l1j/server/server/model/world/siege/CCastle.java#L146-L210): ChangeOwner는 기존 수성 혈맹의 castleId를 0으로 변경하고 새 혈맹의 소유권·공성 상태를 메모리에 반영한 다음, 별도 작업으로 성 메타데이터 및 각 혈맹을 저장한다. 저장 제출 실패를 기록하더라도 이미 실행한 점령 상태를 되돌리거나 확정하지 않는다.
- [CCastleSiegeManager.java:166–174](https://github.com/choeyeonggeol16-sketch/1234/blob/32d5d64c6bf721861bc20a9dec1893da4fc5fba4/rkern/src/l1j/server/server/model/world/siege/CCastleSiegeManager.java#L166-L174): 성 메타데이터는 treasury owner lane에 별도 제출한다.
- [ClanTable.java:564–576](https://github.com/choeyeonggeol16-sketch/1234/blob/32d5d64c6bf721861bc20a9dec1893da4fc5fba4/rkern/src/l1j/server/server/datatables/ClanTable.java#L564-L576): 일반 혈맹 metadata 저장에는 hascastle이 포함되며 각 혈맹별 owner lane에서 실행한다.
- [L1CrownInstance.java](https://github.com/choeyeonggeol16-sketch/1234/blob/32d5d64c6bf721861bc20a9dec1893da4fc5fba4/rkern/src/l1j/server/server/model/Instance/L1CrownInstance.java): ChangeOwner가 true를 반환하면 즉시 면류관을 제거한다.

따라서 기존 소유권 해제, 새 소유권 설정, 성 메타데이터 사이에 부분 커밋이 발생할 수 있고, 이전 혈맹 snapshot이 확정된 소유권을 덮는 경로도 검토해야 한다. 장애를 실제 재현한 것은 아니며 기존 공성 구조에도 존재했던 영속성 결함이다.

### 수정 지시

1. 현재 CCastle의 점령 전제(진행 중 공성, 공격자 등록, 혈맹 정체성, 기존 성 보유 여부, 면류관의 실제 맵·거리·개체 유효성)를 보존한다. 동일 성 및 동일 공격 혈맹에 대해 동시에 두 점령이 승인되지 않도록 기존 실행 소유권/receipt 예약을 활용한다.
2. castleId, 기존 수성 혈맹 ID 또는 무주 상태, 새 혈맹 ID, 해당 공성 회차와 객체 identity, 필요한 성 metadata를 immutable preimage로 capture한다. DB 경로는 실제 관련 castle·clan_data 행을 같은 트랜잭션에서 잠그고 기존 소유권 및 새 혈맹의 미보유 상태를 재검증한다.
3. 기존 소유 혈맹의 hascastle 해제, 새 혈맹의 hascastle 등록, 점령에 필요한 성 metadata를 하나의 CacheD durable receipt/SQL 트랜잭션에서 확정한다. castle treasury lane과 해당 혈맹 lane을 함께 예약할 수 있는 기존 다중 owner 경로를 사용한다. 세금·공금의 별도 정상 거래와 기존 수성 시간 정책을 변경하지 않는다.
4. ClanTable.updateClan 등의 전체 snapshot 쓰기, 해산·혈맹명 변경, 공성 reload·종료·GM 명령과 점령 receipt 사이의 순서를 확인한다. hascastle의 전용 소유권을 명확히 하거나 기존 저장 경로를 조건부로 제한하여 오래된 이미지가 점령 결과를 덮지 못하게 한다. SQL 식별은 안정적인 clan_id/castle_id를 사용한다.
5. DB 확정 후 기존 공성 소유 실행 경로에서 방어 혈맹 전환, 공격자 정리, 수성 기한 반영, 승리 공지, 귀환, 탑/문 처리 및 면류관 제거를 실행한다. ChangeOwner의 true 의미를 단순 접수와 확정으로 혼동하지 않도록 L1CrownInstance 호출부까지 수정한다. 접수 중에는 동일 면류관/성의 중복 점령을 방지한다.
6. 정상 롤백이면 기존 소유권과 면류관을 유지하고 재시도 가능하게 한다. COMMIT 응답 유실·메모리 반영 실패 시 기존 CacheD 불확정 receipt 격리 및 복구를 사용한다. 공성 종료/reload와 진행 중 receipt의 순서를 보장한다. DB 완료를 기다리면서 CIO/공성 락을 점유하지 않는다.

### 완료 검증

무주 성 점령, 기존 혈맹 보유 성 점령, 동시 면류관 요청, 동일 혈맹의 복수 성 점령 경합, 혈맹 해산·정보 저장·공성 종료/reload 경합, SQL 단계별 실패, rollback, COMMIT ACK 유실, 재시작 후 소유권 재구성. DB와 메모리에 단일 소유권이 남고 성공 공지가 확정 뒤 한 번만 나가야 한다. 기존 CastleSiegeFlowRegression을 유지한다.

## F02. GM 출석 전체 초기화와 CacheD 저장 경합

### 확인된 문제

- [AttendanceLoadManager.java:223–270](https://github.com/choeyeonggeol16-sketch/1234/blob/32d5d64c6bf721861bc20a9dec1893da4fc5fba4/rkern/src/l1j/server/server/model/Attendance/AttendanceLoadManager.java#L223-L270): GM 전체 초기화는 스케줄러 dispose 후 지연 작업에서 PC 출석 데이터를 제거하고 attendance_startup 및 attendance_userinfo를 직접 TRUNCATE한다. 실패하더라도 재로딩 경로가 이어질 수 있다. MySQL/MariaDB의 TRUNCATE는 일반 DML 트랜잭션으로 원자적 rollback할 수 없다.
- [SC_ATTENDANCE_USER_DATA_EXTEND.java:91–145](https://github.com/choeyeonggeol16-sketch/1234/blob/32d5d64c6bf721861bc20a9dec1893da4fc5fba4/rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/SC_ATTENDANCE_USER_DATA_EXTEND.java#L91-L145): account lane에서 snapshot 재시도 및 이전 진행 상태 병합을 수행한다. 일반 snapshot이 존재하지 않는 행을 다시 생성하지 않게 되어 있으나, 새 행이 생긴 뒤 이전 snapshot이 적용되거나 지연된 초기 INSERT가 실행될 가능성을 전체 초기화 generation으로 구분하지 않는다.
- [SC_ATTENDANCE_USER_DATA_EXTEND.java:269–308](https://github.com/choeyeonggeol16-sketch/1234/blob/32d5d64c6bf721861bc20a9dec1893da4fc5fba4/rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/SC_ATTENDANCE_USER_DATA_EXTEND.java#L269-L308): 초기 생성, 로그인 snapshot 적용과 예약 등록도 별도 실행 경로다.
- [AttendanceRewardClaims.java](https://github.com/choeyeonggeol16-sketch/1234/blob/32d5d64c6bf721861bc20a9dec1893da4fc5fba4/rkern/src/l1j/server/server/model/Attendance/AttendanceRewardClaims.java): 지급과 출석 claim을 동일 receipt에서 확정하는 기존 안전 경로가 있다. 전체 초기화는 이 경로와도 경합하지 않아야 한다.

### 수정 지시

1. 전체 초기화를 기존 관리자 기능의 의미대로 보존하되 출석 저장·신규 초기 생성·로그인 로드/적용·보상 claim·일반 일일 rollover의 진입점을 전수 확인한다. 초기화 진행 중 신규 출석 작업을 잠시 제한한다.
2. 출석 전체 초기화 세대/epoch를 일반 일일 출석 날짜와 구분한다. DB에서 확정된 세대를 유지하거나 기존 CacheD barrier 및 복구 정책으로 동등한 영속적 격리를 구현한다. 이전 generation에서 대기하던 초기 INSERT, snapshot retry, login completion 및 claim이 초기화 후 생성된 행을 변경할 수 없어야 한다. 같은 날짜에 초기화를 실행해도 구분해야 한다.
3. 이미 승인된 보상 지급 receipt는 초기화와 명확히 순서화한다. 지급된 아이템만 남고 claim 상태가 예상치 않게 유실되거나, 실패한 claim이 아이템을 지급하지 않도록 기존 트랜잭션 경계를 유지한다. 불확정 receipt를 무시하거나 강제 큐 삭제하지 않는다.
4. TRUNCATE 두 번을 독립 실행하지 말고 transactional table을 확인하여 전체 초기화, 초기 달력/세대 갱신을 하나의 원자적 DB 작업으로 처리한다. 필요한 경우 명시적인 DB migration을 작성한다. 성공 COMMIT 이후에만 기존 세션의 출석 상태를 retire하고 새 세대의 스케줄러를 시작·등록한다. 실패 시 이전 세대를 안전하게 유지하거나 복구 상태로 격리한다.
5. DB 확정 전에 성공 공지 또는 새 출석 UI를 발행하지 않는다. reconnect, 무인상점, 늦은 세션 callback, retry, scheduler dispose/restart를 구분한다. 초기화 실패로 출석 시스템이 영구 중단되거나 과거 데이터가 복구되는 일이 없어야 한다. 전체 초기화 시에만 필요한 전체 세션 방문은 허용하며 매초 순회를 재도입하지 않는다.

### 완료 검증

동일 날짜 전체 초기화, 재시도 대기 중 초기화, 이전 초기 INSERT의 지연 완료, 초기화 후 같은 계정 새 행 생성, 로그인/reconnect, claim과 reset의 동시 실행, SQL 단계별 실패/rollback/ACK 유실, 재시작. 이전 generation의 저장 결과가 새 generation으로 넘어가지 않는지 실제 MariaDB/MySQL에서 확인한다. 기존 AttendanceStorageRegression의 지급 원자성 및 진행 상태 병합 회귀를 유지한다.

## F03. 출석 설정 리로드와 Window 재계산

### 확인된 문제

- [AttendanceLoadManager.java:145–153](https://github.com/choeyeonggeol16-sketch/1234/blob/32d5d64c6bf721861bc20a9dec1893da4fc5fba4/rkern/src/l1j/server/server/model/Attendance/AttendanceLoadManager.java#L145-L153): 설정 리로드가 loadConfig 후 refreshConfiguration을 호출한다.
- [AttendanceScheduler.java:52–77](https://github.com/choeyeonggeol16-sketch/1234/blob/32d5d64c6bf721861bc20a9dec1893da4fc5fba4/rkern/src/l1j/server/server/model/Attendance/AttendanceScheduler.java#L52-L77): Window는 day, cutoffNanos, intervalMillis 및 이전 경계를 가진다.
- [AttendanceScheduler.java:90–120](https://github.com/choeyeonggeol16-sketch/1234/blob/32d5d64c6bf721861bc20a9dec1893da4fc5fba4/rkern/src/l1j/server/server/model/Attendance/AttendanceScheduler.java#L90-L120): refreshConfiguration은 현재 세션을 갱신할 뿐 Window를 교체하지 않는다. 새 resetPeriodSecond는 다음 일반 경계 처리에서 적용되므로 기존 Window가 남는다.

### 수정 지시

- 새 출석 설정을 검증하고 일관된 시점에 발행한다. resetPeriodSecond가 0 이하인 설정은 기존 유효 설정을 훼손하지 않고 거부한다.
- 변경 시각까지의 기존 적격 시간을 먼저 정산하고, 기존 게임의 KST/설정 시간대 및 초기화 정책에 따라 새 예정 경계와 monotonic cutoff를 계산한다. 이미 지난 경계를 재생하거나 중복 초기화하지 않도록 durable day와 비교한다.
- 현재 Window와 새 설정의 세대, 대기 중인 dayQueued/advanceDay, 세션 타이머 generation을 명확히 교체한다. 기존 세션 credit()가 Window 변경을 실제 일일 초기화로만 가정하므로, 단순 설정 변경을 가짜 초기화로 전달하여 partial time을 유실하지 않도록 별도 분기를 검증한다.
- 보상 필요 시간/일일 횟수 변경도 기존 정책과 호환되게 재계산한다. 기존 진행 상태를 임의 초기화하지 않는다. F02의 전체 초기화 generation과 혼동하지 않으며 이전 예약은 취소 또는 stale callback으로 무효화한다.

### 완료 검증

초기화 주기 증가·감소, 경계 직전/직후 리로드, 보상 임계치 변경, PC방 활성/비활성, 이미 일일 한도에 도달한 계정, 동시 리로드, 지연된 이전 타이머, DB 실패, 재접속. 유효한 설정 변경 후 새 경계가 즉시 적용되고 정상 출석 누적시간/보상은 보존되어야 한다.

## F04. 캐릭터 버프 저장의 DB 쓰기 절감

### 확인된 미완료 항목

- [CharBuffTable.java:112–140](https://github.com/choeyeonggeol16-sketch/1234/blob/32d5d64c6bf721861bc20a9dec1893da4fc5fba4/rkern/src/l1j/server/server/datatables/CharBuffTable.java#L112-L140): 활성 효과 snapshot 및 기존 whitelist 순서 정렬은 이미 구현되어 있다. 이 부분을 다시 리팩토링하지 않는다.
- [CharBuffTable.java:155–196](https://github.com/choeyeonggeol16-sketch/1234/blob/32d5d64c6bf721861bc20a9dec1893da4fc5fba4/rkern/src/l1j/server/server/datatables/CharBuffTable.java#L155-L196): 저장은 여전히 char_obj_id의 모든 기존 행을 DELETE한 다음 현재 snapshot 전체를 batch INSERT하는 단일 트랜잭션이다. 이는 현재 원자성을 보장하는 정상 방식이며 그 자체를 결함이라고 하지 않는다. 이전 보고서의 DB 변경분 최적화는 미완료다.

### 수정 지시

1. 실제 character_buff schema, key/중복 허용, paid buff receipt, DeleteBuff, 로그인 복원 및 로그아웃 저장 호출부를 확인한다. 기존 whitelist 및 순서, 변신 spriteId, 영구 지속 효과와 남은 시간 표현을 유지한다.
2. CacheD의 기존 세션 owner lane에서 확정된 저장 이미지와 현재 활성 snapshot을 비교할 수 있는지 검토한다. 재로그인과 유료 버프 경로에서 기존 DB 이미지와 메모리 기준이 달라지지 않도록 한다.
3. 안전한 경우에만 동일 snapshot의 불필요한 SQL을 생략하고, 제거된 효과는 DELETE, 추가·변경된 효과는 INSERT/UPDATE 또는 적절한 batch UPSERT로 처리한다. 정산은 하나의 트랜잭션으로 유지한다. 필요한 key가 없다면 실제 기존 행의 중복과 복원 의미를 먼저 검증한 후 migration을 제안한다. 임의 schema 변경 금지.
4. 남은 시간이 감소하는 정상 버프는 실제 저장 대상 변경일 수 있다. 시간 감소를 무조건 무시하거나 기존 로그아웃 복원 규칙을 변경하지 않는다. COMMIT 이후에만 persisted image를 갱신하고 저장 실패·ACK 유실·동시 receipt에 대한 기존 보호를 유지한다.
5. 조회를 위한 추가 SQL/메모리 비용까지 포함해 기존 전체 교체 방식과 비교한다. 실제 개선이 확인되지 않거나 기존 방식이 더 안전·효율적이면 그 이유와 측정 결과를 남기고 전체 교체 방식을 유지한다. 단순히 DELETE를 없애기 위해 더 비싼 구조를 도입하지 않는다.

### 완료 검증

무변경, 효과 추가/제거/만료/재등록, 변신, 유료 버프, 영구 지속, logout/relogin, 기존 복원 순서와 wire 의미, DB 장애 및 ACK 유실. 최종 DB 행의 동일성을 확인하고 SQL round trips, 쓰기 행 수, CPU, allocation 및 저장 지연을 비교한다.

## 제출 기준

F01과 F02를 먼저 완료하고 F03, F04 순으로 진행한다. 각각 실제 변경 파일/커밋과 회귀 결과를 제출한다. 전체 Java 21 타입 컴파일과 관련 회귀를 수행하고, 실제 DB 장애·재시작 검증과 미검증 항목을 구분한다. 기존 완료 기능의 재구현은 금지한다. 이 문서는 수정 완료를 의미하지 않는다.
