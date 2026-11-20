# RKern · Netty Linux 고부하 전송 벤치마크

2026년 11월 20일, RKern Java Selector와 Netty 4.2.21.Final의 NIO Selector, epoll, io_uring으로 길이 프레임 TCP 에코를 비교했다. RKern은 1,024개와 5,000개 연결에서 가장 높은 처리량과 가장 낮은 p99를 기록했다. 5,000개에서 Netty io_uring 대비 처리량은 **30.9% 높고 p99는 20.3% 낮았다.** RKern의 최고 RSS는 네 경로 중 가장 낮았고 JDK direct 사용량은 가장 높았다. [GitHub Actions 실행](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44681743457) · [회차별 CSV](results.csv) · [환경 JSON](environment.json)

## 1,024개 지속 연결

| 전송 경로 | 성공 회차 | 응답/초 | 연결당 응답/초 | RTT p99 (ms) | RTT p99.9 (ms) | CPU/응답 (µs) | 최고 RSS (MiB) | JDK direct (MiB) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| RKern Java Selector | 3 | 433,848 | 423.7 | 24.05 | 26.21 | 2.90 | 76.6 | 4.03 |
| Netty io_uring | 3 | 359,031 | 350.6 | 32.38 | 40.43 | 4.93 | 123.0 | 8.32 |
| Netty epoll | 2 | 259,354 | 253.3 | 42.13 | 48.34 | 5.51 | 116.5 | 8.10 |
| Netty NIO Selector | 3 | 288,468 | 281.7 | 41.75 | 53.68 | 6.44 | 120.7 | 8.01 |

## 5,000개 지속 연결

| 전송 경로 | 성공 회차 | 응답/초 | 연결당 응답/초 | RTT p99 (ms) | RTT p99.9 (ms) | CPU/응답 (µs) | 최고 RSS (MiB) | JDK direct (MiB) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| RKern Java Selector | 3 | 360,375 | 72.1 | 136.79 | 138.22 | 3.39 | 120.7 | 19.56 |
| Netty io_uring | 3 | 275,369 | 55.1 | 171.60 | 174.81 | 6.81 | 145.7 | 8.32 |
| Netty epoll | 2 | 185,823 | 37.2 | 311.51 | 409.30 | 7.23 | 143.8 | 8.10 |
| Netty NIO Selector | 3 | 181,522 | 36.3 | 287.19 | 341.98 | 9.17 | 147.9 | 8.01 |

## 측정 조건

- 동일 Ubuntu 24.04 GitHub Actions 러너에서 Linux `7.0.0-1014-aws`, Intel Xeon 6975P-C, 가용 CPU 4개, OpenJDK 21.0.13을 사용했다. 서버와 부하 생성기는 별도 JVM이며 각 JVM은 `-Xms64m -Xmx256m -XX:ActiveProcessorCount=2`로 실행했다. 서버 I/O worker는 경로당 2개다.
- TCP loopback에서 2바이트 길이 헤더와 payload 256바이트를 사용했다. 연결별로 최대 8개 요청을 보낸 뒤 응답 바이트를 확인하는 closed-loop 시험이다. 측정 구간 안에서 요청과 응답이 모두 완료된 표본을 집계했다.
- 백엔드별로 서버 JVM을 새로 띄우고 1,024개와 5,000개를 차례로 실행했다. 조건당 준비 1.5초, 측정 2.6초이며 표는 성공 회차의 중앙값이다. Netty epoll은 첫 회차의 1,024개 측정에서 클라이언트 연결이 리셋되어 해당 회차를 중단했다. 5,000개는 그 회차에서 실행하지 않았다. 성공한 후속 2회를 표에 반영했다. [실패와 완료 로그](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44681743457/job/136106518539)
- CPU는 측정 구간의 서버 user·system CPU 시간 합계를 응답 수로 나눴다. 최고 RSS는 각 측정 종료 시점의 프로세스 `VmHWM`을 사용하므로 5,000개 수치에는 같은 JVM에서 앞서 실행한 1,024개 구간도 반영될 수 있다. JDK direct는 측정 종료 시점의 JVM `BufferPoolMXBean` 값이다. 커널 소켓 메모리와 io_uring 링까지 포함한 총량은 이 지표로 산정할 수 없다.
- 측정 범위는 암호화·게임 로직·DB를 제외한 전송 계층 에코다. Linux에서 RKern의 JDK Selector도 내부적으로 epoll을 사용한다. 여기서 비교하는 대상은 RKern의 Java Selector 경로와 Netty의 각 전송 구현이다.

## 재현

RKern 소스 기준 커밋은 `62e5933`이다. 측정용 커밋은 이 소스에 고부하 실행 워크플로를 추가했다. io_uring 초기화가 허용되는 Linux에서 실행한다. [측정용 커밋](https://github.com/choeyeonggeol16-sketch/1234/commit/565769f8ee1bb9759d69d37fa2104116a0aac7e4) · [벤치마크 스크립트](../run.py)

```sh
python3 -u rkern/docs/jproring_linux_benchmark_20261120/run.py \
  --framed --owners 2 --workload persistent --clients 1024 --clients 5000 \
  --window 8 --payload-bytes 256 --trials 3 --warmup-ms 1500 --measure-ms 2600 \
  --output /tmp/rkern-high-load
```
