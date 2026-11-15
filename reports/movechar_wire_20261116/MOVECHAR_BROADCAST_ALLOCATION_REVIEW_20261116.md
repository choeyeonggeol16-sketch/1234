# RKern / L1J — SC_MoveChar 공유 브로드캐스트·패킷 생성 할당 최적화 비교

- 작성: 2026-11-16 (KST)
- 범위: **플레이어/NPC 이동 패킷의 17바이트 직렬화와 공유 방송 버퍼의 소유권**. 게임 좌표 갱신, 이동 판정, AOI 선택, CIO/IOCP SEND 순서, 소켓 암호화는 변경 대상에서 제외.
- 기준 소스: `downloads/RKern_Broadcast_NonSC_Phase64_FULL_SRC_20261116.zip` (SHA-256 `1fd040e4bb4fe89c9c8fe1d8e5f6c2877fbfb6e990f5e551016fe8dc1557a23d`), 66차 브로드캐스트 감사에서 동일 ZIP 유지.
- 문서 성격: **설계·정적 코드 검토 + 별도 Java 21 마이크로벤치마크**. 운영 서버 전체 부하 검증이나 실제 소스 패치가 아니다.

## 1. 실제 현재 코드 — 무슨 비용이 발생하나

`src/l1j/server/server/model/network/core/sc/packet/SC_MoveChar.java`에서 `broadcast(cha,x,y,heading)`는 `broadcastWire(...)`를 한 번 호출한다. 현재 `broadcastWire()`는:

```java
return ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN)
    .putShort((short) 17).put((byte) Opcodes.S_MOVE_OBJECT)
    .putInt(objectId).putShort((short) x).putShort((short) y)
    .put((byte) heading).put((byte) 129).putInt(0).array();
```

반환된 `wire`는 `CSharedBroadcast.send(cha,wire,true,-1,null)`로 들어간다. 이 메서드는 `L1World.borrowVisiblePlayerFast(cha)`에서 주변 수신자를 얻고, `CTLMFilter.sameWorldOther(...)`로 **본인/다른 맵을 제외**하며, 연결된 수신자의 소켓에 `shared.send(...)`를 수행한다. 마지막에는 빌린 목록을 반환한다.

`CAsyncSendConstBuffer` 생성자는 다음처럼 원본을 **무조건 복제**한다.

```java
wire = frame.clone(); // one defensive copy, never a per-recipient copy
```

이 방어적 복제는 비동기 SEND 도중 호출자가 원본 배열을 수정하는 문제를 막는다. 각 수신자 SEND는 `CAsyncSendConstBuffer.send(CUserSocket,int)`로 들어가며, **16 KiB 이하 + ConstBuffer 지원**이면 공유 Native source 등록을 시도하고, 그렇지 않은 연결 또는 조건에서는 `target.sendFrozenWire(wire)`를 사용한다. 후자는 **구형 패킷 직렬화로 돌아가는 fallback이 아니라, 이미 직렬화된 동일 바이트의 소켓 FIFO 전송 경로**다.

이동 한 건에 대해, 최소한 관찰되는 패킷 바이트 배열은 **17바이트 생성 배열 1개 + 17바이트 방어적 복제 배열 1개**다. 여기에 공유 버퍼 래퍼, 수신자 조회 및 네이티브/소켓 SEND 비용이 추가될 수 있다. 소켓별 암호화·전송까지 1회가 되는 뜻은 아니다.

## 2. 선택 가능한 방법과 할당/안전성

| 방법 | 이동 1건 패킷 경로에서 예상 할당 | 추가 복사 | 장점 | 주의점 / 평가 |
|---|---|---|---|---|
| A. **현행 Heap ByteBuffer** | `byte[17]` 1개 + 방어적 복제 `byte[17]` 1개; `HeapByteBuffer`는 JIT 제거 가능 | 1회 | 표준 직렬화, 기존 검증 | 정적 소스에는 ByteBuffer 생성이 보이나 JIT에서는 반드시 할당되지 않음 |
| B. **직접 LE 비트 시프트** | A와 동일한 배열 2개 | 1회 | 외부 뷰·핸들 필요 없음, 코드 생성 단순 | 수동 필드 오프셋을 회귀 테스트로 보호해야 함 |
| C. **static VarHandle LE view** | A와 동일한 배열 2개; 핸들은 클래스 초기화 1회 | 1회 | `int/short` 직접 기록, 오프셋 가독성 | 실제 CPU 개선은 JIT·머신에 따라 달라짐 |
| D. **불변 17B 템플릿 `.clone()` 후 필드 패치** | A와 동일한 배열 2개 | 총 2회 clone (템플릿 1 + 방어적 복제 1) | 고정 헤더·opcode·129를 미리 채울 수 있음 | 정적 템플릿 자체를 변경하면 안 됨; clone 횟수만으로 느리다고 단정 불가 |
| E. **신뢰된 소유권 이전(adopt-owned) + 버퍼 직접 작성** | `byte[17]` **1개** + 공유 버퍼 래퍼; 미들 배열/clone 없음 | **0회** (패킷 소유권 경계 기준) | 패킷 생성 구간의 40B 절감 가능, 직렬화 1회 | **현행 코드에 미구현**. 호출자가 넘긴 후 배열을 절대 수정하지 않는 계약·수명·예외/종료 검증이 선행되어야 함 |
| F. **ThreadLocal / 샤드별 스크래치** | 재사용 스크래치 자체 할당은 줄지만 비동기 송신용 독립 배열 또는 clone 필요 | 일반적으로 1회 이상 | 임시 작업 버퍼 반복 생성 완화 | ThreadLocal 바이트 배열을 그대로 SEND에 넘기면 다음 이동에 의해 내용 오염. 현재 17B 용도에 비추천 |
| G. **수신자별 기존 `CIOFrame` 직접 쓰기** | 공유 바이트 배열 생성을 생략할 수 있으나 소켓별 프레임 비용 존재 | 복제 없음(경로별) | 기존 socket FIFO 이용 | 수신자 N명에 대해 패킷 N번 직렬화. 방송 목적에서는 비추천 |
| H. **공유 Native/FFM 메모리 직접 직렬화** | Java 배열을 생략할 설계 여지 | 구현 의존 | Native 공유 handle과 결합 가능 | 17B 전용 Native allocation/리퍼런스 수명 부담, 플랫폼 차이; 현행 메커니즘보다 낫다는 근거 부족 |
| I. **비동기 refcount 버퍼 풀** | 풀 hit 시 신규 배열 할당 감소 가능 | 구현 의존 | 극단적 부하에서 잠재적 재사용 | 수신자별 SEND 수명, 멀티-owner, 실패/종료에서 복제·오염 위험; 17B 때문에 별도 풀 추가 비추천 |

**중요:** 표의 '복사 없음'은 **패킷 생성 → 공유 버퍼 소유권 이전 단계**만 뜻한다. Native ConstBuffer 생성/소켓 FIFO/개별 암호화의 추가 복사를 없앤다는 주장이 아니다. 모든 방법에서 수신자당 TCP 송신은 여전히 필요하다.

## 3. Java 21 독립 측정 결과

- 런타임: OpenJDK **21.0.12.1**, Linux x86-64 / virtualized Intel Xeon Platinum 8573C (5 vCPU). `-Xms512m -Xmx512m`.
- 계측: `com.sun.management.ThreadMXBean.getThreadAllocatedBytes()`로 **패킷 생성 + 현행 방어적 clone**만 측정. 100만 회 × 5패스, 사전 워밍업. 반환 배열을 `volatile`에 게시해 제거 최적화 방지.
- 17B wire 형식과 LE 필드 값은 **기존 ByteBuffer 구현 모형을 기준으로 100,648개 입력**에서 후보 4가지의 모든 17바이트가 일치함을 확인. 사용한 opcode는 비교용 고정 상수이며, 실제 게임의 opcode 상수 조회/실제 클라이언트 검사로 간주하면 안 됨.
- E방식은 **현행 `CAsyncSendConstBuffer`를 수정·실행한 것이 아니라**, 방어적 clone을 생략했을 때의 *모형*이다.
- 실제 CIO, IOCP, Native ConstBuffer 생성, AES/rolling-key 암호화, AOI 조회, GC tail latency, Windows 런타임은 **미측정**.

| 벤치마크 구현 | 중앙값 할당 / 이동 | 실행시간 중앙값 (ns/이동) | 평가 |
|---|---:|---:|---|
| A. ByteBuffer + clone | **80 B** | 25.11 | 기준 |
| B. 직접 LE + clone | **80 B** | 22.49 | 단순한 대체안; 할당 개선은 없음 |
| C. VarHandle + clone | **80 B** | 23.17 | 가독성 우수; 할당 개선은 없음 |
| D. template clone + clone | **80 B** | 24.45 | 고정 헤더 작성 최소화; 할당 개선 없음 |
| E. 직접 작성 + 소유권 이전 **모형** | **40 B** | 14.66 | **복제 1회 감소 잠재력**; 아직 실제 통합 전 |

이 VM에서는 17바이트 배열 두 개가 각각 **40바이트(Java 객체 헤더·정렬 포함)**를 차지했다. A가 B/C/D와 동일한 80B였다는 것은 **이 측정에서는 JIT가 HeapByteBuffer 객체 자체를 제거했다는 뜻**이다. 따라서 `ByteBuffer.allocate(17)`를 코드에서 없애기만 해서는 실제 GC 할당이 줄어든다고 주장할 수 없다.

ns 수치는 소량 데이터 마이크로벤치마크의 **참고치**다. 패스 간 변동과 측정 잡음이 있고, 실제 운영 부하에서 순위/성능 차이를 보장하지 않는다. `trusted` 모형은 데이터 복사 자체를 하나 덜 수행하기 때문에 CPU 비교의 작업량도 다르다.

### 이동 이벤트 규모별 패킷 생성 배열 할당량 모델

| 이동 방송 건수/초 | 현행 80B × 건수 | 소유권 이전 모형 40B × 건수 | 절감 가능량 |
|---|---:|---:|---:|
| 5,000 | 0.40 MB/s | 0.20 MB/s | 0.20 MB/s |
| 20,000 (예: 5,000명 × 초당 4회) | 1.60 MB/s | 0.80 MB/s | **0.80 MB/s** |
| 50,000 | 4.00 MB/s | 2.00 MB/s | 2.00 MB/s |
| 100,000 | 8.00 MB/s | 4.00 MB/s | 4.00 MB/s |

이 표는 **패킷용 두 배열만 계산**한 모델이다. 공유 wrapper, 수신자 조회, 네트워크 큐/암호화/Native 버퍼의 할당은 제외하며, 실제 동접 5,000명의 이동 방송 빈도를 측정한 값은 아니다.

## 4. 어느 것이 실제로 더 나은가 — 결론

1. **변경 최소·안전 최우선:** 지금 구조를 유지하거나 **B(수동 LE)** / **C(VarHandle)** 중 명시적인 직렬화를 선택한다. 17B 전체 패킷 호환성이 검증 가능하고, CIO/IOCP·소켓 수명·방송 범위가 전혀 변하지 않는다. 그러나 측정상 **패킷 배열 할당은 80B로 그대로**라서 GC 개선 목적만으로는 B/C를 긴급 적용할 이유가 없다. B/C 미세 CPU 차이는 통합 벤치에서 재확인해야 한다.
2. **할당을 실제 줄이는 방향:** **E(소유권 이전 + 공유 버퍼 직접 작성)**가 비교 대상 중 유일하게 현행 방어적 clone 1개를 없애면서 **방송당 직렬화 1회**를 유지할 수 있는 설계다. **효과는 40B/이동 방송 + 배열 내용 17B 복사 제거 수준**에서 시작한다. 따라서 유효한 최종 후보지만, 첫 단계부터 무조건 채택할 만큼 거대한 절감은 아니다.
3. **권장하지 않는 변경:** `ThreadLocal` 재사용 배열을 소켓에 그대로 넘기기, 모든 수신자에게 각각 `CIOFrame` 재직렬화, 17B 전용 Native 풀/새 스레드/새 큐, 임의로 생성자 `wire.clone()` 제거. 이들은 안전성이나 운영 복잡도에서 불리하다.

### E 설계의 통합 조건 (패치 제안, 아직 구현하지 않음)

- `CAsyncSendConstBuffer`에 **명시적으로 소유권을 이전받는 전용 API**(예: `adoptOwned`)를 제공하되, 기존 일반 생성자의 defensive clone은 그대로 유지한다. 임의 호출자에게 노출되는 `byte[]`와 참조를 섞지 않는다.
- 이동용 `byte[17]`에 모든 필드를 기입하고, **완성된 이후에만** 공유 SEND가 시작되도록 한다. 그 이후 이동 스레드·호출자가 원본 배열을 변경하거나 재사용하지 않는다. 공유 클래스는 sealed/closed 여부를 확인하고, 외부에 수정 가능한 배열을 반환하지 않는다.
- `CAsyncSendConstBufferExceptSelf`의 **자기 자신 제외**, `CSharedBroadcast.send`의 **AOI 필터/borrow-release**, 소켓별 `sendFrozenWire`/ConstBuffer lease와 `close()` 참조 해제를 정확히 유지한다.
- 클라이언트 접속 해제, Native ConstBuffer 등록 실패, 혼합 owner, 패킷 전송 거부, 동시 이동, 송신 완료 지연 및 버퍼 재사용 경합에 대한 **바이트 불변성·lifetime 회귀 검사**가 필요하다.
- 기존 **Windows IOCP와 Linux TCP 양쪽에서** 동일 랜덤 이동 패킷, 0~7 방향, 경계 좌표, 20+ 수신자 AOI, 재접속·종료·오류 주입, GC allocation-rate·CPU/이동·p99 SEND 지연을 함께 비교한다. 실제 개선이 유의하지 않으면 변경하지 않는다.

### 추천 작업 순서

- **0단계(이미 확인):** 기본 17B 프레임 형식과 공유 방송 진입점 확인. 기존 배포 SRC는 그대로 둔다.
- **1단계(낮은 위험):** B/C를 별도 A/B로 비교하고, 실제 `SC_MoveChar` 통합에서도 동일한 opcode/헤더·좌표·방향의 wire byte parity가 유지되는지 확인. 눈에 띄는 CPU/할당 이점이 없으면 채택할 이유가 적다.
- **2단계(효과가 필요한 경우):** E 소유권 API를 전용 경로로 구현해 clone을 정확히 1회 제거. **추가 worker/queue/ThreadLocal/native 풀 없음**.
- **3단계(운영 채택 게이트):** 5,000명 목표와 가까운 실측 부하에서 이동 방송 throughput, bytes allocated/이동, CPU/이동, GC pause, 소켓 FIFO/중복·유실 검증. 이 단계까지 끝나야 더 빠르다고 확정한다.

**최종 권고:** **지금 당장 ByteBuffer 문법만 바꾸는 것으로 GC 개선을 약속하지 말 것.** 현재 패킷은 JIT 환경에서 이미 80B/이동 수준이며, 확실한 할당 절감은 **불변 공유 버퍼의 안전한 소유권 이전(E)**에 있다. 다만 E의 실제 이득은 40B/이동 방송 정도이므로 먼저 전체 부하에서 중요도를 판단하고, 도입 시에는 원본 배열의 동시 변경과 SEND lifetime을 반드시 증명할 것.

## 5. 근거 및 재현 방법

- 실제 SC 코드: [`SC_MoveChar_REVIEW.txt`](../broadcast_nonsc_phase60_20261116/SC_MoveChar_REVIEW.txt) (64차 ZIP 중 `SC_MoveChar.java` 정적 검토 사본)
- 실제 방송 대상 조회/자기 자신 제외: [`CSharedBroadcast_REVIEW.txt`](../broadcast_nonsc_phase60_20261116/CSharedBroadcast_REVIEW.txt)
- 실제 버퍼 clone/Native·FIFO 경로/참조 해제: [`CAsyncSendConstBuffer_REVIEW.txt`](../broadcast_nonsc_phase60_20261116/CAsyncSendConstBuffer_REVIEW.txt)
- 기존 이동 프레임 파리티 경로: [`migrate_identical_wire_fanouts.py`](../../patches/broadcast_nonsc_phase57_20261116/migrate_identical_wire_fanouts.py)
- 본 보고서의 독립 테스트 코드: [`MoveCharWireBench.java`](./MoveCharWireBench.java)
- 5개 패스 전부의 원시 출력: [`bench-output.txt`](./bench-output.txt)

실행:

```bash
javac MoveCharWireBench.java
java -Xms512m -Xmx512m MoveCharWireBench
```

상세 주의: 이 테스트는 `byte[]`가 비동기 전송 중 진짜로 변경되지 않는지를 검증하는 테스트가 **아니다**. E는 객체 수명 검증과 네트워크 통합 후에만 운영 채택 가능하다.