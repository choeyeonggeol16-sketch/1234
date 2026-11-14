# MJLin 복호화 메모리 코드 — MainServer/Client 프로토콜 전수 인덱스 및 수신 디스패처 정적 분석

작성: 2026-11-14  |  분석 유형: 실제 복호화 바이너리 정적 추출 + x86 분기 추적  |  대상: 2019년 MJ 클라이언트

> **범위:** MJ 프리서버 클라이언트의 `MainServer_Client*.proto`와 `Lineage::MainServerPacketHandler` 경로. 이 파일은 NC 원본 서버나 MainServer.exe 자체가 아니다. 서버 구현 함수/SQL/저장 정책을 클라이언트 자료만으로 확정하지 않는다.

## A. 자료·검증·재현 기준

| 항목 | 결과 |
|---|---|
| 복호화 이미지 | `mjlin_loaded_code_00400000_0180D000.bin` / 21,024,768바이트 |
| SHA-256 | `256f7886e98d7d2c573ab76917f78e30dc4f34cb723a002d604602cdb2f6cbdb` |
| 기준 VA | `0x00400000`; 아래 VA = raw file offset + base |
| 초기 바이너리 | `mjlin.bin` 8,354,488 bytes; SHA-256 `9c82129efed1d93981782069e82de79869920769aa0f67bc1183ca75cf621823` |
| 메모리 덤프 | `mjlin.bin.3860.dmp` 46,149,837 bytes; 별도 제공, 본 추가 추출의 직접 입력은 아님 |
| 프로토콜 소스 | 메모리 내 `google.protobuf.FileDescriptorProto` 구조를 직접 역직렬화 |
| 검사한 MainServer proto | **11개 파일, 최상위 메시지 429개, 최상위 메시지의 직접 필드 1293개** |
| 별도 의존성 | `Lineage_CommonDataTypes.proto`의 `LIN` 공용 타입을 메시지 참조 경로로 확인 |

확인 등급: **[D] descriptor가 보증하는 이름/타입/필드/enum**, **[A] 어셈블리에서 관찰한 조건/호출/주소**, **[I] 의미 해석 및 요청/응답 예상 흐름**, **[U] 이 자료로는 미확인**. 기능명을 기반으로 추측한 동작을 원본 서버의 확정 구현으로 표기하지 않는다.

## B. 서버 프로토콜 패키지/파일 전수 목록 [D]

| 파일 | Protobuf package | descriptor 시작 VA | 최상위 message | 최상위 field | import |
|---|---|---|---:|---:|---|
| [`MainServer_Client.proto`](schemas/MainServer_Client.reconstructed.proto) | `LIN` | `0x01277FA0` | 266 | 806 | `Lineage_CommonDataTypes.proto` |
| [`MainServer_Client_ArenaCo.proto`](schemas/MainServer_Client_ArenaCo.reconstructed.proto) | `LIN` | `0x01193278` | 62 | 183 | `Lineage_CommonDataTypes.proto` |
| [`MainServer_Client_BuilderCommand.proto`](schemas/MainServer_Client_BuilderCommand.reconstructed.proto) | `LIN.BUILDER` | `0x011A4AE8` | 9 | 27 | `MainServer_Client_Inventory.proto` |
| [`MainServer_Client_Companion.proto`](schemas/MainServer_Client_Companion.reconstructed.proto) | `LIN` | `0x0119E958` | 11 | 63 | `Lineage_CommonDataTypes.proto` |
| [`MainServer_Client_GameGate.proto`](schemas/MainServer_Client_GameGate.reconstructed.proto) | `LIN` | `0x01177398` | 2 | 2 | — |
| [`MainServer_Client_Indun.proto`](schemas/MainServer_Client_Indun.reconstructed.proto) | `LIN` | `0x011780F8` | 27 | 72 | `Lineage_CommonDataTypes.proto` |
| [`MainServer_Client_InterRacing.proto`](schemas/MainServer_Client_InterRacing.reconstructed.proto) | `LIN` | `0x0117BCA8` | 10 | 26 | `Lineage_CommonDataTypes.proto` |
| [`MainServer_Client_Inventory.proto`](schemas/MainServer_Client_Inventory.reconstructed.proto) | `LIN` | `0x0119A820` | 13 | 62 | — |
| [`MainServer_Client_PlaySupport.proto`](schemas/MainServer_Client_PlaySupport.reconstructed.proto) | `LIN` | `0x0117D180` | 8 | 7 | — |
| [`MainServer_Client_Pledge.proto`](schemas/MainServer_Client_Pledge.reconstructed.proto) | `LIN` | `0x0119CEE8` | 9 | 21 | — |
| [`MainServer_Client_WeaponMastery.proto`](schemas/MainServer_Client_WeaponMastery.reconstructed.proto) | `LIN` | `0x01181468` | 12 | 24 | `Lineage_CommonDataTypes.proto` |

**전체 개수:** base `MainServer_Client.proto` 266개 + 10개 기능별 파일 163개 = 429개. 여기서 패키지는 Protobuf namespace이며 Java 패키지가 아니다. C++ 생성 클래스의 논리 네임스페이스는 예를 들어 `LIN::CS_NP_LOGIN_REQ`, `LIN::SC_START_PLAY_SUPPORT_ACK`, `LIN::BUILDER::CS_BUILDER_COMMAND_REQ`이다. RTTI 및 디스크의 원본 C++ 구현 함수 이름을 뜻하는 것은 아니다.

모든 파일은 별도 `schemas/*.reconstructed.proto`로 필드 번호·타입·required/optional/repeated·enum/기본값까지 복원했다. 실제 `.pb.cc` 원본 파일을 갖고 있는 것이 아니라 descriptor를 읽어 작성한 재구성 텍스트다.

### C. 클라이언트에서 확인한 C++ 클래스/함수 주소 [A]

| 심볼 또는 복원 역할 | VA | 어셈블리 근거 | 확인 정도 |
|---|---|---|---|
| RTTI `Lineage::MainServerPacketHandler` | TypeDescriptor `0x0158222C` | MSVC RTTI 문자열 `.?AVMainServerPacketHandler@Lineage@@` 및 TypeDescriptor 참조 | 클래스 존재 확인 |
| `MainServerPacketHandler` 연동 전달 래퍼(원본 메서드명 미확인) | `0x009928C0` | 객체 `[ecx+4]`와 2인자를 스택에 올리고 `call 0x00AE5AB0`; `ret 8` | 직접 확인 |
| `MainServerPacketHandler` 객체 생성 초기화 함수(원본 생성자 이름 추정) | `0x009928E0` | 객체에 vtable `0x011F8100` 저장, 두 필드 초기화 | 직접 확인 |
| `MainServerPacketHandler` 소멸자 계열 | `0x00992950` | 조건부 객체 해제 및 기반 종료 호출 | 직접 확인 |
| MainServer 패킷 입력 디스패치 핵심 | `0x00AE5AB0` | 첫 바이트 `movzx`, 상태 분기, 테이블 인덱스와 간접 점프 | 직접 확인 |
| 실행 상태 코드 검사 | `0x00AE5C1F` | `cmp dword ptr [0x015D8E44],3`; 일치·불일치에 따라 별도 테이블 | 직접 확인, 상태 코드의 이름 미확인 |
| 상태=3의 opcode 테이블 | `0x00AE6690`/`0x00AE64D0` | 첫 바이트 `opcode-1`로 index byte lookup 후 VA jump table | 직접 확인 |
| 상태≠3의 opcode 테이블 | `0x00AE67D0`/`0x00AE6790` | 첫 바이트 `opcode`를 0xFB 범위 검사 후 인덱스/점프 | 직접 확인 |
| 다단계 하위 packet code 분기 | `0x00AE63F0`, `0x00AE640E` | `[packet+1]`에서 16비트 수치를 읽고 추가 비교/분기 | 직접 확인; proto message number와 대응 미확인 |
| 기본/미처리 opcode 분기 | `0x00AE6237` | 디폴트 케이스에서 진단/예외 처리 계열 호출 | 직접 확인; 반환 정책 전체는 미검증 |
| RTTI `Lineage::ProtoPacketHandler` | TypeDescriptor `0x015841BC` | `.?AVProtoPacketHandler@Lineage@@` 확인 | 클래스 존재만 확정 |
| RTTI `Lineage::ChatServerPacketHandler` | TypeDescriptor `0x0158418C` | `.?AVChatServerPacketHandler@Lineage@@` 확인 | 클래스 존재만 확정 |
| `ChatServerPacketHandler` 수신 래퍼 후보 | `0x00A00E50` | 입력 포인터+2 및 전체 길이-2 변환, `call 0x00B117F0` | 정적 분기 확인; 정확한 메서드명 미확인 |

**주의:** `MainServerPacketHandler`와 `ProtoPacketHandler`는 실제 RTTI 클래스이지만, 각 VA의 `OnPacket`/`Process` 같은 임의 원본 함수명을 만들어 적지 않는다. `CS_*`/`SC_*`는 실제 Protobuf 클래스명이지만 MainServer의 패킷 처리 함수명은 아니다.

### C-1. 실제 수신 분기의 구조 [A]

```text
MainServerPacketHandler 객체
  -> 호출 래퍼 VA 0x009928C0
  -> 수신 디스패치 VA 0x00AE5AB0(packetPtr, packetLen, context)
      1) packet[0] 추출
      2) 런타임 상태 == 3 ?
         YES -> byte opcode-1, 1..255 table VA 0x00AE6690
              -> case VA table 0x00AE64D0 -> 하위 함수 call
         NO  -> byte opcode, 0..251 table VA 0x00AE67D0
              -> case VA table 0x00AE6790 -> 하위 함수 call
              -> 일부 분기는 packet[1:3] 16-bit 하위 코드도 검사
      3) 처리/미처리/오류 경로로 합류
```

위 수신 진입 함수의 **실제 x86 switch 분기**는 서로 다른 두 상태의 opcode가 중복될 수 있음을 보여준다. 아래 `dispatch_state3.csv`(255개 raw opcode 슬롯)와 `dispatch_otherstate.csv`(252개 슬롯)에 각 1바이트 코드별 case VA·그 case에서 처음 호출한 함수 주소를 보존했다. **표의 `opcode`는 해당 switch의 byte 값일 뿐 `CS_*` Protobuf wire opcode 번호라고 단정할 수 없다.**

`상태=3`: 255 슬롯 중 명시적 case 112개, 미처리/공통 탈출 슬롯 143개. `상태≠3`: 252 슬롯, case VA 분포 16종. 기본/공통 케이스와 16-bit 재분기를 이름으로 착각하지 않도록 생 opcode 표를 따로 기록했다.

### C-2. 원문 Protobuf와 패킷 분기 경계 [D+A]

복호화 메모리에는 `D:\Integration\Jenkins Workspaces\Template_StreamBuild_Client\Common\MainServer_Client.pb.cc` 및 `MainServer_Client_<Feature>.pb.cc` 컴파일 경로 문자열과 MSVC RTTI `.?AVCS_*@LIN@@`가 존재한다. 따라서 **C++ `LIN` Protobuf 생성 코드와 MainServerPacketHandler 입력 처리 구조의 공존**은 입증된다. 그러나 전수 message ID ↔ 위 x86 switch 케이스 ↔ 실제 서버 핸들러 함수 3자 매핑은 이 자료만으로 완성되지 않는다. 둘을 임의 번호로 매칭하지 않는다.

## D. 기능별 서버 계약·요청/응답 처리 [D, 흐름 해석은 I]

### D-1. 로그인·인증·이동 서버·접속 유지

- **`LIN.CS_NP_LOGIN_REQ`**: `1:client_ip(uint32)`, `2:otp_security(uint32)`, `3:auth_provider(string)`, `4:authn_token(string)`, `5:mac_hash(bytes)`
- **`LIN.CS_NP_TOKEN_LOGIN_REQ`**: `1:transaction_id(uint32)`, `2:client_ip_address(string)`, `3:client_app_id(string)`, `4:auth_provider_code(string)`, `5:auth_token(string)`, `6:mobile_2nd_pw(string)`
- **`LIN.CS_MOVE_SERVER_AUTH_REQ`**: `1:dest_serverno(uint32)`
- **`LIN.SC_MOVE_SERVER_AUTH_ERROR_ACK`**: `1:error(.LIN.SC_MOVE_SERVER_AUTH_ERROR_ACK.eErrorType)`
- **`LIN.CS_KEEP_ALIVE_REQ`**: `1:transaction_id(int32)`, `2:client_time(uint64)`
- **`LIN.SC_KEEP_ALIVE_ACK`**: `1:transaction_id(int32)`, `2:client_time(uint64)`, `3:server_time(uint64)`
- **`LIN.SC_PING_REQ`**: `1:transaction_id(int32)`
- **`LIN.CS_PING_ACK`**: `1:transaction_id(int32)`
- **`LIN.SC_CONNECT_HIBREEDSERVER_NOTI_PACKET`**: `1:destIP(uint32)`, `2:destPort(int32)`, `3:reservednumber(uint32)`, `4:onetimetoken(bytes)`, `5:interkind(int32)`, `6:domainname(string)`, `7:worldmove(bool)`
- **`LIN.CS_HIBREED_AUTH_REQ_PACKET`**: `1:reservednumber(uint64)`, `2:onetimetoken(string)`
- **`LIN.SC_HIBREED_AUTH_ACK_PACKET`**: `1:result(.LIN.Result)`
- **`LIN.CS_MOVE_LOGOUT_NOTI_PACKET`**: `1:reservednumber(uint64)`
- **`LIN.SC_CLIENTGATE_APPTOKEN_NOTI`**: `1:appToken(string)`
- **`LIN.SC_DISCONNECT_SOCKET_NOTI_PACKET`**: `1:is_empty(bool)`
- **`LIN.SC_GAME_GATE_ERROR_NOTI`**: `1:errorType(.LIN.SC_GAME_GATE_ERROR_NOTI.GameGateErrorType)`, `2:errorCode(int32)`, `3:errorModule(int32)`, `4:errorLine(int32)`

**[I]** Client 로그인 요청/토큰→서버 승인 및 게임 연결 상태 수립, 게임/하이브리드 서버 이동 시 token/예약 번호를 새 인증 메시지로 전달, keep-alive에서는 `transaction_id`를 회신값과 대조하는 구조로 해석할 수 있다. 실제 인증 성공·오류 DB 조회, 타임아웃, 토큰 서명 검증은 클라이언트 descriptor에 없다.

`GameGate` 확장 메시지: `CS_GAMEGATE_INSTANT_TOKEN_REQ`의 `app_id` → `SC_GAMEGATE_INSTANT_TOKEN_NOTI`의 `instant_token_id`가 보인다. 서버가 토큰을 어떻게 생성/검증하는지는 미확인이다.

### D-2. PSS 자동사냥 — MainServer 사용 시간/시작·종료

- **`LIN.CS_START_PLAY_SUPPORT_REQ`**: *본문 필드 없음*
- **`LIN.SC_START_PLAY_SUPPORT_ACK`**: `1:result .LIN.SC_START_PLAY_SUPPORT_ACK.eResult`
  - enum `eResult`: VALID=0, INVALID_MAP=1, INVALID_LEVEL=2, TIME_EXPIRE=3, UNKNOWN_ERROR=99
- **`LIN.CS_FINISH_PLAY_SUPPORT_REQ`**: *본문 필드 없음*
- **`LIN.SC_FINISH_PLAY_SUPPORT_ACK`**: `1:remain_time uint32`
- **`LIN.SC_FORCE_FINISH_PLAY_SUPPORT_NOTI`**: `1:reason .LIN.SC_FORCE_FINISH_PLAY_SUPPORT_NOTI.eReason` `2:remain_time uint32`
  - enum `eReason`: INVALID_MAP=1, INVALID_LEVEL=2, TIME_EXPIRE=3, USER_DEAD=4, MAP_TIME_EXPIRED=5
- **`LIN.CS_PSS_TIME_CHECK_REQ`**: *본문 필드 없음*
- **`LIN.SC_PSS_TIME_CHECK_ACK`**: `1:remain_time uint32`
- **`LIN.SC_UPDATE_PLAY_SUPPORT_TIME_NOTI`**: `1:type .LIN.SC_UPDATE_PLAY_SUPPORT_TIME_NOTI.eType` `2:remain_time uint32`
  - enum `eType`: CHARGE_BY_EVENT=1, CHARGE_BY_ITEM=2

**[D]** `SC_START_PLAY_SUPPORT_ACK.result`: `VALID=0`, `INVALID_MAP=1`, `INVALID_LEVEL=2`, `TIME_EXPIRE=3`, `UNKNOWN_ERROR=99`. `SC_FORCE_FINISH_PLAY_SUPPORT_NOTI.reason`: `INVALID_MAP=1`, `INVALID_LEVEL=2`, `TIME_EXPIRE=3`, `USER_DEAD=4`, `MAP_TIME_EXPIRED=5`. 시간 갱신 타입은 `CHARGE_BY_EVENT=1`, `CHARGE_BY_ITEM=2`. `remain_time`은 uint32이다.

**[I] 서버 구현 순서 후보:** 시작 `CS_START...` 접수→맵/레벨/잔여시간 정책 검증→`SC_START...ACK` 반환→클라이언트 BotConductor가 이동/공격→중지/사망/맵/시간 조건에 따라 `SC_FORCE_FINISH...NOTI` 또는 수동 FINISH→별도 `PSS_TIME_CHECK` 잔여시간 조회. descriptor의 결과 코드로 **검사 항목의 존재만** 확인되며 실제 서버 검사 순서/주기/SQL/서버 코드는 검증하지 못했다.

### D-3. 제작·거래성 작업·창고

- `CS_CRAFT_LIST_ALL_REQ`: `1:hash_value(bytes)`
- `SC_CRAFT_LIST_ALL_ACK`: `1:eResult(.LIN.SC_CRAFT_LIST_ALL_ACK.eCraftListAllReqResultType)`, `2:craft(.LIN.SC_CRAFT_LIST_ALL_ACK.Craft)`
- `CS_CRAFT_ID_LIST_REQ`: `1:npc_id(int32)`
- `SC_CRAFT_ID_LIST_ACK`: `1:eResult(.LIN.SC_CRAFT_ID_LIST_ACK.eCraftIdListReqResultType)`, `2:craft_id_list(.LIN.SC_CRAFT_ID_LIST_ACK.CraftIdList)`, `3:eBlindType(int32)`, `4:npc_type(.LIN.SC_CRAFT_ID_LIST_ACK.eCraftNpcType)`
- `CS_CRAFT_MAKE_REQ`: `1:npc_id(int32)`, `2:craft_id(int32)`, `3:count(int32)`, `4:slotInfo(.LIN.CS_CRAFT_MAKE_REQ.CraftInputItemSlotInfo)`, `5:batch_transaction_id(int32)`
- `SC_CRAFT_MAKE_ACK`: `1:eResult(.LIN.SC_CRAFT_MAKE_ACK.eCraftMakeReqResultType)`, `2:output_items(.LIN.SC_CRAFT_MAKE_ACK.CraftOutputItem)`, `3:limit_craft_info(.LIN.SC_CRAFT_MAKE_ACK.LimitCraftInfo)`
- `CS_CRAFT_BATCH_REQ`: `1:npc_id(int32)`, `2:craft_id(int32)`, `3:count(int32)`
- `SC_CRAFT_BATCH_ACK`: `1:eResult(.LIN.SC_CRAFT_BATCH_ACK.eCraftBatchAckResultType)`, `2:batch_transaction_id(int32)`
- `CS_ALCHEMY_DESIGN_REQ`: `1:hash_value(bytes)`
- `SC_ALCHEMY_DESIGN_ACK`: `1:result_code(.LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode)`, `2:inputLists(.LIN.SC_ALCHEMY_DESIGN_ACK.InputList)`, `3:outputLists(.LIN.SC_ALCHEMY_DESIGN_ACK.OutputList)`, `4:alchemies(.LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy)`
- `CS_ALCHEMY_MAKE_REQ`: `1:alchemy_id(int32)`, `2:inputs(.LIN.CS_ALCHEMY_MAKE_REQ.Input)`
- `SC_ALCHEMY_MAKE_ACK`: `1:result_code(.LIN.SC_ALCHEMY_MAKE_ACK.ResultCode)`, `2:output_items(.LIN.SC_ALCHEMY_MAKE_ACK.OutputItem)`
- `CS_ITEM_EXCHANGE_REQ`: `1:source_warehouse(int32)`, `2:source_id(int32)`
- `SC_WAREHOUSE_INFO_NOT`: `1:warehouseType(int32)`, `2:ackType(int32)`, `3:iteminfo(.LIN.SC_WAREHOUSE_INFO_NOT.Item)`

**[D]** `CS_CRAFT_MAKE_REQ`와 `SC_CRAFT_BATCH_ACK`에는 `batch_transaction_id` 필드가 있다. **[I]** 접수/결과를 연관시킬 식별자 역할은 가능한 해석이지만, 서버의 정확히 한 번 커밋·중복방지·영속성 정책이 입증된 것은 아니다.

`MainServer_Client_Inventory.proto`는 `ItemInfo`(object_id,name_id,db_id,count...), `ItemEquipInfo`, `TradeItemInfo`를 정의하고 `SC_INVENTORY_INFO_NOTI`, `SC_ADD_INVENTORY_NOTI`, `SC_UPDATE_INVENTORY_NOTI`, `SC_WAREHOUSE_ITEM_LIST_NOTI`, `SC_PERSONAL_SHOP_ITEM_LIST_NOTI`, `SC_EXCHANGE_ITEM_LIST_NOTI` 등 **서버 발신 목록/상태 메시지**를 제공한다. 해당 파일 자체에는 해당 거래의 모든 `CS_` 요청이 있지 않다. 제시된 알림 필드만으로 창고·거래 DB 처리 수순을 복원할 수 없다.

### D-4. 인던 파티방/아레나

`MainServer_Client_Indun.proto`: `CS_ARENACO_BYPASS_INDUN_ROOM_LIST_REQ` → `SC_...ROOM_LIST_ACK`, `CS_ARENACO_CREATE_INDUN_ROOM_REQ(build_info)` → `SC_ARENACO_CREATE_INDUN_ROOM_ACK(result,room_id)`, `CS_ARENACO_ENTER_INDUN_ROOM_REQ(room_id,password,invite_accept,host_char_name)` → `SC_ARENACO_ENTER_INDUN_ROOM_ACK`, `CS_ARENACO_BYPASS_INDUN_GAME_START_REQ(room_id,map_kind,user_level)` → `SC_ARENACO_BYPASS_INDUN_GAME_START_ACK`, 퇴장/초대/강퇴/상태 통지 등이 descriptor에 존재한다.

`MainServer_Client_ArenaCo.proto`: 일반 아레나 방 생성·입장·팀 변경·준비·시작·관전자·응원 메시지가 있다. **[I]** 인던 대기실과 실제 인스턴스 시작 절차를 구분할 프로토콜 뼈대지만, 맵 전환 애니메이션·월드 할당·인터서버 전송 구현은 `ACK`만으로 증명할 수 없다.

### D-5. 기타

- `LIN.BUILDER`(별도 package): `BuilderCommand`, `CS_BUILDER_COMMAND_REQ`, `SC_BUILDER_COMMAND_ACK`, 장비/유저/메시지/스펠 지연 처리 등 9개 메시지.
- `Companion`: 이름 변경, 능력치 배분, TM 명령, 스킬 강화·티어, 전투 능력치 전송.
- `Pledge`: 혈맹 축복 추첨/재선택, 연맹 목록 및 변경, 휴식 경험치 정보.
- `InterRacing`: 맵 UI 텔레포트, 경주 준비·시작·정지·이동, 배당/티켓 및 랭킹.
- `WeaponMastery`: 숙련도 게이지·포인트·강화·룰렛 잠금/오픈.
- Base `MainServer_Client.proto`: 채팅/채팅 서버 passport, 출석, 랭킹, 스탯 리뉴얼, 업적·월드 상태·개인 상점·혈맹 신청 등이 함께 존재.

## E. 패키지별 메시지명과 직접 필드 전수 인덱스 [D]

표기: `field#이름:타입[cardinality]` / enum/nested는 각 재구성 proto 파일의 원본 숫자와 제약을 참조. 중첩 타입도 schema 파일에 포함했다. 아래는 **메시지 429개 이름 전체**와 직접 필드다.

### `MainServer_Client.proto` — package `LIN` (266 messages)

| # | message class | direct fields (number:name:type) |
|---:|---|---|
| 1 | `LIN.CS_NP_LOGIN_REQ` | `1:client_ip:uint32, 2:otp_security:uint32, 3:auth_provider:string, 4:authn_token:string, 5:mac_hash:bytes` |
| 2 | `LIN.CS_KEEP_ALIVE_REQ` | `1:transaction_id:int32, 2:client_time:uint64` |
| 3 | `LIN.SC_KEEP_ALIVE_ACK` | `1:transaction_id:int32, 2:client_time:uint64, 3:server_time:uint64` |
| 4 | `LIN.SC_PING_REQ` | `1:transaction_id:int32` |
| 5 | `LIN.CS_PING_ACK` | `1:transaction_id:int32` |
| 6 | `LIN.CS_MOVE_SERVER_AUTH_REQ` | `1:dest_serverno:uint32` |
| 7 | `LIN.SC_MOVE_SERVER_AUTH_ERROR_ACK` | `1:error:LIN.SC_MOVE_SERVER_AUTH_ERROR_ACK.eErrorType` |
| 8 | `LIN.CS_CRAFT_LIST_ALL_REQ` | `1:hash_value:bytes` |
| 9 | `LIN.SC_CRAFT_LIST_ALL_ACK` | `1:eResult:LIN.SC_CRAFT_LIST_ALL_ACK.eCraftListAllReqResultType, 2:craft:LIN.SC_CRAFT_LIST_ALL_ACK.Craft` |
| 10 | `LIN.CS_CRAFT_ID_LIST_REQ` | `1:npc_id:int32` |
| 11 | `LIN.SC_CRAFT_ID_LIST_ACK` | `1:eResult:LIN.SC_CRAFT_ID_LIST_ACK.eCraftIdListReqResultType, 2:craft_id_list:LIN.SC_CRAFT_ID_LIST_ACK.CraftIdList, 3:eBlindType:int32, 4:npc_type:LIN.SC_CRAFT_ID_LIST_ACK.eCraftNpcType` |
| 12 | `LIN.CS_CRAFT_MAKE_REQ` | `1:npc_id:int32, 2:craft_id:int32, 3:count:int32, 4:slotInfo:LIN.CS_CRAFT_MAKE_REQ.CraftInputItemSlotInfo, 5:batch_transaction_id:int32` |
| 13 | `LIN.SC_CRAFT_MAKE_ACK` | `1:eResult:LIN.SC_CRAFT_MAKE_ACK.eCraftMakeReqResultType, 2:output_items:LIN.SC_CRAFT_MAKE_ACK.CraftOutputItem, 3:limit_craft_info:LIN.SC_CRAFT_MAKE_ACK.LimitCraftInfo` |
| 14 | `LIN.CS_CRAFT_BATCH_REQ` | `1:npc_id:int32, 2:craft_id:int32, 3:count:int32` |
| 15 | `LIN.SC_CRAFT_BATCH_ACK` | `1:eResult:LIN.SC_CRAFT_BATCH_ACK.eCraftBatchAckResultType, 2:batch_transaction_id:int32` |
| 16 | `LIN.SC_FISHING_NOT` | `1:fishingKind:LIN.SC_FISHING_NOT.FISHING_NOT_KIND, 2:startTime:int32, 3:rodKind:LIN.SC_FISHING_NOT.FISHING_ROD, 4:rodCount:int32` |
| 17 | `LIN.SC_NOTIFICATION_MESSAGE_NOT` | `1:suffileNumber:sint32, 2:notificationMessage:string, 3:messageRGB:bytes, 4:duration:sint32` |
| 18 | `LIN.SC_NOTIFICATION_STRINGKINDEX_NOTI` | `1:suffileNumber:sint32, 2:stringIndex:sint32, 3:messageRGB:bytes, 4:duration:sint32, 5:args:string` |
| 19 | `LIN.SC_SIEGE_OBJECT_PUT_NOT` | `1:IdOfObject:uint32, 2:bloodKind:LIN.SC_SIEGE_OBJECT_PUT_NOT.SIEGE_BLOOD_KIND` |
| 20 | `LIN.SC_SIEGE_ZONE_UPDATE_NOT` | `1:siegeZoneKind:LIN.SC_SIEGE_ZONE_UPDATE_NOT.SIEGE_ZONE_KIND` |
| 21 | `LIN.SC_SIEGE_EVENT_NOT` | `1:eventKind:LIN.SC_SIEGE_EVENT_NOT.SIEGE_EVENT_KIND, 2:castles:LIN.SC_SIEGE_EVENT_NOT.CASTLE_INFO` |
| 22 | `LIN.CS_SIEGE_WAR_REQ` | `1:castleId:uint32` |
| 23 | `LIN.SC_DEVELSERVER_MESSAGE_NOTI` | `1:messagegKind:LIN.SC_DEVELSERVER_MESSAGE_NOTI.S_KIND, 2:develMessage:string` |
| 24 | `LIN.SC_SIEGE_INJURY_TIME_NOIT` | `1:siegeKind:LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND, 2:remainSecond:sint32, 3:pledgeName:string` |
| 25 | `LIN.SC_WITHDRAW_DEFENCE_SUCCESS_ADENA_NOTI` | `1:npcId:uint32, 2:withdrawalAdena:sint64` |
| 26 | `LIN.CS_WITHDRAW_DEFENCE_SUCCESS_ADENA_REQ` | `1:npcId:uint32, 2:adenaCount:sint64` |
| 27 | `LIN.CS_ITEM_EXCHANGE_REQ` | `1:source_warehouse:int32, 2:source_id:int32` |
| 28 | `LIN.SC_WAREHOUSE_INFO_NOT` | `1:warehouseType:int32, 2:ackType:int32, 3:iteminfo:LIN.SC_WAREHOUSE_INFO_NOT.Item` |
| 29 | `LIN.SC_ACCOUNT_BENEFIT_NOTI` | `1:type:LIN.SC_ACCOUNT_BENEFIT_NOTI.eBenefitType, 2:enable:bool` |
| 30 | `LIN.SC_SIEGE_PROMOTE_NOTI` | `1:surfNumber:int32, 2:stringIndexMesage:int32, 3:messageRGB:bytes, 4:duration:int32, 5:descCastle:sint32, 6:descNPC:string` |
| 31 | `LIN.SC_NOTIFICATION_MESSAGE` | `1:pos:LIN.SC_NOTIFICATION_MESSAGE.display_position, 2:desc:string, 3:messageRGB:bytes, 4:duration:int32, 5:serverno:int32` |
| 32 | `LIN.SC_REDBLOOD_ENEMY_PLEDGE_NOTI` | `1:status:LIN.SC_REDBLOOD_ENEMY_PLEDGE_NOTI.init_status, 2:enemypledge:string` |
| 33 | `LIN.SC_SIEGE_TRUE_TARGET_NOTI` | `1:targetNumber:uint32, 2:status:LIN.SC_SIEGE_TRUE_TARGET_NOTI.tt_status, 3:spritenumber:int32` |
| 34 | `LIN.SC_SPELL_BUFF_NOTI` | `1:noti_type:LIN.SC_SPELL_BUFF_NOTI.eNotiType, 2:spell_id:uint32, 3:duration:uint32, 4:duration_show_type:LIN.SC_SPELL_BUFF_NOTI.eDurationShowType, 5:on_icon_id:uint32, 6:off_icon_id:uint32, 7:icon_priority:uint32, 8:tooltip_str_id:uint32, 9:new_str_id:uint32, 10:end_str_id:uint32, 11:is_good:bool, 12:overlap_buff_icon:uint32, 13:main_tooltip_str_id:uint32, 14:buff_icon_priority:uint32, 15:buff_group_id:uint32, 16:buff_group_priority:uint32` |
| 35 | `LIN.CS_CASTLE_PROPERTY_REQ` | `1:reqKind:LIN.CS_CASTLE_PROPERTY_REQ.REQ_KIND, 2:castle_Id:uint32` |
| 36 | `LIN.SC_CASTLE_PROPERTY_ACK` | `1:castleInfoList:LIN.SC_CASTLE_PROPERTY_ACK.CASTLE_INFO` |
| 37 | `LIN.CS_BLOOD_PLEDGE_JOIN_OPTION_REQ` | `1:type:LIN.CS_BLOOD_PLEDGE_JOIN_OPTION_REQ.eOPTION_TYPE` |
| 38 | `LIN.SC_BLOOD_PLEDGE_JOIN_OPTION_ACK` | `1:result:LIN.SC_BLOOD_PLEDGE_JOIN_OPTION_ACK.eRESULT, 2:enable_join:bool, 3:join_type:LIN.ePLEDGE_JOIN_REQ_TYPE, 4:hashed_password:bytes` |
| 39 | `LIN.CS_BLOOD_PLEDGE_JOIN_OPTION_CHANGE_REQ` | `1:enable_join:bool, 2:join_type:LIN.ePLEDGE_JOIN_REQ_TYPE, 3:hashed_password:bytes` |
| 40 | `LIN.SC_BLOOD_PLEDGE_JOIN_OPTION_CHANGE_ACK` | `1:result:LIN.SC_BLOOD_PLEDGE_JOIN_OPTION_CHANGE_ACK.ePLEDGE_JOIN_OPTION_CHANGE_ACK_RESULT` |
| 41 | `LIN.CS_BLOOD_PLEDGE_JOIN_REQ` | `1:pledge_name:string, 2:join_type:LIN.ePLEDGE_JOIN_REQ_TYPE, 3:join_msg:string, 4:hashed_password:bytes` |
| 42 | `LIN.SC_BLOOD_PLEDGE_JOIN_ACK` | `1:result:LIN.SC_BLOOD_PLEDGE_JOIN_ACK.ePLEDGE_JOIN_ACK_RESULT, 2:pledge_name:string, 3:need_join_type:LIN.ePLEDGE_JOIN_REQ_TYPE, 4:msg:string` |
| 43 | `LIN.CS_BLOOD_PLEDGE_JOINING_LIST_REQ` | `1:req_type:LIN.ePLEDGE_JOINING_LIST_TYPE` |
| 44 | `LIN.SC_BLOOD_PLEDGE_JOINING_LIST_ACK` | `1:req_type:LIN.ePLEDGE_JOINING_LIST_TYPE, 2:data:LIN.SC_BLOOD_PLEDGE_JOINING_LIST_ACK.PLEDGE_JOINING_DATA` |
| 45 | `LIN.CS_BLOOD_PLEDGE_JOIN_CANCEL_REQ` | `1:pledge_uid:uint32` |
| 46 | `LIN.SC_BLOOD_PLEDGE_JOIN_CANCEL_ACK` | `1:result:LIN.SC_BLOOD_PLEDGE_JOIN_CANCEL_ACK.ePLEDGE_JOIN_CANCEL_ACK_RESULT` |
| 47 | `LIN.CS_BLOOD_PLEDGE_JOIN_CONFIRM_REQ` | `1:req_type:LIN.CS_BLOOD_PLEDGE_JOIN_CONFIRM_REQ.ePLEDGE_JOIN_CONFIRM_REQ_TYPE, 2:user_name:string` |
| 48 | `LIN.SC_BLOOD_PLEDGE_JOIN_CONFIRM_ACK` | `1:result:LIN.SC_BLOOD_PLEDGE_JOIN_CONFIRM_ACK.ePLEDGE_JOIN_CONFIRM_ACK_RESULT` |
| 49 | `LIN.CS_SOCIAL_ACTION_REQ` | `1:action_type:LIN.SOCIAL_ACTION_TYPE, 2:action_code:int32` |
| 50 | `LIN.SC_SOCIAL_ACTION_NOTI` | `1:object_id:uint32, 2:action_type:LIN.SOCIAL_ACTION_TYPE, 3:action_code:int32` |
| 51 | `LIN.SC_TOGGLE_EFFECT_NOTI` | `1:object_id:uint32, 2:bWear:bool, 3:effect_id:int32` |
| 52 | `LIN.SC_ALL_SPELL_PASSIVE_NOTI` | `1:passives:LIN.SC_ALL_SPELL_PASSIVE_NOTI.spellpassivedata` |
| 53 | `LIN.SC_ADD_SPELL_PASSIVE_NOTI` | `1:passiveid:int32, 2:param:int32` |
| 54 | `LIN.SC_DAMAGE_OF_TIME_NOTI` | `1:targetid:int32` |
| 55 | `LIN.SC_BUFF_ICON_ON_OFF_NOTI` | `1:iconid:int32, 2:on:bool` |
| 56 | `LIN.SC_TAM_POINT_NOTI` | `1:tam_point:uint64` |
| 57 | `LIN.SC_PLAY_TIME_LIMIT_NOTI` | `1:playTimeLimitSec:int32, 2:remainPlayTimeSec:int32` |
| 58 | `LIN.SC_FATIGUE_NOTI` | `1:fatigueStatus:LIN.SC_FATIGUE_NOTI.FATIGUE_STATUS, 2:penaltyLevel:int32, 3:combatFatigue:uint64` |
| 59 | `LIN.SC_SIHON_RANK` | `1:historyRankInfos:LIN.SC_SIHON_RANK.RankHistoryInfo, 2:rankinfos:LIN.SC_SIHON_RANK.RankInfo, 3:myinfos:LIN.SC_SIHON_RANK.MyInfo, 4:emptyData:int32` |
| 60 | `LIN.SC_SIEGE_REDBLOOD_LOTTO_NOTI` | `1:surfNumber:int32, 2:stringIndexMesage:int32, 3:messageRGB:bytes, 4:duration:int32, 5:lottouser:string, 6:lottoAdena:int32` |
| 61 | `LIN.SC_ARCA_ACTIVATION_INFO_ACK` | `1:activate_user_list:LIN.SC_ARCA_ACTIVATION_INFO_ACK.UserInfo, 2:max_activate_characters:uint32, 3:buff_spell_id:uint32, 4:cancelation_delay:int64` |
| 62 | `LIN.CS_ARCA_ACTIVATION_CANCEL_REQ` | `1:user_id:uint32` |
| 63 | `LIN.SC_NPC_INFO` | `1:npcId:uint32, 2:npcHp:uint32, 3:npcMaxHp:uint32, 4:npcMp:uint32, 5:npcMaxMp:uint32` |
| 64 | `LIN.SC_DEATH_PENALTY_STATUS` | `1:globalDeathPenaltyStatus:uint32, 2:expDeathPenaltyStatus:uint32, 3:itemDeathPenaltyStatus:uint32` |
| 65 | `LIN.SC_POLYMORPH_EVENT_NOTI` | `1:eventEnable:bool` |
| 66 | `LIN.SC_HUNTING_GUIDE_BOOKMARK_NOTI` | `1:bookmarks:LIN.SC_HUNTING_GUIDE_BOOKMARK_NOTI.HUNTING_GUIDE_BOOKMARK_DATA` |
| 67 | `LIN.SC_BUDDY_LIST_NOTI` | `1:type:LIN.SC_BUDDY_LIST_NOTI.eBUDDY_LIST_TYPE, 2:buddys:LIN.SC_BUDDY_LIST_NOTI.BUDDY_DATA` |
| 68 | `LIN.CS_PARTY_MEMBER_MARK_CHANGE_REQ` | `1:member_id:int32, 2:mark:int32` |
| 69 | `LIN.SC_PARTY_MEMBER_MARK_CHANGE_NOTI` | `1:member_id:int32, 2:mark:int32` |
| 70 | `LIN.SC_PARTY_OPERATION_RESULT_NOTI` | `1:type:LIN.SC_PARTY_OPERATION_RESULT_NOTI.ePARTY_OPERATION_TYPE, 2:actor_name:string` |
| 71 | `LIN.CS_TUTORIAL_START_REQ` | `1:type:LIN.TUTORIAL_TYPE, 2:is_skip:bool` |
| 72 | `LIN.SC_TUTORIAL_START_ACK` | `1:type:LIN.TUTORIAL_TYPE, 2:result:LIN.TUTORIAL_RESULT` |
| 73 | `LIN.CS_TUTORIAL_END_REQ` | `1:type:LIN.TUTORIAL_TYPE` |
| 74 | `LIN.SC_TUTORIAL_END_ACK` | `1:type:LIN.TUTORIAL_TYPE, 2:result:LIN.TUTORIAL_RESULT` |
| 75 | `LIN.SC_TUTORIAL_NOTI` | `1:type:LIN.SC_TUTORIAL_NOTI.NOTI_TYPE` |
| 76 | `LIN.SC_CHARACTER_STATUS_NOTI` | `1:sts_data:LIN.SC_CHARACTER_STATUS_NOTI.STATUS_DATA` |
| 77 | `LIN.CS_CHAT_SERVER_INFO_REQ_PACKET` | `1:transaction_id:uint32` |
| 78 | `LIN.SC_CHAT_SERVER_INFO_ACK_PACKET` | `1:transaction_id:uint32, 2:address:string, 3:port_number:uint32` |
| 79 | `LIN.CS_CHAT_SERVER_PASSPORT_REQ_PACKET` | `1:transaction_id:uint32` |
| 80 | `LIN.SC_CHAT_SERVER_PASSPORT_ACK_PACKET` | `1:transaction_id:uint32, 2:world_id:uint32, 3:socket_id:uint64, 4:passport:uint32` |
| 81 | `LIN.SC_RANGE_INFO` | `1:range_type:LIN.SC_RANGE_INFO.RANGE_TYPE, 2:targe_id:int32, 3:range:int32` |
| 82 | `LIN.SC_ENCHANT_RESULT_NOTI` | `1:enchant_type:LIN.SC_ENCHANT_RESULT_NOTI.ENCHANT_TYPE, 2:enchant_result:LIN.SC_ENCHANT_RESULT_NOTI.ENCHANT_RESULT_TYPE, 3:bless_code:LIN.SC_ENCHANT_RESULT_NOTI.BLESS_CODE` |
| 83 | `LIN.SC_CLIENTGATE_APPTOKEN_NOTI` | `1:appToken:string` |
| 84 | `LIN.SC_CONNECT_HIBREEDSERVER_NOTI_PACKET` | `1:destIP:uint32, 2:destPort:int32, 3:reservednumber:uint32, 4:onetimetoken:bytes, 5:interkind:int32, 6:domainname:string, 7:worldmove:bool` |
| 85 | `LIN.CS_HIBREED_AUTH_REQ_PACKET` | `1:reservednumber:uint64, 2:onetimetoken:string` |
| 86 | `LIN.SC_HIBREED_AUTH_ACK_PACKET` | `1:result:LIN.Result` |
| 87 | `LIN.CS_MOVE_LOGOUT_NOTI_PACKET` | `1:reservednumber:uint64` |
| 88 | `LIN.SC_ACTIVE_SPELL_EX_NOTI` | `1:spells:LIN.SC_ACTIVE_SPELL_EX_NOTI.spellex` |
| 89 | `LIN.SC_ADD_ACTIVE_SPELL_EX_NOTI` | `1:spellid:int32, 2:spelltype:int32` |
| 90 | `LIN.SC_ACTIVE_SPELL_EX_INFO` | `1:infos:LIN.SC_ACTIVE_SPELL_EX_INFO.info` |
| 91 | `LIN.SC_ATTENDANCE_BONUS_INFO` | `1:checkInterval:int32, 2:startTime:int32, 3:resetPeriod:int32, 4:dailyMaxCount:int32, 5:weekendMaxCount:int32, 6:attendanceBonuses:LIN.SC_ATTENDANCE_BONUS_INFO.AttendanceBonus` |
| 92 | `LIN.SC_ATTENDANCE_USER_DATA` | `1:attendanceData:LIN.SC_ATTENDANCE_USER_DATA.UserAttendanceData` |
| 93 | `LIN.SC_ATTENDANCE_TIME_INFO` | `1:attendanceTime:int32, 2:attendanceStartTime:int32, 3:todayRewardCount:int32, 4:remainedTime:int32, 5:remainedRewardCount:int32, 6:resultCode:LIN.UserAttendanceTimeStatus, 7:rewardableUser:bool` |
| 94 | `LIN.CS_ATTENDANCE_TIME_INFO` | `(empty)` |
| 95 | `LIN.CS_ATTENDANCE_BONUS_REQUEST` | `1:attendanceIndex:int32` |
| 96 | `LIN.SC_ATTENDANCE_DATA_UPDATE` | `1:attendanceIndex:int32, 2:attendanceState:int32` |
| 97 | `LIN.AttendanceBonusInfo` | `1:bonusType:LIN.AttendanceBonusType, 2:itemId:int32, 3:itemCount:int32, 4:itemName:string, 5:itemInteractType:int32, 6:itemIcon:int32, 7:itemBless:int32, 8:itemDesc:string, 9:itemExtraDesc:bytes, 10:itemAttr:int32` |
| 98 | `LIN.AttendanceBonusGroup` | `1:attendaceIndex:int32, 2:bonuses:LIN.AttendanceBonusInfo` |
| 99 | `LIN.SC_ATTENDANCE_BONUS_INFO_EXTEND` | `1:checkInterval:int32, 2:resetPeriod:int32, 3:dailyMaxCount:int32, 4:weekendMaxCount:int32, 5:totalBonusGroupCount:int32` |
| 100 | `LIN.SC_ATTENDANCE_BONUS_GROUP_INFO` | `1:groupType:LIN.AttendanceGroupType, 2:bonusGroup:LIN.AttendanceBonusGroup` |
| 101 | `LIN.UserAttendanceDataExtend` | `1:index:int32, 2:state:LIN.UserAttendanceState, 3:time:int32` |
| 102 | `LIN.UserAttendanceDataGroup` | `1:groupType:LIN.AttendanceGroupType, 2:groupData:LIN.UserAttendanceDataExtend, 3:resultCode:LIN.UserAttendanceTimeStatus, 4:recvedRewardCount:int32, 5:totalRewardCount:int32, 6:attendanceTime:int32, 7:totalAttendanceTime:int32` |
| 103 | `LIN.SC_ATTENDANCE_USER_DATA_EXTEND` | `1:groups:LIN.UserAttendanceDataGroup, 2:userFlag:LIN.AttendanceUserFlag, 3:active_group:LIN.AttendanceGroupType` |
| 104 | `LIN.CS_ATTENDANCE_BONUS_REQUEST_EXTEND` | `1:index:int32, 2:group:LIN.AttendanceGroupType` |
| 105 | `LIN.SC_ATTENDANCE_DATA_UPDATE_EXTEND` | `1:index:int32, 2:state:int32, 3:group:LIN.AttendanceGroupType` |
| 106 | `LIN.SC_BLOODPLEDGE_USER_INFO_NOTI` | `1:bloodpledge_name:bytes, 2:rank:uint32` |
| 107 | `LIN.SC_GAME_GATE_ERROR_NOTI` | `1:errorType:LIN.SC_GAME_GATE_ERROR_NOTI.GameGateErrorType, 2:errorCode:int32, 3:errorModule:int32, 4:errorLine:int32` |
| 108 | `LIN.SC_WORLD_PUT_NOTI` | `1:worldnumber:int32, 2:homeserverno:int32, 3:isunderwather:bool, 4:isunderground:bool, 5:eventflag1:uint32, 6:eventflag2:uint32, 7:ghost_region:LIN.RegionT` |
| 109 | `LIN.SC_WORLD_PUT_OBJECT_NOTI` | `1:point:uint32, 2:objectnumber:uint32, 3:objectsprite:int32, 4:action:int32, 5:direction:int32, 6:lightRadius:int32, 7:objectcount:int32, 8:alignment:int32, 9:desc:bytes, 10:title:bytes, 11:speeddata:int32, 12:emotion:int32, 13:drunken:int32, 14:isghost:bool, 15:isparalyzed:bool, 16:isuser:bool, 17:isinvisible:bool, 18:ispoisoned:bool, 19:emblemid:uint32, 20:pledgename:bytes, 21:mastername:bytes, 22:altitude:int32, 23:hitratio:int32, 24:safelevel:int32, 25:shoptitle:bytes, 26:weaponsprite:int32, 27:couplestate:int32, 28:boundarylevelindex:int32, 29:weakelemental:int32, 30:manaratio:int32, 31:botindex:uint32, 32:homeserverno:int32, 33:team_id:uint32, 34:dialog_radius:uint32, 35:speed_value_flag:int32, 36:second_speed_type:int32, 37:explosion_remain_time_ms:uint32, 38:proclamation_siege_mark:bool, 39:npc_class_id:int32, 40:companion:LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion, 41:force_haction:string, 42:is_portal:bool, 43:proclamation_siege_pledge:bool` |
| 110 | `LIN.SC_WHOUSER_NOTI_PACKET` | `1:whouserinfo:LIN.SC_WHOUSER_NOTI_PACKET.WHOUSER_INFO, 2:currentusercount:int32, 3:hunting_map_info:LIN.SC_WHOUSER_NOTI_PACKET.HuntingMapInfo` |
| 111 | `LIN.SC_BUILDER_LIST_NOTI_PACKET` | `1:builders:LIN.SC_BUILDER_LIST_NOTI_PACKET.BUILDER_INFO` |
| 112 | `LIN.SC_STAT_RENEWAL_INFO_NOTI` | `1:infotype:sint32, 2:strinfo:LIN.SC_STAT_RENEWAL_INFO_NOTI.STR_INFO, 3:intinfo:LIN.SC_STAT_RENEWAL_INFO_NOTI.INT_INFO, 4:wisinfo:LIN.SC_STAT_RENEWAL_INFO_NOTI.WIS_INFO, 5:dexinfo:LIN.SC_STAT_RENEWAL_INFO_NOTI.DEX_INFO, 6:coninfo:LIN.SC_STAT_RENEWAL_INFO_NOTI.CON_INFO, 7:chainfo:LIN.SC_STAT_RENEWAL_INFO_NOTI.CHA_INFO` |
| 113 | `LIN.CS_STAT_RENEWAL_CALC_INFO_REQ` | `1:lev:int32, 2:class:int32, 3:infotype:int32, 4:option_value:int32, 5:option_mask:int32, 6:str:int32, 7:int:int32, 8:wis:int32, 9:dex:int32, 10:con:int32, 11:cha:int32` |
| 114 | `LIN.CS_STAT_RENEWAL_BASESTAT_INFO_REQ` | `1:str:int32, 2:int:int32, 3:wis:int32, 4:dex:int32, 5:con:int32, 6:cha:int32` |
| 115 | `LIN.SC_STAT_RENEWAL_BASESTAT_INFO_RES` | `1:strinfo:LIN.SC_STAT_RENEWAL_BASESTAT_INFO_RES.STR_INFO, 2:intinfo:LIN.SC_STAT_RENEWAL_BASESTAT_INFO_RES.INT_INFO, 3:wisinfo:LIN.SC_STAT_RENEWAL_BASESTAT_INFO_RES.WIS_INFO, 4:dexinfo:LIN.SC_STAT_RENEWAL_BASESTAT_INFO_RES.DEX_INFO, 5:coninfo:LIN.SC_STAT_RENEWAL_BASESTAT_INFO_RES.CON_INFO, 6:chainfo:LIN.SC_STAT_RENEWAL_BASESTAT_INFO_RES.CHA_INFO` |
| 116 | `LIN.SC_STAT_RENEWAL_CARRY_WEIGHT_INFO_NOTI` | `1:carrypercent:int32, 2:carrycurrent:int32, 3:carrymax:int32` |
| 117 | `LIN.SC_MAGIC_EVASION_NOTI` | `1:magicevasion:int32` |
| 118 | `LIN.SC_TOTAL_DRINKED_ELIXIR_NOTI` | `1:elixir:int32` |
| 119 | `LIN.SC_STAT_RENEWAL_BASE_STAT_NOTI` | `1:str:int32, 2:int:int32, 3:wis:int32, 4:dex:int32, 5:con:int32, 6:cha:int32` |
| 120 | `LIN.CS_ALCHEMY_DESIGN_REQ` | `1:hash_value:bytes` |
| 121 | `LIN.SC_ALCHEMY_DESIGN_ACK` | `1:result_code:LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode, 2:inputLists:LIN.SC_ALCHEMY_DESIGN_ACK.InputList, 3:outputLists:LIN.SC_ALCHEMY_DESIGN_ACK.OutputList, 4:alchemies:LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy` |
| 122 | `LIN.SC_ALCHEMY_EXTRA_INFO_NOTI` | `1:summoned_petball_item_id:int32` |
| 123 | `LIN.CS_ALCHEMY_MAKE_REQ` | `1:alchemy_id:int32, 2:inputs:LIN.CS_ALCHEMY_MAKE_REQ.Input` |
| 124 | `LIN.SC_ALCHEMY_MAKE_ACK` | `1:result_code:LIN.SC_ALCHEMY_MAKE_ACK.ResultCode, 2:output_items:LIN.SC_ALCHEMY_MAKE_ACK.OutputItem` |
| 125 | `LIN.SC_USER_START_SUNDRY_NOTI` | `1:is_pccafe:bool, 2:is_enable_ranking_system:bool, 3:pc_move_delay_reduce_rate:int32, 4:npc_move_delay_reduce:int32` |
| 126 | `LIN.SC_BASECAMP_CHART_NOTI_PACKET` | `1:charts:LIN.SC_BASECAMP_CHART_NOTI_PACKET.CHART_INFO, 2:team_points:int64, 3:winner_team_id:int32` |
| 127 | `LIN.SC_BASECAMP_POINTRANK_NOTI_PACKET` | `1:top_rankers:LIN.SC_BASECAMP_POINTRANK_NOTI_PACKET.PointRankInfoT, 2:my_rank:LIN.SC_BASECAMP_POINTRANK_NOTI_PACKET.PointRankInfoT, 3:team_points:int64` |
| 128 | `LIN.SC_THEBE_CAPTURE_INFO_NOTI_PACKET` | `1:points:LIN.SC_THEBE_CAPTURE_INFO_NOTI_PACKET.CapturePointT, 2:remain_time_for_next_capture_event:uint32` |
| 129 | `LIN.SC_BOX_ATTR_CHANGE_NOTI_PACKET` | `1:worldNumber:int32, 2:box:LIN.SC_BOX_ATTR_CHANGE_NOTI_PACKET.Box, 3:attribute:int32` |
| 130 | `LIN.CS_CHAT_REQ_PACKET` | `1:transaction_id:uint32, 2:type:LIN.ChatType, 3:message:bytes, 5:target_user_name:bytes, 6:target_user_server_no:uint32` |
| 131 | `LIN.SC_CHAT_ACK_PACKET` | `1:transaction_id:uint32, 2:type:LIN.ChatType, 3:message:bytes, 4:target_user_name:bytes, 5:target_user_server_no:uint32, 6:result:LIN.CHAT_RESULT, 7:chat_ban_duration:int32` |
| 132 | `LIN.SC_CHAT_MESSAGE_NOTI_PACKET` | `1:time_t64:int64, 2:type:LIN.ChatType, 3:message:bytes, 4:message_color:uint32, 5:name:bytes, 6:server_no:uint32, 7:object_id:uint32, 8:loc_x:uint32, 9:loc_y:uint32, 10:ranker_rating:int32, 11:target_user_name:string, 12:is_server_keeper:bool` |
| 133 | `LIN.CS_EXCLUDE_REQ_PACKET` | `1:mode:LIN.CS_EXCLUDE_REQ_PACKET.eExcludeMode, 2:type:LIN.CS_EXCLUDE_REQ_PACKET.eExcludeType, 3:charNames:bytes` |
| 134 | `LIN.SC_CHANGE_TEAM_NOTI_PACKET` | `1:object_id:uint32, 2:object_team_id:int32` |
| 135 | `LIN.SC_DISCONNECT_SOCKET_NOTI_PACKET` | `1:is_empty:bool` |
| 136 | `LIN.SC_EVENT_COUNTDOWN_NOTI_PACKET` | `1:remain_time:uint32, 2:event_desc:bytes` |
| 137 | `LIN.CS_ACTION_SCRIPT_REQ` | `1:action_script_id:uint32, 2:action_script_name:string` |
| 138 | `LIN.SC_APP_SERVER_PLAY_TIME` | `1:resultCode:LIN.SC_APP_SERVER_PLAY_TIME.ResultCode, 2:playTime:int32` |
| 139 | `LIN.SC_EQUIP_ON_OFF_NOTI` | `1:effect_list_Id:int32, 2:on_off:bool` |
| 140 | `LIN.SC_2FA_PC_REGISTRATION_CHANGE_AGREEMENT_REQ` | `1:transaction_id:uint32` |
| 141 | `LIN.CS_2FA_PC_REGISTRATION_CHANGE_AGREEMENT_ACK` | `1:transaction_id:uint32, 2:is_agree:bool` |
| 142 | `LIN.LogDFormat` | `1:log_detail_code:int32, 2:data1_num:int64, 3:data2_num:int64, 4:data3_num:int64, 5:data4_num:int64, 6:data5_num:int64, 7:data6_num:int64, 8:data7_num:int64, 9:data8_num:int64, 10:data1_str:string, 11:data2_str:string, 12:data3_str:string` |
| 143 | `LIN.CS_CLIENT_INFO_LOG_REQ` | `1:transaction_id:uint32, 2:log_data:LIN.LogDFormat` |
| 144 | `LIN.CS_NP_TOKEN_LOGIN_REQ` | `1:transaction_id:uint32, 2:client_ip_address:string, 3:client_app_id:string, 4:auth_provider_code:string, 5:auth_token:string, 6:mobile_2nd_pw:string` |
| 145 | `LIN.Ranker` | `1:rating:int32, 2:rank:int32, 3:previous_rank:int32, 4:class:int32, 5:name:string, 6:uid:int32, 7:score:int32, 8:class_rating:int32` |
| 146 | `LIN.CS_TOP_RANKER_REQ` | `1:class_id:int32, 2:version:int64` |
| 147 | `LIN.SC_TOP_RANKER_ACK` | `1:result_code:int32, 2:version:int64, 3:class_id:int32, 4:total_page:int32, 5:current_page:int32, 6:rankers:LIN.Ranker` |
| 148 | `LIN.SC_TOP_RANKER_NOTI` | `1:total_ranker:LIN.Ranker, 2:class_ranker:LIN.Ranker, 3:almost_upper_total:bool, 4:almost_lower_total:bool, 5:almost_upper_class:bool, 6:almost_lower_class:bool` |
| 149 | `LIN.CS_MY_RANKING_REQ` | `1:version:int64` |
| 150 | `LIN.SC_MY_RANKING_ACK` | `1:result_code:int32, 2:version:int64, 3:class_rank:int32, 4:previous_class_rank:int32, 5:all_rank:int32, 6:previous_all_rank:int32` |
| 151 | `LIN.SC_TEAM_EMBLEM_SWITCH_NOTI` | `1:is_turn_on:bool` |
| 152 | `LIN.MinigamePuzzleInfo` | `1:board_width:int32, 2:board_height:int32, 3:start_time:uint64, 4:end_time:uint64, 5:card_index:uint32, 6:result_card_key:bytes` |
| 153 | `LIN.MinigamePuzzleResult` | `1:card_index:uint32` |
| 154 | `LIN.MinigameCatchGremlinInfo` | `1:board_width:int32, 2:board_height:int32, 3:start_time:uint64, 4:end_time:uint64, 5:gremlin_info:LIN.MinigameCatchGremlinInfo.GremlinInfo` |
| 155 | `LIN.MinigameCatchGremlinResult` | `1:result_info_list:LIN.MinigameCatchGremlinResult.ResultInfo` |
| 156 | `LIN.MinigameAdenUserInfo` | `1:position:int32, 2:hp:int32, 3:mp:int32, 4:remain_turn_count:int32` |
| 157 | `LIN.MinigameAdenDiceInfo` | `1:dice_list:int32` |
| 158 | `LIN.MinigameAdenInfo` | `1:board_width:int32, 2:board_height:int32, 3:start_time:uint64, 4:end_time:uint64, 5:turn_count:int32, 6:user_info:LIN.MinigameAdenUserInfo, 7:magic_card_info_list:LIN.MinigameAdenInfo.MagicCardInfo, 8:box_info_list:LIN.MinigameAdenInfo.BoxInfo` |
| 159 | `LIN.MinigameAdenResult` | `(empty)` |
| 160 | `LIN.CS_MINIGAME_LIST_REQ` | `(empty)` |
| 161 | `LIN.SC_MINIGAME_LIST_ACK` | `1:minigame_info_list:LIN.SC_MINIGAME_LIST_ACK.MinigameInfo, 2:server_time:uint64` |
| 162 | `LIN.CS_MINIGAME_START_REQ` | `1:minigame_code:LIN.MinigameCode` |
| 163 | `LIN.SC_MINIGAME_START_ACK` | `1:result_code:LIN.SC_MINIGAME_START_ACK.ResultCode, 2:minigame_code:LIN.MinigameCode, 3:session_id:uint32, 4:puzzle_info:LIN.MinigamePuzzleInfo, 5:catch_gremlin_info:LIN.MinigameCatchGremlinInfo, 6:aden_info:LIN.MinigameAdenInfo` |
| 164 | `LIN.CS_MINIGAME_END_REQ` | `1:session_id:uint32, 2:puzzle_result:LIN.MinigamePuzzleResult, 3:catch_gremlin_result:LIN.MinigameCatchGremlinResult, 4:aden_result:LIN.MinigameAdenResult` |
| 165 | `LIN.SC_MINIGAME_END_ACK` | `1:result_code:LIN.SC_MINIGAME_END_ACK.ResultCode, 2:minigame_code:LIN.MinigameCode` |
| 166 | `LIN.CS_MINIGAME_ADEN_PLAY_REQ` | `1:session_id:uint32` |
| 167 | `LIN.SC_MINIGAME_ADEN_PLAY_ACK` | `1:result_code:LIN.SC_MINIGAME_ADEN_PLAY_ACK.ResultCode, 2:dice_info:LIN.MinigameAdenDiceInfo, 3:user_info:LIN.MinigameAdenUserInfo` |
| 168 | `LIN.CS_MINIGAME_ADEN_USE_CARD_REQ` | `1:session_id:uint32, 2:id:int32` |
| 169 | `LIN.SC_MINIGAME_ADEN_USE_CARD_ACK` | `1:result_code:LIN.SC_MINIGAME_ADEN_USE_CARD_ACK.ResultCode, 2:user_info:LIN.MinigameAdenUserInfo` |
| 170 | `LIN.NotificationInfomation` | `1:notification_id:int32, 2:objectid:uint32, 3:hyperlink:bytes, 4:displaydesc:bytes, 5:startdate:uint64, 6:enddate:uint64, 7:teleport:LIN.NotificationInfomation.TeleportData, 8:eventnpc:LIN.NotificationInfomation.EventNpcData` |
| 171 | `LIN.SC_NOTIFICATION_INFO_NOTI` | `1:maxpagecount:int32, 2:currentpagecount:int32, 3:notificationinfo:LIN.NotificationInfomation` |
| 172 | `LIN.SC_NOTIFICATION_CHANGE_NOTI` | `1:delete_notification_id:int32, 2:delete_objectid:int32, 3:change:LIN.NotificationInfomation` |
| 173 | `LIN.CS_NOTIFICATION_TELEPORT_REQ` | `1:notification_id:int32` |
| 174 | `LIN.CompletedAchievement` | `1:achievement_id:uint32, 2:completed_time:uint64, 3:get_reward:bool` |
| 175 | `LIN.CriteriaProgress` | `1:criteria_id:uint32, 2:quantity:uint64` |
| 176 | `LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH` | `1:total_pages:uint32, 2:current_page:uint32, 3:completed_achievmenet:LIN.CompletedAchievement` |
| 177 | `LIN.SC_ADD_CRITERIA_PROGRESS_BATCH` | `1:total_pages:uint32, 2:current_page:uint32, 3:criteria_progress:LIN.CriteriaProgress` |
| 178 | `LIN.CS_COMPLETED_ACHIEVEMENT_REWARD_REQ` | `1:achievement_id:uint32, 2:optional_reward_indexes:uint32` |
| 179 | `LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK` | `1:result:LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.eResultCode, 2:achievement_id:uint32` |
| 180 | `LIN.CS_ACHIEVEMENT_TELEPORT_REQ` | `1:achievement_id:uint32` |
| 181 | `LIN.SC_ACHIEVEMENT_TELEPORT_ACk` | `1:result:LIN.SC_ACHIEVEMENT_TELEPORT_ACk.eResultCode, 2:achievement_id:uint32` |
| 182 | `LIN.SC_CRITERIA_UPDATE_NOTI` | `1:criteria_id:uint32, 2:quantity:uint64` |
| 183 | `LIN.SC_ACHIEVEMENT_COMPLETE_NOTI` | `1:achievement_id:uint32, 2:completed_time:uint64` |
| 184 | `LIN.QuestProgress` | `1:id:uint32, 2:start_time:int64, 3:finish_time:int64, 4:objectives:LIN.QuestProgress.ObjectiveProgress, 5:is_shown_in_quest_window:bool` |
| 185 | `LIN.CS_QUEST_PROGRESS_REQ` | `(empty)` |
| 186 | `LIN.SC_QUEST_PROGRESS_ACK` | `1:quest_list:LIN.QuestProgress` |
| 187 | `LIN.SC_QUEST_PROGRESS_UPDATE_NOTI` | `1:quest:LIN.QuestProgress` |
| 188 | `LIN.CS_QUEST_REVEAL_REQ` | `1:id:uint32` |
| 189 | `LIN.SC_QUEST_REVEAL_ACK` | `1:result:LIN.SC_QUEST_REVEAL_ACK.eResultCode, 2:id:uint32` |
| 190 | `LIN.CS_QUEST_START_REQ` | `1:id:uint32` |
| 191 | `LIN.SC_QUEST_START_ACK` | `1:result:LIN.SC_QUEST_START_ACK.eResultCode, 2:id:uint32` |
| 192 | `LIN.CS_QUEST_FINISH_REQ` | `1:id:uint32, 2:optional_reward_indexes:uint32` |
| 193 | `LIN.SC_QUEST_FINISH_ACK` | `1:result:LIN.SC_QUEST_FINISH_ACK.eResultCode, 2:id:uint32` |
| 194 | `LIN.SC_QUEST_FINISH_NOTI` | `1:id:uint32` |
| 195 | `LIN.CS_QUEST_TELEPORT_REQ` | `1:id:uint32` |
| 196 | `LIN.SC_QUEST_TELEPORT_ACK` | `1:result:LIN.SC_QUEST_TELEPORT_ACK.eResultCode, 2:id:uint32` |
| 197 | `LIN.CS_QUEST_SHOW_IN_WINDOW_REQ` | `1:id:uint32, 2:is_show:bool` |
| 198 | `LIN.SC_QUEST_SHOW_IN_WINDOW_ACK` | `1:result:LIN.SC_QUEST_SHOW_IN_WINDOW_ACK.eResultCode, 2:id:uint32, 3:is_show:bool` |
| 199 | `LIN.CS_SEAL_ITEM_REQ` | `1:object_id:uint32` |
| 200 | `LIN.BUILDER_DATA_DELIVERY` | `1:builder_user:string, 2:target_user:string, 3:command:string` |
| 201 | `LIN.SC_BATTLE_ARENA_AUTO_MATCH_REQ` | `1:arena_id:int32` |
| 202 | `LIN.CS_BATTLE_ARENA_AUTO_MATCH_ACK` | `1:arena_id:int32, 2:is_apply:bool` |
| 203 | `LIN.SC_BATTLE_ARENA_AUTO_MATCH_RESULT_NOTI` | `1:arena_id:int32, 2:result:LIN.SC_BATTLE_ARENA_AUTO_MATCH_RESULT_NOTI.eResultCode` |
| 204 | `LIN.SC_EXP_INCREASE_NOTI` | `1:original_exp:uint32, 2:boosted_final_exp:uint32` |
| 205 | `LIN.SC_TURN_ON_TELEPORT_FLAG_NOTI` | `1:empty:int32` |
| 206 | `LIN.SC_MAP_TELEPORT_INFO_NOTI` | `1:npc_id:int32, 2:map_datas:LIN.SC_MAP_TELEPORT_INFO_NOTI.MapData` |
| 207 | `LIN.SC_DIALOGUE_MESSAGE_NOTI` | `1:dialogues:LIN.SC_DIALOGUE_MESSAGE_NOTI.Dialogue` |
| 208 | `LIN.SC_PLAY_MOIVE_NOTI` | `1:movie_file:string, 2:play_time_sec:uint32` |
| 209 | `LIN.SC_NPC_SPEED_VALUE_FLAG_NOTI` | `1:object_id:uint32, 2:speed_value_flag:int32, 3:second_speed_type:int32` |
| 210 | `LIN.SC_PK_MESSAGE_AT_BATTLE_SERVER` | `1:interkind:int32, 2:killer_name:string, 3:killer_cache_no:int32, 4:die_name:string, 5:die_cache_no:int32` |
| 211 | `LIN.SC_OPEN_PERSONAL_SHOP_ACK` | `1:object_id:uint32, 2:result:LIN.SC_OPEN_PERSONAL_SHOP_ACK.eOpenResult` |
| 212 | `LIN.SC_PARTY_SPELL_AVATAR_NOTI` | `1:spell_id:uint32, 2:active_flag:bool, 3:icon_id:int32, 4:desc_index:int32` |
| 213 | `LIN.SC_AVAILABLE_SPELL_CHANGE_NOTI` | `1:type:LIN.SC_AVAILABLE_SPELL_CHANGE_NOTI.Type, 2:spells:bytes` |
| 214 | `LIN.CS_CLIENT_FUNC_LOG_NOTI` | `1:fid:LIN.CS_CLIENT_FUNC_LOG_NOTI.FuncID` |
| 215 | `LIN.SC_EQUIPSET_NOTI` | `1:current_set:int32, 2:equip_sets:LIN.SC_EQUIPSET_NOTI.Sets, 3:request_limit_sec:int32, 4:limit_level:int32` |
| 216 | `LIN.CS_EQUIPSET_UPDATE` | `1:equip_set:int32, 2:equip_current_set:int32` |
| 217 | `LIN.BuffInfo` | `1:duration:uint32` |
| 218 | `LIN.SC_BUFF_NOTI` | `1:buff_type:LIN.SC_BUFF_NOTI.eBuffType, 2:buff_info:LIN.BuffInfo` |
| 219 | `LIN.CS_USER_PLAY_INFO_REQ` | `(empty)` |
| 220 | `LIN.SC_USER_PLAY_INFO_NOTI` | `1:map_time_limit_info:LIN.SC_USER_PLAY_INFO_NOTI.MAP_TIME_LIMIT_INFO` |
| 221 | `LIN.CS_MAP_TELEPORT_REQ` | `1:action:string, 2:can_ignore_combat:bool` |
| 222 | `LIN.MonsterBookV2Info` | `1:system:LIN.MonsterBookV2Info.SystemT, 2:decks:LIN.MonsterBookV2Info.DeckT` |
| 223 | `LIN.SC_MONSTER_BOOK_V2_INFO_NOTI` | `1:info:LIN.MonsterBookV2Info` |
| 224 | `LIN.CS_MONSTER_BOOK_V2_REWARD_REQ` | `1:deck_index:uint32, 2:grade:LIN.eMonsterBookV2RewardGrade` |
| 225 | `LIN.SC_MONSTER_BOOK_V2_REWARD_ACK` | `1:deck_index:uint32, 2:grade:LIN.eMonsterBookV2RewardGrade, 3:result:LIN.SC_MONSTER_BOOK_V2_REWARD_ACK.eResultCode, 4:reward_exp_amount:uint32` |
| 226 | `LIN.SC_MONSTER_BOOK_V2_UPDATE_AMOUNT_NOTI` | `1:deck_index:uint32, 2:card_index:uint32, 3:amount:uint32` |
| 227 | `LIN.SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI` | `1:deck_index:uint32, 2:state:LIN.eMonsterBookV2DeckState` |
| 228 | `LIN.CS_MONSTER_BOOK_V2_TELEPORT_REQ` | `1:deck_index:uint32, 2:card_index:uint32` |
| 229 | `LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK` | `1:deck_index:uint32, 2:card_index:uint32, 3:result:LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode` |
| 230 | `LIN.CS_PERSONAL_SHOP_OPEN_REQ` | `1:toggle:int32, 2:sell_item:LIN.CS_PERSONAL_SHOP_OPEN_REQ.ItemDesc, 3:buy_item:LIN.CS_PERSONAL_SHOP_OPEN_REQ.ItemDesc, 4:title:string, 5:polymorph:string` |
| 231 | `LIN.SC_OBJECT_EXPLOSION_NOTI` | `1:obj_id:uint32, 2:remain_tims_ms:uint32` |
| 232 | `LIN.SC_MESSAGE_BOX` | `1:msg_code:uint32, 2:modal:bool` |
| 233 | `LIN.CS_CLIENT_VERSION_INFO` | `1:protocol_version:uint32, 2:active_code_page:int32, 3:check_seed:uint32, 4:client_version:int32, 5:client_instance_count:uint32, 6:check_resource_seed:bytes, 7:is_remaster:bool, 8:client_specific_value:int32` |
| 234 | `LIN.SC_SERVER_VERSION_INFO` | `1:version_check:uint32, 2:server_id:uint32, 3:build_number:int32, 4:cache_version:int32, 5:auth_version:int32, 6:npc_server_version:int32, 7:status_start_time:int32, 8:english_only_config:int32, 9:country_code:int32, 10:client_setting_switch:int32, 11:game_real_time:int32, 12:global_cache_version:int32, 13:tam_server_version:int32, 14:arca_server_version:int32, 15:hibreed_inter_server_version:int32, 16:arenaco_server_version:int32, 17:server_type:int32` |
| 235 | `LIN.SC_TEAM_ID_SERVER_NO_MAPPING_INFO` | `1:mapping_info:LIN.SC_TEAM_ID_SERVER_NO_MAPPING_INFO.MAPPING` |
| 236 | `LIN.PartyUISpellInfo` | `1:bufficon_id:uint32, 2:is_good:bool, 3:spell_id:uint32, 4:duration:uint32, 5:tooltip_id:uint32` |
| 237 | `LIN.PartyMember` | `1:name:string, 2:accountid:uint32, 3:object_id:uint32, 4:game_class:int32, 5:gender:int32, 6:hp_ratio:int32, 7:mp_ratio:int32, 8:world:int32, 9:location:int32, 10:party_mark:int64, 11:alive_time_stamp:uint64, 12:server_no:uint32, 13:activated_spell:LIN.PartyUISpellInfo` |
| 238 | `LIN.SC_PARTY_MEMBER_LIST` | `1:leader_name:string, 2:member:LIN.PartyMember` |
| 239 | `LIN.SC_PARTY_MEMBER_LIST_CHANGE` | `1:leader_name:string, 2:out_user:string, 3:goto_inter_server_user:string, 4:new_user:LIN.PartyMember` |
| 240 | `LIN.SC_PARTY_MEMBER_STATUS` | `1:name:string, 2:object_id:uint32, 3:hp_ratio:int32, 4:mp_ratio:int32, 5:status:int32, 6:world:int32, 7:location:int32, 8:mark:int32, 9:activated_spell:LIN.PartyUISpellInfo` |
| 241 | `LIN.SC_PARTY_SYNC_PERIODIC_INFO` | `1:status:LIN.SC_PARTY_MEMBER_STATUS` |
| 242 | `LIN.SC_HYPERTEXT` | `1:url:string, 2:npcid:uint32, 3:parameter:int32, 4:text:string, 5:cookie:string` |
| 243 | `LIN.CS_PARTY_CONTROL_REQ` | `1:controlType:LIN.CS_PARTY_CONTROL_REQ.ePartyControlType, 2:target_object_id:uint32, 3:target_user_name:string, 4:target_mark:int32` |
| 244 | `LIN.ATTENDANCE_INFO` | `1:attendance_id:int32, 2:group_id:int32, 3:status:int32, 4:playtimeminute:int32` |
| 245 | `LIN.ATTENDANCE_GROUP_INFO` | `1:group_id:int32, 2:max_check_minute:int32, 3:cur_complete_today:int32, 4:max_complete_today:int32` |
| 246 | `LIN.SC_ATTENDANCE_INFO_NOTI` | `1:group_info:LIN.ATTENDANCE_GROUP_INFO, 2:attendance_info:LIN.ATTENDANCE_INFO` |
| 247 | `LIN.SC_ATTENDANCE_COMPLETE_NOTI` | `1:attendance_id:int32, 2:group_id:int32, 3:status:int32` |
| 248 | `LIN.CS_ATTENDANCE_REWARD_REQ` | `1:attendance_id:int32, 2:group_id:int32` |
| 249 | `LIN.SC_ATTENDANCE_REWARD_ACK` | `1:attendance_id:int32, 2:group_id:int32, 3:status:int32, 4:item_name_id:int32, 5:item_count:int32, 6:broadcast_item:bool` |
| 250 | `LIN.SC_ATTENDANCE_STATUS_INFO_NOTI` | `1:attendance_info:LIN.ATTENDANCE_INFO, 2:extend_info:int32` |
| 251 | `LIN.SC_ATTENDANCE_REWARD_ITEM_NOTI` | `1:reward_item_info:LIN.SC_ATTENDANCE_REWARD_ITEM_NOTI.ATTENDANCE_REWARD_ITEM` |
| 252 | `LIN.SC_BONUS_DESCRIPTION_NOTI` | `1:type:LIN.SC_BONUS_DESCRIPTION_NOTI.eType, 2:id:uint32, 3:bonus_list:LIN.SC_BONUS_DESCRIPTION_NOTI.Bonus` |
| 253 | `LIN.SC_BONUS_OCCUR_NOTI` | `1:enable:bool, 2:type:LIN.SC_BONUS_OCCUR_NOTI.eType, 3:Id:uint32, 4:addtional_bonus_list:LIN.SC_BONUS_OCCUR_NOTI.Bonus` |
| 254 | `LIN.SC_SPECIAL_RESISTANCE_NOTI` | `1:resistance:LIN.SC_SPECIAL_RESISTANCE_NOTI.Value, 2:pierce:LIN.SC_SPECIAL_RESISTANCE_NOTI.Value` |
| 255 | `LIN.SC_CROWD_CONTROL_NOTI` | `1:target_id:uint32, 2:teleport_impossible:bool` |
| 256 | `LIN.SC_SPEED_BONUS_NOTI` | `1:speedBonus:LIN.SC_SPEED_BONUS_NOTI.Value` |
| 257 | `LIN.SC_POLYMORPH_NOTI` | `1:objId:int32, 2:sprite:int32, 3:action:int32, 4:desc:bytes, 5:class_id:int32` |
| 258 | `LIN.SC_MESSAGE_NOTI` | `1:type:LIN.SC_MESSAGE_NOTI.eType, 2:msg_code:int32, 3:msg_str:string, 4:args:string, 5:option_a:int32` |
| 259 | `LIN.SC_FISH_CAUGHT` | `1:flag:bool, 2:name_id:int32, 3:msg_number:int32` |
| 260 | `LIN.SC_SUMMON_PET_NOTI` | `1:bonus_duration:int32, 2:monclass:int32, 3:petball_nameId:int32, 4:bonus_desc:bytes` |
| 261 | `LIN.CS_CHAR_ON_SERVER_TRAFFIC_INFO_REQ` | `(empty)` |
| 262 | `LIN.SC_CHAR_ON_SERVER_TRAFFIC_INFO_ACK` | `1:char_info:LIN.CharOnServerInfo, 2:traffic_info:LIN.ServerTrafficInfo` |
| 263 | `LIN.SC_SPELL_DELAY_NOTI` | `1:next_spell_delay:int32` |
| 264 | `LIN.SC_ENTRANCE_INFO_NOTI` | `1:waiting_user_count:uint32` |
| 265 | `LIN.CS_ENTRANCE_QUEUE_CANCEL_REQ` | `(empty)` |
| 266 | `LIN.CS_CHAR_LIST_ENTER_REQ` | `(empty)` |

### `MainServer_Client_ArenaCo.proto` — package `LIN` (62 messages)

| # | message class | direct fields (number:name:type) |
|---:|---|---|
| 1 | `LIN.SC_ARENACO_SERVER_STATUS_NOTI` | `1:status:LIN.SC_ARENACO_SERVER_STATUS_NOTI.eStatus` |
| 2 | `LIN.CS_ARENACO_BYPASS_MY_INFO_REQ` | `(empty)` |
| 3 | `LIN.SC_ARENACO_BYPASS_MY_INFO_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_MY_INFO_ACK.eResult, 2:record_info:LIN.ArenaRoomInfo.ArenaRecord` |
| 4 | `LIN.CS_ARENACO_BYPASS_ROOM_LIST_REQ` | `1:page_id:uint32` |
| 5 | `LIN.SC_ARENACO_BYPASS_ROOM_LIST_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_ROOM_LIST_ACK.eResult, 2:page_id:uint32, 3:total_page:uint32, 4:room_info:LIN.ArenaRoomInfo` |
| 6 | `LIN.CS_ARENACO_BYPASS_ROOM_INFO_REQ` | `1:room_id:uint32` |
| 7 | `LIN.SC_ARENACO_BYPASS_ROOM_INFO_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_ROOM_INFO_ACK.eResult, 2:room_info:LIN.ArenaRoomInfo` |
| 8 | `LIN.CS_ARENACO_CREATE_ROOM_REQ` | `1:build_info:LIN.ArenaBuildInfo` |
| 9 | `LIN.SC_ARENACO_CREATE_ROOM_ACK` | `1:result:LIN.SC_ARENACO_CREATE_ROOM_ACK.eResult, 2:room_id:uint32` |
| 10 | `LIN.CS_ARENACO_BYPASS_CHANGE_ROOM_PROPERTY_REQ` | `1:room_id:uint32, 2:title:bytes, 3:closed:bool, 4:password:string, 6:map_kind:LIN.eArenaMapKind` |
| 11 | `LIN.SC_ARENACO_BYPASS_CHANGE_ROOM_PROPERTY_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_CHANGE_ROOM_PROPERTY_ACK.eResult, 2:room_id:uint32` |
| 12 | `LIN.SC_ARENACO_BYPASS_CHANGE_ROOM_STATUS_NOTI` | `1:room_id:uint32, 2:observer_count:uint32, 3:player_info:LIN.ArenaUserInfo, 4:room_info:LIN.ArenaRoomInfo` |
| 13 | `LIN.CS_ARENACO_ENTER_ROOM_REQ` | `1:room_id:uint32, 2:role:LIN.ArenaUserInfo.eRole, 3:password:string, 4:invite_accept:bool, 5:host_char_name:bytes` |
| 14 | `LIN.SC_ARENACO_ENTER_ROOM_ACK` | `1:result:LIN.SC_ARENACO_ENTER_ROOM_ACK.eResult, 2:room_id:uint32` |
| 15 | `LIN.SC_ARENACO_BYPASS_ENTER_ROOM_NOTI` | `1:room_id:uint32, 2:user_info:LIN.ArenaUserInfo` |
| 16 | `LIN.CS_ARENACO_BYPASS_CHANGE_TEAM_REQ` | `1:room_id:uint32, 2:team_id:LIN.eArenaTeam` |
| 17 | `LIN.SC_ARENACO_BYPASS_CHANGE_TEAM_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_CHANGE_TEAM_ACK.eResult, 2:room_id:uint32, 3:team_id:LIN.eArenaTeam` |
| 18 | `LIN.SC_ARENACO_BYPASS_CHANGE_TEAM_NOTI` | `1:room_id:uint32, 2:arena_char_id:int64, 3:team_id:LIN.eArenaTeam` |
| 19 | `LIN.CS_ARENACO_BYPASS_CHANGE_READY_ENTER_REQ` | `1:room_id:uint32, 2:ready:bool, 3:own_adena:uint64, 4:entrance_adena:uint64, 5:user_level:uint32` |
| 20 | `LIN.SC_ARENACO_BYPASS_CHANGE_READY_ENTER_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_CHANGE_READY_ENTER_ACK.eResult, 2:room_id:uint32, 3:ready:bool` |
| 21 | `LIN.SC_ARENACO_BYPASS_CHANGE_READY_ENTER_NOTI` | `1:room_id:uint32, 2:arena_char_id:int64, 3:ready:bool` |
| 22 | `LIN.CS_ARENACO_BYPASS_CHANGE_LOCK_ROOM_REQ` | `1:room_id:uint32, 2:is_lock:bool` |
| 23 | `LIN.SC_ARENACO_BYPASS_CHANGE_LOCK_ROOM_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_CHANGE_LOCK_ROOM_ACK.eResult, 2:room_id:uint32, 3:is_lock:bool` |
| 24 | `LIN.SC_ARENACO_BYPASS_CHANGE_LOCK_ROOM_NOTI` | `1:room_id:uint32, 2:is_lock:bool` |
| 25 | `LIN.CS_ARENACO_BYPASS_GAME_START_REQ` | `1:room_id:uint32, 2:own_adena:uint64, 3:entrance_adena:uint64, 5:user_level:uint32` |
| 26 | `LIN.SC_ARENACO_BYPASS_GAME_START_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_GAME_START_ACK.eResult, 2:room_id:uint32` |
| 27 | `LIN.CS_ARENACO_BYPASS_ROOM_CHAT_REQ` | `1:room_id:uint32, 2:message:bytes, 3:sender_char_name:bytes` |
| 28 | `LIN.SC_ARENACO_BYPASS_ROOM_CHAT_NOTI` | `1:room_id:uint32, 2:arena_char_id:int64, 3:message:bytes, 4:sender_char_name:bytes, 5:server_id:uint32` |
| 29 | `LIN.CS_ARENACO_EXIT_ROOM_REQ` | `1:room_id:uint32` |
| 30 | `LIN.SC_ARENACO_EXIT_ROOM_ACK` | `1:result:LIN.SC_ARENACO_EXIT_ROOM_ACK.eResult, 2:room_id:uint32` |
| 31 | `LIN.SC_ARENACO_BYPASS_EXIT_ROOM_NOTI` | `1:room_id:uint32, 2:user_info:LIN.ArenaUserInfo` |
| 32 | `LIN.CS_ARENACO_INVITE_ROOM_REQ` | `1:room_id:uint32, 2:host_arena_char_id:int64, 3:guest_char_name:bytes, 4:need_password:bool, 5:buildercaster_password:bytes` |
| 33 | `LIN.SC_ARENACO_INVITE_ROOM_ACK` | `1:result:LIN.SC_ARENACO_INVITE_ROOM_ACK.eInviteResult, 2:room_id:uint32, 3:host_arena_char_id:int64` |
| 34 | `LIN.SC_ARENACO_INVITE_ROOM_NOTI` | `1:room_id:uint32, 2:host_char_name:bytes, 3:guest_char_name:bytes, 4:need_password:bool, 5:buildercaster_password:bytes, 6:from_builder:bool, 7:is_indun:bool` |
| 35 | `LIN.CS_ARENACO_INVITE_ROOM_DENY_REQ` | `1:room_id:uint32, 2:host_char_name:bytes, 3:guest_char_name:bytes, 4:is_indun:bool` |
| 36 | `LIN.SC_ARENACO_INVITE_ROOM_DENY_NOTI` | `1:room_id:uint32, 2:host_char_name:bytes, 3:guest_char_name:bytes` |
| 37 | `LIN.CS_ARENACO_BYPASS_KICK_REQ` | `1:room_id:uint32, 2:kick_arena_char_id:int64` |
| 38 | `LIN.SC_ARENACO_BYPASS_KICK_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_KICK_ACK.eResult, 2:room_id:uint32, 3:kick_arena_char_id:int64` |
| 39 | `LIN.SC_ARENACO_BYPASS_KICK_NOTI` | `1:room_id:uint32, 2:kick_arena_char_id:int64, 3:kick_char_name:bytes` |
| 40 | `LIN.ArenaActorInfo` | `1:arena_char_id:int64, 2:role:LIN.ArenaUserInfo.eRole, 3:server_id:uint32, 4:character_name:bytes, 5:character_class:LIN.CharacterClass, 6:gender:LIN.Gender, 7:team_id:LIN.eArenaTeam, 8:marker_id:int32` |
| 41 | `LIN.ArenaPlayerStatus` | `1:arena_char_id:int64, 2:kill_count:uint32, 3:death_count:uint32, 4:hp_ratio:uint32, 5:mp_ratio:uint32, 6:loc_x:int32, 7:loc_y:int32, 8:attack_amount:uint32, 9:damaged_amount:uint32, 10:heal_amount:uint32, 11:poisoned:bool, 12:paralysed:bool, 13:is_live:bool, 14:obj_id:uint32, 15:is_ready:bool` |
| 42 | `LIN.RoundScoreBoard` | `1:team_a:LIN.RoundScoreBoard.RoundScore, 2:team_b:LIN.RoundScoreBoard.RoundScore` |
| 43 | `LIN.ArenaGameStatus` | `1:play_time_msec:uint32, 2:is_timer_run:bool, 3:observer_count:uint32, 4:round_score_board:LIN.RoundScoreBoard, 5:team_status:LIN.ArenaGameStatus.TeamStatus` |
| 44 | `LIN.SC_ARENA_GAME_INFO_NOTI` | `1:player_info:LIN.ArenaActorInfo, 2:map_kind:LIN.eArenaMapKind, 3:round:uint32, 4:round_time_sec:uint32, 5:result_display_time_sec:uint32` |
| 45 | `LIN.SC_ARENA_PLAY_STATUS_NOTI` | `1:player_status:LIN.ArenaPlayerStatus, 2:game_status:LIN.ArenaGameStatus` |
| 46 | `LIN.SC_ARENA_OBJECT_STATUS_NOTI` | `1:towers:LIN.SC_ARENA_OBJECT_STATUS_NOTI.Tower` |
| 47 | `LIN.SC_ARENA_PLAY_EVENT_NOTI` | `1:type:LIN.SC_ARENA_PLAY_EVENT_NOTI.eType, 10:actor_info:LIN.ArenaActorInfo, 20:arena_char_id_target:int64, 21:arena_char_id_causer:int64, 30:team_id:LIN.eArenaTeam, 40:spell_id:uint32` |
| 48 | `LIN.SC_ARENA_PLAY_RESULT_NOTI` | `1:result:LIN.ArenaResult, 2:remain_time_sec:int32, 3:reason:LIN.SC_ARENA_PLAY_RESULT_NOTI.eReason` |
| 49 | `LIN.CS_ARENA_PLAY_EXIT_REQ` | `(empty)` |
| 50 | `LIN.CS_ARENA_CHEER_REQ` | `1:to_team_id:LIN.eArenaTeam, 2:text_msg:string` |
| 51 | `LIN.SC_ARENA_CHEER_ACK` | `1:result:LIN.SC_ARENA_CHEER_ACK.eResult` |
| 52 | `LIN.ArenaCheer` | `1:server_id:int32, 2:sender:string, 3:character_class:LIN.CharacterClass, 4:msg:string, 5:received_time:int32` |
| 53 | `LIN.SC_ARENA_CHEER_NOTI` | `1:team_id:LIN.eArenaTeam, 2:cheers:LIN.ArenaCheer, 3:total_count:int32` |
| 54 | `LIN.CS_ARENA_CHEER_LIST_REQ` | `1:team_id:LIN.eArenaTeam` |
| 55 | `LIN.SC_ARENA_CHEER_LIST_NOTI` | `1:team_id:LIN.eArenaTeam, 2:cheers:LIN.ArenaCheer, 3:begin:bool, 4:end:bool` |
| 56 | `LIN.CS_ARENA_CHAT_BLOCK_REQ` | `1:arena_char_id:int64, 2:is_blocked:bool` |
| 57 | `LIN.SC_ARENA_CHAT_BLOCK_ACK` | `1:result:LIN.SC_ARENA_CHAT_BLOCK_ACK.eResult, 2:arena_char_id:int64` |
| 58 | `LIN.CS_ARENA_OBSERVER_TELEPORT_REQ` | `1:player_arena_char_id:int64, 2:x:int32, 3:y:int32` |
| 59 | `LIN.SC_ARENA_OBSERVER_TELEPORT_ACK` | `1:result:LIN.SC_ARENA_OBSERVER_TELEPORT_ACK.eResult` |
| 60 | `LIN.CS_ARENA_PLAY_READY_REQ` | `(empty)` |
| 61 | `LIN.SC_ARENA_PLAY_READY_ACK` | `1:result:LIN.SC_ARENA_PLAY_READY_ACK.eResult` |
| 62 | `LIN.SC_SCRIPT_PRESENTATION_NOTI` | `1:type:LIN.SC_SCRIPT_PRESENTATION_NOTI.eType, 2:args:int32` |

### `MainServer_Client_BuilderCommand.proto` — package `LIN.BUILDER` (9 messages)

| # | message class | direct fields (number:name:type) |
|---:|---|---|
| 1 | `LIN.BUILDER.BuilderCommand` | `1:command_no:int32, 2:limit_builder_level:int32, 3:command:bytes, 4:need_target_id:bool, 5:need_target_pos:bool, 6:need_inven_target_id:bool` |
| 2 | `LIN.BUILDER.SC_BUILDER_COMMAND_LIST_NOTI` | `1:builder_commands:LIN.BUILDER.BuilderCommand` |
| 3 | `LIN.BUILDER.CS_BUILDER_COMMAND_REQ` | `1:command_no:int32, 2:command_params:bytes, 3:target_id:uint32, 4:target_pos_x:uint32, 5:target_pos_y:uint32` |
| 4 | `LIN.BUILDER.SC_BUILDER_COMMAND_ACK` | `1:result_code:LIN.BUILDER.SC_BUILDER_COMMAND_ACK.ResultCode, 2:result_message:bytes` |
| 5 | `LIN.BUILDER.SC_EQUIP_INFO_NOTI` | `1:target_name:bytes, 2:target_gender:int32, 3:target_class:int32, 4:equip_list_noti:LIN.SC_EQUIP_LIST_NOTI` |
| 6 | `LIN.BUILDER.SC_LIST_USER_NOTI` | `1:infos:LIN.BUILDER.SC_LIST_USER_NOTI.Info, 2:begin:bool, 3:last:bool` |
| 7 | `LIN.BUILDER.SC_SPELL_LATE_HANDLING_NOTI` | `1:on_off:bool, 2:correction_level:LIN.BUILDER.SC_SPELL_LATE_HANDLING_NOTI.eLevel` |
| 8 | `LIN.BUILDER.SC_PRE_CREATECHAR_NOTI` | `1:serverId:uint32, 2:no_char:bool` |
| 9 | `LIN.BUILDER.SC_BUILDER_MESSAGE_NOTI` | `1:target_user:LIN.BUILDER.SC_BUILDER_MESSAGE_NOTI.eTarget, 2:target_message:bytes` |

### `MainServer_Client_Companion.proto` — package `LIN` (11 messages)

| # | message class | direct fields (number:name:type) |
|---:|---|---|
| 1 | `LIN.SC_COMPANION_STATUS_NOTI` | `1:obj_id:uint32, 2:name:bytes, 3:class_id:uint32, 4:level:uint32, 5:exp:int32, 6:base_max_hp:uint32, 7:temp_max_hp:uint32, 8:hp:uint32, 9:base_str:uint32, 10:base_con:uint32, 11:base_int:uint32, 12:bonus_str:uint32, 13:bonus_con:uint32, 14:bonus_int:uint32, 15:elixir_use_count:uint32, 16:remain_stats:uint32, 17:temp_str:uint32, 18:temp_con:uint32, 19:temp_int:uint32, 20:friend_ship_guage:int32, 21:friend_ship_marble:int32, 22:minus_exp_penalty:bool, 30:ac:int32, 31:mr:int32, 32:movedelay_reduce:double, 33:attackdelay_reduce:double, 34:pvp_dmg_ratio:uint32, 35:is_combo_time:bool` |
| 2 | `LIN.CS_COMPANION_NAME_CHANGE_REQ` | `1:desired_name:bytes` |
| 3 | `LIN.SC_COMPANION_NAME_CHANGE_ACK` | `1:result:LIN.SC_COMPANION_NAME_CHANGE_ACK.eResult, 2:new_name:bytes` |
| 4 | `LIN.CS_COMPANION_STAT_INCREASE_REQ` | `1:str:uint32, 2:int:uint32, 3:con:uint32` |
| 5 | `LIN.SC_COMPANION_BUFF_NOTI` | `1:buff_list:LIN.SC_COMPANION_BUFF_NOTI.Buff` |
| 6 | `LIN.CS_COMPANION_TM_COMMAND_REQ` | `1:command:LIN.CompanionT.eCommand, 2:target_id:uint32` |
| 7 | `LIN.SC_COMPANION_SKILL_NOTI` | `1:skills:LIN.SC_COMPANION_SKILL_NOTI.Skill` |
| 8 | `LIN.CS_COMPANION_SKILL_NEXT_TIER_OPEN_REQ` | `(empty)` |
| 9 | `LIN.CS_COMPANION_SKILL_ENCHANT_REQ` | `1:skill_id:uint32` |
| 10 | `LIN.SC_COMPANION_SKILL_ENCHANT_ACK` | `1:skill_id:uint32, 2:result:LIN.SC_COMPANION_SKILL_ENCHANT_ACK.eResult` |
| 11 | `LIN.SC_COMPANION_COMBAT_DATA_NOTI` | `1:obj_id:uint32, 2:melee_hit:int32, 3:melee_cri_hit:double, 4:melee_cri_server_hit:double, 5:melee_dice_dmg:int32, 6:melee_stat_dmg:int32, 7:melee_wild_dmg:int32, 8:melee_buff_dmg:int32, 9:melee_elemental_dmg:int32, 10:melee_last_dmg:int32, 11:spell_id:int32, 12:spell_hit:int32, 13:spell_server_hit:int32, 14:spell_cri_hit:double, 15:spell_cri_server_hit:double, 16:spell_dice_dmg:int32, 17:spell_stat_dmg:int32, 18:spell_wild_dmg:int32, 19:spell_buff_dmg:int32, 20:spell_dmg_multi:int32, 21:spell_last_dmg:int32, 22:total_dmg:int32` |

### `MainServer_Client_GameGate.proto` — package `LIN` (2 messages)

| # | message class | direct fields (number:name:type) |
|---:|---|---|
| 1 | `LIN.SC_GAMEGATE_INSTANT_TOKEN_NOTI` | `1:instant_token_id:string` |
| 2 | `LIN.CS_GAMEGATE_INSTANT_TOKEN_REQ` | `1:app_id:string` |

### `MainServer_Client_Indun.proto` — package `LIN` (27 messages)

| # | message class | direct fields (number:name:type) |
|---:|---|---|
| 1 | `LIN.CS_ARENACO_BYPASS_INDUN_ROOM_LIST_REQ` | `1:page_id:uint32, 2:map_kind:LIN.eArenaMapKind` |
| 2 | `LIN.SC_ARENACO_BYPASS_INDUN_ROOM_LIST_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_INDUN_ROOM_LIST_ACK.eResult, 2:page_id:uint32, 3:total_page:uint32, 4:room_info:LIN.IndunRoomInfo` |
| 3 | `LIN.CS_ARENACO_BYPASS_INDUN_ROOM_INFO_REQ` | `1:room_id:uint32` |
| 4 | `LIN.SC_ARENACO_BYPASS_INDUN_ROOM_INFO_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_INDUN_ROOM_INFO_ACK.eResult, 2:room_info:LIN.IndunRoomDetailInfo` |
| 5 | `LIN.CS_ARENACO_CREATE_INDUN_ROOM_REQ` | `1:build_info:LIN.IndunBuildInfo` |
| 6 | `LIN.SC_ARENACO_CREATE_INDUN_ROOM_ACK` | `1:result:LIN.SC_ARENACO_CREATE_INDUN_ROOM_ACK.eResult, 2:room_id:uint32` |
| 7 | `LIN.CS_ARENACO_BYPASS_CHANGE_INDUN_ROOM_PROPERTY_REQ` | `1:room_id:uint32, 2:title:bytes, 3:min_level:int32, 4:closed:bool, 5:password:string, 6:distribution_type:LIN.eDistributionType, 7:max_player:int32, 8:map_kind:LIN.eArenaMapKind` |
| 8 | `LIN.SC_ARENACO_BYPASS_CHANGE_INDUN_ROOM_PROPERTY_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_CHANGE_INDUN_ROOM_PROPERTY_ACK.eResult, 2:room_id:uint32` |
| 9 | `LIN.SC_ARENACO_BYPASS_CHANGE_INDUN_ROOM_STATUS_NOTI` | `1:room_id:uint32, 2:observer_count:uint32, 3:player_info:LIN.ArenaUserInfo, 4:room_info:LIN.IndunRoomInfo` |
| 10 | `LIN.CS_ARENACO_ENTER_INDUN_ROOM_REQ` | `1:room_id:uint32, 2:password:string, 3:invite_accept:bool, 4:host_char_name:bytes` |
| 11 | `LIN.SC_ARENACO_ENTER_INDUN_ROOM_ACK` | `1:result:LIN.SC_ARENACO_ENTER_INDUN_ROOM_ACK.eResult, 2:room_id:uint32` |
| 12 | `LIN.SC_ARENACO_BYPASS_ENTER_INDUN_ROOM_NOTI` | `1:room_id:uint32, 2:user_info:LIN.ArenaUserInfo` |
| 13 | `LIN.CS_ARENACO_BYPASS_CHANGE_INDUN_READY_ENTER_REQ` | `1:room_id:uint32, 2:ready:bool, 3:own_adena:uint64, 4:user_level:uint32` |
| 14 | `LIN.SC_ARENACO_BYPASS_CHANGE_INDUN_READY_ENTER_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_CHANGE_INDUN_READY_ENTER_ACK.eResult, 2:room_id:uint32, 3:ready:bool` |
| 15 | `LIN.CS_ARENACO_BYPASS_INDUN_GAME_START_REQ` | `1:room_id:uint32, 2:map_kind:LIN.eArenaMapKind, 3:user_level:uint32` |
| 16 | `LIN.SC_ARENACO_BYPASS_INDUN_GAME_START_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_INDUN_GAME_START_ACK.eResult, 2:room_id:uint32` |
| 17 | `LIN.CS_ARENACO_INVITE_INDUN_ROOM_REQ` | `1:room_id:uint32, 2:host_arena_char_id:int64, 3:guest_char_name:bytes, 4:need_password:bool, 5:condition:LIN.IndunEnterCondition, 6:buildercaster_password:bytes` |
| 18 | `LIN.SC_ARENACO_INVITE_INDUN_ROOM_ACK` | `1:result:LIN.SC_ARENACO_INVITE_INDUN_ROOM_ACK.eInviteResult, 2:room_id:uint32, 3:host_arena_char_id:int64` |
| 19 | `LIN.CS_ARENACO_BYPASS_INDUN_NOTI_REQ` | `(empty)` |
| 20 | `LIN.SC_ARENACO_BYPASS_INDUN_NOTI_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_INDUN_NOTI_ACK.eResult, 2:noti_info:LIN.IndunNotiInfo` |
| 21 | `LIN.CS_ARENACO_EXIT_INDUN_ROOM_REQ` | `1:room_id:uint32` |
| 22 | `LIN.SC_ARENACO_EXIT_INDUN_ROOM_ACK` | `1:result:LIN.SC_ARENACO_EXIT_INDUN_ROOM_ACK.eResult, 2:room_id:uint32` |
| 23 | `LIN.SC_ARENACO_BYPASS_EXIT_INDUN_ROOM_NOTI` | `1:room_id:uint32, 2:user_info:LIN.ArenaUserInfo` |
| 24 | `LIN.CS_ARENACO_BYPASS_INDUN_KICK_REQ` | `1:room_id:uint32, 2:kick_arena_char_id:int64` |
| 25 | `LIN.SC_ARENACO_BYPASS_INDUN_KICK_ACK` | `1:result:LIN.SC_ARENACO_BYPASS_INDUN_KICK_ACK.eResult, 2:room_id:uint32, 3:kick_arena_char_id:int64` |
| 26 | `LIN.SC_ARENACO_BYPASS_INDUN_KICK_NOTI` | `1:room_id:uint32, 2:kick_arena_char_id:int64, 3:kick_char_name:bytes` |
| 27 | `LIN.SC_INDUN_TOWER_HIT_POINT_RATIO_NOTI` | `1:object_id:uint32, 2:hp_ratio:int32` |

### `MainServer_Client_InterRacing.proto` — package `LIN` (10 messages)

| # | message class | direct fields (number:name:type) |
|---:|---|---|
| 1 | `LIN.CS_MAPUI_TELEPORT_REQ` | `1:worldNumber:int32, 2:x:int32, 3:y:int32` |
| 2 | `LIN.SC_INTER_RACING_READY_NOTI` | `1:race:LIN.RaceInfoT, 2:racer:LIN.RacerInfoT, 3:canBuy:bool, 4:racerTicket:LIN.RacerTicketT` |
| 3 | `LIN.SC_INTER_RACING_START_NOTI` | `1:race:LIN.RaceInfoT, 2:racer:LIN.RacerInfoT` |
| 4 | `LIN.SC_INTER_RACING_STOP_NOTI` | `1:race:LIN.RaceInfoT, 2:winRacerId:int32, 3:prize:int32` |
| 5 | `LIN.SC_INTER_RACING_MOVING_NOTI` | `1:racer:LIN.RacerInfoT` |
| 6 | `LIN.CS_INTER_RACING_STATUS_INFO_REQ` | `1:worldNumber:int32` |
| 7 | `LIN.SC_INTER_RACING_STATUS_INFO_ACK` | `1:race:LIN.RaceInfoT, 2:canBuy:bool, 3:racerTicket:LIN.RacerTicketT, 4:racerMapView:LIN.RacerMapViewDataT` |
| 8 | `LIN.SC_INTER_RACING_TICKET_SELL_LIST_NOTI` | `1:npcId:int32, 2:currency:int32, 3:items:LIN.SC_INTER_RACING_TICKET_SELL_LIST_NOTI.SellItemT` |
| 9 | `LIN.CS_INTER_RACING_RANK_INFO_REQ` | `1:raceKind:int32, 2:raceTrack:int32` |
| 10 | `LIN.SC_INTER_RACING_RANK_INFO_ACK` | `1:raceKind:int32, 2:raceTrack:int32, 3:rankInfo:LIN.RaceRankInfoT` |

### `MainServer_Client_Inventory.proto` — package `LIN` (13 messages)

| # | message class | direct fields (number:name:type) |
|---:|---|---|
| 1 | `LIN.ItemInfo` | `1:object_id:uint32, 2:name_id:int32, 3:db_id:int32, 4:count:int32, 5:interact_type:int32, 6:number_of_use:int32, 7:icon_id:int32, 8:bless_code_for_display:int32, 9:attribute_bit_set:int32, 10:attribute_bit_set_ex:uint64, 11:is_timeout:bool, 12:category:int32, 13:enchant:int32, 14:deposit:uint32, 15:overlay_surf_id:int32, 16:elemental_enchant_type:int32, 17:elemental_enchant_value:int32, 18:description:bytes, 19:extra_description:bytes, 20:left_time_for_pre_notify:int32, 21:companion_card:LIN.ItemInfo.CompanionCard, 22:bless_code:int32, 23:real_enchant:int32, 24:is_merging:bool, 25:weight:int32, 26:is_identified:bool` |
| 2 | `LIN.ItemEquipInfo` | `1:object_id:uint32, 2:equip_position:int32, 3:is_equiped:bool, 4:item_info:LIN.ItemInfo` |
| 3 | `LIN.SC_EQUIP_LIST_NOTI` | `1:item_equip_infos:LIN.ItemEquipInfo` |
| 4 | `LIN.SC_EQUIP_LIST_UPDATE_NOTI` | `1:item_equip_infos:LIN.ItemEquipInfo` |
| 5 | `LIN.SC_INVENTORY_INFO_NOTI` | `1:item_infos:LIN.ItemInfo, 2:owner_object_id:uint32, 3:total_pages:int32, 4:cur_pages:int32` |
| 6 | `LIN.SC_ADD_INVENTORY_NOTI` | `1:item_info:LIN.ItemInfo, 2:on_start:bool, 3:owner_oid:uint32` |
| 7 | `LIN.SC_UPDATE_INVENTORY_NOTI` | `1:item_info:LIN.ItemInfo` |
| 8 | `LIN.SC_INVENTORY_EFFECT_NOTI` | `1:item_id:uint32, 2:effect_code:LIN.SC_INVENTORY_EFFECT_NOTI.eEffectCodeType` |
| 9 | `LIN.TradeItemInfo` | `1:index:int32, 2:count:int32, 3:price:int32, 4:item_info:LIN.ItemInfo, 5:trade_info:int32, 6:buy_count:int32` |
| 10 | `LIN.SC_PERSONAL_SHOP_ITEM_LIST_NOTI` | `1:type:LIN.SC_PERSONAL_SHOP_ITEM_LIST_NOTI.ePersonalShopType, 2:seller_id:int32, 3:size:int32, 4:item_list:LIN.TradeItemInfo` |
| 11 | `LIN.SC_WAREHOUSE_ITEM_LIST_NOTI` | `1:checker:int32, 2:count:int32, 3:warehouse_type:LIN.SC_WAREHOUSE_ITEM_LIST_NOTI.eWarehouseType, 4:price:int32, 5:warehouse_size:int32, 6:item_list:LIN.SC_WAREHOUSE_ITEM_LIST_NOTI.WarehouseItemInfo, 7:serial_last:bool` |
| 12 | `LIN.SC_EXCHANGE_ITEM_LIST_NOTI` | `1:type:LIN.SC_EXCHANGE_ITEM_LIST_NOTI.eExchangeType, 2:item_list:LIN.ItemInfo` |
| 13 | `LIN.SC_GOODS_INVEN_NOTI` | `1:add_state:bool` |

### `MainServer_Client_PlaySupport.proto` — package `LIN` (8 messages)

| # | message class | direct fields (number:name:type) |
|---:|---|---|
| 1 | `LIN.CS_START_PLAY_SUPPORT_REQ` | `(empty)` |
| 2 | `LIN.SC_START_PLAY_SUPPORT_ACK` | `1:result:LIN.SC_START_PLAY_SUPPORT_ACK.eResult` |
| 3 | `LIN.CS_FINISH_PLAY_SUPPORT_REQ` | `(empty)` |
| 4 | `LIN.SC_FINISH_PLAY_SUPPORT_ACK` | `1:remain_time:uint32` |
| 5 | `LIN.SC_FORCE_FINISH_PLAY_SUPPORT_NOTI` | `1:reason:LIN.SC_FORCE_FINISH_PLAY_SUPPORT_NOTI.eReason, 2:remain_time:uint32` |
| 6 | `LIN.CS_PSS_TIME_CHECK_REQ` | `(empty)` |
| 7 | `LIN.SC_PSS_TIME_CHECK_ACK` | `1:remain_time:uint32` |
| 8 | `LIN.SC_UPDATE_PLAY_SUPPORT_TIME_NOTI` | `1:type:LIN.SC_UPDATE_PLAY_SUPPORT_TIME_NOTI.eType, 2:remain_time:uint32` |

### `MainServer_Client_Pledge.proto` — package `LIN` (9 messages)

| # | message class | direct fields (number:name:type) |
|---:|---|---|
| 1 | `LIN.WorldInfo` | `1:world_id:int32, 2:world_state:LIN.WorldInfo.eState` |
| 2 | `LIN.CS_BLESS_OF_BLOOD_PLEDGE_PICK_REQ` | `1:world:LIN.WorldInfo` |
| 3 | `LIN.CS_BLESS_OF_BLOOD_PLEDGE_SHUFFLE_REQ` | `(empty)` |
| 4 | `LIN.SC_BLESS_OF_BLOOD_PLEDGE_ACK` | `1:result:LIN.SC_BLESS_OF_BLOOD_PLEDGE_ACK.eResult` |
| 5 | `LIN.SC_BLESS_OF_BLOOD_PLEDGE_UPDATE_NOTI` | `1:world_candidate:LIN.WorldInfo, 2:can_shuffle:bool` |
| 6 | `LIN.SC_REST_EXP_INFO_NOTI` | `1:rest_gauge:int32, 2:default_ration:int32, 3:extra_exp_ratio:int32, 4:reduce_efficiency:int32, 5:bless_of_blood_pledge:bool` |
| 7 | `LIN.PledgeInfo` | `1:pledge_id:uint32, 2:pledge_name:string, 3:master_name:string, 4:emblem_id:uint32` |
| 8 | `LIN.SC_BLOOD_PLEDGE_ALLY_LIST` | `1:master:LIN.PledgeInfo, 2:leader_pledge_foundation_day:int32, 3:allys:LIN.PledgeInfo` |
| 9 | `LIN.SC_BLOOD_PLEDGE_ALLY_LIST_CHANGE` | `1:add_pledge:LIN.PledgeInfo, 2:remove_pledge:LIN.PledgeInfo, 3:dismiss_ally:bool` |

### `MainServer_Client_WeaponMastery.proto` — package `LIN` (12 messages)

| # | message class | direct fields (number:name:type) |
|---:|---|---|
| 1 | `LIN.InputItemT` | `1:slot_id:int32, 2:name_id:int32` |
| 2 | `LIN.SC_WEAPON_MASTERY_INFO_NOTI` | `1:weapon_mastery:LIN.WeaponMasteryT, 2:mastery_gauge:int32` |
| 3 | `LIN.SC_WEAPON_MASTERY_GAUGE_NOTI` | `1:mastery_gauge:int32` |
| 4 | `LIN.SC_WEAPON_MASTERY_POINT_NOTI` | `1:weapon_type:int32, 2:mastery_point:int32` |
| 5 | `LIN.CS_WEAPON_MASTERY_ENCHANT_REQ` | `1:weapon_type:int32, 2:mastery_slot_id:int32, 3:input_item:LIN.InputItemT` |
| 6 | `LIN.SC_WEAPON_MASTERY_ENCHANT_ACK` | `1:weapon_type:int32, 2:mastery_slot_id:int32, 3:enchant_value:int32, 4:result:LIN.SC_WEAPON_MASTERY_ENCHANT_ACK.eResult` |
| 7 | `LIN.CS_WEAPON_MASTERY_ROULETTE_TRY_REQ` | `1:weapon_type:int32` |
| 8 | `LIN.SC_WEAPON_MASTERY_SLOT_OPEN_NOTI` | `1:weapon_type:int32, 2:mastery_slot_id:int32, 3:enchant_value:int32` |
| 9 | `LIN.SC_WEAPON_MASTERY_ROULETTE_TRY_ACK` | `(empty)` |
| 10 | `LIN.CS_WEAPON_MASTERY_ROULETTE_SLOT_LOCK_REQ` | `1:weapon_type:int32, 2:roulette_slot_id:int32, 3:action:LIN.CS_WEAPON_MASTERY_ROULETTE_SLOT_LOCK_REQ.eAction` |
| 11 | `LIN.SC_WEAPON_MASTERY_ROULETTE_SLOT_LOCK_ACK` | `(empty)` |
| 12 | `LIN.SC_WEAPON_MASTERY_ROULETTE_SLOT_OPEN_NOTI` | `1:weapon_type:int32, 2:mastery_slot_id:int32, 3:bonus_id:int32` |

## F. 서버팩 개발 시 직접 사용할 계약과 금지할 추측

1. **모델·DTO 생성:** `schemas/*.reconstructed.proto`의 패키지/정확한 필드 번호/타입/required 여부로 양측 스키마를 맞춘다. 네트워크 packet ID와 protobuf message 이름은 서로 다른 축이다.
2. **게임 네트워크:** 패킷의 1-byte 상위 분기와 16-bit 하위 분기 가능성을 분리해 실측한 실제 opcode 등록표와 대조한다. 이 분석으로 numeric opcode를 `CS_*`에 임의 부여하지 않는다.
3. **PSS 정책:** 시작 승인·맵 제한·레벨 제한·시간 소진·사망·맵 시간 소진을 result/reason별 분기한다. 값은 schema에 있지만 정책 조건·기본 시간·재충전 저장 로직은 서버 소스를 통해 검증한다.
4. **세션/토큰:** `CS_NP_LOGIN_REQ`, `CS_NP_TOKEN_LOGIN_REQ`, GameGate 및 HIBREED transfer 메시지의 데이터 필드는 확인되지만 서명, 중복 로그인, 만료 및 탈취 방지는 별도 서버 구현 검증 대상으로 둔다.
5. **제작·아이템:** `batch_transaction_id`와 창고·거래 `ItemInfo`의 존재를 재현하되 이 필드만 보고 DB 트랜잭션이나 아이템 복제 방지 로직이 원본과 같다고 단정하지 않는다.
6. **MainServer 함수:** 이 파일은 클라이언트의 `Lineage::MainServerPacketHandler`를 복원한 것이지, 서버 `MainServer.exe`의 C++ 함수 배치·SQL·인증 처리 코드를 역분석한 것은 아니다.

## G. 미확인·제약

- 복호화 이미지는 PE 전체 SizeOfImage(`0x01B2F000`)가 아닌 0x140D000바이트 부분 덤프. descriptor·RTTI·일부 패킷 디스패처만 확정 가능.
- FileDescriptorProto가 보존하는 것은 schema. 원본 `.proto`의 주석/서비스 서버 구현·클라이언트의 wire opcode 번호가 아니다.
- C++ 함수의 실제 이름을 디버그 심볼로 갖고 있지 않다. 기능별 `CS/SC` 처리 함수 주소와 모든 송신 호출은 미완성, 억지로 이름·주소를 만들지 않음.
- 캡처·동적 실행이 없어 로그인/인던/창고/시장 검색 각 패킷이 실제로 어떤 순서·값으로 송수신되는지는 미확정.
- `Lineage_CommonDataTypes.proto` 및 기반 `.proto`는 이번 **MainServer 전수 파일 11개** 외부의 공용 타입 의존성을 설명하는 자료이며, 사용자 요청의 주 대상과 혼동하지 않음.
- 이 보고서는 실제 소스 바이트의 정적 결과다. 서버 `MainServer.exe` 및 PDB가 없는 이상 MainServer 내부 native 함수명/SQL까지 완료라고 표기할 수 없다.

## H. 추출 근거·재현 방법

1. 복호화 이미지 `0x00400000` 기준 `.proto` 식별자들을 찾아 바로 앞 Protobuf field#1 (`0x0A, name_length`)에서 시작하는 `FileDescriptorProto` 필드 열을 복원.
2. `google.protobuf.descriptor_pb2.FileDescriptorProto.ParseFromString`로 파일 name/package/dependency/message_type/field/enum 수치 직접 검증; 시작 주소와 길이 아래 별도 `schema_index.json`에 기록하지 않고 B절에 모두 표시.
3. `MainServerPacketHandler` MSVC RTTI type descriptor와 vtable 참조를 찾아 `0x009928C0`/`0x00AE5AB0`로 추적. x86 `movzx`, `cmp`, byte-index lookup, indirect jump, direct call을 확인.
4. 상태=3에서 0x00AE6690(255 byte case index)/0x00AE64D0(32-bit jump target), 다른 상태에서 0x00AE67D0(252 byte case index)/0x00AE6790을 주소로 직접 읽어 `dispatch_*.csv` 생성. handler의 첫 인접 `call rel32`가 있으면 목적 VA 기재. 이 단순 데이터 추적을 완전한 호출 그래프라고 주장하지 않는다.
5. 현 보고서와 11개 재구성 `proto`, 2개 x86 `CSV` 모두 동일한 21,024,768-byte 복호화 이미지에서 생성됨. 실제 raw 바이너리/덤프는 저장소에 이 보고서 게시를 위해 추가 업로드하지 않음.

## I. 기존 연구와 연결

- [기존 MJBin PSS 정적 분석](../REVERSE_ENGINEERING_20261113.md)
- [MainServer 연동 작업 기준](../MAIN_SERVER_WORKSPACE_20261114.md)
- [PSS 세부 설정](../schemas/PlaySupport.reconstructed.proto)

