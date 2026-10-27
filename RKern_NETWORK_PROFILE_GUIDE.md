# RKern 네트워크 프로파일링과 수정 작업 지침

2026-10-27. 기준은 최신 v5. NPC·게임 소스·DB는 수정하지 않았다. 사용량을 아끼기 위해 작은 패킷 묶음과 4KiB 송신만 RKern/Netty epoll 각 1회, 유효 JFR 4개로 제한했다.

## 확인한 내용

같은 2 CPU·2 워커·암호화·TCP 하네스에서 5초 워밍업 뒤 약 10초의 측정 구간만 분석했다. 시작/종료 marker로 워밍업 샘플을 제외했다. JFR을 붙인 실행의 처리량은 앞선 무계측 성능 결과와 섞지 않는다.

아래 비율은 **Java ExecutionSample의 leaf 위치 비율**이다. 전체 CPU 사용시간의 정확한 백분율이 아니며, 1회·짧은 샘플링이므로 JIT 인라이닝과 샘플링 편향이 있다. native 메서드 표본은 별도 집계했다. 호출 스택의 inclusive 비율을 더하면 중복되므로 합산하지 않았다.

작은 응답 32개 / 128연결, RKern의 Java 실행 샘플 378개:

| leaf 함수 | 샘플 | 비율 |
|---|---:|---:|
| Cryptor.encrypt | 68 | 18.0% |
| CIOSlotPool.release | 66 | 17.5% |
| CIOUserSocket.finishStagedSend | 40 | 10.6% |
| CIOUserSocket.updateSendReadPauseLocked | 35 | 9.3% |
| CIOBufferPool.Lease.start | 28 | 7.4% |
| CIOUserSocket.encrypt | 27 | 7.1% |
| CIOBufferPool.Bucket.acquire | 21 | 5.6% |

슬롯 반환·lease 시작·bucket 대여의 leaf 표본은 합계 115/378(30.4%)이다. **풀을 대여·반납하고 전송을 등록하는 횟수부터 줄이는 것이 우선 후보**다. 이 숫자가 그대로 개선 가능 비율이라는 뜻은 아니다. CYieldLock을 주원인으로 단정할 자료는 확보하지 못했다.

4KiB 송신:

| 코어 | Java 실행 표본 | Cryptor.encrypt leaf |
|---|---:|---:|
| RKern | 691 | 646 / 691 = 93.5% |
| Netty epoll | 651 | 601 / 651 = 92.3% |

이 경로의 암호화는 양쪽이 같은 코드를 사용한다. **큰 패킷의 CPU 공통 비용**이 확인된 것이며, 이것만으로 RKern의 긴 p99 지연 원인을 설명할 수 없다.

I/O 스레드만 거른 추정 heap allocation도 집계했다. 작은 패킷 RKern에서는 JDK NIO.2의 Integer/EPollPort.Event가 크게 보이고, Netty에서는 DefaultChannelPromise가 크게 보였다. 측정된 pool acquire/release 비용을 곧바로 대량 heap 객체 생성이나 GC 문제로 해석하면 안 된다. 집계 ZIP에 원시 함수별 수치를 보존했다.

## 수정 1 — 전송 등록과 lease 왕복 횟수 줄이기

파일: `CIOUserSocket.java`의 `tryWriteAction`(1025), `appendActionLocked`(1073), `finishStagedSend`(820), `acceptStagedSend`(831), `commitStagedOutboundFrameLocked`(853). 연결 파일은 `CIOBufferPool.java`의 `Bucket.acquire`·`Lease.start/release`, `CIOSlotPool.java`의 `acquire/release`다. 줄 번호는 v5 기준 근사 위치이며 함수명을 우선한다.

1. 먼저 요청 1건당 lease acquire/release, tail append 성공/실패 이유, ticket 예약·drain 횟수를 낮은 샘플 비율로 센다. 모든 패킷마다 전역 AtomicLong을 추가하지 않는다.
2. 기존에 커밋됐지만 아직 암호화·I/O에 제출되지 않은 tail을 재사용하는 범위를 확인한다. 추가 버퍼를 얻었다가 기존 tail에 복사하고 곧바로 반납하는 경로의 빈도를 줄인다.
3. 여러 작은 패킷을 한 번에 수락·기록하는 **네트워크용 batch entry**를 검토한다. byte/frame 수락 검증, 큐 등록과 publish를 묶되 각 논리 패킷의 헤더·암호화 경계는 유지한다. 기존 단건 API와 실패 계약은 유지한다.
4. 최적 경로는 이전 예약 생산자·staged SEND·callback ticket이 없고 writable tail 소유권이 확정된 경우로 제한한다. 조건을 만족하지 않으면 기존 FIFO 경로를 쓴다. 단순히 락이나 ticket 검사를 없애는 수정은 하지 않는다.
5. 슬롯 generation·중복 반환·stale lease 검사는 보존한다. 먼저 연산 빈도를 낮춘 다음 allocator 내부 변경의 효과를 따로 측정한다.

성공 기준: 같은 32응답 부하에서 acquire/release·ticket 횟수와 요청당 CPU 시간이 줄고, 수락/완료 바이트·순서가 동일해야 한다. 개별 연결에 16KiB lease를 영구 보유시키는 방식은 1만 연결 메모리·풀 가용성을 별도 계산해야 한다.

## 수정 2 — batch 단위 카운터·워터마크 반영

파일: `CIOUserSocket.java`의 `bytesAdmitted`(293), `bytesReleased`(317), `updateSendReadPauseLocked`(300).

지금은 8바이트 액션을 append할 때마다 bytesAdmitted → 워터마크 검사를 한다. 현재 코드도 pause 상태가 바뀔 때만 volatile 값을 쓰므로, 이미 있는 최적화를 다시 제안하는 작업은 피한다.

batch API에서는 N개 프레임·총 byte를 먼저 검사하고 카운터를 일괄 반영하는 방법을 검토한다. high-water에 걸리는 경계를 누락하지 않고, 실패/abort에서 예약 bytes와 frames를 정확히 되돌려야 한다. low-water 수신 재개는 락을 푼 뒤 한 번만 게시한다. 처리량을 올리려고 역압을 끄거나 큐를 무제한 늘리지 않는다.

성공 기준: 워터마크 검사 횟수가 줄어도 READ pause/resume과 부분 WRITE/abort 회계가 기존과 같아야 한다. 수정 1과 CPU 개선치를 중복 합산하지 않는다.

## 수정 3 — 긴 p99는 타임라인을 먼저 분리

파일: `CIOUserSocket.java`의 `onRead`(1219), `dispatchDecodedFrame`(1365), `kickRead`(1165), `drainSocketCallbacks`(1398), `publishWriteCompletion` 및 `SendBurst.flush`.

**이번 JFR로 p99 원인은 확정하지 못했다.** 기존 지연에는 클라이언트 enqueue 대기도 들어 있고, JFR 실행 표본만으로 네트워크 큐 대기 시간을 복원할 수 없다.

다음 수정을 하기 전에 요청 sequence별로 아래 시각을 낮은 비율로 샘플링한다. 연결 ID+sequence로 대응시키고, 게임 패킷 형식은 변경하지 않는 테스트 계측을 사용한다.

- 클라이언트 enqueue / 실제 socket write 완료
- 서버 READ 프레임 진입 / handler 종료
- 서버 WRITE 제출 / WRITE 완료
- 클라이언트 마지막 응답 수신

이를 통해 클라이언트 대기·서버 처리·WRITE 대기를 분리한다. 서버 worker 편중과 한 callback의 처리 시간·프레임 수, 즉시 완료 후 연속 재진입 횟수도 함께 기록한다.

서버 READ/완료 처리의 독점이 확인되면 프레임 수 또는 짧은 시간 budget을 두고 기존 네트워크 실행기로 continuation을 넘기는 실험을 한다. 고정 budget 값이 정답이라고 가정하지 않는다. handler 중간을 잘라 실행하지 않고 프레임 경계에서 양보한다.

남은 encrypted frame·부분 프레임·cipher state를 보존하고, READ 소유권을 넘긴 뒤 continuation을 한 번만 게시해야 한다. 처리 중 추가 READ를 겹쳐 게시하거나, READ_KICK만 올려 같은 스레드의 while 루프를 계속 돌게 만드는 구현은 피한다. NPC 실행기로 옮기거나 패킷마다 새 스레드·새 task 객체를 만드는 방식은 사용하지 않는다.

성공 기준: 같은 offered load에서도 p95/p99와 최대 대기가 줄어야 한다. 처리량 감소·클라이언트 병목 때문에 지연만 낮아진 결과를 개선으로 판정하지 않는다.

## 수정 4 — 암호화는 별도 후순위 작업

파일: `Cryptor.java`의 `encrypt`(210 부근), `CIOUserSocket.preparePackedEncryption`(1103), `postWrite/postGather`.

현재 암호화는 이미 8바이트 unroll을 사용한다. hash 배열을 순서대로 갱신하는 상태 의존성이 있어 SIMD를 붙이면 자동으로 빨라지는 구조가 아니다. 먼저 wrapper·bounds check·중복 검증·JIT 인라이닝 여부를 확인하고 실제 암호화 바이트 처리 비용을 따로 측정한다.

알고리즘·키 갱신 순서·패킷별 hash 상태는 바꾸지 않는다. 하나의 큰 전송 버퍼 전체를 한 패킷처럼 암호화하는 변경도 하지 않는다. 이 최적화는 Netty도 사용하는 공통 비용을 줄이는 작업이며, RKern/Netty 차이와 p99 문제를 전부 해결한다고 주장하지 않는다.

## 적용 순서와 회귀 검사

수정 1 → 수정 2 → 지연 타임라인 계측 → 근거가 확인된 공정성 조정 → 공통 암호화의 별도 미세 최적화 순서로 진행한다. 각 단계의 변경과 A/B를 분리한다. 이번 자료는 수정 지침이며 아직 적용·개선 검증한 패치가 아니다.

- 기존 실제 transport/buffer/cipher 회귀, 액션 batching 회귀, 동시 생산·종료 검사를 사용한다.
- golden wire bytes, 혼합 길이·분할 수신, 즉시/부분 WRITE 완료, 앞선 인코더 정체, close 중 예약 생산자, 포화·abort·READ 재개를 확인한다.
- 현재 비교의 작은 패킷·1,024연결·4KiB 송신을 같은 조건으로 반복하고 CPU/요청·처리량·p50/p95/p99·수락 실패·pool 사용량을 함께 기록한다.
- 1만 연결의 활성+대기 버퍼 메모리 예산을 계산하고 장시간 부하는 따로 검증한다. 이번 결과는 1만 동접 판정이 아니다.

[프로파일 집계·측정 명령·분석 코드 ZIP](downloads/RKern_network_profile_20261027.zip)

원시 JFR은 작업 공간에 보존했다. 공개 ZIP은 함수 집계와 프로파일 하네스·측정 metadata이며 원시 환경 정보가 들어갈 수 있는 JFR 본체는 포함하지 않는다. Linux의 짧은 분리 실험 1회씩이므로 실서버의 모든 병목을 확정한 목록으로 사용하지 않는다.
