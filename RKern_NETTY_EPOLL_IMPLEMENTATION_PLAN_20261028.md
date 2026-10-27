# RKern이 Netty epoll을 넘기 위한 구현 지침

작성일: 2026-10-28, 한국 시간.

대상: `RKern_SRC_20261028_NPC_latest_Network_optimized.zip`.

**수정 순서는 SEND 순서 보장 → READ 완료의 추가 executor 경유 제거 → 실제 packed batch와 owner fast path → 수신 중복 처리 제거다. 암호화만 더 빠르게 만드는 것으로 현재 네트워크 격차를 해결하려고 하면 안 된다.**

이번에는 코드 검토와 기존 성능 54회에 더해, 진단 복사본에서 실행 경로를 계측했다. 운영 소스는 변경하지 않았다. 아래 지침은 구현 작업과 합격 조건을 정리한 것이며, 아직 작성하지 않은 수정본의 가속 비율이나 승리를 보장하지 않는다.

## 1. 목표를 숫자로 고정한다

같은 환경에서 다시 측정한 중앙값이다. Netty는 4.1.139.Final native epoll, 서버 CPU 2개, 공통 프로토콜과 동일한 새 Cryptor를 사용했다.

| 부하 | 현재 요청/초 | Netty 요청/초 | 동률까지 필요한 처리량 증가 | 현재 / Netty CPU μs/요청 |
| --- | ---: | ---: | ---: | ---: |
| 1연결·진행 요청 1개 | 29,690 | 38,649 | +30.2% | 19.40 / 12.57 |
| 128연결·요청당 작은 응답 32개 | 259,250 | 383,433 | +47.9% | 7.68 / 5.18 |
| 1,024연결·응답 1개 | 197,867 | 331,047 | +67.3% | 9.82 / 5.83 |
| 64연결·4KiB 응답 1개 | 131,343 | 147,780 | +12.5% | 15.06 / 13.20 |

CPU가 병목인 구간에서 참고할 비용 예산은 작은 묶음 약 **2.50 μs/요청**, 다중 연결 약 **3.99 μs/요청**, 4KiB 약 **1.86 μs/요청**을 현재보다 줄이는 것이다. 이는 관측된 Netty와의 비용 차이이며, 특정 수정이 이 비용을 줄여 준다는 예측은 아니다. 단일 연결은 CPU 포화 시험이 아니므로 CPU 비용 비율을 그대로 처리량 예측에 사용하지 않는다.

재측정 전 임시 목표로 Netty 중앙값보다 5% 높은 처리량을 잡으면 각각 **40,582 / 402,605 / 347,599 / 155,169 요청/초**다. 작은 묶음의 목표는 논리 응답 약 1,288만 개/초에 해당한다. 다음 측정에서는 Netty도 다시 돌려 목표를 갱신한다.

현재 p99는 작은 묶음 8.36ms 대 Netty 6.31ms, 4KiB 5.16ms 대 3.14ms다. 처리량을 높이면서 긴 지연이 이전 v5처럼 다시 늘어나는 수정은 최종 합격으로 보지 않는다.

## 2. P0 — owner의 SEND 추월부터 막는다

### 추가 진단에서 확인한 사실

16연결, 연결당 진행 요청 64개, 응답 1개 조건을 사용했다. READ 프레임 예산은 기본값 32다. 진단 카운터는 실행 시간에 영향을 주므로 이 실행의 처리량은 성능 결과로 사용하지 않는다.

| 진단 복사본 | READ 함수 중첩 | 앞선 staged SEND가 있는데 direct 등록 | 순서 결과 |
| --- | ---: | ---: | --- |
| 현재 경로 1회차 | 0 | 32회 | 실패: 4917 대신 4923 |
| 현재 경로 2회차 | 0 | 32회 | 실패: 4309 대신 4313 |
| 현재 경로 3회차 | 0 | 47회 | 실패: 1907 대신 1910 |
| 모든 SEND를 티켓으로 등록한 대조군 | 3회 모두 0 | 3회 모두 0 | 3회 통과 |

실제 로그에는 `staged=3`, `pending=5`인 상태에서 owner가 새 action을 direct 등록한 사례가 있다. 이번 실행에서 READ 함수의 동시 진입은 잡히지 않았지만 SEND 순서 위반 경로는 잡혔다. READ 중첩이 모든 조건에서 불가능하다고 증명한 것은 아니다.

owner direct 경로 자체는 v5에도 존재한다. 이 결함 전체가 이번 변경에서 처음 만들어졌다고 단정하지 않는다. 현재 그룹/재개 방식에서 재현되는 순서 조건을 확인한 것이며, READ 설정 0의 대조 시험 통과는 잠재적인 SEND 우회 결함까지 제거됐다는 증거가 아니다.

### 수정할 위치

`CIOUserSocket.java`의 다음 경로를 함께 검토한다. 줄 번호는 업로드 원본 기준이다.

- `isSocketSerializerOwner()` 약 842행.
- `tryWriteAction()` 약 1057행: owner 여부만으로 `direct`를 정한다.
- `stageOutboundFrameLocked()` 약 805행: direct일 때 앞선 티켓에 합류하지 않는다.
- `finishStagedSend()` / `acceptStagedSend()` 약 847/860행: direct이면 즉시 부착한다.
- `beginDirectFrame()` / `commitDirectFrame()` / `tryWritePacket()`: 인코딩 전 예약 순서도 보존해야 한다.
- `drainSocketCallbacks()` 약 1577행: 현재 callback도 `_socketPending`에 포함된다.

### 발생 가능한 순서

1. READ 처리 스레드가 응답 A를 SEND 티켓에 넣는다. 다른 스레드가 serializer를 실행 중이어서 A는 staged 상태로 남는다.
2. WRITE 완료를 처리하는 serializer owner가 READ 재개를 실행한다.
3. 후속 요청의 응답 B가 owner 경로에서 생성된다.
4. B가 owner라는 이유로 direct 등록되면 앞서 staged된 A보다 먼저 `_writing`/pending 버퍼에 붙을 수 있다.
5. 암호화는 실제로 붙은 순서대로 정상 실행돼도 응답의 논리 순서는 B→A가 된다.

따라서 READ pump 하나만 직렬화하는 것으로는 충분하지 않다. owner 확인 뒤에도 **앞선 SEND admission 순서**를 따로 확인해야 한다.

### 구현 지침

1. **SEND의 순서 결정 지점을 명시한다.** action은 admission 시점, 바깥에서 인코딩하는 packet/frame은 인코딩 전 예약 시점에 순서를 확보한다. 취소된 예약은 CANCELLED로 소비해 뒤 티켓이 진행하게 한다.
2. 정확성 기준 구현에서는 owner가 생성한 SEND도 동일한 티켓 순서를 따른다. 인코딩 전 예약도 owner라고 건너뛰지 않는다. 대조군처럼 모든 SEND를 티켓화한 것은 정확성 비교용 기준이며 최종 고성능 구현은 아니다.
3. 8바이트 action처럼 잠금 안에서 인코딩과 admission을 끝내는 경로에만 제한적인 inline fast path를 복원한다. 앞선 staged SEND와 인코더 예약이 없고, 현재 실행 중인 callback 외에 앞선 티켓이 없음을 확인한다.
4. frame/packet은 예약 후 잠금 밖에서 인코딩하므로 action의 조건을 그대로 복사하면 안 된다. 예약 토큰이 이미 확보한 순서를 commit에서 유지한다. 다른 인코더가 나중에 만든 staged SEND와 더 먼저 예약된 SEND를 구분한다.
5. `direct`를 stage 내부에서만 바꾸지 않는다. 호출자가 `finishStagedSend(lease, direct)`에도 같은 결정을 사용한다. 예약·공개·소비 방식이 어긋나면 중복 실행 또는 고립된 티켓을 만들 수 있다.
6. owner라는 이유만으로 pending 티켓을 무시하는 우회는 없앤다. WRITE 완료/READ 재개 같은 제어 callback의 실행 소유권과 패킷 admission 순서를 함께 관리한다.

```mermaid
flowchart LR
    A[패킷 순서 예약] --> B[비공개 버퍼에 인코딩]
    B --> C[FIFO 순서로 공개·부착]
    C --> D[논리 프레임별 암호화]
    D --> E[WRITE 또는 gather]
    E --> F[완료 후 카운터·버퍼 반환]
```

**합격 조건:** 순번·중복·누락·암호문 오류 0. READ 예산 0/1/8/32/256, window 1/8/64/256을 포함한다. 미완료 인코더 예약, 중첩 owner SEND, partial WRITE, `write()` 반환 전 완료 callback, close/abort를 강제로 교차시킨다. 앞선 인코더를 latch로 멈춘 뒤 다음 SEND가 먼저 나가지 않는지 확인하는 결정적 테스트를 추가한다.

## 3. P1 — READ마다 생긴 간접 callback 작업을 제거한다

### 원인 범위를 뒷받침하는 관측

`CIOServer.java` 약 73~90행은 READ 예산이 켜지면 다음 구성을 사용한다.

```java
Executors.newFixedThreadPool(completionWorkers);
AsynchronousChannelGroup.withThreadPool(ioExecutor);
```

예산 0은 기존 `withFixedThreadPool(...)` 그룹을 사용한다. 두 구성은 같은 개수의 워커를 요청하더라도 JDK provider의 실행 경로가 같다거나 할당 비용이 같다는 뜻이 아니다.

추가 진단은 16개 활성 연결의 짧은 왕복을 사용했다. READ callback 스택을 정해진 간격으로 512개 표본 수집했다.

| 구성 | `sun.nio.ch.Invoker$2` 경유 표본 | 그 경로가 없는 표본 |
| --- | ---: | ---: |
| 업로드 기본 그룹 | 512 | 0 |
| READ 설정 0의 기존 그룹 | 0 | 512 |

사용한 JDK 21의 `Invoker.invokeIndirectly()` 바이트코드도 확인했다. 이 경로는 `new Invoker$2(...)` 후 `executeOnPooledThread(...)`를 호출한다. 자체 continuation에서 `this` Runnable을 재사용해도 JDK가 READ 완료마다 만드는 간접 작업까지 제거되는 것은 아니다.

별도의 비계측 성능 대조에서는 1,024연결 처리량이 설정 0에서 196,688→226,273 요청/초, 약 15% 증가했다. 이 값은 예산과 그룹 변경을 함께 끈 효과다. 간접 작업 하나의 순수 CPU 비용을 분리해 측정한 값은 아니다. WRITE 완료는 진단에서 대부분 submit 스레드와 같았으므로 WRITE도 항상 같은 추가 handoff를 한다고 일반화하지 않는다.

### 구현 지침

1. P0을 고친 뒤 처리량 기준선은 기존 fixed group으로 복원한다. `cio.readFrameBudget=0`은 비교 기준으로 사용하되 이것만으로 순번 오류를 고쳤다고 판정하지 않는다.
2. READ budget 설정 하나가 channel-group 토폴로지까지 조용히 바꾸지 않도록 설정과 로그를 분리한다. 그룹 모드, 완료 워커 수, continuation 실행 경로를 명시한다.
3. fixed group의 내부 executor는 일반 공개 API로 얻을 수 없다. 내부 JDK 클래스 접근·Unsafe·JNI로 강제로 꺼내 쓰지 않는다. 해당 그룹에서 안전한 continuation 경로가 준비되지 않았다면 그 조합은 비활성/미지원으로 명시한다.
4. fairness는 별도 변경으로 재도입한다. 모든 READ를 항상 큐로 넘기는 구성부터 택하지 말고 실제로 예산을 소진한 READ만 양보하는 실행 경계를 설계한다. 연결당 continuation 하나와 합쳐지는 재개 신호, 안전한 소유권 인계, 확실한 재실행을 보장한다.
5. `_readKick`을 0으로 만드는 것만으로 소유권 인계가 안전하다고 판단하지 않는다. 인계 직전/직후의 READ 완료·송신 압력 해제·close 신호가 모두 재개 또는 종료로 이어져야 한다. buffered frame을 남긴 채 다음 네트워크 이벤트만 기다리면 응답 없는 opcode 등에서 멈출 수 있다.
6. fairness 재도입은 처리량과 p99를 함께 판정한다. 기존 v5의 4KiB p99 약 21ms로 되돌리는 것을 최종 성과로 삼지 않는다.

**합격 조건:** 기본 작은 왕복 경로가 불필요한 간접 완료 작업을 거치지 않는지 재확인한다. 추가 작업이 반드시 필요한 경로는 수와 비용을 기록한다. 1,024연결 CPU/요청과 처리량이 기준선을 회복하고 P0 정합성 시험을 모두 통과해야 한다.

## 4. P1 — batch가 실제로 한 번의 admission이 되게 만든다

### 현재 batch의 한계

`tryWriteActionBatch()` 약 1132행은 append에 실패하면 단건 `tryWriteAction()`을 호출하고 다음 항목에서 다시 append를 시도한다. 부하 경로에서 실패하면 N개 단건 작업에 batch 시도 잠금까지 추가한다.

진단 결과:

- 첫 실행: fast append 4,302,025프레임, 단건 fallback 4,040,471프레임. 약 48%가 fallback이었다.
- 상세 실행: fast append 3,122,103프레임, fallback 3,341,321프레임.
- 상세 실행의 fallback 중 3,139,339회, 약 **94%**는 버퍼·공간·앞선 SEND·예약 조건이 맞는데도 **현재 owner callback 하나가 pending으로 포함돼서** 막힌 경우였다.

진단은 계측으로 타이밍이 변하므로 비율을 모든 게임 부하에 일반화하지 않는다. 다만 막히는 분기 자체는 직접 확인했다. 기존 비계측 batch 비교에서도 추가 이득을 확인하지 못했다.

### 4-A. 현재 owner callback과 앞선 대기 callback을 구분한다

`writableBurstTailLocked()` 약 1097행의 `SOCKET_PENDING != 0`은 실행 중인 현재 callback도 거절한다. `drainSocketCallbacks()`는 callback의 실행을 마친 뒤에 pending을 감소시킨다.

P0 순서 규칙을 먼저 구현한 후, 현재 callback만 있는 owner 경로에 제한적인 예외를 검토한다.

```java
int pending = (int) SOCKET_PENDING.getVolatile(this);
boolean onlyCurrentOwner = pending == 1 && isSocketSerializerOwner();
if (pending != 0 && !onlyCurrentOwner) return null;
```

**이 조각만 단독으로 적용하면 안 된다.** 기존의 앞선 staged SEND/예약 0, 동일 burst 소유, 적절한 queued 상태, 버퍼 공간, `_ioPending == false`, `_writePrepared == false` 조건을 함께 유지한다. `_socketPosting` 중에는 owner 예외가 성립하면 안 된다. 외부 인코더의 순서 예약은 P0 규칙으로 별도 보장한다.

### 4-B. 빈 tail / 부하 상태에서도 packed batch를 한 lease로 만든다

fast tail이 없을 때 N번 단건 fallback으로 끝내지 않는다. bounded chunk마다 다음 절차를 구현한다.

1. 모든 입력 범위를 먼저 검증한다. 인코딩이 필요한 chunk의 FIFO 순서, 물리 버퍼 슬롯, wire 바이트 및 논리 프레임 예약을 확보한다.
2. 소유한 비공개 lease 하나에 `[길이 8][본문 6]` 프레임 N개를 인코딩한다. 배열은 호출 중에만 읽고 반환 뒤 보관하지 않는다.
3. chunk의 wireBytes와 논리 frameCount를 한 번에 공개한다. 물리 lease 하나와 논리 프레임 N개를 다른 단위로 관리한다.
4. 한 chunk에 한 SEND 티켓과 한 batch admission을 사용한다. 정상적인 batch 성공 경로에서 잠금·워터마크·풀 작업이 N에 비례하지 않게 한다.
5. chunk는 현재 wire/버퍼/압력 한도 안에서 자른다. chunk 사이 다른 생산자와의 순서를 명시한다. 이미 공개한 일부 프레임을 조용히 취소하지 않는다.

**현재 단건 commit에 packed 데이터를 그대로 넣으면 안 된다.** `commitStagedOutboundFrameLocked()` 약 894행은 첫 헤더를 전체 길이로 다시 쓰고 `_frameCount = 1`로 설정한다. 이 경로를 그대로 쓰면 프레임 경계와 워터마크가 깨진다.

packed 전용 공개 경로 또는 명확한 metadata 인수를 추가해 다음을 보장한다.

- 첫 프레임의 길이 8을 chunk 전체 길이로 덮어쓰지 않는다.
- `_packedFrames = true`, `_frameCount = N`, `_wireLength = wireBytes`가 공개 전에 확정된다.
- admission, 예약, complete, close, 실패에서 wire 바이트와 **논리** 프레임 N개를 같은 값으로 증감한다. 물리 lease 수로 논리 프레임을 대체하지 않는다.
- `preparePackedEncryption()`의 프레임별 암호화는 유지한다. 합친 버퍼 전체를 한 패킷으로 암호화하면 안 된다.
- partial WRITE에서는 암호화한 바이트를 다시 암호화하지 않는다. completion 전이나 `write()` 호출 반환 전 lease를 재사용하지 않는다.
- `completeGather()`가 이미 구현한 바이트/프레임 합산 후 한 번의 해제 처리는 유지한다.

### 4-C. 실제 호출 경로에 연결한다

업로드 전체 소스에서 `tryWriteActionBatch()`는 정의만 있다. 현재 `RDoActionGFX.send()`는 `connection.sendAction()`을 사용한다. API가 존재하는 것만으로 게임 경로가 batch로 바뀌지 않는다.

네트워크 출력 경계에서 이미 같은 연결로 연속 발생한 액션을 묶는다. 새로운 NPC 전송 규칙이나 대기 시간을 만들지 않는다. 서로 다른 클라이언트의 암호문을 공유하거나 다른 연결의 프레임을 한 TCP 버퍼로 합치지 않는다. 이미 배열을 가진 호출부라면 재사용하고, 새 배열 수집 비용이 생긴다면 그 비용도 end-to-end 측정에 포함한다.

**합격 조건:** 제어된 32개 액션 owned-burst 시험에서 정상 fast/packed 경로 비율을 높이고, 잠금 횟수·SEND 티켓 수·lease acquire/release·워터마크 호출을 chunk당 측정한다. 예를 들어 정상 제어 시나리오의 fast/packed 프레임 비율 90% 이상을 작업 목표로 잡되 포화 시 올바른 fallback까지 없애려고 하지 않는다. 실패 이유를 self-pending / 앞선 SEND / 인코더 예약 / I/O 중 / 공간 부족으로 나눠 기록한다. 최종 판정은 전체 TCP 처리량과 p99로 한다.

## 5. P2 — 수신 중복 작업을 원래의 단일 경로로 정리한다

### opcode preview를 무조건 수행하지 않는다

`onReadFrame()` 약 1431행은 preview로 분류한 뒤 정상 패킷을 다시 full decrypt한다. 현재 이 preview가 known opcode의 full decrypt를 줄이지 않는다.

- v5처럼 한 번 decrypt하고 opcode를 읽어 분류하는 경로를 기준으로 비교한다.
- 패킷 속도 제한, 필터, session/creature 실행 경계는 보존한다.
- unknown opcode도 필요한 cipher 상태 진행을 수행한다. decrypt를 건너뛰고 버리면 뒤 패킷 상태가 어긋날 수 있다.
- 특별히 preview가 필요한 경로가 있다면 실제로 회피하는 작업과 호출 조건을 명확히 한다. 모든 패킷의 공통 비용으로 두지 않는다.

### 불필요한 compact를 회피한다

`onRead()` 약 1346행의 무조건 `recv.compact()`는 다음처럼 구분하는 v5 경로와 비교한다.

```java
if (!recv.hasRemaining()) {
    recv.clear();
} else if (recv.position() == 0) {
    int buffered = recv.limit();
    recv.clear();
    recv.position(buffered);
} else {
    recv.compact();
}
```

미완성 프레임이 이미 offset 0에 있으면 제자리에서 남기고, 실제로 소비한 prefix가 있을 때만 남은 바이트를 옮긴다. `compact()`가 항상 전체 버퍼를 복사한다고 과장하지 않는다. 완전히 소비된 경우 이동할 바이트는 0이다.

**합격 조건:** TCP 조각 수신, 헤더 1바이트 분할, body 분할, 여러 프레임과 tail 혼합, 큰 receive lease 승격·축소가 원본과 같다. preview 호출 수, 실제 decrypt 수, compact 호출과 이동한 바이트 수를 기록한다. 이 변경의 정량 이득은 아직 따로 측정하지 않았다.

## 6. P2 — 작은 암호화 경로를 분리하되 네트워크 격차와 혼동하지 않는다

이전 별도 계산 시험에서는 업로드 Cryptor의 6바이트를 작은 PRGA 경로로 분리했을 때 약 15~19% 계산 시간 감소를 관측했다. 큰 패킷의 추가 이득과 회귀 없는 성능은 확정하지 못했다.

- 1~7바이트는 미리 읽기·교환 위치 보정이 없는 짧은 경로를 검토한다.
- 큰 경로의 int[256], 패킷마다 j=1/k=0, 패킷 사이 permutation 상태, 8바이트 XOR와 tail을 유지한다.
- alias 보정 없는 미리 읽기, byte[]로 축소, 큰 unroll 확대, SIMD 적용을 성능 개선으로 가정하지 않는다. 기존 실패 실험을 새 해결책처럼 반복하지 않는다.
- JNI·Unsafe·클라이언트 규칙 변경은 사용하지 않는다.
- 연속 패킷의 암호문과 permutation 전체, 길이 경계와 LE/BE, heap/direct, base/offset, 송수신 교차를 확인한다.

Netty와 RKern 서버에 같은 Cryptor를 사용한 비교에서는 이 개선이 Netty에도 적용된다. 따라서 암호화 최적화만으로 RKern 고유의 queue/lease/dispatch 비용 차이가 사라지는 것은 아니다. 승리 판정에서는 공통 codec을 다시 맞춘다.

## 7. 변경을 이렇게 나누어 제출한다

| 순서 | 변경 단위 | 완료 조건 |
| --- | --- | --- |
| 1 | SEND admission/예약/FIFO 정리 | owner·foreign·인코더 교차에서도 순번/중복/누락 0 |
| 2 | 기준 fixed group 복원, topology 설정 명시 | 추가 간접 READ 완료 경로와 비용을 재확인 |
| 3 | 안전한 owner append + packed batch fallback | chunk 단위 비용으로 줄고 byte/frame accounting 보존 |
| 4 | 실제 네트워크 출력에 batch 연결 | 테스트 전용 API 점수가 아닌 실제 호출 경로가 사용 |
| 5 | 단일 decrypt/compact 정리 | 필터·cipher·수신 조각 처리 계약 보존 |
| 6 | 작은 Cryptor 경로 | 크기별 회귀와 공통 codec 조건을 함께 검증 |
| 7 | 필요한 경우에만 fairness 재도입 | p99 개선을 유지하며 P0/처리량 기준 통과 |

한 커밋에 전부 섞지 않는다. 각 변경 후 정합성 검사를 통과하고 같은 조건으로 Netty를 재측정한다. 단순 회복과 추가 가속을 구분한다. 앞 변경에서 얻은 개선율들을 더해 최종 개선율로 계산하지 않는다.

## 8. 최종 승리 판정

### 먼저 통과할 정확성 항목

- window 1/8/64/256과 여러 활성 연결 수, 기본/비기본 READ budget.
- 서로 다른 생산자, owner 중첩, 인코더 예약 후 지연·취소, 일반 packet과 action/batch 혼합.
- 조각 READ, partial/zero WRITE, gather, 즉시/다른 스레드 완료, close/abort와 pool 포화.
- 클라이언트가 받은 전체 프레임의 순번·내용·총수, cipher 상태, 버퍼 재사용 세대, 최종 예약/바이트/프레임 카운터.

오류를 내고 빠르게 종료한 실행은 승리로 세지 않는다. 오류·끊김·거절도 결과에 포함한다.

### 처리량과 지연을 함께 비교한다

1. 동일 Java/CPU quota/affinity/heap/direct cap/프로토콜/cipher/패킷 수/flush 정책을 사용한다. Netty는 현재 묶음 버퍼 구현을 유지한다. 워커나 CPU를 늘리면 Netty도 같은 자원을 준다.
2. 새 JVM, 워밍업 10초 이상·측정 15초 이상, 최소 7회 교차 실행을 권장한다. median뿐 아니라 각 실행 범위와 bootstrap 신뢰 구간 등 변동을 제시한다. 5% 차이보다 변동이 크면 결론을 보류한다.
3. 처리량은 준비/등록한 수가 아니라 클라이언트가 정상 수신한 논리 응답 수로 계산한다. 작은 응답 32개를 1개로 줄이거나 암호화를 끄지 않는다.
4. 현재 네 가지 기본 부하와 혼합 크기, 저부하/포화 부하를 모두 포함한다. 첫 목표는 지정한 모든 기본 부하에서 Netty 대비 처리량 +5% 이상의 반복 가능한 이득과 p99 회귀 없음이다. 일부만 이기면 해당 조건의 승리로 표현한다.
5. p99는 같은 offered load에서도 비교한다. 현재 closed-loop 점수 외에 두 서버가 감당할 수 있는 공통 요청률의 50/80/95% 등에서 pacing한 시험을 추가한다. 송신 예정 시각과 실제 송신 시각을 구분해 클라이언트 대기를 숨기지 않는다.
6. 클라이언트 CPU가 먼저 차면 서버 최대 성능을 판정하지 않는다. 양쪽 시험의 클라이언트 자원을 같이 늘리고 VM 전체 CPU quota도 늘리거나 별도 부하 머신을 쓴다. 현재 4 CPU quota에서 클라이언트 affinity만 3개로 늘리면 서버와 quota를 다툴 수 있다.
7. 네트워크 대기 단계는 요청 enqueue→클라이언트 write→서버 read/handler→write submit→write complete→클라이언트 마지막 수신으로 나눠 낮은 비율로 샘플링한다. 모든 패킷에 여러 nanoTime·로그를 추가한 값을 최종 점수로 사용하지 않는다.

### 남은 차이가 JDK provider 고정 비용이면

위 작업 후에도 목표에 못 미치고 새 프로파일에서 RKern 코드 밖의 NIO.2 provider 비용이 차이를 지배한다면, Linux용 `SocketChannel`/`Selector` backend를 작은 별도 prototype으로 비교한다. 공개 packet API·pool·Cryptor·FIFO 계약을 유지하고 먼저 1,024연결 시험으로 가능성을 확인한다. 순수 Java로 작성할 수 있지만 새 backend가 Netty epoll보다 빠르다고 미리 가정하지 않는다. Windows IOCP 성능은 별도 판정한다.

pool 세대 검사 제거, lock 알고리즘의 무작정 교체, 여러 연결의 cipher 병합, 무제한 큐/스레드, 게임/NPC 실행 경계 변경으로 숫자를 높이는 작업은 이 계획에 포함하지 않는다.

## 9. 근거와 작업 범위

- [54회 성능 비교와 원시 결과](RKern_NETTY_EPOLL_REBENCH_20261028.md).
- [추가 진단 코드·로그·구현 지침 ZIP](downloads/RKern_netty_epoll_plan_evidence_20261028.zip).
- 추가 진단은 계측과 대조 변경을 넣은 복사본이다. **진단 diff는 배포용 가속 패치가 아니다.** 카운터가 바꾸는 타이밍 때문에 진단 실행의 처리량을 성능 표에 합치지 않았다.
- 재현 패키지를 다시 빌드해 진단 4종의 class 각 36개와 고정 클라이언트의 주요 class가 실제 진단에 사용한 것과 SHA-256까지 같음을 확인했다. 패키지에서 티켓 FIFO 대조군 TCP 시험도 재실행해 통과했다.
- 이번 작업에서는 원본 ZIP, 운영 소스, NPC AI, 게임 규칙, DB를 수정하지 않았다. 보고서에 적은 구현 후 전체 프로젝트 빌드와 네트워크 회귀가 별도로 필요하다.

입력 ZIP SHA-256: `7dac9373d456a1956e67d88215294ad581541378399023fc7dfe92846e659500`.

이 계획의 목표는 명시한 프로토콜과 자원 조건에서 Netty epoll adapter를 반복 가능하게 넘는 것이다. 모든 운영체제와 모든 부하에서 Netty보다 빠르다는 보장은 현재 근거로 할 수 없다.
