# RKern JProRing 전송 엔진 전수 개선 감사 보고서
**2026-11-20 | 정적 소스 감사 | 구현 변경 없음**

## 0. 기준 및 결론

기준 커밋: [0da0640878b45630986d005b1528fd303d802755](https://github.com/choeyeonggeol16-sketch/1234/commit/0da0640878b45630986d005b1528fd303d802755) (rkern/jproring-linux-benchmark-20261120). CIO 통합 브랜치의 후속 2개 커밋은 벤치마크 파일 4개 추가이며 생산 전송 코드는 동일하다. 아래 Java 경로는 별도 표기 없으면 rkern/src/l1j/server/server/model/network/ 기준이다. 위치는 해당 함수명 및 기준 커밋 라인으로 고정한다. 실제 Windows/MySQL/게임 클라이언트, 성능 측정, 전체 빌드와 신규 회귀는 이번 감사에서 실행하지 않았다. 테스트 소스 존재는 테스트 통과 증명과 구분한다. 모든 동시성 위험은 확인된 코드 특성과 재현이 필요한 결함 후보로 구분한다.

**핵심 결론:** 멀티 Selector + 기존 CIO owner hosting + 단일 연결의 READ 직렬 실행을 보존한다. 새로운 네트워크 아키텍처로 교체할 이유는 발견되지 않았다. 그러나 독립형 framed transport의 무복사 append 및 owner-local 풀과 실제 게임용 RawHandler + CUserSocket의 전송 특성이 다르다. 성능을 개선할 때 실제 기본 게임 경로를 기준으로 삼는다. Netty 우위/열위는 측정 전 단정하지 않는다.

우선순위: P0 안정성/정확성 차단 및 벤치마크 신뢰성, P1 hot path 및 실제 게임 SEND 최적화, P2 고동접 확장/진단, P3 측정 기반 정리. 확인은 코드 구조 확인을 의미하며 성능 효과를 실측했다는 뜻이 아니다.

## 1. 보존할 장점과 실행 경로

- Server.startLoginServer → core/server/Server의 CDummyServerCallback 생성자 → JavaCioServer → SelectorTransport.bindHosted → CIOThreadCallback.attachSelectors. 별도 네트워크 워커를 추가하지 않고 기존 game/timer owner에 selector driver를 탑재한다.
- CDummyServerCallback.CreateSocket은 currentShardIndex가 유효하면 해당 shard를 게임 affinity로 선택. 외부 native 경로에서는 기존 분산 정책 사용. 고정 소유권 유지.
- SelectorTransport.Owner의 intrusive ready queue: Connection.previous/next/linked, schedule/unlink; 중복 큐잉 방지/노드 할당 제거. Consumer 기반 select와 readiness OR 병합.
- SelectorRawIo는 방향별 한 개 outstanding operation, 완료 전에 callback 필드 및 버퍼 참조 해제. CSocket은 READ/WRITE 재사용 callback/request를 연결당 보유한다.
- CUserSocket.onReadFrame/dispatchPlaintext: pooled direct buffer를 ReadBuffer.resetTrusted로 읽고 정상 프레임당 한 번 복호화. opcode gate, rate limit, 기존 session policy 유지. 같은 읽기 펌프에서 handler 실행 및 다음 READ 등록. 빈 프레임 제한과 8바이트 미만 암호화 프레임 거부 정책 유지.
- 실제 게임 SEND는 CIOBufferPool, WriteBuffer, 기존 FIFO와 부분 송신, watermark, send burst를 재사용. 독립 framed 모드의 SelectorSendQueue와 구분. 공유 방송은 원본 plaintext와 수신자 암호화 상태를 분리한다.
- write 0 시 OP_WRITE 등록; 진행 중에는 ready queue에서 후속 처리를 진행. 종료 시 pending I/O 취소, lease 반환, mailbox 취소 및 재접속/재시작 테스트 코드 보유.
- 독립 framed 모드는 4KiB/64KiB RECV, 16KiB/64KiB SEND owner-local pool, tail 직접 append, 최대 16 gather buffers/256KiB. 실제 게임 경로는 전역 CIOBufferPool과 최대 8 gather buffers/64KiB. 이 상수 차이를 혼동하지 않는다.
- Java SocketChannel/Selector는 Windows JDK WEPoll, Linux JDK EPoll 계열을 사용하며 OS별 실제 provider를 로그로 확인한다. 순수 Java 애플리케이션 구현이지 커널 io_uring을 호출하는 구현은 아니다. Asio strand/완료 콜백, event-loop, 재사용 슬롯, 배치 처리와 유사한 설계 요소를 채택했지만 원본 기술의 실제 구현과 동일하다고 주장하지 않는다.

## 2. P0 — 운영 안전성과 검증의 선결 조건

### A01. 종료 콜백 예외의 엔진 전체 전파 [위험 후보, P0]
근거: core/transport/selector/SelectorTransport.java:504–534 terminate, 598–610 drainCloses, 641–650 advance, 682–685 fail. terminate의 finally에서 handler.onClosed를 호출하고 별도 예외 격리가 없다. 일부 turn 경로는 catch가 있으나 drainCloses/drainExpired/명령 경계에서 RuntimeException이 나가면 fail→전체 requestStop에 도달할 수 있다.
수정: callback 경계별 예외와 치명적 VM 오류 정책을 정의. 연결의 I/O 취소, pool 반환, admitted 감소, session cleanup을 각 단계 finally로 보장. 일반 연결 cleanup 실패를 다른 연결의 종료로 확대하지 않고 적절히 기록. 무조건 Throwable 무시 금지.
검증: onClosed 및 cancel callback 예외 주입, 정상 peer echo 지속, 해당 연결 lease/refcount 0, 정확히 1회 terminal notification. 심각한 owner invariant 실패는 명시적으로 서버 실패 처리.

### A02. Raw graceful/abort 종료 상태 기계 [검증 필요, P0]
근거: SelectorTransport.Connection.beginDrain/finishTurn/armRawWrite/terminate, SelectorRawIo.cancel, CSocket.closeGracefully/close/javaTerminated, CUserSocket.close/driveWriteOnce/drainWriteCompletion. raw 모드는 단일 pending WRITE를 보며 drainReason 설정 후 신규 WRITE 등록을 거부한다. 게임 FIFO에 추가 lease가 남은 상태에서 provider drain을 시작하지 않는 계약이 필수.
수정: 게임 FIFO drain → provider graceful close → terminal → session cleanup의 순서를 명문화. abort는 대기/진행중 작업을 각각 한번 취소. close command 우선순위 처리와 earlier accepted command cancellation 계약 유지. close 중에는 새 SEND admission 금지. EOF 정책은 기존 게임 클라이언트 동작과 대조.
검증: 1바이트씩 partial write, 복수 gather residual, peer FIN/RST, close-after-flush/abort 동시 호출, mailbox full, timer timeout, encoder 미게시 중 종료. FIN 전 모든 승인된 bytes 전달 또는 명시적인 실패. double free/late callback/stale key 0.

### A03. 종료/재시작과 hosted owner 생명주기 [위험 후보, P0]
근거: SelectorTransport 생성자 bindHosted/listener.register/host.attach, Owner.advance/awaitWork/closeOnOwner/finish/cleanup, CIOThreadCallback.attachSelectors/외부 stop, core/server/Server.finishShutdown. Hosted driver는 기존 스레드이므로 standalone thread lifecycle과 다르다. 완료 latch와 기존 CIO 스레드 join은 별개.
수정: attach 실패/부분 설치/owner startup race rollback, attach 이후 초기화 실패 시 전 owner 자원 회수, 이전 driver active/finished/새 driver 교체의 publication 및 generation 경계를 검토. shutdown coordinator가 완료/실패를 올바르게 구분. 타임아웃 뒤 버퍼 임의 해제 금지. 기존 스케줄러 전체를 네트워크 종료와 함께 무조건 중단하지 않는다.
검증: bind 실패, selector close, partial attach 오류 주입, 시작 즉시 stop, detach/rebind 100회, 종료 중 accept/producer, owner 예외, shutdown timeout 및 재시도. listener/selector/FD/lease/registered session 정합 확인.

### A04. 외부 접근과 Owner·게임 상태 일치 [검증 필요, P0]
근거: CSocket.submit/CSocket callbacks, CUserSocket.send serializer는 임의 생산자에서 실행될 수 있고 READ는 Java owner로 넘김. CDummyServerCallback currentShardIndex. 같은 소켓 RX 직렬화가 NPC/타인/DB/타이머의 게임 상태 접근까지 자동 보호하지 않는다.
수정: 모든 호출자의 thread/owner matrix 작성: RECV, WRITE 준비/암호화, WRITE I/O, broadcast, timers, logout, NPC/game callbacks. Java owner fast path와 외부 producer 경계를 명시. 데이터/키/버퍼 재사용은 owner 전용 또는 기존 synchronization 계약을 증명한 구간에만 적용. 외부 packet mutable 인자를 지연 인코딩할 경우 freeze/snapshot 또는 기존 시점 인코딩 유지.
검증: 동일/다른 owner 및 NPC/타이머 동시 SEND, 로그인/로그아웃, attack/move/skill, 계정 전환. packet FIFO/암호화 순서/게임 상태 동등성. 동기화를 일괄 삭제하지 않는다.

### A05. 암호화 및 protocol 정책 일치 [검증 필요, P0]
근거: CUserSocket.SetKey/encrypt/decrypt/onReadFrame/dispatchPlaintext/preparePackedEncryption, Cryptor.encrypt/decrypt, SelectorSendQueue.send(독립). 첫 S_KEYBYTES 평문 이후 세션별 상태가 진행한다. 독립 framed dispatch는 cipher!=null 및 remaining==0이면 복호화 없이 handler로 전달; 게임 경로는 빈 프레임 rate gate를 둔다.
수정: 실제 게임 정책을 canonical로 유지. 정상/필터링/unknown opcode의 decrypt 횟수, handshake retry, 포화로 거부된 frame의 키 상태, encode failure와 partial WRITE 재암호화 금지. 독립 API에서 빈 프레임의 계약을 명확히 정의하거나 암호 활성화 후 거부. Java와 native wire 상대 비교는 원본 DLL과 compatible JDK가 있을 때 별도 수행.
검증: known-answer vectors 및 실제 클라이언트 wire capture, 모든 representative opcode/frame size, packed mixed frames, 손상/빈 프레임 flood, reconnect/key reset, 첫 응답 실패, 수신 0/1/2/7/8 bytes 경계, 65535 byte max, byte-order 및 unaligned 8-byte action frame. 기존 gameplay gate 누락 금지.

### A06. 세션/접속 제한 및 registry 계약 [검증 필요, P0]
근거: JavaCioServer.register/forget/setAccount/setMac/setEntered/setState, core/server/Server.activateAccepted/onClosed, CDummyServerCallback.CreateSocket. IPv4/IP/MAC/동일 계정 entered gate와 기존 IP 카운터가 함께 존재.
수정: 기존 native semantics와 상태 전이 대조. accept/admission/등록 실패의 decrement exactly once. JavaCioServer의 registry monitor 내부에서 CSocket.selectorReport monitor를 획득하는 진단 경로의 역순 lock 가능성을 호출 그래프로 확인. 사용자/계정 정보 로그 노출 제한.
검증: IP/MAC 한도 경계, 동시 duplicate entry, failed login/close 중 재로그인, stale session, 접속 폭주 및 capacity full, counters 0 복원, registry 진단을 동시에 호출.

### A07. 기존 native 전용 호출 및 Java 기본 경로의 분리 [검증 필요, P0]
근거: CSocket.SetKey/releaseUserPacket/encryptUserData/pauseNativeUser/SendConstBuffer(MemorySegment)는 parent(CIOServer)를 사용; JavaCioServer형 CSocket.parent는 null. CUserSocket.nativeDirect 등으로 분기한다. 새 Java 경로에서 native 라이브러리 미초기화 접근이 발생하면 NPE 또는 링크/로드 오류 가능.
수정: 해당 호출들의 실제 reachability 조사, DLL-free 기본 게임 경로의 분기 계약 검사. 지원되는 Java 방송/직접 송신만 연결하고 기존 native 호환 경로를 임의 삭제하지 않음. Java transport와 native provider diagnostics 의미 분리.
검증: DLL 없는 Java 21 환경에서 서버 시작, 모든 대표 송신/이동/공격/프로토/대형 방송, 계정 조작/관리자 명령/종료. Windows/Linux 각각 실제 게임 부팅과 로그인.

### A08. 벤치마크를 안전성 게이트와 분리 [확인된 제한, P0]
근거: rkern/docs/jproring_linux_benchmark_20261120/TransportServer.java는 RawEcho 및 SelectorTransport.bind(standalone); TransportDriver는 64바이트 echo/window 1. 전체 게임 실행 경로를 통과하지 않는다. A23–A28의 측정 보완을 먼저 수행. 기존 기능 회귀와 별개로 독립 결과를 기록한다.
검증: 독립 raw / framed / 실제 game-compatible transport 세 종류의 결과를 구분. 실제 게임 테스트를 통과한 버전만 운영 채택 후보.

## 3. P1 — 실제 게임 hot path (가장 큰 최적화 후보)

### A09. 512 슬롯 SEND serializer와 lock 비용 [확인, P1]
근거: core/connection/CUserSocket.java:2160–2283 tryWritePacketInternal. _stateLock.lock, SOCKET_PENDING CAS, SOCKET_TICKET getAndAdd, _reservedDirect, encode 후 재lock. direct=false. canExecuteOwnerControlDirect(1861)는 단순 CIO affinity가 아니라 기존 serializer owner로 제한.
개선: 동일 CIO owner가 생성·인코딩·commit하는 일반 SEND fast path를 설계. 외부 생산자의 이미 예약됐지만 아직 게시되지 않은 FIFO ticket이 있으면 앞지르지 않는다. 순서 기준점/linearization point, 재진입, SEND_START 및 completion control 순서, close/cancel을 증명한다. 기존 외부 경로를 즉시 삭제하거나 모든 send를 owner mailbox에 몰아넣지 않는다. 먼저 외부/내부 호출 비율을 계측.
검증: 20 producer 동시 송신, unpublished reservation의 의도적 stall, FIFO byte sequence/키 동일성 100%, partial completion/close, CPU/packet CAS 실패·lock 대기·allocation. 이전 동작 유지.

### A10. 게임 SEND 직접 append로 병합 복사 제거 [확인, P1]
근거: CUserSocket.packAdjacentStagedPacketLocked(1653–1696) 및 compactReadyStagedPacketsLocked; dest.put(prior, packet.buffer(), 0, length). 일반 패킷은 별도 lease에 encode한 뒤 copy merge. 독립 SelectorSendQueue.send는 아직 전송되지 않은 tail 공간에 직접 인코딩하고 enqueue 전 encrypt.
개선: owner-only, 암호화 및 post 전, tail에 충분한 공간이고 earlier ticket이 완전히 정리된 경우 header 자리 확보→기존 WriteBuffer로 body encode→길이 확정→FIFO commit. 암호화 시점/키를 기존 계약에 맞추고 이미 전송/암호화된 영역 수정 금지. 실패 시 position/limit 및 admission 회복. WriteBuffer의 learned size를 최종 크기로 오인하지 않고 capacity exceeded 경계와 retry side effects 관리. 외부 생산자 및 공유 plaintext는 기존 경로 유지 또는 검증된 전용 경로.
검증: 8B action/이동/프로토 길이 혼합, 16KiB boundary, 64KiB, encoder 예외/overflow, 외부 ticket 경쟁, 0/partial write, close 중 encode. copy bytes, borrow/return, syscall/packet, CPU/packet, p99 및 wire byte parity.

### A11. 실제 게임 버퍼 풀 경쟁 [확인, P1]
근거: CIOBufferPool은 공유 Bucket과 CIOSlotPool bitmap CAS, generation, CIOBufferEx ACTIVE 및 LongAdder 계측. CIOSlotPool.acquire는 threadId 기반 시작 bitmap scan. SelectorBufferPool의 owner-local free stack은 독립 framed 경로이고 JavaCioServer는 RawHandler로 실제 게임에서 CIOBufferPool 사용.
개선: 우선 경쟁률/scan 길이/remote lease 비율 계측. RECV처럼 소유가 확실한 영역부터 owner-local cache/free stack을 검토. SEND foreign producer/방송용 전역 pool과 반환 경계를 분리할 수 있으면 단계적으로 적용. 임의 cross-thread release 금지, bounded global capacity 및 idle owner의 pool starvation 방지. 기존 slot generation과 lifetime 유지.
검증: uneven owner load, pool full, cross-owner release, shutdown/reconnect, 5000 sessions, direct cap/lease balance, JFR allocation/CPU/CAS 비용. local reserve가 전역 용량을 무제한 증폭시키면 채택하지 않는다.

### A12. CSocket completion adapter 중복 동기화 [확인, P1]
근거: CSocket.java:64–89, 225–240, 267–285, 350–429. owner READ/WRITE request 및 callback은 재사용하나 readOwner/writeOwner 및 descriptors를 synchronized로 저장·조회·해제. CUserSocket READ_KICK/READ_EVENT/READ_ACTIVE와 SEND serializer가 다시 보호. SelectorRawIo도 one-outstanding 상태 관리.
개선: 동일 owner 요청/등록/완료를 실제로 전용화할 수 있는 변수부터 fast path. 외부 producer, reject callback inline, close/cancel, descriptor lifetime의 경우 기존 보호. owner-only와 foreign 경로 사이 publication ordering을 증명. 중첩 재진입 방지 trampoline 유지.
검증: inline reject, read 0, EOF, partial/gather, foreign close/WRITE progress, owner callback reentry, stale completion. 동일 owner monitor 진입 및 CAS/packet 감소 실측.

### A13. RECV 프레이밍/compact 비용 [확인된 구조, P1]
근거: CUserSocket.onRead(3189–3268)는 flip/position/compact 및 large promotion/demotion. 독립 SelectorFrameReader는 readOffset 기반으로 공간 부족 때만 compact. 실제 game framing은 독립 reader를 사용하지 않음.
개선: payload view offset 방식 비교. 완전히 소비된 buffer는 clear; 미완성 prefix만 필요한 경우에 한해 compact. 4KiB→64KiB 전환 및 incomplete head deadline 유지. 상태형 암복호화 및 borrowed ReadBuffer scope 유지. 연결별 수신 lease를 너무 자주 반환해 재대여 비용을 늘리지 않는다.
검증: 모든 분할 헤더/본문 위치, 200개 합쳐진 packet/부분 tail, max frame, send pressure pause/resume, malformed, timeout, reconnect. compact bytes와 JFR/CPU 비교.

### A14. 배치/송신 flush 정책 [측정 필요, P1]
근거: CUserSocket.dispatchDecodedFrame, beginReadPassSendBurst/endReadPassSendBurst, driveWriteOnce(3657–3690), GATHER_MAX_FRAMES=8/GATHER_MAX_BYTES=64KiB. 독립 모드 16/256KiB는 별개. Ready queue에서 한 connection turn에 raw write 수행.
개선: 자연스럽게 모인 SEND만 batching하고 강제 flush delay 기본 도입 금지. gather 길이 및 byte limit을 대표 게임 burst에서 A/B. 큰 gather의 head-of-line, 부분 write 및 이미 소진된 descriptor 재제출 비용 고려. 한 iteration에서 write syscall budget, latency-sensitive 평문 handshake 우선 정상 FIFO 유지.
검증: request/response one outstanding 및 window 16/64/256, 8B packet burst, broadcast fan-out, slow peer, p99/p99.9와 syscall/packet. 크기 확대만으로 최적이라 단정하지 않는다.

### A15. 방송의 공유 원본과 per-recipient 암호화 [확인된 비용/P1, 방송 변경은 별도 승인]
근거: CAsyncSendConstBuffer.send는 synchronized, Source는 AtomicInteger refcount, Java createPooledWritableConstBuffer는 CIOBufferPool acquire, CUserSocket의 Java 경로는 native const buffer와 다르게 recipient용 복사/암호화가 필요. SharedPacketFormat/large wire path 존재.
개선: plaintext 작성 1회 유지, 수신자별 암호화/순서 유지. immutable 원본 lifetime을 검증하고 producer monitor 범위 및 불필요 view/copy만 측정. 암호화된 byte stream을 여러 세션 간 공유하지 않는다. 기존 방송 동작은 별도 기능 검증 없이 수정하지 않는다.
검증: 수신자별 cipher vector, 중도 disconnect, refcount underflow/stale pooled source, 대형 packet, 20/100/500 recipient burst, source reuse 및 exception. 방송 자체 최적화는 요청 범위 승인 후 진행.

## 4. P2 — 확장성 및 메모리

### A16. 외부 mailbox 비용과 재사용 [확인, P2]
근거: SelectorTransport.Connection.submit(313–330): same-owner 즉시 실행, alwaysDefer 및 foreign은 new OwnerTask; Owner.offer/poll/hasMailboxWork synchronized(ArrayDeque); CSocket.postCompletion에서도 adapter 할당; CSocket.requestDeadline lambda.
개선: 먼저 queue depth, enqueue/execute/cancel, lock contention/alloc 계측. 동일 owner fast path 보존. 실제 병목이면 bounded MPSC ring 또는 기존 CIO ingress 재사용 검토; control close는 데이터 queue full에도 진행. reusable command 수명/중복 enqueue 및 ABA 검증. ring full 및 publish hole 대책 명시. busy spin 기본 금지.
검증: producer 수 1/4/20, full/reject, offer와 select sleep race, owner-side reentry, exactly once cancel, false return 시 caller owns. throughput/CPU/GC/latency 함께 비교.

### A17. PriorityQueue 임의 삭제 비용 [확인, P2]
근거: SelectorTransport.scheduleDeadline 254–266 및 clearStall/terminate 499–511은 PriorityQueue.remove(connection) = O(N); deadlines/stalled 각각 큐. 이미 CIO timing wheel이 존재.
개선: deadline 갱신 빈도, heap size 및 expiry tail부터 계측. hosted 경로는 기존 CIO timing wheel에 소유권을 맞춘 deadline slot 또는 기존 heap에 indexed removal 검토. 추가 타이머 스레드 금지. stale callback generation, monotonic clock, earliest-due semantics, cancel/reschedule 정확히 유지.
검증: 5000 connections, simultaneous expiry, reschedule storm, pause/resume timeout, idle wake, reconnect/stop stale callbacks.

### A18. 이벤트 루프 공정성과 장시간 callback [확인된 한계, P2]
근거: SelectorTransport.Owner.work: ACCEPT32, COMMAND64, READY256, 500us phase; 시간 체크는 8개 처리 경계. 독립 FRAME_BUDGET16과 게임 ConnectionConfig.MAX_PACKETS_PER_COMPLETION 구분. ready collection, expired/close phase/accept에 같은 deadline이 강제 적용되지는 않음. CIOThreadCallback.run의 wheel/packet 및 driver.advance/awaitWork 결합.
개선: 단계별 실행 시간/queue residence/timer drift/slow opcode 계측. 시간을 검사할 수 있는 packet 경계에서 yield 판단. 개별 handler는 선점 불가하므로 blocking SQL/긴 handler를 기존 안전한 경로로 분리할 수 있는지 검토(게임/DB 수정은 별도 승인). owner간 공정성을 위한 새 worker 추가 금지. idle select vs timer wake gate 검증.
검증: 같은 shard 1개 hot socket + 1000 quiet sockets, 200개 합쳐진 frames, 30ms handler, timer spike, concurrent broadcast 및 close. quiet p99, wheel drift, CPU utilization; max packets 및 phase budget이 실제 타이머 목표를 충족하는지 검토.

### A19. 단일 accept owner와 연결 분포 [확인된 구조, P2]
근거: listener.register(owners[0]); reserveOwner round robin + admitted cap; owner0 직접 register, 타 owner mailbox로 전달. Owner0의 onOpen/admission 작업이 game 타이머/READ와 같은 루프.
개선: 접속 폭주에서 owner0 utilization/admission latency/queue reject 측정. 기존 owner 수 유지, burst cap과 IP/slot gate 이전의 불필요 작업 감소. Round robin은 기본으로 유지하고 load-aware 배정은 접속 시점만 검토; active socket migration 금지 unless ownership protocol proved.
검증: churn/로그인 폭주와 5000 persistent connections 동시, 불균형 owner 부하/쿼터, no admission leak.

### A20. Direct memory 상한과 heap retained footprint [확인된 비용, P2]
근거: Server.startLoginServer는 connectionCapacity=maxOnline+max(256,maxOnline>>>3) (65535 cap), SEND slots=capacity*SEND_LEASES_PER_CONNECTION (65535 cap), large recv <=512, large send<=256; CIOBufferPool은 lazy backing이나 warmup 후 보유. CUserSocket serializer 배열 및 pending queue는 connection retained heap에 기여. 5000개의 4KiB RECV만으로 20,480,000 bytes (약 19.53 MiB) backing이며 전체 RSS는 그보다 크다.
개선: per-socket heap/cipher/writer/serializer 배열 실제 retained size, direct high-water 및 pool cap, RSS/working set 별도 측정. 유휴/slow connection lease 최적화는 실제 재대여 비용과 tradeoff. 큰 버퍼 rollback/잔여 작은 tail의 demotion은 복사 대조. 연결 종료 후 외부 핸들이 Owner/transport/pool 전체를 간접 보존하는지 heap dump 검증(직접 reader/send null만으로 전체 retention 해결 보장 불가).
검증: idle 5000, busy 5000, 10000 churn, reconnect 1h, pool high-water 안정화, no outstanding leases, FD 및 retained paths. GC/Cleaner 의존 off-heap 회수와 shutdown 의미 확인.

### A21. 역압력과 pool starvation 정책 [검증 필요, P2]
근거: 독립 frame send는 byte/frame cap 및 high/low watermark; pool exhausted는 reject 또는 IOException. 게임 CUserSocket은 별도 queue/bytes/pressure 및 hard close/reject policies. Raw mode는 selector sendPressure 대신 CUserSocket의 제어에 의존.
개선: 분류별 counters: global pool exhaustion, per socket bound, mailbox bound, optional send reject, mandatory disconnect, slow peer timeout. 1개 slow peer가 전체 send pool/owner-local cache를 고갈시키지 않도록 quota/pressure 검토. mandatory protocol packet silent drop 금지. resume에서 buffered frame이 ready 등록 없이 굶주리지 않도록 owner wake 증명.
검증: slow consumer 1%/10%, all receivers paused, pool full, 정상 peer high-water/p99 및 packet loss/close reason. backpressure 후 정상 진행 복구.

### A22. 진단과 오류 유형 및 운영 로그 [확인된 제한, P2]
근거: core/server/Server.performance는 Java 경로 null, threadDiagnostics 빈 배열, poolReport 제한된 summary. JavaCioServer.reportSockets는 synchronized map scan 및 socket monitor, snapshot allocation. CUserSocket executeMeasured는 매 handler nanoTime 및 slow counters.
개선: per-owner primitive counters/sampled snapshot (packets, bytes, syscalls, gather occupancy, wakeup, selectNow/select blocking, owner jobs, queue high water, phase time, timeout/close reason, CAS retry, pool alloc/copy), 낮은 빈도 수집. 정확히 동일한 counter 정의로 Netty 경쟁 비교. 오류 폭주 rate limit; 실제 치명 오류는 누락 금지. 기존 운영 UI와 metric schema 호환 확인.
검증: diagnostics on/off A/B, live snapshot consistency, benchmark에서 캡처 비용 수치화. hot path 별도 log/object allocation 최소화.

## 5. 벤치마크 감사 및 필수 확대 (P0 측정 공정성 / P2 확장)

### A23. 비교 대상/경로를 명확히 분리
현재 TransportServer.runRKern은 RawEcho, standalone owner 1, per-session 64B direct buffer, cipher 없음, CIO/게임 serializer/packet framing 없음. Netty도 단순 raw echo. 이는 OS TCP transport 및 callback 비교이며 게임 서버 벤치마크가 아니다. 
개선: (a) raw echo, (b) 같은 length prefix/framing/encode/batch/암호화/limits를 가진 framed comparison, (c) 기존 CUserSocket과 CIO hosted, 실제 opcode representative fixture. (c)는 동등한 Netty game adapter를 준비할 때만 framework 간 직접 비교로 해석. 별도로 기존 RKern baseline과 end-to-end A/B. 구현자 변경 금지 조건은 명확히 기록.

### A24. 동접/window/payload 스케일 확대
현재 1/16 persistent + 8 churn, 64B, blocking request/response window=1; 서버 1 owner, RKern cap 128. 5000 목표나 멀티 Selector scalability를 검증하지 못함.
확대: owner=1/2/4/8(물리 코어 및 실제 설정에 맞춤), clients=1/16/128/1024/5000, window=1/16/64/256, payload=8/32/64/256/4096/65535 wire-boundary applicable, RX coalescing/large TX burst, broadcast, mixed 80% idle/20% active, slow client/churn. 모든 조합을 무조건 반복하기보다 최소 대표 matrix 후 병목 조건 집중. 5000용 load generator는 비동기/다중 프로세스 사용, client bottleneck 별도 측정.

### A25. CPU 효율·시간·통계
현재 3 trials, warmup750ms/measure1500ms, wall RTT p50/p95/p99; CPU/req 및 p99.9 없음. Backend 순서 rotate는 좋음.
개선: 독립 JVM repeat >=5, 충분한 warmup/JIT 안정화(예: 30s+), measurement 30–60s 또는 안정화 근거, 30–60min soak. server process CPU user+sys / completed requests, context switches, perf syscall counts, JFR allocation per request, GC pause 및 safepoint. client CPU/affinity/CPU quota 별도 기록. p99.9/max, confidence/dispersion median/IQR, 개별 trial raw values. closed-loop latency에는 coordinated omission 한계 명시; open-loop rate sweep과 overload 결과 별도.
검증: 동일 JDK/heap/GC/CPU/physical machine/loopback settings/TCP flags/backlog/threads. 단일 run이나 공유 CI runner로 운영 성능을 단정하지 않는다.

### A26. 메모리 지표 측정 시점 불일치 [확인]
TransportServer.runRKern은 server.close 이후 memory(handler.allocated*64) 출력; Netty는 listener/group shutdown 전 memory(allocator.metric()). run.py는 마지막 MEMORY 값들을 해당 서버의 모든 workload 행에 동일 기입. RSS/HWM은 workload 끝에 샘플되지만 HWM은 서버 프로세스 시작 이후 누적.
개선: 각 workload steady-state/peak/after drain 동일 시점에서 양측 RSS, heap used/retained, direct/native pool bytes 및 committed capacity. baseline idle subtraction 및 live connection count. JDK direct MXBean이 Netty native/unsafe backing 전체를 포착하지 않을 수 있어 RSS/allocator metric을 함께 제시. per-workload peak는 주기 샘플/독립 프로세스로 측정.

### A27. Netty 및 OS 설정 공정성 [검증 필요]
현재 Netty 4.2.21.Final, PooledByteBufAllocator.DEFAULT, FixedRecvByteBufAllocator(64), ctx.writeAndFlush; RKern RawEcho session 64B buffer 재사용 및 one-pending reads. 두 구현의 read/flush scheduling과 read outstanding policy가 동일하지 않다.
개선: 기본 생산형 설정 비교와 동등 동작 설정 비교를 분리한다. Netty raw pooled mode의 realistic buffer size, batching/readComplete flush, AUTO_READ 정책을 문서화하고 동일 echo byte semantics 검증. io_uring native availability 실패는 N/A와 원인 출력(실행했다고 간주 금지). Linux kernel, cgroup, JVM flags, native provider, WEPoll Windows provider, socket buffer actual values 기록. 최소 Linux와 Windows 각각 실제 테스트(Windows는 Netty NIO 등 실행 가능한 엔진으로 비교).
검증: 버전/환경/커밋 pin, 반복 순서 randomization 또는 rotation, 외부 네트워크 부하 없는 dedicated host에서 재현.

### A28. CI 검증 및 결과 보존 [확인된 제한]
workflow .github/workflows/jproring-linux-benchmark.yml은 ubuntu-24.04, timeout7분, 현재 python 출력만 실행; run.py는 --output 지정 시 results.csv/environment.json 저장. workflow는 해당 옵션/업로드가 없음.
개선: --output 사용 및 artifact 저장, stderr/unsupported 및 failed trials 보존. production source 변경이 benchmark workflow trigger에 반영되도록 paths 확장 또는 명시 dispatch. CI quick smoke와 장시간 dedicated benchmark 분리. Windows 기존 receive smoke에 CIO 통합/partial SEND/reset 테스트 추가. build exact revision과 결과를 report에 연결. 실패 result exclude 규칙 명시.

## 6. P3 — 유지보수 및 코드 경계

### A29. 두 transport 실행 경로의 drift
SelectorTransport의 framed/RawHandler 두 경로와 기존 CUserSocket의 parser/producer가 동시에 존재. 중복 구현이 곧 병목은 아니지만 테스트 명칭과 production hot path가 다르게 진화할 수 있다. standalone API는 독립 검증용으로 유지할 수 있으나 게임용 단일 canonical 경로의 범위를 문서화하고 공통 계약 테스트/비교 후 중복 정리. 새 fallback/추상화 추가 금지. 기존 native 호환 목적은 별도 승인 없이 제거하지 않는다.

### A30. 세밀한 비용 최적화는 측정 후
interestOps read/compare/update, selectNow 반복, System.nanoTime 호출, thread-local decode/write buffer cache, retained writer/command wrappers, global diagnostic counters, acceptance registry boxing, array scan/gather descriptors의 비용을 async-profiler/JFR/perf로 확인. OS provider가 내부적으로 만드는 객체까지 측정하기 전 zero-allocation을 선언하지 않는다. 실측 개선이 없거나 p99/CPU/안정성이 나빠지는 변화는 되돌린다. 새 worker/queue/class/설정 토글은 필요성이 입증된 경우만.

## 7. 실작업 순서 및 승인 기준

| 순서 | 작업 | 종료 조건 |
|---|---|---|
| 0 | A01–A08, 기준 기능/벤치마크 확정 | 정확한 byte/FIFO/close/lease invariant; baseline raw/framed/game 데이터 저장 |
| 1 | A09–A10: owner fast SEND 및 직접 append | earlier ticket 불추월, 동일 wire/cipher, 복사/lock/CPU/packet 측정 감소 |
| 2 | A11–A14: shared pool, READ/WRITE adapter, compact 및 flush | 모든 기능 회귀 통과 + baseline 대비 대표 부하 순개선 |
| 3 | A16–A22: mailbox/deadline/공정성/메모리/역압력/계측 | 1024/5000 연결, owner imbalance, churn, slow peer, timer drift 검증 |
| 4 | A15(방송 범위 승인 시), A29–A30 측정 기반 개선 | 방송 correctness 및 production 경로 유지, 불필요한 코드/작업 비용 억제 |
| 5 | A23–A28 확장 benchmark 및 Windows/Linux 운영 검증 | 재현 가능한 보고서, throughput/CPU per packet/p99/p99.9/heap/direct/RSS, no leak |

모든 단계는 source pin 및 A/B 동일 조건으로 검증, 의미 있는 성능 이득과 안전성이 확인된 개선만 기본 코드에 반영한다. 5000명 목표는 실제 game workload/DB/timer를 포함해 검증하며 echo 수치만으로 선언하지 않는다. 기존 게임 동작/CacheD/아이템/NPC 로직과 방송 범위는 변경하지 않는다. 실작업 변경 목록, 성능 보고서, 회귀 실패 및 미실시 항목을 각 커밋에서 기록한다.

## 8. 주요 코드 링크
- [SelectorTransport / SelectorRawIo / SelectorSendQueue / SelectorBufferPool](https://github.com/choeyeonggeol16-sketch/1234/tree/0da0640878b45630986d005b1528fd303d802755/rkern/src/l1j/server/server/model/network/core/transport/selector)
- [실제 게임 CUserSocket](https://github.com/choeyeonggeol16-sketch/1234/blob/0da0640878b45630986d005b1528fd303d802755/rkern/src/l1j/server/server/model/network/core/connection/CUserSocket.java)
- [CIOBufferPool / CIOSlotPool](https://github.com/choeyeonggeol16-sketch/1234/tree/0da0640878b45630986d005b1528fd303d802755/rkern/src/l1j/server/server/model/network/core/connection)
- [CSocket / JavaCioServer](https://github.com/choeyeonggeol16-sketch/1234/tree/0da0640878b45630986d005b1528fd303d802755/rkern/src/l1j/server/server/model/network/core/transport)
- [CIOThreadCallback](https://github.com/choeyeonggeol16-sketch/1234/blob/0da0640878b45630986d005b1528fd303d802755/rkern/src/l1j/server/server/model/network/thread/CIOThreadCallback.java)
- [CIO 통합 테스트](https://github.com/choeyeonggeol16-sketch/1234/tree/0da0640878b45630986d005b1528fd303d802755/rkern/docs/jproring_cio_integration_20261120)
- [Linux 비교 벤치마크](https://github.com/choeyeonggeol16-sketch/1234/tree/0da0640878b45630986d005b1528fd303d802755/rkern/docs/jproring_linux_benchmark_20261120)

**최종 권고:** 현재 뼈대를 유지하며 실제 게임 SEND의 FIFO/복사/락 비용부터 개선하고 실측하라. 독립 프레이밍 엔진과 실제 게임 경로가 다른 점이 가장 중요한 감사 결론이다. 위 30개 항목은 확인한 소스 범위의 개선 과제이며 전체 게임 서버의 미발견 결함이 없다는 보장은 아니다.
