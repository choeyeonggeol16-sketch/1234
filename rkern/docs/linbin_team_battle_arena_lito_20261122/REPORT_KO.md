# 린빈 팀배틀 · 투기장 · 리토 · ArenaCo 통합 역분석 종합 보고서

작성일: 2026-11-22  
대상 바이너리: mjlin_loaded_code_00400000_0180D000.bin (복호화된 x86 적재 이미지, 21,024,768 bytes)  
원본 SHA-256: 256f7886e98d7d2c573ab76917f78e30dc4f34cb723a002d604602cdb2f6cbdb (로컬 원본 직접 검증)  
목적: **린빈에서 확인한 원본 그대로** 향후 RKern에 복원하기 위한 구조·주소·필드 기준서.  
변경: **보고서 하나만 신규 작성. Java, DB, 기존 proto는 변경하지 않음.**

## 1. 결론

**TeamBattleControl은 리토의 팀전 모드 컨트롤러다.** 이 결론은 클래스 이름이 비슷하다는 추정이 아니라, 실제 실행 이미지의 MSVC RTTI 부모 배열과 x86 명령어에서 확인했다. **PVPBattleControl도 동일한 리토 인터페이스의 하위 클래스**다.

원본 리토 배틀 화면에는 **PvpBattle, TeamBattle, GameCountDown, ScoreBoard, MapRouletteControl**을 직접 조회하는 초기화 코드가 있고, 추가로 라운드·팀 점수·킬·시간·전투 결과·관전자·캐스터 UI 이름이 있다.

별도로 원본에는 **SC_TEAM_BATTLE_ARENA_* 4개 이름-번호 등록**, **투기장 자동매칭 3개 Protobuf 메시지**, **ArenaCo 경기·로비 최상위 메시지 62개**가 존재한다. 세 계열의 데이터는 서로 관련되는 콘텐츠로 분석할 가치가 높지만, **네트워크 수신 콜백이 전부 동일한 Lito UI로 합류하는지는 아직 확인되지 않았다.** 확정한 관계와 추가 확인할 관계를 아래에서 분리한다.

## 2. MSVC RTTI 실제 상속 복원

### 2.1 확정 클래스 계층

~~~text
ILitoBattleModeControl
    +-- PVPBattleControl
    +-- TeamBattleControl
~~~

| 원본 VA | MSVC 데이터 | 판독 결과 |
|---|---|---|
| 0x015793D4 | TypeDescriptor | ILitoBattleModeControl |
| 0x015793FC | TypeDescriptor | TeamBattleControl |
| 0x01579C04 | TypeDescriptor | PVPBattleControl |
| 0x0137BA70 | ClassHierarchyDescriptor | TeamBattleControl, 부모 클래스 수 2 |
| 0x0137BA80 | Base class array | TeamBattleControl, ILitoBattleModeControl 순서 |
| 0x0137BA8C | BaseClassDescriptor | TeamBattleControl → 타입 0x015793FC |
| 0x0137BA40 | BaseClassDescriptor | ILitoBattleModeControl → 타입 0x015793D4 |
| 0x0137C404 | ClassHierarchyDescriptor | PVPBattleControl, 부모 클래스 수 2 |
| 0x0137C414 | Base class array | PVPBattleControl, ILitoBattleModeControl 순서 |
| 0x0137C420 | BaseClassDescriptor | PVPBattleControl → 타입 0x01579C04 |

두 ClassHierarchyDescriptor를 직접 읽어 부모 타입 포인터가 **같은 ILitoBattleModeControl**을 가리키는 것을 확인했다. 따라서 팀전/PvP 두 모드는 **클라이언트의 리토 모드 시스템에 속한다**는 것을 확정한다.

### 2.2 실제 실행 함수

| 원본 함수 VA | 디스어셈블 확인 내용 |
|---|---|
| 0x007F6350 | TeamBattleControl(0x015793FC)과 ILitoBattleModeControl(0x015793D4) RTTI를 인수로 동적 형변환 호출(0x00FD1C0A) |
| 0x007F6510 | 리토 배틀 UI의 PvP/Team/카운트다운/점수판/맵 룰렛 요소 초기화 |
| 0x007F6AC0 | 같은 팀전 RTTI로 동적 형변환 후 0x007BD3B0 호출 |
| 0x007F6B00 | UI 이벤트 0x202 비교 후 TeamBattleControl 경로 |
| 0x007F7330 | 팀전 RTTI 형변환 후 0x007BF510 함수로 전달 |
| 0x007F7360 | UI 이벤트 0x201 비교 후 TeamBattleControl 경로 |
| 0x007F6460 | UI 이벤트 0x202 / 0x206 비교 분기 |
| 0x007F64C0 | UI 이벤트 0x201 비교 분기 |

여기서 **0x201·0x202·0x206은 코드상 UI 이벤트 비교 상수**이다. 이를 네트워크 패킷 번호로 잘못 기록하지 않는다. 위의 0x007BD3B0 및 0x007BF510은 호출 대상 주소를 확인했으나 최종 기능명과 내부 규칙은 추가 분석 대상이다.

## 3. 실제 리토 UI 초기화 경로

**0x007F6510 함수**에서 가상 함수 [vtable+0x7C]를 통해 다음 문자열 이름의 UI 객체를 순서대로 조회한다.

| 실행 코드 VA | UI 문자열 VA | 실제 이름 |
|---|---|---|
| **0x007F65AF** | 0x011BC2C8 | PvpBattle |
| **0x007F66A1** | 0x011BC2D4 | TeamBattle |
| **0x007F672F** | 0x011BC2E0 | GameCountDown |
| **0x007F6756** | 0x011BC2F0 | ScoreBoard |
| **0x007F6768** | 0x011BC2FC | MapRouletteControl |

원본 어셈블리 핵심:

~~~asm
; 0x007F65AF
push 0x011BC2C8    ; PvpBattle
call dword ptr [eax+0x7c]

; 0x007F66A1
push 0x011BC2D4    ; TeamBattle
call dword ptr [eax+0x7c]

; 0x007F672F
push 0x011BC2E0    ; GameCountDown
call dword ptr [eax+0x7c]

; 0x007F6756
push 0x011BC2F0    ; ScoreBoard
call dword ptr [eax+0x7c]

; 0x007F6768
push 0x011BC2FC    ; MapRouletteControl
call dword ptr [eax+0x7c]
~~~

**별도 리토 화면/컨트롤 이름 존재:**

| VA | 클라이언트 문자열 |
|---|---|
| 0x012B7A10 | LitoMainMenu |
| 0x012B7A20 | LitoLobby |
| 0x012B7A2C | LitoResultUI |
| 0x012B7A3C | LitoRoomUI |
| 0x012B7A48 | LitoBattleUI |
| 0x011BC31C | LitoMapRoulette |
| 0x011BC310 | Roulette |
| 0x011BC330 | Round |
| 0x011BC338 | TeamAScore |
| 0x011BC344 | TeamBScore |
| 0x011BC350 | KillCount |
| 0x011B8030 | LitoTimer |
| 0x011B804C | LitoWarningTimer |
| 0x011B8198 | TimeCountDown |
| 0x011B81F4 | ObserverCount |
| 0x011B80AC | ReadyButton |
| 0x011B8168 | SelectTeam |
| 0x011BC1EC | PvpMark |
| 0x011BC204 | TeamMark |
| 0x011BC244 | StartEffect |
| 0x011BC260 | KOEffect |
| 0x011BC26C | PKEffect |
| 0x011BC278 | WinImg |
| 0x011BC280 | LoseImg |
| 0x011BC288 | DrawImg |
| 0x011BC030 | PlayReady |
| 0x011BC03C | ToggleParty |
| 0x011BC048 | PopupParty |
| 0x011BC07C | SendMessage |
| 0x011BC098 | ToggleChat |
| 0x011BC130 | ObserverAddon |
| 0x011BC140 | PlayerAddon |
| 0x011BC14C | CasterAddon |
| 0x012B75F0 | MatchMakingUI (리토 직접 연결은 미확인) |

**해석:** 리토 UI에 단순 전투 화면 하나만 있는 것이 아니라 로비·방·모드(PvP/팀전)·준비·시간·점수·맵 선택·결과·관전자/캐스터가 분리되어 있다. 다만 **실제 UI 팝업과 각 버튼 송신 패킷**은 이름만으로 확정하지 않는다.

## 4. 별도 Team Battle Arena 정보 4종 — 숫자 등록 코드도 직접 확인

아래 4개는 **CommonPacket 계열**로 나타나며, ArenaCo Protobuf의 필드/메시지와 바로 동일하다고 취급하지 않는다.

| 등록 코드 VA | 등록 값 | 이름 문자열 VA | 원문 |
|---|---|---|---|
| **0x00A0B9DF** | **0x002F** | 0x0120AA24 | SC_TEAM_BATTLE_ARENA_TYPE_INFO |
| **0x00A0BA01** | **0x0030** | 0x0120AA44 | SC_TEAM_BATTLE_ARENA_ROOM_INFO |
| **0x00A0BA23** | **0x0031** | 0x0120AA64 | SC_TEAM_BATTLE_ARENA_RANKER_INFO |
| **0x00A0BAAB** | **0x0035** | 0x0120AAE4 | SC_TEAM_BATTLE_ARENA_PLAYER_MARKER |

등록 함수 **0x00A0B4F0**은 내부에서 숫자 키를 기준으로 컨테이너를 탐색하고 삽입한다. 각 등록 위치에서는 **문자열 포인터**와 **0x2F / 0x30 / 0x31 / 0x35의 32비트 정수**가 각각 전달된다.

또한 다음 타입 RTTI가 있다.

| RTTI 이름 주소 | 타입 |
|---|---|
| 0x0158F9EC | TeamBattleArenaTypeInfo |
| 0x0158FA14 | TeamBattleArenaRoomInfo |
| 0x0158FA3C | TeamBattleArenaRankerInfo |
| 0x01595293 | TEAM_BATTLE_ARENA_PLAYER_MARKER@CommonPacket |

확정 가능한 기능 분류: **투기장 종류 정보, 경기방 정보, 랭킹 정보, 참가자 식별 마커**를 위한 전용 데이터 형식 흔적.

**검증 경계:** 숫자-이름 등록은 직접 증명되었지만, 이 값들이 실제 네트워크 프레임 최종 opcode인지, 4종의 바이너리 payload 필드 배치가 무엇인지, 리토 TeamBattleControl이 이 4종의 수신 대상인지는 **완전한 네트워크 디스패처 XREF를 아직 확인하지 않았다.** 현 단계에서 ArenaCo의 Protobuf 메시지에 이 4종을 억지로 합치지 않는다.

## 5. Battle Arena 자동 매칭 3종

원본 **MainServer_Client.proto**에 별도 존재한다.

- **SC_BATTLE_ARENA_AUTO_MATCH_REQ** (서버→클라이언트): arena_id (required int32, field 1).
- **CS_BATTLE_ARENA_AUTO_MATCH_ACK** (클라이언트→서버): arena_id (required int32, field 1), **is_apply** (required bool, field 2) — 참가 수락/거절.
- **SC_BATTLE_ARENA_AUTO_MATCH_RESULT_NOTI** (서버→클라이언트): arena_id (required int32, field 1), result (required enum, field 2).
- 결과: **eAUTO_MATCH_RESULT_MATCH_SUCCEED=0**, **eAUTO_MATCH_RESULT_OTHER_REJECT=1**, **eAUTO_MATCH_RESULT_ERROR=2**.

이를 통해 **서버가 매칭 수락을 요청하고 클라이언트가 수락/거절 응답을 보낸 뒤 결과를 통지하는 계약**이 존재함을 확인했다. 실제 상대 탐색·매칭 타임아웃·참가 인원/수락 조건·클라이언트 MatchMakingUI 이벤트 연결은 별도 분석 대상이다.

## 6. Lito/ArenaCo 대전방 전체 패킷 구조

바이너리 내 ArenaCo proto에는 **최상위 메시지 62종**이 존재한다. 중첩 메시지와 enum을 포함한 실제 원문은 **부록 D에 전문을 수록**했다.

### 6.1 로비·대기방·전적·경기 시작

| 단계 | 요청 / 응답·통지 |
|---|---|
| 서비스 상태 | SC_ARENACO_SERVER_STATUS_NOTI |
| 전적 조회 | CS_ARENACO_BYPASS_MY_INFO_REQ → SC_ARENACO_BYPASS_MY_INFO_ACK |
| 방 목록 | CS_ARENACO_BYPASS_ROOM_LIST_REQ → SC_ARENACO_BYPASS_ROOM_LIST_ACK |
| 방 상세 | CS_ARENACO_BYPASS_ROOM_INFO_REQ → SC_ARENACO_BYPASS_ROOM_INFO_ACK |
| 방 생성 | CS_ARENACO_CREATE_ROOM_REQ → SC_ARENACO_CREATE_ROOM_ACK |
| 방 설정 | CS_ARENACO_BYPASS_CHANGE_ROOM_PROPERTY_REQ → SC_ARENACO_BYPASS_CHANGE_ROOM_PROPERTY_ACK |
| 방 갱신 | SC_ARENACO_BYPASS_CHANGE_ROOM_STATUS_NOTI |
| 입장 | CS_ARENACO_ENTER_ROOM_REQ → SC_ARENACO_ENTER_ROOM_ACK 및 ENTER_ROOM_NOTI |
| 팀 선택 | CS_ARENACO_BYPASS_CHANGE_TEAM_REQ → SC_ARENACO_BYPASS_CHANGE_TEAM_ACK/NOTI |
| 준비 | CS_ARENACO_BYPASS_CHANGE_READY_ENTER_REQ → SC_ARENACO_BYPASS_CHANGE_READY_ENTER_ACK/NOTI |
| 잠금 | CS_ARENACO_BYPASS_CHANGE_LOCK_ROOM_REQ → SC_ARENACO_BYPASS_CHANGE_LOCK_ROOM_ACK/NOTI |
| 시작 | CS_ARENACO_BYPASS_GAME_START_REQ → SC_ARENACO_BYPASS_GAME_START_ACK |
| 채팅 | CS_ARENACO_BYPASS_ROOM_CHAT_REQ → SC_ARENACO_BYPASS_ROOM_CHAT_NOTI |
| 초대 | CS_ARENACO_INVITE_ROOM_REQ → SC_ARENACO_INVITE_ROOM_ACK/NOTI |
| 초대 거절 | CS_ARENACO_INVITE_ROOM_DENY_REQ → SC_ARENACO_INVITE_ROOM_DENY_NOTI |
| 강퇴 | CS_ARENACO_BYPASS_KICK_REQ → SC_ARENACO_BYPASS_KICK_ACK/NOTI |
| 퇴장 | CS_ARENACO_EXIT_ROOM_REQ → SC_ARENACO_EXIT_ROOM_ACK/NOTI |

원본 메시지에는 **방장 권한, 이미 입장한 유저, 정원 초과, 비밀번호·맵·직업·레벨·서버·대전 비용, 운영 시간 제한**에 대응하는 상세 실패 enum이 있다. 단순 성공/실패 코드로 변경하지 말고 부록 원문 값을 사용해야 한다.

### 6.2 경기 모드·맵·참가자

- **eArenaTeamBuildingType:** TEAM_BUILDING_NORMAL=1, TEAM_BUILDING_MATCH=2, TEAM_BUILDING_BROADCAST=3.
- **eArenaTeam:** TEAM_NONE=0, TEAM_A=1, TEAM_B=2, TEAM_C=3, NEUTRAL_PASSIVE=-2, NEUTRAL_HOSTILE=-1.
- **ArenaUserInfo.eRole:** Player=1, Observer=2, BuilderCaster=3.
- **eArenaGambleType:** GAMBLE_NONE=0, GAMBLE_ADENA_100K=1.
- **eArenaMapKind:** LastShowdown=1, ArenaMatch=11, WarSquare=12, WarSquare2=13, BattleHunter=14, ArenaMatch_LFC=15, WarSquare_LFC=16, WarSquare_LFC1~5=17~21, BreakingTower=22, BreakingTower_LFC=23, LastShowdown_LFC=24.
- 원본 공통 맵 enum에는 OrimLab Minor/Normal/Major도 있지만, 이를 리토 맵 목록에 무조건 포함해서는 안 된다.
- **ArenaBuildInfo:** 방 제목·팀 구성 형태·레벨·직업·아데나 규칙·비밀번호·관전·맵·서버 제한.
- **ArenaRoomInfo:** 방 메타, 현재/최대/최소 인원, 경기 중인지·잠금 여부, 회원·전적.
- **ArenaUserInfo:** 캐릭터·서버·직업·성별·팀·역할·방장/READY/입장 플래그.

### 6.3 경기 시간·카운트다운·상태

| 원본 메시지/모델 | 정확한 필드 |
|---|---|
| **SC_ARENA_GAME_INFO_NOTI** | player_info(1), map_kind(2), round(3), **round_time_sec(4)**, **result_display_time_sec(5)** |
| **SC_ARENA_PLAY_STATUS_NOTI** | player_status(1), game_status(2) |
| **ArenaGameStatus** | **play_time_msec(1)**, **is_timer_run(2)**, observer_count(3), round_score_board(4), team_status(5) |
| **RoundScoreBoard** | team_a(1), team_b(2); 각 RoundScore에 is_win, attack_amount |
| **SC_ARENA_PLAY_EVENT_NOTI** | type(1), actor_info(10), arena_char_id_target(20), arena_char_id_causer(21), team_id(30), spell_id(40) |
| **SC_ARENA_PLAY_RESULT_NOTI** | result(1), **remain_time_sec(2)**, reason(3) |

이벤트 enum: **GameForfeited=1, GameCountDown3Sec=2, GameCountDown10Sec=3, PlayerEntered=11, PlayerOut=12, PlayerDead=13, PlayerSpellSucceded=14, YourSelfEntered=21, BattleHunterWin=31, TeamTowerBroke=41.**

시간 단위를 원본대로 유지: **round_time_sec/result_display_time_sec/remain_time_sec = 초**, **play_time_msec = 밀리초**. LitoWarningTimer·GameCountDown과의 연결/표시 기준은 클라이언트 핸들러 검증 대상으로 남는다.

### 6.4 점수판·킬/데스·오브젝트

- **ArenaPlayerStatus:** arena_char_id, kill_count, death_count, hp_ratio, mp_ratio, loc_x, loc_y, attack_amount, damaged_amount, heal_amount, poisoned, paralysed, is_live, obj_id, is_ready.
- **ArenaGameStatus.TeamStatus:** team_id, cheer_msg_count, team_kill_count.
- **SC_ARENA_OBJECT_STATUS_NOTI.Tower:** obj_id, team_id, hp_ratio.
- 라운드별 승패 배열과 TeamTowerBroke/BattleHunterWin 이벤트까지 존재하므로 **팀 승점·탑 파괴·배틀헌터 상태를 표시할 프로토콜 근거**가 있다.

### 6.5 승패·MVP·랭킹

- **ArenaTeamResult.eOutcome:** DRAW=0, VICTORY=1, DEFEAT=2.
- **ArenaUserResult.eArenaResultGrade:** MVP=1, BEST_ATTACKER=2, BEST_DEFENDER=3, BEST_HEALER=4, BEST_POINTER=5, NONE=6.
- **ArenaUserResult:** arena_char_id, grade, kill, attack, defence, heal.
- **ArenaTeamResult:** team_id, outcome, cheeringmessage_count, user_result, kill_count.
- **ArenaRoomInfo.ArenaRecord** 및 **ArenaUserRecord:** RANK_NONE/RANK_SOLO/RANK_TEAM, total/win/draw/lose/win_rate.

### 6.6 관전·응원·채팅·맵 연출

- **CS_ARENA_OBSERVER_TELEPORT_REQ:** player_arena_char_id 또는 x/y, 응답에는 유효하지 않은 위치·대상 이탈·관전 불가능 상태별 실패 enum.
- **CS_ARENA_CHEER_REQ, SC_ARENA_CHEER_NOTI, SC_ARENA_CHEER_LIST_NOTI:** 팀 응원과 목록·횟수.
- **CS_ARENA_CHAT_BLOCK_REQ / SC_ARENA_CHAT_BLOCK_ACK:** 채팅 차단.
- **CS_ARENA_PLAY_READY_REQ / SC_ARENA_PLAY_READY_ACK, CS_ARENA_PLAY_EXIT_REQ:** 경기 상태 전환·퇴장.
- **SC_SCRIPT_PRESENTATION_NOTI:** type=1 **ArenaMapSelect**, repeated int32 args. LitoMapRoulette와 역할상 일치하지만 **직접 수신→UI 호출 XREF는 아직 미확인**.

## 7. 세 계열의 연결 여부 최종 정리

| 두 요소 | 확인된 증거 | 판정 |
|---|---|---|
| 리토 UI ↔ TeamBattleControl | RTTI 부모 배열 + 런타임 캐스팅 + TeamBattle 요소 조회 | **확정** |
| 리토 UI ↔ PVPBattleControl | RTTI 부모 배열 + PvpBattle 요소 조회 | **확정** |
| 리토 UI ↔ GameCountDown/ScoreBoard/MapRouletteControl | 동일 UI 초기화 함수의 실제 호출 | **확정** |
| Team Battle Arena 4개 이름 ↔ 0x2F/0x30/0x31/0x35 | 원본 코드 내 숫자-문자열 등록 | **등록 관계 확정** |
| SC_TEAM_BATTLE_ARENA_* → TeamBattleControl | 타입명/패킷/모드가 모두 존재 | **수신 핸들러 호출 미확인** |
| 자동매칭 3종 → MatchMakingUI | proto + UI 이름 존재 | **직접 핸들러 호출 미확인** |
| ArenaCo 경기 패킷 → LitoBattleUI | proto + UI 클래스 존재 | **실제 디스패처 호출 미확인** |
| ArenaMapSelect → LitoMapRoulette | proto + UI 이름 존재 | **실제 호출 미확인** |
| 2005 원조 리토·2016 콜로세움 UI와 현재 린빈 화면의 버전 일치 | 명칭/기능 단서 | **클라이언트 실제 실행 비교 미확인** |

**따라서 팀배틀 컨트롤러는 확실히 리토 내부 기능이다.** 그러나 팀배틀 정보 패킷 4종을 ArenaCo Protobuf에 합쳐 쓴다거나, 자동매칭 3종이 반드시 같은 모드로 연결된다는 가정은 원본 증거가 더 필요하다.

## 8. 원본 그대로 복원하려면 추가로 해독해야 할 항목

1. **0x2F/0x30/0x31/0x35 등록 테이블의 실제 네트워크 디스패처 사용처**와 네 개 CommonPacket 본문 구조.
2. 각 **ArenaCo 원본 메시지의 실제 숫자 message ID** 및 수신/송신 등록부.
3. **TeamBattleControl/PVPBattleControl 모드 인터페이스의 모든 가상 함수**와 호출자·실행 타이밍.
4. LitoMainMenu/LitoLobby/LitoRoomUI/LitoBattleUI/LitoResultUI의 **열기/닫기/전환 호출 순서**.
5. PvpBattle/TeamBattle UI 입력→**CS_ 요청 송신**과 SC_ 응답→화면 반영 연결.
6. 매칭 신청·수락/거절·오류·취소·실패/재접속과 **MatchMakingUI 연결**.
7. MapRouletteControl/LitoMapRoulette의 **맵 확정 연출과 SC_SCRIPT_PRESENTATION_NOTI args 의미**.
8. 라운드·타이머·3초/10초 카운트다운·점수/탑/승패/랭킹의 **원본 트리거와 전달 순서**.
9. 모드별 실제 룰(1:1, 팀전, 배틀헌터, 타워 등), 원본 서버 연계·보상 확정/재접속/중복 처리.
10. 실제 클라이언트 실행으로 **UI 표시·패킷 캡처·수신 함수·게임 종료까지** 회귀 검증.

이후 코드 구현 단계에서도 **원본 확인된 실행 구조와 필드를 기준**으로 만들고, 임의 정책을 원본 복원이라고 표현하지 않는다. 이 문서는 소스 수정 요청이 아니므로 **운영 서버 코드를 변경하지 않는다.**

---

# 부록 A. ArenaCo 최상위 패킷/자료 메시지 62개 목록

01. SC_ARENACO_SERVER_STATUS_NOTI
02. CS_ARENACO_BYPASS_MY_INFO_REQ
03. SC_ARENACO_BYPASS_MY_INFO_ACK
04. CS_ARENACO_BYPASS_ROOM_LIST_REQ
05. SC_ARENACO_BYPASS_ROOM_LIST_ACK
06. CS_ARENACO_BYPASS_ROOM_INFO_REQ
07. SC_ARENACO_BYPASS_ROOM_INFO_ACK
08. CS_ARENACO_CREATE_ROOM_REQ
09. SC_ARENACO_CREATE_ROOM_ACK
10. CS_ARENACO_BYPASS_CHANGE_ROOM_PROPERTY_REQ
11. SC_ARENACO_BYPASS_CHANGE_ROOM_PROPERTY_ACK
12. SC_ARENACO_BYPASS_CHANGE_ROOM_STATUS_NOTI
13. CS_ARENACO_ENTER_ROOM_REQ
14. SC_ARENACO_ENTER_ROOM_ACK
15. SC_ARENACO_BYPASS_ENTER_ROOM_NOTI
16. CS_ARENACO_BYPASS_CHANGE_TEAM_REQ
17. SC_ARENACO_BYPASS_CHANGE_TEAM_ACK
18. SC_ARENACO_BYPASS_CHANGE_TEAM_NOTI
19. CS_ARENACO_BYPASS_CHANGE_READY_ENTER_REQ
20. SC_ARENACO_BYPASS_CHANGE_READY_ENTER_ACK
21. SC_ARENACO_BYPASS_CHANGE_READY_ENTER_NOTI
22. CS_ARENACO_BYPASS_CHANGE_LOCK_ROOM_REQ
23. SC_ARENACO_BYPASS_CHANGE_LOCK_ROOM_ACK
24. SC_ARENACO_BYPASS_CHANGE_LOCK_ROOM_NOTI
25. CS_ARENACO_BYPASS_GAME_START_REQ
26. SC_ARENACO_BYPASS_GAME_START_ACK
27. CS_ARENACO_BYPASS_ROOM_CHAT_REQ
28. SC_ARENACO_BYPASS_ROOM_CHAT_NOTI
29. CS_ARENACO_EXIT_ROOM_REQ
30. SC_ARENACO_EXIT_ROOM_ACK
31. SC_ARENACO_BYPASS_EXIT_ROOM_NOTI
32. CS_ARENACO_INVITE_ROOM_REQ
33. SC_ARENACO_INVITE_ROOM_ACK
34. SC_ARENACO_INVITE_ROOM_NOTI
35. CS_ARENACO_INVITE_ROOM_DENY_REQ
36. SC_ARENACO_INVITE_ROOM_DENY_NOTI
37. CS_ARENACO_BYPASS_KICK_REQ
38. SC_ARENACO_BYPASS_KICK_ACK
39. SC_ARENACO_BYPASS_KICK_NOTI
40. ArenaActorInfo
41. ArenaPlayerStatus
42. RoundScoreBoard
43. ArenaGameStatus
44. SC_ARENA_GAME_INFO_NOTI
45. SC_ARENA_PLAY_STATUS_NOTI
46. SC_ARENA_OBJECT_STATUS_NOTI
47. SC_ARENA_PLAY_EVENT_NOTI
48. SC_ARENA_PLAY_RESULT_NOTI
49. CS_ARENA_PLAY_EXIT_REQ
50. CS_ARENA_CHEER_REQ
51. SC_ARENA_CHEER_ACK
52. ArenaCheer
53. SC_ARENA_CHEER_NOTI
54. CS_ARENA_CHEER_LIST_REQ
55. SC_ARENA_CHEER_LIST_NOTI
56. CS_ARENA_CHAT_BLOCK_REQ
57. SC_ARENA_CHAT_BLOCK_ACK
58. CS_ARENA_OBSERVER_TELEPORT_REQ
59. SC_ARENA_OBSERVER_TELEPORT_ACK
60. CS_ARENA_PLAY_READY_REQ
61. SC_ARENA_PLAY_READY_ACK
62. SC_SCRIPT_PRESENTATION_NOTI

# 부록 B. 원본 팀·맵·경기 종류 enum 원문

출처: [Lineage_CommonDataTypes.proto](../MainServerProto/schemas/Lineage_CommonDataTypes.proto)

~~~proto
enum eArenaTeamBuildingType {
  TEAM_BUILDING_NORMAL = 1;
  TEAM_BUILDING_MATCH = 2;
  TEAM_BUILDING_BROADCAST = 3;
}

enum eArenaMapKind {
  None = 0;
  _SMALL_MAP_BEGIN = 1;
  LastShowdown = 1;
  _SMALL_MAP_END = 1;
  _MIDDLE_MAP_BEGIN = 11;
  ArenaMatch = 11;
  WarSquare = 12;
  WarSquare2 = 13;
  BattleHunter = 14;
  ArenaMatch_LFC = 15;
  WarSquare_LFC = 16;
  WarSquare_LFC1 = 17;
  WarSquare_LFC2 = 18;
  WarSquare_LFC3 = 19;
  WarSquare_LFC4 = 20;
  WarSquare_LFC5 = 21;
  BreakingTower = 22;
  BreakingTower_LFC = 23;
  LastShowdown_LFC = 24;
  _MIDDLE_MAP_END = 24;
  _INSTANCE_DUNGEON_BEGIN = 201;
  OrimLab_Minor = 201;
  OrimLab_Normal = 202;
  OrimLab_Major = 203;
  _INSTANCE_DUNGEON_END = 203;
}

enum eArenaGambleType {
  GAMBLE_NONE = 0;
  GAMBLE_ADENA_100K = 1;
}

enum eArenaTeam {
  NEUTRAL_PASSIVE = -2;
  NEUTRAL_HOSTILE = -1;
  TEAM_NONE = 0;
  TEAM_A = 1;
  TEAM_B = 2;
  TEAM_C = 3;
}
~~~

# 부록 C. 원본 ArenaCo 사용자·방·전적·승패 자료형 전문

출처: [Lineage_CommonDataTypes.proto](../MainServerProto/schemas/Lineage_CommonDataTypes.proto)

~~~proto
message ArenaBuildInfo {
  required bytes title = 1;
  required .LIN.eArenaTeamBuildingType team_building_type = 2;
  required uint32 min_level = 3;
  required uint32 max_level = 4;
  repeated .LIN.CharacterClass allow_class = 5;
  required .LIN.eArenaGambleType gamble = 6;
  required bool closed = 7;
  optional string password = 8;
  required bool observe = 9;
  required .LIN.eArenaMapKind map_kind = 10;
  required bool allow_all_server = 11;
  repeated uint32 allow_server = 12;
}

message ArenaRoomInfo {
  message ArenaRecord {
    enum eArenaRankType {
      RANK_NONE = 0;
      RANK_SOLO = 1;
      RANK_TEAM = 2;
    }
    required .LIN.ArenaRoomInfo.ArenaRecord.eArenaRankType type = 1;
    required uint32 total = 2;
    required uint32 win = 3;
    required uint32 draw = 4;
    required uint32 lose = 5;
    required uint32 win_rate = 6;
  }
  message MemberInfo {
    required .LIN.ArenaUserInfo userInfo = 1;
    repeated .LIN.ArenaRoomInfo.ArenaRecord record = 2;
  }
  message GameInfo {
    required uint32 min_level = 1;
    required uint32 max_level = 2;
    repeated .LIN.CharacterClass allow_class = 3;
    required bool allow_all_server = 4;
    repeated uint32 allow_server = 5;
  }
  message Base {
    required uint32 room_id = 1;
    required bytes title = 2;
    required uint32 member_count_cur = 3;
    required uint32 member_count_max = 4;
    required uint32 member_count_min = 5;
    required .LIN.eArenaMapKind map_kind = 6;
    required bool closed = 7;
    required bool observe = 8;
    required .LIN.eArenaGambleType gamble = 9;
    required .LIN.eArenaTeamBuildingType team_building_type = 10;
    required .LIN.ArenaRoomInfo.GameInfo game = 11;
    optional bool is_playing = 12;
    optional bool is_locked = 13;
  }
  message Extend {
    repeated .LIN.ArenaRoomInfo.MemberInfo member = 1;
  }
  required .LIN.ArenaRoomInfo.Base base = 1;
  optional .LIN.ArenaRoomInfo.Extend extend = 2;
}

message ArenaUserInfo {
  enum eRole {
    Player = 1;
    Observer = 2;
    BuilderCaster = 3;
  }
  required int64 arena_char_id = 1;
  required uint32 server_id = 2;
  required bytes character_name = 3;
  required .LIN.CharacterClass character_class = 4;
  required .LIN.Gender gender = 5;
  required .LIN.eArenaTeam team_id = 6 [default = TEAM_A];
  required .LIN.ArenaUserInfo.eRole role = 7 [default = Player];
  required bool room_owner = 8 [default = false];
  required bool ready = 9 [default = false];
  required bool in_room = 10 [default = false];
  optional uint32 order = 11;
  optional int32 enter_count = 12;
}

message ArenaUserRecord {
  enum eArenaRankType {
    RANK_NONE = 0;
    RANK_SOLO = 1;
    RANK_TEAM = 2;
  }
  required .LIN.ArenaUserRecord.eArenaRankType type = 1;
  required uint32 total = 2;
  required uint32 win = 3;
  required uint32 draw = 4;
  required uint32 lose = 5;
  required uint32 win_rate = 6;
}

message ArenaMemberInfo {
  required .LIN.ArenaUserInfo userInfo = 1;
  repeated .LIN.ArenaUserRecord record = 2;
}

message ArenaUserResult {
  enum eArenaResultGrade {
    MVP = 1;
    BEST_ATTACKER = 2;
    BEST_DEFENDER = 3;
    BEST_HEALER = 4;
    BEST_POINTER = 5;
    NONE = 6;
  }
  required int64 arena_char_id = 1;
  required .LIN.ArenaUserResult.eArenaResultGrade grade = 2;
  required uint32 kill = 3;
  required uint32 attack = 4;
  required uint32 defence = 5;
  required uint32 heal = 6;
}

message ArenaTeamResult {
  enum eOutcome {
    eRESULT_DRAW = 0;
    eRESULT_VICTORY = 1;
    eRESULT_DEFEAT = 2;
  }
  required .LIN.eArenaTeam team_id = 1;
  required .LIN.ArenaTeamResult.eOutcome outcome = 2;
  required uint32 cheeringmessage_count = 3;
  repeated .LIN.ArenaUserResult user_result = 4;
  required uint32 kill_count = 5;
}

message ArenaResult {
  repeated .LIN.ArenaTeamResult team_result = 1;
  required .LIN.eArenaGambleType gamble = 2;
}
~~~

# 부록 D. ArenaCo 원본 패킷 62종 전문 — 필드 번호·타입·enum 전체

출처: [MainServer_Client_ArenaCo.proto](../MainServerProto/schemas/MainServer_Client_ArenaCo.proto). 바이너리 내 FileDescriptorProto에서 복원한 원문으로, 스키마의 순서·required/optional/repeated·enum 번호를 변경하지 않는다.

~~~proto
// Recovered from mjlin_loaded_code_00400000_0180D000.bin, SHA-256 256f7886e98d7d2c573ab76917f78e30dc4f34cb723a002d604602cdb2f6cbdb
// Source: embedded FileDescriptorProto, NOT NC-authored source file.
// package/message/field/enum/number/cardinality derived directly from binary descriptor.
// syntax="proto2" reconstructed from descriptor required/optional semantics.
syntax = "proto2";
package LIN;

import "Lineage_CommonDataTypes.proto";

message SC_ARENACO_SERVER_STATUS_NOTI {
  enum eStatus {
    INTERAL_ERROR = 1;
    NOW_CLOSED = 2;
  }
  required .LIN.SC_ARENACO_SERVER_STATUS_NOTI.eStatus status = 1;
}

message CS_ARENACO_BYPASS_MY_INFO_REQ {
}

message SC_ARENACO_BYPASS_MY_INFO_ACK {
  enum eResult {
    SUCCESS = 1;
    ERROR_INTENAL = 2;
    ERROR_NOT_EXIST_USER = 3;
    ERROR_NOT_FOUND_RECORD = 4;
  }
  required .LIN.SC_ARENACO_BYPASS_MY_INFO_ACK.eResult result = 1;
  repeated .LIN.ArenaRoomInfo.ArenaRecord record_info = 2;
}

message CS_ARENACO_BYPASS_ROOM_LIST_REQ {
  required uint32 page_id = 1 [default = 1];
}

message SC_ARENACO_BYPASS_ROOM_LIST_ACK {
  enum eResult {
    SUCCESS = 1;
    ERROR_INTENAL = 2;
    ERROR_NOT_EXIST_USER = 3;
    ERROR_NOT_EXIST_ROOM = 4;
  }
  required .LIN.SC_ARENACO_BYPASS_ROOM_LIST_ACK.eResult result = 1;
  required uint32 page_id = 2;
  required uint32 total_page = 3;
  repeated .LIN.ArenaRoomInfo room_info = 4;
}

message CS_ARENACO_BYPASS_ROOM_INFO_REQ {
  required uint32 room_id = 1;
}

message SC_ARENACO_BYPASS_ROOM_INFO_ACK {
  enum eResult {
    SUCCESS = 1;
    ERROR_INTENAL = 2;
    ERROR_NOT_EXIST_USER = 3;
    ERROR_NOT_EXIST_ROOM = 4;
  }
  required .LIN.SC_ARENACO_BYPASS_ROOM_INFO_ACK.eResult result = 1;
  optional .LIN.ArenaRoomInfo room_info = 2;
}

message CS_ARENACO_CREATE_ROOM_REQ {
  required .LIN.ArenaBuildInfo build_info = 1;
}

message SC_ARENACO_CREATE_ROOM_ACK {
  enum eResult {
    SUCCESS = 1;
    ERROR_INTERNAL = 2;
    ERROR_NOT_EXIST_USER = 3;
    ERROR_EXCEED_MAX_ROOM_COUNT = 4;
    ERROR_EXCEED_MAX_ARENA_COUNT = 5;
    ERROR_CANNOT_CREATE_ARENA = 6;
    ERROR_NOT_ENOUGH_MONEY = 7;
    ERROR_INVALID_TITLE = 8;
    ERROR_PASSWORD = 9;
    ERROR_TEAM_COUNT = 10;
    ERROR_TEAM_MEMBER_COUNT = 11;
    ERROR_ALLOCATE_ARENA = 12;
    ERROR_JOIN_ROOM = 13;
    ERROR_OTHER_ROOM_ENTERED = 14;
    ERROR_CLASS = 15;
    ERROR_SERVER = 16;
    ERROR_GAMBLETYPE = 17;
    ERROR_LEVEL = 18;
    ERROR_NOT_OPEN_TIME = 19;
  }
  required .LIN.SC_ARENACO_CREATE_ROOM_ACK.eResult result = 1;
  optional uint32 room_id = 2;
}

message CS_ARENACO_BYPASS_CHANGE_ROOM_PROPERTY_REQ {
  required uint32 room_id = 1;
  required bytes title = 2;
  required bool closed = 3;
  required string password = 4;
  required .LIN.eArenaMapKind map_kind = 6;
}

message SC_ARENACO_BYPASS_CHANGE_ROOM_PROPERTY_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
    FAIL_NOT_EXIST_USER = 3;
    FAIL_TITLE = 4;
    FAIL_PASSWORD = 5;
    FAIL_MAPKIND = 6;
    FAIL_INVALID_ROOM_STATUS = 7;
    FAIL_NOT_FOUND_ROOM = 8;
    FAIL_INVALID_ROOMID = 9;
    FAIL_NOT_OWNER = 10;
  }
  required .LIN.SC_ARENACO_BYPASS_CHANGE_ROOM_PROPERTY_ACK.eResult result = 1;
  required uint32 room_id = 2;
}

message SC_ARENACO_BYPASS_CHANGE_ROOM_STATUS_NOTI {
  required uint32 room_id = 1;
  required uint32 observer_count = 2;
  repeated .LIN.ArenaUserInfo player_info = 3;
  optional .LIN.ArenaRoomInfo room_info = 4;
}

message CS_ARENACO_ENTER_ROOM_REQ {
  required uint32 room_id = 1;
  required .LIN.ArenaUserInfo.eRole role = 2;
  optional string password = 3;
  optional bool invite_accept = 4;
  optional bytes host_char_name = 5;
}

message SC_ARENACO_ENTER_ROOM_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
    FAIL_TO_ALREADY_PLAY = 3;
    FAIL_NOT_ENOUGH_MONEY = 4;
    FAIL_CANNOT_OBSERVE = 5;
    FAIL_INVALID_PASSWORD = 6;
    FAIL_INVALID_ROOM_STATUS = 7;
    FAIL_NOT_FOUND_ROOM = 8;
    FAIL_FULL = 9;
    FAIL_ALREADY_ENTERED_PLAYER = 10;
    FAIL_ALREADY_ENTERED_OBSERVER = 11;
    FAIL_INVALID_ROLE = 12;
    FAIL_OTHER_ROOM_ENTERED = 13;
    FAIL_CLASS = 14;
    FAIL_SERVER = 15;
    FAIL_GAMBLETYPE = 16;
    FAIL_LEVEL = 17;
    FAIL_ALREADY_ENTERED_CASTER = 18;
    FAIL_NOT_OPEN_TIME = 19;
  }
  required .LIN.SC_ARENACO_ENTER_ROOM_ACK.eResult result = 1;
  required uint32 room_id = 2;
}

message SC_ARENACO_BYPASS_ENTER_ROOM_NOTI {
  required uint32 room_id = 1;
  required .LIN.ArenaUserInfo user_info = 2;
}

message CS_ARENACO_BYPASS_CHANGE_TEAM_REQ {
  required uint32 room_id = 1;
  required .LIN.eArenaTeam team_id = 2;
}

message SC_ARENACO_BYPASS_CHANGE_TEAM_ACK {
  enum eResult {
    SUCCESS = 1;
    ERROR_FULL = 2;
    FAIL = 3;
  }
  required .LIN.SC_ARENACO_BYPASS_CHANGE_TEAM_ACK.eResult result = 1;
  required uint32 room_id = 2;
  required .LIN.eArenaTeam team_id = 3;
}

message SC_ARENACO_BYPASS_CHANGE_TEAM_NOTI {
  required uint32 room_id = 1;
  required int64 arena_char_id = 2;
  required .LIN.eArenaTeam team_id = 3;
}

message CS_ARENACO_BYPASS_CHANGE_READY_ENTER_REQ {
  required uint32 room_id = 1;
  required bool ready = 2;
  optional uint64 own_adena = 3;
  optional uint64 entrance_adena = 4;
  optional uint32 user_level = 5;
}

message SC_ARENACO_BYPASS_CHANGE_READY_ENTER_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
    FAIL_NOT_ENOUGH_MONEY = 3;
    FAIL_GAMBLETYPE = 4;
    FAIL_LEVEL = 5;
  }
  required .LIN.SC_ARENACO_BYPASS_CHANGE_READY_ENTER_ACK.eResult result = 1;
  required uint32 room_id = 2;
  required bool ready = 3;
}

message SC_ARENACO_BYPASS_CHANGE_READY_ENTER_NOTI {
  required uint32 room_id = 1;
  required int64 arena_char_id = 2;
  required bool ready = 3;
}

message CS_ARENACO_BYPASS_CHANGE_LOCK_ROOM_REQ {
  required uint32 room_id = 1;
  required bool is_lock = 2;
}

message SC_ARENACO_BYPASS_CHANGE_LOCK_ROOM_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
    FAIL_NOT_OWNER = 3;
    FAIL_INVALID_ROOM_STATUS = 4;
  }
  required .LIN.SC_ARENACO_BYPASS_CHANGE_LOCK_ROOM_ACK.eResult result = 1;
  required uint32 room_id = 2;
  required bool is_lock = 3;
}

message SC_ARENACO_BYPASS_CHANGE_LOCK_ROOM_NOTI {
  required uint32 room_id = 1;
  required bool is_lock = 2;
}

message CS_ARENACO_BYPASS_GAME_START_REQ {
  required uint32 room_id = 1;
  optional uint64 own_adena = 2;
  optional uint64 entrance_adena = 3;
  optional uint32 user_level = 5;
}

message SC_ARENACO_BYPASS_GAME_START_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
    FAIL_INVALID_USER = 3;
    FAIL_INVALID_ROOM = 4;
    FAIL_INVALID_OWNER = 5;
    FAIL_INVALID_STATE = 6;
    FAIL_ISSUE_PLAY = 7;
    FAIL_NOT_ENOUGH_MONEY = 8;
    FAIL_GAMBLETYPE = 9;
    FAIL_LEVEL = 10;
    FAIL_NOT_OPEN_TIME = 11;
  }
  required .LIN.SC_ARENACO_BYPASS_GAME_START_ACK.eResult result = 1;
  required uint32 room_id = 2;
}

message CS_ARENACO_BYPASS_ROOM_CHAT_REQ {
  required uint32 room_id = 1;
  required bytes message = 2;
  optional bytes sender_char_name = 3;
}

message SC_ARENACO_BYPASS_ROOM_CHAT_NOTI {
  required uint32 room_id = 1;
  required int64 arena_char_id = 2;
  required bytes message = 3;
  required bytes sender_char_name = 4;
  required uint32 server_id = 5;
}

message CS_ARENACO_EXIT_ROOM_REQ {
  required uint32 room_id = 1;
}

message SC_ARENACO_EXIT_ROOM_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
  }
  required .LIN.SC_ARENACO_EXIT_ROOM_ACK.eResult result = 1;
  required uint32 room_id = 2;
}

message SC_ARENACO_BYPASS_EXIT_ROOM_NOTI {
  required uint32 room_id = 1;
  required .LIN.ArenaUserInfo user_info = 2;
}

message CS_ARENACO_INVITE_ROOM_REQ {
  required uint32 room_id = 1;
  required int64 host_arena_char_id = 2;
  repeated bytes guest_char_name = 3;
  required bool need_password = 4;
  optional bytes buildercaster_password = 5;
}

message SC_ARENACO_INVITE_ROOM_ACK {
  enum eInviteResult {
    eRESULT_SUCCESS = 1;
    eRESULT_ERROR_REJECT = 2;
    eRESULT_ERROR_NOT_EXIST_USER = 3;
    eRESULT_ERROR = 4;
    eRESULT_ERROR_NOT_OPEN_TIME = 5;
  }
  required .LIN.SC_ARENACO_INVITE_ROOM_ACK.eInviteResult result = 1;
  required uint32 room_id = 2;
  required int64 host_arena_char_id = 3;
}

message SC_ARENACO_INVITE_ROOM_NOTI {
  required uint32 room_id = 1;
  required bytes host_char_name = 2;
  required bytes guest_char_name = 3;
  required bool need_password = 4;
  optional bytes buildercaster_password = 5;
  optional bool from_builder = 6;
  optional bool is_indun = 7 [default = false];
}

message CS_ARENACO_INVITE_ROOM_DENY_REQ {
  required uint32 room_id = 1;
  required bytes host_char_name = 2;
  required bytes guest_char_name = 3;
  optional bool is_indun = 4 [default = false];
}

message SC_ARENACO_INVITE_ROOM_DENY_NOTI {
  required uint32 room_id = 1;
  required bytes host_char_name = 2;
  required bytes guest_char_name = 3;
}

message CS_ARENACO_BYPASS_KICK_REQ {
  required uint32 room_id = 1;
  required int64 kick_arena_char_id = 2;
}

message SC_ARENACO_BYPASS_KICK_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
    FAIL_NOT_EXIST_KICKER = 3;
    FAIL_INVALID_KICKER = 4;
    FAIL_NOT_EXIST_TARGET = 5;
    FAIL_ROOM_STATE = 6;
    FAIL_ROOM_EXIT = 7;
  }
  required .LIN.SC_ARENACO_BYPASS_KICK_ACK.eResult result = 1;
  required uint32 room_id = 2;
  required int64 kick_arena_char_id = 3;
}

message SC_ARENACO_BYPASS_KICK_NOTI {
  required uint32 room_id = 1;
  required int64 kick_arena_char_id = 2;
  required bytes kick_char_name = 3;
}

message ArenaActorInfo {
  required int64 arena_char_id = 1;
  required .LIN.ArenaUserInfo.eRole role = 2;
  optional uint32 server_id = 3;
  optional bytes character_name = 4;
  optional .LIN.CharacterClass character_class = 5;
  optional .LIN.Gender gender = 6;
  optional .LIN.eArenaTeam team_id = 7;
  optional int32 marker_id = 8;
}

message ArenaPlayerStatus {
  required int64 arena_char_id = 1;
  optional uint32 kill_count = 2;
  optional uint32 death_count = 3;
  optional uint32 hp_ratio = 4;
  optional uint32 mp_ratio = 5;
  optional int32 loc_x = 6;
  optional int32 loc_y = 7;
  optional uint32 attack_amount = 8;
  optional uint32 damaged_amount = 9;
  optional uint32 heal_amount = 10;
  optional bool poisoned = 11;
  optional bool paralysed = 12;
  optional bool is_live = 13;
  optional uint32 obj_id = 14;
  optional bool is_ready = 15;
}

message RoundScoreBoard {
  message RoundScore {
    required bool is_win = 1;
    optional uint32 attack_amount = 2;
  }
  repeated .LIN.RoundScoreBoard.RoundScore team_a = 1;
  repeated .LIN.RoundScoreBoard.RoundScore team_b = 2;
}

message ArenaGameStatus {
  message TeamStatus {
    required .LIN.eArenaTeam team_id = 1;
    optional uint32 cheer_msg_count = 2;
    optional uint32 team_kill_count = 3;
  }
  optional uint32 play_time_msec = 1;
  optional bool is_timer_run = 2;
  optional uint32 observer_count = 3;
  optional .LIN.RoundScoreBoard round_score_board = 4;
  repeated .LIN.ArenaGameStatus.TeamStatus team_status = 5;
}

message SC_ARENA_GAME_INFO_NOTI {
  repeated .LIN.ArenaActorInfo player_info = 1;
  required .LIN.eArenaMapKind map_kind = 2;
  required uint32 round = 3;
  required uint32 round_time_sec = 4;
  required uint32 result_display_time_sec = 5;
}

message SC_ARENA_PLAY_STATUS_NOTI {
  repeated .LIN.ArenaPlayerStatus player_status = 1;
  optional .LIN.ArenaGameStatus game_status = 2;
}

message SC_ARENA_OBJECT_STATUS_NOTI {
  message Tower {
    required uint32 obj_id = 1;
    optional .LIN.eArenaTeam team_id = 2;
    optional int32 hp_ratio = 3;
  }
  repeated .LIN.SC_ARENA_OBJECT_STATUS_NOTI.Tower towers = 1;
}

message SC_ARENA_PLAY_EVENT_NOTI {
  enum eType {
    GameForfeited = 1;
    GameCountDown3Sec = 2;
    GameCountDown10Sec = 3;
    PlayerEntered = 11;
    PlayerOut = 12;
    PlayerDead = 13;
    PlayerSpellSucceded = 14;
    YourSelfEntered = 21;
    BattleHunterWin = 31;
    TeamTowerBroke = 41;
  }
  required .LIN.SC_ARENA_PLAY_EVENT_NOTI.eType type = 1;
  optional .LIN.ArenaActorInfo actor_info = 10;
  optional int64 arena_char_id_target = 20;
  optional int64 arena_char_id_causer = 21;
  optional .LIN.eArenaTeam team_id = 30;
  optional uint32 spell_id = 40;
}

message SC_ARENA_PLAY_RESULT_NOTI {
  enum eReason {
    None = 1;
    AttackAmount = 2;
  }
  required .LIN.ArenaResult result = 1;
  required int32 remain_time_sec = 2;
  required .LIN.SC_ARENA_PLAY_RESULT_NOTI.eReason reason = 3;
}

message CS_ARENA_PLAY_EXIT_REQ {
}

message CS_ARENA_CHEER_REQ {
  required .LIN.eArenaTeam to_team_id = 1;
  required string text_msg = 2;
}

message SC_ARENA_CHEER_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
  }
  required .LIN.SC_ARENA_CHEER_ACK.eResult result = 1;
}

message ArenaCheer {
  enum eConst {
    MAX_MSG_BYTE_LENGTH = 50;
    MAX_CHEER_COUNT = 10000;
  }
  required int32 server_id = 1;
  required string sender = 2;
  required .LIN.CharacterClass character_class = 3;
  required string msg = 4;
  required int32 received_time = 5;
}

message SC_ARENA_CHEER_NOTI {
  required .LIN.eArenaTeam team_id = 1;
  repeated .LIN.ArenaCheer cheers = 2;
  required int32 total_count = 3;
}

message CS_ARENA_CHEER_LIST_REQ {
  required .LIN.eArenaTeam team_id = 1;
}

message SC_ARENA_CHEER_LIST_NOTI {
  required .LIN.eArenaTeam team_id = 1;
  repeated .LIN.ArenaCheer cheers = 2;
  optional bool begin = 3;
  optional bool end = 4;
}

message CS_ARENA_CHAT_BLOCK_REQ {
  required int64 arena_char_id = 1;
  required bool is_blocked = 2;
}

message SC_ARENA_CHAT_BLOCK_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
  }
  required .LIN.SC_ARENA_CHAT_BLOCK_ACK.eResult result = 1;
  required int64 arena_char_id = 2;
}

message CS_ARENA_OBSERVER_TELEPORT_REQ {
  optional int64 player_arena_char_id = 1;
  optional int32 x = 2;
  optional int32 y = 3;
}

message SC_ARENA_OBSERVER_TELEPORT_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL_TARGET_PLAYER_NOT_EXISTED = 2;
    FAIL_TARGET_PLAYER_LEAVE_GAME = 3;
    FAIL_TARGET_PLAYER_BECAME_OBSERVER = 4;
    FAIL_CANT_GO_INVALID_LOCATION = 5;
    FAIL_INVALID_REQUEST = 6;
  }
  required .LIN.SC_ARENA_OBSERVER_TELEPORT_ACK.eResult result = 1;
}

message CS_ARENA_PLAY_READY_REQ {
}

message SC_ARENA_PLAY_READY_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
  }
  required .LIN.SC_ARENA_PLAY_READY_ACK.eResult result = 1;
}

message SC_SCRIPT_PRESENTATION_NOTI {
  enum eType {
    ArenaMapSelect = 1;
  }
  required .LIN.SC_SCRIPT_PRESENTATION_NOTI.eType type = 1;
  repeated int32 args = 2;
}
~~~

# 부록 E. 원본 Team Battle Arena 등록 주소 및 클래스 요약

~~~text
TypeDescriptor:
  ILitoBattleModeControl=0x015793D4
  TeamBattleControl=0x015793FC
  PVPBattleControl=0x01579C04
ClassHierarchyDescriptor:
  TeamBattleControl=0x0137BA70, baseArray=0x0137BA80
    TeamBattleControl=0x0137BA8C, ILitoBattleModeControl=0x0137BA40
  PVPBattleControl=0x0137C404, baseArray=0x0137C414
    PVPBattleControl=0x0137C420, ILitoBattleModeControl=0x0137BA40
UI init:
  0x007F6510
  0x007F65AF PvpBattle
  0x007F66A1 TeamBattle
  0x007F672F GameCountDown
  0x007F6756 ScoreBoard
  0x007F6768 MapRouletteControl
  0x007F6AC0 TeamBattleControl dynamic_cast + 0x007BD3B0
  0x007F7330 TeamBattleControl dynamic_cast + 0x007BF510
CommonPacket name registry:
  0x00A0B9DF key=0x002F SC_TEAM_BATTLE_ARENA_TYPE_INFO
  0x00A0BA01 key=0x0030 SC_TEAM_BATTLE_ARENA_ROOM_INFO
  0x00A0BA23 key=0x0031 SC_TEAM_BATTLE_ARENA_RANKER_INFO
  0x00A0BAAB key=0x0035 SC_TEAM_BATTLE_ARENA_PLAYER_MARKER
~~~

# 부록 F. 자동 매칭 원본 protobuf 전문

출처: [MainServer_Client.proto](../MainServerProto/schemas/MainServer_Client.proto)

~~~proto
message SC_BATTLE_ARENA_AUTO_MATCH_REQ {
  required int32 arena_id = 1;
}

message CS_BATTLE_ARENA_AUTO_MATCH_ACK {
  required int32 arena_id = 1;
  required bool is_apply = 2;
}

message SC_BATTLE_ARENA_AUTO_MATCH_RESULT_NOTI {
  enum eResultCode {
    eAUTO_MATCH_RESULT_MATCH_SUCCEED = 0;
    eAUTO_MATCH_RESULT_OTHER_REJECT = 1;
    eAUTO_MATCH_RESULT_ERROR = 2;
  }
  required int32 arena_id = 1;
  required .LIN.SC_BATTLE_ARENA_AUTO_MATCH_RESULT_NOTI.eResultCode result = 2;
}
~~~

---

## 검증 내역 및 남은 한계

**이번 작업에서 실제 검증:** 복호화된 이미지 해시, 문자열·RTTI, 양쪽 ClassHierarchyDescriptor와 부모 BaseClassDescriptor, x86 코드 내 UI 객체명/가상 호출, Team Battle Arena 번호 등록값, ArenaCo/자동매칭 proto 원문 전량 대조.

**실제 실행/검증 미실시:** 원본 클라이언트 기동 및 스크린샷, 전 패킷 실제 wire 번호, CommonPacket 수신 본문, 모든 네트워크→UI XREF, 원본 ArenaCo 서버 코드, 통합 런타임·경기 실전.

**최종 결론:** 팀전과 PvP는 리토 배틀의 내부 모드라는 점이 확정됐다. 투기장 정보·자동 매칭·ArenaCo 통신까지 남아 있어 복원 자료가 풍부하며, **완전한 결합 전에 서로 다른 패킷 층의 호출 관계를 구체적으로 확인**해야 한다. 임의로 원본 규칙을 만들지 않으며, 기존 Java/DB/스키마를 변경하지 않았다.
