# RKern 103~110차 방송 전용 통합 감사

## 기준 / 변경 범위

- 기준: `RKern_NC_NativeBroadcast_Phase102_BroadcastOnly_FailureAndChatFix_FULL_SRC_20261117.zip`.
- 생산 Java 변경: **1개 파일** — `src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java`.
- 다른 생산 Java, Native DLL, CUserSocket 일반 SEND/RECV, CIO 공통 코드, DB, NPC, 게임 상태 로직은 변경하지 않음.
- 기존 수신자 순서/중복 처리, 패킷 작성, Java 21/NIO2 이관 여부를 바꾸지 않음.

## 실제 확인된 오류 (105차 범위)

### 전체 유저 / GM 선택적 방송 수신자 필터 실패가 후속 방송을 차단

`sendWrittenAllPlayableBestEffort`의 `pc.isPrivateShop()` / `pc.is_world()`와
`sendWrittenAllGmsBestEffort`의 `pc.isGm()`가 RuntimeException을 던지면,
기존 코드에서는 컬렉션의 **전체 반복문 바깥** catch로 제어가 이동하므로
뒤에 있는 정상 수신자가 송신되지 않았음. 이미 존재하던
`sendWrittenOptionalTo`의 per-recipient SEND 실패 격리와 불일치.

**수정**: 두 선택적 전체 방송 메서드만 각 수신자의 필터 검사에
`try/catch(RuntimeException)`을 적용해 해당 수신자만 건너뜀. 기존 전체
컬렉션 획득·반복 실패 catch, 로깅 빈도 제한, shared 원본 close/finally,
GM/상점/월드 필터, FIFO와 중복 수신자 의미론은 유지.

- **수정 전 실제 생산 클래스 + Java 21 typed stubs**: `playable after throwing selectors missing` assertion 발생 (`original110_test.log`).
- **수정 후**: private-shop/월드/GM 예외 삽입, 앞뒤 정상 수신자, GM 필터,
  정상 필터 및 중복 순서, Native producer+consumer reference 반환 시험 통과.

## 103~110차 연속 검수 범위

| 차수 | 검수 대상 | 확인 내용 |
| --- | --- | --- |
| 103 | Native shared source | 98차 double release/CAS/overflow/return-failure 회귀 통과 |
| 104 | AOI·FIFO·중복 수신 | 87차 5,000명 mock, 90차 두 그룹 FIFO 회귀 통과 |
| 105 | 선택적 전체 방송 필터 | 원본 오류 재현 → 변경 후 실패 주입 통과 (이번 생산 변경) |
| 106 | 대형 프레임 | 16,385/65,535바이트, 초과 거부, writer mismatch, 반환 경계 신규 시험 통과 |
| 107 | MS949 채팅 | 102차 20,000/65,535바이트 채팅 방송 원본 회귀 통과 |
| 108 | 패킷 바이너리 호환 | 88·89차 S-packet, S_EVENT, system message 바이트 테스트 통과 |
| 109 | 이벤트 알림 | 92·93·101차 오림/LFC/GM/공지, Native 반환 실패 회귀 통과 |
| 110 | 통합 / 범위 / ZIP | Java 1,710개 **구문** 검사 통과, 원본 대비 정확히 생산 1개 파일 변경, 재압축 CRC/재추출 테스트 |

## 재현·회귀 시험

- `run_new_tests.sh` — 실제 `CSharedBroadcast.java`와 생산 공유 소스 클래스
  + 최소 타입 스텁을 Java 21로 컴파일하여 `OptionalWorldFilter110`,
  `LargeAndFanout110`을 실행. 특수 필터 실패, 대형 프레임, FIFO/중복,
  writer 호출 횟수, resource close 확인.
- `regression.log` — 실행된 기존 회귀 목록 및 실제 출력 보관.
- `original110_test.log` — 원본 102차 생산 소스의 재현 실패 출력.
- 기존 87/88/89/90/92/93/94/98/101/102차 방송 관련 스크립트를 개별 실행,
  타임아웃을 통과로 취급하지 않음.

### 실행 방법

```bash
bash docs/phase110_broadcast_audit/run_new_tests.sh
bash docs/phase102_broadcast_only/run_new_tests.sh
bash docs/phase101_broadcast_scope/run_broadcast_tests.sh
bash docs/phase87_audit/run_tests.sh
bash docs/phase98_audit/run_tests.sh
```

## 검증 한계

- 통과는 typed-stub 컴파일·모의 송신·정적 검사·실패 주입 기준.
- Java 21 전체 소스 타입 컴파일은 프로젝트 외부 JAR 부재로 **미완료**.
- Windows Native DLL/IOCP 실제 송신, 동접 5,000명 실환경, 게임 클라이언트
  UI/장시간 soak는 수행하지 않았음.
- 이 보고서는 **이번에 재현한 오류 1건의 수정과 명시된 범위의 회귀 통과**를
  뜻하며 전체 방송 코드 무결점 보증이 아님.
