# 101차 방송 수정 범위 정정

## 원본
- 기준: 99차 전체 SRC (Phase99).
- 100차의 수정 가운데 **방송 코어와 방송 전용 호출부에 해당하는 7개 생산 Java 파일만 유지**했습니다.
- 일반 SEND/소켓 종료/풀 관리 공통 코드 `CUserSocket.java`, `CIOBufferPool.java`의 100차 수정은 취소했습니다. 두 파일은 99차와 바이트 동일합니다.
- 신규 DLL/워커/스레드/큐, NIO.2 이관, DB/NPC/전투 상태 로직 변경 없음.

## 방송 전용 유지 수정
- `CAsyncSendConstBufferExceptSelf.java`: Native 공유 방송 원본 생성 실패 시 cleanup의 중첩 예외 정보 보존.
- `CSharedBroadcast.java`: 선택적 알림/그룹 방송 중 수신자 컬렉션 반복 실패의 예외 격리.
- `OrimController.java`: **방송 메서드**에서 오림 파티장 대사 중복 송신 방지 및 알림 실패 격리.
- `MJInstanceEnums.java`, `MJServerMacroLoader.java`, `BossController.java`, `FishingTimeController.java`: **방송 전용 알림 호출문만** best-effort로 조정.

## 범위 검증
- 실제 ZIP 추출 후 위의 7개 생산 Java 외 변경 파일이 없는지 99차와 바이트 대조.
- `CUserSocket.java`, `CIOBufferPool.java`는 99차와 byte-identical.
- `run_broadcast_tests.sh`와 `run_syntax.sh` 실행 결과는 최종 압축 생성 전에 기입.
- 외부 JAR 전체 타입 컴파일, 실제 Windows IOCP, 실서버 동작 검증은 이 정정 패키지에서 수행하지 않음.

**주의:** 99차에 이미 존재하던 네트워크 코어 수정은 그대로 보존합니다. 이번 정정은 100차에서 추가로 벗어난 변경만 되돌리는 것이며, 과거 전 차수 전체의 네트워크 변경을 소급 제거하는 것은 아닙니다.

## 이번 패키지의 실행 검증 (2026-11-17)
- Java 21 생산 코드 구문 검사: 1,710개, 오류 0.
- 방송 범위 동작 테스트: `run_broadcast_tests.sh` 통과 (오림 파티장 1회 송신, 첫 그룹 예외 후 두 번째 그룹 송신, Native 원본 참조 회수, 93차 방송·인던 회귀).
- 기존 87, 88, 89(wire/message), 90차 방송 AOI·FIFO·패킷 바이트 회귀 테스트: 개별 실행 모두 통과.
- 새로 설치하거나 수정한 Native DLL 없음. 소켓 부하 테스트, 전체 타입 컴파일, Windows IOCP 실전 검증은 수행하지 않음.
