# JProRing 최신 네트워크 — 실제 잔여 중복 제거와 Linux 고부하 재검증
2026-11-20 | 기준: [5b54e34](https://github.com/choeyeonggeol16-sketch/1234/commit/5b54e3401f99ca08067c3c8deae7ec868fcc4c7d) | 브랜치: rkern/unified-jproring-game-20261120

## 1. 구현 범위 확정
이 문서는 이전 N01~N15 감사의 후속 지시이며, 이전 보고서의 광범위한 후보 목록을 구현 대상으로 간주하지 않는다. 이미 끝난 작업을 반복하거나 클래스 개수를 줄이기 위해 정상적인 구조를 변경하지 않는다. 동일/유사 실행 책임을 적절히 합치고 실제 중복 참조, 상태, 할당, 불필요한 동기화 및 전달 비용을 제거하는 것이 목표다.
JProRing의 순수 Java 21 멀티 Selector, 기존 CIO 샤드 소유권, NC식 순서 보존 및 SEND 병합 설계는 유지한다. 새 worker/queue/native/FFM, 다른 네트워크 엔진으로 교체 금지. 네트워크와 무관한 게임 코드, DB, PSS, NPC, 스킬, 상점, 방송에는 손대지 않는다.

재작업 제외: 이미 삭제된 CSelectorRawIo, CIPConnectionCounter, CIOObject 및 이전 CUserSession; IP live/retained 단일 registry와 listener restart 보존, lifecycle bits/retained set, byte[512] callback tags, owner READ/WRITE monitor 제거, shard fallback 제거, 로그인 deadline 통합, 문자열 scratch 공통화 및 구 telemetry 제거. 기존 5b54e34의 개선을 그대로 살린다.

**명시적 제외:** N14 timing wheel/CSlot, N09 CIOBufferEx/CIOFrame/CIOSlotPool (서로 다른 소유권 및 수명 계약, 측정된 중복 없음), N12 방송, N11 session/GM/CacheD monitor (불필요함 미입증), N08 standalone framed stack (game raw path에서 해당 pool/reader/send queue를 할당하지 않음), N15 단순 설정 namespace 정리. N04 queue 통합은 별도 필수 작업이 아니다. 정상적인 bounded mailbox/packetIngress의 서로 다른 책임을 보존하며 아래 R02에서 실제 중복 전달이 확인된 edge만 고친다. 기존 좋은 버퍼 풀·타이밍 휠·암복호화·송신 병합을 재설계하지 않는다.

## 2. R01 — 동일 소켓 객체/어댑터 통합 (핵심 구현)
현재 CUserSocket → CSocket → CSelectorTransport.Connection 구조. CSocket은 216줄의 completion adapter이다. CSocket.java 13–77에 userSocketOwner, javaPeer, remote, closing/ioDrained, identity, 2 OwnerCommand 및 4 callback 필드가 남아 있다. CIOServer.sockets에서 Connection→CSocket map을 유지한다. CUserSocket에도 자체 수명, CONNECTED 및 session 정보가 있다. 따라서 동일 TCP 연결의 물리 I/O/게임 세션을 이어주는 adapter 객체와 reciprocal reference 제거 가치가 있다. 지난 수정에서 이미 owner read/write monitor 및 armed buffer 상태를 없앴으므로 다시 만들거나 제거 작업을 반복하지 않는다.

- CSocket의 필요한 기능을 CUserSocket에 편입하고 production CSocket 객체 및 단순 forwarding API를 제거. Connection은 실제 SocketChannel/SelectionKey/owner의 책임을 가진다. CUserSocket과 중복된 상태/참조가 있으면 단일화하며, standalone 계약 분리 때문에 독립 Connection이 필요한 경우 이름만 보고 억지로 합치지 않는다. 게임 소켓 attachment/terminal callback이 direct CUserSocket을 사용하도록 실제 호출 경로 간소화. pending raw I/O는 기존 Selector owner에 유지한다.
- CIOServer 등록 map, account/MAC/API 및 early close/bind, test fixture 생성부의 참조를 전수 치환. 게임 소켓 생성 이전 실패/handshake 거절과 완료를 정확하게 처리. 단순 wrapper 이름 변경, 같은 객체/락을 다른 위치에 그대로 이동해 개선이라고 주장하지 않는다.
- foreign WRITE는 기존 handoff 하나를 사용. 현재 CSocket.java 173–213의 monitor는 실제 cross-thread pending argument 전달을 보호한다. CUserSocket의 기존 serializer/lease와 합칠 수 있는 경우만 동기화 단일화; 별도의 queue/monitor 추가 금지. owner-only 상태와 실제 cross-thread state를 분리하고 중복된 per-connection callback/command 객체 또는 self-reference를 줄인다. 
- session cleanup 이전 IP reservation, graceful/abort close, transport drained/cleanup 양쪽 완료, stale session, reconnect, account/MAC 중복 등록 및 capacity, 기존 test factory 계약 보존. 종료 관련 새 비동기 thread 또는 동기 DB logout 도입 금지.
- 수용: 실제 production 객체 그래프 및 참조 수 감소, CSocket wrapper 제거, armed read/write 및 foreign handoff 정확히 한번, 컴파일과 TCP 회귀 통과. 안정성을 위해 필수 책임을 남겨야 하는 경우 근거와 대안 비용을 명시하며 중복 코드만 정리한다.

## 3. R02 — 남은 완료 이벤트 중복 전달과 serializer 비용 (실측/증명 조건)
근거: CUserSocket.java:730–793 READ/WRITE completion; 3353 이후 serializeSocketCallback; _stateLock, SOCKET_PENDING/TICKET/DRAINING 및 512슬롯 ring.
READ idle fast path는 이미 READ_KICK CAS 후 직접 drain하고, 활성 pump 재진입 시 READ_EVENT를 사용한다. WRITE도 이미 inline defer/완료 prefix retire/canExecuteOwnerControlDirect가 있다. 이런 최적화는 반복 구현하거나 hot path를 다시 복잡하게 만들지 않는다.
- R01 이후 실제 Selector owner에서 발생하는 정상 READ/WRITE 완료와 foreign rejection/close callback 실행 스레드를 전수 구분. 같은 owner에서 불필요하게 completion publish→CAS→ticket→drain을 다시 거치는 실제 경로만 직행시킨다. 기존 직접 경로가 이미 최적이면 그대로 둔다.
- 동일 요청이 Selector mailbox와 CIO packetIngress를 연속 통과하는지 실제 호출 그래프/테스트로 확인. redundant enqueue/wakeup이 있는 경우만 없애며, 별도 CacheD/world/session replay와 transport command/close priority 경계를 합치지 않는다.
- 송신 serializer는 현재 외부 생산자도 드레인할 수 있다. owner라는 이유만으로 WRITE_EVENT/CAS/lock/ring을 제거하지 않는다. 미공개 FIFO ticket, 앞선 SEND의 prefix 완료, staged reservation, 재진입/inline complete, partial gather, lease 반환, backpressure와 reject/close를 유지한다. 분리된 암호화 순서나 재정렬 금지.
- byte callback 배열 축소는 완료됐으므로 다시 작업하지 않는다. 나머지 ring 저장 영역/동기화는 실제 state 중복과 충분한 FIFO/수명 증거가 있는 부분만 축소한다. 512 capacity를 임의로 줄여 고부하 성능/안전성을 악화시키지 않는다.
- 개선 후보별 전후 CPU/req, p99/p99.9, throughput, 할당을 동일 harness로 A/B. 이득 없는 실험은 채택하지 않는다. 단순 CAS 호출 개수 감소를 성능 개선으로 간주하지 않는다.

## 4. R03 — 접속 생성/등록의 같은 책임 및 잔존 얇은 API
- 현재 Server.activateAccepted → IServerCallback.CreateSocket(CDummyServerCallback) → CUserSocket 생성. CDummyServerCallback은 이제 별도 shard balancing/counter를 갖지 않는다. production game factory가 단일 구현이면 실제 생성 및 admission orchestration을 Server/CIOServer의 적절한 한 경계에 합치고 불필요해진 factory wrapper를 제거. 하위 Selector transport의 고유 책임, 실제 test factory 요구는 보존. l1j.server.Server bootstrap은 대상 아님.
- CIOServer.sockets.size 제한, Server.admitted 제한, Selector owner admission이 동일 수량/시점/실패 정책인지 대조. 실제 중복일 때만 하나로 통합. transport live vs retained cleanup, per-owner/global capacity는 서로 다를 수 있으므로 카운터를 기계적으로 삭제 금지. IP registry 개선은 이미 완료. 계정/MAC registry 보호는 유지.
- CUserSocket.getConnection()이 자기 자신을 반환하는 alias 등 R01 이후 의미 없는 접근/동일 타입 중복 체크 및 사용하지 않는 어댑터 API를 실제 call sites 전수 치환하여 제거. client()의 nullable/transport-only 의미는 보존. 통계 삭제 이후 남은 호환 API가 실제 미사용일 때만 삭제. account registry 안전 상태, 오류 로그, 현황 조회는 성능 telemetry와 구분.
- 실행 시 반복 할당되는 adapter/forwarder/람다 및 중복 참조 제거 위주. 단순히 클래스가 작다는 이유로 삭제하지 않는다. 실제 개선 없는 config/타이밍/버퍼 구조 변경 금지.

## 5. R04 — 필수 안전성 검증과 확인된 결함 수정
- 현재 5b54e34 커밋 설명의 Java21 compile/20 producer/96 reset-reconnect/5000 connections는 자체 보고이며 이 후속 감사에서 독립 실행하지 않았다. 최신 수정본으로 다시 전체 dependency compile 및 실제 TCP 회귀. 실패 시 원인 수정 및 재실행.
- 기존 SelectorCioIntegrationTest를 보존·확장. 20개 producer의 동일 소켓 cipher FIFO, 순차 수신, 정확한 opcode 처리, merged SEND byte equality, fragmented/coalesced frames, partial/zero write/gather, unpublished ticket/prefix retirement/reentrant encode, rejection/slow peer/backpressure, owner shutdown/cancel, stale reconnect 및 slot/lease 전체 회수.
- 초기 handshake 실패, IP/MAC/account 중복, listener restart 중 이전 logout pending, 96회 이상 reset/reconnect, 5000 connections connect/disconnect, timed close/retry/deadline 테스트. full buffer drain 후 retained registry 제거의 양 순서 테스트. Windows JDK21 Selector에서도 회귀.
- 스킬/상점/방송/PSS 게임 기능에 영향 없는지 검사하되 해당 소스 무단 수정 금지. 범위 내 재현 가능한 결함은 실제 수정하고 회귀를 추가, 외부 결함은 보고.
- 특정 최적화가 안전성 또는 고부하 성능을 악화시키면 그 변경만 되돌리고 기존 좋은 경로를 유지. 잘 작동하는 구조를 대체할 명분 찾기 금지.

## 6. R05 — Linux 고부하 재벤치마크 (필수)
비교 대상 4개: 최신 RKern Java Selector(JProRing), Netty 4.2.21.Final NIO Selector, native epoll, native io_uring. Linux JDK Selector 자체도 epoll 기반이므로 Java readiness implementation vs Netty transport implementation 비교이다. Windows 실행 결과를 Linux native transport 수치처럼 사용 금지. 지원되는 Linux 환경에서 native transport 초기화/실사용을 검증하고 silent fallback 금지.

기존 [고부하 결과](https://github.com/choeyeonggeol16-sketch/1234/blob/62744062a8449a88f9dccec2649924c21ece088f/rkern/docs/jproring_linux_benchmark_20261120/high_load_20261120/REPORT_KO.md) (측정 소스 62e5933):
| 5000 지속 연결 | req/s | RTT p99 ms | CPU μs/req | peak RSS MiB |
|---|---:|---:|---:|---:|
| RKern | 360375 | 136.79 | 3.39 | 120.7 |
| Netty NIO | 181522 | 287.19 | 9.17 | 147.9 |
| Netty epoll | 185823 | 311.51 | 7.23 | 143.8 |
| Netty io_uring | 275369 | 171.60 | 6.81 | 145.7 |
기존 결과는 256B/framed/window8, 2 owners, Ubuntu 24.04, JDK21, CPU 4개 runner에서 3회 중앙값(성공 epoll 2회)이다. 최신 개선의 성능으로 오인하지 않는다. 이전 단일 연결 결과는 io_uring이 앞섰고, 모든 부하에서 이겼다는 뜻이 아님.

### A. 동일 조건 재현 및 최신 A/B
- 기존 run.py/TransportServer.java harness를 재사용. RKern 최적화 전 5b54e34와 최종 개선본, Netty 3경로를 같은 Linux runner/CPU quota/kernel/JDK/heap/GC/IO owners/부하 생성기/socket options/프레이밍 및 payload 내용에 맞춰 비교. Netty version 고정 및 실제 transport log 확인.
- 우선 기존 1024/5000 지속 연결, window8, payload256B, 2 owners를 그대로 재현한다(아래 기존 명령). 최신 source와 baseline의 정확한 SHA 및 환경 기록. 1/16 연결도 기존 비교용 회귀로 측정.
- 각 조건 최소 5회 성공 중앙값/범위(이전 재현용 3회와 별도로 반복), 실행 순서 순환, 동일 warmup/측정. 기존 1.5s warmup/2.6s 측정 조건 재현 후 안정 구간 10s 이상 warmup/30s 이상 측정 추가. 실패한 회차와 원인 전부 기록. 실패를 표에서 조용히 제외하지 않는다.
- 고부하 추가: 1024/5000 연결에서 window1/8/32, payload 64/256/4096B, 고처리량 16/window256; 비용이 과하면 대표 부하 조합을 선택하고 명시. slow reader 혼합과 burst/connection churn을 별도로 검증. 5000 연결 장시간 soak(가능하면 30~60분), 풀 회수와 retained heap/GC 점검.
- 모든 backend에 같은 scoreboard, offered load, 정확한 completed request 집계. wire payload/프레이밍 및 응답 검증. closed-loop 외 같은 고정 유입률 부하에서도 tail latency 비교(가능한 경우, 미실행 명시). 클라이언트 병목/runner CPU limit 확인. spike나 timeout 발생 시 기록.

기존 재현 명령:
```sh
python3 -u rkern/docs/jproring_linux_benchmark_20261120/run.py \
  --framed --owners 2 --workload persistent --clients 1024 --clients 5000 \
  --window 8 --payload-bytes 256 --trials 3 --warmup-ms 1500 --measure-ms 2600 \
  --output /tmp/rkern-high-load
```
실제 최신 harness 옵션을 확인하고 변경이 필요한 경우 테스트 코드를 수정한다. 위 명령 결과를 실행하기 전 예측해서 작성 금지.

### B. 측정 범위 분리 — 실제 게임 소켓 변경 효과
기존 4-way benchmark는 CSelectorTransport raw echo 경로이며 CUserSocket의 게임 framing/cipher/serializer/SEND 병합/lease/lifecycle을 모두 실행하지 않는다. 따라서 R01/R02의 효과를 A의 결과만으로 판단하지 않는다.
- 기존 GameSendBenchmark/SelectorCioIntegrationTest 등 production game listener 경로 테스트를 최신 API에 맞춰 재사용. 실제 CUserSocket에서 동일 sender/recipient workloads, 암복호화 FIFO, 패킷 처리와 송신 병합을 수행하는 baseline vs final A/B, 1024/5000 연결 및 20 producer fanout/SEND 경쟁 비교. 테스트 payload와 작업량 고정, 실제 game path를 사용하는지 확인.
- 네 경로를 동일 game-level protocol/crypto/packet dispatch workload로 비교할 수 있는 경우에만 그 수치를 Netty와 직접 대조. Netty raw echo vs RKern 실제 게임 패킷 비용을 같은 표에서 성능 우열로 해석하지 않는다.
- 실제 월드/DB 포함 5000명 운영 결과가 아닌 것을 명시. 사용자에게 여러 JVM 성능 toggle을 요구하지 않는다.

### C. 보고서 및 채택 기준
각 count별 throughput/connection/sec, RTT p50/p95/p99/p99.9, server CPU μs/response, peak RSS, Java heap used/retained, direct buffers, allocation bytes/op, GC, failure/reject, 종료 후 lease/registry 회수. kernel socket/io_uring 메모리가 RSS/direct 지표에 포함되는 범위 명시. 비교 baseline/final의 절대값 및 상대 변화율과 raw per-run CSV, environment.json, 실행 명령, Actions URL 업로드. 측정에 필요한 계측은 test/JFR/OS 외부에서 수행하고 production telemetry 복구 금지.
성능 개선이 반복 측정 변동성 안에 있거나 실제 game path에서 악화되면 무리하게 채택하지 않는다. 가장 빠른 안전한 경로 유지. 데이터 부족은 미측정으로 표시, 성능 승리/버그 부재를 단정하지 않는다.

## 7. 완료 기준
R01의 실제 소켓 adapter/중복 책임 통합, R02의 확인된 불필요한 전달만 최적화, R03의 진짜 중복 생성·호환 경로 정리, R04 회귀·버그 수정, R05 Linux 4-way 고부하 + production game path A/B 검증. 실익이 없거나 보호가 필요한 항목은 추가 변경 없이 근거를 남긴다. 이전 N01~N15를 다시 전부 수정하는 것으로 완료하지 않는다. 이번 문서 자체는 정적 소스 검사 및 구현 지시이며 실제 코드나 테스트를 실행하지 않았다.

## 8. 현재 소스 참조
- [core/connection/CUserSocket.java](https://github.com/choeyeonggeol16-sketch/1234/blob/5b54e3401f99ca08067c3c8deae7ec868fcc4c7d/rkern/src/l1j/server/server/model/network/core/connection/CUserSocket.java)
- [core/transport/CSocket.java](https://github.com/choeyeonggeol16-sketch/1234/blob/5b54e3401f99ca08067c3c8deae7ec868fcc4c7d/rkern/src/l1j/server/server/model/network/core/transport/CSocket.java)
- [core/transport/selector/CSelectorTransport.java](https://github.com/choeyeonggeol16-sketch/1234/blob/5b54e3401f99ca08067c3c8deae7ec868fcc4c7d/rkern/src/l1j/server/server/model/network/core/transport/selector/CSelectorTransport.java)
- [core/transport/CIOServer.java](https://github.com/choeyeonggeol16-sketch/1234/blob/5b54e3401f99ca08067c3c8deae7ec868fcc4c7d/rkern/src/l1j/server/server/model/network/core/transport/CIOServer.java)
- [core/server/Server.java](https://github.com/choeyeonggeol16-sketch/1234/blob/5b54e3401f99ca08067c3c8deae7ec868fcc4c7d/rkern/src/l1j/server/server/model/network/core/server/Server.java)
- [core/server/CDummyServerCallback.java](https://github.com/choeyeonggeol16-sketch/1234/blob/5b54e3401f99ca08067c3c8deae7ec868fcc4c7d/rkern/src/l1j/server/server/model/network/core/server/CDummyServerCallback.java)
- [thread/CIOThreadCallback.java](https://github.com/choeyeonggeol16-sketch/1234/blob/5b54e3401f99ca08067c3c8deae7ec868fcc4c7d/rkern/src/l1j/server/server/model/network/thread/CIOThreadCallback.java)
- [core/connection/CUserSocketMap.java](https://github.com/choeyeonggeol16-sketch/1234/blob/5b54e3401f99ca08067c3c8deae7ec868fcc4c7d/rkern/src/l1j/server/server/model/network/core/connection/CUserSocketMap.java)
