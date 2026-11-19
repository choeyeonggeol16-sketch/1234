# RKern 방송 99차 — 송신·종료·Native 소유권 실패 안전성 (2026-11-17)

## 실제 기준과 범위

- 기준: `RKern_NC_NativeBroadcast_Phase98_Refcount_FailureSafety_FULL_SRC_20261117.zip` (내용 재추출).
- 생산 Java 변경: **2개 파일**, `src/l1j/server/server/model/network/core/connection/CUserSocket.java` 및 `CIOBufferEx.java`.
- 추가: `docs/phase99_audit/` 재현/회귀 테스트 및 보고서, README 상단 최신 안내.
- Native DLL, IOCP/CIO 작업자, SEND FIFO 알고리즘, 암호화, 방송 패킷 포맷, 수신자 필터/순서는 변경하지 않음.

## 기존 98차 소스에서 재현된 결함과 수정

1. **승인 후 재취소**: `commitDirectFrame()`이 송신 원본을 FIFO에 인계하고 `CIOFrame.finishIO()` 후 drainer/버퍼 반환 등의 후속 작업에서 예외를 던지면 호출부 `finally`가 이미 풀에 반환된 핸들을 `abortDirectFrame()`에 재전달한다. 재사용된 핸들은 다른 SEND를 가리킬 수 있다. FIFO에 인계된 경우 예외를 진단·연결 종료로 처리하고 `true`(admitted, not delivered)를 반환하여 과거 핸들 재취소를 차단한다. 인계 전 거절은 기존대로 `false`, 호출부가 abort한다.
2. **직접 프레임 예약 롤백 중단**: Native 버퍼 반환이 예외를 던지면 `rollbackDirectBegin()`에서 CANCELLED FIFO 티켓 드레인과 READ 재개·종료 알림이 실행되지 않았다. 각각의 정리를 시도하고 최초 실패 및 suppressed를 보존한다. `beginDirectFrame()`의 원래 실패는 롤백 실패로 덮지 않고 연결도 닫는다.
3. **직접 프레임 취소 중단**: `abortDirectFrame()`에서도 Native 반환 예외로 예약 카운터·FIFO 정리가 멈췄다. 검증된 공통 롤백을 재사용하며, 인코더의 `finally`에서 원래 오류가 가려지지 않도록 실패는 로그에 남기고 소켓을 닫는다. 정상 optional abort의 기존 비치명적 정책을 유지한다.
4. **풀 슬롯 반환 누락**: `CIOBufferEx.release()`에서 writer 정리 예외가 나면 Native source 해제 및 물리 슬롯 반환으로 진입하지 못했다. 두 자원의 정리와 슬롯 반환을 모두 시도하고 실패를 suppressed로 유지한다. `start()`의 실패 정리 예외도 `_active` 복원을 막지 않게 수정.
5. **WRITE 완료 정리 중단**: `completeWrite()`는 release 예외 후 `_writing`이 남을 수 있었고, `completeGather()`는 뒤따르는 gather 프레임을 건너뛰었다. 실패 후에도 모든 프레임을 방문하고 송신 회계를 정리하며 해당 소켓을 닫는다. 정상 FIFO 우선순위와 전송 경로는 변경하지 않았다.
6. **종료 시 남은 SEND 미회수**: transport close, 세션 옵저버, 대기 Native 패킷 취소, 큐의 첫 Native 원본 반환 오류가 뒤 프레임 회수를 방해할 수 있었다. `close()`가 가능한 Java 리스 정리와 종료 알림을 계속 진행하도록 격리.
7. **READ 종료 완료 영구 대기**: `releaseReadWhenIdle()`이 `_reading = null` 이후 `_pool.release()`에서 실패하면 `_readReleased`가 false로 남아 종료 완료 통지가 막혔다. 리스 정리 실패를 기록하고 종료 상태를 전진시켜, 실패한 연결을 닫는다.

## 검증 방식 (운영 환경 검증과 구별)

- **98차 원본의 실제 메서드를 추출**하여 타입 스텁에서 예외 주입: 7개 신규 검증 스크립트가 원본에서 실패함을 확인. 그중 `CIOBufferEx.java`와 `CIOFrame.java`는 실제 생산 클래스를 Java 21 컴파일하여 사용.
- 동일 시험을 수정본에 적용: 정상/거부/승인 후 5가지 예외 및 **재사용된 프레임 보호**, DirectAbort optional/strict, rollback 7모드, Native/Writer 동시 실패·슬롯 재사용, 3개 gather 반환 중 1·3번 실패, 소켓 close 중 세션·Native·버퍼 반환 연쇄 실패, READ 반환 실패·중복 반환 검사 모두 통과.
- 87차: 5,000명 *모의* 송신, 5회 무작위, AOI/FIFO/Native ref. 88~89차: 패킷 바이트 비교. 90~91차: 그룹 송신·상태처리 실패 주입. 92~94차: 게임 알림/오림/LFC/다중 원본 반환 회귀 개별 통과.
- Java 21 javac 구문 파싱: 생산 1,710개 **오류 0**. 이는 전체 의존성 타입 컴파일 성공이 아님.

## 주요 판정과 한계

- 이번 검증은 **Native 실제 downcall과 Windows IOCP를 실행한 결과가 아니다.** FFM/IOCP 완전 수명 안전성, Native 해제 downcall 자체가 실패할 때 네이티브 해제의 실성공 여부를 보장하지 않는다.
- 외부 JAR가 포함된 전체 서버 타입 컴파일, Windows 실접속·로그아웃·재접속·5,000명 실제 소켓 부하와 장시간 soak는 수행하지 못했다.
- 패킷 포맷이나 암호화·FIFO 기존 설계 자체를 교체하지 않았으므로 **새 NIO.2 설계로 이관하는 작업도 수행하지 않았다.**
- 정상 실행 경로에서는 신규 Native allocate나 Java 큐·스레드·클래스를 추가하지 않았다.

## 재실행

`bash docs/phase99_audit/run_tests.sh`

테스트에 포함된 `test_*.py`는 실제 생산 메서드·클래스를 읽어 typed stub과 컴파일하며, `RKERN_TEST_CUSERSOCKET` 및 `RKERN_TEST_CIOBUFFEREX` 환경변수로 이전 소스에 동일한 실패 주입을 적용할 수 있다. 테스트 내 stub은 production에 포함되지 않는다.
