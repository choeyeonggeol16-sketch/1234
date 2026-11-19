# 111차 — 110차 기준 구형 브로드캐스트 잔존 검증 및 전체 공지 실패 격리

## 결론

- 110차 생산 Java **1,710개**에서 주석·문자열을 제외한 실행 코드의 구형 방송 API(`broadcastPacket`, `broadcastPacketToAll`, `wideBroadcastPacket`, `broadcast_map`) 호출은 **0개**입니다.
- `SC_*` 패킷의 방송 메서드 **156개**의 본문에서 구형 `sendPackets`·`sendPacket`·`new S_*` 방송 직렬화 경로는 **0개**입니다.
- `L1PcInstance`/`L1Object` 직접 반복문의 `sendPackets` 후보 **10곳**은 GM/유저 개인별 응답·파티 상태 처리 등 수신자별 내용을 만드는 경로입니다. 동일 원본을 반복 직렬화하는 구형 전체 방송으로 분류되지 않습니다. 위치와 헤더는 `legacy_inventory.json`에 보관합니다.
- 위 결과는 **소스 레벨의 구형 API 제거 확인**입니다. 리플렉션·외부 JAR·실제 Windows DLL 동작에 대한 완전한 보장은 아닙니다.

## 발견된 방송 오류 1 — 공용 전체 방송 한 수신자의 예외로 후속 공지 중단

`CSharedBroadcast.sendWrittenAll()`이 엄격한 `sendWrittenRecipients()`를 호출하여, 개별 수신자의 SEND 중 RuntimeException이 발생하면 이후 정상 수신자에게 전송하지 못했습니다. 이 공통 메서드를 이용하는 방송으로 `SC_SystemMessage`, `SC_NewChat`, `SC_Weather`, `SC_War`, `SC_PacketBox`, `SC_IconMessage` 등의 전체 공지가 있습니다.

**수정:** `sendWrittenAll` 안에서 수신자별 연결 확인과 SEND 실패를 독립 처리하고, 동일 Native 원본과 원래 FIFO/중복 순서를 유지합니다. `sendWrittenRecipients` 등 엄격한 API의 예외 계약은 **수정하지 않았습니다**. 게임 상태·개인별 송신·공통 CIO/SEND 코어는 수정하지 않았습니다.

- **110차 원본에서 재현:** 실제 `CSharedBroadcast.java` + 타입 스텁의 첫 수신자 SEND 실패 주입 시 `IllegalStateException`이 전파되어 다음 수신자 전송 중단 (`original_110_reproduction.log`).
- **111차 수정본:** 첫 수신자 SEND 실패 시에도 다음 수신자 정상 전송; 일치하는 3바이트 프레임·FIFO·중복 수신·한 번 인코딩·Native 한 번 반환·무수신 시 지연 생성·엄격한 명시 수신자 API의 예외 전파 통과 (`new_tests.log`).

## 발견된 방송 오류 2 — 잘못된 패킷을 수신자 수만큼 재인코딩

단순히 기존 BestEffort 반복 메서드를 전체 방송에 적용하는 중간 버전은 **패킷 원본 작성 자체가 실패해도 수신자마다 다시 작성**했습니다. 실제 중간 버전에서 원본 재생성 2회(수신자 2명) 실패 주입으로 재현 (`intermediate_invalid_writer_reproduction.log`).

**수정:** 방송 전용 원본 클래스 `CAsyncSendConstBuffer`에 *오류 경로에서만* 호출되는 `hasFrozenSource()`를 추가했습니다. 유효한 원본이 존재하면 개별 SEND 실패로 처리하고 다음 수신자로 진행합니다. 원본 작성이 실패하여 유효한 프레임 자체가 없으면 즉시 전체 방송을 중단하여 5,000회 불필요한 재시도를 방지합니다. 정상 송신 hot path에서 추가 잠금 획득은 없습니다.

## 수정 범위

생산 Java 변경 파일 **정확히 두 개**:

1. `src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java`
2. `src/l1j/server/server/model/network/core/broadcast/CAsyncSendConstBuffer.java`

기타 생산 Java / Native DLL / RECV·SEND·CIO 공통 코드 / DB / NPC / 전투·게임 상태 코드는 110차와 바이트 단위 동일합니다. 신규 파일은 **검증 자료(`docs/phase111_legacy_broadcast_audit/`)뿐**입니다.

## 검증

- Java 21 typed-stub 컴파일 + 실제 생산 방송 메서드 실행 (`run_tests.sh`): **PASS**.
- 전체 공지 실패 주입, 5,000명 모의 송신(중간 1명 실패), FIFO, 동일 프레임, 지연 인코딩, Native 반환 1회, 5,000명 인코더 오류 1회 중단: **PASS**.
- 110차 대표 누적 회귀 스크립트 `docs/phase110_broadcast_audit/run_representative_regressions.sh`: **PASS** (87~110차의 방송 관련 패킷·AOI·참조수·실패 주입 대표 테스트; `representative_regressions.log`).
- 구형 방송 API 0개·SC 방송 156개·수신자별 송신 후보 10개 자동 감사 (`test_legacy_paths.py`): **PASS**.

### 검증 한계

전체 의존성 JAR를 포함한 생산 1,710개 Java의 **완전한 타입 컴파일**, Windows Native DLL/IOCP 실송신, 실제 클라이언트 및 동접 5,000명 장시간 테스트는 수행하지 않았습니다. `new_tests.log`의 SEND 실패는 **타입 스텁에 의도적으로 삽입한 예외**이며 실제 Windows 소켓에서 재현된 이벤트는 아닙니다. 전체 방송의 무결점을 보증하지 않습니다.

### 재실행

```bash
bash docs/phase111_legacy_broadcast_audit/run_tests.sh
bash docs/phase110_broadcast_audit/run_representative_regressions.sh
```
