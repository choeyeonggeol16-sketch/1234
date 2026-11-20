# JProRing — 남은 네트워크 최적화 구현 지시서

기준: 보고서 커밋 [059631b](https://github.com/choeyeonggeol16-sketch/1234/commit/059631b0dceb65f9157fed68ce224989fa4b400a), 생산 코드 [49252dc](https://github.com/choeyeonggeol16-sketch/1234/commit/49252dcf2d557986ebf256e85cddfa487eac5427).
대상 브랜치: `rkern/jproring-audit-fixes-20261120`. [기존 지시서](NEXT_IMPLEMENTATION_KO.md) · [최근 검증](FOUR_ITEMS_KO.md) · [감사표](FINAL_IMPLEMENTATION_KO.md).

## 0. 이번 작업의 범위와 채택 원칙

- Java 21 멀티 Selector + 기존 고정 CIO Owner + CIO Timing Wheel + CUserSocket serializer를 유지한다. 새 I/O 스레드, executor, 중복 게임 실행 큐, 다른 transport 도입 금지.
- 리니지 암복호화 알고리즘, 연결별 키 갱신, wire format은 변경하지 않는다. 이번에는 Cryptor 자체의 최적화도 보류한다. 게임 로직, CacheD, NPC, 방송 콘텐츠와 외부 게임 호출부는 수정하지 않는다.
- 우선 기존 구조에서 남은 할당·복사·동기화·시스템 호출 비용을 제거한다. 타이머 재예약 힙, RECV offset, backlog, hosted 초기화 경쟁 수정 등 이미 검증된 경로는 보존한다.
- 동일 소켓의 FIFO, 미게시 SEND ticket, 재진입, 종료·부분 송신, lease 및 공유 source 수명, backpressure, 전체 메모리 상한은 성능보다 우선한다.
- 사용자에게 운영 JVM 옵션 선택을 맡기지 않는다. 후보별 격리 A/B는 테스트에서만 수행하고 이득이 입증된 기본 경로를 채택한다. 효과 없음/역효과는 기각 및 임시 옵션 제거. 기존 경로가 더 안전하고 빠르면 유지도 종결 판정이다.
- 아래 R01~R05를 순서대로 구현·검증하고 가능한 항목을 이어서 완료한다. 자료나 실제 머신이 필요한 항목만 명시적으로 분리한다. 미실측을 완료로 표시하지 않는다.

## 1. 현재 확인된 성과와 아직 남은 부분

| 항목 | 현재 기준선 |
|---|---|
| 일반 패킷 직접 tail 인코딩 | 제한된 writer 계약인 WritablePacket.DirectTail 구현; BytesWritablePacket 정확한 클래스 및 ProtoPacketWriter.Raw 적용 |
| READ 풀 | acquireNearShard가 공유 CAS 비트맵에서 shard별 시작 word를 선택; 전역 탐색 가능. 진짜 로컬 free list는 아님 |
| SEND 풀 | 기존 전역 CIOSlotPool; generation 및 ACTIVE 반환 게이트 유지 |
| WRITE | CSocket의 descriptor monitor와 serializer/stateLock 경로 유지; READ 동일 owner monitor 재진입 일부 제거 |
| Gather | 부분 완료 prefix 제거; 4/8/16 측정 후 기본 8 유지 |
| Mailbox | SelectorTransport.Owner의 monitor + ArrayDeque; 외부 OwnerTask 제공, 별도 close control 보장 |
| 암호화 | JFR 265표본 중 113개 관측. CPU 비중 확정 아님. 알고리즘/순서 변경 제외 |

16연결/256B/window16의 BytesWritablePacket A/B: 173,650 → 197,316 req/s, CPU 5.95 → 4.56µs/req, p99 33.65 → 33.99ms (각 2회, 1.7초 측정). 단기간 공유 환경 결과이므로 전체 서버 우위로 외삽하지 않는다. 5,000 TCP 연결 중 1,000개 활성, 40ms pace, 60초 실제 게임 리스너 fixture: 1,401,225 응답, 23,354 req/s, p50 0.341ms, p99 20.63ms, p99.9 46.48ms, SEND 거절 0, direct backing 20.80MiB, 종료 뒤 lease/registry 0. DB·실클라·월드 전투 미포함. 이 수치를 후속 회귀의 기준으로 사용한다.

## R01. 진짜 Owner Local Buffer Pool — W03 / A11 / A20

대상: `CIOBufferPool.java`, `CIOSlotPool.java`, `CIOBufferEx.java`, `CUserSocket.java`, 필요한 기존 Owner lifecycle 연결부.

### 확인된 병목 구조

현재 `acquireReceive` → `acquireNearShard` → `acquireFrom`은 AtomicLongArray bitmap CAS를 실행한다. `Bucket.acquireSlot`은 slot당 CIOBufferEx와 writer를 lazy 생성하고 재사용한다. `releaseOwnedLease`도 bitmap CAS가 남아 있다. SEND 및 shared source lease는 다양한 producer에서 획득하고 owner 또는 종료 경로에서 반환될 수 있다. 단순 shard별 탐색을 local pool 완료로 보고하지 않는다.

### 구현 방향

1. 기존 전역 슬롯/메모리 예산 내에서 작은 bounded owner-local primitive free-slot 구조 또는 동일 비용 수준의 소유 word 방식을 비교한다. 기존 `SelectorBufferPool`의 owner-local 정책을 참고하되 raw game 경로와 다른 lifetime 계약을 그대로 복제하지 않는다.
2. READ와 large READ를 먼저 구현한다. 실제 실행 thread의 owner 인증과 소켓 affinity를 구분한다. 같은 owner 재대여/반환에서 전역 bitmap CAS를 피할 수 있어야 한다. 불필요한 ThreadLocal 조회나 per-borrow wrapper를 추가하지 않는다. 외부 반환은 안전하게 중앙 풀로 넘기거나 기존 owner ingress에 합쳐 회수한다.
3. SEND는 일반 small SEND, large SEND, shared source 별로 호출/수명 분석 후 적용한다. 인코딩 producer와 I/O 완료 owner가 다를 수 있으며, 실제 획득 주체를 기록하거나 중앙 경로를 사용한다. 단순 socket affinity 기반 local push/pop 금지. 외부 스레드가 owner의 배열을 직접 변경하지 않는다.
4. 로컬 슬롯, 전역 free 슬롯, 실제 active lease의 상태는 상호 배타적이어야 한다. 동일 slot의 두 경로 중복 할당, stale generation, ABA, double return을 막는다. 기존 ACTIVE exactly-once release gate를 근거 없이 제거하지 않는다. cached free slot은 active lease로 통계 집계하지 않는다.
5. 로컬 refill/반납은 bounded batch로 필요할 때만 수행한다. cache가 전역 여유 슬롯을 독점하여 타 owner를 굶기지 않도록 회수/공유 정책을 설계한다. 130개 슬롯을 한 shard가 모두 이용하는 기존 회귀와 전역 메모리 상한을 보존하거나, 정책 변경 시 거절 조건과 보장 대안을 검증한다. 큰 버퍼는 낮은 cache cap부터 비교한다.
6. shutdown/rebind 실패, 강제 종료, 외부 늦은 반환 때 owner epoch 및 슬롯 수명을 검증한다. cache flush와 active lease 반환을 구분한다. 종료된 owner에 반환을 게시하고 유실하는 경로 금지. backing lazy allocation, bounded cap, pool snapshot 의미 유지.

### 종료 조건

- 1/2/4/8 owner, 동일 owner 반복 대여/반환, cross-owner 반환, 외부 20 producer, 큰 버퍼 승격·축소, full pool/idle shard/편향 부하, close/restart, generation wrap을 검증한다. 모든 lease 1회 반환, stale 사용 및 중복 할당 0.
- 기존 vs local 경로별 borrows/returns, local hit, refill, remote return, CAS 시도/실패, allocation, CPU/req, RSS/direct, p99를 측정한다. 진단은 실측 전용이며 기본 hot path에 원자 카운터를 추가하지 않는다.
- READ에서 효과가 작더라도 과도한 복잡성은 피한다. SEND 비용 우위가 입증되지 않으면 안전한 전역 경로 유지로 판정한다.

## R02. 실제 게임 패킷 직접 인코딩 확대 — W02 / A10

대상: `WritablePacket.DirectTail`, `WriteBuffer`, `CUserSocket.reservePacketTailLocked / encodePacketTail`, 기존 outbound packet writer.

- 실제 서버의 SEND 호출에서 많이 쓰이는 writer 유형/크기와 기존 DirectTail hit·miss 이유를 먼저 측정한다. Bytes와 cached Proto는 이미 적용되어 있다. 패킷 종류마다 새 wrapper나 클래스를 만들지 않는다.
- 고정 길이 또는 엄격한 wire 상한을 증명할 수 있는 writer부터 기존 계약을 구현한다. 헤더 2바이트 포함, 외부 frame-relative 영역 준수, 상한 계산에 부수효과 없음, 인코더 한 번 실행만 허용. learned size나 평균 길이는 상한이 아니다.
- tail reserve, ticket FIFO, serializer token pin, _tailEncoding, _reservedDirect 및 _reservedWireBytes 회계 유지. 다른 producer나 compact가 pinned lease를 수정/반환하지 않도록 검증한다. 인코딩을 stateLock 안으로 옮기지 않는다. 재진입과 예외/overflow/close 시 한 번만 retire, 암호화되지 않은 오류 bytes 노출 금지.
- 범위 초과나 계약 미지원은 인코딩 시작 전에 기존 안전 경로를 선택한다. 이미 실행된 side-effect writer를 다시 실행해 복구하지 않는다. 전체 게임 패킷에 무조건 interface 붙이기 금지.
- 20 foreign producer, 순서 역전 시도, 재진입, writer 실패, 최대 frame, reset 및 send pressure 회귀. 실제 wire 독립 기준 암호 바이트 비교. 패킷 유형별 direct hit, 임시 lease 대여 감소, 병합 복사 bytes, CPU/req와 p99를 A/B로 계측한다. hit가 없는 사례는 최적화 완료로 치지 않는다.

## R03. WRITE 중복 동기화 절감 — W04 / A12

대상: `CSocket`, `CUserSocket` 기존 WRITE completion/serializer/lease 경로, 필요 최소 범위의 SelectorRawIo.

- CSocket의 javaWriteRequest는 descriptor 상태를 monitor에서 읽어 writeRaw를 호출한다. `write` admission, `completeJavaWrite`, close와 취소 경로를 전체 추적한다. 실제 같은 owner에서 inline 등록할 때 READ fast path처럼 중복 monitor 횟수를 줄일 수 있는지 확인한다.
- 단일 buffer와 gather descriptor의 게시/회수, 원격 close, 실패 callback, 중복 completion, 부분 WRITE 재등록에서 happens-before를 증명한다. 단순히 synchronized를 제거하거나 owner가 아닌 호출자를 owner로 간주하지 않는다.
- CUserSocket serializer 512 ticket, _stateLock, pending/draining CAS, staged packing, producer reservation, completion 및 send pressure 경계를 분리 측정한다. 이전 head direct commit 후보는 CPU/처리량 열세로 기각되어 있으므로 그대로 부활시키지 않는다.
- 같은 owner의 중복 진입만 우선 제거한다. 외부 producer와 owner 사이의 안전한 동기화는 유지. _socketHead보다 앞선 ticket 우회 금지, _reservedDirect 및 pending balance, close/FIN 전송 순서 보존.
- 단일/다중 producer(1/2/4/20), partial gather, write=0, OP_WRITE 재개, overflow, external close/RST, 정상 drain 후 FIN, 재시작을 검증한다. CPU/req, lock 횟수와 대기 ns, CAS/실패, p99.9를 비교한다. 10ms JFR monitor 이벤트가 없다는 이유로 경합이 없다고 단정하지 않는다.

## R04. 송신 병합 복사 최소화 및 Gather — W02/W06/W08 / A10/A14/A15

- `packAdjacentStagedPacketLocked`, `compactReadyStagedPacketsLocked`, 각 WRITE burst, shared broadcast source에서 plaintext 작성·복사·암호화·write를 별도 계측한다. R02 직접 인코딩과 중복 집계하지 않는다.
- 직접 tail이 가능한 packet은 해당 경로 우선, 이미 작성된 packet의 copy-vs-gather 선택 비용을 실측한다. 암호화 key state 및 수신자별 독립 cipher는 그대로 유지한다. ciphertext를 세션 간 공유하지 않는다.
- 기존 게임 gather 8/64KiB 기본 유지. 4/8/16 실측 결과 16은 호출/CPU 우세였으나 8은 p99/p99.9 우세였음. 새로운 workload에서 이득 증명이 있는 경우만 조정한다. batch를 모으려는 인위적인 지연이나 sleep 추가 금지.
- partial completion 뒤 소진 descriptor 제외 및 single segment fast path 유지. 정확한 write bytes, frame 및 lease count 회계, source retain/release 보존. 방송 전용 20/100/500(+가능하면 1000) 독립 cipher와 느린 수신자 혼합 검증. 방송 게임 호출부는 범위 제외.
- 32/64/256/4096B, window1/16/32/256, 단일·다중 수신자에서 copied bytes/req, writes/1000 responses, CPU, latency를 측정한다. SocketChannel.write 호출 카운트와 OS syscall 실제 횟수는 구분한다.

## R05. 외부 생산자 Mailbox 저할당화 — W07 / A16/A18

대상: `SelectorTransport.Connection.submit`, `Owner.offer/poll/queueClose`, 기존 CIO ingress 계약.

- 현재 owner mailbox는 synchronized ArrayDeque이고 외부 submit에서 OwnerTask wrapper가 생성된다. same-owner inline 경로는 유지한다. 다른 owner, NPC, DB callback 등 외부 생산자별 실제 빈도와 객체 할당을 확인한다.
- 실제 비용이 클 경우에만 기존 큐 내부를 bounded reusable 슬롯/primitive sequence 기반 MPSC 또는 기존 동일 목적 구조로 대체해 비교한다. 불필요한 per-task wrapper를 먼저 줄인다. 별도 worker, 중복 queue, 무제한 queue 금지.
- enqueue publication 순서, 다중 producer FIFO 계약, 예약 후 미게시 producer 정지, full queue 거절, 정확히 한 번 execute/cancel, close control 우선 처리, lost wakeup 방지, 종료 선형화 지점을 명시한다.
- admission 이후 execute가 보장되지 않는 경우 취소/lease 회수가 필수다. 일반 큐 포화가 close 제어를 막지 않아야 한다. reserved-but-unpublished head 때문에 owner가 무기한 spin하거나 select/timer를 굶기지 않도록 한다.
- 기존 command/ready budget 및 timing wheel fairness 유지. producer 1/2/4/8/20, burst/idle, stalled producer, wakeup race, rejecting/closing/stopping, callback throw 및 restart 검증. CPU/command, allocation, p99 ingress, wakeup, timer drift, queue depth를 A/B한다. monitor 기존 구조보다 개선이 없으면 유지 결정으로 종료한다.

## R06. 최종 검증 및 완료 기준 — W09~W12

1. 모든 변경은 직전 최신 소스에서 진행. 변경별 isolated A/B와 전체 조합 재측정. 기존 Linux/Windows JDK21 compile, 독립 raw/framed, 실제 game fixture, 방송 42종/1,008벡터, 100회 재시작, 1,000 RST 재접속 회귀 유지.
2. 안전성 추가: 동시 SEND + read pressure + close + deadline, partial attach/owner 실패, shutdown timeout/retry, pool exhaustion, slow peer 1%/10%, 게임 timer fairness. 오류가 다른 연결이나 owner를 멈추지 않는지, 대여=반환, 세션/FD 회복 확인.
3. 동일 머신/JDK/CPU/heap/transport 옵션, 동일 요청/응답 payload와 socket 옵션, 부하 생성기 CPU 분리, 측정 순서 교차. 30초 이상 warmup/measure 및 독립 JVM 5회 이상을 기본 목표로 한다. 공유 CI 짧은 screening과 실제 성능 수치는 분리한다.
4. raw echo와 length-framed는 RKern / Netty NIO / native epoll / io_uring 동일 조건, 게임 리스너는 이전 RKern vs 최신 RKern을 별도 비교한다. Netty 게임 adapter가 없으면 게임 경로 우위라고 표시하지 않는다. 1/16/128/1024/5000 connections, 1/16/256 windows, 64B/256B/4KiB, 1/2/4/8 owners 중 대표 matrix를 구성한다. Windows WEPoll은 동일 Windows 호스트 비교.
5. throughput, CPU/req, alloc bytes/req, RSS/direct/backing·pool active/idle, p50/p99/p99.9/max, error/reject, GC/safepoint, lock/CAS, copy bytes/write calls, per-owner 부하·timer drift. p99 단위 통일. closed-loop뿐 아니라 paced/open-loop에서 offered load, completed, backlog, dropped와 coordinated omission을 분리 기록.
6. 5,000 연결·1,000 active paced 60초 테스트를 유지하고 전원 활성/slow peer 혼합/30~60분 soak로 확대한다. 40ms pace는 포화 처리량 시험이 아님. 실제 캐릭터 이동/공격/스킬/방송/MySQL 부하는 운영 자료가 있을 때만 완료 판정. 실클라 및 DB 없는 테스트를 실운영 동접으로 표현 금지.
7. DirectTail 카운터는 새 경로의 실제 hit를 계산할 수 있게 시험 fixture를 보완한다. 기존 CSV의 commit_hits/byte_append_hits는 제거된 실험 경로 항목이라 새 DirectTail 사용률을 설명하지 못한다.
8. `FINAL_IMPLEMENTATION_KO.md` 또는 후속 결과 문서에 R01~R06 및 A01~A30, W01~W12 대응, 실제 변경 파일/커밋, 원본 CSV·환경·CI 링크를 기록한다. 완료/기각/유지(근거)/부분/외부환경 미검증으로 구분한다. 성능 개선이 확인되지 않은 후보는 기본 경로에서 제거하고 그 사유를 남긴다.

## 작업 순서

R01 READ local → R01 SEND lifetime/remote return → R02 실제 writer 확대 → R03 WRITE 동기화 → R04 복사/Gather → R05 Mailbox → R06 통합 회귀 및 성능 검증. 독립 변경별 결과를 남기되 작업을 불필요하게 중단하지 않는다.

최종 목표는 아키텍처 교체나 기능 추가가 아니다. **기존 패킷 순서·암호화·자원 수명·안정성은 그대로 보존하고, 실제 게임 네트워크 Hot Path에서 반복되는 비용을 줄이는 것**이다.
