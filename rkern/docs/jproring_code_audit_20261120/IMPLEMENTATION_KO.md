# JProRing 감사 30개 항목 반영 현황

기준은 [감사 보고서](REPORT_KO.md)다. 안정화 수정 [`c9cb876`](https://github.com/choeyeonggeol16-sketch/1234/commit/c9cb876)과 후속 구현 [`eff7fcf`](https://github.com/choeyeonggeol16-sketch/1234/commit/eff7fcf)을 검증했다. `부분`은 구현이나 테스트의 일부가 완료됐다는 뜻이다.

| 항목 | 상태 | 반영 내용 및 남은 검증 |
| --- | --- | --- |
| A01 | 반영 | 일반 종료·취소 콜백 예외를 연결 단위에서 기록하고 격리했다. 콜백 네 곳에 예외를 주입해 정상 연결과 admission 회수를 검증했다. |
| A02 | 부분 | raw gather 잔여 바이트와 실제 게임 FIFO가 FIN 전에 완료됨을 검증했다. 승인 전후 abort·타이머·느린 클라이언트가 교차하는 전체 조합은 별도다. |
| A03 | 부분 | 호스트 설치 도중 오류나 종료 요청이 오면 드라이버를 회수한다. 부분 설치 실패, 시작 중 종료, 기존 CIO 재바인드를 검증했다. 100회 재시작·종료 타임아웃 반복 검증은 남았다. |
| A04 | 부분 | 실제 CIO 샤드와 게임 패킷 처리 affinity, 타이머, 외부 인코더 20개의 암호화 FIFO를 검증했다. DB, NPC, 이동·전투 호출자의 실제 운영 동작은 세션 환경이 필요하다. |
| A05 | 부분 | 독립 프레임의 빈 암호화 입력을 거부하고 Java 암호화 벡터와 게임 초기 키·접속 필터를 테스트했다. 원본 DLL과 실제 클라이언트 wire 대조가 남았다. |
| A06 | 부분 | 레지스트리 락에서 소켓 진단 호출을 분리했다. IP·MAC·중복 캐릭터 진입과 종료 후 카운터 복귀를 검증했다. 실제 계정 재로그인 경합은 DB가 필요하다. |
| A07 | 부분 | Java 소켓에서 native 전용 호출이 잘못 들어오면 lease 예약 전 명확한 오류를 내도록 했다. DLL 없는 게임 리스너 테스트를 추가했다. 운영 클라이언트의 전체 명령 경로는 별도다. |
| A08 | 부분 | raw·length-framed 전송 비교와 실제 게임 리스너 기능 테스트를 분리했다. 운영 채택에는 동등한 게임 경로의 성능과 월드 접속 검증이 남았다. |
| A09 | 후보 | `-Drkern.network.javaDirectPacketCommit=true`로 자기 FIFO head의 SEND만 직결 커밋한다. 외부 SEND 20개가 먼저 예약된 경우 정확한 암호문 순서를 확인했다. 기본 경로 채택 기준은 실제 게임 부하 A/B다. |
| A10 | 대기 | 일반 게임 SEND는 현재 병합 복사를 한다. tail 직접 인코딩을 검증하려면 작성 실패·재시도, 외부 티켓, close에서 바이트와 키 순서가 동일해야 한다. |
| A11 | 부분 | 기존 공유 풀의 슬롯·고갈 진단을 사용한다. bitmap 경합과 원격 반환을 실측한 뒤 owner-local 캐시를 평가할 수 있다. |
| A12 | 대기 | CSocket 동기화 비용을 분리 실측하고 외부 완료·inline reject의 descriptor 수명을 검증해야 한다. |
| A13 | 부분 | 기존 수신 경로는 전량 소비 시 `clear`를 쓰고 부분 프레임이 이미 선두면 복사를 생략한다. 추가 offset 경로의 복사량과 승격 비용을 비교할 수 있다. |
| A14 | 부분 | 송신 window를 설정해 자연 배치를 시험할 수 있다. 게임 gather 크기 변경은 동일 프레임·부하에서 syscall/packet과 지연을 비교한 뒤 판단한다. |
| A15 | 대기 | Java 공유 방송의 수신자별 암호화는 통합 테스트 대상이다. 방송 자체의 복사·락 변경은 팬아웃 기능 및 비용 검증이 필요하다. |
| A16 | 부분 | mailbox 현재 깊이·최고점·거절 횟수를 노출하고 포화 상태를 테스트했다. 큐 교체 후보에는 producer 경합·할당 실측이 필요하다. |
| A17 | 부분 | 샤드별 deadline과 지연 WRITE 수를 노출했다. 임의 삭제 비용과 갱신 빈도를 실측한 뒤 자료구조 변경을 판단한다. |
| A18 | 부분 | 단일 READ의 프레임 예산과 CIO 타이머 수행을 통합 테스트로 검증했다. 느린 핸들러와 방송이 겹치는 상황의 샤드별 지연은 별도다. |
| A19 | 부분 | 샤드별 연결 등록·admission 수를 볼 수 있다. 실환경의 로그인 폭주, accept owner CPU 및 할당 분포 측정이 남았다. |
| A20 | 부분 | raw owner의 미사용 framed 풀 생성을 제거했다. 단기 연결 퇴역 후 lease 반환을 검증했다. 게임 세션의 retained heap·장기 RSS 추적은 남았다. |
| A21 | 부분 | 한 연결이 대형 SEND 풀을 채우고 읽지 않을 때 같은 샤드의 다른 연결에서 소형 응답이 진행되는 것을 확인했다. 운영 부하에서 공용 풀 고갈과 복구율 측정이 남았다. |
| A22 | 부분 | 샤드별 연결, 승인 큐, 예약 deadline, stalled WRITE, 0바이트 WRITE·타임아웃, 콜백 오류를 진단한다. syscall·복사·CAS 비용과 계측 오버헤드는 별도의 프로파일링이 필요하다. |
| A23 | 부분 | raw와 동일 길이 헤더를 쓰는 plaintext framed 에코를 분리했다. 기존 CIO + CUserSocket + 암호화 경로는 기능 테스트했다. 게임용 Netty 어댑터 및 동등 성능 비교가 남았다. |
| A24 | 부분 | 소유 스레드 수, 연결 수, payload, in-flight window 옵션을 추가했다. RKern loopback raw 1,024개와 5,000개 연결을 짧게 실행했다. 실제 게임 5,000명과 혼합 부하는 별도다. |
| A25 | 부분 | p99.9, 최대 지연, 측정 구간 서버 CPU/요청을 CSV에 기록한다. 안정된 장시간 측정, open-loop, JFR·GC 추적은 별도다. |
| A26 | 부분 | 각 부하가 끝나고 서버가 열려 있는 같은 시점에 direct·풀·RSS를 수집한다. 부하 진행 중 peak와 idle/RSS 기준 비교는 남았다. |
| A27 | 부분 | 프레임 길이 형식과 동일한 TCP 조건으로 Linux 백엔드를 비교할 수 있다. 전용 장비 반복 실행과 Windows 성능 비교가 남았다. |
| A28 | 반영 | 생산 전송 소스 변경 시 Linux 스모크와 Windows WEPoll 회귀를 실행한다. Linux는 CSV·환경·실패 내역을 보존한다. |
| A29 | 부분 | 실제 게임 경로는 `Server.startLoginServer → CIOThreadCallback → Selector Raw I/O → CUserSocket`이다. 독립 Selector framed 경로는 별도 프레임·풀 검증에 쓴다. 공통 계약과 장기 코드 정리는 별도다. |
| A30 | 대기 | 기본값에 추가 핫패스 최적화를 적용하려면 호스트 프로파일과 실제 게임 A/B가 필요하다. |

## 재현과 검증 범위

- Linux Java 21에서 독립 전송 테스트와 게임 리스너 통합 테스트를 실행했다. 게임 리스너는 SEND 후보를 끈 경우와 켠 경우 각각 암호화 FIFO 및 종료 후 lease 반환을 검증했다.
- 5,000 연결 결과는 raw loopback에서 2 owner·64바이트·window 1, 준비 300 ms·측정 700 ms인 동작 확인이다. 모든 연결의 TCP 수립을 기다린 뒤 10,712개의 에코 응답을 확인했고 종료 시 버퍼 대여가 모두 해제됐다. 실제 게임 5,000명 처리량과 장기 안정성은 운영 검증 항목이다.
- Linux framed 스모크는 RKern, Netty NIO·epoll·io_uring 모두 응답 검증을 통과했다. Windows WEPoll도 SEND 후보 끔·켬 모두 통과했다. [Linux framed 스모크](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44649244089) · [Windows WEPoll 회귀](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/44649244245) · [기존 비교 결과](../jproring_linux_benchmark_20261120/README.md)

```bash
bench=rkern/docs/jproring_linux_benchmark_20261120/run.py
python3 "$bench" --framed --owners 2 --workload persistent --clients 16 \
  --window 8 --payload-bytes 256 --trials 1 --warmup-ms 200 --measure-ms 350 \
  --output /tmp/rkern-framed-smoke
python3 "$bench" --backend rkern --owners 2 --workload persistent --clients 5000 \
  --trials 1 --warmup-ms 300 --measure-ms 700 --output /tmp/rkern-5000-smoke
```

`server_cpu_ns_per_op`는 `/proc`의 서버 user+system CPU tick을 측정 구간 양 끝에서 샘플링한 값이다. 짧은 구간에는 tick 해상도와 스케줄링 오차가 있다. `echo_or_netty_pool_bytes=-1`은 RKern 독립 framed 풀의 live 값이 해당 지표에서 제공되지 않는다는 뜻이다.
