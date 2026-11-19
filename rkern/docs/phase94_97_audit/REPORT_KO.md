# RKern 방송 94~97차 통합 검수 (2026-11-17)

기준: `RKern_NC_NativeBroadcast_Phase93_Comprehensive_FailureAudit_FULL_SRC_20261117.zip`

## 결론 — 완료와 미완료를 구분

- **94차 통합 Java21 컴파일: 미완료.** 1,710개 production Java 전부 `javac --enable-preview --release 21`에 투입했으나 배포 ZIP에 `HikariCP`, `Javolution`, `JCTools`, `Agrona`, `Trove3`, `fastutil`, `Caffeine` 등 필요한 외부 JAR가 없어 타입 검사 전에 다수 오류 발생. 실제 전체 의존성 빌드 성공으로 주장하지 않음. 구문 전수 파싱은 오류 0.
- **95차 통합 경계 검수: 범위 내 완료.** Native source retain/release, strict 송신, optional 실패 격리, 다중 프레임 반납을 검수. 실제 재현한 두 경로를 수정. 전체 외부 의존성/Windows 통합 실행은 별도.
- **96차 패킷 호환성 모의시험: 범위 내 완료.** 기존 87~93차 테스트, 88~89차 패킷 바이트 비교, AOI·FIFO·클랜·전투·미니게임 예외 주입을 재실행. 테스트에서 실패 없음. 실게임 전후 패킷 캡처 비교는 미완료.
- **97차 Windows IOCP·실동접 5,000명: 미완료.** 본 검증 환경은 Linux x86-64/JDK21이며 ZIP의 `CTransport.dll`은 Windows x64 PE DLL이다. Windows 프로세스·게임 클라이언트·5,000명 실동접 생성기/운영 DB가 없으므로 실서버 부하 및 장시간 soak 시험을 실행했다고 주장할 수 없다. 기존 **5,000명 모의 송신**은 실제 접속 테스트가 아니다.

## 이번에 추가로 발견·수정한 결함

1. `src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java`의 `sendWrittenMapTeamFrames`는 여러 Native 원본을 순서대로 닫을 때 첫 원본의 `close()`가 실패하면 남은 원본의 반환을 건너뛰었다. 반복 송신 중 이미 큐로 인계한 소스가 있을 경우 Native 보유가 지속될 수 있다. 모든 원본에 대한 반환을 시도하고, 첫 예외를 기본으로 유지하며 후속 반환 실패는 suppressed 예외로 남기도록 수정했다. 기존 패킷 내용, 참가자 순회, 팀 필터, 수신자별 FIFO를 변경하지 않았다.
2. `src/l1j/server/MJTemplate/L1Instance/MJL1LiftGateInstance.java`의 `closeSharedAttrs`에서도 같은 결함이 있었다. 여러 타일 속성 공통 패킷의 Native 원본을 모두 닫도록 수정했고, 송신/프레임 구성 예외가 이미 발생한 경우 반납 예외로 원래 실패를 덮어쓰지 않도록 했다. 타일 속성 → 액션 → 오브젝트 삭제의 기존 순서 및 월드 상태 변경은 그대로 뒀다.

## 이전 소스와 새 소스의 실제 대조

- 실제 production `CSharedBroadcast`와 typed stub 소켓으로 93차 원본을 시험했을 때, 최초 Native 원본 `release()` 예외 뒤 두 번째 원본 미반납이 재현됨: `AssertionError: second Native frame was not released after first failure`.
- 수정본은 **두 프레임 모두 반환 시도, 원래 예외 전파, 두 번째 실패의 suppressed 기록, FIFO** 검사를 통과.
- LiftGate의 production `closeSharedAttrs` 메서드를 그대로 추출해 컴파일했고 세 프레임 연속 해제 시 첫/세 번째 실패에서도 중간 프레임을 빠뜨리지 않으며 최초 실패 보존을 확인함.

## 테스트 실행 내역

- `bash docs/phase87_audit/run_tests.sh`: Java 1,710개 구문 파싱 0건, 5,000명 **모의** 방송, 8개 경계·동시성, 5회 무작위, AOI/중복/자기 자신/원본 반환: 통과.
- `bash docs/phase88_audit/tests/run_wire_test.sh`: S_SkillSound, S_EffectLocation, Pledge 바이트 일치 40,000: 통과.
- `bash docs/phase89_audit/tests/run_wire.sh`: ProtoWriter 40,000 + S_EVENT 50,000: 통과.
- `bash docs/phase89_audit/tests/run_message_test.sh`: 서버/시스템 메시지 40,000: 통과.
- `docs/phase90_audit/tests/DoubleGroupFanout90.java`: 팀별 원본·중복 FIFO·참조수: 통과.
- `python3 docs/phase91_audit/tests/run_failures.py`, `run_combat.py`, `python3 docs/phase92_audit/tests/test_game_notice_failure.py`, `python3 docs/phase93_audit/tests/test_orim_failure.py`, `test_lfc_finish_failure.py`, `test_source_boundaries.py`: 모두 통과.
- `WorldOptionalRegression93.java`(actual production core + typed stub): 통과. 이 시험은 의도적으로 로그에 예외를 출력함.
- `bash docs/phase94_97_audit/tests/run_map_frame.sh`: 이번 수정의 실제 production `CSharedBroadcast` 프레임 반환/FIFO: 통과.
- `python3 docs/phase94_97_audit/tests/test_gate_release.py`: 실제 LiftGate helper 추출·컴파일·실패 주입: 통과.

전체 타입 컴파일 재시도 시 정상 배포 환경의 외부 의존 JAR가 필요하다. 일치하지 않는 라이브러리 버전을 임의로 ZIP에 혼합하거나 production API를 임시 stub으로 교체하지 않았다. 운영 채택 전에는 **Windows Java 21 + 실제 네이티브 DLL + 일치하는 전체 JAR + DB + 클라이언트 부하**에서 전체 컴파일, IOCP 접속·종료, 1,000→3,000→5,000명 동접, 전투·AOI 패킷 손실·p99 지연·RSS/Native slab 참조수·장시간 soak를 검증해야 한다.

## 코드 변경 및 재실행

변경된 production Java 정확히 두 개:
- `src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java`
- `src/l1j/server/MJTemplate/L1Instance/MJL1LiftGateInstance.java`

새 테스트/이 보고서는 `docs/phase94_97_audit/` 하위에 있다.
전체 누적 회귀 재실행: `bash docs/phase94_97_audit/run_tests.sh`.
