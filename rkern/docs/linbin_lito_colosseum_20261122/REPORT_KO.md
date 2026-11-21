# mjlin LitoBattle / ArenaCo · 리니지 콜로세움 복원 종합 보고서

> 작성일: 2026-11-22  
> 목적: 향후 RKern/L1J에 리토·콜로세움 계열 대전 콘텐츠를 구현하기 위한 **원본 바이너리/프로토콜 기준서**  
> 범위: 분석 문서만 신규 작성. **서버 소스, DB, 기존 protobuf 스키마는 수정하지 않음.**

## 0. 한눈에 보는 결론

복호화된 린빈에는 **Lito 전용 UI 이름/시스템 객체 이름**과 **ArenaCo 대전 프로토콜의 상세 데이터 형식**이 함께 존재한다. 방 목록·생성·입장·팀 변경·READY·초대·경기 시작·라운드·제한시간·3초/10초 카운트다운·실시간 점수·승패·MVP·관전·응원·맵 선택까지 구현에 필요한 **패킷 구조 수준의 단서가 풍부하다.**

- **실제 확인:** UI/타이머/점수판 관련 문자열, ArenaCo protobuf 스키마의 구체적 필드·enum, 타이밍/상태/결과 패킷.
- **아직 미확인:** 각 메시지의 **실제 숫자 패킷 ID**, Lito UI의 실제 수신 핸들러·생성/오픈 호출 경로, **LitoBattle ↔ ArenaCo 간 직접 연결**, 2016 콜로세움 화면과 바이너리 UI의 완전한 버전 일치, 원본 서버의 승패 판정·보상 규칙, 실게임에서 UI 활성화 여부.
- 따라서 **복원 가능성을 판단할 기반은 충분하지만, 현재 자료만으로 패킷 한 개를 보내면 UI가 바로 열린다고 확정할 수는 없다.** 패킷 번호 및 UI 핸들러를 먼저 연결해야 한다.
- 과거 리니지 토너먼트(리토) 계열의 명칭과 2016년 콜로세움 계열 ArenaCo를 관련 **분석 후보**로 취급한다. 2005년과 2016년 화면을 동일하다고 단정하지 않는다.

## 1. 분석 원천 · 검증 등급

### 1.1 실제 바이너리

- 입력: 복호화/적재 실행 이미지 **mjlin_loaded_code_00400000_0180D000.bin** (동일 내용의 로컬 파일 mjlin_loaded.bin)
- 크기: **21,024,768바이트**
- SHA-256: **256f7886e98d7d2c573ab76917f78e30dc4f34cb723a002d604602cdb2f6cbdb**
- 이미지 기준 가상 주소는 시작 VA **0x00400000 + 파일 오프셋**으로 표시.
- 실행 이미지를 직접 검색해 UI 명칭·이벤트/프로토콜 명칭의 문자열 오프셋을 재검증했다.
- protobuf 정의의 상세 필드는 이 실행 이미지의 **내장 FileDescriptorProto에서 추출된 저장소 복원본**과 대조했다. 이 파일들은 **NC가 배포한 .proto 소스가 아니라 복원한 것**이다.

### 1.2 GitHub 저장소의 기존 복원 자료

1. [MainServer_Client_ArenaCo.proto](../MainServerProto/schemas/MainServer_Client_ArenaCo.proto) — ArenaCo 요청/응답, 라운드·경기 진행, 관전자, 결과 패킷.
2. [Lineage_CommonDataTypes.proto](../MainServerProto/schemas/Lineage_CommonDataTypes.proto) — ArenaBuildInfo, ArenaRoomInfo, ArenaUserInfo, ArenaResult, 맵·팀·대전방식 enum.
3. [MainServer_Client.proto](../MainServerProto/schemas/MainServer_Client.proto) — SC_BATTLE_ARENA_AUTO_MATCH_REQ, CS_BATTLE_ARENA_AUTO_MATCH_ACK, SC_BATTLE_ARENA_AUTO_MATCH_RESULT_NOTI 등의 부가 프로토콜.

### 1.3 증거 등급

| 등급 | 의미 |
|---|---|
| **확인** | 바이너리 문자열/스키마에 직접 존재하거나 현재 RKern 소스에서 확인 |
| **해석** | 필드 의미상 합리적으로 도출되는 동작; 실제 함수 실행 순서는 미확인 |
| **미확인** | 추후 함수 XREF, 실제 패킷 캡처, 클라이언트 실행으로 검증 필요 |

**주의:** protobuf의 필드 번호(예: 1, 2, 3)는 네트워크 **메시지 ID/opcode가 아니다.** 패킷 명칭만으로 wire ID를 추측해 구현하지 않는다.

## 2. Lito UI·클라이언트 실행 이미지 흔적

아래 주소는 이번에 **실행 이미지에서 해당 ASCII 문자열의 존재를 직접 재검증한 VA**이다. RTTI/리소스 구분은 문자열 출현 문맥에 따른 분류이므로, 주소를 생성자·수신 핸들러 주소로 오인하지 않는다.

| 문자열 | 확인 VA | 예상 역할 / 상태 |
|---|---|---|
| LitoBattleSystem | 0x01574BAC | 리토 대전 시스템 계열 타입 이름; **확인** |
| LitoBattleUI | 0x012B7A48 | 전투 UI 리소스/객체 이름; **확인** |
| LitoRoomUI | 0x012B7A3C | 대기실·방 UI 이름; **확인** |
| LitoMainMenu | 0x012B7A10 | 메인 메뉴 요소; **확인** |
| LitoLobby | 0x012B7A20 | 로비 요소; **확인** |
| LitoResultUI | 0x012B7A2C | 결과 화면 요소; **확인** |
| LitoTimer | 0x011B8030 | 타이머 리소스/요소; **확인** |
| LitoWarningTimer | 0x011B804C | 시간 경고용 타이머 요소; **확인** |
| GameCountDown | 0x011953DF | 경기 카운트다운 요소; **확인** |
| ScoreBoard | 0x01194F41 | 점수판 요소; **확인** |
| TeamAScore | 0x011BC338 | A팀 점수 요소; **확인** |
| TeamBScore | 0x011BC344 | B팀 점수 요소; **확인** |
| LitoMapRoulette | 0x011BC31C | 맵 선택/룰렛 요소; **확인** |
| GameStartHandler | 0x01579BA8 | 경기 시작 핸들러 타입명; **확인** |
| BattleCountDownHandler | 0x01579BD4 | 카운트다운 핸들러 타입명; **확인** |
| SC_ARENA_GAME_INFO_NOTI | 0x0119510A | ArenaCo 경기 정보 패킷명; **확인** |
| SC_ARENA_PLAY_EVENT_NOTI | 0x011952DA | 경기 이벤트 패킷명; **확인** |
| SC_SCRIPT_PRESENTATION_NOTI | 0x01195ADD | 연출 패킷명; **확인** |

**의미:** 랭킹/일반 PvP와 별개의 Lito 로비·방·전투·결과·타이머 UI 요소들이 실제 실행 이미지에 있다. **단, LitoBattleUI가 SC_ARENA_* 수신 경로로 열리는지까지 확인된 것은 아니다.**

## 3. 서버가 보내는 주요 대전 패킷

출처: MainServer_Client_ArenaCo.proto. 아래의 필드명·타입·필드 번호는 복원 스키마 그대로 사용한다.

### 3.1 경기 초기 정보 / 시간

**SC_ARENA_GAME_INFO_NOTI**

| 필드 번호 | 필드 | 타입 | 의미 |
|---:|---|---|---|
| 1 | player_info | repeated ArenaActorInfo | 참가자 정보 |
| 2 | map_kind | required eArenaMapKind | 맵 종류 |
| 3 | round | required uint32 | 라운드 값 |
| 4 | round_time_sec | required uint32 | **라운드 제한시간(초)** |
| 5 | result_display_time_sec | required uint32 | **결과 표시시간(초)** |

**SC_ARENA_PLAY_STATUS_NOTI**

- 1: player_status (repeated ArenaPlayerStatus)
- 2: game_status (optional ArenaGameStatus)

**ArenaGameStatus**

| 번호 | 필드 | 타입 | 의미 |
|---:|---|---|---|
| 1 | play_time_msec | optional uint32 | **경기 진행시간(밀리초)** |
| 2 | is_timer_run | optional bool | 타이머 실행 여부 |
| 3 | observer_count | optional uint32 | 관전자 수 |
| 4 | round_score_board | optional RoundScoreBoard | 라운드 점수판 |
| 5 | team_status | repeated TeamStatus | 팀별 상태 |

**TeamStatus:** 1: team_id(required eArenaTeam), 2: cheer_msg_count(optional uint32), 3: team_kill_count(optional uint32).

**RoundScoreBoard:** 1: team_a(repeated RoundScore), 2: team_b(repeated RoundScore).  
**RoundScore:** 1: is_win(required bool), 2: attack_amount(optional uint32).

**SC_ARENA_PLAY_RESULT_NOTI**

- 1: result (required ArenaResult) — 경기 결과
- 2: remain_time_sec (required int32) — **종료/결과 시 남은 시간(초)**
- 3: reason (required eReason) — 이유 코드: None=1, AttackAmount=2

### 3.2 경기 이벤트와 카운트다운

**SC_ARENA_PLAY_EVENT_NOTI.eType** 값은 다음과 같이 복원된다.

| 값 | 원문 | 의미 |
|---:|---|---|
| 1 | GameForfeited | 경기 몰수/포기 |
| **2** | **GameCountDown3Sec** | **3초 카운트다운 이벤트** |
| **3** | **GameCountDown10Sec** | **10초 카운트다운 이벤트** |
| 11 | PlayerEntered | 참가자 입장 |
| 12 | PlayerOut | 참가자 퇴장 |
| 13 | PlayerDead | 참가자 사망 |
| 14 | PlayerSpellSucceded | 스킬 성공 이벤트 |
| 21 | YourSelfEntered | 자기 캐릭터 입장 |
| 31 | BattleHunterWin | 배틀헌터 승리 이벤트 |
| 41 | TeamTowerBroke | 팀 타워 파괴 |

메시지 본문: 1 type(required), 10 actor_info(optional ArenaActorInfo), 20 arena_char_id_target(optional int64), 21 arena_char_id_causer(optional int64), 30 team_id(optional eArenaTeam), 40 spell_id(optional uint32).

**판단:** 단순히 시간을 client-local 타이머로 세는 UI가 아니라 **서버가 시간/진행 상태와 카운트다운 이벤트를 전달할 수 있는 명시적인 패킷 구조**가 있다. 화면이 3→2→1로 실제 애니메이션 재생되는 호출 경로는 별도 확인 대상이다.

### 3.3 실시간 선수 상태와 전투 기록

**ArenaPlayerStatus**

| 필드 | 필드 번호 | 타입 |
|---|---:|---|
| arena_char_id | 1 | required int64 |
| kill_count / death_count | 2 / 3 | optional uint32 |
| hp_ratio / mp_ratio | 4 / 5 | optional uint32 |
| loc_x / loc_y | 6 / 7 | optional int32 |
| attack_amount / damaged_amount / heal_amount | 8 / 9 / 10 | optional uint32 |
| poisoned / paralysed / is_live | 11 / 12 / 13 | optional bool |
| obj_id / is_ready | 14 / 15 | optional uint32 / bool |

**ArenaActorInfo**는 arena_char_id, 역할(Player/Observer/BuilderCaster), server_id, character_name, 직업, 성별, 팀, marker_id를 담는다.

**SC_ARENA_OBJECT_STATUS_NOTI**는 Tower 배열을 담는다. Tower = obj_id(required uint32), team_id(optional eArenaTeam), hp_ratio(optional int32).  
즉 **탑 파괴 경기와 같은 오브젝트 기반 규칙**을 표현할 구조도 존재한다.

### 3.4 결과·MVP·시합 기록

**ArenaResult**: 1 team_result(repeated ArenaTeamResult), 2 gamble(required eArenaGambleType).

**ArenaTeamResult**

- 1: team_id(required eArenaTeam)
- 2: outcome(required eOutcome) — DRAW=0, VICTORY=1, DEFEAT=2
- 3: cheeringmessage_count(required uint32)
- 4: user_result(repeated ArenaUserResult)
- 5: kill_count(required uint32)

**ArenaUserResult**

- 1 arena_char_id(required int64)
- 2 grade(required eArenaResultGrade) — MVP=1, BEST_ATTACKER=2, BEST_DEFENDER=3, BEST_HEALER=4, BEST_POINTER=5, NONE=6
- 3 kill(required uint32)
- 4 attack(required uint32)
- 5 defence(required uint32)
- 6 heal(required uint32)

**ArenaRoomInfo.ArenaRecord**는 solo/team 구분, total/win/draw/lose/win_rate를 담으므로 개인 전적/방 전적 UI도 구성할 수 있다. **전적 기록 DB 테이블과 집계 주기는 미확인**이다.

## 4. 로비·방 운영 프로토콜 전체 분류

출처: MainServer_Client_ArenaCo.proto. 아래는 **메시지 존재 여부 확인**이며 숫자 msgId는 아직 확인되지 않았다.

| 단계 | C→S 요청 | S→C 응답/통지 | 구현할 수 있는 기능 |
|---|---|---|---|
| 서버 상태 | — | SC_ARENACO_SERVER_STATUS_NOTI | 운영 상태·닫힘 알림 |
| 내 기록 | CS_ARENACO_BYPASS_MY_INFO_REQ | SC_ARENACO_BYPASS_MY_INFO_ACK | 전적 조회 |
| 방 목록 | CS_ARENACO_BYPASS_ROOM_LIST_REQ | SC_ARENACO_BYPASS_ROOM_LIST_ACK | 페이지·전체 페이지·방 목록 |
| 방 상세 | CS_ARENACO_BYPASS_ROOM_INFO_REQ | SC_ARENACO_BYPASS_ROOM_INFO_ACK | 특정 방 조회 |
| 방 생성 | CS_ARENACO_CREATE_ROOM_REQ | SC_ARENACO_CREATE_ROOM_ACK | 조건/맵/제목 설정 |
| 방 설정 변경 | CS_ARENACO_BYPASS_CHANGE_ROOM_PROPERTY_REQ | SC_ARENACO_BYPASS_CHANGE_ROOM_PROPERTY_ACK | 제목·비밀번호·맵 |
| 방 상태 갱신 | — | SC_ARENACO_BYPASS_CHANGE_ROOM_STATUS_NOTI | 관전자/선수/방 메타 |
| 방 입장 | CS_ARENACO_ENTER_ROOM_REQ | SC_ARENACO_ENTER_ROOM_ACK, SC_ARENACO_BYPASS_ENTER_ROOM_NOTI | 플레이어·관전자·방송자 입장 |
| 팀 변경 | CS_ARENACO_BYPASS_CHANGE_TEAM_REQ | SC_ARENACO_BYPASS_CHANGE_TEAM_ACK/NOTI | 팀 배정 |
| 준비 | CS_ARENACO_BYPASS_CHANGE_READY_ENTER_REQ | SC_ARENACO_BYPASS_CHANGE_READY_ENTER_ACK/NOTI | READY 관리 |
| 방 잠금 | CS_ARENACO_BYPASS_CHANGE_LOCK_ROOM_REQ | SC_ARENACO_BYPASS_CHANGE_LOCK_ROOM_ACK/NOTI | 잠금/해제 |
| 경기 시작 | CS_ARENACO_BYPASS_GAME_START_REQ | SC_ARENACO_BYPASS_GAME_START_ACK | 방장의 시작 요청 |
| 방 채팅 | CS_ARENACO_BYPASS_ROOM_CHAT_REQ | SC_ARENACO_BYPASS_ROOM_CHAT_NOTI | 대기실 채팅 |
| 방 퇴장 | CS_ARENACO_EXIT_ROOM_REQ | SC_ARENACO_EXIT_ROOM_ACK, SC_ARENACO_BYPASS_EXIT_ROOM_NOTI | 퇴장/인원 갱신 |
| 초대 | CS_ARENACO_INVITE_ROOM_REQ | SC_ARENACO_INVITE_ROOM_ACK/NOTI | 방 초대 |
| 초대 거절 | CS_ARENACO_INVITE_ROOM_DENY_REQ | SC_ARENACO_INVITE_ROOM_DENY_NOTI | 초대 거절 |
| 강퇴 | CS_ARENACO_BYPASS_KICK_REQ | SC_ARENACO_BYPASS_KICK_ACK/NOTI | 대상 강퇴 |

기존 proto 스키마에 제목·비밀번호·참가 조건·요금·맵·서버 제한이 세부 필드로 존재한다. **별도 게임 모드별 실제 허용 조건은 서버 원본 규칙을 확인해야 한다.**

### 4.1 방 생성·방 정보 세부 필드

**ArenaBuildInfo**

| 번호 | 이름 | 타입/의미 |
|---:|---|---|
| 1 | title | required bytes, 방 제목 |
| 2 | team_building_type | required eArenaTeamBuildingType |
| 3 / 4 | min_level / max_level | required uint32 |
| 5 | allow_class | repeated CharacterClass |
| 6 | gamble | required eArenaGambleType |
| 7 | closed | required bool |
| 8 | password | optional string |
| 9 | observe | required bool |
| 10 | map_kind | required eArenaMapKind |
| 11 | allow_all_server | required bool |
| 12 | allow_server | repeated uint32 |

**ArenaRoomInfo**: 1 base(required Base), 2 extend(optional Extend).

Base에는 room_id, title, member_count_cur/max/min, map_kind, closed, observe, gamble, team_building_type, game, is_playing(optional), is_locked(optional)이 있다. Extend에는 **member(repeated MemberInfo)**가 있고, MemberInfo에는 유저 정보와 전적 배열이 들어 있다.

**ArenaUserInfo**: arena_char_id, server_id, character_name, character_class, gender, team_id, role, room_owner, ready, in_room, order(optional), enter_count(optional).

### 4.2 방 생성 및 참가 실패 코드 예시

복원 스키마에는 단순 성공·실패 외에 실제 조건별 결과 enum이 존재한다.

- SC_ARENACO_CREATE_ROOM_ACK: SUCCESS=1; EXCEED_MAX_ROOM_COUNT=4, EXCEED_MAX_ARENA_COUNT=5, NOT_ENOUGH_MONEY=7, INVALID_TITLE=8, PASSWORD=9, TEAM_COUNT=10, TEAM_MEMBER_COUNT=11, ALLOCATE_ARENA=12, OTHER_ROOM_ENTERED=14, CLASS=15, SERVER=16, GAMBLETYPE=17, LEVEL=18, NOT_OPEN_TIME=19 등이 있다.
- SC_ARENACO_ENTER_ROOM_ACK: SUCCESS=1; FAIL_TO_ALREADY_PLAY=3, NOT_ENOUGH_MONEY=4, CANNOT_OBSERVE=5, INVALID_PASSWORD=6, INVALID_ROOM_STATUS=7, NOT_FOUND_ROOM=8, FULL=9, ALREADY_ENTERED_PLAYER=10, OBSERVER=11, INVALID_ROLE=12, OTHER_ROOM_ENTERED=13, CLASS=14, SERVER=15, GAMBLETYPE=16, LEVEL=17, CASTER=18, NOT_OPEN_TIME=19 등이 있다.
- SC_ARENACO_BYPASS_GAME_START_ACK: SUCCESS=1; INVALID_USER=3, INVALID_ROOM=4, INVALID_OWNER=5, INVALID_STATE=6, ISSUE_PLAY=7, NOT_ENOUGH_MONEY=8, GAMBLETYPE=9, LEVEL=10, NOT_OPEN_TIME=11 등이 있다.

정확한 enum 접두어·번호는 [원본 복원 스키마](../MainServerProto/schemas/MainServer_Client_ArenaCo.proto)를 최종 기준으로 사용한다.

## 5. 맵 선택 / 라운드 / 경기 운영

### 5.1 맵 종류

Lineage_CommonDataTypes.proto의 **eArenaMapKind**에서:

- LastShowdown=1
- ArenaMatch=11, WarSquare=12, WarSquare2=13
- BattleHunter=14
- ArenaMatch_LFC=15
- WarSquare_LFC=16, WarSquare_LFC1~5=17~21
- BreakingTower=22, BreakingTower_LFC=23, LastShowdown_LFC=24
- OrimLab_Minor/Normal/Major=201/202/203 (인스턴스 던전 범주)

**주의:** 같은 enum 안에 서로 다른 콘텐츠 범주의 맵도 들어 있다. 위 목록 전부가 '리토배틀에서 선택 가능'하다는 뜻은 아니다.

### 5.2 맵 룰렛 / 연출

- 실행 이미지에 **LitoMapRoulette** 문자열 존재.
- **SC_SCRIPT_PRESENTATION_NOTI**: 1 type(required eType, ArenaMapSelect=1), 2 args(repeated int32).
- 이 조합은 **경기 맵 선택 연출**을 구현할 때 핵심 후보이다.
- 단, **SC_SCRIPT_PRESENTATION_NOTI → LitoMapRoulette 직접 호출 XREF는 미확인**이며 args 값 의미·순서도 미확인이다.

### 5.3 게임 진행의 예상 상태 전이 (정확한 실행 순서는 추가 확인)

    방 목록 → 방 생성 또는 입장 → 팀 선택 → READY
       → 방장 게임 시작 요청 → 참가자/맵/라운드 정보 통지
       → 10초/3초 카운트다운 이벤트 → 타이머 실행
       → 플레이어/오브젝트/점수판 상태 통지
       → 라운드 점수 갱신 → 게임 결과/개인 등급 통지
       → 결과 시간 경과 후 로비 복귀 또는 퇴장

이 흐름은 **메시지 의미를 연결한 구현 가설**이다. 정확한 로비 전환·망 분리·서버 이동·라운드 간 리셋 조건은 아직 확정되지 않았다.

## 6. 팀·관전자·응원·자동 매칭

### 6.1 팀 / 역할

- eArenaTeam: TEAM_NONE=0, TEAM_A=1, TEAM_B=2, TEAM_C=3, NEUTRAL_PASSIVE=-2, NEUTRAL_HOSTILE=-1.
- eArenaTeamBuildingType: TEAM_BUILDING_NORMAL=1, TEAM_BUILDING_MATCH=2, TEAM_BUILDING_BROADCAST=3.
- ArenaUserInfo.eRole: Player=1, Observer=2, BuilderCaster=3.

**해석:** 팀 A/B 기본 구성 외에 C팀·중립 상태·방송자 역할까지 표현할 수 있다. 특정 모드에서 실제로 몇 팀까지 허용하는지는 미확인.

### 6.2 관전자

- **CS_ARENA_OBSERVER_TELEPORT_REQ**: player_arena_char_id(optional int64), x(optional int32), y(optional int32).
- **SC_ARENA_OBSERVER_TELEPORT_ACK**: SUCCESS=1, TARGET_PLAYER_NOT_EXISTED=2, TARGET_PLAYER_LEAVE_GAME=3, TARGET_PLAYER_BECAME_OBSERVER=4, CANT_GO_INVALID_LOCATION=5, INVALID_REQUEST=6.
- 경기 상태의 observer_count 및 방 입장 role=Observer가 존재.

이는 단순 관전 아이콘이 아니라 **관전 대상 이동을 서버에 요청하는 구조**다. 관전자 위치·가시성·전투 개입 차단의 실제 서버 규칙은 별도 구현 필요.

### 6.3 응원·채팅

- CS_ARENA_CHEER_REQ(to_team_id, text_msg)
- SC_ARENA_CHEER_ACK(result)
- SC_ARENA_CHEER_NOTI(team_id, cheers, total_count)
- CS_ARENA_CHEER_LIST_REQ(team_id) / SC_ARENA_CHEER_LIST_NOTI(team_id, cheers, begin, end)
- CS_ARENA_CHAT_BLOCK_REQ(arena_char_id, is_blocked) / SC_ARENA_CHAT_BLOCK_ACK
- ArenaCheer: server_id, sender, character_class, msg, received_time; enum에 MAX_MSG_BYTE_LENGTH=50 및 MAX_CHEER_COUNT=10000 존재.

### 6.4 자동 매칭 (ArenaCo와 별도 스키마)

MainServer_Client.proto에 다음 메시지가 존재한다.

| 메시지 | 필드 |
|---|---|
| SC_BATTLE_ARENA_AUTO_MATCH_REQ | arena_id(required int32) |
| CS_BATTLE_ARENA_AUTO_MATCH_ACK | arena_id(required int32), 하위 필드는 원본 스키마 확인 |
| SC_BATTLE_ARENA_AUTO_MATCH_RESULT_NOTI | arena_id(required int32), result(required enum) |

결과 enum: MATCH_SUCCEED=0, OTHER_REJECT=1, ERROR=2.

**주의:** 본 자동 매칭 3종은 일반 ArenaCo 로비 메시지와 **같은 서버 로직에서 실제 결합되는지 미확인**이다. 메시지 방향은 이름 그대로 SC(서버→클라이언트), CS(클라이언트→서버)이다.

## 7. 현재 RKern과 대조 (GitHub main, 2026-11-22)

| 파일/영역 | 현 상태 | 추후 구현 시 |
|---|---|---|
| rkern/docs/MainServerProto/schemas/MainServer_Client_ArenaCo.proto | ArenaCo 전체 프로토콜 **복원 스키마 존재** | 이 파일을 계약 기준으로 사용 |
| rkern/docs/MainServerProto/schemas/Lineage_CommonDataTypes.proto | 맵/역할/경기 결과 공통 데이터 구조 존재 | 타입·enum 수치 보존 |
| rkern/docs/MainServerProto/schemas/MainServer_Client.proto | 자동 매칭 부가 메시지 존재 | ArenaCo와 별개 검증 |
| rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/ProtoMsg.java | **ArenaCo 주요 패킷 숫자 ID 선언 없음** | 역분석으로 값 확보 후 등록 |
| rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/CSProto.java | **일반 ArenaCo 방/경기 패킷 핸들러 없음** | 실제 요청 ID 확정 후 기존 라우터에 연결 |
| CSProto.java의 GludioDungeonPackets 등록 | **CS_ARENACO_*_INDUN_* 이름의 오림/글루디오 인던 경로는 있음** | 일반 리토/콜로세움 방 시스템으로 혼동 금지 |
| rkern/src/.../MJInstanceSystem/MJLFC/... | 별도 LFC 관련 코드 일부 존재 | 명칭이 비슷하다고 이 대전 프로토콜 구현이 완료됐다고 판단하지 않음 |

**현재 검증된 범위에서 기존 서버에는 이 ArenaCo 전체 기능의 수신 핸들러/경기 운영 구현이 확인되지 않았다.** 이 문서를 추가하는 것만으로 대전 콘텐츠가 작동하지 않는다.

## 8. 후속 구현 계획 — 기존 RKern 실행 구조를 활용

**사전 조건:** 실제 패킷 메시지 ID 확인, Lito UI 경로 검증, 사용할 경기 맵과 원본 게임 규칙/보상 규정 결정.

1. **클라이언트 호출 경로 역분석**: SC_ARENA_GAME_INFO_NOTI, SC_ARENA_PLAY_EVENT_NOTI, SC_SCRIPT_PRESENTATION_NOTI의 숫자 ID 및 디스패처 → Lito UI 연결 확인. UI 오픈 함수가 실제 존재하는지 증명.
2. **방 단계 우선 구현**: LIST / ROOM_INFO / CREATE / ENTER / CHANGE_TEAM / READY / START / EXIT의 wire 규약 확보. 방 상태 전이와 중복 요청/입장/퇴장 처리.
3. **경기 타이밍**: 서버 소유의 라운드 시작·종료 시각과 기존 **CIO Timing Wheel**을 사용. 매 프레임 개별 타이머·새 워커·중복 큐를 불필요하게 만들지 않고 **SC_ARENA_GAME_INFO_NOTI**, **SC_ARENA_PLAY_STATUS_NOTI**, **SC_ARENA_PLAY_EVENT_NOTI**로 표시 동기화.
4. **라운드·전투**: 기존 CIO 소유 샤드의 공격/스킬/사망 결과를 ArenaPlayerStatus 및 RoundScoreBoard에 반영. 다른 스레드가 같은 상태를 동시에 수정하지 않도록 소유 실행 순서 준수.
5. **관전/응원**: 참가자와 관전자 상태·시야·텔레포트 권한 분리. 응원 목록 및 채팅 범위 관리.
6. **결과·전적**: ArenaResult, ArenaTeamResult, ArenaUserResult 메시지 생성. 종료를 한 번만 확정해 중복 승패/보상을 방지.
7. **입장 요금·보상**: 실제 규칙이 검증된 경우에만 활성화. 아데나 등 경제성 자산은 기존 **CacheD/인벤토리 트랜잭션**으로 원자 처리. 결제·보상 커밋 전 성공 UI 송신 금지.
8. **부하 검증**: 방 대량 생성, 다중 관전자, 같은 팀 동시 공격, 10초/3초 경계, 타임아웃, 강제 종료, 중복 패킷, 재접속/인터서버 전환, 퇴장과 경기 종료 경합, 보상 중복/유실을 검증.

성능 원칙: 기존 네트워크 핵심부를 건드리지 않고 기존 패킷 프레임·CIO owner·타이밍 휠·브로드캐스트 경로를 재활용한다. 핫패스에서 불필요한 객체/컬렉션 생성은 최소화하고, 경기/방 상태는 최대한 재사용 가능한 구조로 둔다. 이는 **향후 구현 제안**이지 바이너리에서 확정된 서버 아키텍처가 아니다.

## 9. 실제 구현 전 반드시 확인할 것

| 확인 과제 | 현재 |
|---|---|
| ArenaCo 요청·응답/통지의 **실제 16비트 또는 기타 숫자 메시지 ID** | 미확인 |
| LitoBattleUI/LitoRoomUI와 SC_ARENA_* 수신 함수의 직접 호출/XREF | 미확인 |
| 클라이언트 UI 리소스 파일 및 실제 화면 표시 가능 여부 | 미확인 |
| 2016 콜로세움과 본 바이너리 Lito 클래스의 실제 1:1 매핑 | 미확인 |
| 10초/3초 이벤트가 발생하는 정확한 타이밍 및 화면 출력 | 이벤트 enum은 확인, 실제 동작 미확인 |
| 플레이 시간 기준 시점·상태 통지 주기·타이머 pause/resume | 필드는 확인, 정책 미확인 |
| 라운드 수/맵별 규칙, Death Match / 팀전 / 탑 파괴 등 세부 조건 | 미확인 |
| 자동 매칭 메시지와 ArenaCo 방 관리의 실제 결합 | 미확인 |
| 경기 입장 조건·과금·보상·승패 판정·재접속 처리 | 미확인 |

**검증 완료:** 실행 이미지의 주요 Lito UI 문자열·VA 및 SHA-256, GitHub 복원 스키마의 패킷 필드/enum, RKern의 ProtoMsg/CSProto 현황.  
**검증 미실시:** 실제 클라이언트 프로세스에서 UI 호출, 라이브 패킷 캡처, 원본 서버 게임 진행, Java 통합 빌드, 운영 부하.

## 10. 핵심 원본 정의 빠른 참조

| 목적 | 주로 확인할 원본 메시지 |
|---|---|
| 방 생성/조회 | CS_ARENACO_CREATE_ROOM_REQ, CS_ARENACO_BYPASS_ROOM_LIST_REQ, SC_ARENACO_BYPASS_ROOM_LIST_ACK |
| 입장/팀/READY | CS_ARENACO_ENTER_ROOM_REQ, CS_ARENACO_BYPASS_CHANGE_TEAM_REQ, CS_ARENACO_BYPASS_CHANGE_READY_ENTER_REQ |
| 시작 | CS_ARENACO_BYPASS_GAME_START_REQ, SC_ARENACO_BYPASS_GAME_START_ACK |
| **제한시간/라운드** | **SC_ARENA_GAME_INFO_NOTI**, **SC_ARENA_PLAY_STATUS_NOTI** |
| **3초/10초 카운트다운** | **SC_ARENA_PLAY_EVENT_NOTI** |
| **점수판/선수 상태** | **ArenaGameStatus**, **RoundScoreBoard**, **ArenaPlayerStatus** |
| 맵 선택 연출 | SC_SCRIPT_PRESENTATION_NOTI(ArenaMapSelect=1) |
| 탑 상태 | SC_ARENA_OBJECT_STATUS_NOTI |
| 승패/개인 결과 | SC_ARENA_PLAY_RESULT_NOTI, ArenaResult |
| 관전 | CS_ARENA_OBSERVER_TELEPORT_REQ, SC_ARENA_OBSERVER_TELEPORT_ACK |
| 응원 | CS_ARENA_CHEER_REQ, SC_ARENA_CHEER_NOTI |
| 준비/실전 입장 보조 | CS_ARENA_PLAY_READY_REQ, SC_ARENA_PLAY_READY_ACK, CS_ARENA_PLAY_EXIT_REQ |
| 자동 매칭 | SC_BATTLE_ARENA_AUTO_MATCH_REQ, CS_BATTLE_ARENA_AUTO_MATCH_ACK, SC_BATTLE_ARENA_AUTO_MATCH_RESULT_NOTI |

---

### 최종 평가

**리토·콜로세움 계열 콘텐츠를 구현하는 데 필요한 자료는 예상보다 풍부하다.** 방 생성/운영부터 UI 표시용 라운드 제한시간, 밀리초 타이머, 카운트다운, 팀 점수, 관전, 경기 결과, MVP까지 **구체적 패킷 계약이 남아 있다.**

실제 복원 작업의 가장 중요한 다음 검증은 **숫자 메시지 ID 확보**와 **Lito UI가 해당 ArenaCo 패킷을 수신해 화면을 여는지 호출 관계 확정**이다. 이 두 항목을 확인한 다음 기존 RKern 서버 구조에 최소 변경으로 결합하는 것이 바람직하다.
