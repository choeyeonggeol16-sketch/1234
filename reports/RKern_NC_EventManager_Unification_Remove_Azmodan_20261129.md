# RKern NC식 게임 이벤트 통합 및 아즈모단 완전 제거 지시서

작성일: 2026-11-29 KST
RKern 조사 기준: [rkern/gameplay-optimization-20261129](https://github.com/choeyeonggeol16-sketch/1234/tree/rkern/gameplay-optimization-20261129)
NC 조사 기준: [Open_LU4_L2OFF abbb2e5](https://github.com/luannbr/Open_LU4_L2OFF/tree/abbb2e5f474d40cf12539a8e97fddc52474ed95e) (공개 OFF 복원 소스).
**이 커밋은 구현 지시서만 추가한다. 기존 Java/DB/게임 브랜치는 수정하지 않는다. 구현 담당자는 변신, NPC 대사 등 진행 중인 최신 변경을 포함한 전체 소스를 먼저 확보한다.**

## 1. 최종 목표

기능별 Controller, DateScheduler, Loader에 분산된 월드/콘텐츠 이벤트의 스케줄 등록·실행·종료·리로드·시간 변경 대응을 NC의 CEventManager/CEvent 구조로 통합한다. 실제 게임 기능을 통째로 한 클래스로 몰아넣는 것이 아니라 공통 일정과 수명 관리를 일원화하고, 각 기능의 기존 상태 및 실제 실행 책임은 해당 소유자에 둔다.

- 전용 패키지 model.world.event에 CEventManager, CEvent 및 실제 필요한 NC 명칭의 이벤트 책임을 집중한다. 패키지 경로는 L1 적응안이며 기존 동일 책임 클래스가 있으면 통합한다.
- 필드/사냥터 상태는 NC CFieldCycleSystem/CFieldCycle의 책임에 대응한다. 게임 서버 공통 일정과 필드 소유 상태를 구분한다.
- 이벤트 정의 등록, 식별자/타입 조회, 활성/비활성 상태, 개방·종료·실행 시각, 재등록/취소/리로드, 실패 복구 및 종료를 공통화한다.
- CServerTimer, CGameTime, CIOThreadCallback timing wheel, CNPCServer, CWorldWork/CInstanceWork, CacheD 등 이미 존재하는 실행 기반을 그대로 활용한다. 독립 timer thread, executor, 새 worker, 이중 timing wheel 및 중복 queue 금지.
- 실제 기능 이관과 전체 호출 검증을 완료한 뒤 기존 흩어진 스케줄러/어댑터/호환 클래스와 GameServer의 개별 등록·종료 호출을 제거한다. 기존 클래스가 도메인 상태도 관리하면 먼저 해당 책임을 보존·이관하며 클래스 삭제만으로 기능을 없애지 않는다.
- NPC 대사 및 오라는 각각 작성된 별도 NC 개편 지시서를 유지한다. 변신은 현재 작업 중이므로 범위 제외. 공성·기간제 아이템·NPC/플레이어 타이머의 기존 도메인 실행 구조를 불필요하게 다시 작성하지 않는다.
- **아즈모단 혈맹 지하 던전은 통합하지 말고 기능·코드·일일 초기화·전용 데이터 참조를 완전히 제거한다.**

## 2. NC 실제 구조와 적용 기준

### 2.1 공통 게임 이벤트

[GameEventSystem.h L119–184](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/GameEventSystem.h#L119-L184):
CEvent의 실제 공통 인터페이스는 Load, Init, InEventTime, InMyTick, DoTickAction, IsAllDone, ShouldBeRegistered, GetEventType이다. 이벤트 ID/이름/기간/시작 상태 등을 소유한다.
[GameEventSystem.h L1130–1212](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/GameEventSystem.h#L1130-L1212):
CEventManager는 CIOObject + Singleton 기반이다. 등록/미등록 이벤트 map, 타입별 인덱스, GetEvent, SetEventEnable, Reload, ReloadAll, TimerExpired를 제공한다.
[GameEventSystem.cpp L4098–4168](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/GameEventSystem.cpp#L4098-L4168):
이벤트 설정 로딩→factory→Load→_RegisterEvent→타입 인덱스 구성→InitEvent→AddTimer(1000,0).
[GameEventSystem.cpp L4255–4310](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/GameEventSystem.cpp#L4255-L4310):
TimerExpired에서 미등록 이벤트의 활성 조건을 검사하고 등록 map으로 이동한다. 등록된 CEvent_World는 CheckEventState를 실행하고, InEventTime 및 InMyTick을 통과한 이벤트만 DoTickAction을 실행한다. 끝에서 1초 타이머를 재등록한다.
[GameEventSystem.h L530–563](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/GameEventSystem.h#L530-L563):
CEvent_World는 eventId/state/param 및 예약 시각 목록을 가진다.
[GameEventSystem.cpp L295–329](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/GameEventSystem.cpp#L295-L329), [L477–519](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/GameEventSystem.cpp#L477-L519):
CEvent_CoretimeTeleport는 실제 날짜/시각/요일 조건과 시간대별 가격 계산을 별도 책임으로 처리한다. RKern의 강제 퇴장형 사냥터와 동일한 기능이라고 단정하지 않는다.

### 2.2 기능별 소유 구조도 존재한다

- [FieldCycle.h](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/FieldCycle.h): CFieldCycleSystem, CFieldCycle. [FieldCycle.cpp L1495–1610](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/FieldCycle.cpp#L1495-L1610)에서 단계 전환, timer ID dispatch, 시간 조건, 영역/문 상태 변경 확인.
- [AirShipAction.cpp L499–528](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/AirShipAction.cpp#L499-L528): CShipController::TimerExpired → Serializer → actionNumber 유효성 검사 → 현재 CShipAction::OnTick. 선박 자체의 상태 전환은 선박 소유 실행부 책임이다.
- [AnnounceTimer.cpp L157–188](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/AnnounceTimer.cpp#L157-L188): CAnnounceTimer의 간격 공지 및 타이머 재등록.
- [ReservationAnnounceTimer.cpp L115–136](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/ReservationAnnounceTimer.cpp#L115-L136): CReservationAnnounceTimer의 예약 공지 및 마지막 전송 시각.
- [ReservationNPCSpawnTimer.cpp L401–410](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/ReservationNPCSpawnTimer.cpp#L401-L410): CReservationNPCSpawnTimer의 예약 스폰 처리.
- [MonRaceData.h](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/MonRaceData.h), [MonRaceData.cpp L219–232](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/MonRaceData.cpp#L219-L232): CMonRaceDB/CMonRaceData가 경주 상태 및 DB 초기화를 관리한다. 전체 NC 경주 타이머 호출부는 구현 전 추가 확인.
- [EventMatch.h L118–248](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/EventMatch.h#L118-L248): CEventMatch/CEventMatchMgr가 경기별 실행 책임을 소유한다. L1 투견/무한대전과 일대일 기능 동일성은 미확인.
- [L2Time.cpp L131–178](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/L2Time.cpp#L131-L178), [L398–437](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/L2Time.cpp#L398-L437): CL2Time에서 정산·기반 서비스 시간 작업을 처리하는 별도 timer ID가 존재한다.

**NC가 모든 타이머를 CEventManager 하나로 몰아넣는 것은 아니다.** NC에서 확인된 CEventManager의 일정/상태/등록 책임을 RKern에 도입하고, L1의 기존 timing wheel을 재사용하여 전역 이벤트 등록을 통합하는 것이 목표다. NC가 1초 폴링을 하므로 동일하게 복제하라는 뜻도 아니다. RKern은 개방·종료 등 명확한 다음 시각이 있는 이벤트를 deadline에 직접 예약하여 반복 검사 비용을 줄인다. NC 명칭은 실제 대응 책임이 확인된 경우에만 사용한다.

## 3. 현행 RKern 조사 목록과 이관 계획

[GameServer.java L298–354](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/GameServer.java#L298-L354), [L571–609](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/GameServer.java#L571-L609)에서 각 서비스의 시작/종료 경로를 확인했다. 아래는 등록 경로이며 운영 설정으로 비활성일 수 있다.

| 현행 클래스/기능 | 현재 실행 | 이관 및 삭제 지시 |
|---|---|---|
| OneTimeController | CGameTime SECOND, 자정 혈맹 지하 던전 초기화 | 아즈모단과 함께 완전 삭제. CEventManager에 등록하지 않는다. |
| MinuteScheduler / AbstractDateScheduler / DateSchedulerLoader | MINUTE listener, 모든 acceptor 반복 검사 | CEventManager 일정으로 이관, CFieldCycleSystem/CFieldCycle 및 경기 소유 이벤트로 분리. 구형 3개 클래스 삭제. |
| AbstractDateAcceptor / AcceptorToNpcListenerAdapter / AcceptorToCombatListenerAdapter | 요일/시간 조건, 개방/종료, 입장 및 경기 | 공통 이벤트 상태/기간과 실제 입장/경기 로직 분리. 기존 adapter 제거. |
| DominanceTimeController | 1초 신호, 분별 지배의 탑 보스 검사 | 기존 CBossNpcMaker/스폰 책임과 일정 통합. 별도 전역 컨트롤러 삭제. |
| CBossNpcMaker | 보스 deadline 및 60초 복구 예약 | 이미 있는 NPC 스폰 소유자는 유지. 공통 이벤트 일정 및 recovery admission을 통합하되 실제 재스폰/스폰 엔진 이중 실행 금지. |
| ServerMacroLoader | tb_servermacro, 1초 목록 검사 | 일정 등록 및 메시지 실행을 공통 이벤트로 이관. 로더/별도 heartbeat 제거 또는 실제 설정 로딩 책임만 흡수. |
| CAnnounceTimer | 설정된 공지 간격, 최초 3분 지연 | NC CAnnounceTimer는 실제 이름이므로 방송 기능의 책임은 유지할 수 있다. 개별 독립 예약은 공통 이벤트 등록으로 통합하고 ServerMacroLoader와 중복 메시지 책임 정리. |
| BugRaceController | CGameTime SECOND, 경기 상태 및 개별 주자 타이머 복구 | CMonRaceDB/CMonRaceData의 실제 역할과 대조, 기존 경주 로직·베팅·정산·개별 주자 소유자 유지. 경기 일정은 통합. 기존 전역 controller 제거 후 모든 호출 이관. |
| ShipTimeController | 5초마다 선박 운행 시간대 검사 | CShipController/CShipAction 책임으로 이관. 출항/도착/탑승 가능한 구간 상태는 기존 L1 규칙 유지. 별도 5초 컨트롤러 제거. |
| DogFightLoader | 설정 활성 시 30초 후 경기 자동 예약 | CEventMatch/CEventMatchMgr 참고, 공통 일정 등록과 실제 경기 소유자 분리. 자동 시작 전용 스케줄 중복 제거. |
| L1UltimateBattle | 무한대전 일정 | 경기 소유 실행은 보존하고 공통 일정으로 편입. 실제 start/stop 및 동적 등록 전체 조사 후 변경. |
| L1ClanRanking | 1분마다 CacheD 제출, 정각 갱신 | 정각 deadline 기반 공통 유지보수 작업으로 등록. 랭킹 데이터/DB 작업의 owner 보존. |
| NpcShopManager | AUTO_GM_SHOP 설정 시 1시간 보충 | 상점 소유 상태 유지. 주기 등록은 공통 시간 작업으로 이관. |
| CWorldAgitTimer | 다음 만료 deadline + SECOND 복구 검사 | 기존 아지트 deadline/generation 소유 유지. 필요 시 공통 일정 API에 연결하고 독립 heartbeat 중복 검토. 공성/아지트 구조 전면 재작성 금지. |
| CResidenceSettlement | 게임 날짜/시간 listener, 25일 정산 | 기존 CGameTime 게임 시간 이벤트 사용. 중복 timer 없이 CEventManager에서 등록 lifecycle만 통합 가능. DB 정산 확정성 유지. |
| CCastleSiegeManager | SECOND listener, 공성 상태 | 최근 개편된 공성 기능과 이름 유지. 이벤트 시작/종료 등록 계약만 검토. 중복 작업/재작성 금지. |
| CInventoryExpiry | 만료 heap, 기존 CIO wheel, 유휴 250ms | 아이템 만료는 콘텐츠 이벤트가 아니다. 기존 transactional expiry 및 owner 유지. 불필요한 유휴 poll은 별도 검증 범위로 남기고 이 보고서로 강제 흡수하지 않는다. |
| AuraController | 군주 오라와 혈맹 축복 2초 | 별도 오라 개편 보고서대로 캐릭터/혈맹 소유 처리. CEventManager 전역 오라 스캔으로 되돌리지 않는다. |
| NpcChatTimeController | 분별 NPC 대사 | 별도 NPC 대사 보고서대로 CNPCServer/TimerData owner wheel 이관. 중복 수정 금지. |
| CGameTime, CServerTimer | 공통 clock / CIO timer | 유지. 새 스케줄러로 재작성하지 않는다. |

보스, 상점, 랭킹, 공성, 기간제, 아지트, 경기 등에서 이미 구현된 독립적인 도메인 상태/기존 owner timer는 삭제 목표 자체가 아니다. 제거 대상은 중복된 전역 일정 관리와 불필요한 Controller/Wrapper다. AppCenter HTTP 등 운영 서비스, 로그인/네트워크 타이머, CacheD, NPC action, 스킬 효과, 던전 개인 이용시간은 게임 이벤트 통합과 분리한다.

## 4. P0 — 전체 참조 인벤토리 및 중복 검증

최신 checkout에서 다음 문자열 및 각 클래스 호출자 전체를 rg로 확인하고 결과를 파일/줄/소유자/등록조건/주기/DB/종료 방식별로 기록한다.
```sh
rg -n 'CServerTimer\.|scheduleServerTimer\(|scheduleAtFixedRate\(|scheduleWithFixedDelay\(|scheduleRequired|CGameTime\.(addRealListener|removeRealListener)|addGameListener\(|new Timer\(|ScheduledExecutorService|Thread\.sleep\(' rkern/src
rg -n 'OneTimeController|Azmodan(System)?|UnderDungeon|UnderMapid|under_dungeon|4703|MinuteScheduler|AbstractDateScheduler|DateSchedulerLoader|AbstractDateAcceptor|AcceptorToNpcListenerAdapter|AcceptorToCombatListenerAdapter|DominanceTimeController|CBossNpcMaker|ServerMacroLoader|CAnnounceTimer|BugRaceController|ShipTimeController|DogFightLoader|L1UltimateBattle|L1ClanRanking|CWorldAgitTimer|CResidenceSettlement|NpcShopManager' rkern
```
GameServer 이외 lazy singleton 생성, GM reload, NPC packet, action listener, dungeon manager, 스폰/종료 콜백, 테스트, SQL/XML 설정을 빠짐없이 조사한다. 현재 보고서는 주요 경로를 직접 읽은 결과이며 전체 Java 파일의 rg를 이미 실행한 것으로 주장하지 않는다. 기존 보고서 작업에 의해 이미 삭제/수정된 기능은 최신본 기준 다시 판정하고 복원하지 않는다.

## 5. P1 — 아즈모단 및 OneTimeController 완전 제거 (선행 작업)

### 확인된 사실

- [NpcServiceHtmlCommands.java L80–115](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/network/core/cs/packet/NpcServiceHtmlCommands.java#L80-L115): 아지트 관리인 action "g"에서 개방 확인 패킷 및 입장 텔레포트가 주석 처리되어 있고 리뉴얼로 이용 불가 메시지와 return이 들어가 있다.
- [CS_Attr.java L336–341](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/network/core/cs/packet/CS_Attr.java#L336-L341): case 4703에서도 위조 확인 패킷으로 던전이 개방되거나 혈맹 재화가 차감되지 않도록 차단한다.
- [AzmodanSystem.java](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/world/instance/dungeon/clandungeon/AzmodanSystem.java): startAzmodan, activeClans, dailyClanMaps 및 전용 CInstantZoneManager 잔존.
- [Azmodan.java L151–180](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/world/instance/dungeon/clandungeon/Azmodan.java#L151-L180): 종료 시 혈맹 상태를 2로 변경하고 DB 갱신.
- [OneTimeController.java L37–99](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/Controller/OneTimeController.java#L37-L99): SECOND listener가 남아 있고 자정 시간대 매일 모든 혈맹을 순회해 updateUnderDungeonOrThrow, setUnderDungeon(0), setUnderMapid(0), removeClan을 수행한다.
- [ClanTable.java L840–857](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/datatables/ClanTable.java#L840-L857): 혈맹별 직접 JDBC UPDATE clan_data SET under_dungeon = ? WHERE clan_id = ?. L114에서 under_dungeon 로드.
- [L1Clan.java L624–659](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/L1Clan.java#L624-L659): _underDungeon, _underMapid 및 getter/setter 잔존.

### 삭제 지시

1. 전체 참조 감사 후 Azmodan.java, AzmodanSystem.java, Controller/OneTimeController.java 삭제. GameServer import/start/stop 삭제. CGameTime listener 등록 자체가 사라져야 한다.
2. NpcServiceHtmlCommands의 아즈모단 전용 action "g" 분기, 관련 템플릿/HTML 연결, 주석 처리된 개방/입장, pc.setTempID 등 전용 부수 동작 정리. 일반 아지트 구매/관리/귀환, 다른 "g" 동작은 보존한다. CS_Attr 4703는 삭제 이후에도 기본 invalid packet 처리로 무해하게 거부되도록 확인. 삭제가 우회 진입/재화 차감을 만들면 안 된다.
3. L1Clan의 _underDungeon/_underMapid 및 getter/setter, ClanTable의 under_dungeon 로딩/삽입/업데이트 등 전용 참조를 모두 제거한다. _rankTime/_rankDate 등 무관 필드 보존. SQL 및 CacheD에서도 참조 0을 확인한다.
4. 전용 npcchat/HTML/스폰/맵 설정 및 DB 데이터가 실제로 아즈모단에만 사용되는 경우에만 제거한다. 공용 몬스터 ID, 아지트 지하실 구매, 혈맹 축복의 기운, 공용 레이드, 인스턴스 공통 코드 임의 삭제 금지.
5. under_dungeon 컬럼은 전체 schema/query/운영 데이터 의존성을 확인한 후 별도 migration으로 정리한다. 백업 및 복구 경로를 확보하고 운영 DB에 즉시 DROP을 실행하지 않는다. 보고서 작성 자체는 DB를 수정하지 않는다.
6. 삭제 전후 로그인/혈맹 생성·가입·해산·저장, 아지트 관리 및 일반 인스턴스 던전 테스트. 자정 혈맹 전수 UPDATE와 OneTimeController timer가 0인지 검증한다. 아즈모단을 CEventManager에 새로 등록하지 않는다.

## 6. P2 — 공통 이벤트 소유 및 시간 관리

- CEventManager.Init/등록/조회/활성화/비활성화/Reload/ReloadAll/TimerExpired 역할을 NC에 맞춘다. CEvent는 기존 일정과 이벤트 상태를 소유하며 GetEventType, InEventTime, InMyTick, DoTickAction 등의 실제 책임을 적용한다. 전부 가상 클래스/람다/객체로 쪼개지 말고 이벤트 수와 기능별 실행 빈도에 맞춰 단순화한다.
- 각 이벤트 식별자와 generation, 다음 open/close/tick deadline, 활성 상태를 한 군데서 관리한다. primitive ID/배열 및 기존 timing wheel을 우선하고, 정의가 없는 이벤트의 예약/상태 객체를 생성하지 않는다.
- 하나의 중앙 실행 진입점과 기존 timing wheel 예약으로 필요한 시점에만 dispatch한다. 매초 전체 이벤트를 복제 스캔하는 새 루프나 별도 priority queue를 무조건 만들지 않는다. 기존 wheel에서 효율적으로 지원하는 예약 방식을 선택한다. 고유 경기/선박/NPC 액션의 세밀한 타이밍은 도메인 소유자가 관리한다.
- 실제 시간의 날짜/요일/시간대/DST 및 벽시계 보정, 게임 시간의 가속/경계 통과를 구분한다. monotonic 지연과 wall-clock 목표를 혼동하지 않는다. 스케줄 지연/서버 재시작 시 이미 열려 있는 이벤트의 복구, 종료 시각 처리, 중복 재실행 방지 정책을 기능별로 보존한다.
- manager/timer는 상태 확인 및 작업 게시만 담당. SQL/CacheD 응답 대기, 월드 전수 순회, 경기 루프, sleep, 네트워크 send drain 등을 CIO timer/네트워크 owner에서 실행하지 않는다. CWorldWork/CInstanceWork/CacheD와 대상 CIO owner 경로를 기존 책임에 맞춰 사용한다.
- 중복 실행 방지, 기존 bounded admission/retry, 정지/리로드 stale generation 무효화, 실행 완료 확인, 예외 격리를 공통화한다. 한 이벤트 실패가 다른 이벤트를 중지시키지 않도록 한다.
- 리로드는 전체 유효 정의를 별도로 구성·검증 후 원자적으로 게시하고, 이전 예약 무효화와 실제 상태 이관을 직렬화한다. SQL 실패 시 구형 유효 정의 유지. shutdown 시 신규 등록 차단→타이머/이벤트 취소→기존 작업 drain→저장 및 owner 종료 순서 보존.
- GameServer에는 CEventManager 초기화/start/stop 등 공통 lifecycle과 이미 존재하는 도메인 서비스의 필수 초기화만 남긴다. 별도 기능마다 중복적인 timer start/stop 호출을 남겨두지 않는다. 실제 NC 명칭을 검증한 뒤 사용하고 이름만 바꾼 호환 wrapper 금지.

## 7. P3 — 시간제 사냥터 및 경기 통합 (MinuteScheduler 제거)

[MinuteScheduler.java L30–49](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/task/calendar/MinuteScheduler.java#L30-L49): 1분마다 _acceptors 전체 순회.
[AbstractDateAcceptor.java L104–125](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/task/calendar/acceptor/AbstractDateAcceptor.java#L104-L125): 요일/개방/종료 조건 및 상태 전환.
[ActionListenerLoader.java L137–234](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/character/action/listener/ActionListenerLoader.java#L137-L234): tb_act_listener_npc_time_teleporter 및 tb_act_listener_npc_time_combat으로 일정 등록.
[AcceptorToNpcListenerAdapter.java L123–198](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/task/calendar/acceptor/AcceptorToNpcListenerAdapter.java#L123-L198): NPC ID별 Config 개방 플래그, 개방 공지, 종료 시 전 접속자 2회 순회 및 두 종류 텔레포트 실행.
[AcceptorToCombatListenerAdapter.java](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/task/calendar/acceptor/AcceptorToCombatListenerAdapter.java): CombatObserver 생성, 실제 경기 잔여시간으로 종료 시각 갱신, quit listener 및 종료 공지.

- NC CFieldCycleSystem/CFieldCycle에 대응하는 사냥터 운영 상태를 하나의 권위 데이터로 둔다. 잊섬/거무/라스타/지배/풍둥/오설/화둥/용계/테베 등 기존 NPC ID와 맵/공지 조건의 실제 DB 정의를 조사한다. 현행 Config 개방 플래그의 모든 reader/writer 이관 후 중복 필드 제거.
- 시간제 NPC는 공통 이벤트/필드 상태와 실제 admission 조건을 조회한다. 입장비·필요 레벨/버프·이용시간·입장 가능 맵·아이템 소모의 기존 계약을 유지한다. 입장 승인과 닫힘의 경쟁을 막고 실패한 텔레포트에 비용만 소모되는 경로를 차단한다.
- 개방/종료 시각만 예약하며 매분 acceptor 전체 순회를 제거한다. 같은 사냥터 복수 입장 NPC/겹치는 시간대에 상태와 공지가 중복되지 않도록 한다. 다중 일정 및 자정을 넘는 기간의 의미 검증.
- 기존 SimpleDateTime.after의 hour/minute/second 개별 비교, Calendar 생성 및 분 단위 초 경계 처리를 검수하고 정상적인 시간 비교로 수정하되 사용자 운영 일정은 보존한다.
- 사냥터 종료 시 입장 먼저 차단 후 기존 CWorld.getVisiblePlayers(mapId/map) 또는 정확한 맵 identity의 occupant API로 해당 맵 사용자만 처리한다. 전접속자 두 번 순회 제거. 기존 텔레포트의 실제 성공/실패, fallback 의도를 확인하고 기존 CIO owner 및 월드 이동 확정 경로로 안전하게 귀환한다. 다른 맵으로 이미 이동한 유저 재강제 귀환 금지. 전서버 공지는 기존 브로드캐스트로 별도 1회 실행.
- 시간제 배틀필드는 실제 경기 observer/instance 소유 상태 및 종료 callback을 유지한다. 일정 manager가 경기 진행을 중복 수행하지 않는다. 실패한 observer 생성 시 잘못 열린 상태/공지 방지, 리로드 중 stale quit callback 방지.
- 리로드에서 기존 acceptor dispose가 강제 종료를 유발하는 현행 경로를 감사한다. 새 정의의 게시 전에 기존 참가자/입장 상태의 이관 정책을 확정한다. 정상 이벤트가 리로드 때문에 닫히거나 참가자가 추방되지 않도록 한다.
- 기존 3 scheduler 및 3 acceptor 계열, 더 이상 사용되지 않는 SimpleDateTime/시간 보조 wrapper는 전체 호출 0 확인 후 삭제한다. 다른 NPC action, dungeon 개인 제한 시간 로직은 유지한다.

## 8. P4 — 나머지 전역 이벤트 이관

### 8.1 보스 스폰
DominanceTimeController의 초/분 polling을 폐기하고 지배의 탑 실제 boss 정의 및 일정 소유자를 기존 CBossNpcMaker/스폰 시스템과 통합한다. CReservationNPCSpawnTimer 책임을 참고하되 이미 존재하는 CBossNpcMaker의 deadline/recovery 동작을 중복 구현하지 않는다. 층별 spawn/공지/이펙트/위치, 요일, 누락 복구, 보스 생존 상태와 중복 스폰 방지 유지. 기존 DominanceFloorLv1~11 등 실제 기능 차이 확인 후 중복 래퍼를 통합하되 전투/스폰 공통 로직을 훼손하지 않는다.

### 8.2 자동 공지 및 서버 매크로
CAnnounceTimer/ServerMacroLoader의 각 설정과 type/대상/문자열/주기를 조사한다. NC CAnnounceTimer 및 CReservationAnnounceTimer의 역할을 참고해 공지 실행과 공통 일정 등록을 분리한다. tb_servermacro의 각 type이 공지 외 실제 명령을 실행한다면 해당 실행 책임을 보존한다. 초기 3분 지연, 설정 간격, 재로드 및 첫 실행 시각 유지. 매초 목록/접속자 조회 및 정렬 재실행을 없애고 실제 due 작업에서만 방송한다. 새로운 매크로 기능을 추가하지 않는다.

### 8.3 선박 및 경기
ShipTimeController의 5초 검사에서 실제 선박 시간대와 상태 전환을 추출한다. CShipController/CShipAction은 NC에서 확인된 역할이다. 출항/도착/귀환/안내/탑승 제한을 단계별로 관리하고 next deadline에 실행한다. 상태 유지에 반복 작업이 꼭 필요하면 기존 owner에서만 수행한다. L2 비행선 이동 좌표/규칙을 그대로 복제하지 않는다.
BugRaceController는 CMonRaceDB/CMonRaceData 및 실제 NC 경주 실행을 추가 대조하고 상태 전환, 5개 주자, 베팅 개시/마감/경주/정산, DB 복구의 수명 및 재시도 보존. 독립 SECOND 등록 제거는 실패 복구 대체 경로 확보 후 수행.
DogFightLoader 및 L1UltimateBattle는 기존 경기 예약, 사용자/보상/instance 종료 및 설정 조건을 보존한다. CEventMatch/CEventMatchMgr의 책임을 실제 대응 범위에서 참고한다. 경기 진행 중 긴 실행과 정산을 manager 타이머로 옮기지 않는다.

### 8.4 랭킹/상점/아지트/정산
L1ClanRanking은 기존 첫 실행 및 정각 갱신, GM 즉시 갱신, CacheD 순서 및 실패 정책을 보존한다. 매분 DB worker에 무효 작업을 제출하지 않도록 실제 갱신 시각만 예약한다.
NpcShopManager는 실제 1시간 재고 보충과 설정·정산·동시 구매 계약 유지. CWorldAgitTimer의 현재 deadline heap/clock repair와 중복되지 않게 등록만 통합. CResidenceSettlement의 게임 시간 기반 정산 날짜와 DB receipt 유지. NC CL2Time의 실제 정산 책임과 대조하며 리니지2의 정산 시각을 임의로 적용하지 않는다.
CCastleSiegeManager 및 기존 공성 상태/시간/DB는 최근 변경을 보존한다. CEventManager에서 lifecycle 등록을 공통화할 수 있는 범위만 적용. CInventoryExpiry 등 물품 만료는 별도 transactional 도메인 책임으로 유지한다.

## 9. P5 — 검증과 최종 삭제 게이트

- 실제 최신 전체 소스에서 Java 21 의존성 타입 컴파일 및 기존 회귀. GameServer 초기화와 종료, 등록 실패/restart/reload, 중복 이벤트/타이머, 예외 격리, owner queue saturation, CacheD 실패/ACK 유실 및 세션/맵 identity 경쟁.
- 시간제 사냥터: 개방/종료 정각, 복수 요일·겹친 기간·자정, 개방 중 재시작, 입장비와 동시 닫힘, 해당 맵만 귀환, 다른 유저/맵 영향 0, 공지 한 번, 복수 observer/경기.
- 보스: 지정 일정, GM 스폰, reload, 생존 상태, 중복 spawn 0. 선박/버그베어/투견/무한대전: 전 상태 전환 및 보상/재화 확정성. 공지/매크로: 이전 메시지/순서/간격/설정 유지. 혈맹 랭킹/아지트/정산/상점: 데이터 유지 및 중복 지급/차감 0.
- 아즈모단: 전용 Java 참조/타이머/입장 로직 0, 자정 혈맹 전체 UPDATE 0, 4703 위조 확인으로 개방/재화 차감 불가, 다른 혈맹/아지트/인스턴스 동작 유지. SQL schema 제거 여부는 전체 참조 확인 및 백업 가능한 migration으로 보고.
- 기존 vs 신규 동일 구성(목표 5,000 PC)에서 활성 이벤트 수, timing wheel 등록 수, 실제 wakeup/초, 전역 polling 및 world lookup/scan, SQL job, owner queue, CPU, JFR allocation/retained heap, p95/p99 지연, 이벤트 실행 오차를 A/B로 기록. 전역 일정 통합 이후 불필요한 heartbeat 및 새 스레드/큐가 생기지 않았는지 검증한다.
- legacy 파일 삭제 전 호출부 0, 설정/GM reload 전환 완료, 게임 정책 동등성 확인. 삭제/변경 파일 목록, NC 직접 확인된 책임과 L1 적응 부분, 테스트 결과 및 미검증 항목을 보고한다.
- 최종 결과는 CEventManager/CEvent 중심의 공통 이벤트 등록·상태·일정·lifecycle, 기존 도메인 소유 실행, 기존 CIO timing wheel 재사용, 불필요한 독립 스케줄러 삭제, 아즈모단 완전 제거다. 현재 보고서만으로 전체 구현/성능 검증이 완료된 것으로 취급하지 않는다.

## 10. 선행 보고서 및 범위

- [군주 오라 개편](https://github.com/choeyeonggeol16-sketch/1234/blob/e045bac3b63043a986871330dda0e975db13c1dc/reports/RKern_NC_Party_Aura_Skill_Pump_Refactor_20261129.md)
- [시간 지정 NPC 대사 개편](https://github.com/choeyeonggeol16-sketch/1234/blob/608042e96e07777bd7f68057e2d6c3d84276b4d1/reports/RKern_NC_NPC_Timed_Chat_Event_Refactor_20261129.md)

이번 보고서는 위 별도 개편을 덮어쓰지 않는다. 실제 운영 설정, 전체 repo 참조, NC 경주/경기/상점/랭킹의 세부 대응 호출, DB 전체 schema 및 실게임 검증은 구현 전후 확인해야 한다. NC 코드에서 확인한 이름을 우선하고 없는 이름을 원본 복원으로 주장하지 않는다. 보고서만 커밋하며 production 코드/DB는 변경하지 않는다.
