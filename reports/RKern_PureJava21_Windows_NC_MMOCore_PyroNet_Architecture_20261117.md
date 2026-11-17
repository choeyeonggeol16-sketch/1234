# RKern 순수 Java 21 Windows MMORPG 네트워크 — NC × MMOCore × PyroNet 통합 아키텍처 및 구현 명세

> 작성: 2026-11-17  
> 대상: RKern/L1J, Windows, Java 21, 실서비스 단일 물리 서버 5,000 동접 1차 목표  
> 문서 유형: **실제 구현용 상세 설계 보고서**. 코드 변경이나 운영 검증 완료를 의미하지 않음.  
> 최종 결론: **전용 ListenThread 1개 + JDK 21 NIO.2/Windows IOCP 다중 완료 워커 + NC식 연결별 논리 Serializer + MMOCore식 패킷 병합 + PyroNet식 즉시 WRITE 등록 + 기존 CIO 게임 소유 실행부 유지. 전체 통신 핫패스 저수준·저할당 설계.**

---

## 0. 요구사항 및 절대 경계

- **순수 Java 21 애플리케이션.** CTransport.dll, 직접 FFM downcall/upcall, JNI 사용자 라이브러리, RIO 의존성을 최종 서버에서 제거한다. JDK 내부가 OS 네이티브 API를 사용하는 것은 별개다.
- **Windows 운영 전용.** Linux Selector, epoll 대체 구현 및 그에 대한 분기·설정은 이번 제품 범위에 넣지 않는다.
- **실서비스용 단일 구조.** NIO.1/NIO.2를 사용자에게 선택시키는 전송 토글, 실패한 실험 경로의 상시 유지, 임의 워커·중복 큐를 만들지 않는다.
- **전용 로그인 입구 + 다중 스레드.** 오직 1개의 전용 ListenThread가 TCP ACCEPT와 짧은 접속 admission을 담당한다. I/O는 여러 JDK 완료 스레드가 병렬 처리한다. **소켓 하나의 READ/WRITE/암호화 상태는 논리적으로 직렬화**한다. 소켓마다 전담 스레드를 만들지 않는다.
- **저수준·저할당 절대 기준.** RECV/SEND/암호화/방송/완료 콜백 핫패스에서 불필요한 객체 생성, 박싱, 임시 컬렉션·람다·Future·Runnable·ByteBuffer wrapper·byte[] 복사를 최소화하고 가능한 한 제거한다. Primitive·flat array·재사용 객체·bounded pool을 우선하며 메모리·CPU·p99를 측정한다.
- **기존 게임 동작 보존.** CIO 샤드의 게임 상태 소유 실행, CIO Timing Wheel, CacheD, NPC, 프로토콜, 필터·접속 절차는 재설계하지 않는다. 기존 호출 API를 최대한 동일하게 유지한다.
- **송신과 방송의 순서·상태 유지.** 이동·공격·스킬 및 방송이 동일 접속자에게 도착하는 순서, 암호화 키 발전 순서, 버퍼 수명, 종료 후 콜백 무해성을 보장한다.
- **운영 성능 목표.** 5,000 동접, 짧은 게임 패킷/혼합 부하, 장시간 안정성, CPU/패킷과 p99 지연, GC 할당량, 부분 송신 복구. 10,000 동접은 확장 여유를 확인할 후속 목표.
- **본 문서는 설계 명세.** 실측되지 않은 수치를 측정 완료처럼 기재하지 않는다. 운영 채택 전에 충족해야 할 합격 조건을 명시한다.

## 1. 검토한 정확한 원본과 증거

### 1.1 RKern 최신 소스

- 기준 A: RKern_ItemFunction_DomainHandlers_NoItemUse_FULL_SRC_20261117.zip
- 기준 A SHA-256: 09c1bb655a099be738adeeb5a4199495c88d2e105ab98ba6dcd949627eb24ac6
- 기준 B: RKern_NC_NativeBroadcast_Phase114_VisualFanout_FailureSafety_FULL_SRC_20261117.zip
- A: ZIP 1,901 entries, B: 1,891 entries.
- 공통 ZIP entries의 차이는 아이템 관련 Java 17개 파일이며, 신규/제거 entry 역시 아이템 도메인 분리 중심이다. 이번 보고서의 핵심 네트워크 클래스는 이 두 판본에서 동일하다. 다만 아이템 패킷 호출부는 A를 기준으로 한다.
- 기준 A의 실제 Java 소스에서 다음을 열어 코드 위치와 실행 흐름을 확인했다.
  - src/l1j/server/server/model/network/core/transport/CIOServer.java
  - src/l1j/server/server/model/network/core/transport/CSocket.java
  - src/l1j/server/server/model/network/core/transport/CIOPacketCodec.java
  - src/l1j/server/server/model/network/core/connection/CUserSocket.java
  - src/l1j/server/server/model/network/core/connection/CIOBufferPool.java
  - src/l1j/server/server/model/network/core/connection/CIOSlotPool.java
  - src/l1j/server/server/model/network/core/session/CIOClient.java
  - src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java
  - src/l1j/server/server/model/network/codec/Cryptor.java
  - src/l1j/server/server/model/network/thread/CIOThreadCallback.java

### 1.2 비교 원본

- NC L2Server(10).exe 정적 역분석 자료(2026-10-23): CUserSocket IOCP OnRead, 소켓별 512슬롯 serializer(0x45C7AC), SEND 등록(0x545A18), WRITE 완료(0x545E38), 선제 504/사후 512 경계, 최대 8 IOCP worker 관찰.
- MMOCore 원본: https://github.com/L2J/MMOCore/blob/master/MMOCore/src/main/java/com/l2jserver/mmocore/SelectorThread.java  
  https://github.com/L2J/MMOCore/blob/master/MMOCore/src/main/java/com/l2jserver/mmocore/MMOConnection.java
- PyroNet 원본: https://github.com/wuxudong/pyronet/blob/master/src/main/java/jawnae/pyronet/PyroSelector.java  
  https://github.com/wuxudong/pyronet/blob/master/src/main/java/jawnae/pyronet/PyroClient.java
- JDK 21 API 계약: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/AsynchronousSocketChannel.html  
  https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/AsynchronousChannelGroup.html

## 2. 현재 RKern의 실제 동작과 교체 경계

| 현재 구성 | 소스 근거 | 최종 Java 전환 |
|---|---|---|
| Native IOCP 생성·완료 콜백 | CIOServer.java, FFM MemorySegment·MethodHandle 경계 | AsynchronousServerSocketChannel 및 AsynchronousChannelGroup |
| CTransport 핸들 기반 READ/WRITE | CSocket.java, nativeReceive, pause, sendReserved | AsynchronousSocketChannel + CompletionHandler |
| 소켓 512슬롯 직렬화 | CUserSocket.java serializeSocketCallback, drainSocketCallbacks | 의미 보존, 사용자 레벨 Native 이벤트 제거 |
| Native 패킷 전달·해제 | CUserSocket.java transportNativePacket, nativePacketPhase | Java RX 상태 머신, 안정적 디코딩과 CIO dispatch |
| Native 코덱 필수 진입 | CUserSocket.java SetKey()가 CIOPacketCodec 생성 | CIOPacketCodec 순수 Java화, 암호화 프로토콜 검증 |
| Java 암호화 참조 구현 | Cryptor.java initKey, decrypt, encrypt | 연결별 RX·TX 전용 키 상태 확정 |
| Native 완료 기점의 SEND | CUserSocket.java kickWrite, postGather, postWrite | 비동기 WRITE pump 및 병합 버퍼 |
| Direct 버퍼 풀 4KB/16KB/64KB | CIOBufferPool.java | 기존 슬롯·Lease 의미를 Java I/O 수명에 재연결 |
| 기존 게임 sendPacket API | CIOClient.java sendPacket, enqueuePacket | 소스 호환 |
| 게임 샤드 / 스케줄 | CIOThreadCallback.java | 그대로 유지 |
| Native 공유 방송 | CSharedBroadcast.java, CAsyncSendConstBuffer 사용 | Java 공유 평문 원본 + 수신자별 암호화/병합 |

특히 현재 CUserSocket.SetKey는 Java Cryptor를 생성하면서도 Native CIOPacketCodec를 필수로 만든다. **소켓만 Java로 바꿔도 로그인/암호화가 이어지는 구조가 아니다.** 코덱 이관이 1차 제품 완성의 필수 조건이다.

또한 CSocket에는 setAccountIdentity, setCharacterEntered, setMacIdentity, configureDeadlines, pauseNativeUser 같은 Native 관리 API가 있다. 이 기능의 정책을 Java에서 보존해야 하며, 단순히 메서드를 삭제하면 안 된다.

## 3. 4개 엔진의 설계 대조 — 가져올 것과 버릴 것

| 설계 문제 | NC L2Server | MMOCore | PyroNet | RKern 최종 정책 |
|---|---|---|---|---|
| 병렬 I/O | IOCP 완료 워커 | Selector 스레드 | Selector 스레드 | **JDK 21 Windows IOCP 완료 풀** |
| 소켓 실행 순서 | 연결별 serializer | 연결·SEND 큐 락 | Selector 소유 스레드 | **NC식 논리 serializer** |
| 동일 문맥 즉시 실행 | 소유 실행자 직접 콜백 | Selector 루프 중심 | Eager Write | **안전할 때 즉시 WRITE 제출** |
| 패킷 길이 | NC 프로토콜 | 2바이트 프레이밍 | ByteSinkPacket16 | **기존 L1J 바이트 규약 그대로** |
| 여러 패킷 SEND | 연결별 병합 | MAX_SEND_PER_PASS, Direct staging | ByteStream staging | **MMOCore식 연속 버퍼 병합** |
| 부분 송신 | 완료 후 진행 상태 | pending write buffer | ByteStream discard(sent) | **한 접속에서 in-flight WRITE 하나** |
| 큐/버퍼 | ticket + native payload | 재사용 노드/버퍼 | 간단한 ByteStream | **기존 RKern bounded Lease 재활용** |
| 게임 로직 | 직접/외부 실행 | packet handler | listener callback | **기존 CIO 소유 실행부** |
| 비채택 요소 | OS API 직접 호출, 포화 실패의 모호한 수명 | selectNow + sleep, 불필요한 intermediate copy | Selector 전체 timeout 순회, queue만 넣고 wakeup 없음 | 제거 |

**핵심 결론:** NC를 그대로 복제하는 것이 아니라 **NC의 실행 보장**, **MMOCore의 게임 송신 병합**, **PyroNet의 첫 SEND 지연 최소화**를 Java의 완료 모델로 구현한다. **최초 구현 시 SerializerQueue → SendQueue → BatchQueue 식의 중복 계층을 만들지 않고, 연결별 단일 TX Pump와 하나의 송신 FIFO로 통합한다. 이 최적화는 완성 후 작업이 아니라 첫 제품 필수 사양이다.**

## 4. 최종 제품 아키텍처(단일 확정안)

    5,000 TCP clients
           |
           v
    [dedicated ListenThread x1]
      ACCEPT / admission only
           |
           v
    [Windows JDK 21 AsyncChannelGroup]
      완료 워커 1 ... N (멀티스레드)
           |
       +---+------------------------+
       |                            |
    [READ completion]           [WRITE completion]
       |                            |
    [CUserSocket RX]            [CUserSocket TX]
       |                            |
    2B frame parser          logical serializer / FIFO
       |                            |
    RX Cryptor                 plaintext packet staging
       |                            |
    opcode / rate filter       per-recipient TX Cryptor
       |                            |
    CIO 기존 소유 샤드          MMOCore 16KB batch assembler
       |                            |
    L1 game logic              eager async WRITE submit
       |                            |
       +-- sendPacket() ------------+
                                    |
                          AsynchronousSocketChannel

동일 소켓의 READ와 WRITE는 서로 다른 완료 워커에서 동시에 돌아올 수 있다. 이 둘을 전역 락 하나로 묶어 대기시키지 않는다. **RX 상태, TX 상태, 연결 수명은 분리**하고, RX→게임 순서 및 TX→소켓 순서를 각각 보장한다. 공유 close/state 전환은 짧은 원자 상태 전이에서만 조정한다.

## 5. I/O 스레드 배치 및 실행 보장

### 5.1 서버/그룹

- AsynchronousChannelGroup.withFixedThreadPool(initialWorkers, NetworkThreadFactory).
- 초기 워커 기준은 min(8, max(2, availableProcessors))이다. NC의 최대 8과 운영 환경을 함께 반영한 **기본 배치 값**이며, 숫자 자체가 성능 검증 결과는 아니다.
- JDK 그룹의 완료 스레드는 RECEIVE·SEND 결과와 핸들러 실행을 담당한다. 서버가 연결마다 Thread를 생성하지 않는다.
- **전용 플랫폼 ListenThread 1개**를 만들고, 동일 AsyncChannelGroup에 바인딩한 AsynchronousServerSocketChannel의 단일 outstanding accept() Future를 **이 ListenThread에서만 get()으로 기다린다**. 완료되면 **다음 ACCEPT를 먼저 재등록**하고, 짧은 접속 quota/IP/상태 admission 후 해당 채널에 첫 READ를 등록한다. accepted 채널의 RECV/WRITE 완료는 기존 AsyncChannelGroup의 여러 완료 워커에서 실행한다. ListenThread에서 로그인 DB·게임 로직은 실행하지 않는다.
- JDK Future는 연결 수립마다 발생하는 콜드패스 비용으로 취급한다. **게임 패킷마다 Future·Runnable·CompletionHandler를 만들지 않는다.** 별도 I/O 그룹과 소켓별 스레드를 추가하지 않는다.
- 별도의 자체 I/O worker pool, 무제한 work queue, 이벤트 복사 전용 dispatcher를 추가하지 않는다.
- 그룹 shutdown은 서비스 종료 제어 스레드에서 수행하고, 완료 핸들러 안에서는 awaitTermination·Thread.sleep·Future.get을 실행하지 않는다.

### 5.2 인라인 완료와 재진입

JDK의 AsynchronousChannelGroup 명세는 같은 그룹 스레드가 I/O를 즉시 완료한 경우 CompletionHandler가 인라인 호출될 수 있음을 허용한다. 따라서 **WRITE 완료 → 다음 WRITE → 즉시 완료 → 다음 WRITE**의 무한 재귀를 만들면 안 된다.

- 연결별 pump는 wip/owner 상태로 재진입을 감지한다.
- 완료 핸들러는 잔여 바이트·상태를 갱신하고, 이미 실행 중인 pump가 있으면 신호만 남긴다.
- 반복 처리는 반복문과 처리 예산으로 제한하고, 재귀적인 호출 스택을 만들지 않는다.
- 한 완료 워커가 특정 소켓을 장시간 독점하지 않도록 byte/frame 처리량을 제한한다.

## 6. 소켓별 NC 논리 Serializer — 정확한 규약

### 6.1 소유권

**독립된 Serializer 작업 큐와 SEND 패킷 큐를 각각 만들지 않는다.** 동일 연결의 송신 FIFO·TX Owner·16KiB 병합·암호화·WRITE 완료 조정을 하나의 TX Pump 상태 머신에 통합한다. NC Serializer의 순서·수명 보장을 없애는 것이 아니라 중복 실행 단계를 제거하는 것이다.

소켓에는 고정된 OS 스레드 번호가 없다. **현재 실행권을 획득한 완료 스레드 또는 게임 호출 스레드가 해당 소켓의 TX 상태를 직렬 처리**한다.

- 비경합 상황: 직접 fast path.
- 경합 상황: 접수 선후관계를 확정한 뒤 FIFO에 기록, 단일 drainer가 소비.
- 소유권이 없다고 임의의 WRITE를 병렬 제출하면 안 된다.
- 완료 콜백의 재진입은 같은 owner 문맥일 때도 이전 대기 티켓을 추월하면 안 된다.

### 6.2 순서의 선형화 지점

여러 생산자가 동시에 sendPacket을 호출할 때 **성공적으로 송신 접수가 확정되는 시점**을 순서의 기준으로 삼는다.

- 서로 다른 생산자의 엔큐 순서는 short admission lock 또는 명시적인 publish-sequence 경계에서 확정한다.
- 순서번호를 먼저 예약한 작업이 준비되기 전에 후속 패킷이 먼저 암호화·송신되지 못하게 한다.
- same-thread inline 경로가 이미 접수된 대기 티켓을 추월하지 않도록 ready-prefix drain 후 실행한다.
- RX read owner와 TX owner를 혼동하지 않는다. 동일 게임 명령의 여러 SEND는 burst scope로 모을 수 있지만 다른 producer의 확정된 순서는 뒤집지 못한다.

### 6.3 512슬롯의 의미

NC의 512는 C++ serializer의 구현 용량이지 Java에서 모든 세션이 반드시 512개 객체를 상시 생성해야 한다는 명령이 아니다.

- 접속별 정상 경로는 고정 필드 중심으로 운영한다.
- 경합 시에만 재사용 가능한 **bounded primitive ring**을 준비한다. 논리 용량 512와 선제 여유 확보의 정책을 유지하되, 자료구조의 실제 메모리 배치는 Java 21에 맞춰 설계한다.
- 포화 시 작업을 조용히 잃지 않는다. 필수 SEND는 연결 오류/종료 경로로 명확히 귀결시키고, 선택적 방송은 **암호화 전 단계에서만** 정책에 따라 제외할 수 있다.
- enqueue가 성공했다고 반환한 프레임은 버퍼 소유권을 해당 세션이 인수한 것이다. 종료 전까지 재처리·반환 책임을 가진다.

## 7. RECV 상태 머신 — 연결당 미완료 READ 하나

### 7.1 주요 상태

- RX_IDLE: READ 등록 가능
- RX_PENDING: 단일 비동기 READ 미완료
- RX_DECODING: 완료된 수신 바이트 조립/프레이밍
- RX_PAUSED: 기존 부하/게임 접수 압력에 따른 일시정지
- RX_CLOSING: 종료, 재등록 금지

READ 완료는 완료 바이트 수가 0/양수/-1인지 구분한다. -1은 EOF, 실패(Throwable)는 오류 종료 또는 기존 정책 처리, 0은 무한 동기 재호출을 피하면서 진행성을 유지한다.

### 7.2 정확한 수신 흐름

1. READ를 하나만 등록하고, Buffer를 해당 I/O가 완료할 때까지 다른 스레드가 변경하지 않는다.
2. 완료된 바이트를 2바이트 길이 프레임 상태 머신에 제공한다.
3. 헤더 1바이트만 온 경우 유지한다. 헤더 2바이트가 완성되면 unsigned 길이를 판단한다.
4. **실제 기존 클라이언트의 길이 포함 규칙·엔디언**을 그대로 적용한다. 2바이트 unsigned 길이로 표시 가능한 범위를 넘어서는 요청은 즉시 거부한다.
5. 헤더보다 짧은 길이, 허용된 최대 크기를 넘는 길이, 암호화 필수 바이트 미충족, 악성 연속 프레임은 해당 연결의 프로토콜 오류로 처리한다.
6. 패킷이 완성될 때마다 RX 키를 **정확히 한 번** 발전시키며 복호화한다. 필터/알 수 없는 opcode라고 해서 사전 복호화를 건너뛰지 않는다.
7. opcode 분류·rate limit·인증/로그인 상태 검사를 수행한다.
8. 소켓 내 명령 순서를 유지하면서 **기존 CIO 소유 샤드**로 게임 상태 변경을 인계한다. 기존 직접 처리 가능한 네트워크 명령의 경계를 임의로 넓히지 않는다.
9. READ pause 조건을 검사하고, 필요할 때 다음 단일 READ를 등록한다.

### 7.3 수신 버퍼와 백프레셔

- 기본 풀: 4 KiB, 부분 프레임이 큰 경우 64 KiB 풀까지 승격(이미 확인된 RKern 버퍼 계급).
- 미완성 프레임을 위해 정확히 필요한 바이트를 보존한다. 새 READ가 이전 바이트를 덮어쓸 수 없다.
- 게임 실행 샤드가 수용할 수 없는 상황에서 READ를 무조건 계속 진행하지 않는다. 기존 CIO 접수 제한·거부/종료 정책과 연결한다.
- slowloris/부분 프레임 지연은 기존 네트워크 deadline 정책을 CIO Timing Wheel에서 실행한다. 연결별 새 timer thread를 두지 않는다.
- 한 완료 콜백의 프레임 처리 수에는 공정성 상한을 두고, 처리 예산 초과 시 다음 단계로 넘긴다. 추가 메모리 큐를 새로 만드는 것은 기본 정책이 아니다.

## 8. SEND 상태 머신 — MMOCore 병합 + Eager Write

### 8.1 주요 필드와 불변식

- txAdmissionSequence / txDrainSequence: 성공 접수 FIFO 순번.
- txPendingFrames / txPendingBytes: bounded 압력 계수.
- txWriterOwner: TX 상태 변경 실행권(단일 TX Pump, 중복 Serializer/SEND Queue 없음).
- txPendingRing: 연결별 단 하나의 논리 SEND FIFO. 선행 티켓이 없는 유휴 fast path에서만 큐를 건너뛰고, 경합 시 bounded reuse ring을 사용.
- txAssembleLease: 현재 조립 가능한 16 KiB 풀 버퍼.
- txFlightLease: JDK에 WRITE 제출한 버퍼(완료 전 불변).
- txWritePending: 동일 세션에 미완료 WRITE 하나만.
- txCipher: 연결별 송신 전용 rolling state.
- txClosing / generation: close, stale callback 차단.

**절대 불변식:**
- 연결별 txWritePending ∈ {0,1}.
- WRITE 등록 후 참조된 ByteBuffer의 bytes/position/limit을 완료 전 외부에서 수정하지 않는다.
- 패킷의 암호화는 **송신 FIFO 순서대로 단 한 번** 진행한다.
- 부분 송신된 Flight의 잔여 바이트보다 새 패킷이 먼저 WRITE될 수 없다.
- 완료·실패·종료를 통틀어 각 풀 Lease는 정확히 한 번 반환한다.

### 8.2 패킷 생성부터 전송까지

    game: CIOClient.sendPacket(packet)
       -> 패킷 데이터의 수명/스냅샷 확정
       -> admission: frame/byte quota 확보와 FIFO 순번 확정
       -> tx owner 확보 또는 bounded ring에 enqueue
       -> owner: 기존 Flight 여부 확인
       -> 빈 Flight면 16KiB Assembly에 여러 완전한 프레임 직렬화
       -> 프레임마다 암호화·길이 헤더(현행 정확 규약) 적용
       -> Assembly 완성 시 Flight로 소유권 이동
       -> 즉시 AsynchronousSocketChannel.write(Flight, handler)
       -> WRITE completion:
          a) 전부 송신: Lease 반환 -> 다음 batch
          b) 일부 송신: 남은 위치에서 동일 Flight 재등록
          c) 실패: 종료 상태 전환 -> in-flight 완료 경계 정리

### 8.3 MMOCore식 병합의 실질적 정의

    [2B header][packet A body][2B header][packet B body][2B header][packet C body]
    |-------------------- 하나의 연속 ByteBuffer ----------------------------|

게임 패킷 A/B/C를 새로운 논리적 게임 패킷으로 합치지 않는다. **TCP WRITE 제출에 사용할 연속 바이트 배열/버퍼로 함께 싣는 것**이다. 클라이언트는 기존 2바이트 헤더로 동일하게 해석한다.

- 기본 연속 병합 버퍼: 16 KiB.
- 이미 WRITE 중인 동안 발생한 수십~수백 패킷은 다음 빈 Flight를 위해 병합한다.
- 여러 게임 패킷이 하나의 명령에서 연속 발생하면 기존 beginSendBurst/endSendBurst 성격의 배치 경계를 활용하여 매 패킷 즉시 WRITE 제출을 피한다.
- 동시에 **유휴 연결의 단일 패킷**은 불필요하게 묶음 타이머를 기다리지 않는다.
- 이미 만들어진 대형 프레임이 16KiB를 넘으면 별도의 64KiB Lease 또는 안정적으로 소유된 버퍼 구간을 사용한다. 게임 패킷의 최대 크기를 16KiB로 낮추지 않는다.
- Gathering Write는 **복사하지 않고 안정적으로 보유한 여러 바이트 버퍼가 이미 있는 큰 프레임 경로**에만 사용한다. 모든 소형 패킷에 무작정 ByteBuffer 배열을 새로 만들어 호출하지 않는다.
- JDK의 Gathering Write도 비동기 WRITE는 하나다. ByteBuffer[]/positions 전체를 완료까지 보호한다.

### 8.4 PyroNet Eager Write의 Windows 의미

PyroNet 원본은 Selector 이벤트를 기다리지 않고 현재 실행 문맥에서 SocketChannel.write를 시도한다. Windows NIO.2에서는 **SocketChannel의 블로킹 없는 즉시 커널 쓰기 완료**를 모사하는 것이 아니라 **WRITE가 비어 있으면 곧바로 비동기 WRITE를 제출하는 정책**으로 옮긴다.

- txWritePending==0, 이전 대기/Flight 우선 순서 만족, owner 획득 성공이면 즉시 write() 등록.
- txWritePending==1이면 추가 write()를 중복 호출하지 않는다.
- 별도 지연 타이머를 도입해 모든 SEND를 묶지는 않는다.
- 완료 핸들러가 오기 전에 새 패킷이 생기면 다음 Assembly에 모아두되, 완료 버퍼를 변경하지 않는다.
- Eager path 역시 FIFO·암호화 경계를 우회하지 않는다.

## 9. 송신 시점 스냅샷과 게임 상태

현재 CIOClient.sendPacket은 ServerPacket/byte[]/Proto 스트림 등 다양한 입력을 허용하고, WritablePacket은 구현에 따라 호출 이후 게임 객체를 참조할 수 있다. **비동기 송신을 이유로 게임 상태 읽기를 나중의 I/O 완료 스레드로 옮기면 기존 응답 내용이 바뀔 수 있다.**

따라서 접수 규칙은 다음과 같다.

1. 생산자 스레드에서 정해진 시점의 패킷 바이트를 확정하거나, 안전한 불변 값만 담은 기존 Typed Verb를 전달한다.
2. 명령의 정해진 시점에서 생성된 스냅샷이 이후 게임 상태 변화에 영향받지 않도록 한다.
3. **암호화는 별도 문제**이므로 연결별 TX 순서가 확정된 뒤 단일 TX owner 경계에서 적용한다.
4. 전송 실패 후 암호화된 프레임을 임의 삭제하고 같은 연결로 후속 패킷을 계속 보내지 않는다. rolling cipher 동기화를 깨뜨린다.

이 단계는 단순 성능 최적화가 아니라 **현재 게임 패킷의 의미와 키 상태를 보존하기 위한 필수 설계**다.

## 10. 암호화·첫 평문·키 시퀀스의 필수 복원

기존 Cryptor.java는 initKey, decrypt, encrypt 및 opcode preview 기능을 보유한다. 하지만 현재 SetKey()는 Native CIOPacketCodec를 필수 생성하여 Native framing/crypto 경로를 선택한다.

### 10.1 정확성 조건

- 최초 handshake 프레임의 평문 여부와 그 전환 시점 일치.
- 패킷 길이 헤더의 포함 범위, 바이트 순서, 암호화 적용 범위 일치.
- 패킷별 키 갱신 횟수 정확히 1회. **필터된 opcode**도 수신 키 전진 방식 일치.
- 송신 암호화와 수신 복호화의 상태는 방향별 독립.
- 패킷 처리 실패/연결 종료 시 잘못된 다음 키를 유지한 채 재사용 금지.
- 로그인/캐릭터 선택/게임 입장/재접속 과정의 시퀀스·검증·세션 stamp 유지.

### 10.2 반드시 제작할 골든 벡터

Native 기존 서버에서 **운영에 안전한 테스트 계정과 로컬 환경의** wire 바이트를 확보하여 다음을 Java 코덱과 비교한다.

| 벡터 | 기대 조건 |
|---|---|
| 첫 연결·초기 키 | byte-for-byte wire 일치 |
| 첫 평문 응답 후 암호화 | 적용 시작 프레임 완전 일치 |
| 연속 패킷 1,000개 | 매 프레임 ciphertext 일치 |
| 복호화 후 opcode 미인식 | 다음 정상 패킷까지 key 동기화 |
| 길이 헤더 1바이트 분할 | 정확히 한 번만 디코딩 |
| 대형 프레임 4/16/64KiB 경계 | 프레이밍·암호화 결과 일치 |
| 클라이언트 로그인~입장~종료 | 절차 및 시퀀스 일치 |

**골든 벡터 불일치가 한 건이라도 있으면 Native 제거 단계를 완료로 선언하지 않는다.**

## 11. CSharedBroadcast 순수 Java화

현행 Native broadcast를 이미 수차례 최적화했더라도, 새 제품에서는 **그 API와 의미는 보존하고 Native buffer 보유 방식만 제거**해야 한다.

    1회 방송 내용 작성
          |
    Immutable shared plaintext frame
          |
    +-----+-----+------+
    |     |     |      |
    A     B     C      ...
    |     |     |
    각 세션 FIFO 송신 접수
    |     |     |
    각 세션의 개별 rolling TX cipher
    |     |     |
    각 세션 16KiB assembler / WRITE

- 방송 원본 생성은 가능하면 한 번만 수행한다.
- 수신자별 암호화는 반드시 **그 수신자의 TX FIFO 순서 안에서** 수행한다.
- 공용 원본은 immutable. Native FFM 공유 Lease 대신 Java 참조·풀 버퍼 수명 관리.
- 선택적 방송의 메모리 압력 정책은 **접수·암호화 이전**에 결정한다. 암호화 후 폐기하면 해당 연결의 키 발전이 틀어질 수 있다.
- 기존 AOI, GM, clan, 맵·팀, 자기 자신 포함/제외, movement/action, optional/mandatory 정책을 그대로 유지한다.
- 방송 범위와 게임 호출부를 일괄 재작성하지 않는다. 기존 CSharedBroadcast 호출 계약을 보존한다.

## 12. 버퍼 풀 및 메모리 상한

현재 코드의 CIOBufferPool은 bounded lazy direct-buffer slots이다. 이미 4KiB RX, 16KiB TX, 64KiB large 버킷과 정확한 슬롯 회수 검증 계수를 갖고 있다.

**새 운영용 기본 정책:**
- 기본 RX buffer 4KiB, 큰 조립 프레임 64KiB(상시 세션별 예약 금지).
- 기본 TX assembly 16KiB, 예외적 큰 프레임 64KiB.
- pool acquire 실패는 명시적 backpressure/close 경로. 무제한 heap Buffer 생성으로 우회 금지.
- 입출력 작업별 Lease 상태: FREE → RESERVED → IN_FLIGHT → COMPLETED/ABORTED → FREE.
- 버퍼 소유자는 **풀 슬롯 + 소켓 세대 + operation kind**로 추적한다.
- completion handler와 abort 경로가 동시에 같은 Lease를 반환하지 못하도록 단일 상태 전이로 보호한다.
- 5,000연결 상시 각각 64KiB RX+16KiB TX 예약은 금지한다. 재사용 풀과 실제 활성 I/O 수에 따른 lazy 할당을 유지한다.
- connection queue는 프레임 수와 대기 wire bytes 모두 상한을 둔다. 예시 운영 시작값: 연결별 128KiB 또는 512 프레임 중 먼저 도달한 쪽; 서버 전체 SEND 대기 총량에도 상한. 이는 코딩 전에 확정할 **안전 기본값 후보**로, 실제 동접/게임 패턴을 통해 운영 설정값을 정한다.
- 0-copy라는 단어를 함부로 사용하지 않는다. JDK의 direct write도 커널 경계 비용이 존재한다. 이 설계의 1차 효과는 **Java 내부 중간 복사를 줄이고 ByteBuffer 재사용을 늘리는 것**이다.

## 13. 수명·종료·재접속 상태 머신

    ACCEPTED -> HANDSHAKING -> ACTIVE -> DRAINING -> CLOSING -> CLOSED

종료가 시작되면 신규 SEND 접수/READ 재등록을 중단한다. Graceful close는 기한 내 이미 수용한 필수 SEND의 완료를 기다리되 완료 워커를 블로킹하지 않는다. 기한 초과·오류는 강제 종료로 귀결한다.

- socket generation 증가를 통해 **이전 연결의 늦은 완료**가 새 세션 상태를 변경하지 못하게 한다.
- READ 1건/WRITE 1건의 완료/실패를 명시적으로 기록한다.
- JDK write() 호출 자체가 예외를 던진 경우에도 in-flight 소유권·풀 Lease를 정확히 회수한다.
- close()가 시작됐어도 이미 제출한 READ/WRITE의 핸들러가 늦게 실행될 수 있다. **완료 핸들러가 마지막 Lease를 해제하기 전 게임 세션 삭제를 확정하지 않는다.**
- Native 코드의 socket AddRef 및 이유별 참조를 Java에서 그대로 복제할 필요는 없지만, **논리 in-flight count와 completion gate**는 반드시 구현한다.
- 모든 게임 cleanup은 기존 CIO 소유 실행 경계에서 한 번만 실행한다.
- 연결 처리·비정상 종료·중복 close·재접속·shutdown은 동일한 상태 전이 규칙을 사용한다.
- 공용 CIO Timing Wheel 기반 deadline을 유지하되 세대 번호를 확인해 과거 연결 timer가 새 연결을 닫지 못하도록 한다.

## 14. 인증·보안·프로토콜 방어 정책 보존

현재 Native CSocket에서 보이는 계정/캐릭터/MAC identity·user state·deadline API와 CUserSocket opcode/rate/filter 정책을 Java로 대체해야 한다.

- 로그인 전 및 게임 세션 접수 제한, 동일 계정 중복 접속, MacIdentity, 핸드셰이크 타임아웃, 활성 접속 탐지를 기존 의미대로 유지.
- 2바이트 unsigned length 검증: 0/1/길이 초과/음수 해석 방지.
- READ 버퍼 누적 공격·중간 헤더만 보내고 멈추는 클라이언트·매우 빠른 opcode 반복 방어.
- 대량 송신을 유발하는 클라이언트 때문에 전역 풀이 소진될 경우 연결별 제한 먼저 적용.
- 예외 로그는 정상 종료에서 스택 트레이스 폭주를 일으키지 않도록 제한하고, 실제 프로토콜 오류는 계수화한다.
- Native API를 없앨 때 **보안 검증을 조용히 비활성화하는 임시 stub은 금지**한다.

## 15. 운영 단계의 정확한 스레드 구조

    ListenThread (platform thread x1)
        <-- ACCEPT 단일 pending Future 대기 / 입구 정원·IP 검증
        <-- 로그인 DB·게임 로직 금지

    AsynchronousChannelGroup
        worker-0  <-- 여러 소켓의 ACCEPT 완료/READ/WRITE 완료
        worker-1  <-- 여러 소켓의 ACCEPT/READ/WRITE 완료
        ...
        worker-N  <-- 여러 소켓의 ACCEPT/READ/WRITE 완료

    기존 CIO 샤드
        shard-0   <-- 게임 상태 실행
        shard-1   <-- 게임 상태 실행
        ...
    기존 CIO Timing Wheel
        <-- 네트워크 deadline 이벤트 연결

- 네트워크는 **ListenThread 1개 + 고정 수의 JDK I/O 완료 워커 여러 개**로 운영한다. 5,000명에게 스레드 5,000개를 생성하지 않는다.
- ListenThread는 접속 입구 전담이며 로그인 인증/DB/게임 상태 변경을 실행하지 않는다. 클라이언트의 pre-auth 상태와 quota만 짧게 다룬다.
- 네트워크 워커는 다른 유저의 WRITE·READ를 동시에 처리한다.
- 동일 소켓은 필요한 상태에만 논리 실행권을 적용한다. 서로 다른 소켓의 전송은 독립적이다.
- 게임 상태는 기존 shard에서 처리하므로 두 소켓이 같은 게임 객체를 변경할 때도 해당 게임 실행 계약을 따른다.
- **완료 워커에서는 데이터베이스·경로탐색·느린 게임 핸들러를 실행하지 않는다.** 긴 작업으로 네트워크 완료 처리를 막지 않는다.

## 16. 정확한 마이그레이션 파일 범위

| 순서 | 기존 파일 | 의도 |
|---|---|---|
| M1 | core/transport/CIOServer.java | FFM 로딩 제거, 전용 ListenThread 1개 + AsyncGroup/Accept·입장 admission·종료/join 제어 |
| M2 | core/transport/CSocket.java | Native socket handle 제거, AsynchronousSocketChannel wrapper |
| M3 | core/transport/CIOPacketCodec.java | Native SetKey/codec 제거, 순수 Java 프레이밍·암호화 연결 |
| M4 | core/connection/CUserSocket.java | READ/WRITE pumps, Serializer, 512 bounded admission, complete/close |
| M5 | core/connection/CIOBufferPool.java · CIOSlotPool.java | Java I/O 버퍼 소유권·풀 할당 보존 |
| M6 | core/session/CIOClient.java | 기존 sendPacket API 유지, 완료/종료 연결 최소 수정 |
| M7 | core/broadcast/CSharedBroadcast.java | 공용 Java plaintext source, fanout, 수신자별 TX 순서 |
| M8 | codec/Cryptor.java | Native와 동일한 golden-vector 결과 검증 후 확정 |
| M9 | network config/bootstrap, 기존 Timer 접수부 | Native deadline·identity 처리 이관 |

**변경 금지 기본 범위:** NPC, DB/CacheD, CIO 게임 샤드 스케줄 정책, 게임 명령 의미, 기존 패킷 클래스의 바이너리 포맷. 네트워크에서 실제 의존성을 찾을 때만 호출 경계를 최소 변경한다. 불필요한 새 네트워크 워커/큐/스레드 클래스를 만들지 않는다.

완료 후 모든 production Java에서 CTransport.dll, FFM MemorySegment, Linker, Native 코덱 API 의존성이 제거됐는지 **전체 검색·타입 컴파일·런타임 시작 검증**을 수행한다. 서드파티/테스트 자료로 남은 문자열과 실제 production 호출은 구분하여 감사한다.

## 17. 구현 순서 — 임시 이중 경로가 없는 최종 제품을 목표

**1단계: 전송 계약과 암호화 골든 벡터 확보**
- 기존 실제 CTransport wire와 Java Cryptor 결과 비교.
- RECV length·cipher·opcode 및 SEND first-plain 규약 고정.
- Native identity/deadline와 Java 대응 목록 확정.

**2단계: Windows 순수 Java 21 I/O 기반 연결**
- ListenThread 1개 + AsyncGroup + 단일 outstanding ACCEPT + one READ/one WRITE + close-state.
- 새 ACCEPT 먼저 등록, pre-auth quota/IP admission, listener 종료 시 close/join까지 완료.
- CIOClient 기존 호출 계약 유지.
- Windows JDK 21 실제 호스트에서 우선 확인.

**3단계: NC Serializer와 MMOCore SEND Assembler**
- 단일 연결/다중 producer FIFO, ready-prefix, in-flight 1, partial WRITE.
- 16KiB batch + large packet + burst flush + 즉시 write submission.
- 실패/포화/취소/중복 완료에서 Buffer 정확히 회수.

**4단계: RX와 CIO 완전 연결**
- header fragmentation, crypto, opcode, shard ownership, login/session/lifecycle.
- 수신 backpressure와 network deadline 통합.

**5단계: Java 방송 전환**
- 공유 원본·수신자별 암호화·배치/수명 검사.
- AOI/이동/공격/공지/클랜/GM/맵 팀의 회귀 비교.

**6단계: 전체 SRC 통합 및 Native 완전 제거**
- 실제 Java 21 전체 타입 컴파일.
- DLL 없이 Windows에서 실행/로그인/전투/종료.
- Native 호출 경로 제거. 상시 동시 운영할 fallback 엔진 남기지 않음.

## 18. 테스트 매트릭스 — 합격 없으면 운영 배포 금지

| 분야 | 필수 시나리오 | 합격 조건 |
|---|---|---|
| TCP | 연결/끊김/재접속/반쯤 닫힘 | session cleanup 1회 |
| ListenThread/ACCEPT | 1개 전용 입구, 5,000 접속 폭주, backlog·pre-auth 제한, 취소·종료·재시작 | 이중 ACCEPT/누수/중복 ListenThread 0, join 완료 |
| 로그인 입구 | 느린 handshake·분할 프레임·IP 집중 접속·동일 계정 | ListenThread에서 DB/게임 실행 0, pre-auth 슬롯 누수 0 |
| 저할당 | 1/1,024/5,000 연결 이동·공격·SEND·RECV·방송의 JFR 할당 추적 | 패킷별 신규 Runnable/Future/ByteBuffer/임시컬렉션/중간복사 0 또는 명시적 예외 기록 |
| RX split | 길이 헤더 1B+1B, 바디 모든 절단점 | 정확히 원본 1패킷 |
| RX coalesce | 단일 READ에 수십 패킷 | 원래 순서 유지 |
| Crypto | Native 골든 벡터/키 전진 | byte-for-byte 100% |
| TX concurrency | 생산자 2/8/20개가 한 유저에게 동시 SEND | 손실/중복/순서 역전 0 |
| TX partial | 1B/작은 양/0/반복 부분 WRITE | 프레임 완성, 잘못된 재전송 0 |
| TX pressure | 512슬롯 포화, byte cap, global pool 고갈 | 명시적인 거부/종료, deadlock 0 |
| IO callback | write() 즉시 완료/인라인 재진입/연속 완료 | stack recursion 폭주 0 |
| Close race | WRITE+READ+close, quit, restart, 1000회 | 이중 반환 0, leak 0 |
| Buffer | 모든 성공/실패/중단 경로 | acquire == release 정산 |
| Burst | 명령 하나가 SEND 수십~수백개 발생 | FIFO, 병합, 지연 최소화 |
| Broadcast | 1→N, 이동·공격·스킬 교차 | 수신자별 암호화·순서 일치 |
| Game | 로그인→캐선→이동→전투→로그아웃 | 기존 운영 동작과 동등 |
| Long soak | 5,000 동접, 24시간 이상 | 접속/메모리/큐/지연 안정성 |
| Shutdown | 완료 워커 종료·풀 반환·서버 재기동 | 명시적 종료/재시작 100% |

게임 상태 변경과 socket WRITE 성공은 서로 다른 성공 의미를 가진다. **명령 접수 성공, 버퍼 직렬화 성공, 커널 WRITE 완료, 게임 처리 완료를 구분**해 기록한다.

## 19. 성능 측정 방식과 운영 승인 기준

성능을 평가할 때는 **실서버와 동일한 Windows·동일 JVM·동일 패킷·동일 클라이언트 부하**를 사용한다.

필수 측정:
- 1연결, 16연결, 1,024연결, 5,000연결
- window=1 짧은 왕복과 여러 outstanding request
- 작은 이동·공격 패킷, 256B/1KiB/4KiB 혼합, 16KiB/64KiB 대형
- fanout 10/100/1000명 및 대규모 이동 방송
- RECV/s, SEND/s, 총 Mbps, CPU/packet 또는 ns/packet, 평균/p95/p99/p99.9
- direct memory actual/peak, heap retained, allocation/op 및 count/op, GC pause, pending byte/frame, READ paused 시간
- ListenThread CPU, ACCEPT/s, admission 처리시간, pre-auth slots/bytes, 로그인 거부·타임아웃·backlog 압력
- JFR 할당 stack top-N, 호출 위치별 생성량·보유량, pool miss, fallback 생성·복제 횟수
- 연결별 stalled-write time, write-submit/completion count, 평균 packets/write, buffer-pool exhaustion
- 실제 Windows Process Explorer/ETW/JFR 계측으로 코어 핫패스 분석

**운영 승인 게이트**
1. Native DLL·FFM이 없어도 Windows 로그인·게임 플레이가 정상 완료.
2. 모든 프로토콜 골든 벡터 일치.
3. 패킷·암호화·게임 실행 순서의 회귀 오류 0.
4. 5,000 동접에서 지속적인 큐 증가·버퍼 누수·전체 서버 교착·핸들러 정체 없음.
5. 운영 부하에서 CPU/packet과 p99 latency를 기존 엔진과 비교해 목표 대비 성능이 확인됨.
6. graceful/forced close, Windows 작업 종료, 재시작 후에도 자원 회수 검증 완료.

**참고:** 10,000 동접, 초당 수십만 패킷, 특정 p99 수치는 설계만으로 달성 완료가 아니다. 해당 수치는 실측 완료 후 보고서에 기록한다.

## 20. 구현자가 놓치면 안 되는 반례

1. **연결별 FIFO와 실제 WRITE 순서는 다르다.** enqueue 순번이 올바르더라도 암호화와 WRITE 시작을 owner 밖에서 하면 순서가 깨진다.
2. **같은 스레드라고 재진입이 안전한 것은 아니다.** 예약된 오래된 슬롯을 뛰어넘으면 FIFO가 깨진다.
3. **완료 핸들러는 즉시 호출될 수 있다.** recursive write chaining은 stack overflow 위험이 있다.
4. **JDK에 넘긴 ByteBuffer는 완료까지 사용 중이다.** 새 패킷을 병합하겠다고 그 버퍼를 덮어쓰면 데이터가 깨진다.
5. **부분 전송과 패킷 경계는 별개다.** write completion이 프레임 하나를 완료한다는 뜻이 아니다.
6. **암호화 뒤 포화 드롭은 위험하다.** 롤링 키가 이미 전진했으므로 후속 패킷 복호화가 깨진다.
7. **모든 SEND를 Eager 처리하면 병합률이 낮아진다.** 기존 burst scope와 진행 중 WRITE 동안의 batch를 활용한다.
8. **RX와 TX를 소켓 전체 대형 락 하나로 묶으면 병렬 완료가 의미 없어질 수 있다.** 상태별 소유권을 분리한다.
9. **네트워크 완료 워커에서 게임 락을 오래 보유하면 전체 접속이 지연된다.** 게임 상태 변경은 CIO 실행부.
10. **Native 코드 삭제와 기능 삭제를 혼동하면 안 된다.** identity, deadline, 로그인/보안 필터도 순수 Java로 이동한다.
11. **POOL 부재 시 heap fallback을 무제한 생성하면 안정성이 무너진다.** 자원 상한과 명시적 실패 경로를 유지한다.
12. **운영 중 이중 네트워크 엔진은 불필요하다.** 이관 검증은 별도 테스트에서 수행하고 배포본에는 최종 Java 경로 하나만 남긴다.

## 21. 최종 의사결정

**Windows Java 21 NIO.2 기반 멀티스레드 완료 모델을 제품 기본 전송으로 확정한다.**

성능 설계의 우선순위:
1. **정확성:** NC식 연결별 Serializer/순서 보장, rolling cipher, close/lifetime.
2. **핫패스:** PyroNet의 지연 없는 첫 WRITE 제출, 네트워크 완료 처리 단축.
3. **처리량:** MMOCore의 작은 패킷 일괄 병합, 16KiB Assembly, 부분 송신 보존.
4. **메모리:** **저수준 데이터 지향·핫패스 불필요 객체 생성 제거**를 설계 필수조건으로 두고, 기존 RKern CIOBufferPool의 bounded lazy Lease·재사용을 보존.
5. **게임 호환:** CIOClient API, CIO 샤드, Timing Wheel, 브로드캐스트와 게임 프로토콜을 유지.

이 조합은 **네트워크 전체는 다중 완료 스레드로 병렬 실행하고, 각 소켓에 필요한 작업만 순서대로 실행하는 구조**다. NC의 실행 철학에 MMOCore의 MMO 패킷 최적화와 PyroNet의 빠른 송신 시작 정책을 결합한다.

최종 서버는 Native 코드를 유지한 개선판이 아니라, **CTransport 통신 계층을 완전히 제거한 순수 Java 21 Windows MMORPG 네트워크 엔진**이어야 한다.

---



## 22. 운영 필수: NC식 전용 ListenThread / 로그인 진입부 (2026-11-17 추가)

### 22.1 기본 원칙 및 실제 스레드 구성

**TCP 접속의 입구는 전용 ListenThread(플랫폼 스레드) 1개로 구현한다.** NC의 네트워크 입구 역할 분리 철학을 따르되, 분석 기록에 없는 NC 전용 스레드 이름·내부 구현을 실제 확인 사실로 단정하지 않는다.

    [Windows TCP listen socket]
               |
               v
       [ListenThread x 1]
         - 단일 pending ACCEPT
         - 빠른 admission/quota/IP 검사
         - NEXT ACCEPT 먼저 등록
               |
               v
       [JDK 21 AsyncChannelGroup]
       completion worker 1 ... N
         - READ / WRITE completions
         - length framing / Java codec
               |
               v
       [기존 CIO 소유 shard]
         - 로그인 인증 결과와 게임 상태 변경
         - 캐릭터 입장, 이동, 공격, 스킬

**스레드 소유권과 수행 범위**

1. 서버 부트스트랩은 기존 CIOServer 계층에서 ListenThread를 단 1개 생성한다. 개별 클라이언트마다 Thread/Executor/Timer/Worker를 생성하지 않는다.
2. Java 21에서 비동기 소켓을 유지하면서 입구를 분리하기 위해, 전용 ListenThread가 AsynchronousServerSocketChannel.accept()의 Future.get()을 대기한다. 이 blocking은 **입구 스레드에서만 허용**된다. READ/WRITE 완료 워커에서는 get(), sleep(), 대기성 DB 호출이 금지된다.
3. ACCEPT는 **동시에 1건만 outstanding**으로 등록한다. 완료되면 다음 ACCEPT를 재등록한 뒤, 이전 accepted channel의 짧은 admission을 수행한다. ACCEPT 등록 간의 빈 구간을 줄이고 admission이 길어지는 설계를 막는다.
4. Accepted AsynchronousSocketChannel은 리슨 소켓과 동일한 AsyncChannelGroup에 소속된다. ACCEPT 소켓을 별도 async 그룹으로 이전시키거나, SocketChannel을 AsynchronousSocketChannel로 변환하는 비표준/native 방식은 금지한다.
5. ListenThread는 connection quota, pre-auth quota, 빠른 IP/접속 제한, 세션 세대 번호/기본 상태, 최초 READ 등록까지만 수행한다. **DB 로그인 인증·DNS 역조회·패킷 암복호화 본처리·게임 이벤트·큰 메모리 할당·블로킹 로그는 수행하지 않는다.**
6. 로그인 인증·계정/캐릭터 상태 변경은 기존 프로토콜 처리 및 CIO 실행 소유권을 그대로 따른다. pre-auth → authenticated → active 승격 상태는 연결별 정확히 한 번만 처리한다.
7. 서버 종료 시 리슨 채널을 닫아 pending accept().get()을 해제하고 서버 종료 제어 스레드가 ListenThread를 join한다. 이후 활성 소켓의 READ/WRITE in-flight 해소 및 async 그룹 종료를 수행한다. 모든 오류 경로에서 busy-spin과 listener 중복 생성은 금지한다.

**JDK 제약을 정확히 반영:** AsynchronousServerSocketChannel의 pending ACCEPT는 하나이고, CompletionHandler는 비동기 채널 그룹 워커에서 실행된다. 여기서 말하는 ListenThread는 **완료 워커가 아니라 ACCEPT를 기다리고 입구를 통제하는 별도 제어 스레드**다. 따라서 실제 입구 스레드라는 요구와 NIO.2/IOCP 멀티 완료 모델이 공존한다.

참조: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/AsynchronousServerSocketChannel.html 및 https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/AsynchronousChannelGroup.html

### 22.2 로그인 입구의 메모리·공격 방어

- 입구에서는 pre-auth 세션 수, 계정 승격 대기량, 연결별 초기 버퍼 바이트를 제한한다. 대기 중인 소켓마다 대형 버퍼나 512개 객체를 미리 잡지 않는다.
- 느린/부분 핸드셰이크, 첫 패킷 없이 정지한 연결, IP당 과도한 접속 시도, 입구 정원 초과를 기존 정책과 호환되게 처리한다. 기존 CIO Timing Wheel의 deadline을 재사용한다.
- 거부/취소/중단 시 연결 상태, 버퍼 Lease, admission counter를 **정확히 한 번만 회수**한다.
- 로그인 때문에 기존 게임 네트워크의 READ/WRITE가 멎지 않도록 ListenThread의 CPU 비용 및 admission 최대 처리시간을 운영 지표로 계측한다.
- 1,000/5,000 동시 진입·퇴장·재접속 폭주에서도 I/O 완료 워커가 listener 대기나 인증 DB의 영향을 직접 받지 않게 한다.

### 22.3 ListenThread 합격 조건

- 실행 중 ListenThread는 정확히 한 개; restart/close 후 유령 listen thread 0.
- 단일 outstanding accept, 중복 accept 예외 0, 정상 수락 소켓 유실 0.
- ACCEPT 중 interrupt, listen close, 그룹 shutdown, 로그인 실패, pre-auth 초과, 통신 오류를 모두 검사.
- 프레임·암호화·캐릭터 입장 전후 기존 연결 규약/세션 ID/차단·중복 로그인 규칙 유지.

## 23. 운영 필수: 최상급 저수준 Java 21 네트워크 / 객체 생성 최소화 (2026-11-17 추가)

### 23.1 최우선 개발 철학

**'객체지향적으로 보기 좋은 코드'보다 데이터 배치, cache locality, CPU/packet, allocation rate, retained heap, Direct memory peak, p99 지연과 운영 안정성을 우선한다.** 순수 Java 21 범위에서 가능한 가장 저수준의 데이터 지향 설계를 선택한다.

- **RECV 패킷 한 건 / SEND 패킷 한 건 / WRITE 완료 한 건 / 브로드캐스트 수신자 한 명마다 추가 객체를 만들지 않는 것**을 핫패스 기본 규약으로 한다.
- Primitive/int/long/boolean 필드, flat array, bounded primitive ring, 연결별 재사용 completion handler, buffer pool, 단일 owner pump를 먼저 사용한다.
- 핫패스에서는 불필요한 new, autoboxing, lambda capture, Stream/Iterator, 임시 HashMap/List, Runnable·Future·CompletableFuture, ByteBuffer.wrap/duplicate/slice, 임시 byte[], Packet wrapper 계층 추가를 금지한다.
- **Accept Future·새 Socket Channel·실제 세션 생성 객체**는 접속 수립에서 발생하는 콜드패스 비용이다. API에 필수인 객체까지 0이라고 주장하지 않는다. 세션 생성 시 장수명 상태 객체는 필요한 만큼만 만들고 즉시 버릴 객체는 피한다.
- 512슬롯은 NC 실행 규칙을 위한 정책상의 한계이지 접속 5,000개 모두에 512개 작업 객체를 만들어두라는 뜻이 아니다. 평시의 연결별 필드를 최소화하고 경합 시 재사용 가능한 슬롯/primitive 구조를 쓴다.
- 더 많은 메모리를 사용해도 **실제 성능 향상이 뚜렷한 경우**는 허용한다. 단, 무제한 생성/큐 팽창/불필요한 상시 대형 객체는 금지한다.
- 실패한 실험용 JVM 스위치/중복 코드 경로를 운영 코드에 남기지 않고 가장 검증된 기본 경로 하나로 유지한다.

### 23.2 층별 구현 규약

| 계층 | 금지할 저효율 패턴 | 채택할 기본 패턴 |
|---|---|---|
| Listen/ACCEPT | 연결마다 새 worker/thread/task graph | ListenThread 하나 + lazy, bounded pre-auth |
| READ | 매 완료 새 Handler, ByteBuffer, 복제 배열 | 연결별 재사용 Handler + pooled RX + primitive cursor/length |
| 프레이밍/복호화 | 매 패킷 임시 객체·복사·박싱 | 직접 바이트 범위 처리 + 독립 rolling cipher 상태 |
| Serializer | 작업당 Runnable/Future/Node 객체 | primitive ticket, reuse slots, FIFO linearization |
| SEND | heap 패킷 → 임시 wrapper → Direct copy | 가능하면 Direct assembly에 바로 작성, 재사용 포인터 |
| MMOCore 병합 | 매 패킷 복수 중간 복사·버퍼 할당 | 16KiB 재사용 batch + 부분 WRITE 상태 유지 |
| Gathering | 매 호출 ByteBuffer[]와 view 생성 | 기존 완성 버퍼와 재사용 가능한 view 배열 |
| WRITE 완료 | callback마다 새 Closure·재귀 체인 | 재사용 Handler, bounded loop pump, in-flight 최대 1 |
| 방송 | 수신자마다 원본 전체 재생성 | immutable 공유 원본 + 수신자별 암호화/병합 |
| CLOSE | 지연 작업별 별도 보조 객체·큐 | generation과 명시적 종료 상태, lease once-release |

### 23.3 버퍼·메모리 수명

- 4KiB RX / 16KiB TX / 필요시 64KiB 대형 풀의 **lazy bounded lease** 정책을 기본으로 한다. 접속마다 큰 RX·TX·512슬롯을 상시 예약하지 않는다.
- 패킷별 헤더/암호화 상태를 정확하게 유지하면서 필요하지 않은 임시 byte 배열 복사를 줄인다. 게임 패킷 객체 생성 API 자체는 기존 호출을 우선 보존하고, 신규 네트워크 계층이 만들어내는 추가 할당부터 제거한다.
- JDK에 WRITE 제출한 ByteBuffer의 position/limit/내용은 완료 이전까지 누구도 변경하지 않는다. 완료 후 잔여 데이터 처리, close race, 세션 generation, pool lease 반환을 정확히 한 번 처리한다.
- Direct ByteBuffer도 Java wrapper와 Direct memory를 점유한다. 풀 크기를 키우기 전 **활성 수/보유 피크/CPU/packet**을 계측한다.
- Direct-buffer staging을 한 단계 줄일 때 생기는 락·경쟁·부가 복사·핸들러 호출 비용까지 합산하여 결정한다.

### 23.4 객관적 성능·안정성 합격 기준

1. **핫패스 할당 감사:** JFR/프로파일링으로 READ, SEND, 암호화, broadcast, completion별 allocations/op, bytes/op, retained heap을 분리해서 측정한다. 새 Runnable/Future/임시 컬렉션/ByteBuffer/중간 복사 발견 시 제거하거나 불가피한 이유와 비용을 보고서에 명시한다.
2. **1,024/5,000 접속 메모리 검증:** 유휴/이동·공격/송신 폭주/접속 폭주 단계에서 총 힙, 세션당 retained heap, Direct memory peak, pool miss, GC pause, 입구 pre-auth 메모리 상한을 검사한다.
3. **CPU와 공정성:** 패킷당 CPU·시스템 호출·암복호화/병합 비용, 워커 경합, tail latency, false sharing, busy loop 여부를 측정한다.
4. **정확성:** 로그인/입장/재접속, 소켓별 FIFO, RX 프레이밍, rolling crypto, 부분 WRITE, 연속 SEND, buffer retain/release, shutdown/join을 회귀 검증한다.
5. **최종 산출물:** Windows 실서비스용 **Java 21 하나의 정규 네트워크 구현**, 사용자 작성 Native DLL/FFM/JNI/RIO 제거, 기존 CIO 게임 실행 구조·게임 프로토콜은 보존한다.

**제품 원칙: 단순한 Java 라이브러리가 아니라, NC의 실행 정책 + MMOCore 패킷 병합 + PyroNet 즉시 WRITE + 전용 ListenThread + 저수준 데이터 지향 구현이 결합된 최상급 Windows 순수 Java MMORPG 네트워크 엔진을 목표로 한다.**

---


## 24. 최상위 구현 필수: 최초 제작부터 비용을 제거하는 고성능 설계 (2026-11-17 추가)

> **중요:** 아래는 네트워크가 완성된 뒤 다시 뜯어고치는 후속 최적화 제안이 아니다. **처음 구현할 때부터 반영해야 하는 설계 계약**이다. 기존 0~23장과 상충하는 중복 큐/불필요한 작업 객체 설명이 있으면 이 24장의 비용 절감 실행 경로를 우선한다. Windows 순수 Java 21, 기존 CIO, 프로토콜·보안·수명 보장 조건은 유지한다.

### 24.1 패킷당 비용을 항목별로 줄인다

    CPU(send/packet)
      = admission·FIFO 확정
      + 큐 이동 및 메모리 fence
      + 직렬화/게임 시점 스냅샷
      + 연결별 암호화
      + 바이트 복사
      + WRITE 제출 CPU / 해당 WRITE의 게임 패킷 수
      + WRITE 완료 CPU / 해당 WRITE의 게임 패킷 수

    GC pressure = 앱 신규 객체/packet + JDK 내부 I/O 작업 객체/READ·WRITE
    Resident memory = 연결별 고정 필드 + 활성 RX/TX 버퍼 + 대기 FIFO + JDK 소켓 상태

**목표:** CPU/packet·JFR bytes allocated/packet·queue hops/packet·bytes copied/frame·WRITE submissions/1000 frames·p99 지연·세션당 retained heap을 초기 구현부터 계측한다. WRITE 등록 수 감소가 RTT나 CPU에 자동으로 몇 퍼센트 유리하다는 숫자는 꾸며 쓰지 않는다.

### 24.2 가장 큰 구조 변경: Serializer와 SEND Queue를 하나로 합쳐라

**배제할 계층**

    게임 패킷 → Serializer Queue → SEND Queue → Batch Queue → WRITE Queue → IOCP

**채택할 유일한 실행 경로**

    CIOClient.sendPacket()
           |
     소켓별 admission: 접수 순서/FIFO/바이트 정원 선형화
           |
       +---+-----------------------------+
       |                                 |
      소유자 FREE + 선행 없음           선행 있음 / 다른 생산자 접수 중 /
       |                                WRITE 진행 중
       v                                 |
      직접 TX owner fast path             단일 bounded FIFO
       |                                 |
       +---------------+-----------------+
                       |
                  TX Pump owner
                       |
              Direct 16KiB assembly
              + 연결별 순서 암호화
                       |
               하나의 in-flight WRITE
                       |
               JDK async IOCP completion
                       |
               부분 WRITE 잔여 바이트 우선
               완료되면 다음 FIFO batch

- **송신용 논리 큐는 연결마다 최대 하나.** 큐에 넣기 위해 패킷별 Runnable/Continuation/TaskNode를 만들지 않고 기존 패킷 참조·재사용 슬롯·primitive 메타데이터로 관리한다.
- **비경합 즉시 경로는 큐 경유 0회**를 목표로 한다. 단, 이미 접수한 선행 티켓 또는 암호화 대기 프레임이 있다면 반드시 그걸 먼저 처리한다.
- **admission 선형화와 owner 확보의 동시성 프로토콜이 먼저다.** CAS만 성공했다고 FIFO 보장이 되지 않는다. 순서 예약 후 미게시 슬롯을 후속 producer가 추월할 수 없게 publish sequence/ready prefix를 보장한다.
- 접수 순서는 작은 per-socket admission critical section 하나에서 확정한다. 선택 방식은 짧은 synchronized 또는 안전한 CAS+publish다. 전체 연결을 막는 global SEND lock 금지.
- **락 보호 중 패킷 직렬화·암호화·Direct 복사·비동기 WRITE 등록을 길게 수행하지 않는다.** owner 내부의 bulk work는 다른 producer가 접수 순서만 확정할 수 있도록 분리한다.
- owner는 초당 패킷마다 새 스레드를 뜻하지 않는다. 게임 호출자 또는 비동기 완료 워커 중 현재 해당 연결 TX 상태의 실행권을 확보한 하나의 실행 문맥이다.
- 완료 중 다시 WRITE 등록 시 callback inline 재진입과 재귀 stack 폭주를 막는 단일 wip/owner loop를 사용한다. 다른 네트워크 전용 worker/queue를 추가하지 않는다.

**비용 목표:** 비경합 packet은 추가 SEND 큐 이동 0, 경합/WRITE 대기 packet은 논리 SEND 큐 1개; packet마다 Serializer Lock + Queue Lock + Batch Lock 3단 잠금을 만들지 않는다. 서로 다른 소켓의 송신끼리는 global critical section을 공유하지 않는다.

### 24.3 두 번째: WRITE 요청 횟수를 줄여 JDK 내부 객체 생성까지 감소

**Windows JDK 21 내부에서 확인한 비용:** OpenJDK jdk21u의 WindowsAsynchronousSocketChannelImpl은 implWrite마다 PendingFuture, WriteTask와 단일버퍼용 1칸 ByteBuffer 배열 생성 경로가 있다. implRead도 PendingFuture, ReadTask, 배열 생성 경로가 있다. CompletionHandler 재사용만으로 이 내부 할당은 없어지지 않는다.

참조: https://github.com/openjdk/jdk21u/blob/master/src/java.base/windows/classes/sun/nio/ch/WindowsAsynchronousSocketChannelImpl.java

**처음부터 적용할 구현**
- 서로 다른 게임 패킷을 TCP 스트림 하나로 병합: 각 프레임의 기존 2바이트 길이와 암호문/평문 경계를 유지하면서 최대 약 16KiB의 하나의 WRITE로 제출한다.
- 게임 명령 하나에서 연속 sendPacket이 나오는 **기존 burst 경계**는 명령 종료 시점에서 한 번 flush한다. 그동안 packet마다 즉시 WRITE를 제출하지 않는다. 새 배치용 타이머, Runnable, 큐 금지.
- 유휴 연결의 단일 패킷은 **즉시 WRITE 제출**한다. 병합률을 높이겠다고 1~5ms 인위적 기다림을 두어 첫 패킷 지연을 악화시키지 않는다.
- WRITE가 이미 pending이면 새 패킷은 단일 FIFO에서 다음 batch로 합친다. 이미 kernel에 제출한 flight는 절대로 덮어쓰지 않는다.
- 예시: 32B 프레임 16개가 **이미 한꺼번에 준비**되어 있다면 512B로 병합해 정상적으로 WRITE 16건 대신 1건으로 제출할 수 있다. 부분 송신·실제 생산 간격에 따라 제출 횟수는 달라진다.
- 단일 큰 프레임은 16KiB 한도로 잘라 게임 프레임 규약을 깨지 않는다. 기존 프로토콜의 유효 길이 범위에 따라 64KiB 등 큰 버퍼로 처리한다.
- 기본 소형 경로에서 Gathering WRITE용 ByteBuffer 배열·slice/duplicate를 매번 만들지 않는다. 여러 독립 대형 버퍼가 이미 안정적으로 살아 있는 경우에만 비용을 따져 채택한다.

**측정 지표:** frames/write, bytes/write, JDK writes/1000 frames, WRITE callback/1000 frames, first-frame queue time, CPU/packet, allocated bytes/packet.

### 24.4 세 번째: 패킷을 최종 Direct Assembly에 직접 기록해 중간 복사 제거

**배제**

    packet -> new byte[] -> temp heap ByteBuffer
           -> Direct staging copy -> encrypted copy -> async WRITE

**직접 작성 가능한 packet에 채택**

    producer가 확정한 패킷 데이터
           -> 최종 Direct 16KiB Assembly에 기존 2B header/body 기록
           -> 연결별 순서 암호화 in-place
           -> 동일 버퍼를 immutable flight로 양도
           -> async WRITE

- 게임 객체 상태를 완료 워커가 **나중에 읽어 패킷 내용을 재계산**하는 방식은 금지한다. producer 호출 시점의 snapshot/바이트 또는 불변 primitive verb로 데이터 내용을 보존한다.
- 기존 패킷 serializer가 heap buffer를 반환한다면 한 번의 복사가 불가피할 수 있다. 호환성이 보장된 직접 기록 가능 패킷부터 네트워크 층의 **추가 중간 복사 0회**를 목표로 한다.
- Direct ByteBuffer는 객체도 메모리도 소비한다. packet마다 ByteBuffer.wrap/duplicate/slice 또는 새 byte[]를 만드는 설계를 금지하고 재사용 버퍼 및 primitive cursor를 쓴다.
- **TX 암호화는 소켓 FIFO 순서로 정확히 한 번.** 암호화 후 배치의 임의 패킷만 취소/드롭하거나 연결 키 전진을 되돌리는 방식 금지.
- 버퍼 소유권은 assembly 단계와 in-flight flight 단계를 분리한다. JDK write 호출 이후 flight bytes·position·limit을 완료 전 다른 스레드가 건드리지 않는다.
- 암호화·직접 기록이 게임 호출 스레드에서 너무 길게 이어져 CIO shard를 막는 문제는 owner의 작업 예산으로 제한한다. 해결책으로 패킷별 추가 queue/worker를 만드는 것은 기본 정책이 아니다.

**측정 지표:** copy passes/frame, copied bytes/wire byte, serializer time/packet, codec time/packet, direct pool lease time, retained snapshot bytes.

### 24.5 네 번째: RECV에서 패킷별 객체·불필요한 I/O 제출 제거

    하나의 RX Direct Buffer
          |
      READ 완료
          |
      2바이트 길이 cursor
          |
      완성 패킷 A, B, C ... 를 동일 버퍼에서 순차 해석
          |
      RX 복호화·opcode·기존 CIO enqueue
          |
      부분 프레임 보존 / 압력 확인 / 다음 1개 READ 등록

- READ 완료마다 새 CompletionHandler, 새 ByteBuffer, 임시 List, packet별 byte[] 복제/래퍼를 만들지 않는다.
- 한 READ에 여러 완전한 프레임이 있으면 **각 프레임마다 다시 READ를 등록하지 않고** 같은 parser cursor에서 순서대로 처리한다. 하나의 소켓에는 미완료 READ 최대 한 개.
- 메시지 길이 헤더 1B/2B 분할, 프레임 완성 직전 수신 중단, 여러 패킷 합쳐진 READ, 대형 패킷, 암호화 전진을 모두 parser의 primitive 상태로 관리한다.
- CIO에 패킷 작업을 넘길 때는 **수신 버퍼의 바이트가 다음 READ에 덮어써지기 전에** 필요한 데이터 소유권을 확정한다. 무조건 0-copy라고 주장하거나 완료 전 풀에 반환하면 데이터가 깨진다.
- 완료 워커 하나가 특정 소켓의 수천 프레임을 무한 처리하지 않도록 처리 예산을 두되, 미완료·미처리 바이트를 조용히 버리지 않는다. 기존 CIO 접수 압력과 READ pause/resume 계약을 보존한다.

**측정 지표:** frames/read, read registrations/1000 frames, RX copy bytes/frame, RX allocations/frame, decode CPU/frame, shard handoffs/frame.

### 24.6 다섯 번째: 5,000 연결의 메모리 상주비용 절감

- **READ가 pending인 연결에는 살아 있는 RX 버퍼가 필요하다.** 5,000 × 4KiB = 약 19.5MiB의 RX 순수 데이터 영역이므로 이를 0으로 산정하지 않는다. ByteBuffer 객체, 세션 필드, JDK 소켓 메모리는 별도다.
- 유휴 연결 전원에게 **16KiB TX assembly + 16KiB flight + 512 슬롯 객체**를 상시 배정하지 않는다. TX는 송신 시점에 lease, 큰 RX는 필요할 때 확대, 경합 ring은 lazy allocation/reuse.
- 단기 할당을 줄이겠다고 5,000개 연결이 영구적으로 거대한 대형 배열을 붙잡는 것도 금지한다. pool miss, retained heap, GC, CPU의 실제 차이를 본다.
- 고부하 송신에서 한 전역 synchronized buffer pool이 병목이 되지 않도록 기존 CIOBufferPool의 lease 모델을 유지하면서 필요한 경우 샤드 지역 free-slot/재사용 구조로 메모리 경쟁을 줄인다. 다른 소켓/완료 워커가 반환하는 cross-thread recycle 안전성이 우선이다.
- 대기 큐는 연결별 프레임 수와 바이트 수, 서버 총량에 상한이 있다. 버퍼 부족 시 무제한 new로 우회하지 않는다. **암호화 전 admission**에서 적절한 reject/close 정책으로 누적 메모리를 제한한다.
- long-lived 연결 필드는 primitive와 재사용 handler 중심으로 최소화하고, 박싱/임시 Collection/람다 capture/packet별 Runnable을 피한다.

**측정 지표:** idle retained heap/client, active heap/client, direct memory actual/peak, pending bytes/client, pool miss/s, GC bytes/packet.

### 24.7 여섯 번째: MMORPG 방송 fanout 비용 절감

    한 번만 생성한 immutable 방송 원본
              |
      기존 AOI 수신자 그대로 순회
              |
      각 수신자의 단일 TX FIFO에 접수
              |
      해당 수신자의 Direct assembly로 바이트 기록
      + 수신자별 독립 TX rolling cipher
              |
      각 연결의 다음 WRITE batch

- 동일 이동·공격·스킬 원본을 수신자 N명에게 보내면서 packet serializer를 N번 실행하는 경로 제거. immutable 공통 원본은 **이벤트당 1회 생성/작성** 목표.
- **수신자별 암호화는 제거할 수 없다.** 대신 암호화된 결과를 그 연결의 최종 Direct assembly에 기록해 추가 임시 버퍼·복사를 줄인다.
- 기존 AOI, 자신 포함 여부, GM/clan/party, optional/mandatory 정책을 바꾸지 않는다. 대량 fanout의 비용을 줄인다는 명목으로 새로운 broadcaster thread/queue를 추가하지 않는다.
- 수신자가 공유 원본을 사용하는 동안 그 데이터를 회수·수정하지 않는다. pool/lease/refcount의 수명 보장을 지킨다.
- 한 CIO shard가 거대 fanout을 실행하며 다른 게임 작업을 오랫동안 막지 않도록 기존 게임 실행 경계를 유지하면서 fanout CPU/event 및 처리 예산을 관찰한다.

**측정 지표:** common packet serializations/event(목표 1), recipient additional allocations, bytes copied/recipient, fanout CPU/event, frames/write.

### 24.8 일곱 번째: I/O 워커·ListenThread·타이머 비용 최적화

- ListenThread는 **1개**, TCP Accept/admission만 수행. 게임 READ/SEND/암호화/DB를 맡기지 않는다.
- JDK AsyncChannelGroup의 고정 완료 워커 여러 개가 모든 소켓 완료 이벤트를 병렬 처리한다. 소켓별 스레드·보조 packet-worker·추가 command queue를 만들지 않는다.
- 완료 워커에서 DB, 게임 상태 락 장시간 점유, blocking get(), sleep(), 폭발적인 log를 금지한다. 긴 게임 처리는 기존 CIO 소유 shard에서 처리한다.
- 접속 타임아웃과 네트워크 deadline은 기존 CIO Timing Wheel을 재사용한다. 개별 소켓의 packet마다 새 ScheduledFuture를 만들지 않는다.
- CPU를 덜 쓴다는 명목으로 NIO.2 기본 경로에 무한 spin, 대기 중 SELECT polling, Thread.yield를 추가하지 않는다.
- owner 수행 예산과 완료 콜백의 재진입 방지로 **한 소켓이 모든 완료 워커 시간을 독점**하는 일을 막는다.

### 24.9 구현 전/후 '비용 제거표' (구현자가 승인받아야 할 항목)

| 위치 | 비효율적인 설계 | **처음부터 적용할 고속 경로** | 확인 지표 |
|---|---|---|---|
| TX 계층 | Serializer Queue→SEND Queue→Batch Queue | **TX Pump 1개, SEND FIFO 1개** | packet당 queue hop |
| 비경합 SEND | 매번 FIFO 노드/Task 생성 | 선행 작업 없으면 owner 직접 실행, 추가 큐 **0회** | app allocation/send |
| 경합 순서 | 큐락+Serializer락+WRITE락 연쇄 | 접수 선형화 짧게 1곳 + owner bulk work 무락 | CAS retries/packet, lock hold |
| packet 직렬화 | heap 중간 배열→Direct 중간 버퍼→최종 | 안정적인 packet은 **최종 Direct에 바로 기록** | copied bytes/frame |
| WRITE 등록 | 16프레임에 16 WRITE | 이미 모인 16프레임/512B라면 **1 WRITE** | frames/write |
| JDK 내부 할당 | READ/WRITE를 패킷마다 잦게 호출 | batched WRITE·연속 RX 해석으로 I/O 등록 수 감소 | JFR JDK allocation/packet |
| TX 메모리 | 유휴 클라이언트마다 2×16KiB 예약 | 송신 활성 연결 중심 lazy lease | idle TX memory/client |
| RX 메모리 | 유휴 연결마다 64KiB 예약 | READ pending마다 기본 4KiB, 대형은 필요 시 승격 | RX retained/client |
| 방송 | recipient별 공통 원본 재생성 | 공통 본문 1회, 수신자별 필수 암호화만 | serializations/broadcast |
| 실행 객체 | Packet마다 new Runnable/Closure/List | 재사용 Handler·slot·primitive state | alloc count/op |
| 버퍼 수명 | 비동기 완료 전에 재사용/반환 | flight IN_FLIGHT 기간 불변, once-release | pool leak/double-free |
| 운영 스레드 | 소켓별 쓰레드·다중 dispatcher | ListenThread 1 + JDK 완료 워커 + 기존 CIO | thread count/context switches |

**표의 숫자는 실행 구조의 연산 수 목표이지 미측정 성능 개선률이 아니다.** 특히 신규 JDK I/O task/future/bytebuffer 배열과 기존 게임 packet 생성까지 0이라고 주장하지 않는다.

### 24.10 최초 구현 완료 조건 — 나중에 또 수정하지 않도록

이 24장의 최적화는 **개발 마지막 단계로 미루지 않는다.** 다음을 최초 제품 구현의 Definition of Done에 넣는다.

1. CIOServer: ListenThread 1개, AsyncChannelGroup 다중 완료 워커. Native/FFM 제거, 접속 admission/종료 정확성.
2. CUserSocket: **단일 TX Pump + 논리 FIFO 1개 + owner + 16KiB flight/assembly**. 경합이 없는 정상 송신에서 불필요한 추가 queue 0.
3. CIOPacketCodec/Cryptor: 기존 wire 규약 완전 일치, 불필요한 중간 byte[]/ByteBuffer 래퍼 0 목표.
4. CIOBufferPool: 4/16/64KiB bounded lazy lease, 정확한 수명, buffer copy 최소화.
5. CIOClient.sendPacket: 기존 동작·snapshot 보존, burst scope와 Eager Write 결합, 기존 CIO 소유권 변경 없음.
6. CSharedBroadcast: 공유 원본 1회, 수신자별 순서 암호화·병합, 부하 압력과 pool 수명 보존.
7. 계측: allocations/packet, CPU/packet, frames/write, queue hops/packet, bytes copied/frame, pool retained/peak, p99 기록.
8. 회귀: 5,000연결, 다중 SEND producer, partial WRITE, READ 1B split/여러 프레임, 로그인 재진입, 방송 경쟁, close race, 타임아웃, pool 1회 반환.

**최종 판정:** 압도적인 네트워크를 목표로 한다면 패킷 한 건을 처리하는 동안 **중복 큐·임시 객체·중간 복사·불필요한 WRITE 등록·글로벌 락을 없애는 것이 첫 구현의 필수 사양**이다. 기존 NC FIFO·L1 프로토콜·CIO 게임 실행 순서·로그인·수명 안정성은 절대 희생하지 않는다.

---


## 25. 추가 필수: 24장만으로는 부족하다 — 게임 패킷 처리·CPU 캐시·폭주·운영 비용 제거 (2026-11-17 보강)

> **필수 제품 구현 사양.** 24장의 송신 큐 통합·WRITE 병합·Direct 복사 감소에 더해, 아래 항목까지 **초기부터 구현해야 완성형 저수준 MMORPG 네트워크**가 된다. 25장은 별도 실험 엔진/토글/새 worker를 만들자는 내용이 아니다. 실제 RKern의 수신→CIO 경계, 패킷 코덱·소켓 등록·풀 재사용·연결 생명주기를 기준으로 한다.

### 25.1 1순위: RECV에서 기존 CIO까지 중복 실행·큐 이동 금지

최신 RKern에는 이미 CUserSocket의 네이티브 완료 수신 상태(nativePacketPhase), enqueuePacketImmediate(), CIOThreadCallback.PacketTask가 있으며, 게임 상태는 기존 CIO shard가 소유한다.

**초기 구현의 정확한 요구 사항**

    Windows IOCP READ completion
          |
      CUserSocket RX state
          |
      2B frame + per-session decrypt + opcode
          |
      +-- 이미 네트워크 완료 문맥에서 안전한 짧은 프로토콜 작업
      |
      +-- 게임/세션 소유 실행 작업: 기존 CIO enqueuePacketImmediate()
          (불필요한 NetworkDispatcher, DecodeQueue, PacketTaskQueue 추가 금지)

- 연결별 RX logical sequence를 유지한다. 먼저 온 명령의 실제 처리·게임 소유권 검증 이전에 뒤 패킷을 무단으로 앞질러 실행하지 않는다.
- CUserSocket이 이미 CIOThreadCallback.PacketTask를 구현한 사실을 활용해 **패킷마다 새 Runnable/PacketEvent 객체를 만들지 않는다.** 단, 단일 재사용 PacketTask가 미완료인 상태에서 다음 READ가 같은 필드를 덮어쓰면 안 된다. 기존 단일 pending ingress 계약을 보존하거나 명시적인 bounded owned slot으로 확장한다.
- CIO에 전달할 바이트는 READ 재등록 및 Buffer Pool 반환과 경쟁하지 않도록 **수명 인계(lease/ownership)**를 확정한다. zero-copy가 불가능한 게임 패킷만 필요한 길이만큼 copy한다.
- 단순 packet decode 때문에 게임 서버의 새 worker에 enqueue 후 다시 기존 CIO에 enqueue하는 **이중 handoff**를 금지한다.
- 기존 opcode별 세션 소유 분기와 연결 검증을 재사용하며, 호출 경계가 바뀌어도 NPC·CacheD·게임 소유 샤드는 수정하지 않는다.

**비용 절감 대상:** 패킷당 추가 작업 객체 1개, 네트워크→게임 중복 큐 한 번, 바이트 배열 한 번의 중간 복사, 중복 소유권 검사. 단, 실제로 원본 경로가 이미 이를 피한다면 **그 최적화를 유지**하고 다시 만들지 않는다.

**측정:** inbound queue handoffs/frame, allocations/frame, pending RX bytes/session, CIO admission latency, per-socket sequence violations=0.

### 25.2 2순위: 디코더·opcode 분기·암복호화 CPU 비용 절감

- 긴 객체 그래프/가상 호출/HashMap lookup/리플렉션 대신 opcode → 기존 핸들러 경로의 정적 분기/사전 확정된 dispatch table을 이용한다. 현재 opcode 허용·세션 검사를 먼저 확인하고 보안 분기를 제거하지 않는다.
- 길이 헤더, opcode, packet cursor는 primitive 필드로 유지하고 매 프레임 임시 ReadBuffer/ByteBuffer wrapper·Iterator를 만들지 않는다.
- Cryptor.java에는 이미 rolling cipher, opcode preview, 복호화의 long 단위 처리 경로가 있다. 이를 무턱대고 폐기하지 않고 **JDK 21 JIT가 최적화하기 쉬운 단일 바이트 루프/8바이트 fast path**를 정확한 골든 벡터로 검증한다.
- opcode/skill 미리보기 때문에 **같은 프레임 전체를 두 번 복호화하거나 두 번 복사하지 않는다.** 미리보기는 key 비전진, 실제 decrypt는 정확히 한 번 key 발전.
- 기존 프로토콜의 첫 평문, 4바이트 cipher prefix, 길이와 endian, 여러 패킷이 한 READ에 함께 들어온 경우의 key progression을 byte-for-byte 유지한다.
- 시스템 시간, 원격 주소 문자열, debug-format, stack trace를 정상 패킷마다 생성·조회하지 않는다. deadline 갱신은 기존 Timing Wheel의 coarse 이벤트/필드와 연동한다.

**비용 절감 대상:** decrypt/프레임 반복 처리, 분기/간접 호출, 시간·로그 비용, 패킷별 임시 객체.

**측정:** cycles/decrypt-byte, ns/decoded packet, key advancement mismatch=0, alloc/frame, opcode hot top-N.

### 25.3 3순위: Connection Registry와 공유 메타데이터의 캐시·원자 비용 절감

현행 CIOServer에는 연결을 찾는 AtomicReferenceArray<CSocket>와 관리용 ConcurrentHashMap이 있다. **관리 자료구조를 READ/WRITE 정상 경로에 중복 조회시키지 않는다.**

- 완료 콜백 attachment에는 그 연결에 이미 귀속된 객체·세대 번호를 보유하고, 매 완료마다 전역 연결 레지스트리를 다시 찾는 조회를 피한다. 기존 참조 수명·세션 세대 검증은 유지한다.
- 연결별 hot fields(RX cursor, TX owner, pending byte, cipher state)와 cold fields(remote address, 로깅·진단·계정 문자열)를 구분한다. 외부 API를 깨뜨리지 않는 범위에서 데이터 접근 지역성을 높인다.
- 전역 패킷 카운터에 frame마다 atomic increment를 하지 않는다. 모니터링은 per-worker/per-shard primitive counter 또는 부담이 작은 누적 방식으로 구현하고, 운영자가 조회할 때 aggregate한다. 기능상 반드시 정확한 quota 카운터와 계측용 근사 카운터를 혼동하지 않는다.
- 각 ByteBuffer에 대해 매번 동기화된 글로벌 인덱스 검색/래퍼 변환을 실행하지 않도록 lease slot/generation을 기존 연결 상태에 간결히 보유한다.
- 이득이 불명확한 volatile 필드·Atomic 객체·전역 락을 새로 추가하지 않는다. 필요 상태는 publication/happens-before가 정확히 성립하는 최소 필드에 한정한다. Java Memory Model 위반으로 stale completion이 살아 있는 buffer를 돌려주는 일이 없어야 한다.
- per-worker counters와 자주 갱신되는 session metadata 사이의 false sharing을 회피하는 배치가 필요한지는 JFR/프로파일로 확인한다. 무작정 padding을 5,000개 세션에 추가하지 않는다.

**비용 절감 대상:** lookup, 캐시 미스, 원자 갱신, 객체 보유량, 경합.

**측정:** connection-registry accesses/READ·WRITE completion, global atomic updates/frame, lock contention, cache misses, heap/client.

### 25.4 4순위: Pool 중앙 병목·경쟁을 없애되 Buffer 수명 보존

- 풀 획득·반환을 모든 I/O 워커가 한 synchronized lock에서 수행하면 병렬화가 무너진다. 기존 CIOBufferPool의 bounded lazy lease를 기본으로 삼아, **유휴/경합 없는 정상 경로에서 지역 free-slot 우선**을 검토한다.
- 소켓 간 풀 반환이 완료 콜백 스레드에서 이루어져도 정확히 한 번만 slot을 반환하도록 owner/generation/in-flight 상태를 유지한다.
- 자주 쓰는 4KiB RX/16KiB TX는 사용량이 높은 크기별로 고정 슬롯을 재사용하고, 64KiB large pool은 큰 프레임에서만 쓴다.
- free-slot을 자주 찾기 위해 매 packet마다 전체 pool 배열을 순회하거나 큰 Bitmap을 반복 스캔하지 않는다. 구현 구조가 실제로 그런 경로를 가진 경우에만 bounded free-index/간결한 할당자 경로로 개선한다.
- Direct ByteBuffer의 오래 살아 있는 Java wrapper와 실제 native direct 영역을 별도로 계측한다. buffer lifetime을 추적하는 참조가 GC 대상 버퍼를 계속 잡고 있는 leak을 피한다.
- **Connection A가 I/O 미완료로 보유 중인 lease를 Connection B에게 조기 반환하는 최적화는 절대 금지한다.**

**측정:** pool acquire/return ns/op, contention, scan steps/acquire, misses/s, retained bytes, double-return=0.

### 25.5 5순위: 폭주·느린 클라이언트·WRITE 정체 때 전체 서버 보호

평상시 throughput만 높아도 특정 연결이 SEND 대기 메모리를 무한히 소모하면 5,000동접 실서비스 엔진이 아니다.

- pre-auth admission, 연결별 TX pending frames/bytes, 서버 전체 pending bytes, RX→CIO backlog, buffer pool 용량을 **서로 연결된 제한 정책**으로 설계한다.
- 부하가 증가할 때 WRITE를 무조건 더 제출하거나 큐에 더 쌓지 않는다. 연결별 in-flight WRITE 1개를 지키고, 해당 연결의 과도한 적체를 제한한다.
- READ pause/resume은 현행 CIO 압력/타이머 규칙을 존중한다. WRITE backlog 때문에 READ를 pause해야 할 경우 프로토콜 응답·재인증·로그아웃/타임아웃 등 필수 제어 절차가 어떻게 진행되는지 명시한다.
- optional broadcast는 **접수 및 암호화 전**에만 정책에 따라 제외 가능하다. 암호화가 시작된 송신 프레임을 일부만 없애면서 연결을 유지하는 방식 금지.
- 풀 용량이 부족하면 글로벌 새 byte[]/Direct Buffer 무제한 생성으로 우회하지 않는다. 버퍼 lease 실패는 명시적 거부·유예·정리 정책으로 진행한다.
- 한 연결의 close/reconnect가 반복되더라도 새 handler/lease를 무한 축적하지 않는다. ListenThread 및 CIO Timing Wheel은 기존 리소스 제한과 함께 운영한다.

**측정:** slow-client pending-time, pending bytes/client & global, RX pause duration, rejection/close counts, drop-after-encrypt=0, bound violations=0.

### 25.6 6순위: NIO.2 시간 제한·완료 핸들러·스레드 인계 비용 제거

- Java 비동기 READ/WRITE 호출마다 새로운 timeout/예약 작업 객체를 생성하는 경로를 기본으로 쓰지 않는다. 연결 deadline은 **이미 존재하는 CIO Timing Wheel**에서 관리한다. 단, 기존 socket timeout 및 보안 기능은 동등하게 보존한다.
- WRITE 완료 후 다음 WRITE 제출이 **즉시 완료되는 콜백 재진입**으로 이어질 수 있으므로, 연결별 drain/wip 상태로 non-recursive pump를 유지한다.
- 완료 콜백에서 packet마다 ThreadPool.execute/CompletableFuture/Thread.ofVirtual()을 호출하는 구조 금지. 기존 CIO에 게임 소유권을 인계해야 할 때만 기존 접수 API를 사용한다.
- 한 worker가 특정 connection의 대형 RX batch 또는 긴 TX drain에 갇히면 다른 connection tail latency가 증가한다. byte/frame budget을 두되 미처리 수신 데이터를 **반드시 소유 상태로 보존**하고 다음 기회에 처리한다. 단순 budget 반환으로 pending 작업을 잃지 않는다.
- 빈 소켓이 CPU를 차지하는 timer polling이나 write spin loop를 만들지 않는다. 이벤트/IO 완료에서만 재개한다.
- JDK의 자체 I/O 작업 객체 생성은 호출 횟수를 줄여 간접 감축한다. JDK 내부 구조를 리플렉션으로 수정하거나 사용자 Native transport를 추가하지 않는다.

**측정:** handoffs/frame, callback CPU distribution, max callback runtime, worker blocked time, inlining recursion depth=0, retry-loop spins=0.

### 25.7 7순위: Socket Option과 전송 버퍼의 용도 분리

- TCP_NODELAY의 적용 여부는 게임의 소형 응답 지연을 기준으로 정한다. 애플리케이션의 16KiB batching과 OS Nagle 처리/Windows send buffer 설정은 독립된 요소다.
- SO_RCVBUF/SO_SNDBUF는 연결당 OS 메모리를 소비하므로 **무조건 크게 만들지 않는다.** 5,000 연결 × 소켓당 커널 버퍼가 예상보다 크게 잡히면 Java heap보다 OS TCP 메모리가 커질 수 있다.
- 16KiB는 **Application WRITE Batch 크기**다. TCP MSS/MTU와 혼동하거나 OS 세그먼트 크기를 강제로 16KiB라고 가정하지 않는다.
- 로그인/handshake/평상시 게임 패킷/대량 방송의 크기 분포를 기록하여 적절한 소켓 옵션을 최종 코드 기본값에 정한다. 설정 플래그를 사용자에게 여러 개 떠넘기지 않는다.
- 재접속·Half-close·RST·버퍼 고갈에서 실패 처리와 close 상태가 일치해야 한다.

**측정:** Windows per-process/private bytes, TCP send/recv buffer footprint, packet p99, small-packet response time, WRITE completions/s, connection abort rates.

### 25.8 8순위: 로그인 입구와 세션 생성의 진짜 비용 제어

- ListenThread는 하나이고 ACCEPT만 담당한다. 인증 DB, 캐릭터 생성, 암호화 초기화 전체, 로그 및 큰 버퍼 확보를 넣지 않는다.
- 로그인 전 수립된 연결은 **pre-auth의 최소 상태**만 보유하고, 인증 및 초기 키 교환을 통과한 뒤에 필요한 게임 세션 상태로 승격한다.
- 초기 접속 실패/handshake timeout이 많은 경우에도 거대한 큐와 buffer/slot 선할당이 증가하지 않도록 수명·resource cap을 관리한다.
- 로그인 중 중복 로그인/동일 계정/세션 세대 번호·MAC/IP 정책과 기존 CIO 소유 경계를 유지한다. 빠르다는 이유로 보안 검사를 생략하지 않는다.
- 입구 burst가 READ/WRITE 완료 워커의 tail latency에 끼치는 영향을 별도로 계측한다. 단순 연결 수용량과 로그인 성공 처리량을 서로 구분한다.

**측정:** accepted/s, authenticated/s, time-to-first-valid-frame, pre-auth retained bytes, leaked pre-auth slots, active-game p99 under login storm.

### 25.9 초기 구현 단계의 우선순위 결정

| 우선순위 | 영역 | 처음부터 반드시 구현할 내용 | 이미 24장에 있는지 |
|---|---|---|---|
| **P0** | TX 실행권 + FIFO | 단일 TX Pump, 선형화, direct fast path, in-flight WRITE 1개 | **24장 필수** |
| **P0** | RX→CIO | 기존 PacketTask/샤드 경로 재사용, 추가 dispatcher/queue 0 | **25장 추가** |
| **P0** | 프로토콜 | 암호화 1회/키 순서/게임 객체 snapshot 보존 | 24장 + **25장 구체화** |
| **P0** | 수명·역압력 | 느린 소켓 격리, pool 고갈 시 명시적 종료·복구 | **25장 강화** |
| **P1** | WRITE cost | 16KiB 자연 batching, Direct 작성, JDK 내부 등록 수 절감 | **24장 필수** |
| **P1** | 메모리/Pool | 연결별 lazy lease, 중앙 pool 경합·스캔 감소 | 24장 + **25장 강화** |
| **P1** | 비용 없는 dispatch | opcode/cipher primitive hot path, 불필요한 시간·로그 제거 | **25장 추가** |
| **P1** | 워커 공정성 | 비재귀 completion, budget, 기존 CIO handoff만 | **25장 강화** |
| **P2** | Windows 튜닝 | TCP options, send/recv OS buffer footprint, 실제 frame size 확인 | **25장 추가** |
| **P2** | CPU cache/layout | session hot/cold, registry 조회, 계측용 atomic contention | **25장 추가** |
| **P2** | 로그인 | 1 ListenThread, pre-auth lazy state와 admission 폭주 보호 | 기존 22장 + **25장 강화** |

### 25.10 출시 판단 기준 및 결론

1. RX·TX·방송·로그인 각각에 대해 **CPU/packet, allocations/packet, queue hops/packet, copied bytes/packet, average frames/WRITE, p99, retained heap/client**를 실제 Windows에서 측정한다.
2. 1,024 연결뿐 아니라 5,000 연결에서 유휴와 대량 방송·동시 이동·로그인 폭주를 구분하여 측정한다. 특히 **활성 게임 이용자의 p99**가 로그인 폭주 때문에 악화되는지 검증한다.
3. 정확성 기준은 패킷 누락/중복/FIFO 역전/rolling key mismatch/lease 이중 반환/세션 유출 **0건**이다.
4. 높은 처리량을 위해 코어가 무한 busy spin하거나 특정 CIO 샤드를 멈추게 하는 설계는 채택하지 않는다.
5. 사용자 Native DLL/FFM/JNI를 다시 도입하지 않고, 이 비용 절감 규칙을 **제품 첫 구현의 기본 경로 하나**에 통합한다.
6. 이미 RKern 기존 코드가 잘하고 있는 최적화(재사용 PacketTask, rolling crypto fast path, 기존 CIO·BufferPool·방송 공유 원본)는 삭제하거나 다시 만드는 것이 아니라 **장점을 유지한 채 중복 비용만 제거한다.**

**최종 결론: 24장만으로 '최적화 완료'가 아니다. 24장이 송신 비용의 뼈대라면, 25장은 수신→게임 인계·암호화·글로벌 경합·정체·운영 메모리까지 통합한 제품 성능 요구사항이다. 두 장을 합쳐 첫 구현에서부터 적용한다.**

---

## 출처 및 범위

- RKern: 2026-11-17 저장된 최신 전체 SRC 두 ZIP의 실제 Java 소스 비교. 프로덕션 코드는 이 보고서 작성 과정에서 수정하지 않음.
- NC: 사용자가 확보한 NC L2Server(10).exe 역분석 통합본 중 확인된 serializer/IOCP/등록/완료 흐름만 사용.
- MMOCore: L2J/MMOCore SelectorThread.java 및 MMOConnection.java 실제 코드.
- PyroNet: PyroSelector.java, PyroClient.java 실제 코드.
- API: Oracle Java SE 21 AsynchronousSocketChannel / AsynchronousChannelGroup Javadoc.
- 이 문서는 **설계 명세**이며 전체 Java 21 컴파일, Windows IOCP 구동, 5,000 동접 실측을 아직 수행하지 않았다.
