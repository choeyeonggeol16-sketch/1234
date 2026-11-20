# RKern Linux TCP 벤치마크

1,024개와 5,000개 지속 연결의 처리량, p99·p99.9, CPU, 메모리 결과와 회차별 데이터를 추가했다. [고부하 비교 보고서](high_load_20261120/REPORT_KO.md)

2026-11-20 GitHub Actions Linux 러너에서 RKern의 Java NIO Selector와 Netty 4.2.21.Final의 NIO, epoll, io_uring을 64바이트 TCP 에코로 비교했다. 4개 전송 경로에서 유효한 표본을 얻었다. [지속 연결 실행](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44644857557) · [새 연결 실행](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44645944949) · [회차별 수치](results.csv) · [환경 정보](environment.json)

16개 지속 연결에서 RKern의 처리량 중앙값은 **227,471 요청/초**, RTT p99 중앙값은 **138.1 µs**였다. 같은 조건에서 Netty epoll은 **214,238 요청/초**, RTT p99는 **147.5 µs**였다.

## 결과

모든 지연 시간의 단위는 **µs**다. 지속 연결 수치는 3회 중앙값이다. p99 옆 괄호는 회차별 최솟값–최댓값이다. 새 연결은 네 경로가 모두 완료한 공통 1회 결과다.

### 지속 연결 1개

| 전송 경로 | 전체 요청/초 | 연결당 요청/초 | RTT p50 | RTT p95 | RTT p99 (범위) |
| --- | ---: | ---: | ---: | ---: | ---: |
| RKern Java Selector | 71,667 | 71,667 | 8.0 | 33.2 | 40.5 (40.0–42.0) |
| Netty NIO | 76,525 | 76,525 | 8.3 | 33.0 | 37.8 (36.6–39.3) |
| Netty epoll | 68,141 | 68,141 | 10.2 | 33.3 | 40.7 (33.9–41.3) |
| Netty io_uring | 84,260 | 84,260 | 8.4 | 33.1 | 37.2 (37.2–42.2) |

### 지속 연결 16개

| 전송 경로 | 전체 요청/초 | 연결당 요청/초 | RTT p50 | RTT p95 | RTT p99 (범위) |
| --- | ---: | ---: | ---: | ---: | ---: |
| RKern Java Selector | 227,471 | 14,217 | 63.9 | 126.3 | 138.1 (137.6–138.2) |
| Netty NIO | 203,271 | 12,704 | 71.1 | 138.4 | 156.1 (154.9–159.0) |
| Netty epoll | 214,238 | 13,390 | 67.1 | 131.9 | 147.5 (147.3–149.1) |
| Netty io_uring | 206,502 | 12,906 | 74.3 | 91.3 | 102.5 (99.2–102.5) |

### 동시 접속 작업자 8개

각 작업자는 소켓을 열고 연결한 뒤 에코를 한 번 확인하고 소켓을 닫는 과정을 반복했다. `TCP connect` 시간은 `connect()` 호출부터 반환까지다. `첫 응답` 시간은 클라이언트가 소켓을 만들기 직전부터 출발지 주소 바인딩을 거쳐 64바이트 응답 수신을 완료할 때까지다. 초당 처리 연결은 첫 응답 검증을 완료한 연결 수다.

| 전송 경로 | 처리 연결/초 | 작업자당 연결/초 | TCP connect p50 | TCP connect p99 | 첫 응답 p50 | 첫 응답 p99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| RKern Java Selector | 34,305 | 4,288 | 16.7 | 36.5 | 208.0 | 543.6 |
| Netty NIO | 24,900 | 3,112 | 18.2 | 60.6 | 272.6 | 748.1 |
| Netty epoll | 28,702 | 3,588 | 17.8 | 67.4 | 239.4 | 918.3 |
| Netty io_uring | 28,587 | 3,573 | 18.3 | 59.6 | 243.6 | 859.4 |

새 연결 재측정 중 12개 실행에서 7개가 TCP 연결 타임아웃으로 중단됐다. 네 경로가 모두 완료한 첫 라운드만 위 표와 CSV에 담았다. 두 번째 라운드에 완료한 Netty NIO 기록과 중단 내역은 실행 로그에 있다. 새 연결 수치는 반복 검증이 부족하다. [새 연결 실행 로그](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44645944949)

### 서버 메모리

최고 RSS는 16개 지속 연결 조건까지 서버 JVM의 3회 실행에서 관측한 `/proc/<pid>/status`의 가장 큰 `VmHWM`이다. direct 열은 각 회차 종료 시점의 JVM `BufferPoolMXBean` 값의 중앙값이다. 풀 열은 RKern 에코가 확보한 바이트 또는 Netty 할당기가 확보한 direct 메모리다. io_uring의 링과 커널 소켓 버퍼는 이 풀 지표에 포함되지 않을 수 있다.

| 전송 경로 | 최고 서버 RSS (MiB) | JVM direct 버퍼 (KiB) | 에코 또는 Netty 풀 (KiB) |
| --- | ---: | ---: | ---: |
| RKern Java Selector | 51.6 | 1.0 | 1.0 |
| Netty NIO | 117.5 | 4,104.0 | 4,096.0 |
| Netty epoll | 112.8 | 4,152.0 | 4,096.0 |
| Netty io_uring | 121.4 | 4,264.0 | 4,096.0 |

## 측정 조건

- 지속 연결 러너는 Linux `7.0.0-1014-aws`, Intel(R) Xeon(R) 6975P-C, 가용 CPU 4개였다. 새 연결 러너는 Linux `7.0.0-1017-azure`, INTEL(R) XEON(R) PLATINUM 8573C, 가용 CPU 4개였다. 각 실행 안에서 네 방식을 같은 러너로 비교했다. JDK는 `openjdk version "21.0.13" 2026-10-20 LTS`을 사용했다.
- 전송 경로당 서버 I/O 스레드 1개, backlog 256, `TCP_NODELAY` 활성화, loopback TCP. 서버와 부하 생성기는 별도 JVM에서 실행했다. 두 JVM은 `-Xms64m -Xmx256m -XX:ActiveProcessorCount=2`로 실행했다.
- 회차마다 백엔드별로 새 서버 JVM을 띄웠다. 지속 연결 실행은 같은 서버 JVM에서 연결 1개와 16개를 차례로 측정했다. 최종 새 연결 실행은 서버 JVM을 다시 띄우고 작업자 8개에 집중했다. 각 조건은 준비 750 ms와 측정 1500 ms를 사용했고 백엔드 실행 순서는 회차별로 순환시켰다.
- 새 연결을 반복할 때 작업자마다 다른 loopback 출발지 주소를 사용했다. 준비 측정에서는 한 주소의 임시 포트를 공유했을 때 접속 p99가 회차별로 크게 갈려 최종 집계에서 제외했다. 최종 러너의 임시 포트 범위는 `32768–60999`이다.
- 클라이언트당 미응답 요청은 최대 1건이다. 측정 구간 안에서 시작하고 완료한 작업만 집계했다. 에코 바이트를 매번 확인했고, 각 회차의 p50·p95·p99는 순위 방식으로 계산했다. 닫힌 루프의 짧은 부하이므로 유입률을 고정했을 때의 큐잉 지연은 별도 검증이 필요하다.
- RKern은 게임 경로에서 사용하는 `SelectorTransport`의 raw I/O에 64바이트 버퍼 재사용 에코를 연결했다. Netty는 pooled `ByteBuf`의 수신 내용을 에코했다. 암호화, 게임 패킷 처리, DB와 실제 월드 접속은 이 비교의 범위 밖이다. Linux의 Java Selector도 내부에서 epoll을 연다.
- 호스팅 러너의 스케줄링과 JVM 준비 상태가 꼬리 지연에 영향을 줄 수 있다. 운영 환경의 p99나 장시간 누수 여부를 확정하려면 실제 워크로드에서 검증해야 한다.

## RKern 게임 TCP 기능 검증

별도 Linux 테스트에서 DLL 없는 작업 디렉터리와 JVM direct 메모리 상한 8 MiB로 기존 게임 리스너를 구동했다. 초기 키, CIO 샤드 2개와 타이머, MAC·계정 규칙, 암호화된 대형 방송을 검증했다. 송신 12건이 대기하는 상황에서 강제로 연결을 끊고, 기본 24회와 횟수를 늘린 1,000회 강제 종료·재접속 테스트에서, 매회 모든 송수신 슬롯과 활성 세션 등록이 회수되는 것을 확인했다. 테스트 클라이언트가 패킷을 보내는 방식이며 MySQL과 실제 게임 클라이언트의 월드 접속은 다루지 않는다. [통합 테스트 소스](../jproring_cio_integration_20261120/SelectorCioIntegrationTest.java) · [Windows 기능 검증 로그](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44643784602)

## 재현

JDK 21과 Python 3.9 이상이 있는 Linux에서 아래 명령을 실행한다. Netty JAR은 Maven Central에서 받아 임시 캐시에 저장한다. io_uring 측정은 링 초기화를 허용하는 Linux에서 실행한다.

```bash
python3 rkern/docs/jproring_linux_benchmark_20261120/run.py --workload persistent --output /tmp/rkern-persistent
python3 rkern/docs/jproring_linux_benchmark_20261120/run.py --workload churn --trials 1 --output /tmp/rkern-churn
```

지속 연결 측정 커밋 `af61a5ba08f7c8569224e158965aae19558078c9` · 새 연결 측정 커밋 `0da0640878b45630986d005b1528fd303d802755`. [벤치마크 코드](run.py) · [GitHub Actions 정의](../../../.github/workflows/jproring-linux-benchmark.yml)
