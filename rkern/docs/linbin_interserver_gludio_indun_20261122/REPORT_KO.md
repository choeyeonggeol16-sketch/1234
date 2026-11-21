# mjlin 원본 인터서버·글루디오 인던(ArenaCo/Indun) 전체 복원 기준 보고서

작성일: 2026-11-22  
원본: mjlin_loaded_code_00400000_0180D000.bin / 21,024,768바이트  
SHA-256: 256f7886e98d7d2c573ab76917f78e30dc4f34cb723a002d604602cdb2f6cbdb (실물 검증)  
목적: 향후 RKern에서 **린빈의 기존 프로토콜·UI·서버전환 동작을 그대로 복원**할 때 사용할 보존용 분석 자료. 이 문서는 기존 서버의 대체 설계를 권하는 문서가 아니다.

## 1. 확인 범위

- **바이너리 직접 확인:** InterServerNetwork 연결/인증/이동 로그, HIBREED 관련 문자열, IndunUI/IndunSystem RTTI, GameGate 이름, 인던 클라이언트 UI 리소스.
- **원본에서 복원한 프로토콜:** MainServer_Client_Indun.proto의 메시지 **27개 전부**, 공통 인던 모델, HIBREED 관련 메시지 4개, GameGate 토큰 2개와 오류 메시지, ArenaCo 경기/시간/점수 이벤트 자료.
- **원본과 아직 연결 검증되지 않은 사항:** 각 메시지의 실제 클라이언트 네트워크 ID, 클라이언트의 수신 디스패처→UI 호출 XREF, 인던 입장이 HIBREED를 사용하는 구체적 조건, 원본 서버 측 시간/웨이브/보상 규칙.
- 아래 **부록 A~E에 실제 복원 proto 원문(필드 번호·타입·required/optional/repeated·enum 값)을 그대로 수록**하였으며 임의로 항목을 만들거나 오탈자를 정정하지 않았다.
- 복원 원문은 바이너리 내부 FileDescriptorProto에서 재구성한 것이다. NC가 배포한 원본 소스 파일이라고 주장하지 않는다.
- 주의: **protobuf field number는 네트워크 opcode/msgId가 아니다.**

## 2. 인터서버 실행 이미지 로그 — 직접 확인한 VA

| 가상 주소 | 바이너리 원문 문자열 |
|---|---|
| 0x011EE514 | InterServerNetwork::Connect(%s) |
| 0x011EE7E0 | interserver - time out. |
| 0x011EE810 | interserver - disconnect. |
| 0x011EE834 | interserver - connect. (hostName) |
| 0x011EE860 | interserver - connect fail. (hostName) try again with ip address |
| 0x011EE8AC | interserver - connect. ip addr |
| 0x011EE8D4 | interserver - connect fail. ip addr |
| 0x011EE900 | interserver - connect. |
| 0x011EE920 | interserver - connect fail. |
| 0x011EE944 | interserver - request. |
| 0x011EE964 | interserver - complete change server. |
| 0x011EE994 | interserver - event key. |
| 0x011EE9B8 | interserver - start done. |
| 0x011EE9DC | interserver - request auth. |
| 0x011F797C | ReceiveConnectHibreedServerNoti |
| 0x0120993C | SC_CONNECT_HIBREEDSERVER_NOTI |
| 0x01209978 | CS_HIBREED_AUTH_REQ |
| 0x0120998C | SC_HIBREED_AUTH_ACK |
| 0x0127CF7E | SC_CONNECT_HIBREEDSERVER_NOTI_PACKET |
| 0x0127D038 | CS_HIBREED_AUTH_REQ_PACKET |
| 0x0127D084 | SC_HIBREED_AUTH_ACK_PACKET |
| 0x012843BC | hibreed_inter_server_version |

**확인되는 사실:** 호스트명으로 연결하고 실패 시 IP 주소로 재시도하는 분기, 연결·타임아웃·인증·변경 완료와 관련된 실행 코드 문자열이 존재한다. 위 주소는 문자열 주소이며 함수의 시작 주소가 아니다.

### 2.1 인터서버 메시지 4개 — 원문 필드 계약

**SC_CONNECT_HIBREEDSERVER_NOTI_PACKET / 서버→클라이언트**

| field | 원문 이름 | 타입·카디널리티 | 용도 |
|---:|---|---|---|
| 1 | destIP | required uint32 | 목적지 IP |
| 2 | destPort | required int32 | 목적지 포트 |
| 3 | reservednumber | required uint32 | 전환 예약 번호 |
| 4 | onetimetoken | required bytes | 일회용 토큰 |
| 5 | interkind | optional int32 | 인터서버 유형 |
| 6 | domainname | optional string | 호스트명 |
| 7 | worldmove | optional bool, default false | 월드 이동 |

**CS_HIBREED_AUTH_REQ_PACKET / 클라이언트→대상 서버:** field 1 reservednumber(required uint64), field 2 onetimetoken(required string).

**SC_HIBREED_AUTH_ACK_PACKET / 대상 서버→클라이언트:** field 1 result(required LIN.Result).

**CS_MOVE_LOGOUT_NOTI_PACKET / 클라이언트→서버:** field 1 reservednumber(required uint64).

중요: 예약 번호는 SC에서 uint32, CS에서 uint64이며, 토큰은 SC에서 bytes, CS에서 string이다. 원본을 구현할 때 타입을 임의 통합하지 않는다.

### 2.2 메시지 계약상 가능한 전환 흐름 — 실행순서 미확정

1. 기존 접속 서버가 전환 목적지와 토큰을 담은 SC_CONNECT_HIBREEDSERVER_NOTI_PACKET을 보냄.
2. 클라이언트의 InterServerNetwork가 대상 host/IP 접속을 시도함.
3. 새 소켓에서 CS_HIBREED_AUTH_REQ_PACKET으로 예약 번호와 토큰을 보냄.
4. 대상 서버가 SC_HIBREED_AUTH_ACK_PACKET으로 인증 결과를 보냄.
5. 기존 세션 처리 및 새 월드 진입; CS_MOVE_LOGOUT_NOTI_PACKET의 정확한 송신 시점과 연결 끊는 순서는 미확인.

이 순서는 실제 실행 함수들의 전 구간 XREF로 증명한 것이 아닌 **proto 계약 기반 시퀀스 후보**다. HIBREED는 반드시 사용자 상태·계정·아이템 확정성 및 중복 로그인 방어와 연동돼야 하지만, 그 서버 내부 구현은 본 클라이언트 바이너리의 증거만으로 알 수 없다.

### 2.3 GameGate는 별개의 원본 계약

- VA 0x01177280: MainServer_Client_GameGate.proto
- VA 0x011773C3: SC_GAMEGATE_INSTANT_TOKEN_NOTI
- VA 0x011773FF: CS_GAMEGATE_INSTANT_TOKEN_REQ
- SC_GAMEGATE_INSTANT_TOKEN_NOTI: field 1 instant_token_id(required string)
- CS_GAMEGATE_INSTANT_TOKEN_REQ: field 1 app_id(required string)
- SC_GAME_GATE_ERROR_NOTI: errorType(enum), errorCode(int32), errorModule(int32), errorLine(int32)
- 오류 타입: GGE_ERROR_ETC=0, GGE_AUTH_LOGINTOKEN=1, GGE_AUTH_REQUESTTOKEN=2, GGE_GRADE_LISTGRADES=3.

GameGate의 instant_token_id와 HIBREED의 onetimetoken이 같은 토큰이라고 **단정하지 않는다.** 통신 경로의 직접 연결은 XREF/실행 확인 필요.

## 3. 인던 클라이언트 UI·RTTI — 바이너리에서 확인한 주소

| VA | 문자열 | 증거 |
|---|---|---|
| 0x0117E3C4 | IndunAlarmUI | UI 등록 이름 |
| 0x0117E3E0 | IndunListScroll | UI 하위 구성 |
| 0x0117E770 | indunMapInfo.xml | XML 리소스 이름 |
| 0x0117E784 | indun_info | 데이터/리소스 키 |
| 0x012B7B18 | IndunAlarmUI | UI 이름 |
| 0x012B7B28 | IndunUI | 인던 UI 이름 |
| 0x012BFB20 | getinduninfo | 인던 정보 명령 이름 |
| 0x0156F324 | .?AVIndunAlarmUI@@ | C++ RTTI |
| 0x0156F47C | .?AVIndunMapInfo@@ | C++ RTTI |
| 0x0156F498 | .?AVIndunSystem@@ | C++ RTTI |
| 0x0156F6DC | .?AVIndunUI@@ | C++ RTTI |
| 0x01597CDC | .?AVCommandgetinduninfo@Lineage@@ | C++ RTTI |

이들은 단순 서버 측 예상 UI가 아니라 **클라이언트 실행 이미지에 실존하는 문자열/RTTI**다. 다만 주소 자체가 UI 생성 함수 포인터나 패킷 수신 함수 주소는 아니다.

## 4. 원본 인던 메시지 27종 — 전부 나열

1. **CS_ARENACO_BYPASS_INDUN_ROOM_LIST_REQ** — 클라이언트→서버
2. **SC_ARENACO_BYPASS_INDUN_ROOM_LIST_ACK** — 서버→클라이언트
3. **CS_ARENACO_BYPASS_INDUN_ROOM_INFO_REQ** — 클라이언트→서버
4. **SC_ARENACO_BYPASS_INDUN_ROOM_INFO_ACK** — 서버→클라이언트
5. **CS_ARENACO_CREATE_INDUN_ROOM_REQ** — 클라이언트→서버
6. **SC_ARENACO_CREATE_INDUN_ROOM_ACK** — 서버→클라이언트
7. **CS_ARENACO_BYPASS_CHANGE_INDUN_ROOM_PROPERTY_REQ** — 클라이언트→서버
8. **SC_ARENACO_BYPASS_CHANGE_INDUN_ROOM_PROPERTY_ACK** — 서버→클라이언트
9. **SC_ARENACO_BYPASS_CHANGE_INDUN_ROOM_STATUS_NOTI** — 서버→클라이언트
10. **CS_ARENACO_ENTER_INDUN_ROOM_REQ** — 클라이언트→서버
11. **SC_ARENACO_ENTER_INDUN_ROOM_ACK** — 서버→클라이언트
12. **SC_ARENACO_BYPASS_ENTER_INDUN_ROOM_NOTI** — 서버→클라이언트
13. **CS_ARENACO_BYPASS_CHANGE_INDUN_READY_ENTER_REQ** — 클라이언트→서버
14. **SC_ARENACO_BYPASS_CHANGE_INDUN_READY_ENTER_ACK** — 서버→클라이언트
15. **CS_ARENACO_BYPASS_INDUN_GAME_START_REQ** — 클라이언트→서버
16. **SC_ARENACO_BYPASS_INDUN_GAME_START_ACK** — 서버→클라이언트
17. **CS_ARENACO_INVITE_INDUN_ROOM_REQ** — 클라이언트→서버
18. **SC_ARENACO_INVITE_INDUN_ROOM_ACK** — 서버→클라이언트
19. **CS_ARENACO_BYPASS_INDUN_NOTI_REQ** — 클라이언트→서버
20. **SC_ARENACO_BYPASS_INDUN_NOTI_ACK** — 서버→클라이언트
21. **CS_ARENACO_EXIT_INDUN_ROOM_REQ** — 클라이언트→서버
22. **SC_ARENACO_EXIT_INDUN_ROOM_ACK** — 서버→클라이언트
23. **SC_ARENACO_BYPASS_EXIT_INDUN_ROOM_NOTI** — 서버→클라이언트
24. **CS_ARENACO_BYPASS_INDUN_KICK_REQ** — 클라이언트→서버
25. **SC_ARENACO_BYPASS_INDUN_KICK_ACK** — 서버→클라이언트
26. **SC_ARENACO_BYPASS_INDUN_KICK_NOTI** — 서버→클라이언트
27. **SC_INDUN_TOWER_HIT_POINT_RATIO_NOTI** — 서버→클라이언트

위 27개 전부의 **구체적 field 번호·데이터 타입·enum 수치·required/optional 값은 부록 A에 생략 없이 첨부**했다.

### 4.1 원본 인던 서버→클라 데이터 모델

- **IndunBuildInfo:** title(1 bytes), min_level(2 uint32), fee(3 uint32), key_item_id(4 uint32), distribution_type(5 enum), closed(6 bool), max_player(7 uint32), password(8 optional string), map_kind(9 enum), server_no(10 optional int32).
- **IndunRoomInfo:** room_id(1), title(2), member_count_cur(3), member_count_max(4), member_count_min(5), map_kind(6), closed(7), min_level(8), fee(9), distribution_type(10), is_playing(11 optional), is_locked(12 optional).
- **IndunRoomDetailInfo:** min_level(1), dungeon_type(2), max_player(3), distribution_type(4), condition(5), map_kind(6).
- **IndunEnterCondition:** fee(1 optional), key_item_id(2 optional).
- **IndunNotiInfo:** map_kind(1), count(2).
- **IndunInfoForClient:** keyItemId(1), minPlayer(2), maxPlayer(3), minAdena(4), maxAdena(5), minLevel(6 repeated).
- **SC_INDUN_TOWER_HIT_POINT_RATIO_NOTI:** object_id(field 1, required uint32), hp_ratio(field 2, required int32).

### 4.2 원본 인던 콘텐츠 타입

공통 enum **eArenaMapKind**는 OrimLab_Minor=201, OrimLab_Normal=202, OrimLab_Major=203을 포함한다. **원본에는 3단계 표기가 모두 존재**한다. 단, Major의 완전한 콘텐츠 동작·보상과 NPC 데이터는 별도 분석이 필요하다.

**eDistributionType:** AUTO_DISTRIBUTION=1, GET_PRIORITY=2.

### 4.3 원본 인던 흐름 — 패킷 계약 기준

~~~text
공지/목록:
  CS_ARENACO_BYPASS_INDUN_NOTI_REQ → SC_ARENACO_BYPASS_INDUN_NOTI_ACK
  CS_ARENACO_BYPASS_INDUN_ROOM_LIST_REQ → SC_ARENACO_BYPASS_INDUN_ROOM_LIST_ACK
  CS_ARENACO_BYPASS_INDUN_ROOM_INFO_REQ → SC_ARENACO_BYPASS_INDUN_ROOM_INFO_ACK
방 운영:
  CS_ARENACO_CREATE_INDUN_ROOM_REQ → SC_ARENACO_CREATE_INDUN_ROOM_ACK
  CS_ARENACO_BYPASS_CHANGE_INDUN_ROOM_PROPERTY_REQ → ...PROPERTY_ACK
  CS_ARENACO_ENTER_INDUN_ROOM_REQ → SC_ARENACO_ENTER_INDUN_ROOM_ACK
  SC_ARENACO_BYPASS_CHANGE_INDUN_ROOM_STATUS_NOTI
  SC_ARENACO_BYPASS_ENTER_INDUN_ROOM_NOTI
  CS_ARENACO_BYPASS_CHANGE_INDUN_READY_ENTER_REQ → ...READY_ENTER_ACK
  CS_ARENACO_INVITE_INDUN_ROOM_REQ → SC_ARENACO_INVITE_INDUN_ROOM_ACK
  CS_ARENACO_BYPASS_INDUN_KICK_REQ → ...KICK_ACK / ...KICK_NOTI
시작/전투:
  CS_ARENACO_BYPASS_INDUN_GAME_START_REQ → ...GAME_START_ACK
  SC_ARENA_GAME_INFO_NOTI / SC_ARENA_PLAY_STATUS_NOTI
  SC_INDUN_TOWER_HIT_POINT_RATIO_NOTI
퇴장:
  CS_ARENACO_EXIT_INDUN_ROOM_REQ → SC_ARENACO_EXIT_INDUN_ROOM_ACK
  SC_ARENACO_BYPASS_EXIT_INDUN_ROOM_NOTI
~~~

일반적 요청→응답 대응 관계를 표현한 것이며 **패킷 핸들러 호출 순서 확정 문서는 아니다**. 일반 ArenaCo 대전 패킷과 Indun 전용 패킷은 용도를 구분한다.

### 4.4 인던과 공통 ArenaCo 경기 타이머

- **SC_ARENA_GAME_INFO_NOTI:** player_info(1 repeated), map_kind(2), round(3), round_time_sec(4 **초**), result_display_time_sec(5 **초**)
- **SC_ARENA_PLAY_STATUS_NOTI:** player_status(1 repeated), game_status(2 optional)
- **ArenaGameStatus:** play_time_msec(1 **밀리초**), is_timer_run(2 bool), observer_count(3), round_score_board(4), team_status(5 repeated).
- **SC_ARENA_PLAY_EVENT_NOTI.eType:** GameForfeited=1, GameCountDown3Sec=2, GameCountDown10Sec=3, PlayerEntered=11, PlayerOut=12, PlayerDead=13, PlayerSpellSucceded=14, YourSelfEntered=21, BattleHunterWin=31, TeamTowerBroke=41.
- **SC_ARENA_PLAY_RESULT_NOTI:** result(1), remain_time_sec(2), reason(3).
- **SC_ARENA_OBJECT_STATUS_NOTI:** Tower 목록; object ID·팀·HP ratio.

이 스키마가 클라이언트의 시간·스코어 UI를 표현할 수 있는 자료다. **원본이 인던 화면에서 각각 어떤 조건에 보내는지는 구현 이전에 직접 추적**해야 한다.

## 5. 최신 RKern과의 차이 기록 — 원본과 혼동 금지

이번에 직접 확인한 GitHub Java 파일: GludioInstanceDungeon.java, GludioDungeonPackets.java, DominationHybrid.java, CSProto.java.

| 대상 | 최신 RKern에서 확인한 사실 | 원본 기준 주의 |
|---|---|---|
| 인던 메시지 ID | GludioDungeonPackets.java에 0x08A3~0x08BB 선언 | **RKern에 적힌 숫자**이며 이번 바이너리 디스패처로 각각 독립 확인한 수치가 아님 |
| 인던 라우팅 | CSProto.java에서 11개 요청 등록 | 원본 27개 중 클라 요청과 모든 서버 통지의 동작은 별도 검증 |
| 인던 콘텐츠 | GludioInstanceDungeon.java에 900초/최대 6명/최대 100방·키 4200254·맵 730/731·맵 14301~14400 | 모두 **RKern 구현 수치**, 원본 바이너리 서버 규칙으로 오인 금지 |
| 원본 난이도 | 인던 공통 enum에 201 Minor, 202 Normal, 203 Major | 현재 RKern GludioInstanceDungeon에는 Minor/Normal만 선언 |
| 룸 정보 | 원본 IndunBuildInfo의 key_item_id=4, server_no=10 | 현재 읽기는 일부 자체 규칙 사용·field 13 boost 확장 존재 |
| 초대 | 원본 초대 요청에 need_password/condition 등 상세 정보 | 현재는 일부만 읽고 SC_MessageYN을 통한 로컬 수락 흐름 |
| 게임 시작 | CacheD 원자적 열쇠 차감 확인 후 성공 ACK와 텔레포트 | **현재 구현**. 원본 전환이 HIBREED인지 동일소켓인지 실제 경로 미확인 |
| 시간 단위 | 원본 ArenaGameStatus.play_time_msec = 밀리초 | 현재 encodeArenaPlayStatus(int seconds)가 초 값을 field 1에 그대로 쓰므로 단위 비교 필요 |
| 경기 정보 UI | SC_ARENA_GAME_INFO_NOTI 함수는 코드에 있지만 현재 tick은 status 위주 | 원본 UI 생성/갱신 핸드셰이크와 1:1 비교 필요 |
| 인터서버 시험 | DominationHybrid.java는 GM 전용 single-server 재접속 시험 | 운영용 전체 다중 서버 구축물 아님; 특정 kind는 일반 텔레포트 fallback |
| GameGate | 복원 원본 토큰 proto는 존재 | 온전한 서버 간 GameGate 인증 아키텍처 구현 여부 미확인 |

이 차이는 **원본을 그대로 구현하려면 반드시 참조해야 할 격차 목록**이며, 현 기능 삭제·수정 제안이 아니다. 다른 아키텍처를 창작해서 원본 대신 넣지 않는다.

## 6. 이후 원본 복원에 필요한 확인

1. 인던 메시지 27개 각각의 **실제 숫자 wire ID**와 송신/수신 등록부.
2. IndunUI/IndunAlarmUI/IndunSystem의 **수신 콜백→UI 생성·오픈·닫기·갱신 호출** XREF.
3. 원본 OrimLab_Minor/Normal/Major의 **인원/열쇠/시간/보상/몬스터 스폰/실패 판정**.
4. 인던 게임 시작 시 **HIBREED 연결을 하는지**, 하는 경우 어떤 interkind와 token·worldmove를 쓰는지.
5. InterServerNetwork의 hostname/IP retry·접속 종료·인증·세션 소유 이전의 실제 호출·실행 스레드.
6. GameGate와 HIBREED의 별도 토큰 계약의 **상호 관계와 인증 책임 주체**.
7. CS_MOVE_LOGOUT_NOTI_PACKET이 old/new 어느 소켓에서 언제 전송되는지.
8. 타이머 **seconds vs msec** 원문 처리와 SC_ARENA_GAME_INFO_NOTI/STATUS_NOTI의 정확한 송신 순서.
9. 계정 중복 로그인 방지·서버 이동 중 아이템 거래/로그아웃/장애 복구의 원본 처리.

---

# 부록 A. 인던 프로토콜 27개 전문 — 실제 복원 원문

원천: [MainServer_Client_Indun.proto](../MainServerProto/schemas/MainServer_Client_Indun.proto). 필드·enum·메시지 순서를 변경하지 않고 그대로 보관한다.

~~~proto
// Recovered from mjlin_loaded_code_00400000_0180D000.bin, SHA-256 256f7886e98d7d2c573ab76917f78e30dc4f34cb723a002d604602cdb2f6cbdb
// Source: embedded FileDescriptorProto, NOT NC-authored source file.
// package/message/field/enum/number/cardinality derived directly from binary descriptor.
// syntax="proto2" reconstructed from descriptor required/optional semantics.
syntax = "proto2";
package LIN;

import "Lineage_CommonDataTypes.proto";

message CS_ARENACO_BYPASS_INDUN_ROOM_LIST_REQ {
  required uint32 page_id = 1 [default = 1];
  optional .LIN.eArenaMapKind map_kind = 2 [default = None];
}

message SC_ARENACO_BYPASS_INDUN_ROOM_LIST_ACK {
  enum eResult {
    SUCCESS = 1;
    ERROR_INTENAL = 2;
    ERROR_NOT_EXIST_USER = 3;
    ERROR_NOT_EXIST_ROOM = 4;
    ERROR_ARENACO_DISCONNECTED = 5;
  }
  required .LIN.SC_ARENACO_BYPASS_INDUN_ROOM_LIST_ACK.eResult result = 1;
  required uint32 page_id = 2;
  required uint32 total_page = 3;
  repeated .LIN.IndunRoomInfo room_info = 4;
}

message CS_ARENACO_BYPASS_INDUN_ROOM_INFO_REQ {
  required uint32 room_id = 1;
}

message SC_ARENACO_BYPASS_INDUN_ROOM_INFO_ACK {
  enum eResult {
    SUCCESS = 1;
    ERROR_INTENAL = 2;
    ERROR_NOT_EXIST_USER = 3;
    ERROR_NOT_EXIST_ROOM = 4;
  }
  required .LIN.SC_ARENACO_BYPASS_INDUN_ROOM_INFO_ACK.eResult result = 1;
  optional .LIN.IndunRoomDetailInfo room_info = 2;
}

message CS_ARENACO_CREATE_INDUN_ROOM_REQ {
  required .LIN.IndunBuildInfo build_info = 1;
}

message SC_ARENACO_CREATE_INDUN_ROOM_ACK {
  enum eResult {
    SUCCESS = 1;
    ERROR_INTERNAL = 2;
    ERROR_NOT_EXIST_USER = 3;
    ERROR_EXCEED_MAX_ROOM_COUNT = 4;
    ERROR_EXCEED_MAX_ARENA_COUNT = 5;
    ERROR_CANNOT_CREATE_ARENA = 6;
    ERROR_INVALID_TITLE = 7;
    ERROR_PASSWORD = 8;
    ERROR_TEAM_COUNT = 9;
    ERROR_TEAM_MEMBER_COUNT = 10;
    ERROR_ALLOCATE_ARENA = 11;
    ERROR_JOIN_ROOM = 12;
    ERROR_OTHER_ROOM_ENTERED = 13;
    ERROR_LEVEL = 14;
    ERROR_NOT_ENOUGH_KEY = 15;
    ERROR_INVALID_FEE = 16;
    ERROR_ENTER_LIMIT = 17;
    ERROR_INDUN_BLOCK = 18;
  }
  required .LIN.SC_ARENACO_CREATE_INDUN_ROOM_ACK.eResult result = 1;
  optional uint32 room_id = 2;
}

message CS_ARENACO_BYPASS_CHANGE_INDUN_ROOM_PROPERTY_REQ {
  required uint32 room_id = 1;
  required bytes title = 2;
  required int32 min_level = 3;
  required bool closed = 4;
  required string password = 5;
  required .LIN.eDistributionType distribution_type = 6;
  required int32 max_player = 7;
  required .LIN.eArenaMapKind map_kind = 8;
}

message SC_ARENACO_BYPASS_CHANGE_INDUN_ROOM_PROPERTY_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
    FAIL_NOT_EXIST_USER = 3;
    FAIL_TITLE = 4;
    FAIL_MIN_LEVEL = 5;
    FAIL_CLOSED = 6;
    FAIL_PASSWORD = 7;
    FAIL_DISTRIBUTION_TYPE = 8;
    FAIL_MAX_PLAYER = 9;
    FAIL_INVALID_ROOM_STATUS = 10;
    FAIL_NOT_FOUND_ROOM = 11;
    FAIL_INVALID_ROOMID = 12;
    FAIL_NOT_OWNER = 13;
  }
  required .LIN.SC_ARENACO_BYPASS_CHANGE_INDUN_ROOM_PROPERTY_ACK.eResult result = 1;
  required uint32 room_id = 2;
}

message SC_ARENACO_BYPASS_CHANGE_INDUN_ROOM_STATUS_NOTI {
  required uint32 room_id = 1;
  required uint32 observer_count = 2;
  repeated .LIN.ArenaUserInfo player_info = 3;
  optional .LIN.IndunRoomInfo room_info = 4;
}

message CS_ARENACO_ENTER_INDUN_ROOM_REQ {
  required uint32 room_id = 1;
  optional string password = 2;
  optional bool invite_accept = 3;
  optional bytes host_char_name = 4;
}

message SC_ARENACO_ENTER_INDUN_ROOM_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
    FAIL_TO_ALREADY_PLAY = 3;
    FAIL_NOT_ENOUGH_MONEY = 4;
    FAIL_INVALID_PASSWORD = 5;
    FAIL_INVALID_ROOM_STATUS = 6;
    FAIL_NOT_FOUND_ROOM = 7;
    FAIL_FULL = 8;
    FAIL_ALREADY_ENTERED_PLAYER = 9;
    FAIL_OTHER_ROOM_ENTERED = 10;
    FAIL_LEVEL = 11;
    FAIL_INVALID_SERVER = 12;
    FAIL_ENTER_LIMIT = 13;
    FAIL_INDUN_BLOCK = 14;
  }
  required .LIN.SC_ARENACO_ENTER_INDUN_ROOM_ACK.eResult result = 1;
  required uint32 room_id = 2;
}

message SC_ARENACO_BYPASS_ENTER_INDUN_ROOM_NOTI {
  required uint32 room_id = 1;
  required .LIN.ArenaUserInfo user_info = 2;
}

message CS_ARENACO_BYPASS_CHANGE_INDUN_READY_ENTER_REQ {
  required uint32 room_id = 1;
  required bool ready = 2;
  optional uint64 own_adena = 3;
  optional uint32 user_level = 4;
}

message SC_ARENACO_BYPASS_CHANGE_INDUN_READY_ENTER_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
    FAIL_NOT_ENOUGH_MONEY = 3;
    FAIL_LEVEL = 4;
  }
  required .LIN.SC_ARENACO_BYPASS_CHANGE_INDUN_READY_ENTER_ACK.eResult result = 1;
  required uint32 room_id = 2;
  required bool ready = 3;
}

message CS_ARENACO_BYPASS_INDUN_GAME_START_REQ {
  required uint32 room_id = 1;
  required .LIN.eArenaMapKind map_kind = 2;
  optional uint32 user_level = 3;
}

message SC_ARENACO_BYPASS_INDUN_GAME_START_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
    FAIL_INVALID_USER = 3;
    FAIL_INVALID_ROOM = 4;
    FAIL_INVALID_OWNER = 5;
    FAIL_INVALID_STATE = 6;
    FAIL_ISSUE_PLAY = 7;
    FAIL_NOT_ENOUGH_KEY = 8;
    FAIL_LEVEL = 9;
    FAIL_ENTER_LIMIT = 10;
    FAIL_INDUN_BLOCK = 11;
  }
  required .LIN.SC_ARENACO_BYPASS_INDUN_GAME_START_ACK.eResult result = 1;
  required uint32 room_id = 2;
}

message CS_ARENACO_INVITE_INDUN_ROOM_REQ {
  required uint32 room_id = 1;
  required int64 host_arena_char_id = 2;
  required bytes guest_char_name = 3;
  required bool need_password = 4;
  required .LIN.IndunEnterCondition condition = 5;
  optional bytes buildercaster_password = 6;
}

message SC_ARENACO_INVITE_INDUN_ROOM_ACK {
  enum eInviteResult {
    eRESULT_SUCCESS = 1;
    eRESULT_ERROR_REJECT = 2;
    eRESULT_ERROR_NOT_EXIST_USER = 3;
    eRESULT_ERROR = 4;
    eRESULT_ERROR_NOT_OPEN_TIME = 5;
  }
  required .LIN.SC_ARENACO_INVITE_INDUN_ROOM_ACK.eInviteResult result = 1;
  required uint32 room_id = 2;
  required int64 host_arena_char_id = 3;
}

message CS_ARENACO_BYPASS_INDUN_NOTI_REQ {
}

message SC_ARENACO_BYPASS_INDUN_NOTI_ACK {
  enum eResult {
    SUCCESS = 1;
    ERROR_INTENAL = 2;
    ERROR_NOT_EXIST_USER = 3;
  }
  required .LIN.SC_ARENACO_BYPASS_INDUN_NOTI_ACK.eResult result = 1;
  repeated .LIN.IndunNotiInfo noti_info = 2;
}

message CS_ARENACO_EXIT_INDUN_ROOM_REQ {
  required uint32 room_id = 1;
}

message SC_ARENACO_EXIT_INDUN_ROOM_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
  }
  required .LIN.SC_ARENACO_EXIT_INDUN_ROOM_ACK.eResult result = 1;
  required uint32 room_id = 2;
}

message SC_ARENACO_BYPASS_EXIT_INDUN_ROOM_NOTI {
  required uint32 room_id = 1;
  required .LIN.ArenaUserInfo user_info = 2;
}

message CS_ARENACO_BYPASS_INDUN_KICK_REQ {
  required uint32 room_id = 1;
  required int64 kick_arena_char_id = 2;
}

message SC_ARENACO_BYPASS_INDUN_KICK_ACK {
  enum eResult {
    SUCCESS = 1;
    FAIL = 2;
    FAIL_NOT_EXIST_KICKER = 3;
    FAIL_INVALID_KICKER = 4;
    FAIL_NOT_EXIST_TARGET = 5;
    FAIL_ROOM_STATE = 6;
    FAIL_ROOM_EXIT = 7;
    FAIL_INVALID_ROOM_TYPE = 8;
  }
  required .LIN.SC_ARENACO_BYPASS_INDUN_KICK_ACK.eResult result = 1;
  required uint32 room_id = 2;
  required int64 kick_arena_char_id = 3;
}

message SC_ARENACO_BYPASS_INDUN_KICK_NOTI {
  required uint32 room_id = 1;
  required int64 kick_arena_char_id = 2;
  required bytes kick_char_name = 3;
}

message SC_INDUN_TOWER_HIT_POINT_RATIO_NOTI {
  required uint32 object_id = 1;
  required int32 hp_ratio = 2;
}
~~~

# 부록 B. 인던 공통 타입·원본 맵 enum 전문

원천: [Lineage_CommonDataTypes.proto](../MainServerProto/schemas/Lineage_CommonDataTypes.proto).

~~~proto
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

message IndunEnterCondition {
  optional uint32 fee = 1;
  optional uint32 key_item_id = 2;
}

message IndunBuildInfo {
  required bytes title = 1;
  required uint32 min_level = 2;
  required uint32 fee = 3;
  required uint32 key_item_id = 4;
  required .LIN.eDistributionType distribution_type = 5;
  required bool closed = 6;
  required uint32 max_player = 7;
  optional string password = 8;
  required .LIN.eArenaMapKind map_kind = 9;
  optional int32 server_no = 10;
}

message IndunRoomInfo {
  required uint32 room_id = 1;
  required bytes title = 2;
  required uint32 member_count_cur = 3;
  required uint32 member_count_max = 4;
  required uint32 member_count_min = 5;
  required .LIN.eArenaMapKind map_kind = 6;
  required bool closed = 7;
  required uint32 min_level = 8;
  required uint32 fee = 9;
  required .LIN.eDistributionType distribution_type = 10;
  optional bool is_playing = 11;
  optional bool is_locked = 12;
}

message IndunRoomDetailInfo {
  required uint32 min_level = 1;
  required .LIN.eDungeonType dungeon_type = 2;
  required uint32 max_player = 3;
  required .LIN.eDistributionType distribution_type = 4;
  required .LIN.IndunEnterCondition condition = 5;
  required .LIN.eArenaMapKind map_kind = 6;
}

message IndunNotiInfo {
  required .LIN.eArenaMapKind map_kind = 1;
  required uint32 count = 2;
}

message IndunInfoForClient {
  required int32 keyItemId = 1;
  required int32 minPlayer = 2;
  required int32 maxPlayer = 3;
  required int32 minAdena = 4;
  required int32 maxAdena = 5;
  repeated int32 minLevel = 6;
}
~~~

# 부록 C. HIBREED 인터서버 이동 4종 메시지 전문

원천: [MainServer_Client.proto](../MainServerProto/schemas/MainServer_Client.proto).

~~~proto
message SC_CONNECT_HIBREEDSERVER_NOTI_PACKET {
  required uint32 destIP = 1;
  required int32 destPort = 2;
  required uint32 reservednumber = 3;
  required bytes onetimetoken = 4;
  optional int32 interkind = 5;
  optional string domainname = 6;
  optional bool worldmove = 7 [default = false];
}

message CS_HIBREED_AUTH_REQ_PACKET {
  required uint64 reservednumber = 1;
  required string onetimetoken = 2;
}

message SC_HIBREED_AUTH_ACK_PACKET {
  required .LIN.Result result = 1;
}

message CS_MOVE_LOGOUT_NOTI_PACKET {
  required uint64 reservednumber = 1;
}
~~~

# 부록 D. GameGate 토큰 + 오류 메시지 전문

원천: [MainServer_Client_GameGate.proto](../MainServerProto/schemas/MainServer_Client_GameGate.proto), [MainServer_Client.proto](../MainServerProto/schemas/MainServer_Client.proto).

~~~proto
// Recovered from mjlin_loaded_code_00400000_0180D000.bin, SHA-256 256f7886e98d7d2c573ab76917f78e30dc4f34cb723a002d604602cdb2f6cbdb
// Source: embedded FileDescriptorProto, NOT NC-authored source file.
// package/message/field/enum/number/cardinality derived directly from binary descriptor.
// syntax="proto2" reconstructed from descriptor required/optional semantics.
syntax = "proto2";
package LIN;

message SC_GAMEGATE_INSTANT_TOKEN_NOTI {
  required string instant_token_id = 1;
}

message CS_GAMEGATE_INSTANT_TOKEN_REQ {
  required string app_id = 1;
}

message SC_GAME_GATE_ERROR_NOTI {
  enum GameGateErrorType {
    GGE_ERROR_ETC = 0;
    GGE_AUTH_LOGINTOKEN = 1;
    GGE_AUTH_REQUESTTOKEN = 2;
    GGE_GRADE_LISTGRADES = 3;
  }
  required .LIN.SC_GAME_GATE_ERROR_NOTI.GameGateErrorType errorType = 1;
  required int32 errorCode = 2;
  required int32 errorModule = 3;
  required int32 errorLine = 4;
}
~~~

# 부록 E. 경기 시간·점수·이벤트 메시지 전문

원천: [MainServer_Client_ArenaCo.proto](../MainServerProto/schemas/MainServer_Client_ArenaCo.proto).

~~~proto
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
~~~

---

## 검증 범위

- 실제 21MB 이미지의 SHA-256 및 관련 UI/InterServer 문자열·RTTI 주소를 확인했다.
- GitHub 복원 스키마의 메시지·필드·enum을 재조회하고 **부록에 원문 전체**를 보존했다.
- Java 구현 대조는 최신 main의 실제 파일을 읽고 수행했다.
- **미실시:** 클라이언트 실행 화면, 전 패킷 wire ID 역추적, 완전 XREF, 서버간 실접속 테스트, 전체 빌드/런타임 검증.
- **기존 Java·DB·프로토콜 파일은 수정하지 않았다.**
