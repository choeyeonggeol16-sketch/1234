# RKern 시간 지정 NPC 대사 개편 지시서 — NC OFF NPC Timer/Event 구조

작성일: 2026-11-29 (KST)
RKern 기준: [rkern/gameplay-optimization-20261129](https://github.com/choeyeonggeol16-sketch/1234/tree/rkern/gameplay-optimization-20261129)
NC 기준: [Open_LU4_L2OFF abbb2e5](https://github.com/luannbr/Open_LU4_L2OFF/tree/abbb2e5f474d40cf12539a8e97fddc52474ed95e) (공개 OFF 복원 소스)
이번 커밋은 보고서만 추가한다. 구현 전 최신 게임 브랜치에서 선행 변경을 확인한다.

## 1. 결론

NpcChatTimeController의 매분 전역 대사 정의 순회 및 NPC 재검색을 제거한다. NC처럼 NPC별 타이머/AI 이벤트에서 대사를 실행하도록 한다.

**중요: RKern에는 이미 NPC 대사 전용 TimerData, NPC별 deadline, CNPCServer owner timing wheel이 구현되어 있다. 새 대사 스케줄러나 별도 CIO 타이머를 중복 생성하지 않는다. 기존 NPC owner wheel에 시간 지정 대사의 최초 실행 및 다음 일일 실행 시각만 연결한다.**

- NPC 생성/월드 등록 시 시간 지정 대사가 있는 NPC만 예약한다.
- 해당 시간에 NPC 소유 실행 경로에서 기존 대사 시퀀스를 시작한다.
- 다음 일일 실행 시각을 예약하고 삭제·재스폰·리로드 시 이전 예약을 무효화한다.
- 기존 등장/사망/숨기기 대사, start_delay_time, chat_interval, repeat_interval, 일반/외치기/전서버 대사 및 L1 패킷 동작을 보존한다.
- 전용 스레드, 전역 군집 스캔, 별도 큐 및 중복 timing wheel 추가 금지. 일반 NPC AI/전투/이동/점유/네트워크는 변경하지 않는다.

## 2. NC 실제 소스 근거

### 2.1 시간 조회 및 NPC 타이머

[L2NPC/src/GlobalObject.cpp L1384–1404](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2NPC/src/GlobalObject.cpp#L1384-L1404):
GetL2Time은 게임 내 시/분/밤 상태를 조회하고 GetTimeOfDay는 실제 Unix 초를 반환한다.
[L2NPC/src/NPC.cpp L2460–2464](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2NPC/src/NPC.cpp#L2460-L2464): CNPC::GetTimeHour는 게임 내 시간을 조회한다.
[L2Server/src/L2Time.cpp L230–240](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/L2Time.cpp#L230-L240): 게임 시각을 NPC 서버에 전달하는 경로도 존재한다.

[L2NPC/src/NPC.cpp L1209–1284](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2NPC/src/NPC.cpp#L1209-L1284):
CNPC::AddTimerEx는 ID 및 기간 검증 후 AddTimer로 들어간다. AddTimer는 m_TimerSettingDB에 timer ID 및 UID별 TimerData를 등록하고 CIOObject::AddTimer2를 호출한다.
[L1312–1431](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2NPC/src/NPC.cpp#L1312-L1431): TimerExpired/TimerExpired2는 Serializer.AddExecutable을 통해 AsyncTimerExpired를 실행한다. UID 및 유효성 검사를 거쳐 AsyncTimerExpired1로 진입하고, DoTimerEvent는 CNPCEventFactory::CreateTimerEventEx와 HandleEvent를 사용한다.
[L2NPC/src/NPCEventFactory.cpp L310–365](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2NPC/src/NPCEventFactory.cpp#L310-L365): 타이머 이벤트 및 TIME 이벤트 생성.
[L2NPC/src/NpcEvent.cpp L30–35](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2NPC/src/NpcEvent.cpp#L30-L35): TIMER_FIRED, TIMER_FIRED_EX, CREATED, TIME 이벤트 명칭.

### 2.2 대사 방송

[L2NPC/src/NPC.cpp L7171–7223](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2NPC/src/NPC.cpp#L7171-L7223): Say2는 NPC index와 대사를 내부 패킷으로 보내며 Shout은 외치기 옵션을 사용한다.
[L2Server/src/NpcSocket.cpp L8912–8930](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/NpcSocket.cpp#L8912-L8930): NpcSayPacket은 NPC를 확인하고 Say/Shout으로 위임한다.
[L2Server/src/NPC.cpp L5555–5562](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/NPC.cpp#L5555-L5562): Say는 주변 플레이어에게 방송한다.

NC에서 확인한 것은 NPC 소유 타이머, serializer, AI 이벤트 및 대사 방송 구조다. 특정 실제 시각에 Say2를 호출하는 AI 스크립트 및 TIME 이벤트 상위 배포 경로 전체는 아직 확인하지 못했다. 따라서 아래 일일 시각 계산은 RKern의 기존 기능을 보존하는 L1 적응 정책이며 NC가 똑같은 일일 예약 알고리즘을 쓴다고 단정하지 않는다.

## 3. 최신 RKern 확인 사항

[NpcChatTimeController.java](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/npc/chat/NpcChatTimeController.java), blob 92aea47e6142d8f7364b2b4159c1ab1dc24ed092.
- CGameTime의 SECOND listener 등록, 실제 분별 completedMinute/inFlight 검사, CWorldWork 제출.
- checkNpcChatTime은 Config.TIME_ZONE 기준 실제 HHmm 계산 후 모든 시간 대사 정의를 순회한다.
- 일치하면 getNpcsByTemplateId로 살아 있는 월드 NPC 후보를 찾아 startChat(CHAT_TIMING_GAME_TIME)을 호출한다. 월드 전체 NPC 순회로 단정하지 않는다. 이미 템플릿 ID 기반 조회를 사용한다.
[NpcChatTable.java L20–111](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/npc/chat/NpcChatTable.java#L20-L111), blob 07e5c8c7070fe5464e5192d8df6120e068d7c05f: npcchat DB에서 timing별 primitive map을 구축한다. getTemplateGameTime(npcId)가 이미 존재하며 getAllGameTime은 호출마다 배열을 생성한다. reload는 새 인스턴스를 로딩하고 이전 map을 clear한다.
[L1NpcInstance.java L2368–2450](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/Instance/L1NpcInstance.java#L2368-L2450), blob 11b2f0faea6c35ec0d8385fe80036640203813df: startChat은 정의 조회, 상태 검증, TimerData 등록 후 CNPCServer.register를 수행한다. NPC 소유 _npcChatTimers 정렬 연결 리스트와 npcChatDeadlineMs/tickNpcChat이 존재한다.
[TimerData.java](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/npc/ai/TimerData.java): lifeEpoch, deadlineMs 및 대사 진행 인덱스를 저장하고 TimerExpired에서 한 줄씩 출력한다. 기존 시작 지연/대사 간격/반복 간격, SC_NpcChatPacket 및 전서버 방송을 보존해야 한다. GAME_TIME은 isValid에서 기본 분기에 해당하므로 사망/숨김 처리 의미를 전체 호출과 함께 확인한다.
[CNPCServer.java L980–1008](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/npc/ai/CNPCServer.java#L980-L1008), [L1043–1080](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/npc/ai/CNPCServer.java#L1043-L1080): 기존 NPC wheel에서 npcChatDeadlineMs를 확인해 tickNpcChat을 실행하며, 다음 NPC wakeup 계산에 chatDue를 포함한다.
[GameServer.java L263,334,589](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/GameServer.java): 대사 로더 초기화 및 전역 controller start/stop 참조.

## 4. 구현 지시

### P1. 기존 호출 및 NC 명칭
```sh
rg -n 'NpcChatTimeController|NpcChatTable|L1NpcChat|CHAT_TIMING_GAME_TIME|startChat|npcChatDeadlineMs|tickNpcChat|TimerData|clearNpcChats|pruneNpcChats|npcEventEpoch' rkern
```
NPC 실제 생성/스폰 완료/월드 등록/숨기기/사망/부활/삭제/재사용/리로드, GM 리로드, timer admission 및 CNPCServer 등록/해제 경로를 먼저 조사한다.
- 기존 model.npc.chat 패키지 및 model.npc.ai.TimerData/NpcEventFactory를 활용한다. 기존 패키지의 책임과 클래스명을 실제 NC의 TimerData, CNPCEventFactory, TimerExpired, AddTimerEx, Say/Say2/Shout 등에 대응시키되 공용 클래스의 무관한 호출까지 이름 변경하지 않는다.
- NPC 대사 전용 DB 클래스의 NC 동일 이름을 확인하지 못했다. 임의로 CxxxDB라는 이름을 만들어 원본이라고 주장하지 않는다. 원본에 존재하는 이름을 추가 확인하고 동일 책임에만 적용한다.
- 기존 CNPCServer의 NPC별 다음 실행 deadline과 TimerData를 활용한다. 새로운 독립 스케줄러, 별도 NPC마다 CServerTimer, 새로운 worker 또는 message queue를 만들지 않는다.

### P2. 시간 지정 대사의 NPC별 등록
- 월드 등록이 확정된 NPC에서 getTemplateGameTime(templateId)로 1회 조건 조회. 정의가 없으면 아무 타이머/추가 객체도 생성하지 않는다.
- 기존 Config.TIME_ZONE과 HHmm를 기준으로 다음 실제 실행 시각을 계산한다. 잘못된 HHmm/시간대 및 빈 대사 정의 처리. 현재 분에 스폰/재스폰/리로드되었을 때의 기존 분별 실행 정책과 중복 여부를 조사하여 유지한다.
- 기존 NPC owner에서 다음 시간 지정 deadline이 현재 npcChatDeadlineMs 및 CNPCServer 다음 wakeup 계산에 반영되도록 한다. 기존 TimerData 연결 리스트에 중복 시퀀스를 쌓지 않는다.
- 동일 NPC 생명주기의 동일 날짜/분 이벤트 중복 등록을 방지한다. 지정 시각에 기존 startChat의 검증과 대사 시퀀스 실행을 같은 owner에서 연결하고 다음 일일 실행을 계산한다.
- 일일 스케줄과 대사 시작 지연/문장 간격/반복 간격은 별개다. 기존 is_repeat가 무한 반복이면 다음날 신규 시퀀스와 충돌하는지 조사하고 의도된 반복 정책을 보존한다.
- 예약된 NPC가 삭제/재스폰/숨김/사망 상태로 바뀌면 기존 lifeEpoch/smartId 및 실제 활성 조건을 이용해 stale 이벤트를 차단한다. 부활/재등장 시 누락 없이 다시 예약한다. 대사 없는 일반 NPC의 AI 깨움 횟수/메모리 증가 금지.
- 실제 시각 기반 기능이므로 서버 시각 보정, 자정, DST 및 운영 지연 후 실제 실행 분 처리를 검토한다. 다음 일일 시각은 매번 달력 규칙으로 계산하고 24시간 상수 더하기만 하지 않는다. 과거 분의 대사를 임의로 한꺼번에 방송하는 정책 금지. 필요한 clock-change 신호는 기존 시간 서비스의 실제 지원을 확인하여 활용한다.

### P3. 리로드 및 방송
- npcchat 정의의 안전한 교체를 구현한다. DB 로딩 실패 시 기존 유효 정의와 예약을 유지. 공유 정의를 가변 객체로 중복 저장하거나 이전 정의를 clear해서 실행 중인 owner가 잘못 읽게 하지 않는다.
- 리로드 시 변경된 템플릿 ID의 기존 월드 인덱스를 사용하여 시간 예약을 추가/변경/취소한다. 리로드 때의 필요한 대상 조회와 평상시 전역 반복 조회를 구분한다. 기존 NPC 생명주기 및 정의 generation을 검증한다.
- 기존 등장/죽음/숨기기 대사와 GAME_TIME의 실제 상태 정책 유지. start_delay_time, chat_id1~5, chat_interval, is_shout, is_world_chat, is_repeat, repeat_interval, game_time 및 기존 DB schema 보존.
- TimerData.TimerExpired 및 SC_NpcChatPacket 방송 경로를 그대로 활용한다. NC처럼 NPC의 Say/Shout 책임을 정리할 수 있으나 기존 L1 패킷과 전서버 방송 의미는 바꾸지 않는다. 방송 네트워크 코어 변경 금지.
- 타이머 예약/재예약 실패를 기존 NPC owner의 안전한 admission/retry 경로로 처리한다. 무한 즉시 재시도나 새 전역 스캔으로 대체하지 않는다.

### P4. 기존 controller 제거
- GameServer의 NpcChatTimeController import/start/stop, SECOND listener, completedMinute/inFlight 및 전용 CWorldWork 작업 제거.
- getAllGameTime이 다른 곳에서 사용되지 않으면 삭제하여 매분 배열 할당 제거.
- 모든 호출 이관 후 NpcChatTimeController 클래스 및 불필요한 호환 코드 삭제. CGameTime 및 다른 시간 listener는 유지한다.
- 기존 NPC wheel/대사 시퀀스의 일반 기능을 변경하지 않으며 전용 시간 대사 처리에만 수정 범위를 제한한다.

## 5. 검증 및 완료 기준

- Java 21 전체 의존성 타입 컴파일 및 기존 NPC/대사/스폰 회귀.
- 시간 지정 없는 NPC, 동일 템플릿 다수 NPC, 정상 HHmm, 자정, 동일 분 생성/삭제/재스폰, 서버 재시작, 실제 시각 변경, 시간대/DST 경계, 긴 지연 후 복귀.
- 등장/사망/숨김/부활 및 무한 반복/다중 문장/빈 문장/일반·외치기·전서버 방송이 기존 규칙을 보존하는지.
- stale lifeEpoch/스마트 ID, 맵 이동, 삭제, 리로드 경쟁, timer admission 실패, owner 실행 지연, shutdown에서 중복/유실/잔존 예약 확인.
- 리로드 SQL 오류, 신규/삭제/변경된 대사 정의, 기존 예약 및 객체 수명 검증. 같은 날짜/분 중복 방송 없는지.
- 기존/신규 A/B: 시간 지정 대사 없는 상태 및 실제 다수 NPC 구성에서 minute scan 횟수, template lookup, world NPC lookup, 배열 할당률, 활성 예약 수, NPC wakeup/CPU/p99 및 retained heap 측정. 모든 NPC에 일일 타이머가 생성되지 않는지 확인한다.
- 완료 기준: 전역 NpcChatTimeController 제거, 평상시 매분 전체 대사 정의 순회/배열 생성/월드 NPC 재검색 제거, 기존 NPC owner wheel 재사용, 모든 대사 기능 및 삭제/리로드 안전성 보존, 전체 호출 이관. 미실측 성능 수치를 결과로 주장하지 않는다.

## 6. 확인 범위

NC의 NPC 타이머·이벤트·대사 방송과 최신 RKern의 기존 NPC 대사 timer/owner wheel, 전역 시간 controller를 직접 확인했다. 특정 NC 시간 대사 AI 스크립트, TIME 이벤트 배포 전체, 실제 npcchat DB 행 및 전체 호출 그래프는 미확인이다. 구현/인게임/성능 검증은 아직 진행하지 않았다. 이번 커밋은 보고서만 추가한다.
