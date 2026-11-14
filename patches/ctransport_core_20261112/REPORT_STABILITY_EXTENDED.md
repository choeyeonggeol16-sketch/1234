# 추가 네트워크 검증 — 2026-11-14

직전 배포60a85fa의 운영 소스1,712개와 DLL을 그대로 사용해 추가 검증했습니다. 클라이언트 JVM 분리 비교, 실제 Java CUserSocket 경로의 긴 핸들러 주입,180초 재접속·송신·종료·메모리 회수를 실행했습니다. 실제 결과는 아래에 기록합니다.

## 최종 결과

[실제 Windows 통합 실행43569842086 — 성공](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43569842086), 시험 소스 `5fdbc659ad60b6bca625bbd46bd6cf7f0a2aec28`.

새 코어 결함은 재현되지 않아 운영 코드 변경은 하지 않았습니다. 테스트·보고서·원시 결과를 보강했습니다. 직전 전체 src와 CTransport.dll/PSS를 그대로 사용합니다.

| 동일 프로토콜 비교 | 프레임 수 | 부하 시간(초) | 프레임/초 | p99(ms) | 최대(ms) |
|---|---:|---:|---:|---:|---:|
| warmup | 1,517,888 | 2.003 | 757,835 | 5.329 | 49.633 |
| same-jvm | 8,189,248 | 8.007 | 1,022,812 | 8.720 | 146.704 |
| split-jvm | 7,029,696 | 8.010 | 877,631 | 8.477 | 388.233 |
| split-jvm-repeat | 7,827,648 | 8.010 | 977,188 | 6.672 | 204.314 |

JVM 분리 후에도 최대 지연388.233/204.314ms가 남았습니다. p99는8.477/6.672ms였지만 처리량은같은 JVM보다 낮았습니다. 단순 JVM 분리로 최대 지연이 해결됐다고 볼 수 없으며 원인을 코어로 확정할 수도 없습니다. 서버 함수의 개별 exclusive native CPU 프로파일이나 별도 물리 부하 머신은 이 시험에 포함하지 않았습니다. CPU 값은 동일 JVM일 때 서버+클라이언트, 분리 JVM일 때 부모 서버 프로세스만 포함하므로 동일 범위처럼 비교하지 않습니다.

| 긴 핸들러 주입(실제 Java CUserSocket, IOCP worker2개) | 다른 연결의 처리 | 관측 시간 |
|---|---|---:|
| worker 1개 대기 | 해제 전 처리됨 | 0.595ms |
| worker 2개 대기 | 250ms 대기 동안 처리 안 됨;해제 후 회복 | 251.978ms |

native 현재 콜백 시작값도 각각1/2개 활성으로 확인했습니다. worker 하나가 남으면 다른 연결을 처리했고, 둘을 모두 막으면 다른 연결도 기다렸습니다. 따라서 실제로 긴 핸들러가 확인되면 해당 경로를 선별해 분리하는 것이 우선입니다. 이번 주입 시험만으로 기존 게임 핸들러에 장애가 있다고 판단하지 않았습니다.

| 180초 반복 검사 | 결과 |
|---|---:|
| 실제 반복 시간 | 180.075초 |
| 연결4개 생성·송신·종료 반복 | 1,469회 |
| 전체 바이트 비교한 송신 프레임 | 1,504,256개 |
| 닫힌 Java CUserSocket GC 회수 | 5,876/5,876개 |
| native8K/16K 대여·pending SEND·verifier active | 최종 모두0 |
| native 객체 할당 / 반환 / live | 520,958 / 520,956 / 2 |
| native live bytes baseline / final | 15,776 / 15,776 |
| 메모리 관측146회: direct used | 매번8,308 bytes |
| 메모리 관측146회: object cache | 매번411,152 bytes |
| pool corruption/reference/owner/timer/ORT 오류 | 모두0 |

남은 native 객체2개는 유지 중인 probe server의 기준 객체입니다. 관측146회 모두 대여0, native live2, weak user 잔여0이었습니다. private bytes는약252~332MiB에서 변동했습니다. 최종 Java heap은약3.15MiB입니다. 작은 힙 증가는 시험 구간·weak reference·메모리 관측 기록을 시험 종료까지 보관하는 것도 포함합니다. 이번180초 구간에서 닫힌 소켓이나 대여 버퍼가 누적되는 징후는 없었습니다.

새 JVM 비교/워밍업 24,564,480프레임과 반복 송신 1,504,256프레임, 합계 26,068,736프레임을 추가 처리했습니다. 기존 수신 15,839,040프레임 및 기존 송신 주 시험 3,280,896프레임도 같은 실행에서 다시 통과했습니다.

[원시 JSON·JFR·실행 로그](https://github.com/choeyeonggeol16-sketch/1234/tree/ctransport-core-20261112/patches/ctransport_core_20261112/research/stability-extended-20261114/run-43569842086) · [전체 Windows 실행 로그](https://github.com/choeyeonggeol16-sketch/1234/blob/ctransport-core-20261112/patches/ctransport_core_20261112/logs/stability-extended-windows-actual.log)


## 시험 구성과 해석

`CTransportSplitWindowsTest`는 실제 CTransport.dll/FFM/L1 프레이밍·복호화를 통과시킵니다. 동일 JVM과 별도 클라이언트 JVM 모두 같은 프로토콜로64프레임을 전송하고, 서버가 디코더 완료를 확인한 뒤 별도 TCP 제어 연결로 누적 완료 수를 회신합니다. 클라이언트는 회신 수가 제출 수와 같은지 확인합니다. 서버 최종 누적 수와 클라이언트 보고 수의 일치도 확인합니다.

클라이언트 JVM을 분리해 힙·GC를 분리했지만 같은 Windows 머신의 CPU와 loopback 스택은 공유합니다. 별도 물리 머신 간 시험이 아닙니다. 새 지연은 데이터 전송→디코더 완료→제어 TCP 회신까지 포함합니다. 이전 semaphore 확인 방식의 지연과 직접 비교하지 않고 이번 동일 프로토콜의 두 모드를 비교합니다. 성능 경로의 게임 수신 소유자는 fixture이고 실제 게임/DB 작업을 실행하지 않습니다.

`CUserSocketSoakWindowsTest`는 실제 production CUserSocket/버퍼 풀/FIFO/FFM/DLL을 사용합니다. C_ALIVE 패킷으로 테스트 핸들러를 호출하고 worker 하나/둘을 의도적으로 기다리게 해, 다른 연결의 패킷 실행 여부와 해제 후 회복을 확인합니다. 이는 긴 작업이 IOCP worker를 점유했을 때의 구조적 영향 검사이며 실게임 핸들러나 DB가 실제로 막혔다는 증거가 아닙니다.

이어4개 연결을 반복 생성해 매 회1,024개의 송신 프레임 전체 바이트·순서·별도 키 상태를 비교하고 종료합니다. native 대여 수·객체·레지스트리가 기준값으로 복귀하는지 매 회 확인하고,10회마다 GC 및 메모리 스냅샷을 기록합니다. 모든 닫힌 Java CUserSocket은 weak reference로 추적해 최종 GC 회수를 검사합니다. 보고서와 weak reference 목록 자체는 시험 종료까지 보관하므로 그 크기만큼 힙이 증가할 수 있습니다.

이 시험은180초이며 수시간·수일 soak나 전체 게임 서버 부하를 대신하지 않습니다. 부하 구간, 워커 대기 주입, 회수 기준을 명시해 단순 누적 패킷 수와 구분했습니다.

## 소스 확인과 변경 판단

일반 게임 패킷은 `CUserSocket.dispatchNativePacket()`에서 IOCP owner로 실행되고 세션 패킷은 지정 shard로 전달됩니다. 기존 `CNetworkDiagnostics.timings()/workers()`에 opcode별 완료 수·평균/최대 처리시간 및 실행 중 작업이 있으며 native `threadDiagnostics()`에는 worker별 콜백 누적 시간과 현재 시작 시각이 있습니다. 진단 기능을 중복 구현하지 않았습니다.

검토한 캐릭터 목록·편지·거래성 SQL 경로는 이미 `CacheD.submitDbJobOrdered` 또는 트랜잭션 경로를 이용합니다. JDBC 코드가 있다는 이유만으로 IOCP에서 동기 실행된다고 판정하지 않았습니다. 긴 실제 작업이 확인될 때 기존 DB 작업 큐와 세션 회신 경로를 사용해 선택적으로 분리해야 하며, 모든 핸들러를 다른 큐로 옮기는 변경은 이번 증거로 정당화되지 않습니다.

운영 코드 변경 여부와 추가 발견은 최종 수치에 기재했습니다. 기존 FIFO·암호화·객체 수명·독립 PSS·DB 계약을 보존합니다. `tests/verify_extended_stability_scope.py`는 직전 전체 src의 모든 파일 해시와 native 소스·빌드 스크립트·DLL·PSS를 비교합니다.

## 재실행과 증거

Windows x64/Temurin21에서 `tests/run_windows.ps1`, `tests/run_stability.ps1`, `tests/run_extended_stability.ps1` 순으로 실행합니다. 새 결과는 build/stability-split.json, build/stability-soak.json 및 logs/stability-{split,soak}.log입니다. 개별 클라이언트 JVM의 원시 결과도 stability-split-client-*.json으로 보관합니다.

이번 추가 시험은 운영 소스 변경을 전제로 하지 않습니다. 시험이 통과하고 새 결함이 재현되지 않으면 직전 전체 src를 유지하며 테스트·보고서·CORE 묶음을 갱신합니다.
