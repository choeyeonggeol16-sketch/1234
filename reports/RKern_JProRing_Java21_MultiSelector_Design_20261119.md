# RKern JProRing 설계 보고서 — Java 21 멀티 Selector / Emulated Proactor

작성일: 2026-11-19  
상태: 설계 제안 및 최신 SRC 정적 검토. 구현·컴파일·실측 없음.  
목표: Windows TCP 리니지 게임 서버, 단일 물리 서버 5,000명, Netty NIO 대비 저지연·CPU 효율·메모리 효율을 목표로 자체 전송 엔진 설계.  
JProRing은 이번 설계의 프로젝트 명칭이다. 실제 커널 io_uring이나 IOCP completion API를 구현한다는 뜻은 아니다.

## 0. 결론

리브만의 Emulated Proactor, NC CUserSocket 실행 정책, Asio의 Strand/continuation/allocator, Netty의 고정 EventLoop 및 gathering write, io_uring의 재사용 슬롯·배치 처리 장점을 현재 RKern의 CIO에 결합한다.

**핵심은 멀티 Selector를 하나 더 붙이는 것이 아니라, 기존 CIO 소유 스레드가 자신의 SocketChannel과 게임 실행권을 함께 소유하도록 하여 전달·중복 직렬화·FFM 경계를 없애는 것이다.** 단, 기존 실행 스레드 전제와 외부 게임 상태 변경 정책을 먼저 감사한다. 새로운 스레드·큐·클래스를 무조건 추가하지 않는다. 기존 게임 정책, DB/CacheD, NpcServer, 방송의 수신자 결정은 변경하지 않는다.

Windows에서는 표준 JDK 21 Selector의 WEPoll provider, Linux에서는 JDK epoll Selector를 사용한다. 애플리케이션 차원에서 JNI/FFM/DLL/NIO.2를 사용하지 않는 순수 Java 전송 계층을 목표로 한다. JDK 자체의 native 구현까지 없어진다는 의미는 아니다. Windows WEPoll의 readiness 통지와 IOCP의 실제 바이트 완료는 구분한다.

---

## 1. 현재 RKern의 실제 구현 기준

Library의 최신 발견 전체 SRC:
`RKern_NPC_Action_N8_Inn_Rental_Return_Receipt_FULL_SRC_20261119.zip`
- 크기 21,875,224 bytes, ZIP 항목 3,319개
- SHA-256: `4fa8428b857f24a1c38d8ae2ec55a14ab84b8e512a239a0960274646041124be`
- 소스만 읽었다. 기존 GitHub 보고서보다 최신 작업본으로 선택했다. 운영 서버 배포 버전과 동일한지는 별도 확인 대상이다.

Java 경로 접두사: `src/l1j/server/server/model/network/`.

| 파일 및 위치 | 확인된 현재 구조 | 전환 시 판단 |
|---|---|---|
| native/CTransport.cpp:44,1146~1156 | GetQueuedCompletionStatusEx, batch 128 | readiness batch로 대체. 완료 이벤트와 동일시 금지 |
| native/CTransport.cpp:382,395 | WSARecv/WSASend | SocketChannel.read/write로 대체 |
| native/CTransport.cpp:551~567,592,616 | NativeSendRequest 신규 할당, 일반 송신 vector/memcpy, sharedBlock 경로 별도 | Java owner 슬롯/버퍼 소유권 인계로 최적화 |
| core/config/CIOConfig.java | CTransport.dll 필수, ABI/hash 검사 | Selector용 전송 설정으로 분리 |
| core/server/Server.java:52~60 | Windows Native 전용 시작 경로 | admission/등록/정리 정책 보존, 전송 생성부 교체 |
| core/transport/CIOServer.java:238~257,447~454 | FFM upcall, native plaintext packet 전달 | Java owner 내부 호출 |
| core/transport/CSocket.java | native read/write, packet release, task/deadline | 동일 API 계약을 Java channel 상태로 제공 |
| core/transport/CIOPacketCodec.java | CIOPacketCodec.dll, per-connection native codec | Java codec 동등성 검증 후 대체 |
| core/connection/CUserSocket.java:56,2978~3043 | 세션 패킷은 affinity CIO, 일반 인게임 패킷은 Native IO 역할, 단일 plaintext lease/sequence | owner 직렬화로 통합하되 role/순서/lease 계약 보존 |
| core/connection/CUserSocket.java:352~364,3370 전후 | 512 슬롯, 504 제한, long 배열 2개와 CIOBufferEx[] 1개 | 중복 SEND serializer 제거 후보, 포화 정책 유지 |
| core/connection/CUserSocket.java:3217,3329 | Java onReadFrame, rolling cipher, opcode 배열, SEND burst | 재사용 후보, byte-level 동등성과 실행 역할 검증 필요 |
| thread/CIOThreadCallback.java:369,1185~1189,1936~1998 | 세션 packetIngress ArrayBlockingQueue(8192 기본), MAX_PACKET_BURST=64, 50ms wheel tick, park/unpark | 동일 owner 직접 실행, 기존 ingress 경계 재구성, selector wakeup 통합 |
| thread/CIOThreadCallback.java:620~650,2771~2805 | PC canonical owner=affinityShard, NPC/기타는 기존 owner hash | 소유권 변경 금지 |
| core/connection/CIOBufferPool.java:15~19 | 4KiB RECV, 16KiB SEND, 64KiB large, bounded lazy direct pool | 우선 재사용, 공유 read scratch는 이후 A/B |
| core/broadcast/CAsyncSendConstBuffer.java:173~214 | native handle, read-only wire, retain/release | backing만 Java pool로 변경, 송신 계약 유지 |
| core/broadcast/CSharedBroadcast.java | 기존 방송 수신자 정책, NativeWireWriter | 수신자·게임 동작 유지, writer/source backing 적응 |

현재 경로:

```text
IOCP completion batch -> CTransport / native packet codec
 -> FFM upcall CIOServer.onUserPacket -> CSocket.nativePacket
 -> CUserSocket.transportNativePacket -> dispatchNativePacket
    일반: native IO 역할에서 executeNativePacket
    세션: CIOThreadCallback.enqueuePacketImmediate
 -> onNativePlaintext -> CPacketHandler.HANDLERS[opcode]
 -> SEND burst -> Java serializer -> FFM -> native serializer -> WSASend
```

현재 구조는 이미 상당한 NC식 최적화를 수행한다. 새 구조가 무조건 이긴다는 가정은 금지한다. 특히 IOCP batch의 장점을 잃는 비용과 FFM/중복 전달 제거 이익을 실측한다. Native header 일부(execution_serializer.hpp, timer_wheel.hpp 등)는 이 ZIP에 없어 정책 전체를 검증할 수 없으며, NC 원본과 동일하다고 주장하지 않는다.

## 2. 원본 기술별 채택/제외

| 출처 | 실제 기법 | JProRing 적용 | 가져오지 않을 것 |
|---|---|---|---|
| Libman TProactor/JProactor | Reactor 위 완료 dispatch, cached allocator, intrusive queue, lock 밖 callback | 준비 통지를 owner 내부 I/O 수행/결과 dispatch로 변환 | shared Selector + Leader/Follower mutex 및 스레드 인계 |
| NC L2Server | IOCP worker -> OnRead -> opcode 직접 호출 -> 다음 WSARecv, SEND/WRITE serializer, 512 ticket, 16KiB 뒤쪽 병합 | 한 연결의 프레임 순서, cipher, 현재 실행권, 재진입·송신 수명 계약 | 실제 IOCP 동작을 그대로 readiness로 간주, 512슬롯 무조건 복제 |
| Boost.Asio | private_op_queue, Strand waiting/ready, reentrant direct dispatch, operation memory recycle 전에 콜백 데이터 포획, fast/cold path, speculative I/O | owner 로컬 continuation, 외부 제출만 공유, reusable slot, 분기 최소화 | 범용 Executor/handler 래퍼, 스레드 가변 소유권 |
| Netty NIO/epoll | channel 고정 EventLoop, task/wakeup, write spin, writev, backpressure | 고정 소유권, wakeup 합치기, write budget, gathering write | Pipeline 범용 객체 계층, native epoll 의존 |
| io_uring | submission/completion ring, 배치, slot sequence, 큐 포화 처리 | Java 사용자 공간 bounded MPSC/ready array, flush coalescing | kernel SQ/CQ·multishot·provided buffers 성능을 모방했다고 주장 |
| Getty | event loop 수신 버퍼 재사용 | 부분 프레임 lease와 결합하는 선택적 scratch | selectedKeys.toArray(new ...), 외부 OP_WRITE 변경 경쟁 |
| Grizzly | Selector 위 async read/write record, direct I/O, aggregate, SameThread | 즉시 read/write 및 미완료 상태 처리 | 연결당 CLQ/범용 FilterChain |

NC 비교 기준: 2026-10-23 L2Server(10).exe 정적 역분석 기록, SHA-256 9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588. 0x45C7AC serializer add/drain, 0x545A18 SEND 등록, 0x545E38 WRITE 완료, 0x5456D0 RECV 재등록, 0xA0372C OnRead. NC의 사실과 RKern 소유권 적응 설계를 구분한다.

## 3. 목표 전체 구조

```text
                  ServerSocketChannel / ACCEPT
                            |
                 기존 affinity 배정 / admission
             +--------------+--------------+
             |              |              |
         CIO Owner 0    CIO Owner 1    CIO Owner N-1
         Selector 0     Selector 1     Selector N-1
         ready arrays   ready arrays   ready arrays
         local SEND     local SEND     local SEND
         ingress ring   ingress ring   ingress ring
         Timing Wheel   Timing Wheel   Timing Wheel
             |              |              |
         sessions       sessions       sessions
             +--------------+--------------+
                  기존 World / NPC / CacheD
```

- 기존 네트워크 ingress shard mapping을 기준으로 network-enabled CIO owner만 Selector를 갖는다. 전체 CIO shard 수와 networkShardCount가 다를 수 있으므로 강제 동일화하지 않는다.
- 2/4/8 owner를 실측 후보로 삼되 운영 기본값은 목표 장비에서 검증 후 결정. 접속자 1023이라는 수를 기준으로 동적 스레드를 만들지 않는다. WEPoll은 구형 Windows select의 1024 제한을 설계 근거로 삼지 않는다.
- 연결 lifetime 동안 affinity 고정. 초기에는 migration 금지. 접속 편중은 측정 후 신규 접속 배정만 조정한다.
- ACCEPT는 초기에는 기존 owner 하나의 accept key로 운영한다. 별도 acceptor thread는 입장 부하 실측에서 필요한 경우에만 검토. 채널은 nonblocking 등록 전 owner에 안전하게 전달하고 실패 시 close/등록 제한 반환 exactly once.
- NPC/DB/외부 작업은 기존 실행 권한을 보유하고 기존 경로로 owner에 요청한다. Selector는 소켓 소유자만 접근·변경. 외부 스레드는 interestOps, cipher, pending send cursor 직접 변경 금지.

### 3.1 실행 스레드 역할 변경의 안전 조건

현재 일반 인게임 패킷은 Native IO role, 세션 패킷은 CIO role이다. 새 설계는 둘을 동일 CIO 스레드에 배치하므로 이것 자체가 실행 정책 변경이다. 다음부터 감사한다.

- CUserSocket/CIOClient/CIOShardContext/CPacketHandler, Creature Controller, action routing, WORLD 등록·로그아웃·세션 stamp 검사에서 currentNativeOwner/currentShardIndex 전제와 synchronized/client gate를 추적.
- 동일 CIO에서 직접 실행하더라도 세션 명령의 FIFO, 선행 등록/로그아웃 명령, NPC/DB 완료의 기존 happens-before를 우회하지 않는다.
- foreign actor 공격·인벤토리·거래·CacheD ACK의 소유권은 절대 소켓 고정만으로 안전해지지 않는다. 기존 전달/원자성 보존.
- synchronous DB wait, blocking client monitor, 긴 작업이 Selector를 막는지 측정한다. 기존 비동기 경로를 사용하고 범위 외 문제는 분리 보고한다. 무단 worker 추가 금지.
- 역할 통합을 증명할 수 없다면 그 단계의 production 전환 중지. 기존 CIO session handoff를 보존하는 중간 transport prototype으로 먼저 검증한다. 두 개 전송 엔진을 운영 중 자동 전환하거나 연결을 섞지 않는다.

## 4. Selector 실행 루프 (의사코드)

```java
// 하나의 기존 network-enabled CIO owner
while (running) {
    drainExistingIngressOrUnifiedRing(commandBudget);
    advanceDueGameTimersWithinBudget();
    runDueNetworkDeadlinesWithinBudget();
    runLocalContinuations(continuationBudget);
    flushDirtySockets(writeBudget);

    if (localWorkRemains() || timerDue() || publishedIngress()) {
        selector.selectNow(readyCollector);
    } else {
        // prepare-to-sleep protocol + recheck; no lost wakeup.
        if (!prepareSleepAndRecheck()) continue;
        long waitMs = ceilPositiveMillis(nextDeadlineNanos() - nanoTime());
        if (waitMs <= 0) selector.selectNow(readyCollector);
        else selector.select(readyCollector, waitMs);
        finishSleep();
    }

    processReadyKeys(readBudget, writeBudget, acceptBudget);
    // collector stores keys/flags; no gameplay in select callback.
    // partial frame/local remaining work must be rescheduled without waiting for new readiness.
}
```

실제 구현에서 drain/timer/ready/write가 모두 공정하게 진행되도록 rotating cursor 및 budget을 둔다. deadline 초과는 metric으로 기록하고 남은 타이머 작업이 있으면 block하지 않는다. 기존 50ms 게임 wheel cadence는 보존하고 네트워크 deadline도 기존 wheel 또는 소유자 deadline 상태로 이식한다. 만료 시점 정밀도 및 기존 timer cancel 의미를 유지한다. timer backlog를 무제한 catch-up하여 I/O를 굶기지 않는다.

Java 21 Selector.select(Consumer) 및 selectNow(Consumer) 활용:
- 각 owner에서 Consumer를 한 번 생성. callback은 key attachment의 slot/generation과 readyOps만 기록.
- 같은 key가 여러 번 callback될 수 있으므로 pass epoch와 OR mask로 합친다.
- active socket 상한 + accept key에 맞춘 bounded ready storage, overflow로 이벤트를 버리지 않는다. 용량 초과는 invariant 실패로 진단하거나 검증된 안전 처리. generation/key validity 재확인.
- canceled key/close/reuse 처리. read/write 각각 검사하고 read 후 close된 key에서 write 금지.
- callback은 JDK Selector 내부 lock 구간이므로 게임 처리, write, cross-owner 호출 금지.
- select(0)은 무한 대기라는 점에 주의. work가 있을 때 반드시 selectNow.
- Windows WEPoll은 하나의 readiness backend이며 edge-triggered epoll과 세부 의미가 다르다. level-ready/partial I/O를 안전하게 처리한다.

## 5. Submission 및 NC Serializer의 통합

기존 Java 소켓 serializer는 512 슬롯이다. 현재 Native serializer와 Java serializer가 각각 필요했던 이유를 제거 후 다시 평가한다. **새 설계의 필수 큐는 외부 producer가 owner에게 보내는 bounded ingress뿐이다.** 일반 로컬 SEND/RECV는 공유 ring을 통과하지 않는다.

기존 CIO packetIngress와 외부 timer/action 경로를 감사한 뒤 소유자 단일 ingress로 통합 가능한 대상만 합친다. 여러 기존 command lane에 서로 다른 순서/우선순위 계약이 있으면 무조건 단일 FIFO로 합치지 않는다. 기존 Runnable/TimerOp을 위한 필요한 참조 슬롯은 유지하되 객체 래퍼를 새로 만들지 않는다.

외부 submission 구조 후보:
```text
long[] sequence
int[]  kind, sessionSlot, generation, size
long[] arg0, arg1
Object[] payload   // 이미 생성된 task 또는 buffer lease, 필요한 경우만
producerPosition (atomic, isolated cache line)
consumerPosition (owner only)
```

- bounded power-of-two MPSC; per-slot sequence를 사용한다. producer CAS로 ticket 예약 후 payload 기록, release로 READY 게시. consumer acquire로 확인하고 FIFO 순서만 실행한다.
- EMPTY/READY 두 값만으로 구현하지 않는다. 예약되었으나 아직 게시되지 않은 slot을 재사용하면 안 된다. wrap-around 및 producer stall/generation 검사 필수.
- 예약 성공 뒤 실패 시 tombstone을 게시해서 hole을 해소한다. consumer는 ready prefix만 처리하고 unpublished slot에서 무한 spin하지 않는다. 이전 ticket을 추월하지 않는다.
- full 시 opcode/command 중요도에 따라 기존 거부 또는 연결 종료/명확한 backpressure를 수행. 이미 ACK된 CacheD 결과, mandatory logout/cleanup 등은 드롭 금지. 제어 작업 용량 예약 또는 기존 guaranteed delivery 경로를 명시하고 포화/종료 중 exactly once 보장.
- admission 승인 전에 lease retain 완료, 게시 실패/tombstone/소비/close 중 한 곳에서만 반환. stale generation은 실행하지 않고 lease 정리.
- pooling 해제 후 콜백(Asio) 원칙은 entry의 필요한 값/참조를 로컬에 확보한 경우에만 적용. callback이 기존 buffer를 참조하면 완료까지 lease 유지.
- 동일 owner direct dispatch는 선행 순서 부채가 없고 재진입이 기존 규약상 허용되는 경우에만. 외부 send ticket이 먼저 예약된 경우 신규 local send의 추월 금지. 순서가 필요한 경우 로컬 continuation 또는 동일 ingress에서 기존 순서대로 실행. 깊은 재귀/콜백 연쇄는 owner 전용 bounded continuation으로 제한한다. 일반 패킷마다 새 completion queue를 거치지 않는다.

### 5.1 Wakeup

producer는 게시 완료 후 sleep/wake protocol에 진입. owner는 sleep 의사를 게시한 뒤 ring/dirty/deadline을 재검사하고, producer가 asleep/pending 상태를 봤을 때만 selector.wakeup()을 합쳐 호출한다. clear/recheck 순서에 대해 메모리 모델·실행 인터리빙 테스트를 작성한다. 기존 LockSupport.unpark만 호출하고 Selector가 잠들게 두는 구현 금지. 먼저 JDK wakeup을 단순·정확하게 연결하고 최적화는 lost-wakeup 테스트 후 적용한다.

## 6. ACCEPT / RECV / framing / opcode

1. 기존 admission(접속 제한, IP 정책/허용 목록, account identity)을 통과하고 owner에 register. handshake 및 crypto state 초기화 성공 전 OP_READ enable 정책을 명시한다.
2. SocketChannel.read(directBuffer) 반환값 >0만 framing 진전. 0은 no progress, -1은 EOF. 무한 read spin 금지.
3. 2바이트 LE 길이 헤더, 최소/최대 길이, 65535 wire 상한, 2바이트 empty frame flood handling, malformed 정책 보존. 여러 패킷의 coalesced read와 split header/body 수신 모두 지원.
4. 기존 4KiB RECV/64KiB large pool을 먼저 사용한다. partial frame만 보존하고 정상 완성 프레임은 같은 owner 스택에서 소비. 공유 scratch 1개/owner는 두 번째 최적화 후보: 미완성 tail 또는 지연 session task만 connection lease로 복사하며, handler가 반환 전 데이터를 소비한다는 계약 확인. direct buffer를 비동기 CacheD나 외부 task가 참조하면 독립 lease 필수.
5. 기존 Java Cryptor를 이용하되 Native codec과 wire differential test 전에는 production 대체 금지. 초기 plaintext handshake, rolling key 초기화 및 4바이트 prefix 처리, unknown/filtered opcode도 정확히 한 번 decrypt, 방향별 상태/갱신 순서, 암호화 전 프레임 길이를 검증한다. native encrypted broadcast와 direct path를 모두 비교한다.
6. CUserSocket.allowed, packet rate limit, NetworkSafeLoadManager, CPacketHandler.HANDLERS, 활성 actor/session stamp, login/logout/character select 정책과 현재 disconnect/error 동작을 보존.
7. handler 종료 후 다음 프레임 진행. 처리 중 세션 전환·SEND pressure·close가 발생하면 더 읽지 않는다. 기존 한 연결 한 packet lease 계약에 상응하는 동작을 유지. 한 read에 여러 프레임이 들어와도 byte/frame/time budget을 적용하며 읽어 둔 완성 프레임이 남으면 local ready continuation으로 다시 실행한다.
8. partial header/body deadline과 idle/login/write stall deadline은 긍정적인 진행에 근거해 갱신한다. 입력이 이미 버퍼에 존재하면 새 readiness를 기다리지 않고 resume한다. OP_READ backpressure와 resume 경합 방지.

## 7. SEND / Gathering Write / 방송

```text
owner-local packet -> 기존 WritablePacket serialize / shared plaintext retain
    -> owner ordered send admission -> rolling encryption (exactly once)
    -> owner pending chain / dirty socket
    -> SocketChannel.write (single/gather)
       all accepted: release used leases
       partial / zero: retain cursor, OP_WRITE as needed
       fatal: close + cancel + release
```

- 외부 SEND producer는 현재 시점의 직렬화 결과 또는 변경 불가 source lease를 제출한다. mutable WritablePacket을 owner 실행까지 그냥 보관하지 않는다. 현재 API의 즉시 serialize, failure return, quota reservation, mandatory/optional 구분을 유지.
- 앱 내 flush boundary는 현 CUserSocket SEND burst(중첩, finally flush, read pressure 시 즉시 flush) 정책을 따른다. 같은 owner에서도 기존 큐가 비어 있고 순서 부채가 없을 때만 fast path. 고부하에서 이미 모인 작은 패킷을 16KiB 정도로 병합하는 기존 정책 활용; 인위적인 배치 대기 금지.
- 준비된 ByteBuffer[]를 owner별로 재사용한다. 개별 channel의 pending lease와 position/limit은 write 호출 동안 owner가 독점한다. 실제 쓰인 byte 수를 바탕으로 앞에서부터 완료 lease 반환; 부분 송신은 cursor를 보존하고 다시 암호화하거나 처음부터 재전송하지 않는다.
- OP_WRITE는 실제 0/부분 쓰기로 막혀 대기해야 할 때만 등록. write-spin 예산을 소진했지만 진전이 있다면 dirty continuation에 남겨 공정하게 재시도한다. backlog 없음에서 해제. OP_READ/OP_WRITE bit 변경은 owner에서 직렬화.
- gathering write 호출 수, bytes/call, zero-write, partial-write, latency 계측. SocketChannel.write(ByteBuffer[])가 OS/JDK에서 제한 또는 복사를 할 수 있으므로 언제나 1 syscall/zero-copy라고 단정하지 않는다.
- byte quota, frame quota, low/high water mark, pool exhaustion, slow client disconnect, optional 방송 거부, mandatory 응답의 오류 계약 유지. 큐가 포화했을 때 무제한 메모리 할당 금지.
- CSharedBroadcast / CAsyncSendConstBuffer / CTLMFilter의 recipient selection 및 기존 방송 정책은 유지. encrypted rolling state는 세션마다 달라 공유 plaintext를 직접 여러 연결의 암호문으로 사용하지 않는다. shared source는 수신자마다 필요한 암호화 작업 전에 retain, source read-only; 별도의 mutable recipient send buffer에서 암호화. 참조 수명은 publish 이전 retain, fail/cancel/success에서 exactly once release. 마지막 참조에서만 pool 반환. 수신자별 cipher 작업을 건너뛰는 임의 최적화 금지.
- NativeWireWriter 이름 변경은 필요에 따라 별도 단계에서만. writer를 Java ByteBuffer에 연결해 기존 formatted/extended/move/action 등 호출부를 최대한 보존한다.

## 8. 메모리 / 데이터 지향

현재 512 슬롯 소켓별 배열의 요소 영역은 long[512] 2개=8192 B, 참조[512]는 compressed oops 가정 시 2048 B. 합계 10240 B/connection, 5000 연결 약 48.8 MiB(객체 헤더/정렬 제외). 이 수치는 추정이며 Java 실제 retained heap은 JOL/JFR로 측정한다.

목표는 이 배열을 전 소켓에 복제하는 것보다 owner별 bounded shared submission ring과 로컬 send state를 활용하는 것이다. 단, 모든 기존 ticket 정책을 보존할 수 있을 때만 해당 구조를 제거한다.
- 기존 CIOBufferPool/CIOSlotPool을 재활용하고 owner-local free list, cross-owner 반환만 공유 경로 사용.
- 소켓당 필요한 안정적인 세션 객체는 유지; byte counter, opcode, session generation 등을 primitive로 다룬다. 작업마다 new Runnable/record/ByteBuffer[]/CLQ node 방지.
- 예시 메모리 계획: 5000 x 4096 = 약 19.5 MiB의 RECV backing이 모두 할당된 경우. SEND 16KiB 슬롯은 최대 동시 pending량을 계측해 결정. owner ring은 capacity x 실제 stride x owner 수, direct reserved/committed memory를 별도 보고. shared scratch로 절약될 수 있는 양과 copy 비용을 비교.
- JVM 도구로 allocation rate, GC pause, retained heap, direct buffer 사용, RSS, CPU cache/locality 등을 확인. heap 사용량만 낮추고 native/direct memory가 늘어나는 결과를 숨기지 않는다. RAM 여유가 있어도 bounded 설계를 유지.
- Asio처럼 common path와 slow path 분리하되 Java JIT가 항상 inline한다고 가정하지 않는다. JFR/async-profiler 또는 JVM compilation 정보로 검증한다.

## 9. 연결 수명 / 종료 / Timer

상태: NEW -> REGISTERED -> ACTIVE -> DRAINING/CLOSING -> CLOSED -> RETIRED.
- slot id + generation/session stamp로 이전 연결의 delayed event/command/DB 결과/타이머 거부. 재사용은 최종 취소 및 lease 반환 뒤.
- graceful close: 새로운 game admission 차단, 이미 확정된 mandatory SEND drain, 제한 시간 도달 시 hard close. 기존 denial grace와 close-not-before, write progress deadline 유지. EOF, RST, exception, pool exhaustion, cancel, watchdog 경계에서 idempotent close.
- close는 owner에서 key.cancel + channel.close. 외부 close intent는 기존 guaranteed control path로 전달; queue full이어도 cleanup 유실 금지. closed key에서 남은 ready event 무시.
- CUserSocketRegistry, account registry, client cleanup, close notification, admitted counter 계약 보존. native account/MAC/per-IP 제한과 진단 API는 기능별 Java 대응을 구현하거나 사용 여부를 명시; 의미 없는 stub success로 대체 금지.
- 기존 CIO 게임 Timing Wheel(50ms) 유지. Native network deadline을 기존 owner timer로 이식: login, partial read, write stall, graceful close, identity validation. generation 및 cancellation을 확인한다.
- shutdown: ACCEPT stop -> new external admission stop -> accepted mandatory tasks/returns 드레인 또는 정책대로 취소 -> owner close/drain -> 반환 참조 0 확인 -> wakeup 및 join -> selector/pool 종료. owner가 자기 자신 join 금지. deadline 초과 시 오류/잔존 작업 진단, 성공 위장 금지.

## 10. 구현 단계 / 수정 경계

코드 수정은 이번 요청에 포함되지 않는다. 아래는 향후 작업 순서다.

| 단계 | 작업 | 통과 조건 |
|---|---|---|
| P0 | 최신 source 네트워크 호출 경계 맵, Native role/owner check 및 blocking path, crypto oracle, admission/account registry/MAC/diagnostics 감사 | 역할 통합 조건 및 기존 계약 문서, Windows baseline 확보 |
| P1 | 독립 Selector transport test harness: accept/register/read/write/close, bounded ingress, generation/wakeup | TCP fragmented/coalesced, partial, queue exhaustion, teardown |
| P2 | 기존 CIO owner loop와 Selector 통합; existing shard mapping, local/foreign scheduling, timer | owner thread/ordering/rollback regression. 추가 packet worker 없음 |
| P3 | CSocket/CIOServer/Server 대체 경계와 CUserSocket의 Java framing/codec 연결 | native/Java wire differential, 실제 로그인/세션 전환, 일반 packet |
| P4 | SEND serializer 통합, quota, dirty list, gather, pool reuse | SEND byte order and close/backpressure/partial write stress |
| P5 | shared broadcast backing/codec, timer, registry/diagnostics, full session lifecycle | 동일 수신자/내용/순서·refcount, all resources drained |
| P6 | tuning: read scratch, owner-local pool, wakeup coalescing, fast/cold, budgets | 각 변경 A/B; 낮은 지연과 CPU 개선 없으면 채택하지 않음 |
| P7 | Windows production candidate, full Java 21 type compile, real MySQL/CacheD, in-game 5000 soak | 아래 성능/정합성 gate 모두 충족 |

변경 우선 패키지: core/transport, core/connection, core/server, thread/CIOThreadCallback, network codec/config/diagnostics 및 방송 전송 backing. 기존 API 경계를 보존하고 새 클래스는 실제 책임상 꼭 필요한 경우만. Native 파일은 baseline 보존을 위해 초기 비교 단계에서 삭제하지 않는다. 운영 반영은 검증 후 단일 기본 경로로 전환하며 자동 fallback이나 사용자에게 여러 JVM 토글 변경 요구 금지. 게임/NPC/DB/아이템 로직의 문제는 발견 시 별도 보고하고 무단 수정 금지.

## 11. 검증 및 공정한 벤치마크

비교 대상 A: 현재 Native IOCP/FFM RKern(N8 baseline, Windows).  
B: JProRing(동일 JDK21, 동일 protocol/codec/handler/response semantics).  
C: Netty NIO (Windows 동일 JDK21, 동일 TCP 옵션, 물리 코어/스레드 예산).  
D: Linux 진단용 B vs Netty NIO 및 Netty native epoll, 별도 결과로 표기. Native Windows baseline을 Linux 수치로 대체하지 않는다.

기존 2026-10-30/31 Linux NIO.2 P2 Fusion ON/SingleLock OFF 기록은 참고: 1024/window1 266,232 req/s, CPU 7.418µs/req, P99 8.880ms; Netty epoll 307,288, 6.142µs, 6.881ms. 16/window256 9,305,921 vs 4,323,642 req/s. 이는 현재 Native IOCP/N8 측정값이 아니다. 단일 고처리량 숫자로 5000명 성능을 판단하지 않는다.

| 부하 | 목적 |
|---|---|
| 1 conn window1 | 최소 fast path, RTT |
| 16 conn window256 | 고처리량 배치 및 SEND 병합 |
| 1024 conn window1 | 저부하/높은 활성 연결 분산 |
| 5000 conn idle + active mix, burst | 소켓 수·memory·wakeups·tail latency |
| 64B/512B/4KiB, mixed game packet | 암호화, framing, gather 비용 |
| NPC/공성/방송 fanout, CacheD 로그인·거래, slow readers | 실제 게임 작업과 owner stall |
| connect/disconnect storm, malformed, wrap/reuse, cancel/close race | 안정성과 수명 |
| 20 concurrent producers, ring full, producer preemption | sequence publish, exact once, no lost wakeup |

동일 머신/클라이언트(가능하면 외부 부하발생기), 분리된 CPU 측정, 동일 TCP_NODELAY 및 buffer 설정을 기록하고 5회 이상 warmup/반복, 중앙값과 분산. Windows 2/4/8 owner 탐색, 최종 동일 CPU 예산으로 비교한다. raw transport와 full game protocol 각각 측정하고 codec/broadcast가 빠진 테스트를 운영 성능으로 홍보하지 않는다.

수집: req/s와 bytes/s, CPU µs/request(서버 프로세스 CPU time / 성공 요청), RTT p50/p95/p99/p99.9, server queue wait/handler/WRITE accepted latency 분리, arrival-to-handler latency, syscall/selector poll/wakeup, bytes/write, heap/direct/RSS, allocations/request, GC, owner stall, timer drift, drops/close/duplicate/ordering violations, pool high-water. open-loop offered load에서도 coordinated omission을 피하는 latency를 기록한다. 정확한 p99 측정 기준과 부하의 지속 시간을 명시한다.

채택 gate:
1. wire byte/state/packet order와 게임/트랜잭션 결과 동등, 데이터 누락·복제·leak·deadlock 없이 경계 테스트 및 장시간 5000 soak 통과.
2. 동일 offered load에서 현재 RKern/Netty NIO 대비 CPU/latency 우수 또는 명확한 목표 달성. 5000 게임 부하에서 내부 p99 <1ms는 희망 목표(네트워크 RTT와 구분)이며 확정 성능이 아니다.
3. 특정 고부하 처리량만 높고 1024/4KiB p99 또는 안정성 악화 시 실패. 기준과 허용 변동은 실제 baseline 데이터에 따라 문서화 후 판정.
4. 경우에 따라 IOCP native가 더 효율적일 수 있다. 그러면 Selector 전환 자체를 채택하지 않는다. 사용자에게 수치와 원인을 보고한다.

## 12. 원본 참고 자료

- Libman: [New Proactor implementation for POSIX](https://docs.huihoo.com/ace_tao/proactor.html), [JProactor source](https://sourceforge.net/p/jproactor/code/HEAD/tree/trunk/jproactor/src/au/com/terabit/jproactor/), [Comparing Two High-Performance I/O Design Patterns](https://www.artima.com/articles/comparing-two-high-performance-io-design-patterns).
- ACE: [WIN32_Proactor.cpp](https://github.com/DOCGroup/ACE_TAO/blob/master/ACE/ace/WIN32_Proactor.cpp), [WIN32_Asynch_IO.cpp](https://github.com/DOCGroup/ACE_TAO/blob/master/ACE/ace/WIN32_Asynch_IO.cpp). 공동 개발 코드이며 Libman 단독 구현으로 귀속하지 않는다.
- Asio develop 검토: [scheduler.ipp](https://github.com/boostorg/asio/blob/develop/include/boost/asio/detail/impl/scheduler.ipp), [strand_executor_service.ipp](https://github.com/boostorg/asio/blob/develop/include/boost/asio/detail/impl/strand_executor_service.ipp), [thread_info_base.hpp](https://github.com/boostorg/asio/blob/develop/include/boost/asio/detail/thread_info_base.hpp), [epoll_reactor.ipp](https://github.com/boostorg/asio/blob/develop/include/boost/asio/detail/impl/epoll_reactor.ipp), [io_uring_service.ipp](https://github.com/boostorg/asio/blob/develop/include/boost/asio/detail/impl/io_uring_service.ipp), [win_iocp_io_context.ipp](https://github.com/boostorg/asio/blob/develop/include/boost/asio/detail/impl/win_iocp_io_context.ipp).
- Netty: [NioEventLoop](https://github.com/netty/netty/blob/4.1/transport/src/main/java/io/netty/channel/nio/NioEventLoop.java), [NioSocketChannel](https://github.com/netty/netty/blob/4.1/transport/src/main/java/io/netty/channel/socket/nio/NioSocketChannel.java), [native epoll](https://github.com/netty/netty/tree/4.1/transport-classes-epoll).
- JDK21: [Selector API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/Selector.html), [SocketChannel API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SocketChannel.html), [WEPollSelectorImpl](https://github.com/openjdk/jdk21u/blob/master/src/java.base/windows/classes/sun/nio/ch/WEPollSelectorImpl.java).
- io_uring: [liburing](https://github.com/axboe/liburing). kernel queue와 Java user-space ring은 별개.
- 보조 사례: [Getty](https://github.com/gogym/getty), [Grizzly](https://github.com/eclipse-ee4j/glassfish-grizzly).
- 기존 RKern 저장소: [L2Server Network Comparison](../L2Server_NETWORK_COMPARISON.md), [RKern Network Core Review](../RKern_NETWORK_CORE_REVIEW.md), [Netty epoll rebench](../RKern_NETTY_EPOLL_REBENCH_20261028.md).

외부 프로젝트 소스는 설계 참고이며 이 문서에는 원본 코드를 복제하지 않았다. 채택 구현 시 라이선스 및 귀속을 검토한다. JProRing은 현재 설계 이름이며 아직 구현된 제품이 아니다.

**최종 원칙: NC 실행 순서 + Libman 완료 모델 + Asio 실행권/수명 최적화 + Netty 고정 owner + io_uring의 데이터 지향 배치. 전송 계층에서 줄일 것이 있는지 실측하고, 기존 게임 상태의 확정성을 손상시키지 않는다.**

---

## 13. 추가 검증: 동일 Owner 장시간 점유 방지 — 실제 공개 구현 비교 (2026-11-19)

범위 확정: CacheD/DB/NpcServer의 실행 구조는 그대로 둔다. JProRing의 network-enabled CIO owner에서 네트워크와 게임 실행을 조합하는 문제에만 집중한다. 방송의 수신자 결정, 게임 규칙, 트랜잭션 경계는 변경하지 않는다. 아래는 원본 공개 코드에서 확인한 사실과 RKern 적용 제안을 구분한 추가 설계 기록이다. 아직 RKern 코드 변경이나 성능 실측은 하지 않았다.

### 13.1 직접 확인한 공개 소스와 기술

소스 위치는 조회 당시 branch 기준이며 blob SHA는 확인한 소스의 식별자다. 이후 branch HEAD가 바뀔 수 있다.

| 프로젝트 | 실제 코드/동작 | 한계 및 JProRing 적용 |
|---|---|---|
| Seastar | [maybe_yield.hh](https://github.com/scylladb/seastar/blob/master/include/seastar/coroutine/maybe_yield.hh) L32~40, blob 83db7ad8a1dbbd7cff83f944bd2fdc346e441f6d. await_ready는 need_preempt를 확인하고, 필요하면 await_suspend에서 coroutine promise를 schedule한다. [공식 튜토리얼](https://docs.seastar.io/master/split/4.html)은 기본 task quota 500µs와 explicit preemption point를 설명한다. | 강제 선점이 아니다. yield 지점 없는 CPU 연산은 reactor를 굶길 수 있다. 500µs는 Seastar 설정이지 RKern 측정 결과가 아니다. Java에서는 안전 지점에서 primitive cursor를 저장하고 동일 owner에서 재개하는 상태 기계로 적응. |
| Netty 4.1 | [NioEventLoop.java](https://github.com/netty/netty/blob/4.1/transport/src/main/java/io/netty/channel/nio/NioEventLoop.java) L135,548~569, blob 399cddcda3071f98e58c89b57319d53247914b13. 기본 ioRatio=50, 측정한 ioTime에 (100-ioRatio)/ioRatio를 곱해 task 예산을 계산. [SingleThreadEventExecutor.java](https://github.com/netty/netty/blob/4.1/common/src/main/java/io/netty/util/concurrent/SingleThreadEventExecutor.java) L460~490, blob 03b49d0b213f6206a4fa298de27ab989d5854e73. deadline 확인은 64개 task 단위. | 개별 task를 중간에 끊지 않는다. 일반 게임 handler가 I/O 처리 안에서 동기 호출되면 task 예산만으로는 그 handler를 제어하지 못한다. I/O와 continuation 양쪽에 상한을 두고 실제 시간도 측정. ioRatio나 64를 그대로 채택하지 않는다. |
| Redis | [expire.c](https://github.com/redis/redis/blob/unstable/src/expire.c) L320~321,363~368,384~399,447~448,498~501, blob bf46862cfbce3c080cb005598b81f1d72641fa19. current_db 및 expires_cursor로 진행 상태 보존, fast/slow expire cycle별 시간 예산, 16회 iteration마다 경과 시간 검사 및 timelimit_exit 설정. [server.c](https://github.com/redis/redis/blob/unstable/src/server.c)의 beforeSleep에서 fast expire cycle 실행. | Redis가 모든 명령을 선점한다는 뜻이 아니다. 증분 순회 가능한 느슨한 작업에 적용. JProRing에서는 기존 게임 의미와 snapshot 규약이 보존되는 작업에만 cursor 재개 후보. CacheD를 Redis처럼 변경하지 않는다. |
| Aeron / Agrona | [MediaDriver.java](https://github.com/aeron-io/aeron/blob/master/aeron-driver/src/main/java/io/aeron/driver/MediaDriver.java) L251~266, blob 9e0970a4890f2142b98a3810ca9dd3896fe3bbe5. SHARED mode는 sender, receiver, nativeResourceAgent, conductor를 같은 AgentRunner의 composite에 넣는다. [CompositeAgent.java](https://github.com/aeron-io/agrona/blob/master/agrona/src/main/java/org/agrona/concurrent/CompositeAgent.java) L119~132, blob b186f80cf95a64df2ed7f9734b16518c4af6c0cd. 각 agent.doWork를 순회하고 workCount를 합산한다. [공식 Agent Concurrency Model](https://github.com/aeron-io/agrona/wiki/Agent-Concurrency-Model)은 doWork가 유한한 작업 후 반환하는 계약을 설명. | Composite 호출 자체에 강제 시간 제한이 있는 것은 아니다. 각 단계가 유한량만 수행해야 한다. JProRing owner의 RECV/ingress/timers/continuation/SEND를 bounded duty cycle로 구성. Aeron UDP transport나 busy-spin 기본값을 그대로 가져오지 않는다. |
| Libevent | [event.c](https://github.com/libevent/libevent/blob/master/event.c) L1692~1805,1826~1848, blob 861043d699d044a93cde0cf2b072d81b7aec8c64. event_process_active_single_queue에 max_to_process/endtime 전달, 처리 중 시간 상한 확인. [공식 설정 API](https://libevent.org/doc/event_8h.html) event_config_set_max_dispatch_interval에서 max_interval, max_callbacks, min_priority 적용을 설명. | callback이 끝난 경계의 제한이며 강제 선점 아님. 특정 우선순위는 제한 제외 가능. RKern에서는 긴급 close/control의 유실을 방지하되 무제한 우선순위 처리로 정상 I/O를 굶기지 않음. |
| libuv | [src/win/core.c](https://github.com/libuv/libuv/blob/v1.x/src/win/core.c) L730~735, blob 96a796106239fcacacc4966b898407267efc17b9. uv__poll 후 pending request 처리 반복을 최대 8회로 제한하여 starvation 완화. [Unix core.c](https://github.com/libuv/libuv/blob/v1.x/src/unix/core.c)도 같은 정책을 포함. | 8개 개별 콜백 제한이 아니라 pending drain pass 제한이다. callback 하나 또는 pass 하나의 최악 시간까지 보장하지 않는다. JProRing에서는 재귀 flush/continuation pass에 상한을 두되 처리량 및 시간도 따로 제어. |
| TrinityCore | [WorldSession.cpp](https://github.com/TrinityCore/TrinityCore/blob/master/src/server/game/Server/WorldSession.cpp) L366~371,498~506, blob 96455f150805e6b4b588c3198063aa7a59dd1391. Update에서 MAX_PROCESSED_PACKETS_IN_SAME_WORLDSESSION_UPDATE=100 선언. 실제 종료 비교는 increment 뒤 >100이므로 단순 정상 흐름에서는 101개까지 실행 가능. 나머지는 다음 Update에서 처리. | 별도 game/session update model이다. 동일 IO thread 방식의 성능 근거는 아님. 세션별 packet budget 및 남은 패킷의 재실행 정책만 참고. 상한 100을 복제하지 않는다. |
| Skynet | [skynet_server.c](https://github.com/cloudwu/skynet/blob/master/skynet-src/skynet_server.c) L293~346, blob 782abffacbafb9593f30a5645b2aeab51c3b5c5d. 첫 메시지 후 남은 queue length를 weight만큼 우측 shift해서 배치를 정하고 다른 global queue가 있으면 현재 queue를 되돌려 다음 서비스로 전환한다. | actor/service mailbox 공정성 참고. weight에 따라 상당한 배치를 처리할 수 있고 고정 wall-time 상한이 아니다. worker pool이므로 Core/owner 고정 실행과 같지 않다. JProRing에 별도 서비스 큐나 worker를 추가할 근거가 아니다. |

보조 검증:
- [Tokio coop/mod.rs](https://github.com/tokio-rs/tokio/blob/master/tokio/src/task/coop/mod.rs) L116,343, blob fccaebd9a6ebc0ddebeb38147d068167139f4395: cooperative budget 초기 128 및 poll_proceed. count 기반으로 작업 중 양보하는 참고 사례. Tokio executor나 future 생성은 채택하지 않는다. Rust task 재개와 고정 CIO owner 실행은 구분한다.
- [SPDK Event Framework](https://spdk.io/doc/event.html): 공식 문서상 CPU별 reactor, lockless message queue, poller와 이벤트 처리 교차 실행. storage framework 사례이며 게임 서버 성능 증거가 아니다.
- NC: 기존 L2Server 바이너리에서 OnRead -> opcode/user 메서드 -> recv rearm 확인. 긴 게임 로직에서 yield / quota / 별도 scheduling을 수행했다는 직접적인 증거는 확보되지 않았다. 임의로 NC 기술로 귀속하지 않는다.
- 위 기술들은 각각 같은 종류의 starvation 문제에 대한 해결책을 제공한다. 모든 시스템이 같은 실행 구조를 쓰거나 Libman에게 직접 영향을 받았다는 주장은 하지 않는다.

### 13.2 JProRing 실행 정책 (위 구현을 근거로 제안, 미구현)

채택 후보: NC 직접 실행 + Aeron bounded duty cycle + Netty/Libevent 시간 및 개수 예산 + Seastar/Redis 안전한 중단 지점과 cursor 재개. 기존 section 4,5,6,9의 실행 루프 및 순서 계약을 구체화한다.

1. 같은 CIO owner 내부에는 함수 호출만 남긴다. 별도의 game worker, task executor, per-packet queue를 추가하지 않는다. 단, 기존 외부 owner/CacheD/NpcServer 간 정상 전달은 유지한다.
2. loop마다 I/O-ready, ingress/control, expired timer, local continuation, SEND 각각이 실행 기회를 얻어야 한다. 큐의 끝까지 무제한 실행 금지. control burst도 제한하되 close/commit 결과 정리 등 mandatory 처리가 유실되지 않도록 기존 delivery 계약 보존.
3. owner 전체 phase budget + 소켓당 frame/byte budget + 안전하게 분할 가능한 개별 작업 budget을 분리한다. 한 phase의 남은 일을 이유로 다른 phase가 무한히 밀리지 않도록 round-robin cursor를 활용한다. 실제 처리 예산은 미확정.
4. 0 반환/nonready는 진전 없음을 뜻하고 즉시 반복하지 않는다. 다만 메모리에 읽어 둔 완성 프레임, due timer, dirty SEND, 게시된 ingress, runnable continuation이 남아 있으면 selector.selectNow를 사용한다. 읽기 예산 종료 후 새 readiness를 기다리지 않아야 한다.
5. 개별 짧은 opcode의 원자 실행을 우선한다. game handler 내부에 임의의 yield를 넣거나 selector를 재귀 호출하지 않는다. 루프 경계의 시간 예산은 이미 시작한 긴 단일 handler를 강제로 중단하지 못한다.
6. 실제 측정에서 긴 순회로 확인된 작업만 명시적인 안전 지점에서 단계화: owner-local persistent cursor/phase, 필요한 immutable inputs/generation/lease를 유지하고 return 후 기존 owner 로컬 작업 메커니즘으로 재개한다. 별도 OS 스레드로 보내지 않으며, Java Thread.yield/sleep/park를 cooperative continuation의 대용으로 사용하지 않는다. 게임 로직 전체를 coroutine이나 virtual thread로 변환하지 않는다.
7. 재개 시 대상 generation, session validity, 타겟/월드 상태 및 작업 의미에 필요한 version을 확인. 변경된 데이터에 대해 재시작·취소·snapshot 유지 중 기존 게임 동작에 맞는 정책을 사전에 선택한다. 진행률을 위해 atomic section을 억지로 쪼개지 않는다.
8. packet 순서를 보존하기 위해 이미 처리 중인 session의 뒤따르는 opcode가 continuation을 추월하지 않게 한다. 한 세션이 pending일 때 다른 세션 I/O는 계속 처리 가능하지만, 외부 명령과 lifecycle/close/CacheD callback ordering도 함께 검증한다. 다음 socket packet을 기다리면서 기존 완성 프레임을 무기한 묶어 두지 않는다.
9. 방송/공격/거래/재화/인벤토리 등은 단순 loop처럼 임의 분할하면 안 된다. 방송 수신자 순회 분할도 snapshot과 packet order/lease를 검증하기 전에는 구현하지 않는다. 이번 범위에서는 게임 동작 및 방송 정책을 변경하지 않는다. 안전한 중단 지점이 확인되지 않으면 원래 단일 실행을 유지하고 핫패스 최적화 또는 기존 전용 실행부 사용을 검토한다.
10. Timing Wheel은 미래 실행 시점 예약, cooperative continuation은 현재 준비된 긴 작업의 공정한 재개다. 별도의 스레드를 만들 필요는 없다. 즉시 재개할 일을 50ms game wheel에 넣어 인위적인 지연을 만들지 않는다. 기존 타이머 cadence와 취소 의미 보존.

실행 루프 의사코드 (개념, 새 클래스나 큐 추가 지시가 아님):

    while (running):
        runBoundedControlAndIngress()
        pollSelectorWhenNeededWithoutBlockingOnReadyWork()
        runBoundedReadyFramesRoundRobin()
        runBoundedDueTimers()
        resumeBoundedSafeContinuations()
        flushBoundedDirtySockets()
        if noRunnableWorkAndNoDueDeadline:
            prepareSleepRecheckAndSelect()
        # 위 phase 순서는 기존 순서 계약에 맞춰 검증 후 확정

Budget 튜닝은 64/128/256/500µs 등의 시간 후보와 batch/frame count 후보를 평가하되, 외부 프로젝트 숫자를 RKern production 기본값으로 채택하지 않는다. System.nanoTime 호출 비용을 고려해 count/time hybrid 확인을 A/B 비교하며 긴 작업 측정은 별도로 유지한다. 단일 atomic handler가 2ms라면 100µs phase budget을 설정해도 그 handler는 여전히 2ms 실행될 수 있다.

### 13.3 추가 검증 시나리오

- 동일 owner에 2ms 인위적 CPU 작업 + 다수 소켓, 기존 단일 실행 vs 안전한 분할 실행 비교. 동일 결과를 내는 독립 테스트 작업으로 먼저 측정하고 게임 원자 영역에 인위적 yield를 넣지 않는다.
- RECV flood / tiny frames / partial reads / slow SEND / 타이머 동시 만료 / 외부 제어 작업 burst를 각각 및 혼합으로 가해 phase starvation 확인.
- 수집: max/p99 owner turn duration, phase budget overrun, individual handler max, readiness-to-dispatch, timer lateness, local continuation age, pending frame age, per-session fairness, ingress queue wait, bytes per write, CPU/req, allocations, OS context switch. JVM/OS scheduler/GC 지연은 애플리케이션 명령 전달과 분리 측정.
- 무한 local continuation, endlessly ready socket, unpublished producer slot, mandatory close 및 ACK, generation 재사용, 순서 추월, source lease 조기 반환 여부 검증.
- 5000명 목표 부하에서 기존 RKern / Netty NIO와 동일 offered load 및 동일 game semantics 비교. budget 적용 전후의 p99/p99.9, 처리량, CPU 및 최대 단일 handler 시간을 비교한다. 지연 개선 대신 게임 결과/처리량이 허용 범위 밖으로 악화되면 채택하지 않는다.
- 신규 스레드/큐, CacheD 구조 변경, NPC 실행 구조 변경 없이 달성할 수 있는 개선을 우선한다.

검증 상태: 외부 생산 코드의 관련 구현과 공식 문서 확인 완료. 원본 전체 프로젝트의 실행 시험, 동일 하드웨어 비교, RKern의 구현이나 실측은 수행하지 않았다. 실제 적용 방식·budget·분할 가능한 게임 동작은 P0/P6에서 별도 증명해야 한다.
