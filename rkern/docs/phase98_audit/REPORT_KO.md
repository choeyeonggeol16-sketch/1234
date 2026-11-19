# 98차 방송 Native 원본 수명 재검수 — 2026-11-17

## 기준 및 수정 범위

- 기준: `RKern_NC_NativeBroadcast_Phase94_97_Integrated_Audit_FULL_SRC_20261117.zip`.
- 생산 Java 변경: **1개**, `src/l1j/server/server/model/network/core/broadcast/CAsyncSendConstBuffer.java`.
- CIO 소켓, DLL, SEND FIFO, 암호화, 패킷 포맷, 게임 로직, 새 워커·큐를 변경하지 않음.

## 원본에서 재현한 결함

1. `Source.release()`의 `references.decrementAndGet()`은 중복 `release()`를 검사하기 전에 내부 카운터를 -1로 변경했다. 두 번째 호출에서 예외가 발생해도 -1이 남으므로 뒤따르는 `retain()`이 이를 0으로 증가시키며 **이미 해제된 Native handle의 재참조를 허용**할 수 있다. Java 21 생산 `Source`와 타입 스텁으로 원본의 해당 오류를 재현했다.
2. `openWrittenSource()`는 writer/Native seal 실패 뒤 `source.release()`도 예외를 던지면 *원래 오류가 해제 예외로 덮이는 문제*가 있었다. 원래 오류를 보존하고 해제 오류를 suppressed에 기록하도록 수정했다.
3. 16KiB Native source에서 overflow가 난 상태에 **Native slab 반환까지 실패**한 경우 원래의 overflow만 보고 대형 프레임 경로를 이어가면 반납 결함이 가려질 수 있었다. 원본 해제 실패 때는 fallback을 중단하도록 명확한 오류 경계를 추가했다. 원본 반환이 정상인 크기 초과 패킷은 기존 대형 프레임 경로를 유지한다.

## 검증

- 실제 생산 `CAsyncSendConstBuffer.java`를 Java 21 `javac --enable-preview --release 21`로 컴파일하고 테스트용 소켓/Native 소유자 스텁을 사용.
- 중복 `release()` 이후 `retain()` 재사용 차단, native release 정확히 1회.
- 8개 스레드 × 10,000회 `retain/release` (총 80,000회) 후 단일 최종 반납.
- 패킷 작성 실패 + Native 반환 실패: 원래 예외 보존, 반환 예외 suppressed.
- Native 16KiB보다 큰 프레임: 정상 cleanup 후 하나의 frozen frame 공유, 실패 cleanup에서는 fallback 중지.
- 기존 94~97차 누적 방송 모의 회귀 테스트 실행; 상세 결과는 아래 실행 로그 및 최종 응답 참조.

## 한계

- 독립 테스트는 실 JNI/FFM downcall 대신 타입 호환 스텁을 사용했음.
- 외부 JAR 포함 전체 서버 타입 컴파일, Windows DLL/IOCP, 실제 5,000명 동접 부하 시험은 **미검증**이며 통과 주장하지 않음.
- 실제 Native 해제 호출 자체가 실패했을 때 Native 쪽 메모리가 반환되었는지는 Java 측 테스트만으로 보장할 수 없음.

## 다시 실행

`bash docs/phase98_audit/run_tests.sh`

기존 누적 테스트: `bash docs/phase94_97_audit/run_tests.sh`
