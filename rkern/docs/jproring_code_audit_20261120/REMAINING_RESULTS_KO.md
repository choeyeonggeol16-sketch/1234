# JProRing 남은 최적화 및 회귀 결과

요청 보고서 `d7436c1` 기준으로 검토했다. 채택한 코드 커밋은 `274bb8d`다. Java 21 Selector, 기존 CIO owner/타이머, 암복호화 및 프로토콜, 방송과 게임 호출부를 유지했다. [작업 보고서](REMAINING_OPTIMIZATION_KO.md) · [A01–A30 감사표](FINAL_IMPLEMENTATION_KO.md)

## R01. READ 풀과 SEND 소유권

READ 및 large READ에 owner당 슬롯 하나를 보관하는 후보를 만들었다. 동일 owner는 전역 비트맵 CAS 없이 슬롯을 재사용했고, 전역 자리가 모자라면 다른 owner의 유휴 슬롯을 가져왔다. 다른 스레드의 반환은 공유 비트맵을 사용했다. 130슬롯 전역 상한, 다른 스레드에서 반환한 슬롯, compact/large 유휴 슬롯 회수, 대여·반환 일치 및 owner당 70,000회 generation 순환을 후보에서 확인했다.

동일한 풀에서 전역 경로와 후보의 대여·반환을 owner당 250,000회씩 번갈아 4회 돌렸다. 공유 Linux 환경의 중앙값은 1/2/4/8 owner에서 각각 69.92/89.70/86.76/62.50ns가 71.53/99.23/117.02/121.21ns로 바뀌었다. 로컬 슬롯 후보를 제거하고 기존 shard 시작 비트맵 정책을 유지했다. SEND는 생산자와 완료 owner가 다를 수 있어 전역 반환을 유지했다. SEND 전용 로컬 구조의 이득은 별도 측정하지 않았다. [R01 측정 CSV](measurements/r01-read-cache-ab.csv)

## R02. 기존 캐시 패킷의 직접 tail 후보

`Proto.sendRaw`에 새 packet wrapper 없이 기존 tail을 사용하는 후보를 시험했다. 20개 외부 producer가 먼저 예약한 통합 회귀에서 `SC_SOCIAL_ACTION_NOTI`의 미리 직렬화된 payload가 추가 SEND 대여 없이 들어갔고 독립 cipher 기준 바이트 및 FIFO가 맞았다.

정상 루프백 16연결·window 16·256B에서 두 차례 측정한 후보 hit는 0이었다. 기존 경로 261,250 req/s, 후보 234,907 req/s였으며 짧은 표본의 변동이 컸다. 비용 증가를 감수할 근거가 없어 후보를 제거했다. 기존 `BytesWritablePacket`과 `ProtoPacketWriter.Raw` 적용은 유지했다. 진단을 켜면 기존 직접 tail의 hit와 병합 복사를 관측할 수 있다. 운영 월드 writer 빈도는 확인하지 못했다. [R02/R03 측정 CSV](measurements/r02-r03-game-candidates.csv)

## R03. Java WRITE 등록

`CSocket`에서 같은 Selector owner가 이미 준비한 단일·gather descriptor는 전달받은 인자로 바로 등록한다. 기존 descriptor 게시의 monitor는 그대로 쓰고 inline 등록의 두 번째 진입을 제거했다. 외부 생산자는 기존 descriptor monitor와 mailbox를 사용한다. 정지 중에는 raw WRITE 등록을 거절하고 실패 완료로 회수한다. 종료·부분 gather·write 0 및 재개, 리셋과 정상 drain을 검증했다.

R02 후보가 동일하게 들어 있는 비교 빌드에서 WRITE 등록 코드만 바꿨다. 서버 CPU/req 중앙값은 4.01µs → 3.82µs, p99는 19.63ms → 19.73ms였다. 1.8초 준비와 2.2초 측정 2회의 screening이며 lock 대기 ns와 운영 p99 개선률은 산정하지 않았다. [R02/R03 측정 CSV](measurements/r02-r03-game-candidates.csv)

## R04. 송신 병합과 Gather

직접 tail hit, 인접 staged copy, 포화 시 staged compact copy, 실제 WRITE 대기열 병합 copy를 분리해 집계한다. `-Drkern.network.measureWrites=true`를 지정한 시험에서만 카운터를 갱신한다. 아래는 Linux 16연결·window 16에서 WRITE 대기열 병합만 끈 후보와 기본값의 비교다. 조합당 준비 0.9초, 측정 1.5초, 2회 중앙값이다. [R04 측정 CSV](measurements/r04-copy-vs-gather.csv)

| 응답 payload | 기존 / 병합 끔 copy B/req | 기존 / 병합 끔 write 호출 / 1,000응답 | 기존 / 병합 끔 서버 CPU µs/req | 기존 / 병합 끔 p99 |
|---|---:|---:|---:|---:|
| 64B | 60.51 / 0 | 61.83 / 124.85 | 5.56 / 6.84 | 34.59 / 34.52ms |
| 256B | 241.50 / 0 | 62.43 / 125.00 | 7.42 / 9.72 | 35.52 / 35.58ms |

64B와 256B에서 병합이 write 호출과 CPU 사용을 줄여 유지했다. Gather 8개·64KiB 상한도 유지했다. write 카운터는 `SocketChannel.write` 호출 경계에서 센다. 기존 4KiB·gather 4/8/16 결과는 별도 기록했다. [기존 Gather 실측](FOUR_ITEMS_KO.md#3-gather-락-큐와-방송)

## R05. 외부 생산자 mailbox

기존 monitor와 큐 용량을 유지한 채, 미리 확보한 배열에 연결과 명령 참조를 함께 게시한다. 접속 등록과 외부 명령마다 만들던 wrapper를 제거했다. 게시와 소비는 같은 monitor에서 이루어지고 슬롯은 callback 전에 비운다. close 제어는 기존 별도 경로를 사용한다. 포화 거절, 승인된 명령의 한 번 실행 또는 취소, 20 producer FIFO, owner 강제 실패 통보와 종료 시 자원 회수를 검증했다.

재사용 명령의 제출 스레드 할당은 1/2/4/8/20 producer에서 기존 24.24–27.28B/완료 명령, 적용 후 0B였다. 큐가 가득 차면 재시도하는 시험으로, 절대 elapsed 성능은 producer 수에 따라 엇갈렸다. 실서비스 NPC·DB 작업 생성 자체의 할당과 wakeup/timer drift는 해당 수치에 포함되지 않는다. [R05 측정 CSV](measurements/r05-mailbox-ab.csv) · [측정 코드](tools/RKernMailboxMeasure.java)

## R06. 종료 회귀와 통합 검증

종료 예산이 만료되면 이전 deadline이 남아 재시도도 항상 실패하던 문제를 수정했다. 시도 종료 시 사용한 예산을 정리하고, 이전 리스너가 새 리스너의 예산을 지울 수 없게 했다. 이미 끝난 owner의 실패는 원인과 함께 전달한다. 첫 종료가 만료된 뒤 다음 호출에서 동일 포트로 재시작하고 초기 키를 보내는 회귀를 추가했다.

Linux JDK 21 전체 컴파일, raw/framed, 100소켓에서 느린 읽기 1% 및 10%, 압력 중 정상 응답, 방송 20/100/500 독립 cipher, 실제 게임 리스너 100회 재시작과 1,000회 RST 재접속을 검증했다. [Linux CI](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44680805097) · [Windows CI](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44680804706)

Windows Server 2025 WEPoll에서 전체 빌드와 동일 리스너 재시작·RST 회귀를 통과했다. 방송 wire 형식 42종의 1,008벡터를 기존 C++ 형식 정의와 비교했다. 이전 리스너 클래스로 종료 회귀를 실행하면 만료 오류로 실패하고 수정한 코드에서는 같은 포트에 재연결된다. [방송 벡터 시험](../jproring_cio_integration_20261120/test_shared_packet_formats.py) · [A/B 환경](measurements/r02-r04-screening-environments.json)

### 최종 5,000 연결

동일 공유 Linux 환경에서 4,000 유휴 연결과 1,000 활성 연결을 유지했다. 활성 연결은 응답 후 40ms 간격으로 다음 요청을 보냈다. 준비 5초 후 60초 측정한 단일 실행이다. [결과 CSV](measurements/r06-mix5000.csv) · [환경](measurements/r06-mix5000-environment.json) · [리소스](measurements/r06-mix5000-resources.csv) · [GC](measurements/r06-mix5000-gc.log)

| 항목 | 측정값 |
|---|---:|
| 검증한 응답 / req/s | 1,446,901 / 24,115 |
| p50 / p99 / p99.9 | 0.314 / 9.30 / 47.31ms |
| SEND 거절 | 0 |
| direct backing / 측정 중 RSS | 20.41MiB / 211.75–216.79MiB |
| peak RSS / FD | 220.93MiB / 25 → 5,025 → 25 |
| 종료 후 세션 / lease | 0 / 0 |
| 전체 버퍼 대여 / 반환 | 각각 1,558,444회 |

직전 60초 실행의 p99는 20.63ms, p99.9는 46.48ms였다. 두 결과 모두 공유 CPU에서 단일 실행했고 이번 p99 변화의 원인을 분리할 수 없다. 게임 fixture로 지연과 회수를 확인했으며, 전원 활성 5,000개와 30–60분 운영 soak, 실제 클라이언트·MySQL 월드, 동일 Windows 호스트에서의 성능 비교는 남았다. Netty NIO·epoll·io_uring은 Linux CI의 length-framed smoke 결과를 참고할 수 있다. [CI 비교](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44680805097)
