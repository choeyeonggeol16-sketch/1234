# JProRing 소켓 통합 및 재검증

2026-11-20 · 기준 `5b54e34` · 적용본 `c88edaf`

## 구현 및 회귀

- Selector의 `Connection`에 `CUserSocket`을 직접 연결했다. `CSocket`과 단일 게임 접속 팩토리 래퍼를 제거했다. TCP 채널, readiness, pending I/O의 소유자는 Selector다.
- READ·WRITE·deadline 완료는 연결에 등록한 엔드포인트에 전달한다. 다른 스레드의 WRITE는 현재 소켓의 SEND lease를 사용하며 기존 Selector mailbox에서 한 번 전달한다. CIO의 세션 완료와 월드 완료는 기존 packet ingress를 사용한다. SEND FIFO, 512개 슬롯, 암호화 순서와 완료 가드는 유지했다.
- transport에서 해제되는 수량과 SEND lease 반환 후 해제되는 게임 접속 수량은 서로 다른 시점에 감소한다. 종료 후 세션 정리가 남으면 retained registry에서 계속 추적한다. 초기 키 송신이 풀 고갈로 거절된 경우 접속 수와 IP 슬롯이 회수되고 다음 접속이 성공하는 회귀를 추가했다.
- Linux Java 21 타입 컴파일과 TCP, 버퍼 affinity 검증을 통과했다. 동일 소켓 20개 외부 생산자의 암호화 FIFO, 64 KiB 경계 프레임, partial gather, 로그아웃 대기 중 리스너 재시작, 120회 reset/reconnect 이후의 lease 반환을 확인했다.
- Windows Server 2025의 Java 21 WEPoll에서 컴파일, 리스너 100회 재시작, 1,000회 reset/reconnect를 통과했다. 종료 시 미반환 lease는 0이었다. [Windows Actions](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44759083044)

## 로컬 Linux 에코 비교

Linux 6.18.57, OpenJDK 21.0.13, CPU quota 2, Selector owner 2개, 256 B 길이 프레임과 window 8을 썼다. 워밍업 1.5초와 측정 2.6초를 세 번 시도했다. 표는 성공 회차 중앙값이다.

| 경로 | 1,024 req/s | 1,024 P99 ms | 5,000 req/s | 5,000 P99 ms |
|---|---:|---:|---:|---:|
| RKern `5b54e34` | 138,917 | 142.36 | 86,943 | 566.12 |
| RKern `c88edaf` | 77,137 | 182.84 | 96,363 | 542.62 |
| Netty 4.2.21 NIO | 47,715 | 266.63 | 37,209 | 1,005.01 |
| Netty 4.2.21 epoll | 70,962 | 196.41 | 66,778 | 812.15 |

Netty epoll은 두 회차를 완료했고 1회차는 1,024연결에서 TCP 응답 시간 초과가 발생했다. 이 Linux 환경은 Netty io_uring의 링 생성을 `Operation not permitted`로 거부했다. 측정 중 일부 회차에 로컬 컴파일과 회귀 작업이 겹쳐 1,024연결의 편차가 컸다. [에코 CSV](local_linux/transport-results.csv) · [환경 및 실패 내역](local_linux/transport-environment.json)

## 로컬 Linux 게임 리스너 전후 비교

양쪽 빌드에서 같은 게임 리스너, 길이 프레임, Java 암복호화 및 SEND 경로를 사용했다. 워밍업 2초와 측정 4초를 두 번씩 순서를 바꿔 실행했다. 1,024 연결은 모두 요청을 보냈고, 5,000 연결에서는 1,000개가 40 ms 간격으로 요청을 보냈다. 모든 회차에서 연결과 lease가 0으로 돌아왔다.

| 연결 | 기준 req/s | 적용 req/s | 기준 P99 ms | 적용 P99 ms | 기준 최대 RSS MiB | 적용 최대 RSS MiB |
|---|---:|---:|---:|---:|---:|---:|
| 1,024 | 125,678 | 120,860 | 148.67 | 177.46 | 94.89 | 92.98 |
| 5,000 | 23,001 | 22,841 | 21.41 | 27.90 | 193.86 | 183.13 |

이 짧은 비교에서는 최대 RSS가 낮았고 P99는 높았다. 게임 리스너 측정은 월드와 DB를 연결하지 않은 루프백이다. Netty 에코 결과와 다른 부하다. RSS와 JVM direct 수치는 커널 소켓 버퍼나 io_uring 링의 전체 메모리를 포함하지 않는다. [게임 CSV](local_linux/game-results.csv) · [환경](local_linux/game-environment.json)

## 장시간 측정

Linux Actions에서 두 연결 수에 각각 10초 워밍업과 30초 측정을 5회 설정했다. 에코와 암호화 게임 리스너는 별도 작업이며 각 작업에서 같은 머신에서 두 RKern 버전을 교대로 실행한다. 로컬 표는 장시간 측정과 별도로 기록했다. [Linux Actions 및 아티팩트](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44759082942)

```bash
python3 -u rkern/docs/jproring_linux_benchmark_20261120/run.py \
  --baseline-root "$BASELINE_CHECKOUT" --framed --owners 2 --workload persistent \
  --clients 1024 --clients 5000 --window 8 --payload-bytes 256 \
  --trials 3 --warmup-ms 1500 --measure-ms 2600 --output /tmp/rkern-echo

python3 -u rkern/docs/jproring_cio_integration_20261120/run_game_ab.py \
  --baseline-root "$BASELINE_CHECKOUT" --mode off --workload load1024 --workload mix5000 \
  --trials 2 --warmup-ms 2000 --measure-ms 4000 --sample-resources --output /tmp/rkern-game
```
