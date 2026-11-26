# NC OFF 실제 소스 기반 스레드풀·반복 작업 분석
2026-11-26 — RKern ThreadPool 제거 설계 기준

## 범위와 원본
이번에는 이전 요약을 재사용하지 않고 [Open_LU4_L2OFF](https://github.com/luannbr/Open_LU4_L2OFF)의 실제 C++ 파일과 함수 본문을 조회했다. 아래 줄 번호와 blob SHA는 조회 시점 main 기준이다. 공개 복원·이식판이며 원본 실행 파일과 독립적으로 재대조하지 않았다. 원본 주소는 복원 소스의 주석에 기록된 주소다. RKern Java 코드는 이번 작업에서 변경하지 않는다.

## 1. 스레드 생성: 일반 범용 실행기와 IO 워커를 구분한다
[L2Server/src/L2Server.cpp:2177–2193](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Server.cpp#L2177-L2193) (blob d4d8674a377e90575cb387906eb5e4ec7a60ebdf):
- 선택적 PerfMon 전용 스레드를 만든다.
- GetIOThreadCount() → InitIOThread() → 각 logical ID에 CreateIOThread2(new CIOThreadCallback(threadId)).
- 이후 CCleft, CBlockUpsetManager를 AddTimer(1000,0)로 시작한다. 기능별 반복 executor 생성이 아니다.
[Shared/src/IOCL2Server.cpp:338–349](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOCL2Server.cpp#L338-L349) (blob 3dc6bc4d727aced30e585c7ef36cdc38f69b5a3f): GetIOThreadCount()는 CPU 논리 프로세서 수를 최대 16으로 제한한다. 다른 L2Server 바이너리 분석의 8개 상한을 이 버전에 적용하면 안 된다.
[IOThreadRuntimeWorkers.cpp:164–189](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/Runtime/IOThreadRuntimeWorkers.cpp#L164-L189): 이식판의 StartBoundThread는 StackThread를 생성하고 IOThread2(callback)을 호출하며 native handle을 CThreadManager에 등록한다.
[ThreadManager.cpp:154–182](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/ThreadManager.cpp#L154-L182): 스레드 ID(TLS), 핸들, 수량 등록 담당이다. 범용 Runnable 제출 실행기가 아니다.

## 2. 일반 반복 작업이 실행되는 정확한 경로
[IOCL2Server.cpp:924–988](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOCL2Server.cpp#L924-L988):
- IOThread_common (원본 주석 0x007cce44): 동기화 시각이 지났으면 IOThreadNextSyncTime(), 이후 다음 기준을 현재 시각+50ms로 갱신한다.
- 완료 대기 timeout은 다음 동기화까지 남은 시간이다. Timeout이면 반복 루프로 복귀하고, 완료가 있으면 해당 object의 OnIOCallback을 실행한다.
- IOThreadNextSyncTime (0x007cc748): CIOObject::TimerDispatch() → IOThreadNextSyncTimeCallback().
- [L2Server.cpp:1288 이후](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Server.cpp#L1288): 후자는 world map publication, IO heartbeat 및 smart ID garbage collection도 처리한다.
- 따라서 IO 실행 루프에 일반 타이머 처리와 월드 동기화가 통합되어 있다. 타이머용 별도 ScheduledThreadPoolExecutor 및 만료 때마다 범용 즉시 작업 풀로 넘기는 구조는 이 경로에 없다.

## 3. AddTimer 등록·자료구조·실행
[IOCL2Server.cpp:780–828](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOCL2Server.cpp#L780-L828):
- AddTimer(delay,id) → AddTimer2(delay,id,0); 최소 delay 80ms로 clamp.
- Timer 참조 획득, dueTick 계산, SetInTimer(true), g_ThreadLocalTimer.AddTimer(this,dueTick,id,uid).
- 80ms 제한은 해당 OFF 코드의 정책이며 RKern의 기존 게임 타이밍을 일괄 변경할 근거가 아니다.

[ThreadSafeLibrary.h:2117–2125](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/ThreadSafeLibrary.h#L2117-L2125):
TIME_SLICE=50ms, MAX_SLOT=200, RESERVED_SLOT_TIME=10000ms, nMaxIOThread=32, nCountPerSlot=1000, nMaxObjectCount=469000.
32는 배열 용량이지 실제 IO 워커 개수가 아니다.

[ThreadSafeLibrary.cpp:86–251](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/ThreadSafeLibrary.cpp#L86-L251) (blob 9cd45354ec6efb0ab9ed0af9b4bf43ea5d029d41):
- 호출자의 CThreadManager::GetThreadID()로 타이머 저장소 선택.
- 가까운 예약은 슬롯 배열, 넘치는 슬롯은 풀에서 연결 슬롯 재사용. 먼 예약은 m_ExtTimer[thread] priority queue에 저장.
- Dispatch는 경과 슬롯을 처리하고 장기 예약을 wheel 범위 안으로 옮긴다.
- CSlot은 object, id, uid를 배열에 저장한다. LISTEN ID 27에서는 OnTimerCallback 호출. 그 외에는 thread_local 카운터의 previous==100일 때 OnTimerCallback 호출 및 카운터 초기화(101번째), 나머지는 SafeIOCallback 사용.
- [IOCSocketCore.cpp:352–375](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOCSocketCore.cpp#L352-L375)까지 직접 확인: 기본 OnTimerCallback은 PostIOCompletion으로 IO 완료 큐에 게시한다. 따라서 LISTEN 및 101번째 홉은 재게시, 일반 SafeIOCallback은 현재 스레드에서 OnIOCallback 직접 실행이다. OnIOCallback은 OnTimer/OnTimer2 호출 후 Timer 참조 해제, 게시 실패 경로도 참조 해제한다.
- 중요: [SafeIOCallback:773–778](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOCL2Server.cpp#L773-L778)은 object->OnIOCallback을 직접 호출한다. 이름만 보고 IOCP 재게시 또는 별도 worker enqueue라고 해석하면 틀린다.
- 등록 스레드 기준 자료구조라는 사실이 모든 객체 변경의 직렬성을 보장하지는 않는다. RKern은 기존 CIO owner shard에서 실행하도록 유지한다.
- [IOCL2Server.cpp:832–851](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOCL2Server.cpp#L832-L851)의 owner cancellation은 portable shutdown 추가 경로라고 명시된다. Java에서는 logout/reuse 취소와 generation 검증을 보존한다.

## 4. 실제 반복 작업 사례
| 기능 | 실행 및 재등록 | 근거 |
|---|---|---|
| 일반 공지 | TimerExpired(1) → IntervalAnnounceCheck → AddTimer(1000,1) | [AnnounceTimer.cpp:180–187](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/AnnounceTimer.cpp#L180-L187) |
| 예약 공지 | lock → GuardAddTimer(this,60000,0) → _Update | [ReservationAnnounceTimer.cpp:126–134](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/ReservationAnnounceTimer.cpp#L126-L134) (GuardAddTimer 소멸자 본문은 이번 조회 미확인) |
| 예약 NPC 스폰 | lock → _ProcReservationInfo → AddTimer(60000,0) | [ReservationNPCSpawnTimer.cpp:401–409](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/ReservationNPCSpawnTimer.cpp#L401-L409) |
| IO watchdog | timestamp, logical thread ID 갱신 → AddTimer(5000,0) | [IOCL2Server.cpp:200–211](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOCL2Server.cpp#L200-L211) |
| 메모리·인스턴스 보고 | ReportMemory → ReportInstance → AddTimer(3600000,0) | [L2Server.cpp:251–256](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Server.cpp#L251-L256) |
| 혈맹·퇴거 | startup에서 각각 AddTimer(1000,0) | [L2Server.cpp:2327–2334](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Server.cpp#L2327-L2334) |
| NPC respawn | 초기 25000ms 등록, TimerExpired에서 스폰 시각 검사 후 25000ms 재등록 | [L2NPC/src/RespawnTimer.cpp:35–84](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2NPC/src/RespawnTimer.cpp#L35-L84) |

즉 반복 함수마다 전용 Java 스레드나 ScheduledFuture가 반드시 필요한 것이 아니다. 일반적으로 만료된 객체의 callback에서 업무를 수행하고 다시 등록한다. 같은 timer ID 중복 등록 방지, 예외 후 재등록 여부는 각 owner 정책을 별도로 확인해야 한다.

## 5. 별도 스레드도 실제로 존재한다
모든 작업을 CIO에 넣으라는 뜻이 아니다.
- [L2Server.cpp:812–854](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Server.cpp#L812-L854): PerfMonThread는 독립 반복 루프.
- [ConnectThreadManager.cpp:19–48, 65–73](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/ConnectThreadManager.cpp): relay/account 재연결 후 5000ms sleep, CONNECT logical ID 등록, CreateOwnedThread 사용. 블로킹 외부 서비스의 전용 역할이다.
- [L2Server.cpp:2264–2269,2353–2355,2386–2398,2506–2509](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Server.cpp#L2386-L2398): CommCli, ConnectThreadManager, Petition, PremiumSvc, GoodsInventory, RaidClientManager, CaptchaManager의 CreateThread 호출이 있다. 각 내부 실행 본문은 이번에 전수 확인하지 않았다.
- [L2Server.cpp:3140–3165](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Server.cpp#L3140-L3165): 별도 listener 역할.
- [IOCL2Server.cpp:864–908](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOCL2Server.cpp#L864-L908): wait loop도 TimerDispatch 후 handle wait/sleep 수행.
- [L2NPC/src/NPCServer.cpp:393–402](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2NPC/src/NPCServer.cpp#L393-L402): dbnpc 타이머 시작 및 NPC IO 워커 생성. L2NPC는 독립 실행 소유자다.
- [DeferredJob.cpp](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/DeferredJob.cpp): deferred job은 캐릭터 DB ID별 작업 보관 및 EnterWorld/DoTimerJob에서 처리한다. 이 클래스 자체는 범용 worker pool이 아니다. 실제 DoTimerJob 상위 호출부는 이번에 미확인.

## 6. RKern ThreadPool.java 제거 계획 (아직 구현하지 않음)
목표: 범용 ThreadPool에 게임 반복 작업 및 즉시 작업을 몰아넣는 구조를 제거하고, 현재 CIO·NPC·CacheD 소유자에게 책임을 돌려준다.
1. 최신 브랜치의 ThreadPool.execute, schedule, scheduleAtFixedRate, scheduleWithFixedDelay, 별도 Thread.start 등 모든 사용처와 우회 wrapper를 열거한다. 현재 Java 호출부 전수조사는 아직 하지 않았으므로 아래는 분류 기준이며 확정된 변경 목록이 아니다.
2. 공지, 월드 시간, 이벤트/보스, 버프, 장비, 회복, 자동 종료 등 경량 반복 작업은 기존 CIO 타이머 또는 이미 존재하는 owner 타이머의 callback으로 이전한다. 글로벌 작업은 단일 기존 owner 지정, PC/NPC 작업은 각 기존 owner 지정. 같은 업무를 각 IO 스레드에서 중복 실행하지 않는다.
3. 캐릭터별 반복 스캔/예약이 실제 동일한 주기와 수명을 갖는 경우만 기존 heartbeat에 통합한다. 게임 주기와 효과 실행 순서 보존.
4. 즉시 작업은 이미 owner 위에서 호출되는지 판별한다. 안전하면 직접 실행, 외부 스레드면 기존 owner handoff 사용. 새 큐·워커·범용 executor를 만들지 않는다. 외부 블로킹 작업은 기존 CacheD/전용 서비스에서 수행하고 완료 결과를 owner에게 반환한다.
5. shutdown, logout, respawn, 재접속, 예외, 중복 tick, 긴 작업의 IO 지연, queue overload, 타이머 누락 및 참조 수명 검증.
6. 모든 참조가 제거되고 대체 경로가 검증된 후에만 ThreadPool.java와 관련 설정/startup/shutdown 코드를 삭제한다. 스레드 수 감소만 목표로 DB·파일·외부 서비스 블로킹을 CIO에 넣지 않는다.

## 7. RKern 실제 소스 대조 — 구체적인 제거 대상

[ThreadPool.java:73–119](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/ThreadPool.java#L73-L119): 범용 실행기 필드 3개(_scheduler, _highPriorityScheduler, _instantPool)가 존재한다. 일반 예약 및 조건부 고우선순위 예약 실행기, bounded instant pool. 130–167행의 예약 admission monitor, 302–350행의 RunnableWrapper/Future 처리까지 확인했다. execute(Runnable)과 execute(Thread)는 다르다(185–206행). 후자는 전용 스레드 시작. scheduleBlocking(219–230행)은 지연 후 가상 스레드 시작이므로 예약 트리거와 블로킹 실행을 구분한다.

| 실제 확인한 Java 파일 | 현재 실행 경로 | 이전 기준 |
|---|---|---|
| [AuraController:49–85](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/Controller/AuraController.java#L49-L85) | fixed-delay → queued CAS → instant execute | 기존 CIO/상태 소유자에서 실행, coalescing/epoch 유지 |
| [ShipTimeController:50–80](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/Controller/ShipTimeController.java#L50-L80) | fixed-delay → instant execute | 선박 기존 소유자에서 타이머 실행, 하위 블로킹 확인 |
| [BossController:118–166](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/Controller/BossController.java#L118-L166) | scheduleRequired, 버전 및 ScheduledFuture 관리 | CIO server timer와 기존 NpcServer 소유권 활용, DB/스폰 순서 및 취소 유지 |
| [CInventoryExpiry:67–105](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/model/character/scheduler/CInventoryExpiry.java#L67-L105) | 기존 CIOThreadCallback.scheduleServerTimer, WheelTask 재등록 | 이미 통합된 참조 구현, 새 풀 불필요 |

[GameServer.java:567–576](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/GameServer.java#L567-L576)의 기존 ThreadPool 종료 및 스토리지 종료 순서를 유지한다. 142행 ThreadPool 초기화도 최종 제거 대상이다.

실행기 삭제 순서: 일반/고우선순위 예약 호출을 CIO의 기존 타이머 및 업무 소유 실행부로 이전 → 두 예약 풀의 참조 0 확인 후 제거 → instant 호출의 소유 실행부 직접 실행 또는 기존 owner handoff로 이전 → blocking/dedicated 호출의 기존 업무 소유권·종료 보장 확인 → _instantPool 제거 → ThreadPool.java 및 미사용 설정/시작/종료 참조 제거. fixed-rate의 예정 시각과 fixed-delay의 완료 후 재등록을 구별한다. 전체 호출부 수와 하위 블로킹 경로는 아직 전수 확인하지 않았으므로 실제 이전 전 확인한다.

## 8. 원본의 위험 경계

AddTimer2가 Timer 참조를 확보한 후 호출하는 CThreadLocalTimer::AddTimer는 void 반환이다. 음수 interval 조기 반환 분기(147–172행)의 참조 회수는 조회 경로에 보이지 않는다. 잠재 수명 결함 후보이지 실제 발생을 재현한 것은 아니다. RKern은 등록 실패와 참조/상태 회수를 일치시킨다.

## 9. 확인 범위
L2Server 실제 IO 생성/실행 루프, thread-local timer 등록/dispatch/직접 callback, 여러 반복 업무의 실제 재등록, 별도 재연결 스레드를 조회 완료. CacheD/AuthD 전수 조사, User/Creature heartbeat 원본 파일 식별, 전체 즉시 작업 호출 그래프 및 최신 RKern ThreadPool 사용처 전수 대조는 미완료. 단 ThreadPool.java, GameServer.java, AuraController, ShipTimeController, BossController, CInventoryExpiry의 실제 본문은 확인하여 7절에 반영했다. GitHub 재귀 트리 응답이 truncated였으므로 소스 전체를 검토했다고 주장하지 않는다. 실제 Java 변경, 컴파일, 부하 및 인게임 테스트는 수행하지 않았다. 다음 작업은 최신 Java 호출부 전수 분류 후 기능별로 이관하는 것이다.
