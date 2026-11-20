# RKern JProRing 후속 구현과 검증 결과

[후속 작업 지시서](NEXT_IMPLEMENTATION_KO.md)의 기준 소스는 [`9a9c25a`](https://github.com/choeyeonggeol16-sketch/1234/commit/9a9c25a)다. 게임 SEND 후보의 비교 기준은 동시 접속 대기열 수정까지 적용한 [`02b0d4e`](https://github.com/choeyeonggeol16-sketch/1234/commit/02b0d4e), READ·gather 측정 코드는 [`94c0d79`](https://github.com/choeyeonggeol16-sketch/1234/commit/94c0d79)다. 종료 회귀 기준은 [`8fd305f`](https://github.com/choeyeonggeol16-sketch/1234/commit/8fd305f)다. W02 후속 생산 코드는 [`e6e44cf`](https://github.com/choeyeonggeol16-sketch/1234/commit/e6e44cf)다.

## 실제 게임 리스너 A/B와 수정 판정

`Server.startLoginServer`, 두 CIO 샤드, `CSocket`, `CUserSocket`, 실제 키 교환·암복호화, `WritablePacket`을 통과하는 외부 클라이언트 fixture를 사용했다. 응답마다 독립 기준 암호의 바이트와 일치하는지 확인했다. 처리 핸들러는 테스트용 ALIVE 응답이며 DB·월드·게임 클라이언트 실행이 필요한 항목은 아래에 별도로 표시했다. [fixture](../jproring_cio_integration_20261120/GameSendBenchmark.java) · [실행기](../jproring_cio_integration_20261120/run_game_ab.py)

기존 게임 accept backlog 5에서 16개 동시 접속 중 일부가 초기 키를 받지 못했다. 같은 조건에서 3초 시점의 서버 진단은 16건 중 13건이 등록된 상태를 보여줬다. 서버 생성 시 게임 연결 용량을 기준으로 대기열을 64–512 사이에서 잡도록 수정했다. 기존 IP·MAC·계정 등록 제한을 그대로 확인했고, 수정 후 24개 A/B 회차가 완료됐다. [수정 전 실패 기록](measurements/w01-before-backlog-failures.json) · [수정](https://github.com/choeyeonggeol16-sketch/1234/commit/02b0d4e)

Linux 공유 환경, CPU 할당량 2코어, OpenJDK 21.0.13, 준비 800 ms·측정 1,400 ms, 조합당 독립 서버·클라이언트 JVM 3회 중앙값이다. 지속 연결 1개는 32B 일반 패킷과 window 1, 연결 16개는 256B 일반·바이트 혼합과 window 16이다. RTT는 요청 송신부터 응답 확인까지의 client clock이며 batch 안의 요청은 공통 송신 시작점을 사용한다. 서버 CPU·할당은 같은 측정 구간 양 끝의 JVM 누적 카운터 차이다. 공유 환경의 짧은 screening이며 전용 머신의 30초 이상 반복은 남았다. [원본 CSV](measurements/w01-game-ab.csv) · [환경](measurements/w01-game-environment.json)

| 조합 | 1연결 응답/초 | 1연결 p99 µs | 16연결 응답/초 | 16연결 p99 µs | 16연결 서버 CPU ns/응답 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 기존 SEND | 27,239 | 214.8 | 161,657 | 34,763.7 | 5,389 |
| head 직결만 | 23,621 | 219.2 | 138,960 | 35,444.0 | 6,919 |
| 바이트 append만 | 20,698 | 235.5 | 162,659 | 34,606.1 | 6,015 |
| 두 후보 모두 | 24,981 | 213.0 | 154,709 | 35,001.9 | 6,082 |

- head 직결은 반복적으로 실행됐고, 16연결에서 처리량이 약 14% 감소하면서 CPU/응답이 약 28% 증가했다. `rkern.network.javaDirectPacketCommit` 옵션과 전용 경로를 제거했다.
- 바이트 append는 외부 인코더가 멈춰 있는 기능 fixture에서 실행이 가능했지만 비교한 실제 게임 경로 부하에서는 0건이었다. `rkern.network.javaDirectByteAppend` 옵션과 전용 경로를 제거했다. 범용 writer를 tail에 직접 붙이려면 미게시 ticket, 재진입, 재시도에서 앞선 lease와 writer 범위를 보호하는 계약이 필요하다.
- 기존 게임 SEND의 FIFO·암호화·lease 순서를 유지했고, Java READ는 동일 owner가 버퍼를 직접 등록하도록 바꿨다. RECV는 미소비 offset을 유지하다가 이어 쓸 공간이 부족할 때만 복사한다. Java gather는 부분 완료 뒤 이미 소비된 descriptor를 재제출하지 않고, 한 segment가 남으면 하나의 버퍼로 송신한다. [READ·gather 변경](https://github.com/choeyeonggeol16-sketch/1234/commit/94c0d79)

## 회귀 검사와 기본 경로 재측정

- Windows의 첫 100회 재시작에서 CIO 스레드의 `advance()`와 `awaitWork()` 사이에 hosted attach가 확정되면 owner가 등록되기 전에 검사하던 경쟁 조건이 드러났다. 활성화 뒤 첫 호출이 `awaitWork()`인 경우에도 owner를 등록하도록 수정했다. 같은 호출 순서를 고정한 테스트가 이전 코드에서 `Wrong connection owner`로 실패하고 수정 뒤 통과했다. [실패 실행](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44673306244) · [수정](https://github.com/choeyeonggeol16-sketch/1234/commit/8fd305f)
- Linux Java 21 전체 소스 컴파일에서 기존 경고 5건이 있었다. 독립 parser/transport, 방송 포맷 42종의 1,008개 벡터, 게임 listener 재시작 100회와 RST 재접속 1,000회가 통과했다. 재시작 전후 `/proc/self/fd`는 25→26개였고 각 종료 뒤 registry와 모든 대여·반환 수가 맞았다. [독립 회귀](../jproring_selector_transport_20261120/SelectorTransportTest.java) · [게임 회귀](../jproring_cio_integration_20261120/SelectorCioIntegrationTest.java)
- 최종 Windows WEPoll에서 재시작 100회와 재접속 1,000회가 통과했다. Linux CI의 네 전송 경로 framed 비교와 실제 게임 burst 스모크도 통과했다. [Windows 회귀](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44673772851) · [Linux 비교 및 게임 artifact](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44673772828)

READ·gather 변경판 `94c0d79`의 기본 모드를 동일한 짧은 설정으로 각 2회 다시 측정했다. 1연결 응답/초 중앙값은 **22,944**였고, 16연결 burst는 **170,777**였다. burst CPU 중앙값은 **5,111 ns/응답**, p99는 **34.73 ms**였다. 기존 기본 모드와 대조하면 단일 연결 처리량과 burst 처리량의 방향이 달랐고, 단일·burst 기록 모두 회차 간 변동이 있었다. 송수신 경로의 독립 CPU 기여도와 안정된 p99 변화는 장시간 전용 장비 검증이 필요하다. 128개 게임 연결에서도 두 회차가 완료됐다. [기본 경로 CSV](measurements/w12-game.csv) · [재측정 환경](measurements/w12-game-environment.json)

별도 짧은 JFR에서 실행 표본 67건 중 `Cryptor.encrypt`가 14건, `tryWritePacketInternal`과 `packAdjacentStagedPacketLocked`가 각각 3건이었다. 할당 표본 57건 중 byte 배열은 27건이었다. 표본은 JVM 시작부터 종료까지 포함한다. 비용 비율 확정이나 범용 tail 변경의 채택 판정에는 반복 프로파일이 필요하다. [JFR 표본 요약](measurements/w12-jfr-summary.json) · [GC·safepoint 기록](measurements/w12-profile-gc.log)

2 owner의 raw echo 1,024개와 5,000개 소켓에서 바이트 및 종료 후 대여 반환을 확인했다. 5,000 연결에서 700 ms 동안 응답 20,551건을 확인했고 p99는 **464.5 ms**였다. 공유 CPU에서 부하 생성기도 함께 실행한 단일 회차다. 대규모 실제 게임 세션은 별도 환경에서 검증해야 한다. [raw CSV](measurements/w12-raw-scale.csv) · [환경](measurements/w12-raw-environment.json)

## A01–A30 최종 확인 상태

| 번호 | 판정 | 변경 경로와 확인 사항 |
| --- | --- | --- |
| A01 | 완료 | `SelectorTransport` 취소/종료 예외를 격리했다. 같은 owner의 다른 소켓, 종료 호출 수와 lease 회수가 회귀를 통과했다. |
| A02 | 부분 | `SelectorRawIo` 정상 drain이 부분 gather를 끝낸 뒤 FIN을 보낸다. 실제 게임 graceful FIFO 및 미게시 송신·RST를 확인했다. close queue 포화와 타이머 교차 조합은 남았다. |
| A03 | 부분 | `SelectorTransport.bindHosted` 롤백과 wait-first owner 등록을 고쳤고, 기존 CIO에 listener 100회 재설치를 검증했다. owner 강제 실패와 shutdown timeout 후 재시도 조합은 남았다. |
| A04 | 부분 | `CSocket` 소유 READ 등록 및 게임 핸들러/타이머 shard, 20 외부 생산자 FIFO를 검증했다. 월드 move/attack/skill과 DB·NPC 호출자 전체는 환경이 필요하다. |
| A05 | 부분 | `CUserSocket` 초기 키, rolling cipher, 필터된 opcode, 최대 wire 65,535B와 split header/body를 검증했다. 실클라와 원본 DLL wire capture는 외부 자료가 필요하다. |
| A06 | 부분 | `JavaCioServer` IP·MAC·계정 한도와 중복 진입 및 정확한 ledger 복귀를 확인했다. 실제 MySQL 세션과 재로그인 경합은 환경이 필요하다. |
| A07 | 부분 | `CSocket.requireNativeServer`가 FFM 전용 경계를 검사한다. DLL 없는 리스너를 검증했다. 운영 환경 패킷과 관리 명령 전수 테스트는 남았다. |
| A08 | 부분 | raw, length-framed, 실제 RKern 게임 리스너 benchmark/기능 fixture를 구분한다. 같은 세션 정책을 가진 Netty 게임 adapter의 A/B는 준비되지 않았다. |
| A09 | 기각 | `CUserSocket.tryWritePacketInternal`의 실험 head 직결 경로를 CPU/처리량 열세로 제거했다. |
| A10 | 반영 | 상한을 가진 writer의 tail 직접 인코딩을 구현했고, 기존 `BytesWritablePacket`과 protobuf 캐시 `Raw`를 적용했다. 게임 A/B와 FIFO·추가 슬롯 대여 제거를 확인했다. [후속 검증](FOUR_ITEMS_KO.md) |
| A11 | 반영 | READ와 대형 READ에 shard 우선 할당을 적용했다. 공유 풀 전역 상한과 한 shard의 전체 슬롯 사용, 타 스레드 반환을 검증했다. [후속 검증](FOUR_ITEMS_KO.md) |
| A12 | 부분 | `CSocket.read`는 같은 owner의 raw 등록에서 monitor 재진입 한 번을 없앴다. 외부 READ와 WRITE descriptor 동기화 경계는 기존 계약을 사용한다. WRITE path A/B는 남았다. |
| A13 | 반영 | `CUserSocket.onRead`에서 consumed prefix offset을 두고 실제 수신 공간이 부족할 때 정리한다. 201개 연속 프레임, 65,535B 분할 프레임 및 암호 순서를 검증했다. |
| A14 | 측정 후 유지 | 소진 prefix 제거와 단일 segment 송신을 검증했다. 게임 gather 4/8/16 비교에서 기본 8의 p99와 p99.9가 가장 낮았다. [후속 검증](FOUR_ITEMS_KO.md) |
| A15 | 부분 | 실제 20/100/500 소켓에서 원본 1회 생성, 연결별 암호화와 lease·registry 회수를 재검증했다. 운영 월드 팬아웃 비용은 남았다. [후속 검증](FOUR_ITEMS_KO.md) |
| A16 | 부분 | mailbox depth/정점/거절 수 및 포화 중 정확히 한 번 취소를 검증했다. MPSC 후보 채택 전 producer별 비용 측정이 필요하다. |
| A17 | 완료 | 위치를 저장하는 타이머 힙의 취소·앞당김·재예약·종료 회귀를 유지했다. [5,000 연결 재예약 측정](IMPLEMENTATION_KO.md#a17-타이머-재예약-실측) |
| A18 | 부분 | coalesced 게임 READ와 같은 CIO shard의 타이머 수행을 검증했다. 느린 실제 핸들러·방송 겹침의 deadline drift와 quiet p99는 남았다. |
| A19 | 부분 | 리스너 대기열 수정 후 실제 게임 리스너에서 5,000개 소켓의 초기 키 전송을 검증했다. accept owner별 CPU 비용은 남았다. [후속 검증](FOUR_ITEMS_KO.md) |
| A20 | 부분 | 8 MiB direct limit 아래 실제 rebind/reset을 수행했다. 슬롯 반환·FD 기록과 raw 5,000 연결을 확인했고, 게임 장기 retained heap 검증은 남았다. |
| A21 | 부분 | 하나의 느린 연결이 대형 풀을 사용 중일 때 같은 owner의 정상 응답, 풀 거절, stall timeout 및 종료 후 반환을 확인했다. 실제 1%/10% 혼합 부하는 남았다. |
| A22 | 부분 | owner와 pool 진단에 선택적 write 호출·gather 호출·segment 집계를 추가했다. copy/CAS 및 세밀한 lock wait 비용은 남았다. [후속 검증](FOUR_ITEMS_KO.md) |
| A23 | 부분 | Linux raw·framed·game 경로 각각에서 바이트를 검증했다. game Netty 어댑터는 A08과 같다. |
| A24 | 부분 | raw 1,024/5,000, 게임 리스너 5,000 혼합(유휴 80%, 요청 20%) 60초를 검증했다. slow peer 혼합과 전용 장비 matrix는 남았다. [후속 검증](FOUR_ITEMS_KO.md) |
| A25 | 부분 | 실제 게임 A/B에서 CPU, 할당, 처리량, p50/p99/p99.9를 남겼다. 장시간 반복, open-loop 및 GC 관측 확대가 남았다. |
| A26 | 부분 | 게임 리스너 5,000 연결에서 RSS peak, direct backing, FD 및 모든 lease 회수를 기록했다. 운영 환경의 장기 retained heap은 남았다. [후속 검증](FOUR_ITEMS_KO.md) |
| A27 | 부분 | pin된 Linux Netty NIO·epoll·io_uring과 RKern의 framed 응답, Windows WEPoll 기능을 실행했다. 동일 호스트 조건의 Windows 성능 비교는 남았다. |
| A28 | 완료 | Linux CI는 framed와 game fixture의 CSV/환경/로그를 보존하고 Windows CI는 listener 스트레스와 회귀를 수행한다. |
| A29 | 부분 | 독립 테스트가 raw/framed 공통 close·buffer 계약을 확인하고 실제 게임 회귀에서는 계정·opcode·cipher를 추가로 확인한다. 모듈 통합은 남았다. |
| A30 | 부분 | 이득이 입증되지 않은 두 후보를 제거하고 실제 리스너 실행에서 CPU·할당을 기록했다. 대표 부하별 진단 비용·JFR 확장 검증은 남았다. |

## 실행과 한계

공유 환경에서 다음 명령으로 JDK 21 빌드와 A/B를 재현한다. 네 조합을 실행하려면 비교 커밋 `02b0d4e`를 체크아웃하고, 현재 코드에서는 `--mode off`로 기본 경로를 측정한다.

```bash
python3 rkern/docs/jproring_cio_integration_20261120/run_game_ab.py \
  --trials 3 --warmup-ms 800 --measure-ms 1400 --output /tmp/rkern-game-ab
```

`--jfr`를 사용하면 별도 프로파일링 실행의 recording과 GC/safepoint 로그를 출력 디렉터리 아래에 남긴다. 실제 게임 클라이언트 및 MySQL을 포함한 월드 접속, DLL wire 비교, 전용 5,000 game session 혼합부하와 30–60분 soak는 실행할 자료와 환경이 필요하다.

## W02 후속 검증

`WritablePacket.DirectTail`에 헤더를 포함한 크기 상한을 선언한 Java owner 패킷은 인접한 미전송 lease의 끝에 직접 인코딩할 수 있다. 기존 FIFO 티켓을 예약하고 serializer가 lease를 고정한다. 인코더는 상태 락 밖에서 실행하며, 다른 생산자는 다음 티켓에 게시한다. 상한 초과 시 한 번만 호출한 인코더를 실패 처리하고 해당 티켓을 취소한다. [구현](https://github.com/choeyeonggeol16-sketch/1234/commit/e6e44cf)

실제 암호화 게임 소켓에서 외부 인코더 20개가 예약을 보유한 상태로 정상 송신, 직접 인코딩 및 내부 재진입 송신을 섞었다. 직접 인코딩 packet이 임시 lease를 사용하면 실패하는 테스트는 기준 `043e52f`에서 실패하고 후속 코드에서 통과했다. 크기 초과 사례에서 한 번만 실행하고 추가 lease나 잘못된 송신을 남기지 않았다. Linux·Windows 재시작 100회 및 reset/reconnect 1,000회도 통과했다. [회귀 소스](../jproring_cio_integration_20261120/SelectorCioIntegrationTest.java) · [Windows](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44676276829) · [Linux CI](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44676276740)

직접 인코딩의 대여·복사 변화는 위 회귀에서 검증했다. 운영 패킷 적용 범위를 넓히는 데 필요한 CPU/p99 실측과 owner-local 풀 검증은 남았다.
