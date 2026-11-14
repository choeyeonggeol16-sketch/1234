# DLL 수신 재개 경합 수정 및 FFM 실험 검토 — 2026-11-14

속도 실험 중 수신이 멈추는 현상을 발견해 native READ/RESUME 수명 경합을 고쳤습니다. Java의 중복 FFM 확인 제거안은 같은 호스트 비교에서 개선이 확인되지 않아 철회했습니다. 실제 배포 Java 동작은 직전df32496과 같고 DLL 검증 해시만 갱신합니다. 새 DLL과 전체 src를 함께 적용하고 clean build합니다.

## 최종 검증 결과

[최종 배포 조합 실행43580772728 — 성공](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43580772728). 시험 소스5142b456e6bbf7717e3f30858a703669117f286c, 생성된 DLL pin은 해당 실행의 tested artifact에 있습니다.

- 원래 Java 코드 + 수정 DLL로 본 수신 측정 4,601,216프레임/8.001초를 통과했습니다. 두 구간 처리량은 565,721/584,505fps, 묶음64개 p99는 0.8762/0.7841ms입니다. 이 수치는 단독 동작 확인이며 다른 runner의 이전 수치와 속도 향상률을 계산하지 않습니다.
- 같은 DLL SHA256으로 전체 부하 실행43579624676도 통과했습니다. 이 실행은 기존 대량 송수신·JVM 분리·180초 재접속 1,453회/Java 소켓 5,812개 GC 회수·pool/참조/owner/timer/ORT 오류0을 확인했습니다. 그 실행의 Java FFM 후보는 이후 철회했고, 기존 Java baseline의 수신 측정도 같은 DLL로 통과했습니다.
- 최종 실행에서는 원래 Java로 DLL selftest·FFM/session/codec/공유 송신/SafeSend/참조·독립 PSS·매니저 회귀와 위 수신을 다시 검사했습니다. 이미 같은 DLL로 끝낸3분 soak/일반 부하 단계는 최종 확인에서 반복하지 않았습니다.
- native ASan/UBSan·TSan 전체 suite는 각각 PASS입니다. 원본 재현 실패와 수정 후 로그를 함께 보존합니다. 최종1711 Java compile도 확인했습니다.

새 CTransport.dll SHA256: `0801f22fedac95ee29d33c747c5e4019c3b36d79d31615c7d84cc79405258606`. ABI10/67exports·객체 배치/vtable은 동일하고, PSS DLL은 기존35b476815c56b578e5e12a3d500589e72220ec6493bbc0041da541c588b861fa입니다.

[최종 원시 수신/로그](https://github.com/choeyeonggeol16-sketch/1234/tree/ctransport-core-20261112/patches/ctransport_core_20261112/research/dispatch-performance-20261114/run-43580772728) · [같은 DLL 전체 부하](https://github.com/choeyeonggeol16-sketch/1234/tree/ctransport-core-20261112/patches/ctransport_core_20261112/research/dispatch-performance-20261114/run-43579624676)


## 재현한 결함과 수정

foreign thread가 패킷 lease를 돌려줄 때 READ의 readDelivering 또는 RESUME의 userPumpActive가 아직 설정돼 있으면 queueResume가 재개 게시를 보류합니다. 바로 뒤에 기존 drain이 종료하면서 이 요청을 넘겨받지 못하면 codec에 완전한 프레임이 남았는데도 새 WSARecv를 게시할 수 있었습니다. peer가 이미 보낸 묶음의 처리를 기다리면 더 이상 입력이 오지 않아 진행이 멈춥니다.

readLocked는 완전한 codec 입력이 남으면 새 WSARecv를 게시하지 않도록 했습니다. READ/RESUME의 delivery/pump 상태를 내린 뒤 queueResume를 다시 확인해, 보류된 lease 반환의 재개를 이어받습니다. codec feed에는 L1 parser가 완성한 wire frame만 들어갑니다. 부분 프레임에는 기존 수신을 재등록하고, graceful FIN을 기다리는 읽기는 이 입력 검사에서 제외합니다.

클래스 필드·vtable·67 exports·ABI10·프레임/암호화·입장 제한·FIFO·DB·독립 PSS는 유지합니다. 기존 LongAdder 진단 개선도 그대로입니다.

## 재현·회귀 근거

native_engine_test.cpp에서 이미 두 프레임이 들어왔고 첫 프레임 lease를 잡은 상태를 만든 뒤, READ/RESUME 소유권을 놓는 구간의 foreign 반환을 고정된 순서로 발생시켰습니다. 원본 C++는 `buffered frame was stranded behind a new WSARecv`로 실패했습니다. 수정 후에는 READ/RESUME 두 경우 모두 새 입력 없이 두 번째 프레임을 처리합니다.

수정 후 native 전체 ASan/UBSan·TSan suite를 통과했습니다. 이 suite의 Windows API는 fixture이며 실제 Windows kernel 검사를 대신하지 않습니다. 실제 DLL/FFM 검사는 아래 Windows 실행과 원시 로그에 있습니다.

첫 수신 ABBA 실행43578175936은 마지막 원본 Java 비교에서591,669프레임 이후 타임아웃이 나 전체 실패로 기록했습니다. 이 실행을 성능 개선 성공으로 처리하지 않았습니다. 별도로 위 native 경합을 결정적으로 재현해 수정했습니다. 실패 시 Java phase/closed/pause 및 native socket/registry/worker 상태를 보존하도록 시험도 보강했습니다.

## 철회한 속도 실험

같은 호출에서 native owner를 확인한 결과를 직행 실행에 재사용해 FFM downcall 하나를 줄이는 Java 실험을 했습니다. 큐에서 실행되는 작업은 검사하도록 유지했지만, 같은 고친 DLL을 쓴 ABBA 비교에서 개선이 확인되지 않았습니다.

실험552b42d/실행43579624676의 합산 결과는 원본 Java975,353fps → 후보943,195fps(-3.30%), 회차별 p99 평균0.477025ms → 0.497950ms(+4.39%)였습니다. 이 후보를 운영 코드에 넣지 않았습니다. 실험 사본은 research/dispatch-performance-20261114/rejected-candidate/에 남겼습니다. 원본과 후보 모두 같은 수정 DLL이므로 이 수치는 DLL 경합 수정 전후 비교가 아닙니다.

측정은 실제 CUserSocket/native L1 decoder/FFM,4 peers·2 IO workers,512B 프레임·묶음64개입니다. 게임 클라이언트의 rate/filter 진입 observer와 단일 session executor는 fixture입니다. 지연은 write→묶음 마지막 프레임의 rate/filter 진입+클라이언트 깨우기이며 실제 전투/DB 완료나 RTT가 아닙니다. 이전 송신 p99와 직접 비교하지 않습니다. p99는 회차별 백분위의 산술평균이며 pooled percentile이 아닙니다. 실제 게임 부하와 수시간 운영을 검증한 결과로 확대하지 않습니다.

## 적용과 재실행

**새 CTransport.dll과 전체 src를 함께 적용하고 clean build합니다.** Java 게임 로직은 그대로이며 CIOConfig의 DLL SHA256만 갱신합니다. 기존 DLL만 유지하면 이번 native 수정이 적용되지 않습니다. 독립 PSS DLL은 동일합니다.

tests/verify_dispatch_performance_scope.py는 df32496 대비1712개 src 중 DLL pin만 변경됐고, native 소스 수정은 지정한 CTransport.cpp뿐인지 확인합니다. PSS 및 나머지 native 소스는 해시로 보존합니다.

Windows x64/Temurin21: run_windows.ps1 후 run_dispatch_performance.ps1로 최종 배포 코드의 실제 수신을 검사합니다. -CompareExperiment는 철회한 Java 후보를 별도 classpath에 컴파일해 비교하는 옵션이며 운영 코드에 적용하지 않습니다. native 재현은 run_native_tests.py 및 --tsan입니다. Linux full1711 Java 컴파일은 logs/dispatch-performance-final-full-build.log에 있습니다.

이전 보고서에 남긴 기존 session stress 타임아웃1회의 원인을 이번 수정으로 확정했다고 주장하지 않습니다. 이번에 확인·고친 것은 buffered-frame 재개 경합입니다. 큰 폭의 p99/CPU 개선을 보장하는 변경으로 표기하지 않습니다.
