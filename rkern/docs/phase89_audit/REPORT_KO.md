# RKern 브로드캐스트 89차 — 88차 356건 재분류·확인된 중복 방송 교체·회귀 검증

**기준:** `RKern_NC_NativeBroadcast_Phase88_DeepAudit_FULL_SRC_20261116.zip`. **변경 production Java:** 5개. Native C++ / `CTransport.dll` / CIO 네트워크 워커·큐 / AI / DB는 변경하지 않았다.

## 1. 남은 후보 356건과 원래 567건의 의미

88차 문서에서 356은 **확인된 구형 방송 356건이 아니라** 567개 AST 반복 호출 후보 중 표면상 명시 공유 송신 이름으로 쉽게 판별되지 않았던 후보 수이다. 89차는 88차 원래 **567개 행 전부**에 파일/행/루프/메서드/코드 문맥과 정적 분류 결과를 붙여 `CLASSIFIED_567_LOOP_SITES.tsv`에 보존했다. 567개 전체를 다시 살폈으므로 356개 후보를 제외하지 않았다. 이 CSV/TSV의 분류는 **소스 문맥에 근거한 정적 분류**이며 서버 실행 경로를 모두 재현했다는 증명은 아니다.

| 정적 분류 | 건수 | 처리 |
|---|---:|---|
| 기존 공유 소스 / 브로드캐스트 엔진 내부 | 233 | 구현 상태 보존 |
| 기존 방송 래퍼 또는 서로 다른 게임 이벤트별 방송 | 58 | 기존 Native 공유 메서드와 반복 이벤트 의미 구분 |
| 개인별 패킷/게임 처리/GM·아이템 조회 반복 | 191 | 공유 대상에서 제외 |
| 서로 다른 데이터/수신자별 상태/순서가 있는 송신 | 71 | 무리한 일괄 교체 방지 |
| 네트워크 내부 SEND 큐/완료 처리 | 9 | 게임 방송이 아니므로 제외 |
| 89차에서 전환한 해당 원본 후보 | 5 | 공유 송신으로 교체 |
| **합계** | **567** | **각 항목은 위 TSV에서 추적 가능** |

*위 표의 71건은 `EXCLUDED_RECIPIENT_OR_SEQUENCE` 56, `RECIPIENT_DEPENDENT` 5, `INDIRECT_OR_SEQUENTIAL` 10의 합계다. 191건은 `PERSONAL_OR_GAME_OPERATION` 143과 `NOT_GROUP_BROADCAST` 48의 합계다. 동일 AST 루프와 실행 이벤트가 중첩 집계될 수 있어 '남은 방송 수'로 읽으면 안 된다.*

기존 탐지기의 `send`/`broadcast` 이름 편향을 피하기 위해 `commit`/`enqueue`도 추가로 AST 탐색했고, 특히 **글루디오 인던의 `Proto.commit(w)`를 사용하는 동일 패킷 반복 직렬화 경로**를 별도로 추적했다. 기타 새 검출은 게임 DB 커밋·내부 큐·동시접속 상태와 같은 비방송 패턴이었다. 전체 프로그램의 모든 반사 호출·외부 바이너리 경로까지 정적 증명할 수는 없다.

## 2. 실제 교체한 기존 중복 송신 (6개 주요 경로, Java 5개 파일)

1. `MJCombatObserver.dispose_users()` — 전투장 퇴장 카운트다운 `S_EVENT 0x47` 패킷을 맵 플레이어마다 다시 조립하던 경로를 Native 원본 1개 공유로 전환. 기존 맵 순회/수신 필터/FIFO 보존.
2. `GludioDungeonPackets.broadcastRoomStatus()` — 멤버마다 전체 방 상태 protobuf를 다시 조립하던 경로를 **멤버 목록 1회 스냅샷 → protobuf 1회 완성 → 공유 송신**으로 변경. 원래 extended-protobuf opcode·메시지 ID·트레일러 유지.
3. `GludioInstanceDungeon` 인트로 종료 시 상태 방송 — 동일한 `SC_ARENA_PLAY_STATUS_NOTI`를 `gameInfoSent` 멤버들에게 원본 공유.
4. `GludioInstanceDungeon.finish()` 종료 상태 방송 — 공유 상태 프레임을 **각 멤버에게 먼저 송신한 후 그 멤버를 텔레포트**하도록 유지. 멤버별 `status -> teleport`의 선후 보존.
5. `L1PcInstance` 신규/중립 혈맹 탈퇴 통지 — 모든 온라인 혈맹원에게 동일한 `ServerMessage.LEAVE_CLAN(playerName, clanName)`을 새로 직렬화하던 경로를 한 공유 프레임으로 변경.
6. `MJLFCCreator` 취소 통지 — 블루 팀을 먼저, 레드 팀을 나중에 돌면서 각 멤버에 대해 `setInstStatus(NONE) -> CREATE_CANCEL 메시지`를 보내던 경로에서 **그 순서를 유지**하고 같은 텍스트 프레임을 공유.

**제외한 예:** `Queen1`의 각 플레이어 혈맹명, `Hadin/Valakas`의 플레이어 자신의 오브젝트 ID, `OrimController`의 대상별 난수 좌표, `BossController.sendEvents()`의 수신자 ID 포함 패킷, `CS_Craft`의 비동기 혈맹 버프 업데이트, 물품·상점·GM 결과·인벤토리·입장 상태 변경 루프. 기존 방송 래퍼 이름이 남아도 내부가 이미 Native 공유 구현이면 중복 변환하지 않았다.

## 3. 검증과 한계

- `bash docs/phase87_audit/run_tests.sh`: **PASS**. 실제 `CSharedBroadcast`/`CAsyncSendConstBuffer`를 테스트 스텁과 함께 컴파일, 5,000 모의 수신자 × 2, 경계/동시성/FIFO/참조수/AOI 테스트. **실제 동접 5,000명 시험이 아님**.
- `bash docs/phase88_audit/tests/run_wire_test.sh`: **PASS**, 기존 직접 SC 효과·스킬·혈맹 바이트 vs 공유 Writer 40,000건.
- `bash docs/phase89_audit/tests/run_wire.sh`: **PASS**, 실제 `ProtoWriter` Direct Mode vs Array Mode **40,000건**, `S_EVENT` LittleEndian **50,000건**. 이 테스트는 production `ProtoWriter`를 사용하지만 게임/실제 IOCP는 스텁임.
- `bash docs/phase89_audit/tests/run_message_test.sh`: **PASS**, 실제 `SC_ServerMessage` / `SC_SystemMessage` 직접 송신 vs Native Writer **40,000건** 바이트 일치.
- 최종 Java 1,710개 AST 구문 검사 **오류 0개**. AST 반복 송신 탐지기 재실행 **566건** (패킷을 바꿨다고 모든 AST 루프가 없어지는 것은 아님).
- ZIP CRC, 88차 대비 production 변경 목록·미변경 동일성 대조.

**실행하지 않은 것:** 전체 외부 JAR를 포함한 production 타입 컴파일, 실제 Windows IOCP 서버/클라이언트 접속, 5,000명 실동접, 인던 입장/텔레포트 실동작, 각 시나리오의 전 서버 최종 기능 동등성. 89차의 정적 분류 결과나 모의시험 합격을 근거로 '버그 0개가 증명됐다'거나 '서버 전체 기존 방송 0개'라고 말하지 않는다.

## 4. 소스 변경 파일

- `src/l1j/server/MJCombatSystem/MJCombatObserver.java`
- `src/l1j/server/IndunSystem/Gludio/GludioInstanceDungeon.java`
- `src/l1j/server/server/packets/Protocol/MainServer/Indun/Proto/GludioDungeonPackets.java`
- `src/l1j/server/server/model/Instance/L1PcInstance.java`
- `src/l1j/server/MJInstanceSystem/MJLFC/Creator/MJLFCCreator.java`

감사 분류 자료 및 실행 스크립트는 `docs/phase89_audit/` 내부에 포함한다.
