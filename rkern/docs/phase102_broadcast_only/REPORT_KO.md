# 102차 — 방송 전용 결함 재현·수정 보고서

## 기준 및 범위

- 기준: `RKern_NC_NativeBroadcast_Phase101_Broadcast_Only_Scope_Corrected_FULL_SRC_20261117.zip`
- 생산 Java 수정 정확히 2개:
  1. `src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java`
  2. `src/l1j/server/server/model/network/core/sc/packet/SC_ChatPacket.java` (**방송 메서드와 방송 writer의 허용 길이 판정만**)
- `CUserSocket`, 일반 SEND/RECV, CIO 공통 실행 구조, Native DLL, DB, NPC, 게임 상태 로직 수정 없음.

## 재현 결함 1 — 첫 선택적 방송 수신자의 치명적 SEND 오류에서 Native 원본 누락

`sendWrittenOptionalTo`가 첫 수신자용 `CAsyncSendConstBuffer`를 생성한 뒤, 그 수신자의 SEND에서 `Error`가 발생하면 함수가 반환되지 않으므로 호출자에게 소유권이 이전되지 않습니다. 기존 `catch(RuntimeException)`은 이 경로를 다루지 못해 생성한 Native 원본이 반납되지 않았습니다.

수정: 이 헬퍼가 최초로 생성한 원본(`first`)을 추적하고 첫 송신의 `Error`를 다시 던지기 전에 반드시 `close()`를 시도합니다. 반환 오류가 중첩되면 최초 송신 `Error`에 `suppressed`로 기록합니다. 후속 수신자를 계속 처리하는 일반 `RuntimeException` 정책, 수신 순서, Native 공유 방식은 유지합니다.

**수정 전 재현**: 실제 101차 생산 클래스 + 타입 스텁에서 `first optional recipient Error must release newly created Native source` 실패.

**수정 후**: 같은 시험 통과. Native 반환 실패를 동시 주입해 원래 SEND `Error`와 중첩 반환 실패가 모두 기록되는 것도 통과.

## 재현 결함 2 — 실제 유효한 대형 MS949 채팅 방송 누락

`SC_ChatPacket.broadcastMessage`, `broadcastOpcode`, `broadcastComplex0`, `nativeTargetWriter`가 길이를 `32 + 4 × UTF-16 문자열 길이`로 가정하고 `65535`를 넘으면 일괄 반려했습니다. 이 수치는 **실제 L1 패킷의 바이트 길이가 아닌 과도한 예약 용량 추정치**입니다. 예를 들어 ASCII 20,000자는 framing 포함 20,005바이트에 불과한데 원본 방송이 누락됩니다.

수정: 이 네 방송 경로에 실제 16-bit 패킷 길이 기준을 적용합니다. 보수적 `2 × 문자열 길이`가 한도 이하면 인코딩을 하지 않는 빠른 판정을 유지하고, 경계에서만 정확한 MS949 byte 길이를 계산합니다. `nativeWriter` 역시 같은 판정을 재사용합니다. 기존 opcode/type/objectId, 패킷 직렬화, NUL 종료, recipient iteration, FIFO 및 암호화는 변경하지 않습니다. 16KiB 초과의 정상 패킷은 이미 존재하던 대형 공유 송신 분기를 이용합니다.

**수정 전 재현**: 실제 101차 `SC_ChatPacket` 생산 코드를 컴파일해 실행했을 때 `valid 20,000-byte chat broadcast silently dropped` 실패.

**수정 후**: ASCII 20,000자, MS949 한글 20,000자, 최대 65,535바이트 프레임 전달; 초과 패킷 거부; 원본 opcode/type/프레이밍 확인. 일반 1:1 `sendDirectRaw` 변경 없음.

## 회귀 검증 (모의·추출 환경)

- Java 21 실제 생산 방송 클래스·`SC_ChatPacket`을 타입 스텁과 함께 컴파일하고 신규 실패 주입 검증: 통과.
- 101차 방송 범위 회귀: 통과(오림 파티장 1회, 그룹 전환, LFC, 발락·공성 알림 호출부).
- 87차 방송: AOI, FIFO, 참조수, 5,000명 모의 송신 및 다중 반복: 통과.
- 88/89차 실제 생산 SC 바이트 비교: 40,000 + 40,000 + 50,000 케이스 통과.
- 90차 그룹 방송: 중복 수신자·연결 종료·순서/반환 통과.
- 93차 실패 주입, 98차 참조수/16KiB 경계: 통과.
- 생산 Java 1,710개 구문 검사: 오류 0 (이는 **전체 의존성 타입 컴파일이 아님**).
- 원본 ZIP 전체 항목의 바이트 비교: 위 생산 Java 2개 외 모든 원본 파일 동일.

### 재실행

```bash
bash docs/phase102_broadcast_only/run_new_tests.sh
bash docs/phase101_broadcast_scope/run_broadcast_tests.sh
bash docs/phase90_audit/run_tests.sh
bash docs/phase98_audit/run_tests.sh
```

## 아직 미검증

외부 JAR를 갖춘 전체 프로젝트 타입 컴파일, 실제 Native DLL이 실행되는 Windows IOCP 장기 부하, 5,000명 실접속, 실제 게임 UI 행동은 실행하지 않았습니다. 신규 두 결함이 수정된 것은 해당 실패 주입·모의 검증 기준이며 **서버의 모든 버그가 사라졌다는 증거는 아닙니다**.
