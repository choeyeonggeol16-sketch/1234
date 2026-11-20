# RKern 네트워크 후속 수정 및 검증

검증한 코드 커밋은 `49252dcf2d557986ebf256e85cddfa487eac5427`이다. Linux와 Windows에서 JDK 21의 Selector 게임 경로를 사용했다. Linux 공유 환경의 CPU 할당량은 2코어다. [기존 감사표](FINAL_IMPLEMENTATION_KO.md) · [Linux CI](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44677717873) · [Windows CI](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44677717762)

## 1. 기존 게임 패킷의 직접 인코딩

`BytesWritablePacket`과 캐시된 protobuf의 `ProtoPacketWriter.Raw`에 프레임 헤더 2바이트를 포함한 크기 상한을 명시했다. 인접한 SEND가 같은 버퍼에 들어가면 확보된 영역에 직접 쓴다. 페이로드가 비어 있거나 한도를 넘는 경우, 그리고 인코더를 재정의할 수 있는 `BytesWritablePacket` 하위 클래스는 기존 인코딩 경로를 사용한다. 20개 외부 producer와 재진입 SEND가 있는 통합 회귀에서 두 패킷이 추가 슬롯 대여 없이 전달되는지와 응답의 암호화 순서를 검증했다. [게임 통합 테스트](../jproring_cio_integration_20261120/SelectorCioIntegrationTest.java)

16연결·window 16·256B 혼합에서 `BytesWritablePacket` 구현만 직전 버전으로 교체해 비교했다. 각 2회 중앙값에서 기존은 173,650 req/s와 5.95µs CPU/req, 적용 후는 197,316 req/s와 4.56µs CPU/req였다. p99는 33.65ms와 33.99ms였다. 준비 1초와 측정 1.7초의 공유 머신 결과이므로 p99 개선으로 해석할 수 없다. protobuf 캐시의 효과는 통합 테스트에서 슬롯 대여와 wire 순서로 확인했다. [실측 CSV](measurements/four-writer-ab.csv) · [A/B 환경](measurements/four-ab-environment.json)

## 2. READ 버퍼의 shard 배정과 메모리 상한

READ와 대형 READ가 자기 shard에 해당하는 비트맵 구간에서 먼저 슬롯을 찾는다. 해당 구간이 포화되면 공유 풀 전체에서 빈 슬롯을 찾는다. 슬롯과 direct 메모리 상한은 풀 전체에 한 번 적용되고 유휴 shard에 예약되는 버퍼는 없다. SEND 할당 경로는 기존 정책을 사용한다.

3개 shard의 초기 구간, 다른 스레드에서 반환한 버퍼의 재대여, 한 shard가 130개 전역 슬롯을 모두 사용할 수 있는지, 131번째 대여 거절, backing buffer 중복 할당 방지 및 모든 대여·반환의 일치를 검증했다. Linux와 Windows WEPoll에서 8 MiB direct 한도의 리스너 재시작 100회와 reset/reconnect 1,000회도 통과했다. shard 배정의 CPU 이득은 이번 시험에서 분리 측정하지 않았다. [풀 회귀 테스트](../jproring_cio_integration_20261120/CIOBufferPoolAffinityTest.java)

## 3. Gather, 락, 큐와 방송

16연결·window 32·4 KiB 응답을 같은 게임 경로에서 보냈다. 한 호출에 포함할 gather segment 수만 4·8·16으로 바꾸고 총 바이트 한도 64 KiB를 유지했다. 각 후보는 준비 1.1초와 측정 1.8초를 2회 실행했다. 아래 값은 중앙값이다. write 횟수는 `SocketChannel.write` 호출 경계에서 셌다. [실측 CSV](measurements/four-gather-ab.csv) · [A/B 환경](measurements/four-ab-environment.json)

| Gather 한도 | req/s | p99 | p99.9 | 서버 CPU/req | 응답 1,000건당 write |
|---:|---:|---:|---:|---:|---:|
| 4 | 36,463 | 51.83ms | 110.89ms | 26.69µs | 251.03 |
| 8 | 40,320 | 46.48ms | 53.36ms | 22.79µs | 125.57 |
| 16 | 41,593 | 48.14ms | 56.94ms | 21.55µs | 93.87 |

16은 write 호출이 적었고 8은 이 시험에서 p99와 p99.9가 가장 낮았다. 8을 기본값으로 유지했다. write 진단 카운터는 `-Drkern.network.measureWrites=true`를 지정할 때만 갱신된다.

8초 burst의 JFR 실행 표본 265개 중 암호화가 113개였다. 기본 프로파일의 10ms 기준에서는 Java monitor 대기 이벤트가 없었다. 이 결과로 더 짧은 경합이나 다중 producer에서의 이득을 판단할 수 없어 락과 큐 정책은 유지했다. 큐 포화 시 승인된 작업의 취소와 정리, 20개 producer와 재진입의 암호화 FIFO를 재검증했다. 방송은 별도 20·100·500 소켓 시험에서 원본 1회 생성, 연결별 독립 암호화, 레지스트리·lease 회수를 확인했다. 실제 월드에서 느린 핸들러와 방송이 겹칠 때의 지연은 남았다. [Transport 테스트](../jproring_selector_transport_20261120/SelectorTransportTest.java) · [방송 테스트](tools/GameFanoutVerification.java)

## 4. 게임 리스너 5,000 연결

실제 RKern 로그인 리스너와 Java 암복호화를 실행했다. 테스트 클라이언트 5,000개가 초기 키를 확인했고 4,000개는 유휴, 1,000개는 각 응답 후 40ms를 쉬며 64B 응답을 요청했다. 준비 5초 후 60초 측정했다. 서버와 생성기는 다른 JVM이며 CPU 한도를 공유한다. RTT는 요청을 보낸 시점부터 응답 검증 시점까지 재며 완료를 기다린 뒤 다음 요청을 보내는 closed-loop 결과다. [측정 CSV](measurements/four-mix5000.csv) · [환경](measurements/four-mix5000-environment.json) · [리소스 시계열](measurements/four-mix5000-resources.csv) · [GC 기록](measurements/four-mix5000-gc.log)

| 검증 항목 | 결과 |
|---|---:|
| 암호화 응답 검증 | 1,401,225건, 23,354 req/s, SEND 거절 0 |
| RTT p50 / p99 / p99.9 | 0.341ms / 20.63ms / 46.48ms |
| 풀에서 실제 할당한 direct backing | 약 20.80 MiB, 구성 상한 약 421.67 MiB |
| 측정 중 RSS / 최대 RSS | 211.49–225.53 MiB / 약 276.61 MiB (연결 종료 중) |
| 종료 후 열린 세션 / 빌린 슬롯 | 0 / 0 |
| 전체 대여 / 반환 | 각각 1,504,280회 |
| 리스너 프로세스 FD | 25 → 5,025 → 25 |

슬롯 반환 후에도 풀은 재사용을 위해 direct backing을 보유한다. Heap 스냅샷은 GC 전후의 사용량이며 retained heap 분석은 포함하지 않는다. 시험용 핸들러는 이동·전투·DB를 실행하지 않는다. 완전한 Windows 게임 클라이언트와 초기화된 MySQL 월드가 제공되지 않아 실제 로그인·필드·전투를 포함한 30–60분 운영 테스트와 운영 p99·메모리 증감 확인은 남았다.

Linux에서 아래 명령으로 동일한 합성 부하를 실행할 수 있다.

```sh
python3 rkern/docs/jproring_cio_integration_20261120/run_game_ab.py \
  --workload mix5000 --mode off --trials 1 --warmup-ms 5000 \
  --measure-ms 60000 --sample-resources --profile-writes --output /tmp/rkern-mix5000
```
