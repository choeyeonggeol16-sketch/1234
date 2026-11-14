# 네트워크 코어 실제 부하·안정성 검사 — 2026-11-14

송신 확대 검사에서 실제 큐 깨우기 누락을 재현해 수정했습니다. 마지막 통합 실행의 수치는 아래에 기록합니다. 검사 코드는 배포 코드를 호출하며, 시간을 생성하거나 결과를 추정하지 않습니다.

## 최종 실측 결과

[Windows 통합 실행43567498897 — 성공](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43567498897), 시험 소스 `1e57cca6c67a6b20aac227c13841088b018aa839`.

수신 12,757,120프레임, 주 송신/워밍업 3,280,896프레임, 재접속 송신8,192프레임을 처리했습니다. 합계16,046,208프레임이며, 별도 backpressure 검사에서64개를 접수하고4개를 거절했습니다. RX 시험 전체 66.757초, 자바 송신/회수/종료 시험 전체 40.742초입니다. 아래 시간은 각 패킷 부하 구간이며 종료 대기와 컴파일 시간은 제외합니다.

| 검사 | 프레임 수 | 시간(초) | 프레임/초 | p99(ms) | 최대(ms) | 프로세스 CPU |
|---|---:|---:|---:|---:|---:|---:|
| rx-workers1 | 1,427,776 | 3.003 | 475,455 | 3.129 | 196.241 | 34.3% |
| rx-workers2 | 2,441,792 | 3.001 | 813,590 | 2.109 | 13.010 | 61.3% |
| rx-workers8 | 3,344,768 | 3.002 | 1,114,307 | 3.045 | 219.529 | 91.5% |
| rx-clients64 | 3,657,088 | 3.004 | 1,217,430 | 12.294 | 283.210 | 98.2% |
| rx-fragmented8192 | 57,600 | 3.008 | 19,149 | 87.972 | 568.719 | 97.8% |
| rx-workers1-repeat | 1,750,272 | 3.002 | 582,953 | 2.353 | 3.468 | 29.0% |
| rx-checksum512 | 65,536 | 0.286 | 229,506 | 15.404 | 89.941 | 71.1% |
| rx-checksum8192 | 4,096 | 0.185 | 22,171 | 46.235 | 46.235 | 78.2% |
| production-S2C-small-1 | 1,048,576 | 8.719 | 120,266 | 0.320 | 82.969 | 75.6% |
| production-S2C-small-2 | 1,048,576 | 8.406 | 124,739 | 0.275 | 196.639 | 70.3% |
| production-S2C-small-3 | 1,048,576 | 8.134 | 128,918 | 0.273 | 5.711 | 72.3% |
| production-S2C-4k | 131,072 | 2.235 | 58,640 | 0.480 | 129.409 | 85.3% |

수신1 worker는16 clients에서47.5만fps, 요청8/실제4 worker는111.4만fps,64 clients에서는121.7만fps와 프로세스 CPU98.2%였습니다. 부하 생성기와 검증기도 이 CPU를 쓰므로 서버 단독 처리 한계는 아닙니다. 소형 송신 세 번은각104만여 프레임을8.719/8.406/8.134초에 마쳤습니다. 송신 큐 정지·누락·중복·바이트 비교 오류는 없었습니다.

RX 수명/참조 회수 대기는 각5.049~5.083초로 기본5초 검사 지연과 맞았습니다. 실제 자바 송신 종료 경로는 조건 충족 시 검사기를 취소해 더 빨리 기준값에 복귀했습니다.

최종 회수:

| 항목 | 결과 |
|---|---:|
| GC로 회수한 닫힌 Java CUserSocket | 32/32 |
| native 대여 중8K/16K 버퍼 | 0/0 |
| native pending SEND / verifier active | 0/0 |
| native 객체 할당 / 반환 / live | 995,902 / 995,900 / 2 |
| baseline / final native live bytes | 15,776 / 15,776 |
| final pool corruption / reference / owner / timer / ORT 오류 | 모두0 |
| 재접속8회 후 direct bytes | 매번49,207 |
| 재접속8회 후 object cached bytes | 매번411,152 |
| 송신 부하 중 관측 최고 Java heap / 최종 | 128.348 / 4.448 MiB |
| 송신 부하 중 관측 최고 Java direct / 최종 | 0.586 / 0.047 MiB |
| 송신 부하 중 관측 최고 Windows working set / 최종 | 295.109 / 239.754 MiB |
| 송신 부하 중 관측 최고 Windows private bytes / 최종 | 344.996 / 285.285 MiB |

남은 native 객체2개는 시험 종료 직전에도 유지한 probe server의 기준 객체입니다. 풀 예약량과 재사용 cache는 live 대여 수에서 분리했습니다. 재접속 물결의 private bytes는약277~285MiB에서 변동했고, 보고서/시각 구간 기록을 보관하므로 Java heap에도 작은 기록 증가가 있습니다. 닫힌 소켓32개는 모두 GC 회수됐습니다.

JFR active Java execution sample은 RX 11개/TX 62개로 적어 세부 함수 하나를 최대 병목으로 확정하기 어렵습니다. TX에서는 commitDirectFrame/stageOutboundFrameLocked 등 송신 접수 구간이 관측됐고, 할당 표본은 클라이언트 read·참조 코덱 검증 쪽 비중이 컸습니다. 부하 중 최대 GC pause는 RX 4.587ms, TX 3.778ms였습니다. 추가 대규모 구조 변경을 정당화할 증거는 얻지 못했습니다.

[원시 JSON·JFR·로그](https://github.com/choeyeonggeol16-sketch/1234/tree/ctransport-core-20261112/patches/ctransport_core_20261112/research/stability-20261114/run-43567498897) · [전체 Windows 회귀 실행 로그](https://github.com/choeyeonggeol16-sketch/1234/blob/ctransport-core-20261112/patches/ctransport_core_20261112/logs/stability-windows-actual.log)


## 발견한 운영 코드 결함과 수정

수정 전 Windows 실행 43565159923 및 43566252936에서 소형 송신을 1,048,576 프레임으로 늘리자 한 연결이 멈췄습니다. 상세 증거는 `research/stability-20261114/run-43566252936/stability-failure.json`입니다. 다른 세 연결은 각 262,144 프레임을 끝냈고, 정지 연결은 수신 201,664 / 제출 201,668 프레임이었습니다.

정지 시점의 값은 `_socketPending=1`, `_socketDraining=0`, head slot=2(WRITE_COMPLETE), `_writeEvent=481`, native send_pending=0입니다. 송신 완료가 자바 큐에 공개됐지만 처리할 소유자가 없었습니다. 두 개의 자바 송신 lease도 이 진행 정지 때문에 붙잡혀 있었습니다. 이 상태에서 단순 타임아웃 연장이나 풀 확대는 원인 해결이 아닙니다.

`connection/CUserSocket.java`의 소유권 반환을 `SOCKET_DRAINING.setRelease(this,0)`에서 `setVolatile(this,0)`로 변경했습니다. 반환 직후 readiness를 다시 읽는 프로토콜에는 StoreLoad 순서가 필요합니다. release store만으로는 앞 작업에 대한 순서만 보장해, 이전 소유자가 새 작업을 놓치고 게시자는 이전 소유권을 보는 상황을 막지 못합니다. volatile write로 반환과 후속 확인의 순서를 보장합니다. 기존 FIFO·512슬롯·단일 소유자·암호화·종료 수명 계약을 유지합니다.

수정 후 선행 실행 43567167226은 소형 1,048,576프레임을 세 번, 4KB 131,072프레임을 모두 바이트 비교까지 완료했습니다. 마지막 종료 검사는 테스트가 native close-start 알림을 Java CLOSED 상태와 동일시해서 실패했으므로 이 실행 전체를 성공으로 처리하지 않았습니다. 최종 검사는 두 종료 시점을 나눠 검사합니다. 이미 예약된 송신의 commit=true는 SEND에 접수됐다는 뜻이며 상대방 수신 성공을 뜻하지 않습니다.

## 검사 구성과 경과

- Windows Server2022 GitHub runner, Temurin21, 4 CPU. 서버·클라이언트·검증기가 같은 머신/프로세스의 CPU를 공유합니다. JFR을 켠 수치입니다.
- 수신: 실제 CIOServer/CSocket/FFM/CTransport.dll/L1 디코더. 512B, worker 요청1·2·8, 클라이언트16·64, 8192B 분할 수신을 검사합니다. 각 주 성능 구간은 최소3초, 별도 키 상태의 디코더와 비교하는 payload 체크섬 검사도 실행합니다. 4 CPU에서 요청8은 실제4 worker로 제한됩니다.
- 송신: 실제 production CUserSocket, 자바 풀·FIFO·암호화·FFM·DLL. 클라이언트4, 연결별 window4. 소형 1,048,576프레임을 세 번, 4KB 131,072프레임. 모든 프레임의 길이·순서·시각·payload·암호화 바이트를 비교합니다.
- 송신 지연은 producer timestamp부터 클라이언트가 프레임을 읽은 시점까지입니다. 수신 지연은 64프레임 묶음 write부터 마지막 decoder callback 및 클라이언트 깨우기까지여서 서로 직접 비교할 수 없습니다.
- 8회 연결/종료 물결과32개 Java CUserSocket의 weak reference를 검사하고 GC 회수, native 객체·풀·계정 레지스트리의 기준값 복귀를 확인합니다. 종료 검사기가 유지되는 경우의 기본5초 지연을 포함해 기준값 복귀를 확인합니다.
- 느린 수신자는 backpressure 거절을 실제로 발생시키고 RST 후 풀 반환을 확인합니다. 별도 검사에서는4개 encoder를 예약 상태로 잡고 RST/명시적 Java close와 commit/abort를 겹쳐 FIFO와 lease의 최종 회수를 확인합니다.
- 실제 Windows의 기존 native selftest, FFM/session/codec/shared-send/SafeSend/reference verifier, 독립 PSS, 매니저 이동·크기 변경 회귀 검사도 수행합니다. Linux ASan/UBSan 및 TSan native suite도 통과했습니다. Linux suite의 Windows API는 fixture이며 실제 Windows 검사를 대신하지 않습니다.

## 메모리와 병목 해석

메모리는250ms 간격으로 Java heap/direct, Windows working set/private bytes, native live/cached 객체와 풀 대여 수를 기록합니다. 종료 뒤 Java 소켓 GC 회수와 native 대여/활성 객체 수를 별도로 확인합니다. allocator가 보관하는 cache, JVM 예약 메모리, 기록을 보관하는 보고서 객체를 미반환 소켓과 혼동하지 않습니다. 프로세스 RSS가 시작 값과 같아야 한다는 조건은 사용하지 않습니다.

JFR의 부하 시간 구간에 해당하는 Java execution sample과 할당 가중치, GC pause를 집계했습니다. blocked poller의 NativeMethodSample을 코어 CPU 병목으로 세지 않습니다. 아래 표의 CPU는 클라이언트와 검증기까지 포함한 프로세스 전체 CPU를4 CPU로 정규화한 값입니다. native 코드의 함수별 exclusive CPU 시간을 이 측정으로 확정하지 않습니다.

초기 시험 검증기는 매 바이트 비교 전에 오류 문자열을 만들어4KB 검사에서 약10GB의 표본 추정 할당을 발생시켰습니다. 이 테스트 오류를 먼저 바로잡았으며 해당 최초 실행의 성능을 최종 성능으로 인용하지 않습니다. 8192B 분할 수신은1/3/11/137/4093B 패턴으로 실제 socket write를 반복하므로 syscall과 scheduling 비용도 포함합니다.

## 변경 범위와 적용

직전 배포481ba8c의1,712개 src 파일과 비교해 운영 소스 변경은 CUserSocket 한 파일뿐입니다. `tests/verify_stability_scope.py`로 나머지1,711개 소스, native 소스·빌드 스크립트, CTransport.dll, 독립 PSS DLL 보존을 확인합니다. 게임·DB·PSS 로직을 변경하지 않았습니다. ABI10/67 exports와 기존 패킷 코덱을 유지합니다.

전체 src를 교체하고 clean build합니다. 기존 CTransport.dll SHA256은 `62604a5b1b111526da30b0d06f224eb6d5f3e4aafca0292571aae7932b3f272e`로 동일합니다. 테스트·원시 JSON·JFR·실행 로그는 CORE 묶음에도 포함합니다. 재실행은 Windows x64/Temurin21에서 `tests/run_windows.ps1` 후 `tests/run_stability.ps1`입니다.

이 결과는 실제 DLL을 통과한 localhost 네트워크 검사입니다. 게임·계정·스케줄러 의존성은 fixture이고 실제 DB·월드·수천 실사용자의 콘텐츠 부하는 포함하지 않습니다. 장시간 운영 전체의 무누수를 보장하거나 위 fps를 실제 게임 동접으로 환산하지 않습니다. 이 시험 구간에서의 처리·무결성·회수 결과를 기록합니다.
