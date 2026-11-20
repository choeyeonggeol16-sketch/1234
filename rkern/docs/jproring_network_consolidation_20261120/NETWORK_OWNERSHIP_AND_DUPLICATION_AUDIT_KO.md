# RKern JProRing 네트워크 중복 계층 및 동기화 통합 감사
2026-11-20 | 기준 브랜치: rkern/unified-jproring-game-20261120 | 기준 커밋: [8971c2074481c47b1436dd1ec1a66111b6d690f2](https://github.com/choeyeonggeol16-sketch/1234/commit/8971c2074481c47b1436dd1ec1a66111b6d690f2)

## 1. 목적·범위
CIOClient/CUserSocket 통합 이후 동일 TCP 연결의 중복 클래스, 중복 상태·큐·등록·버퍼·타임아웃 및 비동기 완료 어댑터에서 남은 동기화 비용을 조사한다. Java 21 멀티 Selector/JProRing, 기존 CIO 소유 샤드와 타이밍 휠, SEND 병합, 암복호화 및 FIFO, bounded capacity, 자원 수명 보존. 새 스레드·큐를 만드는 방향 금지. DB/NPC/상점/전투/PSS 및 게임 패킷 내용 변경 금지. 방송 항목은 별도 승인 후 수정.

정적 감사 범위: network 하위 8개 디렉터리(buffer/client/codec/core/packet/safe/thread/utill)의 Git tree 재귀 열거(모두 truncated=false), 주요 인프라 파일의 본문 및 관련 메서드 대조. 개별 CS/SC 게임 패킷 클래스는 목록만 확인; 외부 전체 SRC 호출부 전수 역참조 및 런타임 검증은 미실행. 이하 확인된 구조와 변경 후보를 구분. 수정 완료 또는 실측 개선을 주장하지 않는다.

## 2. 현 구조와 목표
현재 hosted game 경로:
    CIOThreadCallback.CIOThread (game owner + timing wheel)
      └─ CSelectorTransport.Owner (같은 물리 스레드)
          └─ Connection (SocketChannel, SelectionKey, readiness)
               └─ CSelectorRawIo (pending READ/WRITE)
                    ↔ CSocket (completion adapter)
                         ↔ CUserSocket (game session, framing, crypto, serializer, SEND)

별도로 CIOServer.register → Server.activateAccepted → CDummyServerCallback.CreateSocket에서 등록 및 제한을 관리한다.
목표:
    Server (listener + global admission/registry)
      └─ 기존 CIO owner (selector + timing wheel)
           └─ CUserSocket (단일 TCP attachment/session)
                ├─ channel/key/readiness + pending READ/WRITE
                ├─ ordered packet framing/crypto/SEND batching
                └─ lifecycle + bounded foreign-producer handoff
    CIOBufferPool (실제 공유 lease의 소유권 보존)

클래스 통합 자체보다 같은 상태의 소유자, 같은 실행 경계, 같은 publication을 한 번으로 만드는 것이 목적이다. 다른 샤드·NPC·작업 스레드의 SEND 및 종료 요청은 실제 경쟁이므로 동기화가 필요하다. 독립 Selector 테스트 API가 필요하다면 테스트 경로만 유지하되 production game 연결에 중복 객체를 생성하지 않는다. NC 클래스 명칭을 쓰더라도 비동기 IOCP 완료 모델을 Java readiness에서 그대로 모방할 필요는 없다.

## 3. 발견 사항 및 우선순위
| ID | 우선순위 | 대상 | 핵심 |
|---|---|---|---|
| N01 | P0 | CUserSocket + CSocket | 소켓 adapter monitor 및 중복 pending/close 상태 |
| N02 | P0 | Connection + CSelectorRawIo | raw pending state, 중복 callback 객체 |
| N03 | P0 | CUserSocket completion pump/serializer | 완료 전달 CAS, 512슬롯 serializer 축소 |
| N04 | P0 | CIOThread + Selector.Owner | 동일 스레드의 이중 명령 전달 경계 |
| N05 | P0 | CIOServer + CIPConnectionCounter | IP 제한 이중 등록 |
| N06 | P1 | Server + CIOServer + CDummyServerCallback | 접속 registry/count/factory 중복 |
| N07 | P1 | CUserSocketMap + CUserSocket + ReleaseTask | close/cleanup 상태 및 Atomic 객체 |
| N08 | P1 | Selector framed stack vs game raw stack | 별도 프레이밍/암호화/pool/send 구현 |
| N09 | P1 | CIOBufferEx + CIOFrame + CIOSlotPool | lease/handle metadata |
| N10 | P1 | Selector deadline + socket/login APIs | timeout 정책·예약 중복 후보 |
| N11 | P1 | session monitor + CIO replay | 동일 owner 실행 시 monitor 후보 |
| N12 | P2 | CAsyncSendConstBuffer + ExceptSelf | 제외 정책 중복 클래스, wrapper monitor |
| N13 | P2 | command/packet wrapper 및 scratch | 얇은 API·임시 저장소 정리 |
| N14 | P2 | CThreadLocalTimer + CSlot | 슬롯 객체 및 공유 pool 검토 |
| N15 | P2 | CIOConfig + CConnectionConfig + legacy APIs | 설정·상수 shim·미사용 클래스 |

### N01 — CUserSocket + CSocket (최우선)
근거: core/transport/CSocket.java 14–74,160–292; core/connection/CUserSocket.java 103–105,727–789,969–1004.
CSocket은 실제 SocketChannel이 아니라 Selector Connection에 대한 completion adapter이다. readOwner/readBuffer, writeOwner/writeBuffers/writeOffset/writeCount/gathering, singleWrite[] 및 open/closing/ioDrained를 별도로 보유한다. synchronized(this)로 READ/WRITE 예약, 완료, 종료를 처리. 연결마다 재사용 OwnerCommand 두 개, read/write/deadline callback 세 개가 생성된다. CUserSocket에도 _stateLock(ReentrantLock), _reading/_writing, completion CAS, close state가 있다. 게임 경로에서 readOwner/writeOwner는 같은 CUserSocket을 참조한다.
현재 흐름: CSocket.read/write → owner면 peer.readRaw/writeRaw 직접 arm, 아니면 peer.submit → RawIo 완료 → CSocket.completeJavaRead/Write → CUserSocket.transportRead/WriteCompleted.
권장: CUserSocket에 필수 identity/account API와 pending state를 흡수하고 CSocket 참조와 adapter monitor/콜백 제거. 실제 channel arm/complete는 CIO owner에서만 실행; foreign 요청은 검증된 기존 publication 경계로 전달. CSocket 제거만 하고 동일 락을 옮기지 말 것. I/O 중 lease 반환 금지, partial gather/zero write/cancel/synchronous error 정확히 한 번. 모든 호출부 참조 변경 후 클래스 삭제.

### N02 — Connection + CSelectorRawIo
근거: core/transport/selector/CSelectorTransport.java 231–272,298–375; CSelectorRawIo.java 1–97.
Connection은 SocketChannel/key/readyOps/deadline/close, RawIo는 read/write target 및 completion을 저장한다. RawIo 자체에는 락이 없으므로 이 통합만으로 락 감소를 주장하지 않는다. 중복 객체·콜백·target 제거 목적. 먼저 RawIo state를 Connection 또는 game socket attachment에 흡수하고, 최종적으로 game CUserSocket이 단일 connection state를 소유하도록 한다. 독립 framed transport 테스트 계약 및 SelectionKey 취소/interestOps, stall deadline, close reason 보존.

### N03 — completion CAS 및 512슬롯 serializer
근거: CUserSocket.java 305–395,727–895,1795–1854,2861–3077,3315–3589.
연결마다 long[512] callback, long[512] argument, CIOBufferEx[512] send argument 배열이 있다. payload만 8 KiB + reference 2 KiB(compressed oops 가정) = 10 KiB/연결, 5천 연결 약 48.8 MiB. 비압축 reference이면 약 58.6 MiB. 헤더/정렬 제외, 실측 또는 전체 절감 보장 아님.
READ_KICK/READ_EVENT/READ_ACTIVE, WRITE_EVENT, SOCKET_PENDING/TICKET/DRAINING/START_PENDING 등은 완료·foreign producer 경쟁을 보호한다. serializeSocketCallback은 pending CAS/ticket getAndAdd/release publication/drain을 한다.
권장: owner-only completion은 직접 primitive state transition으로 전환하고 별도 completion mailbox/CAS 제거. 외부 SEND ticket 순서, 이전 미공개 SEND 예약 뒤의 action, 이미 전송한 prefix WRITE 완료, 재진입, burst flush 및 _stateLock 하의 capacity/lease accounting 보존. 현재 serializer는 임의 producer가 drain할 수 있으므로 실제 owner confinement 전환 없이 CAS를 삭제하지 않는다. 전체 ring 삭제/축소는 FIFO·cancel·overflow·reservation 의미를 기존 ingress가 보장할 때만 허용. 암호화는 실제 wire 순서로 한 번만.

### N04 — CIO owner/Selector owner/queue
근거: thread/CIOThreadCallback.java 327–343,427–515,1117–1191,1918–2015; SelectorTransport.java 647–765,830–839.
attachSelectors로 기존 CIOThread에 OwnerDriver를 연결한다. CIOThread.run은 timer tick → packetIngress drain → driver.advance → driver.awaitWork. hosted path의 selector와 game timer는 같은 물리 스레드다. Selector.Owner.mailbox는 Object[]/OwnerCommand[]와 synchronized(mailbox), 별도 close control queue를 갖는다. CIO packetIngress는 synchronized(owner) admission을 갖는다.
권장: same-owner 명령의 직접 실행, foreign path마다 publication/wakeup 한 번만. 실제 중첩 edge를 추적하고 owner driver와 CIOThread 통합은 게임 scheduler 외부 동작 변경 없이 시행. packetIngress는 CacheD/session/world 완료를, selector mailbox는 channel register/cancel을 담을 수 있어 무조건 같은 큐라고 단정하지 않는다. 소켓 FIFO와 shard mailbox도 의미가 다르다. earlier ticket, reentrancy, wake gate 및 mailbox full에서도 close 가능해야 하는 계약 보존.

### N05 — IP 접속 제한 이중 등록 (확인)
근거: core/transport/CIOServer.java register/forget/byAddress; core/server/CDummyServerCallback.java 48–113; client/manager/CIPConnectionCounter.java 15–93; CUserSocket.cleanupAfterLogout.
CIOServer는 synchronized HashMap<InetAddress,Integer> byAddress로 먼저 IP 제한 검사/증가하고 transport forget에서 감소한다. CDummyServerCallback은 !Config.shutdownCheck일 때 CIPConnectionCounter.isPermission(address)로 다시 검사·예약. 두 번째는 ConcurrentHashMap<Integer,AtomicInteger>와 tombstone CAS, cleanupAfterLogout에서 감소. 중복 예약 사실 확인. 수명 차이(transport live vs cleanup-retained), shutdownCheck 및 limit=0의 동작이 달라 단순 삭제는 위험.
권장: 단일 authoritative IP registry에서 필요한 수명 정책을 표현. 동시 IPv4 accept/close/reconnect, cleanup pending, shutdownCheck, limit=0, 낮은 port 거절 정책 테스트 후 중복 map/CAS 및 lookup 제거. 접속 제한 완화 금지.

### N06 — Server + CIOServer + CDummyServerCallback
근거: core/server/Server.java 24–142, CIOServer.java 13–160, CDummyServerCallback.java 21–116, IServerCallback.java.
CIOServer sockets Map, Server connections(CUserSocketMap) 및 AtomicInteger admitted, Selector Owner.admitted, factory liveByShard/nextShard가 각각 존재한다. hosted ACCEPT는 이미 currentShardIndex가 정해져 있어 factory fallback load-score는 off-owner용이다.
권장: lifecycle별 카운트와 lookup 소유자를 단일화, 단일 game factory이면 Server에 생성 함수 흡수 후보. test factory 참조 확인 전 IServerCallback 삭제 금지. global limit/owner capacity/active vs retained 의미 구분. handshake, low-port, IPv4, maxOnline, IP/MAC/account 및 duplicate enter, shutdown barrier 보존. 계정/IP/MAC registry는 실제 multi-owner 공유 상태이므로 CIOServer monitor를 무조건 제거하지 않는다. l1j.server.Server bootstrap은 다른 책임이라 이름만 보고 합치지 않음.

### N07 — CUserSocketMap + CUserSocket + CUserSocketReleaseTask
근거: CUserSocketMap.java 1–39, ReleaseTask.java 1–23, CUserSocket.java 1005 이후/3875–3998/4079/4133–4195.
Map value는 각 socket의 AtomicInteger RETIRED/IO_DRAINED/CLEANED, socket은 closeStarted/clientCleanupComplete/_closed/_closeNotified/SESSION_LOGOUT_STARTED, ReleaseTask는 별도 AtomicBoolean started와 callback을 가진다.
권장: lifecycle bits를 소켓 primitive+VarHandle로 단일화할 수 있는 항목을 분석, map value wrapper 제거. retained registry는 실제 I/O drain+L1 cleanup 완료까지 연결을 유지하므로 map 자체 삭제를 가정하지 않는다. register/close race, old session stamp, reconnect, admission decrement once, cleanup once 보존. ReleaseTask는 ThreadPool 및 비상 virtual thread recovery라는 실제 실행 경계를 가지므로 gate를 통합하더라도 exactly-once 및 stale task 보호 필수. logout/DB를 CIO owner에 동기 실행시키지 않는다. PSS detach/개인상점/auto-clan 현재 정책 불변.

### N08 — Selector framed 구현 vs game raw 구현
근거: SelectorTransport.Connection ctor 231–272, Owner.initialize 830–839, CSelectorFrameReader/CSelectorSendQueue/CSelectorBufferPool, CIOServer.RawHandler.
확인: production CIOServer는 RawHandler 사용. 이때 Connection.reader/send 생성하지 않고 Owner.initialize에서도 Selector receivePool/sendPool 생성 생략. 실제 게임은 CUserSocket과 CIOBufferPool을 사용한다. 따라서 두 pool이 동시에 game 연결마다 낭비된다는 주장은 틀리다.
별도 framing/send/cipher/lease 구현은 코드·테스트 유지보수 중복이다. 전체 참조 조사 후 standalone/test-only이면 test fixture로 이동하거나 실제 production 구현을 테스트하도록 통합. round-trip, cipher, partial/zero-write 테스트 이식 후 삭제. 직접적인 production lock/memory 절감으로 계산하지 않는다.

### N09 — CIOBufferEx + CIOFrame + CIOSlotPool
근거: CIOBufferEx.java 8–140, CIOFrame.java 1–52, CIOBufferPool.java 114–167, CIOSlotPool.java 122–248.
Bucket bitmap CAS가 slot 소유권을 확보, CIOBufferEx가 publish/unpublish/release를 ACTIVE로 보호; CIOFrame이 owner/lease/writer/active/ticket/length를 별도 저장. CIOFrame도 lazy 재사용이며 per-packet new가 아니다.
권장: direct frame handle metadata를 lease에 흡수하거나 참조를 줄이되 public builder identity/stale handle/nested encode 검증. slot bitmap/generation CAS는 실제 공유 lease 경쟁 보호. lease ACTIVE와 bitmap은 보호 대상이 다르므로 단순 중복 CAS로 취급하지 않는다. shard-local receive preference와 global overflow capacity 보존. 검증 없는 local pool 전면 교체 금지.

### N10 — deadline 중복 정책
근거: CSocket.java 22,103–128; CUserSocket.java 195–230,4550–4592; CIOThreadCallback.java 402–425; SelectorTransport DeadlineHeap.
생산 configureTransportDeadlines → CSocket.configureDeadlines/requestDeadline → Selector deadline. 별도로 _connectionTime/60초 checkPreLoginTimeout/runLoginTimeout, CIO wheel scheduleLoginTimeout/scheduleNetworkTimeout API 존재. 실제 이중 등록은 미입증.
권장: 전체 call graph 확인 후 login policy 값과 pending timer 하나로 정리. TCP write stall, partial read, graceful close는 다른 목적이며 각각 보존. monotonic clock, 재등록, 취소, 인증 전이, timeout, shutdown 검증. 타이밍 휠 자체를 제거하지 않음.

### N11 — session monitor
근거: CUserSocket.java 3091–3110, CIOThreadCallback.java 355–396, CPacketHandler session/direct opcode 분류.
dispatchPlaintext 일부 handler는 synchronized(this), completeSession 역시 synchronized(client) 안에서 world/session/account/actor/stamp 검사 후 실행. 실제 동일 owner인지와 CacheD/logout/GM/ThreadPool 등의 외부 writer audit 후 owner-only 경로의 monitor만 제거. stale session/game 상태 보호 불변. 게임 로직 변경으로 범위 확대 금지.

### N12 — broadcast wrapper (별도 승인 범위)
근거: CAsyncSendConstBuffer.java 15–112,146–210; CAsyncSendConstBufferExceptSelf.java 4–111; CSharedBroadcast.java.
ExceptSelf는 exclude(recipientId)와 반복 factory를 추가한다. base에 excludedSender/includeSelf 정책을 두면 같은 SEND 책임 통합 가능. eager/lazy source, large fallback, 실패 release와 recipient별 FIFO/암호화 보존. base send/close/hasFrozenSource synchronized는 producer-only 객체라는 완전한 입증 후 제거 후보. Source AtomicInteger refcount는 실제 여러 소유자가 있으면 유지. CSharedPacketFormat은 stateless formatter, CTLMFilter는 대상 정책이라 분리 자체를 낭비로 보지 않음. 별도 승인 전 방송 소스 수정하지 않음.

### N13 — command/paket wrapper, scratch 및 별칭
CIOObject(run/cancel/OnIOCallback alias), PacketTask(빈 extends), Selector OwnerCommand의 execute-or-cancel 의미 비교. CSocket.postCompletion이 생성하는 command adapter 제거 후보. CUserSocket.getConnection()=this, client()=조건부 this, drainOnOwner()=0/requestDrain() no-op 등 이전 wrapper 호환 API의 모든 참조 치환 후 삭제 후보. CPacketHandler의 5개 string scratch ThreadLocal 및 유사 parser는 nested 사용·charset·bounds 검증 후 공통 scratch 후보. CPacketReadBuffer/WriteBuffer와 CPacketCryptor는 다른 수명과 책임. CServerPacket/IPacketWriter는 공통 encoding 계약, CBytesPacket은 실제 데이터가 있어 이름만으로 삭제 금지. size-state CAS는 sizing policy, CPacketFilter AtomicReference는 reload publication이므로 실질 필요.

### N14 — CThreadLocalTimer + CSlot
wheel bucket CSlot 및 static MpmcArrayQueue<CSlot> pool 확인. nested class로 옮긴다고 비용이 감소하지 않는다. shared slot pool을 owner-local freelist로 바꾸는 것은 cross-thread direct admission/return과 용량, 측정 후 별도 결정. timer monitor는 실제 외부 schedule 보호. 이번 네트워크 통합에서 game timer 정책 변경 금지.

### N15 — 설정 및 남은 호환 API
CIOConfig(login/MAC), CConnectionConfig(connection/shard) namespace 정리 후보; properties/JVM override/default/validation 호환 유지. CIOThreadFactory에는 NIO async-group 예외 처리 흔적이 남아 전체 SRC 참조 scan 후 미사용이면 삭제. CSocket.userSocketState()=null, socketNumber fallback, isJavaTransport()=true 같은 adapter shim 정리 후보. 실제 운영 상태, 오류 로그, 안전 invariant와 test harness는 이미 제거한 production telemetry와 구분. DominationHybrid의 전역 lock은 GM handoff 보호이지 중복 소켓 락이 아니다.

## 4. 권장 구현 순서
1. N01/N02: 소켓과 pending I/O state 통합. 각 호출부 전환 및 owner assert. 기존 serializer/FIFO를 먼저 유지하고 중복 callback 제거.
2. N03/N04: owner direct completion부터 CAS 제거. foreign SEND FIFO 및 reservation/prefix retirement/partial write를 증명하고 중복 큐·ring 축소. 새 worker/queue 금지.
3. N05/N06/N07: IP/MAC/account/admission/retained lifecycle 단일화. close+reconnect+cleanup 경쟁 검사.
4. N08/N09/N10/N11: 독립 stack/lease/deadline/session monitor. 사용처와 실제 소유권 입증 후 변경.
5. N12–N15: 방송 별도 승인 및 작은 wrapper/config 정리. 성능 telemetry 복구 금지.
각 단계 최신본 기준 적용, 컴파일/검증 후 다음 단계 진행. 동일 역할의 호환 fallback을 영구히 남기지 말 것. 삭제 클래스 개수가 아닌 실제 hot-path CAS/lock/queue hop/alloc과 성능으로 평가.

## 5. 필수 검증
- JDK21 전체 dependency type compile; 삭제된 class/method/reference/import 전수 확인. 정상 startup/shutdown.
- TCP fragmentation/coalescing, 2-byte framing/invalid length, plaintext handshake, cipher byte-exact order, partial/zero writes, gather, 4K/64K 경계, backpressure/pause/resume, bounded rejection 및 cancellation.
- same/cross-shard 20 producers, 미공개 FIFO ticket 후 SEND/action, prefix WRITE completion, reentrant encoding/exception, burst, close race. accepted SEND exactly once 또는 정의된 cancel, no double release/stale lease/dropped response.
- same IP/MAC/duplicate account admission, maxOnline/owner/global capacity, cleanup pending/reconnect/old socket identity, off-owner close, blocked logout와 shutdown.
- 로그인·partial read·write stall·grace timeout, timer cancel, slow peer isolation, game timer 유지.
- 실제 이동/공격/스킬/상점/거래/PSS/broadcast wire order 검증. 이들의 기능 코드는 수정하지 않음.
- 동일 TCP harness에서 반복 warmup 후 1/1024/5000 연결, 작은/4KiB/고부하 및 mixed slow peer. CPU/request, throughput, p99, heap/direct/GC 및 contention. JFR/외부 테스트 사용, production hot-path telemetry를 재도입하지 않는다. Windows Java21 Selector와 Linux 비교.
- 소스 diff에서 중복 state/monitor/CAS/queue hop 제거를 확인하고 회귀 통과 시 완료. 현재는 정적 감사로 성능 수치·전체 컴파일·실 TCP 결과 없음.

## 6. 기준 소스 링크
- [core/connection/CUserSocket.java#L305-L395](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/connection/CUserSocket.java#L305-L395)
- [core/transport/CSocket.java#L160-L292](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/transport/CSocket.java#L160-L292)
- [core/transport/selector/CSelectorTransport.java#L231-L375](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/transport/selector/CSelectorTransport.java#L231-L375)
- [core/transport/selector/CSelectorRawIo.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/transport/selector/CSelectorRawIo.java)
- [core/transport/selector/CSelectorTransport.java#L647-L765](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/transport/selector/CSelectorTransport.java#L647-L765)
- [thread/CIOThreadCallback.java#L1918-L2015](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/thread/CIOThreadCallback.java#L1918-L2015)
- [thread/CIOThreadCallback.java#L327-L396](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/thread/CIOThreadCallback.java#L327-L396)
- [core/transport/CIOServer.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/transport/CIOServer.java)
- [core/server/Server.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/server/Server.java)
- [core/server/CDummyServerCallback.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/server/CDummyServerCallback.java)
- [client/manager/CIPConnectionCounter.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/client/manager/CIPConnectionCounter.java)
- [core/connection/CUserSocketMap.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/connection/CUserSocketMap.java)
- [core/connection/CUserSocketReleaseTask.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/connection/CUserSocketReleaseTask.java)
- [core/connection/CIOBufferEx.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/connection/CIOBufferEx.java)
- [core/connection/CIOFrame.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/connection/CIOFrame.java)
- [core/connection/CIOSlotPool.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/connection/CIOSlotPool.java)
- [core/transport/selector/CSelectorBufferPool.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/transport/selector/CSelectorBufferPool.java)
- [core/broadcast/CAsyncSendConstBuffer.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/broadcast/CAsyncSendConstBuffer.java)
- [core/broadcast/CAsyncSendConstBufferExceptSelf.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/broadcast/CAsyncSendConstBufferExceptSelf.java)
- [thread/CThreadLocalTimer.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/thread/CThreadLocalTimer.java)
- [packet/CPacketHandler.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/packet/CPacketHandler.java)
- [core/config/CIOConfig.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/core/config/CIOConfig.java)
- [utill/CConnectionConfig.java](https://github.com/choeyeonggeol16-sketch/1234/blob/8971c2074481c47b1436dd1ec1a66111b6d690f2/rkern/src/l1j/server/server/model/network/utill/CConnectionConfig.java)
