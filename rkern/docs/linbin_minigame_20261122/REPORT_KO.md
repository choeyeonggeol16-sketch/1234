# mjlin 복호화 바이너리 미니게임 3종 역분석 / RKern 향후 구현 보고서

- 작성: 2026-11-22
- 상태: **분석·설계 참고 문서** (구현 및 서버 코드 수정 없음)
- 대상으로 확인한 실행 이미지: mjlin_loaded_code_00400000_0180D000.bin / 로컬 재구성본 mjlin_loaded.bin
- 실행 이미지 SHA-256: 256f7886e98d7d2c573ab76917f78e30dc4f34cb723a002d604602cdb2f6cbdb
- 자료 출처: **실제 실행 이미지 내부 protobuf FileDescriptorProto** 직접 파싱, 기존 저장소 복원 스키마와 대조.
- 이미 복원되어 있는 완전한 스키마: [MainServer_Client.proto](../MainServerProto/schemas/MainServer_Client.proto) 중 MinigameCode, MinigamePuzzleInfo ~ SC_MINIGAME_ADEN_USE_CARD_ACK.
- 이 문서는 원본 NC 소스가 아니라 **바이너리에서 추출한 정보**에 기초한다. 게임 규칙·보상·UI 구현에 관한 미확인 사항을 추측으로 확정하지 않는다.

## 1. 이번에 확정한 범위와 아직 모르는 범위

**확정:** 바이너리 이미지에 LIN 패키지의 proto2 메시지 18종(정보/결과 구조 8종, 요청·응답 10종)과 MinigameCode enum이 직렬화된 FileDescriptorProto 형태로 존재한다. 미니게임은 PUZZLE(0), CATCH_GREMLIN(1), ADVENTURE_OF_ADEN(2) 세 종류이다. 각 필드의 번호·원시 타입·required/optional/repeated·중첩 enum 값까지 복원됐다.

**미확인:** 각 메시지의 네트워크 헤더용 실제 **메시지 ID**(숫자 opcode/msgId), 클라이언트 수신 디스패처의 실제 핸들러, 미니게임 UI 리소스, 게임 판정 알고리즘, 확률·보상표, 서버의 세션 저장 구조, 활성화 여부, 쿨타임/타임스탬프 단위. **protobuf 필드 번호(1, 2, 3...)는 네트워크 메시지 ID가 아니다.**

추가 문자열 CS_MINIGAME_NOTI_REQ(실행 이미지 주소 0x0120AC44)는 보였지만, 아래 10개 메시지와의 정확한 관계·스키마·메시지 ID는 확인되지 않았다. 별도 확인 전까지 동일 흐름으로 간주하지 않는다.

### 바이너리 검증 지점

| 주소 (VA) | 실제 확인 내용 |
|---|---|
| 0x012808EB | MinigamePuzzleInfo DescriptorProto 시작 |
| 0x012809AC | MinigameCatchGremlinInfo DescriptorProto 시작 |
| 0x01280BF3 | MinigameAdenInfo DescriptorProto 시작 |
| 0x01281161 | CS_MINIGAME_LIST_REQ |
| 0x0128123D | CS_MINIGAME_START_REQ |
| 0x01281280 | SC_MINIGAME_START_ACK |
| 0x012813F4 | CS_MINIGAME_END_REQ |
| 0x012814BF | SC_MINIGAME_END_ACK |
| 0x01281584 | CS_MINIGAME_ADEN_PLAY_REQ |
| 0x012815B5 | SC_MINIGAME_ADEN_PLAY_ACK |
| 0x01281694 | CS_MINIGAME_ADEN_USE_CARD_REQ |
| 0x012816D5 | SC_MINIGAME_ADEN_USE_CARD_ACK |
| 0x01285FBF | MinigameCode enum |
| 0x0120AC44 | CS_MINIGAME_NOTI_REQ 이름 문자열만 별도 존재 |

검증 방법: 상기 실행 이미지에서 각 protobuf 메시지 레코드(0x22, length-delimited)를 추출해 google.protobuf.descriptor_pb2.DescriptorProto로 직접 파싱했다. 기존 MainServer_Client.proto의 1855~2054행과 필드 정의가 일치한다.

## 2. 프로토콜 전체 목록 — 요청/응답 10종

| 방향 | 메시지 | 실제 필드 (번호: 이름 / 타입 / 규칙) |
|---|---|---|
| C→S | CS_MINIGAME_LIST_REQ | 없음 (빈 메시지) |
| S→C | SC_MINIGAME_LIST_ACK | 1: minigame_info_list / MinigameInfo / repeated; 2: server_time / uint64 / required |
| C→S | CS_MINIGAME_START_REQ | 1: minigame_code / MinigameCode / required |
| S→C | SC_MINIGAME_START_ACK | 1: result_code / ResultCode / required; 2: minigame_code / enum / optional; 3: session_id / uint32 / optional; 4: puzzle_info / message / optional; 5: catch_gremlin_info / message / optional; 6: aden_info / message / optional |
| C→S | CS_MINIGAME_END_REQ | 1: session_id / uint32 / required; 2: puzzle_result / message / optional; 3: catch_gremlin_result / message / optional; 4: aden_result / message / optional |
| S→C | SC_MINIGAME_END_ACK | 1: result_code / ResultCode / required; 2: minigame_code / enum / optional |
| C→S | CS_MINIGAME_ADEN_PLAY_REQ | 1: session_id / uint32 / required |
| S→C | SC_MINIGAME_ADEN_PLAY_ACK | 1: result_code / ResultCode / required; 2: dice_info / MinigameAdenDiceInfo / optional; 3: user_info / MinigameAdenUserInfo / optional |
| C→S | CS_MINIGAME_ADEN_USE_CARD_REQ | 1: session_id / uint32 / required; 2: id / int32 / required |
| S→C | SC_MINIGAME_ADEN_USE_CARD_ACK | 1: result_code / ResultCode / required; 2: user_info / MinigameAdenUserInfo / optional |

SC_MINIGAME_LIST_ACK.MinigameInfo = {1: minigame_code (MinigameCode, required), 2: next_game_time (uint64, required)}.

### 결과 코드 — 바이너리 원본 enum 수치

| 메시지 | 결과 코드 |
|---|---|
| SC_MINIGAME_START_ACK | SUCCESS=0, FAIL=1, ALREADY_PLAYING=2, FAIL_COOLTIME=3 |
| SC_MINIGAME_END_ACK | SUCCESS=0, FAIL=1, FAIL_TIMEOVER=2, FAIL_RESULT=3 |
| SC_MINIGAME_ADEN_PLAY_ACK | SUCCESS=0, FAIL=1 |
| SC_MINIGAME_ADEN_USE_CARD_ACK | SUCCESS=0, FAIL=1 |

## 3. 공통 동작 흐름 (메시지 구조로 재구성한 예상 흐름)

    CS_MINIGAME_LIST_REQ    → SC_MINIGAME_LIST_ACK
                              (목록 / 다음 가능 시간 / 서버 시간)
    CS_MINIGAME_START_REQ   → SC_MINIGAME_START_ACK
                              (결과 / 세션 ID / 게임별 초기 상태)
       ├─ PUZZLE           → CS_MINIGAME_END_REQ(puzzle_result)
       ├─ CATCH_GREMLIN    → CS_MINIGAME_END_REQ(catch_gremlin_result)
       └─ ADVENTURE_OF_ADEN
          ├─ 반복 요청: CS_MINIGAME_ADEN_PLAY_REQ
          │             → SC_MINIGAME_ADEN_PLAY_ACK(주사위 / 사용자 상태)
          ├─ 카드 사용: CS_MINIGAME_ADEN_USE_CARD_REQ
          │             → SC_MINIGAME_ADEN_USE_CARD_ACK(사용자 상태)
          └─ 종료 요청: CS_MINIGAME_END_REQ(aden_result)
    CS_MINIGAME_END_REQ     → SC_MINIGAME_END_ACK

이 순서는 **프로토콜에서 유도한 구현 가설**이지, 클라이언트 호출 스택을 모두 확인한 확정 실행 순서는 아니다. 특히 퍼즐/그렘린 게임의 입력 도중 추가 메시지가 있는지, 아덴의 모험이 자동 종료되는지는 미확인.

## 4. 게임별 실제 정보 구조

### A. PUZZLE (0) — 카드 배열/결과 제출

**MinigamePuzzleInfo:**

| 번호 | 필드 | 원본 타입·규칙 | 읽을 수 있는 의미 |
|---|---|---|---|
| 1 | board_width | required int32 | 보드 너비 |
| 2 | board_height | required int32 | 보드 높이 |
| 3 | start_time | required uint64 | 시작 시점 |
| 4 | end_time | required uint64 | 종료 시점 |
| 5 | card_index | repeated uint32 | 카드 인덱스 목록 |
| 6 | result_card_key | required bytes | 결과 카드 키 바이트 |

**MinigamePuzzleResult:** 1: card_index (repeated uint32).

해석: 서버가 초기 카드 배열/키를 전달하고, 클라이언트가 인덱스 목록을 종료 결과로 제출하는 형태. **카드 매칭 규칙, result_card_key의 인코딩·용도, 정답 검증식, 보상은 아직 모른다.** 서버 구현 시 클라이언트 제출값만으로 보상을 확정하지 말고 서버가 생성·보관한 세션 정답과 비교해야 한다.

### B. CATCH_GREMLIN (1) — 그렘린 잡기

**MinigameCatchGremlinInfo:**

| 번호 | 필드 | 원본 타입·규칙 |
|---|---|---|
| 1 | board_width | required int32 |
| 2 | board_height | required int32 |
| 3 | start_time | required uint64 |
| 4 | end_time | required uint64 |
| 5 | gremlin_info | required GremlinInfo |

**GremlinInfo(필드 번호는 중첩 메시지 내부 번호):** 1: small_count(required int32), 2: medium_count(required int32), 3: large_count(required int32), 4: bomb_count(required int32), 5: target_point(required int32).

**MinigameCatchGremlinResult:** 1: result_info_list (repeated ResultInfo). 중첩 ResultInfo는 1: index(required int32), 2: resultIndex(required int32)이다. 원문 필드 이름은 resultIndex처럼 대문자 I가 포함된다.

해석: 크기별 그렘린 수, 폭탄 수, 목표 점수로 구성된 보드에서 선택 기록을 제출하는 형태. **점수 배분, 폭탄 처리, index/resultIndex의 좌표·대상 매핑, 시간 제한의 실제 단위, 재화 보상은 미확인.**

### C. ADVENTURE_OF_ADEN (2) — 아덴의 모험(주사위/보드)

**MinigameAdenInfo:**

| 번호 | 필드 | 원본 타입·규칙 |
|---|---|---|
| 1 | board_width | required int32 |
| 2 | board_height | required int32 |
| 3 | start_time | required uint64 |
| 4 | end_time | required uint64 |
| 5 | turn_count | required int32 |
| 6 | user_info | required MinigameAdenUserInfo |
| 7 | magic_card_info_list | repeated MagicCardInfo |
| 8 | box_info_list | repeated BoxInfo |

**MinigameAdenUserInfo:** 1: position(required int32), 2: hp(required int32), 3: mp(required int32), 4: remain_turn_count(required int32).

**MinigameAdenDiceInfo:** 1: dice_list(repeated int32).

**MagicCardInfo:** 1: id(required int32), 2: type(required int32), 3: flag(required int32). 중첩 CardType enum은 아래 값으로 존재하지만 **실제 type 필드는 enum 타입이 아니라 int32**다.

| 카드 코드 | 바이너리 enum 이름 |
|---:|---|
| 1 | HEAL |
| 2 | GREATER_HEAL |
| 3 | BLOOD_TO_SOUL |
| 4 | ION_SKIN |
| 5 | ENCHANT_WEAPON |
| 6 | TRIPPLE_ARROW |
| 7 | HOLY_WALK |
| 8 | TELEPORT |
| 9 | IMMUNE_TO_HARM |
| 10 | FULL_HEAL |

**BoxInfo:** 1: box_index(required int32), 2: type(required int32). 역시 type은 enum이 아니라 int32이고, 중첩 BoxType 목록은 아래와 같다.

| 상자/타일 코드 | 이름 | 해당 하위 필드 (BoxInfo 필드 번호) |
|---:|---|---|
| 1 | BLANK | 없음 |
| 2 | MOVE | 3: move_box (optional MoveBox → required int32 distance=1) |
| 3 | MONSTER | 4: monster_box (optional MonsterBox → required int32 id=1, level=2, power=3, dice_power=4) |
| 4 | LUCKY_BAG | 5: luckky_bag_box (optional LuckyBagBox → required int32 id=1) |
| 5 | PORTAL | 6: potal_box (optional PortalBox → required int32 target_box_index=1) |
| 6 | TRAP | 7: trap_box (optional TrapBox → optional int32 damage=1) |
| 7 | HEAL | 8: heal_box (optional HealBox → optional int32 hp=1) |
| 8 | JAIL | 9: jail_box (optional JailBox → required int32 turn_count=1, escape_dice_power=2) |

**MinigameAdenResult:** 메시지는 존재하지만 필드가 **0개인 빈 메시지**다.

아덴 게임의 반복 입력·출력은 CS_MINIGAME_ADEN_PLAY_REQ(session_id) → SC_MINIGAME_ADEN_PLAY_ACK(result_code, dice_info, user_info), 카드 사용은 CS_MINIGAME_ADEN_USE_CARD_REQ(session_id, id) → SC_MINIGAME_ADEN_USE_CARD_ACK(result_code, user_info)로 표현된다.

**주의:** 원본 철자 ION_SKIN, TRIPPLE_ARROW, luckky_bag_box, potal_box를 임의로 고치지 않는다. 호환용 메시지를 만들면 필드 번호와 wire type이 우선이고, 복원 스키마와의 1:1 추적을 위해 이름도 그대로 유지한다.

**미확인:** 주사위 범위·턴 종료 규칙·HP/MP 변화 공식·몬스터 대결식·함정 데미지 산식·카드 MP 비용·보상·카드 획득 로직 등. 아래 enum 값만으로 게임의 확정 규칙을 만들어서는 안 된다.

## 5. 기존 RKern 소스와 대조 (2026-11-22 GitHub main 기준)

| 위치 | 현재 상태 | 향후 적용 시 의미 |
|---|---|---|
| rkern/docs/MainServerProto/schemas/MainServer_Client.proto | **미니게임 3종의 메시지/enum 원본 추출 스키마 이미 존재** | 이미 있는 파일을 재추출·재정의하지 말고 원본 계약으로 사용 |
| rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/ProtoMsg.java | **미니게임 메시지의 숫자 ID 선언 없음** | 실제 패킷 캡처/클라이언트 디스패치 역추적 후에만 ID 추가 |
| rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/CSProto.java | **미니게임 요청 핸들러 등록 없음** | ID 확인 후 등록; 파싱/동시성 규칙은 기존 CIO 경로 유지 |
| rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/Dispatch/CS_ExtendedProto.java | 기존 확장 프로토 프레임 분리 경로 존재 | 새 공통 네트워크 코어 생성 없이 기존 경로 재사용 |
| rkern/src/l1j/server/IndunSystem/MiniGame/L1Gambling.java | 주사위 딜러 홀짝·숫자 베팅 | **아덴의 모험과 다른 기능**, 그대로 분리 |
| rkern/src/l1j/server/IndunSystem/MiniGame/L1Gambling3.java | 몬스터 종류 선택 베팅 | **그렘린 잡기와 다른 기능**, 그대로 분리 |

이번 작업에서는 **서버 Java, DB, 기존 .proto 파일 어느 것도 수정하지 않았다.**

## 6. 추후 구현 순서 (미확인 정보에 의존하지 않는 작업부터)

1. **와이어 ID 확정:** 10종 메시지의 실제 숫자 ID를 클라이언트 수신/송신 등록부 또는 정상 패킷 캡처에서 확보. CS_MINIGAME_NOTI_REQ와 관계도 분리 확인. ID를 임의 배정하지 않는다.
2. **UI 활성 여부 확인:** 퍼즐/그렘린/아덴 화면 리소스, 버튼이 실제 존재하고 요청을 보내는지 확인. 스키마가 있다고 UI가 실행된다고 단정하지 않는다.
3. **공통 LIST/START/END 계약 구현:** 기존 ProtoReader/ProtoWriter, CIO 소유 실행 구조 사용. 세션별 게임 종류·소유 캐릭터·시작/종료 시각·중복 실행·쿨타임 관리.
4. **퍼즐·그렘린:** 보드 초기값과 정답은 서버 보유. 종료 응답의 반복 인덱스 범위, 중복, 정답, 기한을 검증.
5. **아덴의 모험:** 서버가 위치·HP/MP·남은 턴·카드 소지·주사위 상태를 소유. PLAY/USE_CARD 요청은 session_id를 기준으로 상태 전이와 재전송 중복을 방어.
6. **완료·보상 안전성:** 성공/시간 초과/오답/중복 결과를 구별하고 보상 지급 시 한 번만 확정. 경제성 아이템/재화는 기존 CacheD/인벤토리 트랜잭션 경로로 원자적 처리, COMMIT 후 결과 표시.
7. **회귀 검증:** 3종 정상 플로우, 다른 캐릭터 세션 ID 사용, stale session, 시간 경계, 중복 END/PLAY/USE_CARD, 패킷 순서 역전, 재접속, 수신 오류, 보상 DB 실패/재시도 검증.

### 구현 시 선행 확인 항목 (지금은 미확인)

- 모든 메시지의 정확한 숫자 ID 및 클라이언트 UI 수신 분기
- uint64 시간 필드의 단위와 기준 시각
- 퍼즐 result_card_key의 정확한 형식·정답 검증 알고리즘
- 그렘린 결과 index/resultIndex의 값 범위와 점수 계산식
- 아덴 보드 배치, 주사위 분포, 카드/타일의 효과 규칙
- 게임 보상표/쿨타임 규칙/기존 계정 제한
- 종료 및 보상 패킷이 여기의 ACK 외에 존재하는지 여부

## 7. 검증 결과 및 한계

- **확인 완료:** 실제 실행 이미지에서 **18개 DescriptorProto**(요청/응답 10개, 정보/결과 8개)를 직접 파싱했다. MinigameCode enum 0/1/2, 중첩 카드·상자 enum, 각 메시지의 field number/type/label 확인.
- **교차 검증:** GitHub의 기존 복원된 MainServer_Client.proto 1855~2054행과 이름·필드 번호·타입·규칙이 일치함.
- **미검증:** 클라이언트 UI에서 10종 패킷이 모두 실제로 송수신된다는 사실, 메시지 ID, 원본 게임 플레이와 보상 정책, 전체 연동 실행.
- **불변 조건:** 원본 스키마의 필드 타입과 번호, required/optional/repeated, 중첩 enum 수치 및 오탈자(호환 계약)는 유지한다.

**핵심 결론:** 미니게임 3종의 **데이터 계약은 실제 바이너리에서 확인·복원되어 있다.** 다만 그 계약만으로 클라이언트의 UI와 원본 게임 규칙이 확보된 것은 아니다. 구현 전에 숫자 메시지 ID와 판정·보상 규칙을 추가 확인해야 한다.
