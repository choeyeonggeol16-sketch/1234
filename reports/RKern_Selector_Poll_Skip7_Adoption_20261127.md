# RKern Selector 최적화 적용 보고서 — 최대 7회 Polling 생략

- 기준: `rkern/thread-pool-removal-20261126`, commit `6246c78f5f26451af4558b3570812e79df059ac9` (production source는 부모 `0ab42924bd1d74be30c9bf8307db8350d46c83c6`와 동일)
- 대상: [CSelectorTransport.java](https://github.com/choeyeonggeol16-sketch/1234/blob/6246c78f5f26451af4558b3570812e79df059ac9/rkern/src/l1j/server/server/model/network/core/transport/selector/CSelectorTransport.java)
- 원본 Git blob: `09b112064d5277ea76ff43e53fbd32519738052b`
- 상태: **7회 생략 정책만 적용 후보로 선정. 이 커밋은 보고서만 추가하며 production 변경 없음.**

## 1. 적용할 최적화 (단일 변경)

이미 수집한 readiness가 ready list에 남아 있거나 acceptReady가 설정된 경우, 동일 owner에서 불필요한 nonblocking Selector poll을 최대 7회 연속 생략한다. advance()와 awaitWork()가 동일한 owner-local 카운터를 공유한다.

- 기존 advance()는 매번 selectNow(collector) 후 work()를 실행한다. awaitWork()도 select() 내부에서 ready 작업이 있으면 selectNow(collector)를 실행한다. 양쪽에서 같은 ready backlog에 대해 다시 poll하는 비용을 줄인다.
- ready list의 first != null 또는 acceptReady가 참일 때만 생략한다. mailbox만 남았거나 ready 작업이 없다는 이유로 생략하지 않는다.
- 생략 카운터는 owner thread 전용 primitive int 하나로 유지한다. 생략할 때마다 증가시키고 실제 Selector 호출이 성공적으로 반환했을 때만 0으로 초기화한다(빈 반환 포함). 두 진입점 전환 자체로 초기화하지 않는다.
- 7회 연속 생략 후 다음 poll 기회에는 실제 selectNow를 수행한다. 기존 blocking/timed select 경로 및 만료 시간 계산은 그대로 유지한다.
- advance()는 생략 여부와 관계없이 기존 work()를 실행한다. awaitWork()는 생략 시 바로 반환하여 기존 CIO host loop의 다음 advance()로 넘긴다. awaitWork()에서 별도로 work()를 실행하지 않는다.
- 기존 readyOps 병합, ready list 연결·해제, ACCEPT/COMMAND/READY budget, OWNER_PHASE_NANOS, timeout, mailbox, wakeup, 종료, interestOps, READ/WRITE 처리 및 callback 실행 순서를 보존한다.
- 새 클래스·스레드·큐·락·JVM 옵션은 추가하지 않는다. 기존 CIOThreadCallback, CExecutionSerializerFast, CIOSocket, CUserSocket, 버퍼 풀, 암호화, 패킷 처리, 게임 타이머에는 변경하지 않는다.

### 구현 명세 (검증된 variant 7의 동작을 운영 코드로 옮기는 예시)

Owner 내부의 예시이며 아래 코드를 적용하는 커밋은 별도로 검증한다.

```java
private static final int MAX_CONSECUTIVE_POLL_SKIPS = 7;
private int consecutivePollSkips;

private boolean trySkipPoll() {
    if (first == null && !acceptReady) return false;
    if (consecutivePollSkips >= MAX_CONSECUTIVE_POLL_SKIPS)
        return false;
    ++consecutivePollSkips;
    return true;
}

private void pollNow() throws IOException {
    selector.selectNow(collector);
    consecutivePollSkips = 0;
}

private void pollWait(long waitMillis) throws IOException {
    if (waitMillis < 0) selector.select(collector);
    else selector.select(collector, waitMillis);
    consecutivePollSkips = 0;
}
```

- advance(): 기존 selectNow(collector); work();를 `if (!trySkipPoll()) pollNow(); work();`로 교체한다. 기존 initialize(), 시작/종료 및 예외 경로 유지.
- awaitWork(): 기존 시작 및 owner 확인 뒤 `if (!trySkipPoll()) select(Math.max(0, maxWaitMillis));` 실행. 기존 종료 처리 유지.
- select(long): 기존 분기 조건 및 deadline 계산은 변경하지 않고 실제 selector.selectNow를 pollNow로, 마지막 blocking/timed select를 pollWait로 연결한다. 독립 run()의 기존 select(-1) 정책은 유지한다.
- 카운터는 awaitWork와 advance 각각에 따로 두지 않는다. 실제 poll 호출 경로를 빠짐없이 확인한다. 임의로 read 반복, 시간 상한, ready-depth 적응 정책을 합치지 않는다.

## 2. 실제 TCP 측정 결과 — 채택 후보만

최신 CSelectorTransport의 주석·공백을 정규화한 원본(FNV-1a 32-bit `537e3dac`, 915줄)을 확인해 계측용 사본을 컴파일했다. 기존 코드(variant 0)와 통합 7회 생략(variant 7)을 동일한 계측 환경에서 비교했다. Java 21 / Linux loopback TCP Echo, hosted Selector owner 2개, 동일 JVM 내 부하 생성, 256B 메시지, window=1. 실제 CIO 전체가 아닌 독립 OwnerHost가 advance/awaitWork를 호출한다. 각 구성 3회씩, 본 시험 약 1.5초/회, 5,000연결 재검증 약 2초/회. CPU는 owner thread CPU 합계/완료 요청 수이며 전체 시스템 CPU가 아니다. p99는 히스토그램 구간 상한으로 근사했다.

| 시험 | 지표 | 기존 | 7회 | 변화 |
|---|---|---:|---:|---:|
| 1,024연결 | 요청/s | 202,226.59 | 205,368.79 | +1.55% |
| | CPU ns/요청 | 6,220.54 | 5,954.57 | -4.28% |
| | p99 ms | 12.800 | 10.752 | -16.00% |
| | poll/1,000요청 | 305.41 | 178.33 | -41.61% |
| 5,000연결 | 요청/s | 135,838.67 | 144,544.07 | +6.41% |
| | CPU ns/요청 | 9,624.70 | 8,468.27 | -12.02% |
| | p99 ms | 65.536 | 55.296 | -15.63% |
| | poll/1,000요청 | 358.70 | 151.52 | -57.76% |
| 5,000연결 재검증 | 요청/s | 133,936.01 | 147,610.14 | +10.21% |
| | CPU ns/요청 | 10,824.82 | 8,023.82 | -25.88% |
| | p99 ms | 69.632 | 55.296 | -20.59% |
| | poll/1,000요청 | 310.06 | 186.33 | -39.90% |

각 셀은 해당 지표의 3회 중앙값이다. poll/1,000요청은 각 회차의 poll/req 비율을 구한 뒤 중앙값을 계산했다. 이는 Java Selector 호출 수이며 실제 커널 syscall 계측값은 아니다.

### 원시 로그 발췌: 5,000연결 재검증

| 회차 | 기존 요청/s | 7회 요청/s | 기존 CPU ns/요청 | 7회 CPU ns/요청 | 기존 p99 ms | 7회 p99 ms |
|---|---:|---:|---:|---:|---:|---:|
| 1 | 132,716.00 | 148,825.58 | 10,824.82 | 8,023.82 | 59.392 | 55.296 |
| 2 | 141,988.10 | 147,610.14 | 9,253.13 | 7,793.20 | 69.632 | 49.152 |
| 3 | 133,936.01 | 133,337.58 | 11,163.36 | 8,915.21 | 73.728 | 90.112 |

로컬 시험 로그 `selector_skip_sweep/results1024.txt`, `results5000.txt`, `confirm5000.txt`를 다시 확인했다. 3~8회 및 정책 비교 99회 중 위에는 실제 적용 후보와 대응 기준선만 발췌했다. 원시 로그 전체와 계측용 시험 소스는 이 보고서 커밋에 포함하지 않는다.

7회 구성의 모든 위 시험에서 실제 최대 생략 횟수 7에 도달했고, awaitWork 생략과 강제 poll 경로가 실행되었다. 무응답 client 수 0, 전체 연결 open/close 일치, 37B 단위 분할 전송 데이터 검사 및 각 요청의 echo 데이터 일치, 타이머 콜백 진행, 종료 시 잔류 connection/command 0을 확인했다. 재검증 3회 중 1회는 처리량 및 p99 악화, CPU 비용은 3회 모두 개선되었다. 장시간 공정성·안정성까지 입증한 결과는 아니다.

## 3. 적용 전/후 필수 검증 및 채택 기준

- Java 21 전체 관련 코드 타입 컴파일, 기존 TCP/Serializer/CIO 타이머 회귀. 동일 owner 실행, 패킷 순서, close/cancel, mailbox overflow/accept burst, 외부 producer wakeup, owner 종료 및 자원 반환 검증.
- 실제 7회 생략 한도와 실제 poll 후 초기화, 빈 ready에서 대기, 새 연결/새 readable socket이 계속된 ready backlog에서도 처리되는지 검증. 기존 timer/packet budget과 scheduler wake gate를 변경하지 않는다.
- 강제 backpressure(작은 SO_SNDBUF 및 수신 중단), 부분 SEND, OP_WRITE 설정/해제, write stall timeout, disconnect/drain 및 READ pause/resume 테스트. 기존 일반 TCP echo의 결과만으로 이 경로를 통과했다고 간주하지 않는다.
- Windows Java 21 WEPoll 및 Linux epoll에서 production CIO host를 사용한 기준선/7회 A/B: 1·16·1,024·5,000연결, 저부하와 고부하, 256B 및 4KiB, window=1 및 burst, idle/active 혼합. 동일 클라이언트·코어·JVM 조건에서 교차 반복하고 충분한 warmup/measurement와 장시간 soak를 확보한다. 처리량, owner/전체 CPU, p50/p99/p99.9, poll 수, 할당률, 개별 연결 공정성 및 타이머 지연을 비교한다.
- 단일 연결/저부하에서 통합 7회 정책의 성능은 아직 측정하지 않았다. 실제 게임 listener·암호화 패킷·CIO 통합 경로에서의 이득 및 Windows 동작도 미검증이다. Netty native epoll과의 신규 대조는 별도 시험이며 이번 결과에 포함되지 않는다.
- 위 검증에서 고부하 개선이 재현되고 저부하·tail latency·타이머/신규 연결의 처리 지연 및 정확성에 유의한 퇴행이 없을 때만 기본 경로로 채택한다. 실패하면 이 단일 변경을 채택하지 않는다.

**최종 적용 범위: CSelectorTransport.java 한 파일, owner-local 최대 7회 연속 poll 생략 정책만. 현재 production 소스는 수정하지 않았다.**
