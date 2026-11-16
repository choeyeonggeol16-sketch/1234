# RKern `SC_MoveChar` 66차(Java 배열+공유 방송) vs 71차(NC식 Native 풀 직렬화) 비교 검토 요청

> **검토 요청 | 2026-11-16 | 대상: Java 21 / RKern CIO / Windows NIO.2·IOCP / L1 `S_MOVE_OBJECT` 방송**  
> **질문: 실제 동접 5,000명 목표에서 어느 설계가 CPU·지연시간·할당·메모리·안전성에 유리한가? 66차 유지, 71차 유지, 혹은 67차식 Java owned buffer가 더 합리적인가?**

## 1. 먼저 결론

**현재 확보한 근거만으로 66차와 71차 중 실서버 성능 승자를 확정할 수 없다.** 71차는 이동 방송 핫패스의 **중간 Java `byte[17]`와 방어적 복제를 제거**하고 수신자가 0명인 경우 패킷을 만들지 않는 장점이 있다. 반면 Native 풀 획득·반환, FFM 호출, Java 래퍼/메모리 뷰, 풀 경합 및 고정 16KiB 블록 점유 비용이 발생한다.

특히 **71차는 Native 버퍼 확보 실패 시 해당 이동 이벤트의 주변 방송을 전부 포기하는 반면**, 66차의 일반 공유 SEND 경로는 Native 공유 소스 생성 실패 시 준비된 Java wire로 `sendFrozenWire`를 시도한다. 이는 단순 성능 문제가 아니라 **정합성/진행성 회귀 후보**다. 안전성 해결과 측정 전까지는 '71차가 무조건 더 빠르고 NC와 완전히 동일하다'고 결론 내리지 말 것.

**비교 범위:** `SC_MoveChar.broadcast(...)`로 진입하는 **주변 유저 이동 방송**. 1:1 `SC_MoveChar.send(...)`는 두 버전 모두 기존 `beginDirectFrame(17)` + `WriteBuffer.writeC/D/H` + `commitDirectFrame`이고 이번 비교에서 변경되지 않았다. `S_ACTION`도 71차에서 같은 방향으로 수정되었으나 여기서는 이동 패킷만 정량 검토한다.

## 2. 비교 대상 및 실제 소스

| 대상 | 코드 기준 | 패킷 생성 및 전송 |
|---|---|---|
| **A / 66차** | [구 `SC_MoveChar` 추출](https://github.com/choeyeonggeol16-sketch/1234/blob/broadcast-nonsc-phase66-legacy-removal-20261116/reports/broadcast_nonsc_phase60_20261116/SC_MoveChar_REVIEW.txt) · [구 `CSharedBroadcast`](https://github.com/choeyeonggeol16-sketch/1234/blob/broadcast-nonsc-phase66-legacy-removal-20261116/reports/broadcast_nonsc_phase60_20261116/CSharedBroadcast_REVIEW.txt) · [구 공유 버퍼](https://github.com/choeyeonggeol16-sketch/1234/blob/broadcast-nonsc-phase66-legacy-removal-20261116/reports/broadcast_nonsc_phase60_20261116/CAsyncSendConstBuffer_REVIEW.txt) | Java `ByteBuffer.allocate(17)` → `byte[17]` → 공유 생성자에서 `clone()` → AOI → 지원 소켓에 Native ConstBuffer 1회 복사/공유 → FIFO/소켓별 암호화 |
| **B / 71차** | [현 `SC_MoveChar`](https://github.com/choeyeonggeol16-sketch/1234/blob/nc-direct-slab-phase71-20261116/reports/nc_direct_slab_phase71_java/SC_MoveChar.java) · [현 `CSharedBroadcast`](https://github.com/choeyeonggeol16-sketch/1234/blob/nc-direct-slab-phase71-20261116/reports/nc_direct_slab_phase71_java/CSharedBroadcast.java) · [FFM `CIOServer`](https://github.com/choeyeonggeol16-sketch/1234/blob/nc-direct-slab-phase71-20261116/reports/nc_direct_slab_phase71_java/CIOServer.java) · [71차 C++ 변경 스크립트](https://github.com/choeyeonggeol16-sketch/1234/blob/nc-direct-slab-phase71-20261116/patches/nc_direct_slab_phase71_20261116/migrate.py) | AOI → 최초 유효 소켓에서 FFM → 기존 `CIOBufferEx<16384>` 풀 확보 → 공통 `AssemblePooled`가 17바이트 직접 기록 → Native 풀 블록 참조 공유 → FIFO/소켓별 암호화 → 마지막 참조 반환 |
| **C / 67차 (선택적인 대조군)** | [67차 변경](https://github.com/choeyeonggeol16-sketch/1234/blob/broadcast-movechar-lazy-owned-20261116/patches/movechar_phase67_20261116/migrate.py) | AOI → 최초 유효 수신자에서 내부 소유 `byte[17]` 1개 생성 → `clone()` 없이 기존 공유 SEND. 불필요한 Java 배열 복사는 줄이지만 71차 Native 직접 직렬화는 사용하지 않음. |

### A — 기존 66차 이동 방송

```java
public static void broadcast(L1Character cha, int x, int y, int heading) {
    if (cha == null) return;
    int objectId = cha.getId();
    byte[] wire = broadcastWire(objectId, x, y, heading); // allocate(17)
    CSharedBroadcast.send(cha, wire, true, -1, null);      // defensive clone
}
```

구 `CSharedBroadcast.send`는 **패킷 생성과 공유 객체 생성 후** 주변 플레이어를 순회한다. `CAsyncSendConstBuffer` 일반 생성자의 `frame.clone()`은 다른 호출자가 수정할 수 있는 배열에 대한 방어이므로 안전성 이유가 있다. 공유 소스 생성 실패 시 `sendFrozenWire(wire)`가 존재한다.

### B — 71차 이동 방송

```java
public static void broadcast(L1Character cha, int x, int y, int heading) {
    if (cha == null) return;
    CSharedBroadcast.sendMove(cha, cha.getId(), x, y, heading);
}
```

```java
// CSharedBroadcast.sendMove 내부의 최초 유효 수신자 경로
shared = CAsyncSendConstBufferExceptSelf.forMove(
        socket, anchor.getId(), Opcodes.S_MOVE_OBJECT,
        objectId, x, y, heading);
if (shared == null) return; // pooled source 확보 실패 시 이벤트 전체 중단
```

```java
// CIOServer.createPooledMoveConstBuffer 내부: Java에서 별도의 wire 배열 없음
handle = (MemorySegment) createFormattedConstBuffer.invokeExact(
        server, 2, opcode, objectId, x, y, heading);
// Native CIOBufferEx<16384>에 2바이트 길이 + 15바이트 L1 본문 작성
```

71차의 `CAsyncSendConstBuffer.Source`는 **Java 객체**이고 `MemorySegment` 및 `ByteBuffer` 뷰, `AtomicInteger` 참조 상태를 가진다. `CAsyncSendConstBufferExceptSelf` 객체도 만든다. 즉 **'방송 Java `byte[]` 0개'이지 '방송 Java 객체 전체 0개'는 아니다.**

## 3. 구조와 예상 비용 비교

| 구분 | A / 66차 | B / 71차 | 해석 |
|---|---|---|---|
| 실제 L1 wire | 2바이트 LE 길이 + 15바이트 본문 = **17바이트** | **동일** | 프로토콜 차이 없음 |
| 방송당 Java `byte[17]` | **2개** (생성+clone) | **0개** | B가 배열 GC 면에서 유리 |
| Native 풀에 평문 복사 | 지원되는 Native shared SEND에서는 Java wire에서 복사 | 풀에 바로 필드 기록 | B가 해당 메모리 복사 1회를 제거 |
| 주변 수신자 0명 | 배열 2개 및 공유 래퍼 생성 후 AOI 순회 | AOI 확인 후 버퍼 미생성 | B가 확실히 유리한 경로 |
| 주변 수신자 1~N명 | 공유 평문 1회, 수신자별 SEND | 공유 평문 1회, 수신자별 SEND | **둘 다 기존부터 수신자별 재직렬화는 하지 않음** |
| Native 16KiB 풀 점유 | 공유 Native 경로가 수용될 때 확보 | 최초 유효 소켓에서 확보 | B는 Native ConstBuffer 미지원 소켓에서도 풀을 우선 확보 |
| Java FFM 전환 | Native 공유 소스 생성 시 발생 | Native 공유 소스 생성 시 발생 | **'A는 FFM이 전혀 없고 B만 FFM'이라는 비교는 틀림** |
| Java 래퍼 | 방송 래퍼 + 원본 배열/복제; Native 공유 시 `Source`도 발생 | 방송 래퍼 + `Source` + native `ByteBuffer` view/참조 상태 | **전체 할당 바이트/이벤트는 측정 필요** |
| Native 참조/풀 관리 | Native shared SEND 사용 시 기존에 존재 | 블록 자체를 source로 사용, 별도 `L1ConstBuffer` native 래퍼 제거 | B에서 wrapper `new` 1회 제거 |
| 소켓별 SEND 요청 | 기존 Native `NativeSendRequest` 및 FIFO | 동일 (per-recipient request `new` 남음) | 둘 다 원본 NC 전체와 byte-for-byte 동일하지 않음 |
| Native 풀 부족 | Java frozen wire를 이용한 기존 SEND 경로 시도 | `forMove()==null`이면 방송 중단 | **B의 진행성 위험** |
| Native source 미지원 연결 | Java wire에서 일반 FIFO SEND 가능 | 풀 확보 후 Native read-only view에서 일반 FIFO로 복사 | B는 불필요한 Native 경계 가능 |
| 자기 자신 제외/AOI/FIFO | 있음 | 있음 | 의도 유지. 실제 다중 게임 부하 회귀 필요 |
| 작은 패킷에서 CPU 우위 | 미측정 | 미측정 | 결과를 가정하지 말 것 |

**Native 블록 크기 주의:** 71차는 17바이트 패킷에 `CIOBufferEx<16384>`의 **16KiB 용량 버퍼를 대여**한다. 이는 이벤트당 매번 16KiB를 새로 OS 할당한다는 뜻이 아니라, **동시에 대여된 버퍼 1개당 약 16KiB 풀 공간을 점유**한다는 뜻이다. 대여 버퍼가 장기간 살아 있거나 풀 경쟁이 심하면 8K/16K 풀 압박·`exhaustions` 증가로 이어질 수 있다. 66차도 Native const 공유 시 풀 버퍼가 필요하므로 **동일 수신자 구성의 실측 점유량**이 중요하다.

## 4. 실제 수치로 확인된 것과 미측정인 것

### 과거 독립 마이크로벤치 (실서비스 SEND 아님)

[벤치마크 원본 출력](https://github.com/choeyeonggeol16-sketch/1234/blob/broadcast-movechar-optimization-report-20261116/reports/movechar_wire_20261116/bench-output.txt)

- Java 21의 **패킷 생성+clone만** 비교한 5회 실험: `ByteBuffer+clone` **80B/이벤트**, **25.11ns** 중앙값.
- 67차 내부 소유 배열처럼 clone 생략 모델: **40B/이벤트**, **14.66ns** 중앙값.
- 100,648건 17바이트 동등성 검사. 위 시간값에는 **AOI, Native 풀/FFM, cipher, IOCP SEND, 수신자 큐**가 포함되지 않는다.
- 따라서 위 25.11ns vs 14.66ns를 **66차 서버 전체 vs 71차 Native 전체 지연시간**으로 인용해서는 안 된다.

예를 들어 방송 20,000건/초에 매번 두 배열을 만들면 **해당 17바이트 배열 두 개만** `20,000×80B=1.6MB/s` 할당이다. 이는 총 GC/초가 아니며, 71차의 Java 래퍼 할당과 Native 풀 비용을 제외한 단순 모델이다.

### 71차에서 실제 통과한 검사

[Windows 검증 실행](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43778032594) · [71차 audit](https://github.com/choeyeonggeol16-sketch/1234/blob/nc-direct-slab-phase71-20261116/patches/nc_direct_slab_phase71_20261116/audit.json)

- MSVC x64 DLL 빌드; Java 21 전체 **1,710개** 컴파일; Native 자체 테스트.
- 이동·행동 패킷을 **서로 다른 암호화 상태의 소켓 4개**에 전송한 Windows IOCP 회귀, **10회** 통과.
- 71차 결과 ZIP SHA-256: `e04cdcf7df4a1bb30a6d6ae1b93d0d09cb4125f830e7b99f3b55ec833ff6d2d0`.
- **미검증:** 실제 5,000명, 분당 대량 NPC 이동, Native 풀 포화, 장시간 soak, Windows/Linux 공정 A/B 처리량·CPU·p99 비교.

## 5. 외부 리뷰어가 반드시 검토할 위험

1. **[우선순위 1: 이동 방송 누락]** 71차 `sendMove`의 `if(shared==null) return;`은 수신자가 존재해도 Native 풀 포화/서버 중단 경계에서 그 이벤트를 모든 주변 유저에게 미전송할 수 있다. 66차 `sendFrozenWire` 경로와 장애 진행성이 다르다. 풀 고갈 시 실제 게임에서 좌표 갱신 누락/시야 불일치가 발생하는지, 원본 NC에서 실패 시 조치가 무엇이었는지 확인 필요.
2. **[우선순위 2: 미지원 Native 경로]** 71차는 최초 연결 수신자가 `supportsConstBuffer()==false`여도 Native 풀 소스를 먼저 구성하고 Java direct FIFO에 복사한다. 66차는 이 경우 굳이 Native 소스를 만들지 않을 수 있다. 이런 연결 비중을 측정할 것.
3. **[우선순위 3: Native 풀 점유]** 17바이트 패킷도 16KiB 원본 풀 슬롯을 점유한다. 동시 SEND 지속시간, owner ring 포화 및 유효 인근 수신자 수와 함께 Native buffer occupancy/exhaustions를 평가할 것.
4. **[우선순위 4: Java 할당의 실제 총량]** 71차 `Source`, `AtomicInteger`, `ByteBuffer` view, 방송 wrapper, recipient send lease 등을 포함해 **JVM TLAB/스레드당 할당 바이트**를 측정할 것. Java 배열 두 개를 제거한 만큼 실제 총량이 줄지 않을 수도 있다.
5. **[우선순위 5: 경쟁/수명]** `Source.release()` 및 Native refcount의 exactly-once, 원본 Server 종료 시 살아 있는 lease, 다른 Server owner 수신자의 일반 SEND 경로, 암호화 상태·SEND FIFO, 블록 재사용 후 오염 가능성을 점검할 것.
6. **[원본과 구현 구분]** 실제 NC는 `0x41E6E4` 풀 확보, `0x41F694` 패킷 빌드, `0x70429C` 범용 Assemble, `0x924245` 참조 증가, `0x45C7AC` 소켓 실행기 경로가 확인된다. **NC가 L1의 17바이트 `S_MOVE_OBJECT`를 같은 형식으로 썼다는 뜻은 아니다.** 71차의 FFM adapter, `kind=2`, L1 `cdhhccd`, Java AOI 선택은 RKern 적응 구현이다.

## 6. 공정 A/B 실험 제안

**실험군:** A=66차, B=71차, 가능하면 C=67차 lazy+owned-array (해당 버전이 비교 대상으로 유효할 때)로 각각 최신 소스 기반 동일 게임 로직·DB·NPC 설정/서버 수·풀 크기를 맞춘다. 반드시 **Windows Java 21 실제 IOCP**에서 비교하고, Linux는 별도 transport 결과로 보고한다.

| 부하 | 목적 | 필수 관측 |
|---|---|---|
| 가시 수신자 **0명**, 대량 NPC 이동 | lazy 생성의 효과 | packet/sec, JVM allocated bytes/event, CPU/event, Native alloc 시도 |
| 수신자 **1명** | 가장 작은 유효 방송 | p50/p99 stage/send latency, heap allocation, native pool occupancy |
| 수신자 **10~30명** | 전형적 AOI 공유 성능 | bytes copied, Native SEND requests, FIFO p99, CPU/event |
| **다수 이동 동시 발생 + 5,000 접속** | 혼잡 시 진행성 | total CPU, GC, peak native slabs, pool exhaustion, rejected sends, client 좌표 불일치 |
| **의도적 풀 포화 + 연결 종료 + 셧다운** | 장애/수명 | 누락된 이동 이벤트, UAF/이중 반환, pool corruption, ref leak |

통제 조건: 동일 JVM/JDK, 하드웨어, 오브젝트/수신자 배치, 암호화, 패킷 빈도, CIO 네트워크/큐 구성. 웜업 후 반복 **최소 5회**, 중앙값과 p99를 모두 보고, 소켓/스레드 동시성 및 네트워크 바이트가 동일한지 확인. 엔드투엔드 이동 패킷을 클라이언트 또는 TCP 측에서 직접 카운트해 단순 서버 `send()==true`를 전달 완료로 착각하지 않을 것.

**관측 지표:** `req/s or move broadcasts/s`, CPU µs/broadcast, process CPU, JVM allocated B/broadcast 및 GC pause/s, Native pool active 8K/16K & `exhaustions/corruptions`, SEND backlog/거절 건수, 이동 전달 누락율, p50/p99 SEND enqueue→completion, disconnect/close 안전성. CPU profiling은 Java/JNI·FFM·Native 함수 비용을 분리하면 좋다.

## 7. 리뷰어에게 묻는 핵심 질문

1. **같은 동접/이동량에서 A(66), B(71), C(67) 중 어느 쪽이 최선인가?** throughput·CPU·JVM allocation·Native pool footprint를 각각 따로 판정해 달라.
2. 17바이트 이동 패킷에 16KiB slab을 빌리는 비용과 `Source`/FFM overhead는, Java 40~80B 배열을 제거하는 이득보다 작은가? 어느 이벤트 수·AOI 크기부터 역전되는가?
3. 71차 **풀 부족 시 전체 이동 방송 누락**을 허용할 수 있는가? 허용할 수 없다면 기존 CIO FIFO·참조 수명을 지키면서 어떤 진행성 보장을 해야 하는가?
4. `supportsConstBuffer()==false`, 다른 Native server owner, 종료/재접속 시 71차의 source→fallback 동작이 66차보다 더 비싸거나 위험한가?
5. NC의 원본 사용 방식에 더 가깝게 만들더라도, Java 21에서는 **더 단순한 67차 lazy+owned Java 배열**이 실용적인 최적일 가능성이 있는가?
6. 가능하면 **수치·프로파일·코드 경로**를 근거로 하나를 추천하고, 운영 채택 전에 반드시 고쳐야 할 사항을 구분해 달라.

---

**검토 요청 요약:** 'NC와 닮았다' 또는 'new byte[]가 없다'는 이유만으로 71차를 선택하지 않으려고 한다. 실제 이동 방송 17바이트를 **안전하게, 적은 CPU와 낮은 지연으로, 5,000명 규모까지 누락 없이** 보내는 가장 유리한 설계를 알려 달라. **현재 71차가 수치상 더 우수하다는 실험적 결론은 없다.**

**본 보고서는 분석 및 설계 비교 문서이며, 제품 코드는 변경하지 않는다.**
