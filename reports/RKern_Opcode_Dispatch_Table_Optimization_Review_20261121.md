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
