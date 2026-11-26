# RKern 최신 서버 병목 검증 및 NPC 경로 탐색 조정 계획

- 기준: 2026-11-26 최신 전체 소스, 브랜치 `rkern/thread-pool-removal-20261126`, 커밋 [`0ab42924bd1d74be30c9bf8307db8350d46c83c6`](https://github.com/choeyeonggeol16-sketch/1234/commit/0ab42924bd1d74be30c9bf8307db8350d46c83c6).
- 범위: TaskLane, CIO 타이머/패킷 실행 순서, NPC 동적 점유 경로 탐색, CacheD, 중첩 브로드캐스트.
- 상태: 소스 정적 확인 완료. 실제 게임 부하에서 병목 확정 및 개선 효과 실측은 미실시. 본 커밋은 보고서만 추가하며 production 소스는 변경하지 않는다.
- 사용자 지정 NPC 시험 설정: 기본 탐색 반경 8, 확장 탐색 반경 16. 운영 채택은 회귀 및 A/B 통과 후 결정한다.

## 1. 확인 사실과 검증 가설

| ID | 소스에서 확인한 사실 | 검증할 가설 | 상태 |
|---|---|---|---|
| P1 | TaskLane은 여유 병렬 실행 슬롯마다 플랫폼 스레드를 시작하고 큐가 비면 종료한다. | 간헐적인 작업에서 스레드 생성/종료와 큐 대기가 실제 CPU 또는 지연 병목인가? | 미측정 |
| P2 | CIO 루프는 밀린 타이머 최대 100틱 처리 후 제한된 패킷 작업, Selector I/O 순으로 진행한다. 개별 버킷에는 별도 처리 시간 예산이 없다. | 집중 만료가 동일 샤드의 네트워크와 게임 작업을 지연시키는가? | 미측정 |
| P3 | 동적 점유 BFS는 기본 반경 12, 확장 반경 32이며 기존 primitive 배열을 재사용한다. | 8/16으로 축소해도 추격/공격 자리 확보 기능을 보존하면서 CPU 비용을 줄일 수 있는가? | 미측정 |
| P4 | CacheD 전역 모니터가 캐시 및 작업/리소스 순서 상태를 보호한다. | 독립 유저의 동시 경제 작업에서도 전역 모니터 또는 후보 선택이 처리량을 제한하는가? | 미측정 |
| P5 | 특정 optional visual 방송에 중첩 AOI 수신자 순회가 있다. | 해당 호출이 밀집 지역에서 실제 빈번하게 실행되고 중복 전송이 병목이 되는가? | 미측정 |

위 다섯 가지는 확정된 성능 결함이 아니다. 구조만 확인했고 병목의 크기, 발생 빈도 및 사용자 영향은 실측해야 한다. 검증 우선순위는 사용자 지정 변경 후보인 P3, 신규 타이머 이관의 P1/P2, P4, P5 순으로 제안한다. 실제 측정으로 재정렬한다.

## 2. P3: NPC 기본 8 / 확장 16 (사용자 지정)

근거: [PathFinder.java 60–240](https://github.com/choeyeonggeol16-sketch/1234/blob/0ab42924bd1d74be30c9bf8307db8350d46c83c6/rkern/src/l1j/server/server/model/ai/core/PathFinder.java#L60-L240), [CMoveToTargetRelativeAction.java 107–240](https://github.com/choeyeonggeol16-sketch/1234/blob/0ab42924bd1d74be30c9bf8307db8350d46c83c6/rkern/src/l1j/server/server/model/npc/ai/action/CMoveToTargetRelativeAction.java#L107-L240), [AstarProvider.java](https://github.com/choeyeonggeol16-sketch/1234/blob/0ab42924bd1d74be30c9bf8307db8350d46c83c6/rkern/src/l1j/server/server/model/npc/ai/AstarProvider.java).

기존 일반 정적 경로 추격과 분리된 동적 몸체 점유 BFS다. 기본 탐색은 NPC 좌표 중심, 확장은 NPC와 목표의 중간 좌표 중심이다. 근접 공격 슬롯 후보가 전혀 없으면 사전 검사에서 반환한다. 기존 32는 RKern 자체 상수이며 NC 리니지2 원본의 확정 수치가 아니다.

시험 변경 (다른 경로 정책/상수는 유지):

```java
private static final int BODY_ROUTE_RADIUS = 16;
private static final int BODY_ROUTE_LOCAL_RADIUS = 8;
```

| 지표 | 기준 12/32 | 후보 8/16 | 이론상 감소 |
|---|---:|---:|---:|
| 기본 격자 | 25×25 = 625 | 17×17 = 289 | 53.76% |
| 확장 격자 | 65×65 = 4,225 | 33×33 = 1,089 | 74.22% |
| 확장 최대 방향 루프 반복 | 33,800 | 8,712 | 74.22% |
| 기본+확장 최대 방문 타일 합계 | 4,850 | 1,378 | 71.59% |
| 네 primitive 배열 payload (4+2+1+4 B/셀) | 46,475 B | 11,979 B | 74.22% |

배열 수치는 PathFinder 인스턴스당이며 객체 헤더, 정렬 및 다른 필드는 제외한다. 최대 방향 루프 반복 수는 방문 타일×8의 상한이지 실제 충돌 조회 수가 아니다. 조기 종료, 장애물, 확장 호출률에 따라 실제 CPU 절감량이 달라진다. 기본 반경 축소로 확장 빈도가 오히려 증가할 수 있다. 중간 좌표 중심 확장이라 먼 목표 또는 반대편 공격 슬롯이 탐색 창 밖으로 밀려날 수 있다. 기존 정적 경로 범위와 랜덤워크는 변경하지 않는다.

### 필수 회귀 및 A/B

- 동일 맵/시드/스폰/이동 일정으로 12/32 대 8/16을 비교한다. 별도 진단군 12/16은 기본 반경 축소 영향 분리 시에만 사용한다.
- NPC 1/20/50/100마리: 직선 추격, 좁은 입구, ㄱ/U자 장애물, 장애물 반대편 목표, 8개 근접 슬롯 포화, 우회로 유무 및 반경 경계(8/9/16/17칸, 중간 좌표 및 창 밖)를 확인한다.
- 펫·서먼·컴패니언 길막, 타겟 이동/사망/변경, 투명 상태 유효성, 순간이동/맵 이동, 예약 선점 경쟁, 도착 후 공격 전환, NPC 사망/재사용, 취소 및 예약 반환을 검증한다.
- 기존 2D 8방향 통행과 점유, 대각선 끼임 금지, 랜덤워크 3~6칸 및 6초 휴식을 보존한다. 기존 일반 정적 경로와 NPC 타겟 변경 정책에 손대지 않는다.
- 기본/확장 호출 횟수, 확장 비율, 방문 셀 수, 방향 검사 수, 탐색 CPU ns, NPC tick/action p50/p95/p99, 성공률, 공격 전환 시간, 재탐색 횟수, 고착/좌우 왕복, 예약 누수 및 실제 이동/공격 횟수를 기록한다. 단순 방문량 감소만으로 채택하지 않는다.
- 기준에서 성공하던 필수 게임 동작의 회귀가 있으면 후보 채택 보류. 실측 CPU 개선과 지연 악화 여부를 함께 판정한다. 원인 확인 없이 타겟 변경 또는 길막 정책을 임의 변경하지 않는다.

## 3. P1: TaskLane 실행 수명

근거: [TaskLane.java 19–100](https://github.com/choeyeonggeol16-sketch/1234/blob/0ab42924bd1d74be30c9bf8307db8350d46c83c6/rkern/src/l1j/server/server/task/TaskLane.java#L19-L100), [CServerTimer.java 95–165](https://github.com/choeyeonggeol16-sketch/1234/blob/0ab42924bd1d74be30c9bf8307db8350d46c83c6/rkern/src/l1j/server/server/model/network/thread/CServerTimer.java#L95-L165).

WORLD/INSTANCE/SESSION/PERSISTENCE 각 영역은 제한된 parallelism과 큐를 사용한다. tryExecute는 active가 한도 미만이면 threads.newThread(() -> drain(task)).start()를 호출하고, 한도에 도달하면 ready에 등록한다. drain은 대기 작업을 연속 처리하고 비면 종료한다. 장시간 살아 있는 개별 BlockingEvent의 가상 스레드는 별도 경로다. 이는 기존 공유 ThreadPool을 제거한 설계의 실행 수명 비용이지 모든 작업마다 스레드를 생성한다고 단정할 수 없다.

검증: 실제 제출 경로 및 호출 빈도를 수집하고, 1/10/100/1,000/5,000건/s의 합성 간헐·버스트 작업과 실제 월드/접속/저장 혼합 부하에서 lane별 ThreadStart/ThreadEnd, 활성 스레드, 큐 길이와 대기 p99, 스레드 생성 비용, 스케줄링 및 CPU/작업, rejected/실패 수를 확인한다. JDBC·블로킹 작업은 CIO에서 실행하지 않는다. 문제 확인 시 기존 도메인 소유권과 bounded admission을 보존한 수명 개선 후보를 별도 A/B한다. 새 공용 스케줄러/임의 워커를 선제 도입하지 않는다.

## 4. P2: CIO 타이머 집중 처리

근거: [CIOThreadCallback.java 2240–2310](https://github.com/choeyeonggeol16-sketch/1234/blob/0ab42924bd1d74be30c9bf8307db8350d46c83c6/rkern/src/l1j/server/server/model/network/thread/CIOThreadCallback.java#L2240-L2310), [CThreadLocalTimer.java 320–420](https://github.com/choeyeonggeol16-sketch/1234/blob/0ab42924bd1d74be30c9bf8307db8350d46c83c6/rkern/src/l1j/server/server/model/network/thread/CThreadLocalTimer.java#L320-L420).

CIO 샤드는 최대 100개의 밀린 tick을 처리한 다음 MAX_PACKET_BURST까지 ingress 작업을 처리하고 Selector driver.advance()를 호출한다. tick은 현재 버킷의 모든 슬롯을 순회한다. 일반 WheelTask는 직접 실행하며 일부 타이머는 기존 컨트롤러 또는 TaskLane으로 위임한다. 따라서 모든 게임 타이머가 CIO에서 전체 작업을 수행한다고 해석하면 안 된다.

검증: 동일 샤드에서 100/1,000/5,000/10,000개 동시 만료, 균등 분산 만료, 전투+버프+패킷 혼합 부하 및 tick backlog를 재현한다. tick 소요 p99/max, 타이머 만료 지연, 실제 실행 지연, 패킷 ingress 지연, Selector advance 간격, RTT p99/p99.9, 큐 깊이, 누락/중복/취소 후 실행 여부를 확인한다. 문제 확인 시 소유 실행 순서와 동일 세션 FIFO를 유지한 시간 예산 또는 batch 개선만 A/B한다. 만료 순서, 재시도, stop/restart, stale epoch, 스킬/아이템 수명 안전성에 회귀가 없어야 한다.

## 5. P4: CacheD 전역 모니터

근거: [CacheD.java 771–840](https://github.com/choeyeonggeol16-sketch/1234/blob/0ab42924bd1d74be30c9bf8307db8350d46c83c6/rkern/src/l1j/server/server/storage/CacheD.java#L771-L840), [CacheD.java 1010–1095](https://github.com/choeyeonggeol16-sketch/1234/blob/0ab42924bd1d74be30c9bf8307db8350d46c83c6/rkern/src/l1j/server/server/storage/CacheD.java#L1010-L1095), [CTransaction.java 270–320](https://github.com/choeyeonggeol16-sketch/1234/blob/0ab42924bd1d74be30c9bf8307db8350d46c83c6/rkern/src/l1j/server/server/storage/cached/CTransaction.java#L270-L320).

전역 lock이 캐시 및 리소스 소유권 인덱스, FIFO 후보 등록/선택 및 활성 상태를 보호한다. 기존 ordered candidate index는 전체 대기 큐 순회를 줄이는 개선이다. 다만 firstResourceFree는 후보 집합에서 활성 리소스 충돌을 건너뛰며 select는 DB worker 여유도 확인한다. 전역 Lock 존재만으로 DB 전체가 단일 스레드로 직렬화된다고 단정하지 않는다.

검증: 독립 유저 거래, 동일 아이템 경쟁, 창고/상점/파티 재화, 동시 저장, ACK 지연·유실/재시작 조건을 분리한다. JFR JavaMonitorEnter, lock 보유/대기 시간, ready queue 깊이, 후보 검사 횟수, 작업 대기/실행/commit/ACK 지연, SQL/초, 트랜잭션/초, DB 연결 대기 및 CPU를 기록한다. MySQL 실제 통합과 메모리 모의 테스트를 구분한다. 전역 모니터 경합이 실제로 지배적인 경우에만 락 범위 또는 데이터 구조를 검토한다. 아이템·재화 보존, multi-owner 순서, receipt, ACK 재실행 방지, 소유권 및 장애 복구를 절대 약화시키지 않는다.

## 6. P5: 중첩 AOI 브로드캐스트

근거: [CSharedBroadcast.java 520–545](https://github.com/choeyeonggeol16-sketch/1234/blob/0ab42924bd1d74be30c9bf8307db8350d46c83c6/rkern/src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java#L520-L545), [CAsyncSendConstBuffer.java 64–98](https://github.com/choeyeonggeol16-sketch/1234/blob/0ab42924bd1d74be30c9bf8307db8350d46c83c6/rkern/src/l1j/server/server/model/network/core/broadcast/CAsyncSendConstBuffer.java#L64-L98).

sendWrittenNestedVisibleBestEffort는 origin의 가시 플레이어마다 다시 각 anchor의 가시 플레이어를 순회한다. 기존 중복 전송과 필터 및 순서를 의도적으로 보존하는 optional visual 경로이며 일반 모든 방송이 이중 루프를 쓰는 것은 아니다. 완전히 겹치는 500명 규모의 AOI라면 25만 회 수준의 수신자 순회 가능성이 있으나 실제 전송량은 필터·가시성·호출 빈도에 의존한다. 공유 원본의 재사용과 연결별 FIFO는 이미 구현되어 있다.

검증: 먼저 production 호출부와 호출 빈도 확인. 밀집 20/100/500/1,000명 시각 효과 부하에서 외부/내부 순회 수, 유일 수신자/총 전송, 중복 횟수, CPU/방송, 할당, SEND 큐·거부 및 패킷 지연을 기록한다. 실제 호출 경로가 드물거나 영향이 작으면 유지한다. 중복이 원래 게임 동작이면 임의 제거하지 않는다. 변경은 방송 전용 경로로 제한하고 패킷 바이트, 암호화 순서, 필터와 느린 연결의 안전성을 대조한다.

## 7. 공통 측정 및 완료 조건

- 기준 커밋을 고정하고 각 변경은 하나씩 적용한다. 같은 JDK 21, JVM 옵션, CPU affinity/쿼터, OS, 맵, 데이터, 클라이언트 부하와 seed를 사용한다. 기준/후보 순서를 교차한다. warmup 후 최소 5회 반복, 중앙값 및 회차별 편차와 p99/p99.9를 기록한다.
- 동일 장비에서 JFR CPU sampling, allocation, JavaMonitorEnter, ThreadStart/End, GC/safepoint를 확인하고, 짧은 경합 및 타이머 지연은 별도 계측으로 보완한다. 계측 overhead가 있는 실행과 무계측 성능 실행을 분리한다.
- 1,024/5,000 TCP 연결 시험, 실제 암복호화 및 혼합 게임 로직 부하를 분리하고, 5,000 연결 중 유휴/활성 비율과 NPC 수를 명시한다. Windows WEPoll과 Linux epoll 결과를 섞지 않는다.
- CPU/요청, RSS, heap retained, direct buffer, 커널 소켓 메모리(가능할 때), GC 지연, 처리량, 오류/거부, 종료 시 누수 및 지연시간을 기록한다. 최종 실제 월드/DB 운영형 30~60분 이상 soak를 별도 진행한다.
- 모든 수치에 원시 CSV, 실행 환경, 기준 및 후보 commit, 재현 명령과 검증 로그를 남긴다. 미실행/실패/측정 불가능은 그대로 표시한다. 기존 소스 대비 실제 성능 향상 및 게임 동작 안정성이 확인된 변경만 적용한다.
- 소스 변경은 별도 승인/작업 단계에서 수행하며 이번 보고서에서는 수행하지 않았다.

## 8. 기존 벤치마크와 이번 검증의 관계

- [11월 25일 생산 TCP A/B](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/45543397992): 네트워크 구조 통합 전후 시험.
- [1,024 연결 6회 재측정](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/45543955387): window 8 수정 후 517,730 req/s, p99 21.78 ms, CPU/req 2.013 µs, allocation/req 11.73 B. window 1 수정 후 144,660 req/s, p99 14.32 ms.
- [NC 참고 SEND pool 비교](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/45546239359): 기존 bitmap 우세 조건 확인; NC 참고 후보는 운영 미반영.
- [5,000 연결 게임 리스너 60초](https://github.com/choeyeonggeol16-sketch/1234/blob/059631b0dceb65f9157fed68ce224989fa4b400a/rkern/docs/jproring_code_audit_20261120/FOUR_ITEMS_KO.md): 유휴 4,000/활성 1,000, 23,354 req/s, p99 20.63 ms, direct backing 20.80 MiB, RSS 약 211~226 MiB. 실제 월드 전투/DB 부하와 최신 타이머 이관 성능을 입증하지 않는다.

**결론:** 기존 네트워크 구조는 유지한다. NPC 8/16은 사용자 지정 A/B 검증 후보로 확정한다. 나머지 P1/P2/P4/P5는 실제 병목으로 입증된 후에만 수정한다. 전체 서버 수용량 또는 성능 향상률을 이 보고서로 확정하지 않는다.
