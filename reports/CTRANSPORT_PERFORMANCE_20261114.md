# 네트워크 진단 집계 경합 개선·전후 실측 — 2026-11-14

[Windows 실행43572259260 — 통합 성공](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43572259260), 시험 소스 `bfd8e2236076cb8aeaaf01770f545723ca1318fd`.

## 실제 수정

운영 소스는 CNetworkDiagnostics.java 한 파일을 수정했습니다. 기본 활성화된 패킷 수·처리시간 누계·수신/송신 접수 바이트를 AtomicLong/AtomicLongArray 공용 갱신에서 LongAdder 분산 집계로 변경했습니다. 여러 worker가 같은 카운터 cache line을 갱신하는 경합을 줄입니다. 최대시간 CAS, 연결별128개 기록, Work 중첩/epoch reset, 독립 PSS, 입장 제한·수명·FIFO·암호화·DB 로직과 native DLL은 그대로입니다.

대상은 관측용 통계입니다. 진행 중 보고서는 여러 필드를 동시에 확정하는 원자 snapshot이 아니며, 쓰기 작업이 끝나면 합계는 정확합니다. 입장/수명/큐 한도 판정에는 LongAdder를 사용하지 않습니다. 통계 객체와 stripe 저장 공간, 조회 때 sum 계산 비용이 조금 늘어나는 교환 관계가 있습니다.

## 동일 Windows 호스트 ABBA 비교

변경 전A → 변경 후A → 변경 후B → 변경 전B 순서입니다. 같은 JDK·DLL·송신 FIFO·암호화·바이트 검증을 사용하며 각 실행은 별도 JVM에서 warmup 후 측정했습니다. 4 CPU 머신이고 클라이언트/검증기도 CPU를 공유합니다. 횟수2회/버전이므로 운영 전체의 성능 보장으로 확대하지 않습니다.

| 진단 hot-path 시험 | 변경 전A/B 이벤트/초 | 변경 후A/B 이벤트/초 | 전A/B p99(ms) | 후A/B p99(ms) |
|---|---:|---:|---:|---:|
| 1 workers | 6,480,457 / 7,075,131 | 6,617,055 / 7,245,785 | 0.011000 / 0.010099 | 0.010800 / 0.009900 |
| 4 workers | 7,250,864 / 18,023,501 | 24,548,696 / 24,499,646 | 0.039100 / 0.015801 | 0.012100 / 0.011001 |
| 8 workers | 7,342,899 / 17,601,411 | 23,625,791 / 24,249,480 | 0.038900 / 0.016400 | 0.012500 / 0.012201 |

위 p99는64회 begin/admitted/end 호출 묶음이며 네트워크 RTT가 아닙니다. 모든 버전에서 최종 count/completed/traffic의 정확한 합계를 검사했습니다. 변경 전A/B의 편차가 커서 최상의 baseline과 가장 느린 candidate를 비교해도,4 worker의 처리량은약35.9% 증가·p99는약23.4% 감소·CPU/이벤트는약23.3% 감소했습니다.8 worker도 처리량약34.2% 증가·p99약23.8% 감소였습니다.1 worker에는 일관된 큰 이득이 없습니다.

| 실제 송신 시험: 버전별6개 구간 | 변경 전 | 변경 후 |
|---|---:|---:|
| 전체 프레임/초(구간 합산) | 148,983 | 180,935 |
| 회차별 p99의 산술평균 | 0.269267ms | 0.229250ms |
| 프로세스 CPU ns/프레임 | 16,659.5 | 16,748.9 |

송신 처리량은합산 기준 21.45% 증가했고, 회차별 p99 평균은 14.86% 줄었습니다. 각 구간262,144프레임, 버전별1,572,864프레임을 검사했습니다. 서로 다른 구간의 p99를 평균한 수치이며 모든 샘플을 합친 pooled p99가 아닙니다. 일부 개별 구간은 개선되지 않았으므로 모든 상황의 지연 감소를 보장하지 않습니다.

송신은4 peers·연결별 window4·15B 프레임이며 실제 CUserSocket/버퍼 풀/FIFO/코덱/FFM/DLL을 통과한 바이트와 순서를 전부 비교합니다. 게임/계정/스케줄러는 fixture입니다. CPU 수치는 서버+클라이언트+검증기 전체를 포함합니다. 전체 CPU/프레임은약0.54% 증가해 의미 있는 절감이 확인되지 않았고 메모리 절감도 확인하지 못했습니다. 마이크로벤치의 CPU 절감을 서버 전체 운영비 절감으로 환산하지 않습니다.

## 회귀·안정성 및 남은 불확실성

기존 Windows selftest/FFM/session/shared-send/SafeSend/참조 검사/PSS/매니저 검사, 대량 송수신, JVM 분리 비교와180초 재접속 시험을 통과했습니다. 재접속1,444회에서 Java 소켓5,776개가 모두 GC 회수됐고, 최종 native 대여8K/16K·SEND pending·verifier active는0입니다. native live 객체2개/15,776bytes는 probe server 기준값입니다. pool/reference/owner/timer/ORT 오류는0입니다.

최초 실행43572005936에서는 최적화 클래스가 컴파일/로드되기 전의 기존 session stress 단계에서15초 timeout이1회 발생했습니다. 실패 로그를 보존했고, 해당 연결의 frames/open/native state/registry/worker/pool/SEND 상태를 출력하도록 시험을 보강했습니다. 재실행43572259260은 해당 시험부터 전체를 통과했지만 최초 timeout의 원인은 확정하지 못했습니다. 제한 시간을 늘리거나 실패를 성공으로 처리하지 않았습니다. 간헐적인 최대 지연 전체의 원인도 이번 변경으로 해결했다고 주장하지 않습니다.

Linux 전체1,711 Java 컴파일과 진단 정확도/중첩/초기화/확장 명령/비활성 모드 계약 검사를 통과했습니다. native C++/DLL이 동일하므로 기존 ASan/UBSan/TSan 증거를 유지하며 이번 Java 변경에 대한 native sanitizer 재검사로 표기하지 않습니다.

## 적용·재현

전체 src를 교체하고 clean build합니다. CTransport.dll SHA256은62604a5b1b111526da30b0d06f224eb6d5f3e4aafca0292571aae7932b3f272e로 동일합니다. verify_performance_scope.py가 직전c23379c 대비 운영 소스1712개 중 이 한 파일만 변경됐고 나머지1711개와 native/PSS가 보존됐는지 확인합니다.

Windows에서 run_windows.ps1 → run_stability.ps1 → run_extended_stability.ps1 → run_performance.ps1 순으로 재실행합니다. 기존 진단 소스 사본은 research/performance-20261114/CNetworkDiagnostics.java이며 baseline hash와 대조합니다. 원시 JSON·JFR·로그는 아래에 있습니다.

[원시 측정](https://github.com/choeyeonggeol16-sketch/1234/tree/ctransport-core-20261112/patches/ctransport_core_20261112/research/performance-20261114/run-43572259260) · [성공 실행 로그](https://github.com/choeyeonggeol16-sketch/1234/blob/ctransport-core-20261112/patches/ctransport_core_20261112/logs/performance-windows-actual.log) · [최초 timeout 로그](https://github.com/choeyeonggeol16-sketch/1234/blob/ctransport-core-20261112/patches/ctransport_core_20261112/logs/performance-first-session-timeout.log)
