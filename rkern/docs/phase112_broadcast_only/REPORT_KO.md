# 112차 — 111차 이후 방송 경로 연속 재검수 및 실패 주입 수정

## 범위
- 111차 FULL SRC를 기준으로 **방송 소스 및 방송 전용 호출문만** 변경.
- 일반 SEND/RECV/CIO/DB/NPC AI/게임 상태/Native DLL 변경 **없음**.
- 기존 `sendWrittenRecipients` (엄격 실패 계약) 자체는 유지. 오직 공지·효과·채팅 등 선택적 방송 호출부만 `sendWrittenRecipientsBestEffort`로 전환.

## 재현된 문제와 수정
1. **선택적 방송의 잘못된 원본을 수신자 수만큼 반복 작성**:
   - 111차 실제 생산 `sendWrittenRecipientsBestEffort`에서 5,000명 대상 실패 패킷 writer가 **5,000회 호출**되는 문제 재현.
   - 112차 `UnavailableOptionalSource` 예외 경계로 유효한 Native plaintext 작성 실패를 방송 단위로 중단. 이미 얼려진 원본에 대한 개별 SEND 실패는 다음 수신자로 진행.
   - `sendWrittenRecipientsBestEffort`, array/groups/all playable/all GM, clan 처리 모두 1회 실패 후 중단.
   - 65,535바이트 초과 invalid writer는 기존 16KiB + 대형 프레임 대체 시도 **각 1회**로 제한; Native 소스 반환 확인.
2. **20KiB 광역 방송에서 첫 수신자 WRITE 오류가 뒤 수신자까지 차단**:
   - 111차 실제 `sendWrittenLargeSelfAndVisible`/`sendWrittenLarge` 경로에서 한 소켓의 `sendFrozenWire()` RuntimeException이 뒤쪽 방송을 중단하는 문제 재현.
   - 페이로드 생성은 한 번만 하되, 수신자별 소켓 SEND 및 접속 상태 조회 실패만 격리. 패킷 인코더 오류는 전역 오류로 유지.
   - 정확히 20,000바이트 프레임 및 2바이트 길이 prefix 확인. AOI borrow 반환·중복 순서 유지.
3. **전용 방송 진입점의 엄격 송신으로 인한 후속 수신자 누락**:
   - 111차 실제 `SC_SystemMessage.broadcastRecipients`의 첫 수신자 SEND 실패가 예외를 던지며 후속 수신자 전송을 중단하는 문제 재현.
   - NPC 대사 4개 방송 진입점, 복합 채팅, 시스템 메시지, 녹색 맵 공지, 문장 엠블럼, 정기 공지에 선택적 방송 적용.
   - `SC_War.broadcastClan`도 혈맹 멤버 반복자와 순서를 유지하는 `sendWrittenClanBestEffort`로 전환.
4. **잔여 엄격 방송 호출부**:
   - `MJEffectTriggerInstance`의 이동·행동 방송, `MJL1TreasureChest`의 열림 이펙트, `L1ChatParty`의 입장 통지에서 한 명의 SEND 실패가 다음 동작을 차단하는 경로를 해당 **방송 호출문만** 교체.
   - 원본에서 `MJEffectTriggerInstance.do_move`의 SEND 실패로 예외 탈출 재현. 수정본에서 좌표 갱신 유지, 뒤 수신자에게 방송, 반환 성공.

## 타입·테스트
- 실제 생산 `CSharedBroadcast`, `CAsyncSendConstBuffer` 및 테스트용 타입 스텁 Java 21 컴파일/실행: PASS.
- 5,000명 invalid source injection, 20KiB 부하·WRITE 오류·AOI, 6개 SC 방송 진입점, 4개 잔여 방송 진입점: PASS.
- 111차 전체 방송 + 구형 API 감사: PASS.
- 110차 대표 누적 회귀(87~110차): PASS. 기존 패킷 바이트 40k/90k 사례, AOI/FIFO, 5,000명 모의 송신 포함.
- 전체 생산 Java 1,710개 `JavacTask.parse()` 구문 검사 오류 **0**. 타입 의존성 통합 컴파일을 의미하지 않음.
- 원본 111차에서 발생하는 각 신규 오류의 재현 로그와 수정본 테스트 로그를 이 디렉터리에 보존.

## 남은 한계
- 실제 Windows DLL/IOCP 송신, 전체 외부 의존성 JAR 포함 프로덕션 타입 컴파일, 실제 5,000명 장시간 플레이는 **미실행**.
- 다른 엄격 송신 API는 상태 의미와 실패 계약을 무단 변경하지 않음. 이번 범위를 넘어선 일반 네트워크 코어는 손대지 않음.
