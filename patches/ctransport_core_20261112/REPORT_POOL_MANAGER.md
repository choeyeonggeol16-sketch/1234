# 풀·회수 진단과 매니저 메모리 제한

2026-11-13. 마지막 배포 `1f5ff36`에서 요청한 풀 회수 관측과 매니저 창 메모리 증가 경로를 수정했다. 독립 PSS와 CIOPacketCodec.dll, 게임 규칙·SQL은 변경하지 않았다.

## 매니저 사용

**보고서 → 풀·회수 진단**을 열면 2초마다 background에서 갱신한다. 창 하나당 조회는 한 건만 실행하고, 닫으면 갱신 타이머를 중지한다. 같은 진단창을 반복 생성하지 않는다.

- Java RX4K/RX64K/TX16K/TX64K: 사용 슬롯/용량, 관측 최고, 누적 대여/최종 반납, 확보 direct 메모리, 획득 실패.
- Native 8K/16K: 사용 블록/용량, 관측 최고, 누적 대여/최종 반납, 확보 slab 바이트, 실패·무결성 오류.
- Java 보유 소켓·cleanup 대기, native active/retiring, 공유 입력 생산자와 대기 송신 요청, close 시작/terminal.
- JVM heap 사용/확보/최대와 JVM direct 전체 수치. 프로세스 메모리 및 native slab과 범위가 다르다.
- 최근 관측은 최대60개. 메모리·파일에 무한 로그를 만들지 않는다.

확보된 풀 메모리는 반환 후에도 재사용을 위해 남는 것이 정상이다. 사용중 버퍼가0이면 **현재 미반납 없음**, 유효 보유자가 있으면 **보유 중**으로 표시한다. 관측된 보유 지표가 모두0인데 같은 잔여량이60초 이상 유지될 때만 **미회수 의심**으로 표시한다. 이는 외부 native 보유자까지 증명하는 확정 누수 판정이 아니며 자동으로 버퍼를 해제하거나 소켓을 끊지 않는다. 동시 대여·반납 순간에는 Java 카운터 차이가 잠시 보일 수 있다. 최고값은 조회 때 관측한 최고이며 정확한 순간 최고값을 주장하지 않는다.

Native는 기존 ring의 read/write 위치를 gate 안에서 읽어 대여·최종반납을 산출한다. I/O hot path에 새 native 통계 atomic을 넣지 않았다. 공유 참조가 남아 있으면 최종 반환으로 세지 않는다. Java는 성공한 lease 대여와 실제 슬롯 반환을 각각 집계한다.

`L1Interop_GetDiagnostics`의 기존8개 결과는 유지하고, count18 요청에만 marker와 pool extension을 제공한다. marker가 없으면 상세 측정을0으로 오인하지 않는다. ABI10/67 exports는 동일하다. **배포에는 새 CTransport.dll과 전체 src를 함께 사용**한다. DLL SHA pin이 갱신되므로 구 버전 DLL/Jar와 혼용하지 않는다.

## 화면 메모리 경로 수정

기존 chocco.appendLog는 호출마다 invokeLater를 만들고 실행 시점에만 줄 수를 제한했다. 실제 옛 메서드를 추출해 EDT를 정지한 상태에서12,000건을 보냈을 때12,000개 메시지가 유지되고 GC 후 힙54,881,848 bytes가 남는 것을 재현했다. 이는 느린/정지된 EDT의 대기 누적 재현이며, 모든 Windows 드라이버의 창 크기 변경 현상 원인을 증명한 것은 아니다.

화면 전용 로그를 ManagerLogBuffer로 합쳤다. 최대1024건/256Ki 글자, 한 메시지2048글자, 문서64Ki 글자/300줄로 제한하고 한 번의 예약에서 묶어 갱신한다. 압박 시 오래된 화면 표시를 생략하며 새 내용을 유지한다. **FileLogger의 파일 감사 기록 큐·저장·flush 계약은 변경하지 않았다.** 기본 로그 창의 caret 자동 갱신을 줄이고, 상태 갱신도 최신값 하나만 대기하도록 합쳤다. 저장된 비정상적인 초대형 창 크기는 현재 화면 범위로 제한한다. dispose 시 모니터와 보조 창을 정리한다.

수정 후 같은 정지 EDT 입력에서 대기127건/260,223글자로 제한되고11,873건의 오래된 화면 표시를 생략했다. 문서 크기 제한과800회 headless resize/paint 검사를 통과했다. CPU/디스크 기록의 무제한 보장을 의미하지 않는다.

## 실제 Windows 검증

실행 **43412834686**, 테스트 source **cf696369047f30d0eff8b67d42a2d642369a7f2d**, Windows Server2022 / Temurin21. 실제 production Swing manager를 사용하고 게임·DB 바인딩만 fixture로 분리했다. 생성자 테스트 모드에서는 서버/DB를 시작하지 않는다.

실제 창 이동·크기 변경400회, 정지 EDT 로그/상태12,000건, 진단창8회 열기/닫기를 검사했다. 같은 크기 변경 두 구간 뒤 결과:

| 측정 | 구간1 | 구간2 |
|---|---:|---:|
| GC 후 Java heap bytes | 8,547,800 | 6,988,816 |
| JVM 확보 heap bytes | 28,311,552 | 28,311,552 |
| Windows private bytes | 140,062,720 | 135,938,048 |
| GDI / USER 자원 | 99 / 23 | 99 / 23 |

Working set은98,291,712→107,597,824 bytes였다. working set 하나의 증가를 누수로 판정하지 않는다. GC는 검사에서 비교 조건을 맞출 때만 호출하며 운영 UI에는 강제 GC를 추가하지 않았다. Linux 실행 환경은 headless JDK여서 실제 JFrame 검사는 Windows에서 수행했다.

새 DLL/FFM에서는 소켓 종료 뒤 공유 입력 source 하나가 유효 보유자로 남는 상태를 확인하고, source 최종반납 후 native 블록과 Java lease가 모두 회수되는 것을 확인했다. 기존8개 진단 prefix도 검사했다. 기존 송수신·암호화·graceful close·세션/레지스트리·독립 PSS 회귀 검사, 확장 명령 진단과 pool 분류 검사도 통과했다. native pool snapshot의 ASan/UBSan 및 TSan 동시성 검사도 통과했다. 검증 중 발견한 broadcast fixture의 accept 순서 가정은 접속별 준비를 기다리도록 수정했다.

전체 production Java1711개 컴파일, 이전 src의 다른1702개 파일 동일성, native I/O 구조 변경 범위를 검사했다. 현재 native 빌드는 기존에 준비된 MinGW GCC14 static toolchain을 사용하며 실제 Windows에서 검사했다. 이전 Zig 빌드와 같은 속도라는 주장은 하지 않는다. 새 DLL SHA는 `WINDOWS_VERIFICATION.json` 및 `native-build.json`에 고정한다.

원본 함수 대조 자료는 별도 `reports/ctransport_full_network_census_20261113/REPORT_KO.md`에 있다. 식별 함수 목록/대응 자료를 만들었지만 모든 미명명 함수·예외 분기의 전수 검수가 끝났다는 의미는 아니다. 실제 게임 클라이언트·운영 DB·장시간 실게임 부하 검사는 이번 자동 검사 범위 밖이다.
