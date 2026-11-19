# RKern NPC Action 개발 계획 — NC HTML Command / NPC Event / AtomicJob 적용

작성일: 2026-11-19 (KST)  
상태: 설계 및 개발 계획 확정용 초안. 생산 소스 수정 없음.  
기준: RKern_ItemUse_Final_MarbleCache_Phase241_243_FULL_SRC_20261119.zip  
기준 ZIP SHA-256: aa62c65dbee7903339605cbfbfe15b5cd6891699caed67ea7a7679905c60c9e0  
기준 ZIP CRC 검사: 정상.  
선행 분석: [NC NPC Action / HTML Command / AtomicJob 역분석](NC_NPC_Action_HtmlCommand_AtomicJob_RKern_20261119.md)

## 1. 목표와 범위

목표는 CS_NpcAction을 단순히 여러 클래스에 흩어 놓는 것이 아니라 NC의 역할 분리와 실행 순서를 RKern에 맞게 복원하는 것이다.

- 클라이언트 패킷 진입, 명령 분류, 대화 상태, NPC별 실행, 경제 트랜잭션, 응답을 분리한다.
- 기존 정상 동작(명령 문자열, NPC ID, 게임 조건, 보상, 확률, HTML, 패킷 순서)을 우선 보존한다.
- 실제 실패·권한·객체 수명 문제는 별도 차수로 수정하여 기존 동작 차이를 추적한다.
- 기존 Java 21/CIO, NpcServer, 인벤토리 트랜잭션, CacheD receipt를 재사용한다.
- NPC 실행 책임과 AI 이동·전투 실행 책임을 구분한다. NpcServer가 있다는 이유만으로 모든 NPC 명령을 NPC AI 워커로 재게시하지 않는다.
- 네트워크 코어, 방송, 일반 전투, NPC 이동 AI, CacheD 내부 구조 및 기존 아이템 리팩토링은 변경 범위 밖이다. 공통 경계의 실제 결함은 근거를 제시하고 별도 승인 후 수정한다.

## 2. 실제 RKern 기준선

이번 계획 작성 시 243차 ZIP의 생산 Java를 직접 확인했다. 아래 줄 번호는 해당 기준 ZIP 기준이며 이후 수정되면 달라진다.

| 파일/메서드 | 확인된 위치 | 현재 책임 |
|---|---|---|
| CPacketHandler.handleNpcTalk | 3548–3555 | 연결 검사 후 CS_NpcTalk.handle |
| CPacketHandler.handleNpcAction | 3682–3694 | 연결 검사 후 CS_NpcAction.handle(client, p0, s0, s1, (byte[]) o0) |
| CS_NpcAction.java | 7,535줄 | NPC 명령, 특수 UI, 제작, 교환·퀘스트 등 |
| CS_NpcTalk.java | 488줄 | NPC 첫 대화, 특수 NPC 분기, XML 대화, 기본 대화 |
| CS_NpcAcion2.java | 2,935줄 | 기존 보조 NPC 명령. 파일명 오탈자 포함 |
| ActionControlHandler.java | 267줄 | 사전 등록된 UI·서포터용 GameAction 맵 |
| NpcActionTable.java | 118줄 | XML 명령·대화 목록 로딩, 순차 acceptsRequest |
| L1NpcAction.java | 33줄 | acceptsRequest/execute/executeWithAmount 계약 |
| L1NpcActionFactory.java | 63줄 | XML 노드별 액션 생성자 등록 |
| L1NpcMakeItemAction.java | 198줄 | XML 기반 제작 |
| L1NpcListedAction.java | 68줄 | XML 구성 액션 |
| L1NpcTeleportAction.java | 84줄 | XML 기반 이동 |
| ActionListenerLoader.java | 315줄 | NPC ID와 명령별 리스너 조회 |
| NpcServer.java | 1,063줄 | NPC owner, AI·타이머·행동 이벤트 |
| L1InventoryTransaction.java | 869줄 | debit/grant/sql/receipt/owner 완료 처리 |
| SC_NPCTalkReturn.java | 95줄 | HTML 응답 전송 |

CS_NpcAction과 CS_NpcAcion2를 합치면 10,470줄이다. 단지 메인 클래스를 줄이고 보조 클래스에 다시 집중시키는 것으로는 완료가 아니다.

주요 파일은 src/l1j/server/server/ 하위의 model/network/core/cs/packet, model/npc/action, datatables, model/Instance, model/npc/ai에 있다. ActionListenerLoader는 src/l1j/server/MJActionListener에 있다.

### 2.1 현재 CS_NpcAction.handle 순서 (반드시 기준선으로 보존)

| 순서 | 위치 | 동작 |
|---|---|---|
| 1 | 876–887 | client, IN_GAME, active char, command null 검사 |
| 2 | 889–959 | 무시 명령 및 Neige 변신/카드/GM UI 특수 분기 |
| 3 | 961–968 | 폐기된 자동 명령 차단, ActionControlHandler |
| 4 | 981–1042 | 상태 및 월드 대상 조회, NPC 동일 맵·6타일 거리, 특수 초기화, onFinalAction, PC 대상 UI 분기 |
| 5 | 1044–1056 | MJINNHelperLoader.onAction, NpcActionTable.get/execute |
| 6 | 1058 이후 | 세금 UI, buy/sell 및 기존 직접 명령/NPC ID별 분기 |
| 7 | 5713–5734 | ActionListenerLoader.findListener 또는 CS_NpcAcion2.NpcAction |
| 8 | 5735–5803 | 후속 HTML, 공통 제작·보상, 성공/실패 화면 |
| 9 | 5804–5805 | 빈 catch(Exception) — 별도 개선 대상 |

일부 분기는 여러 명령에 부수 효과를 발생시킨 뒤 계속 실행한다. 단순히 모두 첫 매칭 return으로 바꾸면 안 된다. onFinalAction은 void이므로 호출 성공을 근거로 나머지 실행을 생략하지 않는다. actionData의 null과 EMPTY_BYTES 처리, s2의 실제 사용 여부, 대소문자·contains·startsWith·정규식의 차이를 분기 단위로 추적한다.

### 2.2 기존 CS_NpcTalk.handle 흐름

192행 진입 → client/active char/ghost 검사 → 월드 대상 → NPC 효과 → MJINNHelperLoader.onTalk → 특수 NPC 처리 → 경매장 분기 → XML 대화 → 특수 도박 NPC → obj.onTalkAction(pc).

특수 NPC 처리(233행)는 이후 처리로 이어질 수 있다. 최종 return 여부까지 기록한다. onTalkAction/onFinalAction 구현은 L1MerchantInstance, L1TeleporterInstance, L1DwarfInstance, L1QuestInstance, L1PetInstance, L1SummonInstance, L1HousekeeperInstance 등 기존 인스턴스에 존재한다. 복제된 이벤트 프레임워크 대신 이 실행부를 우선 활용한다.

## 3. NC와 RKern의 1:1 역할 대응

NC 주소는 선행 보고서의 해당 빌드 기준이다. '적용 후보'는 새 구현명 또는 기존 구조 활용 방향이지 NC 원본과 완전히 동일하다는 뜻이 아니다.

| NC 역할/함수 | NC VA | 현재 RKern 대응 | 적용 계획 |
|---|---|---|---|
| CUserSocket::HtmlCMDPacket | 0xA1C58C | CPacketHandler.handleNpcAction → CS_NpcAction.handle | 기존 패킷 ABI 유지, User 쪽 명령 진입 분리 |
| LoadHtmlCmdHandler | 0xA1CB64 | ActionControlHandler.init / NpcActionTable.get | 기존 등록·실행 체계 확장, 일반/특수/대상 명령 구분 |
| User::ChangeTalkTarget | 0x9BA410 | CS_NpcTalk, 기존 PC 대화 상태 조사 필요 | 기존 플레이어 대화 세션에 결합 |
| CCreature::ChangeTalkTarget | 0x568638 | 인스턴스 대화/타겟 처리 | 전투 타겟과 대화 타겟 혼용 금지 |
| CHTMLCache::IsValidLink | 0x6D9CE8 | XML action acceptsRequest, HTML 응답 경로 | 서버 발급 명령·NPC·조건을 근거로 검증 |
| CHTMLCacheManager::IsValidLink | 0x6DA258 | NpcActionTable / 기존 HTML 데이터 | 별도 대규모 캐시 중복 금지 |
| TalkChannelCache::SubChannel::IsValidChannel | 0x971744 | 동일 맵·거리 검사 및 기존 세션 상태 | 명령군별 대화 채널 검사 |
| TALKED / 0x06 | 선행 분석 | CS_NpcTalk → onTalkAction / XML | 기존 최초 대화 이벤트 |
| TALK_SELECTED / 0x1A | 선행 분석 | CS_NpcAction / XML action | L1 프로토콜에 맞춰 명령 분류 |
| MENU_SELECTED / 0x0C | 선행 분석 | NPC ID 분기, onFinalAction, ActionListenerLoader | 기존 이벤트 핸들러 연결 |
| buy / sell | 0xA16180 / 0xA16578 | CS_NpcAction, L1Shop | 기존 상점 처리 사용 |
| multisell | 0xA15D30 | 제작·교환 및 XML MakeItem | 기존 경제 트랜잭션 재사용 |
| teleport_ | 0xA1CCEC | L1NpcTeleportAction / L1TeleporterInstance | 무료/유료 이동 분리 |
| recipeNPC | 0xA1E7E0 | 공통 제작부 / L1NpcMakeItemAction | 제작 조건과 결과 처리 |
| quest_choice/start/accept | 0xA1F498 / 0xA224C8 / 0xA1EB80 | questItemExchange, XML SetQuest, 퀘스트 NPC | 진행도와 보상을 동일 영속 경계에 |
| AtomicJobCompositor::AddItemManip | L2NPC 0x501684 | L1InventoryTransaction.debit/grant | 별도 트랜잭션 프레임워크 불필요 |
| AtomicJobCompositor::Process | L2NPC 0x500190 | L1InventoryTransaction.commit | 기존 CacheD 요청 사용 |
| NpcAtomicDelGiveItemParam | 0x7D3894 | 기존 트랜잭션 계획/검증 | L2 내부 opcode 복제 불필요 |
| User::HandleItemManipulation | 0x9A2508 | 인벤토리 트랜잭션 owner apply | 기존 receipt 재사용 |
| CDB::CreateUpdateItems | 0x5E1AD4 | CacheD | 기존 DB 확정·실패 처리 |
| CNPC::ShowPage | L2NPC 0x487A04 | SC_NPCTalkReturn | 패킷 형식과 페이지 순서 유지 |

NC는 프로세스 분리와 내부 접속을 사용한다. RKern은 Java 내부 실행 구조로 역할을 구현한다. L1에는 TALK_SELECTED와 MENU_SELECTED가 NC의 외부 패킷과 일대일 대응한다는 근거가 아직 없으므로 기존 명령 의미에 맞추며 임의의 신규 클라이언트 opcode를 추가하지 않는다.

## 4. 목표 실행 구조

~~~mermaid
flowchart TD
 A["CPacketHandler / 기존 CIO owner"] --> B["CS_NpcTalk / CS_NpcAction"]
 B --> C["User - NPC 상호작용 진입 (후보)"]
 C --> D["명령 분류 및 기존 선행 정책"]
 D --> E["자기 UI / 특수 명령"]
 D --> F["NPC 대상 / 대화 채널 검증"]
 F --> G["기존 onTalkAction / onFinalAction"]
 G --> H["XML NpcActionTable / 기존 리스너 / 기능별 핸들러"]
 H --> I["일반 실행"]
 H --> J["경제 작업 - L1InventoryTransaction"]
 J --> K["CacheD receipt"]
 K --> L["CIO owner 완료 / 인벤토리 반영"]
 E --> M["응답"]
 I --> M
 L --> M
~~~

위 그림은 역할 구조이며 실제 선행/후속 분기 순서를 임의로 변경하는 설계가 아니다. 분기 우선순위는 2.1–2.2의 기존 동작을 기준으로 확정한다.

### 4.1 이름 및 파일 정책

- CS_NpcAction: 기존 static handle 서명 유지, 패킷 진입 책임만 남기는 목표.
- CS_NpcTalk: 기존 static handle 유지하고 최초 대화 실행을 공통 정책에 연결.
- User: 현재 아이템 진입부 및 경제 처리 helper가 이미 존재한다. 기존 User에 NPC 진입부를 추가할지, 같은 책임의 별도 NPC 진입 클래스가 필요한지는 1단계 호출 목록 확인 후 결정. User 자체를 새 거대 클래스로 만들지 않는다.
- HtmlCmdHandler / TalkChannelCache: NC 이름을 후보로 한다. 기존 ActionControlHandler와 대화 세션 조사 후 중복 역할이 없을 때만 신규 클래스를 추가한다.
- NpcActionTable, L1NpcActionFactory, L1NpcAction, ActionListenerLoader: 기존 XML 및 스크립트성 액션 실행 체계를 유지하고 기능별로 확장한다.
- 기존 L1NpcInstance 하위 타입의 대화 처리 메서드를 우선 사용한다. 데이터만 다른 NPC마다 새 클래스를 만들지 않는다.
- CS_NpcAcion2: 기존 호출이 모두 이동·검증되면 제거 가능. 이름 변경만으로 완료 처리하지 않는다.

### 4.2 디스패치 방식

NC의 65-entry 명령 테이블을 참고하되 동일한 개수나 저장 형식을 강제하지 않는다. Java에서는 기존 exact-match 맵, NPC ID 기반 정적 분기, 기존 XML 액션을 비교해 가장 작은 변경을 선택한다.

- ActionControlHandler.handleAction은 현재 _actionMap.get(actionStr)로 정확히 일치시키고, 매칭 후 클릭 사운드를 발생시킨 다음 GameAction.run을 실행한다.
- NpcActionTable은 사용자 XML 디렉터리를 먼저 읽고 이후 기본 디렉터리를 읽으며, acceptsRequest의 첫 성공 항목을 반환한다. 현행 우선순위 및 동적 조건을 보존한다. 파일 순서를 임의로 정렬하거나 공통 Map에 넣어 덮어쓰지 않는다.
- L1NpcActionFactory는 Action, MakeItem, ShowHtml, SetQuest, Teleport를 로드 시 생성한다. 패킷마다 reflection을 사용한다는 근거가 없으므로 이 부분의 불필요한 재작성은 하지 않는다.
- 정확 일치, case-insensitive, prefix, contains, 숫자 문법, NPC별 동일 명령, fall-through를 명령 목록에서 따로 분류한다.
- 명령별 상수 테이블은 초기화 시 한 번 구성. 불필요한 문자열 소문자 복사, split/regex, per-request Context, boxed key, 임시 collection, 캡처 객체를 줄인다. 비교 의미를 유지하지 못하면 최적화를 채택하지 않는다.

### 4.3 대화 세션과 실행 소유권

- 현재 동일 맵/6타일 제한은 CS_NpcAction 994–996행에 존재한다. NpcActionRange.get()이라는 기존 범위 헬퍼도 존재하므로 사용 범위를 조사한다. 일괄 거리 변경은 금지한다.
- 대화 대상 object ID, 접속 세대, 맵/거리, 현재 세션 타입, 발급 페이지/명령 관련 상태를 조사하고 기존 저장 공간을 재사용한다.
- 일반 NPC 명령과 아이템/PC 대상 UI, 경매장, 상점, 시스템 메뉴를 분류한다. 자기 UI에 NPC 거리 검사를 강제하지 않는다.
- PC 대상 분기(1026–1038)는 summonMonster/변신을 target에 실행한다. requester와 target의 권한/소유 관계 검증을 우선 검수한다. 의도된 GM 동작과 자기 UI는 유지한다.
- onFinalAction 및 onTalkAction이 NPC 내부 상태까지 변경하는지 하위 타입별로 조사한다. NpcServer의 owner API를 활용할 수 있지만 모든 명령을 NpcServer 큐에 넣는 설계는 금지한다.
- PC 인벤토리·퀘스트·텔레포트는 기존 PC 소유 실행 문맥에서 처리. NPC 고유 상태 변경은 기존 NPC owner 계약을 조사한다. 두 소유권 경계 간 동기 대기(서로 join/get/lock 대기)나 새 워커·큐를 추가하지 않는다.
- 대화 종료, 이동/맵 변경, NPC 삭제·재사용, 로그아웃과 재접속 시 참조 해제/세대 검증. 실제 대화 수명 정책 확정 전에는 정상 대화 중 임의로 만료시키지 않는다.

## 5. 경제 트랜잭션: 실제 수정 우선순위

| 우선순위 | 현재 소스 근거 | 계획 |
|---|---|---|
| P0 | CS_NpcAction 5738–5799: 재료 배열 검사 이후 consumeItem/storeItem 분리 | 공통 제작 경계 원자화 |
| P0 | L1NpcMakeItemAction 142/146행: XML 재료 소모·제작 지급 | XML 제작에도 동일 경제 경계 적용 |
| P0 | CS_NpcAcion2 82/84, 638/639 등 소모·지급; 1223–1234 다중 재료 | 개별 교환/제작 경로 분류 및 단계적 전환 |
| P0 | CS_NpcAction 905/925행: 변신 주문서 소모 결과 미확인 | 확정 후 효과 적용 검토 |
| P0 | CS_NpcAction 5688–5691: 입장권 소모 및 보스 레이드 시작 | 전역 입장 상태/실패/동시 신청 계약 검수 |
| P1 | L1NpcTeleportAction 76행: 유료 이동 소모 | 소모 성공·이동 가능 상태 및 실패 정책 검수 |
| P1 | XML SetQuest/ListedAction 조합 | 복합 실행 순서, 진행도와 보상 원자성 검수 |
| P1 | catch(Exception) 빈 블록 | 기존 CPacketHandler.reportHandlerError 활용 여부 검토, 오류 은폐 제거 |

위 표는 실제 코드 관측과 위험 후보이다. 모든 개별 경로에 오류가 재현되었다는 의미는 아니다. 외부 호출·트랜잭션·락까지 확인한 뒤 변경 범위를 확정한다.

### 5.1 기존에 원자화된 코드의 보존

CS_NpcAction의 questItemExchange(168행~), lukeinChestReceipt(249행~), dragonEyeExchange(362행~), dragonEyeUpgrade(392행~), npcSingleMaterialExchange(429행~), npcPaidFloorTeleport(458행~), questGrantOnce(478행~)와 기타 receipt 경로가 이미 존재한다. 이 함수를 다시 raw consume/store 방식으로 되돌리지 않는다. 구조 분리 단계에서는 기존 코드를 그대로 이동한 뒤 각 완료 계약을 별도로 검증한다.

### 5.2 L1InventoryTransaction 사용 계약

실제 243차 코드에서 확인한 API:
- begin(pc, resourceKey) 112행
- debit 181행~, grant 241행~
- sql(CTransaction.Mutation) 594행, requireTables 596행
- onFailure 599행
- onDurableCommit 604행: DB 확정 뒤 세션 replay 이전, thread-safe/nonblocking 캐시 게시 전용
- commit 610행: 인벤토리 반영 후 완료, gate 해제 순서 주의
- commitAfterInventoryRelease 618행: owner apply/receipt barrier 및 participant gate 해제 뒤 효과 처리가 필요한 경우
- commitInternal 622행~730행: preimage 검사, 이미지 분리, DB 요청, owner apply
- post 795행~813행: CIOThreadCallback.completeWarehouseMove 및 session stamp 확인
- sqlFailed 823행~845행: rollback 확인과 unknown/committed replay 오류 구분, quarantine
- close 850행~: 미제출 작업 정리(실제 구현 확인 후 사용)

신규 AtomicJobCompositor라는 이름의 트랜잭션 구현을 추가하지 않는다. 기존 L1InventoryTransaction이 NC의 조작 집합 역할을 담당한다.

### 5.3 제작·교환 처리 절차

1. 현재 명령, 대상, 기존 게임 조건 확인.
2. 재료·보상 템플릿, 수량, 강화/속성, 착용 여부, 재료중복 및 산술 오버플로 확인.
3. 기존 자원키와 gate를 재사용해 한 receipt에 debit/grant 및 필요 시 기존 영속 퀘스트 변경을 stage.
4. 최초 단계는 기존 재료 선택, RNG 호출 횟수와 결과, 기존 180칸/무게 제한 및 오류 메시지 의미를 보존한다. 용량 정책 차이는 별도 승인 및 테스트.
5. commit 호출 시 성공으로 간주하지 않는다. DB 확정 및 owner replay 완료 후 성공 HTML/패킷/부수 효과 실행.
6. 효과가 인벤토리 gate를 다시 필요로 하면 commitAfterInventoryRelease의 실제 계약을 확인하고 선택한다. onDurableCommit에서 PC 상태/패킷을 직접 변경하지 않는다.
7. 실패 시 소모와 지급이 함께 확정되지 않도록 기존 rollback/unknown 처리 준수. 이미 커밋된 요청을 ACK 유실만으로 다시 실행하지 않는다.
8. 세션이 교체된 경우 DB 결과와 UI 전송을 구분한다. 정상적으로 확정된 receipt는 복구되어야 하며 오래된 세션에 UI를 보내지 않는다.

중요: 기존 L1NpcAction.execute는 L1NpcHtml을 동기 반환한다. 비동기 트랜잭션 도입 시 null 반환의 의미, L1NpcListedAction의 후속 행동, executeWithAmount 호출자를 모두 분석한다. 성공 HTML을 즉시 반환한 채 비동기로 지급하는 방식은 금지한다. 새 공용 API/반환 상태는 실제 호출 계약이 필요로 할 때만 최소 추가한다.

텔레포트·레이드 입장·스킬 사용 등 외부 게임 상태는 SQL과 같은 트랜잭션이라고 단정할 수 없다. 기존 입장 예약/이동 가능 상태를 먼저 조사하고 소모 확정 후 실패 정책을 명시한다. 단순히 모든 코드를 commit 콜백으로 이동시키지 않는다.

## 6. 단계별 개발 계획

각 차수는 앞선 최신 전체 SRC를 기준으로 한 묶음의 개발·검증 단위다. 차수 숫자는 실제 수정 범위에 맞게 조정하며 고정된 완료 일수/차수로 간주하지 않는다.

| 단계 | 작업 묶음 | 수정 후보 | 통과 조건 |
|---|---|---|---|
| N0 기준선 | 모든 명령과 호출 그래프, XML/리스너/인스턴스 분류 | 생산 소스 수정 없음 | 10,470줄 양쪽 메인/보조 경로와 실제 호출자 목록 |
| N1 패킷 책임 | CS_NpcAction/CS_NpcTalk 진입과 실행 책임 분리 | CS_NpcAction, CS_NpcTalk, User 또는 적절한 기존 실행부 | 정상·실패 패킷/return/부수 효과 순서 등가성 |
| N2 HTML 명령 | UI/특수 명령 및 대상 기반 디스패치 정리 | ActionControlHandler, NpcActionTable 및 실제 필요한 핸들러 | exact/prefix/contains/대소문자/충돌/우선순위 등가 |
| N3 대화 정책 | 대상/세션/권한/거리 및 채널 검증 | 기존 PC 대화 상태 및 NPC 대화 진입부 | 자기 UI 정상, 다른 PC/타 NPC 악용 차단, 정상 대화 유지 |
| N4 NPC 이벤트 | Talked/선택/메뉴 기능별 책임 정리 | 기존 NPC 인스턴스, XML, ActionListener | 이벤트 한 번 실행, fall-through·기존 owner 유지 |
| N5 경제 P0 | 공통 제작 및 XML MakeItem 영속 처리 | 제작 실행부, L1NpcMakeItemAction, 필요한 호출자 | 소비/지급/실패/중복 요청의 원자성 및 UI 완료 |
| N6 경제 확장 | CS_NpcAcion2, 직접 제작, 퀘스트, 변신 소비, 유료 입장 | 실제 각 도메인 실행부 | 각 정상 결과와 rollback/ACK/reconnect 검증 |
| N7 나머지 기능 | 상점, 이동, 공성, 버프, 펫, 이벤트, 기타 | 기존 도메인으로 이동·재사용 | 기존 동작·권한·실행 순서 보존 |
| N8 최적화 | 핫패스 중복 조회, 임시 배열/문자열, 참조 수명 | 실제 측정 대상만 | 할당량/retained heap/CPU 비교, 회귀 없음 |
| N9 최종 검수 | 전체 명령/호출자/컴파일/DB/인게임 검증 | 발견된 결함만 수정 | 소스·통합·실운영 검증 상태 명시 |

N0에서 특히 buy/sell 및 특수 상점 NPC, 성문/세금/공성, 제작/교환/용해, 퀘스트, UI/카드/변신, 레이드/입장권, 펫/서먼, 텔레포트, 버프, 관리자 기능, 기타 custom NPC를 구분한다.

N5/N6의 명확한 경제 결함은 N0에서 호출 계약이 확인되면 선행 수정할 수 있다. 다만 구조 이동과 게임 정책 변경을 한 diff에 섞지 않도록 분리한다.

### N0 산출물 형식 (구현 시작 전)

각 등록 명령 또는 동적 명령 패턴별로 아래 열을 채운 명령대장을 작성한다.

    route_id | source path:line | NPC/template/object type
    command matcher | priority | predecessor side effects
    admission checks | handler | owner | inventory/SQL/quest mutations
    success/failure HTML and messages | return/fall-through
    existing test | target handler | equivalence/intentional-fix classification

기존 명령대장 없이 전체 switch/if를 자동으로 변환하지 않는다. 운영 XML/DB/HTML이 필요하지만 ZIP에 없으면 해당 분기를 미확정으로 표시하고 기존 상태 유지.

## 7. 검증 계획과 종료 조건

### 7.1 단계마다

- 기존 기준 ZIP CRC/SHA 및 production Java 경로를 확인한다.
- 구조 변경은 동일 입력·동일 스텁/난수 seed를 이용해 호출 순서, 실패 결과, 아이템 수량, 메시지, HTML을 대조한다.
- 의도적 버그 수정은 원본에서 재현 실패 → 수정본에서 통과하는 독립 테스트를 작성한다.
- 기존 아이템 1~243차 관련 회귀 스크립트 및 해당 경계 테스트를 재실행한다. 잘못된 단순 소스 문자열 비교는 근거 없이 성공 처리하지 않는다.
- Java 21 소스 구문 검사와 타입 컴파일 결과를 구분한다. 의존 라이브러리가 없어 전체 컴파일에 실패하면 통합 검증 완료로 표기하지 않는다.
- 생산 파일 변경 범위, 기존 미변경 항목 byte identity, CRC 검증. 전달은 최신 전체 SRC ZIP 하나.

### 7.2 경계 조건

| 분야 | 테스트 |
|---|---|
| 명령 | null/empty/긴 명령, 대소문자, 숫자·prefix 충돌, XML first-match, NPC ID별 같은 명령 |
| 대화 | 정상 6타일 경계, 다른 맵, 대상 삭제/재사용, 다른 PC object ID, 자기 UI, 로그아웃 및 재접속 |
| 이벤트 | onFinalAction 이후 기존 분기, XML/리스너 중복, fall-through, 클릭 사운드 및 HTML 순서 |
| 경제 | 부족 재료, 같은 재료 여러 행, 착용 아이템, 강화 선택, 아이템 수량/무게 오버플로, 용량, RNG |
| CacheD | commit/rollback, ACK 유실, unknown outcome, replay, owner apply 실패, stale session, 재시작 |
| 동시성 | 동일 PC 연속 요청, 서로 다른 PC가 동일 NPC 사용, 로그아웃 중 작업, NPC 제거/재생성, 기존 owner 교착 방지 |
| 메모리 | 반복 대화/제작/세션 종료 후 객체 참조 해제, static/ThreadLocal/비동기 callback retained reference |

### 7.3 성능 기준

절대적인 처리량 개선 숫자를 사전에 만들지 않는다. 같은 JVM, 같은 데이터, 같은 명령 분포, 같은 DB 지연, 같은 소유권 실행 조건에서 원본/수정본을 비교한다.

- 일반 HTML/UI 처리의 할당 bytes/op, CPU time/op, p50/p95/p99, lock 경합.
- 제작은 논리 요청/초, receipt/SQL 요청 수, commit 및 owner apply 지연을 분리해서 측정.
- JFR allocation/monitor, GC, 세션 제거 이후 heap dominator/retained size.
- 정상 대화, 상점 목록, 제작 반복, 다수 PC 동시 대화 및 5,000명 목표에 맞춘 부하 모델. 5,000명 수용을 보장한다는 표현은 실제 전체 시스템 검증 전에는 사용하지 않는다.
- 정상 명령 경로에 새 DB 접근이나 새 워커/큐를 넣지 않는다. 더 많은 메모리로 성능을 얻는 선택은 실측 이후 결정한다.

## 8. NC 추가 분석과 개발의 연결

| 확인 과제 | 영향 받는 단계 | 판단 |
|---|---|---|
| 외부 opcode → HtmlCMDPacket 실제 엔트리와 65개 명령 전체 | N1/N2 | 명령 종류와 flag 정확도 향상 |
| CTalkAction / ChangeTalkTarget / TalkChannelCache 폐기 및 플래그 | N3 | 대화 상태 수명 및 검증 범위 결정 |
| NPC 내부 0x06/0x1A/0x0C 실행 스레드 및 event 인자 | N4 | 이벤트 소유권, 전달 순서 결정 |
| 0x3D AtomicJob payload 및 CacheD 응답 전체 | N5/N6 | NC와 RKern의 경제 처리 차이 및 경계 분석 |

N0와 동작 등가의 구조 분리는 현재 확인 결과로 준비할 수 있다. 아직 확인되지 않은 NC 조건이나 스레드 구조를 추정해 적용하지 않는다. 추가 바이너리 확인이 필요한 단계만 선행 분석하고 이미 확보한 전체 네트워크 흐름을 처음부터 반복하지 않는다.

## 9. 현재 상태 및 다음 작업

현재 계획 작성까지 완료. NC 바이너리 재분석, Java 소스 변경, 운영 DB 변경, 테스트 수행을 이번 작업에서 새로 완료한 것으로 주장하지 않는다. 243차 ZIP의 무결성과 관련 생산 소스 구조는 직접 확인했다.

첫 개발 작업은 N0의 명령대장 및 하위 호출 경계 목록 작성이다. 기존 NPC 로직을 하나씩 추출해 확인하되 별도 신규 프레임워크를 만들지 않는다. 사용자의 시작 지시 후 N1부터 진행한다.

최종 완료는 CS_NpcAction 줄 수만으로 판단하지 않는다. CS_NpcAcion2 및 기존 XML/리스너 경로까지 분류되었는지, 기존 기능·세션·실행 순서가 보존되었는지, 경제 작업과 UI 완료가 안전한지, 전체 타입 컴파일 및 실제 환경 검증 결과가 있는지를 모두 확인한다.
