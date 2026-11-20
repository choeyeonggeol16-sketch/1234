# JProRing 후속 최적화 및 전체 감사 종결 작업 지시서
2026-11-20 · 기준 소스 9a9c25a5d70cf67efcff36f4c208df4e78c87f18

기준: [최신 반영 현황](IMPLEMENTATION_KO.md), [원 감사 A01~A30](REPORT_KO.md), [기준 커밋](https://github.com/choeyeonggeol16-sketch/1234/commit/9a9c25a5d70cf67efcff36f4c208df4e78c87f18).

## 0. 작업 목표 및 금지 사항

Codex 작업 지시: 아래 W01~W12를 순서대로 진행하여 남은 27개 감사 항목을 종결한다. 한 작업의 안전성 검사와 A/B를 마치면 다음 작업으로 이어가며, 각 단계의 결과를 기록한다. 기존 뼈대를 유지하고 실제 게임 패킷당 CPU 시간, 메모리 복사, 객체 할당, 동기화 횟수, 송신 시스템콜, tail latency를 감소시키는 것이 목적이다.

- Java 21 Selector + 고정 CIO Owner + 기존 Timing Wheel + 직접 패킷 핸들러 실행 유지. 새로운 네트워크 워커, 별도 패킷 처리 풀, 중복 큐, 범용 프레임워크 도입 금지. 기존 구조를 활용한 필요한 내부 자료구조 수정은 허용.
- 실제 게임 기본 경로: Server.startLoginServer → JavaCioServer / SelectorTransport.bindHosted → CIOThreadCallback owner → SelectorRawIo / CSocket → CUserSocket. 독립 SelectorFrameReader / SelectorSendQueue의 성능 수치로 이 경로의 개선을 대신하지 않는다.
- 기존 패킷 바이트, 헤더, 암호화 키 진행, 수신·송신 FIFO, opcode gate, 로그인/계정 정책, timeout/역압력, 세션 종료, 버퍼 수명, 게임 동작 유지. 외부 생산자의 미게시 FIFO ticket을 추월하지 않는다. 같은 소켓의 수신 직렬화와 외부 게임 상태 변경의 안전성은 별개다.
- 현재 두 실험 옵션을 사용자가 직접 설정하도록 하는 방식으로 끝내지 않는다. 개발자 A/B 전용 옵션을 사용해 후보를 검증한 다음 우수한 경로를 기본값으로 통합하고 불필요한 옵션 제거. 실패한 후보는 제거하고 이유를 기록한다. 소스 수정 없이 실험 옵션을 켜기만 하는 것은 완료가 아니다.
- 기능 변경 없이 최적화할 수 없거나 성능 개선이 입증되지 않는 항목은 억지로 변경하지 않는다. 측정 근거를 남겨 유지/기각으로 판정. 실제 클라이언트, DLL, MySQL, 전용 부하 머신 등 외부 환경이 필요한 항목은 별도 명시하고 가능한 검증을 먼저 마친다. 미검증을 PASS로 표시하지 않는다.
- 네트워크 영역만 생산 코드 변경. CacheD, DB, NPC, 아이템, 월드/전투 규칙은 변경하지 않는다. 방송 관련 작업은 네트워크 내부에서 공유 원본 및 수신자별 송신 비용을 줄이는 범위만 적용한다. 외부 게임 방송 호출부나 동작 재설계가 필요하면 별도 요청 범위로 남긴다.
- 매 단계 최신 커밋 반영, 변경 파일만 git commit. 기준/수정 커밋, 정확한 실행 명령, raw CSV/JFR/GC 자료(가능 범위), 기능 테스트 결과, 실패·미확인 사항을 기록한다. 임의 fallback 경로를 계속 늘리지 않고 필요성 있는 안전 분기만 남긴다.

## 1. 완료 현황과 실제 잔여 작업

기준 구현 보고서 기준 A01/A17/A28은 반영, A09/A10은 후보, A12/A15/A30은 대기, 나머지 22개는 부분 반영이다. 본 지시서 작성 시 소스와 구현 보고서를 확인했으며 기존 PASS는 작성자의 기록이다. 독립 재실행은 하지 않았다.

A17 indexed deadline heap은 유지한다. 보고된 5천 연결 재예약 2.023 → 0.105 µs/건의 성과를 전체 서버 처리량 향상으로 환산하지 않는다. A01의 콜백 격리와 A28 CI 보존 기능 역시 다시 구현하지 말고 신규 변경의 회귀 게이트로 사용한다.

## 2. W01 — 실제 게임 SEND A/B 및 비용 프로파일 (A08, A09, A10, A22, A25, A30)

### 현행 코드 근거
- CUserSocket.tryWritePacketInternal(기준 소스 약 2184행~): 예약 lock, SOCKET_PENDING CAS, SOCKET_TICKET.getAndAdd, 외부 lock-free encode, 후속 state lock.
- JAVA_DIRECT_PACKET_COMMIT, JAVA_DIRECT_BYTE_APPEND의 기본값은 false. 전자는 이미 예약한 자기 FIFO head의 serializer commit을 직결, 후자는 BytesWritablePacket의 byte[]를 앞선 staged SEND lease에 직접 복사하여 중간 lease를 없앤다. 두 후보 모두 기존 ticket 및 동기화 경계를 남기고 있다.
- appendAdjacentStagedPacketLocked(약 1673행~), stageOutboundFrameLocked(1814), finishStagedSend(1931), drainSocketCallbacksAcquired(3614).
- SelectorCioIntegrationTest.verifyConcurrentSendFifo는 외부 20개 encoder의 미게시 예약을 blocking fixture로 재현하여 owner 송신과 암호화 FIFO를 확인한다. 현재 테스트는 기능 검증이며 성능 채택 근거가 아니다.

### 변경 및 측정
1. 바로 실제 게임 리스너를 구동하는 benchmark fixture를 추가한다. 기존 CUserSocket, CSocket, 패킷 인코더, 암복호화, CIO hosted owner를 거치며 lightweight 대표 opcode handler로 입력/출력을 고정한다. 따로 만든 raw echo로 대신하지 않는다. 계정/월드 DB가 필요한 부하는 별도 분리.
2. 4가지 구성(둘 다 OFF, commit ON만, append ON만, 둘 다 ON)을 같은 revision/환경에서 실행. 반드시 후보 hit count와 miss 이유를 출력하고 실제 최적화 경로가 사용됐는지 확인.
3. 단일 연결/window1, 16/128/1024 연결, window16/64/256, 8~64B action/move/small reply 및 256B/4KiB/large response, 1/2/4/8 owner 중 대표 조건을 구성한다. 일반 WritablePacket과 BytesWritablePacket을 구분, 0%/일부 외부 생산자/20 producer 경합으로 나눔. 단일 연결과 burst 모두 보존.
4. CPU ns/완료된 게임 요청, 처리량, p50/p99/p99.9, alloc B/op, pool borrow/return, bitmap CAS retry, state lock wait, serializer ticket publish/claim, packed copy bytes, gather syscall/packet, candidate hits, timer drift를 획득한다. 계측은 샘플 또는 개발 모드로 오버헤드 비교. CPU tick 해상력이 부족한 짧은 시험은 smoke만.
5. 5회 이상 독립 JVM, 워밍업 안정화, 30~60초 측정 권장. 환경 제한이면 가능한 반복과 정확한 제한 기록. 중앙값/범위와 개별 trial, baseline과 변경 효과 제시. 의미 있는 개선 및 안전성 확인 후 우수한 설정을 기본 코드에 적용한다. 개선하지 못하면 후보를 제거하거나 W02에 통합한 다음 재평가.

완료 조건: 실제 game path A/B 자료와 채택/기각 근거, 기본 실행 성능 회귀 없음, FIFO/암호화/close/lease 회귀 통과. 둘 중 하나만 우수하면 그 하나만 채택.

## 3. W02 — 일반 WritablePacket 최종 송신 버퍼 직접 인코딩 (A09, A10)

### 현행 경로
일반 패킷은 pool lease 취득 → WriteBuffer로 인코딩 → stage → appendAdjacentStagedPacketLocked의 dest.put 복사/lease 반환 → FIFO encrypt → write. BytesWritablePacket 후보는 입력 자체가 이미 byte[]이므로 중간 복사 1회를 제거하는 것이지 완전 무복사 생성은 아니다.

### 구현 요구
- 먼저 동일 CIO owner의 ordinary SEND 경로에서 기존 미전송 tail이 안전하게 append 가능한 경우를 좁게 지정한다. 외부/재진입/미게시 앞 ticket, 암호화 또는 I/O 진행 중인 lease는 금지. API상의 true=admitted, 실제 wire delivery는 completion 계약 그대로 유지.
- FIFO reservation의 linearization point 및 기존 serializer DRAINING token과 경쟁 관계를 도식화한다. 자기 head commit 후보가 _socketHead 증가, SOCKET_PENDING 감소, 취소 slot 처리, inline/prefix completion, SEND_START, 예외 탈출에서 정확히 한번 drain되는지 검증한다.
- WritablePacket.writeData는 WriteBuffer.reset(getClass()), position(HEADER_SIZE), mark(), 실패 시 releaseResources()를 호출한다. 단순히 기존 tail writer에 position을 옮겨 호출하면 frame 시작 주소를 덮어쓰거나 buffer 소유권을 잘못 반환할 수 있다. WriteBuffer.armExternal의 base/limit/capacity, 절대 index 헤더 write, reset/mark/writeHeader, learned size 및 resource release 계약을 기준으로 frame-relative view를 구축한다. 별도 ByteBuffer slice/duplicate를 packet마다 생성하지 않는다.
- tail의 final destination에 2B header 공간과 payload capacity를 예약. 안전한 writer frame scope에서 인코딩하고 길이 확정 및 ticket commit 후에만 tail 길이·frame count·watermark 변경. 기존 cipher가 최종 FIFO 순서에서 정확히 한 번 진행되도록 한다. 이미 암호화된 영역은 재인코딩/병합하지 않는다.
- 임의 WritablePacket.writeData 및 write()는 game callback을 포함할 수 있다. global lock 또는 긴 socket state lock을 유지한 채 호출하지 않는다. 안전하게 독점 예약할 수 없다면 기존 verified encoder 경계를 유지한다. tail을 보호하는 reservation 동안 외부 생산자가 이전 내용을 publish/consume/release하지 못하도록 기존 FIFO/token으로 처리. 인코더 재진입이 가능한지 검사한다.
- 길이 초과, false/예외, disconnect, capacity 부족, learned-size 오판, 동시 close에서 pending 및 reservation/lease 상태와 writer 범위를 정확히 원복한다. 부작용이 있는 encoder를 무조건 재호출하지 않는다. 필요한 경우 대상 encoder의 순수성 또는 사전 크기를 확인하고 검증된 대상부터 확장.
- 일반 packet 경로와 DirectFrame/작은 action/shared broadcast의 경합까지 점검. 기존 non-owner encode 시점과 가변 객체 읽기 시점을 함부로 바꾸지 않는다.
- 성공 후 이미 확보된 A09 head commit 및 A10 byte append의 중복 제어를 통합. 같은 frame에 2번 암호화하거나 frame count를 이중 계산하지 않는다.

완료 조건: 대표 일반 WritablePacket이 임시 SEND lease/후속 병합 복사 없이 직접 인코딩되는 실제 hit 증명. 20 producer FIFO, earlier hole, reentrant encode, abort/partial write, boundary 16/64KiB, encode exception 및 재접속을 검사. 원본과 완전한 wire byte 일치. W01 대비 borrow/copy/CPU 감소 확인 후 채택.

## 4. W03 — Owner-local 버퍼 풀 및 메모리 수명 (A11, A20, A21)

### 근거
실제 JavaCioServer는 RawHandler를 사용하고 CUserSocket의 CIOBufferPool/CIOSlotPool을 공유한다. 기존 bitmap CAS 및 threadId 기반 scan, slot generation, CIOBufferEx ACTIVE CAS, LongAdder 계측 비용이 남아 있다. Raw mode의 사용하지 않는 framed pool 제거는 이미 완료됐다.

### 작업
- RECV lease acquire/return의 실행 스레드와 종료/cancel의 foreign 반환 가능성부터 조사. 보장되는 owner-only 4KiB 수신 슬롯부터 로컬 free stack/primitive index 등을 비교한다.
- 다음 SEND owner-only pool 캐시와 외부 생산자/방송 shared lease의 수명을 분리해 검증한다. remote return은 기존 소유권 메커니즘을 이용하고 필요한 경우 기존 owner command 경계를 활용하되 별도 무한 큐를 만들지 않는다. 외부 완료가 owner stack을 직접 변경하지 않는다.
- 신규 풀을 전역 풀과 중복으로 대량 사전 할당하지 않는다. 기존 global cap, 16KiB/64KiB size bucket, generation/identity/double-release 방어, starvation 및 uneven owner load를 유지. local cache가 빈 채로 고갈되는 경우의 안전한 재공급/반환 정책을 정량화.
- lease 보유량/bitmap scan/CAS/remote return/borrow cost, direct committed/resident bytes, heap retained per socket, serializer arrays, writer/cipher objects를 프로필링한다. 시작 후 idle, peak, drain, 재접속, 종료 이후를 나눠 수집한다. 무조건 작은 버퍼/풀을 늘리는 정책 금지.
- 5000 idle 및 혼합 active, owner 1개 과부하/다른 owner idle, global capacity full, slow client와 close race, shutdown/reconnect 1000/10000회에서 outstanding leases/registrations/FD를 검증.
- lease가 모두 반환됐어도 direct buffer backing이나 closed handle이 retained되는 것은 별개다. heap dump와 NMT/RSS/direct MXBean을 함께 분석.

완료 조건: 단일 소유 구간에서 공유 CAS 비용이 실제로 줄고, global resource bound와 외부 반환 안전성이 유지된다. 성능 향상이 없는 범위의 캐시 분할은 채택하지 않는다.

## 5. W04 — CSocket / CUserSocket 중복 동기화 최소화 (A12, A04, A09)

근거: CSocket.read, completeJavaRead, completeJavaWrite, sendReserved, javaReadRequest/javaWriteRequest와 CUserSocket READ_KICK/READ_EVENT/READ_ACTIVE, WRITE serializer. raw I/O 자체도 방향별 하나의 pending request를 유지한다.

- READ부터 시작. 정상 READ 등록/완료가 동일 CIO owner에 있는 경우 ownership을 증명하여 불필요한 monitor 및 atomic RMW를 줄인다. READY/READ 재등록, inline reject, deferred yield, foreign close/cancel, pressure resume 경로는 제외하지 않는다.
- WRITE는 encoder/RX/타이머/NPC·방송 producer와 completion의 소유권을 별도 분석한다. 동일 owner hot path에서만 중복 상태 전이를 줄이고 기존 serializer FIFO 계약 유지. provider call 중 descriptor lifetime 유지.
- foreign 호출 허용 기능의 happens-before, read/write callback의 fields clear-before-callback, exactly one outstanding per direction, duplicate/lost completion 방지.
- CSocket lock을 일괄 제거하지 말고 ownership table 및 race regression을 먼저 구성. 기존 native 호환 경로는 요청 없이 변경하지 않는다.

완료: before/after 동기화 횟수·CPU per game request 측정. inline failure, reentry, partial gather, EOF/RST, mailbox full, cross-owner SEND/close를 포함한 regression 통과.

## 6. W05 — 실제 게임 RECV offset 기반 프레이밍 (A13, A05, A18)

근거: CUserSocket.onRead(기준 약 3228행) flip/compact 및 pool promotion/demotion. 전량 소비 clear와 선두 partial 복사 생략은 구현돼 있으므로 같은 작업을 반복하지 않는다.

- readOffset/writeOffset 또는 동등한 cursor로 consumed prefix를 남겨 두고 실제 write space가 부족할 때만 compact. ReadBuffer.resetTrusted의 body offset, limit과 rolling cipher, frame length little-endian, frame budget/yield, next-read registration을 유지.
- 기존 단일 프레임 fusion 및 backpressure resume, partial head deadline, login timeout, 4KiB→64KiB 승격과 반환, READ buffer lease lifetime을 점검한다. 완전한 head가 backpressure로 대기하는 동안 partial timeout을 부과하지 않는다.
- 기존 빈 packet flood 정책, 8B 미만 encrypted body 거부, 정상 필터링 opcode에도 정확히 한번 decrypt, 65535 max wire 유지. 독립 framed 정책과 실제 game policy의 차이를 문서화.
- 1바이트씩 모든 header/body split, 200 coalesced frame 및 partial tail, malformed length, 대형 frame, pressure, read 0/EOF/RST, timer 및 close race를 테스트. 기존 compact bytes가 적거나 CPU 이득이 없으면 기존 코드 유지.

완료: wire/handler invocation 동일, 실측 copy bytes/CPU 개선 및 p99·timer drift 회귀 없음.

## 7. W06 — 송신 배치 및 Gathering Write (A14, A18, A21)

- 실제 게임 GATHER_MAX_FRAMES=8/GATHER_MAX_BYTES=64KiB, 독립 framed 16/256KiB를 구분. W02 완료 후 자연스럽게 생성된 batch에 대해 4/8/16 buffers 및 32/64/128/256KiB의 대표 A/B를 수행. 상수 확대를 무조건 채택하지 않는다.
- syscall/packet, gather occupancy, partial write count, re-submitted empty descriptors, bytes/turn, p99/p99.9, quiet connection/handshake latency를 수집.
- 소진된 gather prefix를 안전하게 제외하는 후보를 검토. in-flight 상태와 descriptor/lease release 계약을 위반하지 않는다.
- 송신 지연을 강제로 추가하지 않고 기존 SEND burst 경계 활용. FIFO, 일부 송신 후 남은 tail, OP_WRITE interest, 0바이트 WRITE, slow peer/write stall, graceful FIN 순서 검증.

완료: 최적 batch 정책을 기본 코드에 반영하거나 기존값 우수 근거 기록. 송신 시스템콜 절감과 latency 안전성 동시 확보.

## 8. W07 — 외부 mailbox, wakeup 및 owner 공정성 (A16, A18, A19, A22)

- 기존 OwnerDiagnostics의 admission, registered, commands/current/highwater/reject, deadline/stall counters 보존. submit의 same-owner 직접 경로, foreign/alwaysDefer OwnerTask 래퍼 할당, mailbox monitor의 offer/poll/hasMailboxWork, selector.wakeup 비용을 측정.
- 이미 구현된 same-owner fast path를 반복하지 않는다. 1/4/20 producer 측정으로 실제 병목이면 사전 할당 bounded MPSC slot 또는 기존 CIO queue 재사용을 검토. producer publish hole, full/reject, accepted run-or-cancel exactly once, slot reuse ABA, memory ordering, wakeup coalescing/lost wakeup, close control 진행을 증명.
- 무한 spin, 새 worker, 중복 ingress 금지. 동일 owner의 direct submit이 이전 accepted command를 앞지를 수 있는 경우의 계약 확인. FIFO를 요구하는 alwaysDefer 호출은 유지.
- ACCEPT32/COMMAND64/READY256, 단계 500µs(8개마다 확인), game frame budget 및 timing wheel tick과의 공정성 측정. 한 handler는 선점하지 못하므로 느린 handler와 동시 방송에서 실제 timer drift/quiet socket p99를 기록. blocking 호출 발견 시 파일·함수만 보고하고 별도 게임/DB 수정 금지.
- accept owner0의 CPU, connection allocation, admission latency, mailbox reject, shard skew 측정. 필요시 접속 시점 배정만 최적화; active-session 이동 금지.

완료: hot socket 1개+quiet 연결 다수, 5000 persistent/churn 혼합, 20 producer, full queue, sleep race 회귀 통과. 기존보다 빠른 경우만 새 mailbox 정책 채택.

## 9. W08 — 공유 송신 원본/방송 transport 비용 (A15, A21)

- CAsyncSendConstBuffer/Source, SharedPacketFormat, CUserSocket의 recipient copy/암호화 경로, monitor/refcount/read-only view 생성 비용을 측정. 기존 plaintext 작성 1회와 수신자별 암호화 유지.
- W02 직접 append와 공유 plaintext 전송을 통합할 수 있는 정확한 범위를 검사. 암호화된 데이터를 여러 세션에 공유하지 않는다. 필요할 때 recipient별 copy는 유지; 불필요한 중간 lease/copy/view와 monitor 구간만 최소화한다.
- source의 seal/retain/release, cross-owner close, encoder 실패, stale source/slot generation, 20/100/500 수신자와 large area packet을 검증. mandatory SEND silently drop 금지.
- 외부 생산자와 원본 객체 lifetime 보장. 의도하지 않은 broadcast 동작 및 대상자 변경 금지. 게임 방송 호출부/게임 규칙 변경이 필요하면 별도 보고.

완료: byte/cipher parity, fan-out별 encode/copy/alloc/CPU 측정, no leak. transport 범위에서 실증된 개선만 적용한다.

## 10. W09 — 전체 안정성 잔여 조건 종결 (A02~A07, A21)

이미 회귀로 확인한 일반 cases는 유지하고, 아래 교차 조건을 추가한다. 실제 환경 제한은 별도로 표기한다.

| 대상 | 필수 잔여 검증 |
|---|---|
| A02 종료 | graceful/abort/remote FIN/RST/partial gather, admission 전후 및 미게시 ticket, timer와 느린 reader 교차, close queue full, 종료 승인 후 새 SEND 거부, 명시 실패 또는 정확한 FIN 전 delivery |
| A03 lifecycle | hosted attach 실패/즉시 stop/부분 attach/owner failure/stop timeout retry, 동일 CIO scheduler 100회 listener 재시작, FD/lease/counter 0, stale driver 불실행 |
| A04 owner | move/attack/skill representative, cross-owner timer/NPC/foreign producer, logout/reconnect 중 encoder, affine handler/게임 상태 순서 검사. 실제 호출자 검사표 |
| A05 cipher | 초기 S_KEYBYTES, rolling key/known-answer, zero/short/boundary frame, filter/unknown, reject/partial retry, large/packed/mixed, 실제 DLL 및 클라이언트 대조(자료 있으면) |
| A06 registry | IP/MAC/계정 gate, duplicate entry 경쟁, 로그인 거부/취소/재로그인, counters exact once, 실제 MySQL 세션이 있으면 integration |
| A07 native 경계 | DLL-free Linux/Windows 시작, Java 기본 경로에서 native 전용 함수 reachability 검사, 대표 게임 packet/admin/diagnostics/close 실행 |
| A21 pressure | slow client 1%/10%, send pool full, pause/resume, 정상 연결 starvation/latency, mandatory/optional 정책 및 reject reason, 정체 timeout/버퍼 반환 |

A01 일반 callback exception 격리, A17 heap index 정합과 expiry/reinsert, A28 CI는 전 단계 regression 유지. 정상 runtime exception 격리와 fatal invariant failure를 혼동하지 않는다. 신규 코드의 lease 반환 실패를 로그만 남기고 정상 완료로 처리하지 않는다.

## 11. W10 — 공통 경로 계약, 진단, 잔여 heap/CPU 비용 (A20, A22, A29, A30)

- 현재 framed/RawHandler/게임 경로의 차이를 유지한 채 공통 frame boundary, partial write, close, backpressure, cipher, lifetime 테스트의 기대값을 통일 가능한 범위에서 정리. 테스트 의미가 다르면 분리. 실제 게임 경로를 간접 framed benchmark로 바꿔 부르지 않는다.
- owner별 counters: completed inbound/outbound frames/bytes, raw syscalls, gather occupancy, zero writes, wakeups, selectNow/select sleep, pool borrows/returns/exhaustion, copy bytes, queue wait, timer drift, rejects/close reasons. 성능 모드에서 진단 오버헤드 대조. 반복 allocation/logging을 줄이고 상태 snapshot에 필요한 항목만 운영 노출.
- heap histogram/JFR/NMT/async-profiler/perf 등 사용 가능한 도구로 per-socket retained arrays, ByteBuffer wrapper, command/lambda, ClassValue size learning, thread-local cache, atomic counters, interestOps 변경, nanoTime 비용을 측정한다. 비용이 큰 순서로 제거하고 사용하지 않는 실험 switch 및 dead code를 필요한 범위에서 정리.
- Java 21/JDK provider의 자체 객체까지 포함하여 allocation 측정. steady-state network path의 allocation 최소화를 목표로 하되 실제 수치 없이 zero-allocation을 선언하지 않는다.
- 성능상 필요 없는 코드 추가나 임의 클래스 분할은 하지 않는다.

완료: runtime 운영 지표와 성능 데이터를 비교할 수 있는 문서, 실제 profiler top 및 최적화/유지 결정, 전체 소스 의존성 컴파일 가능한 환경에서는 전체 빌드 성공.

## 12. W11 — 최종 Netty 비교 및 5000 연결 확장 인증 (A08, A23~A28)

비교 대상 Netty 4.2.21.Final NIO/native epoll/io_uring(실행 가능할 때). Linux Java Selector도 epoll 기반임을 명시. Windows는 JDK WEPoll 및 비교 가능한 Netty NIO. 기존 Netty 버전과 새 버전의 수치 혼합 금지.

### 공정한 비교 계층
1. Raw transport echo: 기존 runner 유지 및 재현, raw byte 정확성.
2. 동일 2B-length framed: 동일 길이/요청-응답 의미, payload/window, batching 정책 비교 및 byte 검증.
3. 게임-compatible: 기존 RKern CIO hosted + CUserSocket 실제 암복호화/packet invoke/encode 경로. Netty 비교 adapter는 동일 input/output protocol, cipher, handler workload와 admission/queue limits가 있어야 하며 production Netty 재이관은 하지 않는다. 동등 구현이 없으면 기존 RKern baseline vs 최적화판의 game A/B로 명확히 라벨.
4. 실제 운영: 월드/DB/실제 client 기반 5000명은 준비된 운영 환경에서 검증. raw 연결 수만으로 보증하지 않는다.

### 필수 측정 matrix
| 종류 | 대표 조건 |
|---|---|
| 지연/경량 | 1/16 persistent, window1, 8~64B |
| 중고동접 | 128/1024/5000 TCP, 1/2/4/8 owner 중 장비와 일치하는 수, window1 |
| 자연 배치 | 16/128/1024 연결, window16/64/256, payload 64/256B 및 4KiB |
| 큰 프레임 | max wire 65535 이내, partial/gather 복합 |
| 혼합 | 80% idle/20% active, slow peer 1%/10%, burst 200 packets, 타이머 병행 |
| churn | 8/32 동시 재접속, TIME_WAIT/ephemeral port 실패 원인 확인, 공통 성공 trial 비교 |
| 지속 | 30~60분 soak(운영 환경 가능 시 더 길게), RSS/heap/direct/FD/lease/registry 회수 |

모든 조합 대신 대표 부하로 먼저 screening 후 병목 조건 정밀 반복. 5회 이상, 충분한 워밍업 및 30~60초 measurement 목표. 시간/머신 제약 시 short smoke와 full benchmark를 분리한다. 동일 CPU/JDK/GC/heap/socket flags/threads, 순서 rotate, 서버/클라이언트 CPU 분리 또는 기록. client bottleneck 및 cgroup quota 확인. 5000 부하 generator의 socket/thread/FD 한도 검증.

결과: req/s, CPU ns/req (server user+sys), p50/p95/p99/p99.9/max, allocated bytes/request, GC/safepoint, copy bytes, syscalls, queue/timer lag, direct/heap/RSS idle-steady-peak-drained, per-owner load와 failed/rejected count. CPU tick 기반 단기 수치의 해상도 표시. open-loop overload 및 coordinated omission은 별도 분석. actual client feature 미검증과 CI 공유 runner 편차를 명기.

현재 runner의 --owners/--clients/--window/--payload-bytes/--framed 및 p99.9/CPU 지표를 활용한다. 이미 추가된 A28 artifact 및 Windows audit를 재작성하지 말고 실제 game benchmark, 후보/default regression, failure logs, 재현 명령을 CI에 확장. 실패 trial을 누락하지 않는다. 동일 시점 memory snapshot과 별도의 peak 수집. io_uring initialization 실패는 N/A이지 승리/패배가 아니다.

성공 기준: 네 엔진의 같은 조건별 결과를 투명하게 공개. 목표는 처리량·CPU 효율·꼬리 지연시간의 실질적 우위이며, 하나의 조건만 좋아져서 다른 주요 조건 및 안정성이 크게 악화되는 후보는 기각. 1024/5000 연결에서 수치가 불리하더라도 숨기지 않고 프로파일에 근거해 후속 개선. 모든 조건에서 이긴다고 사전 약속하지 않는다.

## 13. W12 — 최종 판정 및 전달 (A01~A30)

- 새 파일 `FINAL_IMPLEMENTATION_KO.md`에 A01~A30 각각 상태(완료/근거 있는 유지·기각/부분/외부 환경 차단), 실제 변경 파일/함수, 테스트 또는 측정 링크, 잔여 이유를 기록한다. 기존 IMPLEMENTATION_KO.md에 단순히 '완료'만 덧붙이지 않는다.
- raw/framed/game workload 및 최적화 후보별 before/after 자료, 채택한 기본 실행 정책, 제거한 실험 옵션 목록을 제출한다. baseline 및 최종 commit SHA를 명시.
- 변경 범위 git diff/전체 compile 가능한 범위, Windows WEPoll·Linux, FIFO+암복호화, 20-producer, partial/gather/zero writes/graceful/abort, 100 restart, 1000+ reconnect, pressure/lease/cipher/state, 1024/5000 socket을 검사. 이전 감사에서 이미 통과한 부분도 최종 회귀에 포함.
- 외부 환경 문제 때문에 특정 운영 검증이 막히면 가능한 모든 구현/회귀/A/B를 끝내고 해당 항목만 외부 차단으로 명시. 실제 실행하지 않은 항목의 PASS, 정확한 근거 없는 성능 수치, 미완료를 모두 끝났다고 표시하는 것 금지.

## 14. 추적 매트릭스

| A항목 | 후속 작업 |
|---|---|
| A01 | W09/W12 회귀 유지 |
| A02 | W09 |
| A03 | W09 |
| A04 | W04/W09 |
| A05 | W02/W05/W09 |
| A06 | W09 |
| A07 | W09 |
| A08 | W01/W11 |
| A09 | W01/W02/W04 |
| A10 | W01/W02 |
| A11 | W03 |
| A12 | W04 |
| A13 | W05 |
| A14 | W06 |
| A15 | W08 |
| A16 | W07 |
| A17 | W09/W12 회귀 유지 |
| A18 | W05/W06/W07 |
| A19 | W07 |
| A20 | W03/W10 |
| A21 | W03/W06/W08/W09 |
| A22 | W01/W07/W10 |
| A23 | W11 |
| A24 | W11 |
| A25 | W01/W11 |
| A26 | W03/W11 |
| A27 | W11 |
| A28 | W11/W12 회귀 유지 및 확장 |
| A29 | W10 |
| A30 | W01/W10/W12 |

**최우선은 W01 실제 게임 SEND A/B → W02 일반 packet 직접 인코딩 → W03 owner-local pool → W04 중복 동기화 축소다.** 해당 네 단계의 구현과 검증을 마치고 W05~W12를 이어서 끝낸다. 정상 게임 동작, 안정성, 메모리 상한, FIFO를 희생한 단순 throughput 개선은 채택하지 않는다.
