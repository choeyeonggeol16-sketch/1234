# RKern opcode 분기 테이블 통합 최적화 검토 보고서

- 작성일: 2026-11-21
- 대상 저장소: `choeyeonggeol16-sketch/1234`
- 확인 기준 브랜치: `rkern/unified-jproring-game-20261120`
- 범위: 수신 opcode 디스패치 핫패스만. **운영 Java 소스 변경 없음.**
- 판정: **실제 성능 이득 미확정. 마이크로벤치 유망 후보이며 운영 채택 보류.**

## 1. 검토 목적

RKern 자체 Java 21 Selector/CIO 네트워크 코어의 초당 처리량과 CPU/패킷을 개선하되, 수신 순서, 복호화 순서, 세션 상태 검사, 필터 정책, 핸들러 실행 순서를 바꾸지 않는다. 이미 최적화된 기본 경로에 단순 분기/배열 접근을 더하는 식의 역최적화를 피한다.

## 2. 현재 코드에서 실제로 확인한 흐름

### A. `CUserSocket.dispatchPlaintext()`

소스: [CUserSocket.java L3169–3185](../rkern/src/l1j/server/server/model/network/core/connection/CUserSocket.java)

```java
final int opcode = decoded.readUnsignedByte();
if (this.checkPacketRateLimit(System.currentTimeMillis())) return false;
if (!allowed(opcode, this.getConnectionState(), this.getAccount() != null,
        this.getActiveChar() != null, this.isGameSessionReady()))
    return inboundHandlerConnected();

if (!CPacketHandler.isFastOpcode(opcode)) {
    CPacketFilter.isFilter(this, opcode);
} else if (CPacketFilter.isFilter(this, opcode) != CPacketFilter.RESULT_FILTER) {
    if (CPacketHandler.isDirectOpcode(opcode)
            || (this.isGameSessionReady() && !isSessionOpcode(opcode))) {
        dispatchDecodedFrame(opcode, decoded);
    } else {
        synchronized (this) {
            if (this.isConnected()) dispatchDecodedFrame(opcode, decoded);
        }
    }
}
```

실제 순서: 패킷 속도 제한 → 접속/캐릭터 상태 허용 검사 → 핸들러 존재 여부 → 동적 패킷 필터 → 직접 실행 여부 또는 세션 직렬화 → 디스패치.

- 필터 미등록 opcode도 `CPacketFilter.isFilter()` 호출은 유지된다.
- `allowed()`와 `isSessionOpcode()`는 opcode 및 현재 세션 상태에 따라 결과가 달라지므로 함부로 하나의 **정적** 허용 비트로 대체할 수 없다.
- `synchronized(this)` 경계는 현재 세션 실행 정책이며 제거 대상이 아니다.

### B. `CPacketHandler` 정적 배열

소스: [CPacketHandler.java L120–129, L395–403](../rkern/src/l1j/server/server/model/network/packet/CPacketHandler.java)

```java
public static final PacketHandler[] HANDLERS = new PacketHandler[256];
private static final boolean[] DIRECT_OPCODE = new boolean[256];

public static boolean isFastOpcode(int opcode) {
    return opcode >= 0 && opcode < 256 && HANDLERS[opcode] != null;
}
public static boolean isDirectOpcode(int opcode) {
    return opcode >= 0 && opcode < 256 && DIRECT_OPCODE[opcode];
}
```

- 기존에도 256칸의 평탄한 배열로 O(1) 조회한다. 해시맵이나 가변 객체 생성이 없어 구조적으로 가볍다.
- `dispatchDecodedFrame()`에서 다시 `HANDLERS[opcode & 0xFF]`를 조회한다([CUserSocket.java L3402–3406](../rkern/src/l1j/server/server/model/network/core/connection/CUserSocket.java)).
- JDK 21 JIT가 일부 경계 검사·중복 접근을 제거할 수 있으므로 **소스 코드에서 보이는 배열 조회 수가 그대로 CPU 비용은 아니다.**

### C. `CPacketFilter`는 동적으로 갱신된다

소스: [CPacketFilter.java L75–87, L108–155, L312 이후](../rkern/src/l1j/server/server/model/network/safe/CPacketFilter.java)

`CPacketFilter`는 256-byte 설정 테이블을 `AtomicReference<State>`로 게시하며, `load()/reload()`가 새 필터 정책을 교체한다. 필터는 캐릭터 마비·이동 불가·텔레포트 등 **현재 게임 상태**도 확인한다.

**따라서 필터 결과를 opcode 고정 `ROUTE_FLAGS`로 흡수해 캐시하는 방식은 현재 의미를 깨뜨린다.** 필터 호출·리로드 가시성은 보존해야 한다.

## 3. 검토한 최적화 후보

### 후보 A — 핸들러 조회를 한 번만 하고 재사용

`HANDLERS[opcode]`를 한 번 로딩한 뒤 유효성 판정과 실제 `execute()`에서 같은 참조를 사용하도록 전달한다. 기존 필터, 세션 소유권, SEND burst 진입·해제 지점은 그대로 유지한다.

잠재 절감: 일부 중복 `HANDLERS` 배열 접근 및 검사. **실제 이득은 미측정**. 정적 배열이 외부에서 변경될 가능성, 호출 사이 객체 수명과 핸들러 등록 정책도 확인해야 한다.

### 후보 B — opcode 정책 비트(`byte[256]`)로 유효성/직접 처리 여부 결합

예시 정책: `VALID=1`, `DIRECT=2`. 초기화 완료 후 정적 테이블을 구성해 기존 `isFastOpcode`/ `isDirectOpcode` 판정을 대체한다. 그러나 **실제 핸들러 실행에 필요한 `HANDLERS[]` 조회는 여전히 남는다.**

잠재 효과: 분기 및 테이블 조회 패턴 단순화. 반대로 별도 `byte[]` 읽기가 더해질 수 있어 **B가 A보다 빠르다는 근거 없음**.

주의: 공개 `HANDLERS[]` 변경 시 `VALID` 비트 불일치 위험. 현재 정적 등록만으로 충분한지 전 호출부 검색 및 회귀 검증 필요.

### 금지되는 단축 방식

- 현재 `CPacketFilter.isFilter()` 생략·결과 정적 캐싱
- `allowed()`나 실제 접속/캐릭터 상태 검사 생략
- 세션 opcode의 `synchronized(this)` 제거
- 복호화보다 opcode 분류를 먼저 실행하거나 수신 순서를 변경
- opcode마다 새 객체 또는 새 컬렉션을 생성하는 디스패처로 전환

## 4. 기존 독립 테스트 결과의 정확한 해석

2026-11-21 대화에서 진행한 **독립 Java 21 핫패스 A/B**에서는 opcode 분기 통합의 중앙값이 **+21.1%**로 보고되었다. 전체 후보군 시험은 총 50회, 조건별 반복 5회였다.

중요한 제한:
1. 이 수치는 **분기 처리만 반복한 마이크로벤치**의 결과이며, 실제 RKern 전체 TCP 서버의 req/s 개선율이 아니다.
2. `CUserSocket`의 필터·복호화·CIO 샤드·핸들러·SEND·상태 변화까지 실행한 통합 A/B가 아니다.
3. 본 보고서 작성 과정에서 해당 실험 원시 CSV를 별도로 재실행·검증하지 않았으므로, **+21.1%를 운영 성능 보증치로 쓰면 안 된다.**
4. JDK 21 JIT 컴파일, 분기 예측, 실 opcode 분포가 테스트와 다르면 이득이 사라지거나 역전될 수 있다.

**현재 판단: 실제 RKern 처리량 향상이 확실하다고 말할 수 없다. 기존 코드에 기능상 결함이 확인된 것도 아니다.**

## 5. 실제 채택을 위한 검증 기준

| 검증 영역 | 요구 사항 |
|---|---|
| 기능 등가성 | 256개 opcode의 인식/미인식, 필터 결과, direct 여부, 세션 동기화 경계 일치 |
| 동적 설정 | `CPacketFilter.reload()` 전후 동일 동작 및 동시 호출 가시성 |
| TCP 실제 경로 | READ → 복호화 → 디스패치 → CIO → SEND를 포함한 동일 소스로 A/B |
| 부하 조건 | 1, 1,024, 5,000 연결 / Window 1·8·고부하 / 256B·4KiB / 실제 opcode 분포 |
| 계측 | 처리량(req/s), CPU/패킷, p50/p99/p99.9, 객체 할당/GC, 오류·패킷 누락 |
| 반복성 | 동일 JDK·CPU 배치·가열 시간에서 교차 반복, 분산·중앙값 확인 |
| 승인 | 실제 엔진에서 의미 있는 처리량 또는 CPU 개선이 반복 확인되고 tail latency와 정확성이 악화되지 않을 때만 채택 |

## 6. 결론 및 권고

1. **운영 코드 변경 보류.** 기존 디스패치는 이미 256칸 O(1) 테이블이며 상당히 가볍다.
2. 개발 분기에서 A와 B를 각각 독립 A/B로 비교하되, **A(기존 핸들러 조회 재사용)**가 더 작은 변경이다.
3. 측정상 차이가 없으면 모두 폐기한다. 기능 안전성보다 마이크로벤치 수치를 우선하지 않는다.
4. **이번 커밋은 보고서 1개 추가만 수행하며 서버 Java 코드는 수정하지 않는다.**

---

### 소스 기준

- `rkern/src/l1j/server/server/model/network/core/connection/CUserSocket.java`: `dispatchPlaintext()` (L3169–3185), `dispatchDecodedFrame()` (L3402 이후)
- `rkern/src/l1j/server/server/model/network/packet/CPacketHandler.java`: `HANDLERS`, `DIRECT_OPCODE`, `isFastOpcode()`, `isDirectOpcode()`
- `rkern/src/l1j/server/server/model/network/safe/CPacketFilter.java`: 동적 필터 상태 및 reload
## 7. 송신 행동 패킷 묶음 암호화 (2026-11-21 추가 검증)

> 이 절은 같은 날 수행한 암호화 A/B 측정 결과와, 별도로 확인한 GitHub 소스 경로를 통합한 기록이다. 기존 opcode 분기 최적화(1~6절)와 서로 독립적인 후보이며 **운영 Java 코드는 수정하지 않았다**.

### 7.1 병목 후보와 기존 구현

소스:

- [CUserSocket.java — \`preparePackedEncryption()\`](../rkern/src/l1j/server/server/model/network/core/connection/CUserSocket.java): 2902–2929행
- [CUserSocket.java — \`encrypt()\`](../rkern/src/l1j/server/server/model/network/core/connection/CUserSocket.java): 181–195행
- [CPacketCryptor.java — \`encrypt()\`](../rkern/src/l1j/server/server/model/network/codec/CPacketCryptor.java): 8바이트 처리 및 6바이트 행동 패킷 전용 분기

현재 RKern의 **행동 패킷 전용 병합 SEND**는 여러 논리 프레임을 하나의 송신 버퍼에 담는다. 하지만 암호화는 8바이트 wire frame(헤더 2B + 암호화 대상 6B)마다 \`CUserSocket.encrypt()\`를 호출한다.

\`\`\`java
// 현행 _actionOnly 처리, 개념을 설명하기 위한 발췌
for (int offset = 0; offset < length; offset += 8) {
    if (!encrypt(writer, offset + CIOBufferPool.HEADER_BYTES, 6))
        return false;
}
\`\`\`

32개의 행동 프레임이라면 암호화 진입이 **32회** 발생한다. 각각의 RC4 상태 갱신을 제거할 수는 없지만, 공통 cryptor 조회, buffer/base/order 접근, 진입/분기 비용은 묶어서 줄일 수 있다. 큰 패킷의 RC4 본체를 대체하는 최적화는 별개다.

### 7.2 개선 방식

- **행동 패킷 1~3개:** 기존 \`encrypt()\` 경로 유지.
- **행동 패킷 4개 이상:** 기존 \`CPacketCryptor\` 객체에서 \`encryptActionBurst(writer, offset, frameCount)\` 같은 전용 경로를 한 번 호출.
- 묶음 내부에서 **각 6바이트 payload를 기존 순서로 독립 암호화**한다. 2바이트 길이 헤더는 암호화하지 않는다.
- RC4 상태 배열과 암호문은 프레임마다 기존과 동일해야 한다. **서로 다른 프레임을 하나의 연속 RC4 스트림으로 바꾸지 않는다.**
- 기존 \`CUserSocket.encrypt()\`의 **첫 송신 평문(\`_handshakePlainPending\`) 처리**를 포함해, 암호화 상태와 예외·실패 반환 의미를 보존해야 한다.
- 일반/혼합 프레임을 포함한 \`_actionOnly == false\` 경로는 현행 프레임 단위 처리를 유지한다.
- 기존 SEND 병합, FIFO ticket, 연결별 cipher 소유권, CIO 소유 실행, 버퍼 풀을 그대로 사용한다. **새 스레드·큐·객체를 만들지 않는다.**

### 7.3 암호문·상태 일치 검사

2026-11-21 테스트 결과로 보고된 수치:

| 검사 | 결과 |
|---|---:|
| 초기 비교 | 4,800조건 / 163,200 프레임, 암호문·RC4 상태 일치 |
| 최종 누적 비교 | **738,240 프레임 일치** |
| 내부 RC4 상태 불일치 | **0건** |
| 첫 송신 평문 처리 | 정상 |
| 묶음 사이 일반 패킷 혼합 | 정상 |
| 성능 측정 | **총 522회** |

이 수치는 해당 독립 A/B 실행에서 보고된 결과다. 여기서 GitHub 운영 빌드에 변경을 적용하거나 실제 게임 TCP 송신 경로의 성능을 측정한 것은 아니다.

### 7.4 암호화 처리량 비교 — 9회 교차 반복 중앙값

**기존 행동 패킷별 \`encrypt()\` 호출 대비 묶음 전용 경로의 암호화 처리량 변화:**

| 묶음 프레임 수 | 32세션 | 5,000세션 |
|---|---:|---:|
| 1 | -5.2% | -6.0% |
| 4 | +11.3% | +10.7% |
| 8 | +17.1% | +18.8% |
| 32 | **+22.3%** | **+23.9%** |
| 128 | +24.1% | +22.7% |

**5,000세션·4개 소유 스레드 별도 검증:**

| 묶음 프레임 수 | 암호화 처리량 변화 | CPU/프레임 |
|---|---:|---:|
| 4 | +14.4% | 13.30ns → 11.84ns |
| 8 | +26.7% | 14.56ns → 11.51ns |
| 32 | **+34.1%** | **13.69ns → 10.35ns** |
| 128 | **+38.3%** | **13.22ns → 10.05ns** |

위 수치는 **행동 프레임 암호화 구간의 처리량** 개선이다. 아직 서버 전체 송신 req/s 또는 TCP throughput 개선율로 측정한 수치는 아니다. 실제 서버 측 개선 폭은 병합 프레임 분포와 암호화가 차지하는 CPU 비중에 달려 있다.

### 7.5 운영 적용 시 필요한 검증

1. \`_actionOnly\` 구간에서 프레임 수가 1, 2, 3, 4, 8, 32, 128개인 경우를 모두 검사. **2~3프레임 경계는 실제 A/B 수치가 없으므로 기존 경로 유지가 보수적 선택**이다.
2. 첫 송신 평문, 재접속·키 초기화, 섞인 일반 패킷, 접속 종료 중 SEND, 예외·부분 전송 시 상태 갱신 및 FIFO 일치.
3. 5,000소켓 기준 작은 행동 프레임 burst와 일반 패킷 혼합 워크로드에서 **실제 SEND 처리량, CPU/패킷, p99/p99.9 지연**을 같은 실행 조건에서 비교.
4. 개선된 경로에서 프레임마다 cipher 순서가 단 한 번만 전진하도록 검증; 암호문과 송신 데이터 바이트 단위 비교.
5. 어떤 조건에서든 결과가 역전되거나 정확성이 손상되면 적용하지 않는다.

### 7.6 채택 판단

- **송신 행동 패킷 묶음 암호화: 개발 적용·실제 네트워크 A/B 권장.** 독립 암호화 비교에서 4프레임 이상 의미 있는 이득이 반복 확인됐다.
- **기존 송신 RC4 본체 대체: 이번 후보 아님.** 이전 8바이트 루프 단순화 및 byte[256] 상태 테이블 변형은 기존 방식보다 느렸다.
- **opcode 분기 테이블 통합: 기존 6절의 보류 판단 유지.**
- **이번 GitHub 변경: 이 보고서의 7절 추가뿐. Java 운영 소스 변경 없음.**
