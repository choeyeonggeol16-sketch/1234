# RKern Native Broadcast 91차 — 전 서버 방송 경로 실패 격리 재감사

기준: `RKern_NC_NativeBroadcast_Phase90_RemainingBroadcast_Fixes_FULL_SRC_20261116.zip`.

## 수정된 생산 Java (3개 파일만)

1. `src/l1j/server/IndunSystem/Gludio/GludioInstanceDungeon.java` — 인던 종료 `finish()`가 `dissolve(r)`로 종료를 확정한 뒤, 공동 알림의 Native 송신 또는 인코딩 예외가 발생하면 일부/모든 멤버의 텔레포트가 누락되고 `scheduleCleanup`까지 실행되지 않을 수 있었다. 종료 공지, 종료 상태 프레임 생성·송신·반납, 참가자별 퇴장을 예외 경계로 분리했다. 정상시 각 참가자는 **종료 상태 패킷 → 그 참가자 텔레포트** 순서를 그대로 유지한다. 실패시 가능한 모든 참가자 퇴장을 시도하고 정리 예약을 반드시 실행한다. 새로운 워커·큐·프로토콜은 추가하지 않는다.
2. `src/l1j/server/server/model/network/core/cs/packet/CS_LeavePledge.java` — 혈맹 DB COMMIT 뒤 공통 알림 Native 송신이 예외를 발생시키면 해당 온라인 혈맹원의 `ClearPlayerClanData`를 건너뛸 수 있었다. 알림 실패와 멤버 상태 정리를 분리하여, 성공 경로의 **알림 → 그 멤버 상태 정리** 순서는 유지하되 알림 실패가 이미 커밋된 탈퇴/해산 상태의 메모리 정리를 막지 못하게 했다.
3. `src/l1j/server/MJCombatSystem/MJCombatObserver.java` — 전투장 종료 카운트다운의 Native 송신 예외가 참가자 퇴장 `scheduleRequired` 호출을 막을 수 있었다. 이 알림의 예외를 격리했다. 경기 결과 `on_closing()`에서는 승리/패배/공통 결과 Native 원본 생성이나 각 참가자에게 보내는 결과 방송이 예외를 발생시키면 보상(`do_rewards`)이 중단될 수 있었다. 결과 방송 실패를 보상 처리와 분리했다. 정상시 **승패별 결과 → 공통 결과 → 보상** 순서는 유지한다. 기존 결과 원본 3개의 공동 소유·참조수/close 경로도 보존한다.

## 실제 재현과 수정 검증

- Phase90 **원본** 메서드 추출/컴파일 실패 주입: 글루디오 `notice()` 예외에서 `finish()`가 중단되어 후속 처리 누락이 재현됨(테스트 비정상 종료). 혈맹 공통 알림 예외에서 해당 멤버 `ClearPlayerClanData()` 누락이 재현됨.
- Phase91 **수정본** 메서드 추출/typed stub Java 21 컴파일·실행: 글루디오 7개 경우(정상, 공지, 원본 확보, 원본 인코딩, 개별 송신, 개별 텔레포트, 참조원본 반납 실패) 모두 이후 참가자의 퇴장·정리 예약 검증 PASS. 혈맹 정상/송신 실패 2개 경우 DB COMMIT 선행·각 온라인 멤버 상태 정리 PASS.
- Phase91 **전투장** 메서드 추출/typed stub Java 21 컴파일·실행: 정상 결과 패킷→보상 FIFO, 개별 송신 실패, 최초 원본 생성 실패, 반납 예외, 종료 카운트다운 송신 실패 5개 경우 PASS. 실패 발생 시에도 보상·퇴장 예약 검증.
- 87~90차 누적 실제 production 방송 구현 + 모의 world/socket 테스트 PASS. 5,000 synthetic recipients × 2 프레임, AOI/중복/송신순서/수명, 88차 40,000 패킷 바이트 비교, 89차 Proto 40,000 + S_EVENT 50,000 + SC 메시지 40,000, 90차 양 팀 원본 공유 PASS.
- production 소스 Java 1,710개 **구문 검사** PASS, parser errors 0.
- 최신 90차와의 변경 파일 정확히 위 3개; 기존 Native C++/DLL, CIO/IOCP transport, SEND 버퍼 풀, 패킷 writer, 게임 전투 수치/DB 로직은 byte-identical.

## 여전히 검증되지 않은 항목

- 외부 JAR를 포함한 서버 전체 Java 타입 컴파일. 현재 컨테이너의 dependency classpath에는 `javolution`, `gnu.trove`, `org.jctools`, `org.agrona`, `fastutil`, `HikariCP`, JAXB 등이 없어서 `javac` 전체 소스의 의존성 해석이 실패했다. 이 실패를 프로덕션 코드 오류라 단정하거나 전체 타입 컴파일 PASS라고 주장하지 않는다.
- 실제 MySQL, Windows IOCP에서의 Native CTransport.dll 로드/송수신, 5,000명 실제 동접, 게임 내 인던·전투장 통합실행. 현 테스트는 정적·모의·추출 메서드 기반이며 모든 서버 버그의 부재를 증명하지 않는다.
- 모든 이벤트에서의 SEND 실패 처리나 모든 구형 방송 이름 제거까지 보장한 것은 아니다. 이번 수정 범위는 **검수에서 재현된 중대한 종료/상태 정리·보상 손실 경계**다.

## 재실행

`bash docs/phase91_audit/run_tests.sh` (JDK21 필요, FFM preview 지원). 새 테스트 2종은 현재 생산 소스의 해당 메서드를 그대로 추출해 별도 typed stubs로 빌드하며, 성공·실패 주입을 반복한다. 실 IOCP 테스트는 아니다.
