# NC OFF 실제 소스 기반 스레드풀 제거 설계 — 2026-11-26

이 문서는 이전 요약 보고서를 전면 폐기하고 공개 OFF C++ 복원 소스와 RKern 대상 브랜치의 실제 파일을 직접 읽어 작성한 대체 보고서다. OFF는 [Open_LU4_L2OFF](https://github.com/luannbr/Open_LU4_L2OFF) main의 복원/이식판이며 원본 바이너리 자체를 새로 역분석한 것은 아니다. 파일별 확인 범위와 blob SHA를 아래에 명시한다.

## 1. 결론

일반 게임 반복 작업을 위한 Java식 범용 예약 풀 + 고우선순위 예약 풀 + 즉시 실행 풀의 삼중 구조를 NC L2Server에서는 확인하지 못했다. NC는 기존 IO 워커와 LISTEN/WaitThread의 타이머 디스패치를 재사용한다. 단, 외부 연동, 모니터링 등 목적이 명확한 전용 스레드도 실제 존재한다. 목표는 RKern의 범용 ThreadPool.java 제거이지 모든 스레드 제거가 아니다. 기존 CIO, NpcServer, CacheD 소유 실행 구조를 활용하고 동기 I/O를 CIO에 합치지 않는다.

## 2. 실제 스레드 생성 및 역할

- [L2Server.cpp:2177–2190](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Server.cpp#L2177-L2190): ListenThread_common에서 PerfMon 초기화 성공 시 전용 스레드를 생성하고, GetIOThreadCount → InitIOThread → CreateIOThread2(new CIOThreadCallback(threadId))를 CPU 수만큼 반복한다.
- [IOCL2Server.cpp:338–371](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOCL2Server.cpp#L338-L371): GetIOThreadCount = min(논리 CPU, 16), 원본 주소 주석 0x007cb144. 원본 IO 초기화는 하나의 completion port와 event를 생성했다고 명시한다. 현재 이식판은 IOThreadRuntime으로 전송 계층을 대체한다.
- [IOThreadRuntimeWorkers.cpp:159–189](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/Runtime/IOThreadRuntimeWorkers.cpp#L159-L189): 현재 이식판 StartWorker가 IOThread2(callback)을 실행하는 고정 StackThread를 생성한다. CThreadManager에 native handle을 등록한다.
- [ThreadManager.h:12–76](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/ThreadManager.h#L12-L76): L2Server 논리 ID 0~15 IO, 16 CAPTCHA, 17 GOODS_INVENTORY, 18 RELAY, 19 RAID, 20 PREMIUM, 21 L2COMM, 22 PETITION, 23 GIP, 24 LOG, 25 PERFMON, 26 MAIN, 27 LISTEN, 28 CHANNEL_CHATTING, 29 CONNECT, 30 ACCOUNT, 31 OLYMPIAD. MAX=32는 ID/저장소 용량이지 항상 32개 실행 중이라는 의미가 아니다.
- [L2Server.cpp:2265–2275](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Server.cpp#L2265-L2275), [2353–2398](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Server.cpp#L2353-L2398), [2506–2509](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Server.cpp#L2506-L2509): CommCli, ConnectThreadManager, Petition, Premium, GoodsInventory, RaidClient, Captcha의 전용 생성 호출이 존재한다. 각 서비스 내부의 조건 및 실제 동시 활성 수는 추가 검증 대상이다.
- [ConnectThreadManager.cpp:21–60,77–86](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/ConnectThreadManager.cpp#L21-L86): 외부 Relay/Account 재접속을 수행하고 5초 sleep하는 CONNECT 전용 스레드. 일반 게임 타이머와 구분된다.
- [L2Server.cpp:812–854](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Server.cpp#L812-L854): PerfMonThreadCommon은 GetPerfMon과 설정 간격 대기를 반복한다.
- [IOC.cpp:91–108](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOC.cpp#L91-L108): 동일 공개 버전의 CacheD는 min(논리 CPU,16), L2NPC는 min(논리 CPU/2,8). 이전 분석한 다른 L2Server 바이너리의 최대 8개 수치를 이 버전에 혼용하지 않는다.

## 3. IO 완료 및 반복 작업 실행 순서

[IOCL2Server.cpp:924–987](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOCL2Server.cpp#L924-L987), 원본 주소 주석 IOThreadNextSyncTime 0x007cc748, IOThread_common 0x007cce44.

    IOThread_common 반복
      현재 시각 > nextSyncTime:
        IOThreadNextSyncTime()
          CIOObject::TimerDispatch()
          IOThreadNextSyncTimeCallback()
        nextSyncTime = 현재 시각 + 50ms
      남은 시간만큼 IO completion 대기
      완료 도착: object->OnIOCallback(...)
      timeout: 다음 반복

[L2Server.cpp:1288–1305](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Server.cpp#L1288-L1305): 동기화 callback은 월드 맵 Read, IO watchdog heartbeat, smart-ID garbage collection을 수행한다. 타이머와 월드 동기화는 기존 IO 실행 루프 안에 있다.

중요: IO 워커만 타이머를 실행하는 것이 아니다. [IOCL2Server.cpp:859–920](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOCL2Server.cpp#L859-L920)의 WaitThread도 watchdog 확인 → TimerDispatch → 등록 핸들 대기(통상 최대 100ms) → OnWaitCallback을 수행한다. [L2Server.cpp:2839–2841](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Server.cpp#L2839-L2841)에서 LISTEN startup thread가 직접 WaitThread에 진입한다. 별도 범용 scheduler pool은 아니다.

[L2NPC IOC.cpp:535–605](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOC.cpp#L535-L605)에서도 같은 50ms IO 동기화 경로를 확인했다.

## 4. CThreadLocalTimer 실제 자료구조 및 콜백

[ThreadSafeLibrary.h:2116–2199](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/ThreadSafeLibrary.h#L2116-L2199), [ThreadSafeLibrary.cpp:86–251](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/ThreadSafeLibrary.cpp#L86-L251).

- 50ms slice, 200 슬롯, 10초 wheel 범위. 논리 스레드별 cursor/time, 예약 슬롯 배열, 장기 min-priority queue.
- CSlot 하나에 객체 포인터, int id, int64 uid의 평행 배열 1000개. 초과 시 공용 슬롯 풀에서 새 슬롯을 얻어 연결. 6869 슬롯 풀과 32×200 예약 슬롯 구조. 이 고정 메모리 규모를 Java로 그대로 복제할 필요는 없다.
- [IOCL2Server.cpp:780–828](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOCL2Server.cpp#L780-L828): AddTimer → AddTimer2 → 80ms 미만 clamp → Timer 참조 확보 → 절대 due tick → SetInTimer(true) → g_ThreadLocalTimer.AddTimer.
- AddTimer는 대상 객체 고정 소유자가 아니라 호출 시점의 TLS thread ID로 wheel을 선택한다. 10초 미만은 슬롯, 그 이상은 장기 큐에 삽입. Dispatch에서 만료 슬롯 처리 후 장기 큐를 wheel 범위로 이동한다.
- CSlot::Dispatch: LISTEN ID 27은 OnTimerCallback 직접 실행. 그 외는 대부분 SafeIOCallback, 이전 hop count가 100일 때 OnTimerCallback 직접 호출 후 카운터 초기화. [SafeIOCallback:773–778](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOCL2Server.cpp#L773-L778)은 object->OnIOCallback을 동기 호출한다. 이 경로에 별도 범용 풀 제출은 없다. OnTimerCallback/OnIOCallback 전체 가상 함수 재정의의 수명 경로는 이번에 전수 확인하지 않았다.
- [IOCL2Server.cpp:832–850](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOCL2Server.cpp#L832-L850): CancelTimersForOwner는 이식판 embedded shutdown 추가 기능이라고 명시. RKern의 로그아웃 취소, generation, 재접속 검증은 유지해야 한다.
- [IOC.cpp:1010–1064](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOC.cpp#L1010-L1064): L2NPC/CacheD용 공유 구현은 현재 TLS timer priority queue에 등록하고 DispatchCurrentTimerQueueAt을 사용한다. L2Server 전용 wheel과 자료구조가 동일하다고 단정하면 안 된다.

## 5. 일반 반복 작업 실제 사례

| OFF 클래스/함수 | 최초 등록 및 반복 정책 |
|---|---|
| [CAnnounceTimer](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/AnnounceTimer.cpp#L180-L188) | 1초, TimerExpired에서 AddTimer 재등록 |
| [CReservationAnnounceTimer](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/ReservationAnnounceTimer.cpp#L126-L164) | 60초, GuardAddTimer로 재등록 |
| [CReservationNPCSpawnTimer](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/ReservationNPCSpawnTimer.cpp#L401-L409) | 60초, 예약 처리 후 AddTimer |
| [CRespawnTimer](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2NPC/src/RespawnTimer.cpp#L19-L85) | 25초 체크, 필요 시 재등록; 절대 스폰 시각 검사 |
| [CReportTimer](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Server.cpp#L251-L256) | 메모리/인스턴스 보고, 1시간 재등록 |
| [CIODeadlockChecker](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/Shared/src/IOCL2Server.cpp#L200-L205) | 5초 재등록 |
| [CL2Time](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Time.cpp#L398-L594) | timer ID별 서로 다른 주기와 동작 |

CL2Time은 특히 RKern의 여러 시간 컨트롤러 통합에 적합한 참조다. [Init:94–114](https://github.com/luannbr/Open_LU4_L2OFF/blob/main/L2Server/src/L2Time.cpp#L94-L114)에서 여러 ID를 최초 등록하고 하나의 TimerExpired switch로 분기한다.

- ID 0 게임 시각/낮밤/문·지역/방송 갱신 10초.
- ID 2 세금/수입 60초; ID 3 인증 재연결 검사 60초; ID 4 투표일 갱신 60초; ID 6 NPC 수 통계 60초.
- ID 7 CacheD ping, ID 8 NPC ping은 설정 주기.
- ID 9/15 패킷 통계 조건부 60초, ID 10 드랍 리셋, ID 11 혈맹 가입 일일 정보 60초.
- ID 12 혈맹 문장 요청 큐 1초, ID 14 동적 영역 5초, ID 16 월드 채팅 송신 1초, ID 17 외부 서비스 ping 30초, ID 18 UI 이벤트 카운트 저장 요청 1시간, ID 19 keepalive 성공 시 30초, ID 20 파티 매칭 정보 60초. ID 5는 즉시 반환, ID 13은 별도 함수가 재예약하는 형태.
- 재등록 시점도 분기마다 다르다. ID 16/18은 실행 전에 재예약하고, 다수 다른 ID는 실행 후 재예약한다. RKern fixed-rate/fixed-delay/one-shot 의미를 그대로 보존해야 한다. OFF의 게임 주기를 리니지1에 강제로 적용하지 않는다.

## 6. RKern 실제 대상 코드 대조

기준 브랜치 `rkern/nc-user-timer-integration-20261126`. 실제 ThreadPool.java 445줄과 CIO/컨트롤러 파일을 읽어 확인했다.

[ThreadPool.java:73–118](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/ThreadPool.java#L73-L118): 일반 BoundedScheduledExecutor, 고우선순위 BoundedScheduledExecutor, bounded LinkedBlockingQueue를 사용하는 instant ThreadPoolExecutor 세 개를 만든다. 일반 예약 및 instant worker는 prestart. [183–280](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/ThreadPool.java#L183-L280)에서 execute(Runnable), 별도 Thread 관리, scheduleBlocking(예약 실행 후 bounded virtual thread)도 제공한다. 따라서 단순 예약 메서드 치환만으로 ThreadPool 삭제는 불가능하다.

| 실제 남아 있는 파일 | 확인된 사용 | 이관 방안 |
|---|---|---|
| [RealTimeClock](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/model/gametime/RealTimeClock.java#L25-L58) | fixed-delay Future | 기존 server timer, 리스너 비용 검사 |
| [L1GameTimeClock](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/model/gametime/L1GameTimeClock.java#L83-L105) | fixed-rate Future | CL2Time 방식의 기존 CIO timer, 게임 시간 규칙 유지 |
| [AuraController](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/Controller/AuraController.java#L49-L90) | 예약 풀 → instant pool, coalescing | 기존 캐릭터/AOI 소유 구조, 분할 실행 및 epoch 유지 |
| [ShipTimeController](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/Controller/ShipTimeController.java#L50-L88) | 예약 풀 → instant pool | 서버 타이머 및 월드 소유 실행, 재시작 guard 유지 |
| [BossController](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/Controller/BossController.java#L118-L166) | 장기 one-shot 및 Future 추적 | 기존 NpcMaker/NpcServer와 타이머, spawn/reload version 유지 |
| [GameSoundController](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/Controller/GameSoundController.java#L22-L60) / [GameEffectActionController](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/Controller/GameEffectActionController.java#L22-L60) | instant → delay → instant 반복 | 기존 객체 소유 timer, 횟수와 취소 의미 보존 |
| [CIO UserTimerTask](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/model/network/thread/CIOThreadCallback.java#L1032-L1105) | autosave가 instant pool에 saveWork 제출 | 저장 경로 실사 후 CacheD 등 기존 저장 소유부로 이관; CIO에서 직접 블로킹 금지 |
| [GameServer](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/GameServer.java#L567-L580) | clock stop 후 ThreadPool 종료 | 생산자 stop → 수락된 작업 drain → 저장 계층 종료 순서 유지 |

이미 [CIOThreadCallback.scheduleServerTimer:499–504](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/model/network/thread/CIOThreadCallback.java#L499-L504)는 shard 0의 기존 CThreadLocalTimer를 사용한다. [CThreadLocalTimer.java](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/nc-user-timer-integration-20261126/rkern/src/l1j/server/server/model/network/thread/CThreadLocalTimer.java#L19-L45)와 CIOThreadCallback의 캐릭터, 장비, 독, NPC, 아이템, 스폰 timer 경로가 이미 존재한다. 별도 wheel/dispatcher/worker 추가 금지. 무거운 전역 작업을 shard 0으로 무조건 몰아 넣지 않는다.

## 7. ThreadPool.java 제거 작업 순서

1. 전체 production Java 호출부 인벤토리: execute(Runnable/Thread), schedule*, scheduleBlocking, Future, shutdown, ThreadConfig. 이번 조사에서는 주요 파일을 확인했으며 전수 참조 집계는 아직 하지 않았다.
2. 일반/고우선 예약 풀부터 제거: 기존 server/character/NPC wheel로 반복·지연 작업 이관. NC처럼 타이머 ID로 책임을 묶되 기존 클래스를 무조건 통합하지 않는다. 동일 epoch에서 한 번만 예약, 취소 후 재실행 금지, 실패 시 재등록/경고 정책 명시.
3. instant pool의 게임 상태 작업은 기존 owner FIFO 또는 해당 소유자의 직접 실행으로 이관. AOI 작업량 상한과 타이머 지연 확인. 기존 FIFO 우회나 중복 직렬화 금지.
4. 저장/블로킹 작업은 CacheD 등 기존 실제 소유 실행 경로로 이관 가능한지 먼저 확인. 동기 SQL, 파일, 외부 I/O가 남으면 전용 경로 책임을 먼저 해결한다. scheduleBlocking과 dedicated Thread는 각각 수명과 취소를 보존하고 범용 ThreadPool에 의존하지 않도록 한다. 신규 워커를 임의로 추가하지 않는다.
5. GameServer 시작/종료, ThreadConfig 및 모든 import/호출 참조 정리 후 ThreadPool.java 삭제. 기능별로 검증 후 다음 단계 진행. 기존 네트워크 코어 및 DB 거래 확정성은 변경하지 않는다.

필수 검증: 동일 타이머 중복 실행, reload/재접속/사망/객체 재사용, fixed rate 대 fixed delay, 예외 후 재등록, 큐 포화 거부, 종료 drain, 저장 중 ACK/재접속 및 장애 복구, 5,000명 부하에서 CIO p99 지연 및 timer backlog. Java 21 전체 타입 컴파일 및 실서버 검증 전 완료 선언 금지.

## 8. 확인한 소스 버전 및 한계

OFF blob SHA:
- IOCL2Server.cpp 3dc6bc4d727aced30e585c7ef36cdc38f69b5a3f
- ThreadSafeLibrary.cpp 9cd45354ec6efb0ab9ed0af9b4bf43ea5d029d41
- ThreadSafeLibrary.h c90c8bb952327154ce4221574ee46e116e801478
- L2Server.cpp d4d8674a377e90575cb387906eb5e4ec7a60ebdf
- IOC.cpp 9c2c41f8c6ba58db068e2eb57ead85afeb30b062
- L2Time.cpp a2e2ffe64bcca3c6942b6f3ce2480f8c266308dd
- ThreadManager.h 888ccd09fce598970a391c215a725df43286f684
- ConnectThreadManager.cpp 336510897dce3766b269a20156ab28d6d3f8e011

RKern src tree 0e2ed1ef2b093bc247415839af517fd8c4428df1; ThreadPool.java blob 2bbeda5f025e30a004b306071fbd2c9f4cb42ec6; CIOThreadCallback.java blob f3fe9964bf4ceffe4f251131ecefa82b0c7843cb; CThreadLocalTimer.java blob 67da93beddf7affbd3d178ceb033e94cfe42d91d.

이번 커밋은 문서 전체 교체만 수행한다. Java 수정, 컴파일, 인게임 테스트, 전체 호출부 감사, AuthD/LogD 전용 경로 전수분석은 수행하지 않았다. 기존 스케줄러 문서와 충돌 시 이 직접 소스 대조의 L2Server/CacheD/L2NPC 관련 확인 결과를 우선한다.
