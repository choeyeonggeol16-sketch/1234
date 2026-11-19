# NC L2Server / L2NPC NPC 상호작용 구조 역분석 및 RKern 적용 보고서

- 작성일: 2026-11-19 (KST)
- 대상: NC L2Server(10).exe, L2NPC(3).exe
- RKern 기준: ItemUse Final MarbleCache Phase241–243 전체 SRC
- 목적: CS_NpcAction의 역할 분리, NC HTML 명령 처리와 NPC 이벤트 구조 복원, 기존 CacheD 기반 제작·교환 안정성 개선
- 상태: 앞선 바이너리 분석 결과의 정리본. 이 보고서를 작성하면서 별도로 소스를 수정하거나 바이너리 전체를 재분석하지 않았다.

## 1. 분석 기준과 결론

| 대상 | 파일 크기 | 식별 |
|---|---:|---|
| L2Server(10).exe | 13,215,232 B | PE x64, SHA-256 9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588 |
| L2NPC(3).exe | 3,098,624 B | PE x64, 이전 분석에서 사용한 파일. SHA-256은 이 보고서에서 미기록 |

주소는 위 빌드의 VA 기준이다. 다른 C3/C4/HF 빌드의 주소나 외부 Extender 소스와 혼용하지 않는다. 아래의 함수명은 이전 분석에서 확인하거나 식별한 명칭이며, 원본 PDB로 전부 검증한 심볼 목록은 아니다. 동일 주소라도 함수 경계와 인자는 실제 구현 전에 다시 확인할 수 있다.

NC의 핵심 역할 분담은 다음과 같다.

1. L2Server의 CUserSocket이 클라이언트 HTML 명령을 해석하고 명령별 핸들러로 분기한다.
2. 대화 대상과 HTML 링크의 유효성 확인 경로가 별도로 존재한다.
3. NPC 스크립트로 처리할 명령은 내부 메시지로 L2NPC에 전달한다.
4. L2NPC는 TALKED / TALK_SELECTED / MENU_SELECTED를 분리된 이벤트로 처리한다.
5. 아이템 삭제·지급을 AtomicJobCompositor에 모아서 게임 서버의 아이템 조작 및 DB 요청 경로로 전달한다.

이는 단순히 패킷 클래스를 여러 파일로 나누는 것과 다르다. 명령 분류, 대화 상태, NPC 이벤트, 게임 상태 수정, DB 확정의 역할을 분리하는 구조다.

## 2. L2Server: 패킷 수신과 HTML 명령 처리

### 2.1 기존 네트워크 분석에서 확인된 수신 경로

    IOCP worker (0x7056AC)
      -> GetQueuedCompletionStatus (call site 0x7057C9)
      -> OnIOCallback (0x54655C)
      -> READ completion (0x545430)
      -> CUserSocket::OnRead (0xA0372C)
      -> opcode handler table (socket +0xC0)
      -> handler direct call (0xA039BE / 0xA03AED)
      -> next WSARecv registration (0x5456D0)

위 경로는 동일 소켓의 수신 프레이밍과 opcode 직접 실행에 관한 확인 사항이다. 모든 NPC 이벤트가 같은 IOCP 워커에서 끝나는 것을 의미하지는 않는다.

리니지2 클라이언트의 Action(0x1F)과 RequestBypassToServer(0x23)는 참고할 대응 패킷이다. 이 빌드에서 각 외부 opcode 테이블 엔트리와 HtmlCMDPacket 사이의 직접 매핑은 별도 검증 항목으로 남겨둔다. 내부 L2Server↔L2NPC opcode와 혼동하지 않는다.

### 2.2 확인된 HTML 처리 함수

| VA | 함수 식별명 | 역할 |
|---|---|---|
| 0xA1C58C | CUserSocket::HtmlCMDPacket | HTML 명령 수신·검증·분기 |
| 0xA1CB64 | CUserSocket::LoadHtmlCmdHandler | 명령 테이블 등록 |
| 0x6D9CE8 | CHTMLCache::IsValidLink | HTML 링크 검증 |
| 0x6DA258 | CHTMLCacheManager::IsValidLink | HTML 캐시 관리자 링크 검사 |
| 0x9BA410 | User::ChangeTalkTarget | 유저 대화 대상 관리 |
| 0x568638 | CCreature::ChangeTalkTarget | Creature 대화 대상 관리 |
| 0x971744 | TalkChannelCache::SubChannel::IsValidChannel | 대화 채널 유효성 검사 |

관련 소스 경로 문자열: UserSocket_HTMLCmd.cpp.

이전 분석에서 LoadHtmlCmdHandler의 초기화 명령 65개와 24바이트 단위 테이블 엔트리를 확인했다. 명령 문자열 포인터, 조건 플래그, 실행 함수 포인터를 저장하며, 수신 명령을 순차적으로 비교하여 핸들러를 호출하는 경로가 확인되었다. 정확한 각 필드 오프셋, 플래그 전체 의미, 65개 전체 행의 상세 목록은 아직 이 보고서에 확보되어 있지 않다.

### 2.3 확인된 명령 테이블 일부

| 등록 명령 | 실행 VA | 기능 분류 |
|---|---|---|
| menu_select | 0xA1EF58 | NPC 메뉴 선택 |
| talk_select | 0xA190C0 | 대화 선택 |
| quest_choice | 0xA1F498 | 퀘스트 선택 |
| quest_start | 0xA224C8 | 퀘스트 시작 |
| quest_accept | 0xA1EB80 | 퀘스트 수락 |
| teleport_ | 0xA1CCEC | 텔레포트 |
| buy | 0xA16180 | 구매 |
| sell | 0xA16578 | 판매 |
| multisell | 0xA15D30 | 교환 목록 |
| recipeNPC | 0xA1E7E0 | NPC 제작 관련 |
| learn_skill | 0xA18A70 | 스킬 습득 |
| enchant_skill | 0xA18D88 | 스킬 강화 |

12개 확인 행을 기재했다. 나머지 53개를 임의로 채우거나 해당 기능의 전체 하위 호출을 확정하지 않는다.

링크 검증은 설정과 명령 조건에 따른 분기가 있으므로, 모든 명령에 동일한 검증을 강제한다고 해석하면 안 된다. L1 클라이언트의 대화창, 특수 UI, 이벤트 명령과의 호환성을 조사한 뒤 적용한다.

## 3. NPC 대화 이벤트와 L2NPC 역할

이전 분석에서 내부 송신/수신 opcode를 양쪽 바이너리에서 대조했다.

| L2Server→L2NPC 내부 opcode | 수신 함수 식별명 | 이벤트 |
|---|---|---|
| 0x06 | TalkPacket | TALKED |
| 0x1A | TalkSelect | TALK_SELECTED |
| 0x0C | MenuSelectEventPacket | MENU_SELECTED |

L2NPC의 CNPCEventFactory 관련 이벤트 생성 함수:
- CreateTalkedEvent
- CreateTalkSelectedEvent
- CreateMenuSelectedEvent

메뉴 선택 경로:

~~~mermaid
flowchart TD
 A["CUserSocket::HtmlCMDPacket"] --> B["명령 및 조건별 링크 검증"]
 B --> C["menu_select / 0xA1EF58"]
 C --> D["NpcServer::Send / 내부 0x0C"]
 D --> E["L2NPC::MenuSelectEventPacket"]
 E --> F["CNPCEventFactory::CreateMenuSelectedEvent"]
 F --> G["NPC 이벤트 핸들러 / 스크립트"]
~~~

대화 대상의 관리와 실제 NPC 명령 실행은 다른 책임이다. 기존에 확인된 CTalkAction 및 CCreatureController::Talk는 대화 행동과 관련된 별도 구조를 보여준다. 단, CTalkAction의 상세 거리 판정, 대상 수명, 이벤트 송신까지의 모든 분기 및 현재 x64 빌드의 VA는 아직 확정하지 않았다. 과거 빌드의 심볼 주소를 이 바이너리에 그대로 적용하지 않는다.

## 4. NPC 제작·교환: AtomicJobCompositor

### 4.1 L2NPC 주소

| VA | 함수 식별명 |
|---|---|
| 0x4BB698 | CNPC::GiveItem1 |
| 0x4BB850 | CNPC::DeleteItem1 |
| 0x487A04 | CNPC::ShowPage |
| 0x501684 | AtomicJobCompositor::AddItemManip |
| 0x50110C | AtomicJobCompositor::AddJob |
| 0x500190 | AtomicJobCompositor::Process |
| 0x5007B4 | AtomicJobCompositor::Clear |
| 0x501A18 | CScriptEngine::AddAtomicJob |

### 4.2 L2Server 주소

| VA | 함수 식별명 |
|---|---|
| 0x7D3894 | NpcAtomicDelGiveItemParam |
| 0x9A2508 | User::HandleItemManipulation |
| 0x5E1AD4 | CDB::CreateUpdateItems |

### 4.3 확인 경로

~~~mermaid
flowchart TD
 A["NPC script"] --> B["GiveItem1 / DeleteItem1"]
 B --> C["AtomicJobCompositor::AddItemManip"]
 C --> D["AtomicJobCompositor::Process"]
 D --> E["NPC→Game / opcode 0x3D"]
 E --> F["NpcAtomicDelGiveItemParam"]
 F --> G["User::HandleItemManipulation"]
 G --> H["CDB::CreateUpdateItems"]
 H --> I["CacheD request"]
~~~

앞선 분석은 0x3D 내부 송신/수신 일치, User 가상 함수 호출 슬롯 +0xBA0, CDB 직접 호출을 확인했다. 스크립트 오류 시 누적 작업을 정리하는 경로도 확인되었다.

의미: 아이템 조작 명령을 수집하여 게임 서버의 아이템 조작 경로로 넘기는 구조가 있다. 다만 AtomicJob 이름만으로 모든 SQL이 단일 DB 트랜잭션이라는 것, 모든 실패/재시작/ACK 유실이 복구된다는 것, 모든 NPC GiveItem/DeleteItem 호출이 이 경로를 탄다는 것을 증명한 것은 아니다. CacheD 내부와 응답 콜백은 별도 분석 대상이다.

RKern에서는 이미 구현한 L1InventoryTransaction 및 CacheD receipt를 활용하는 방향이 타당하다. NC의 내부 패킷을 그대로 신설할 필요는 없다.

## 5. RKern 243차 현황

앞서 확인한 파일 줄 수(해당 SRC 기준):

| 클래스 | 줄 수 |
|---|---:|
| CS_NpcAction.java | 7,535 |
| CS_NpcTalk.java | 488 |
| ActionControlHandler.java | 267 |
| L1NpcActionFactory.java | 63 |
| L1NpcAction.java | 33 |

CS_NpcAction에는 제작, 교환, 퀘스트, 텔레포트, 변신, 상점, 버프, 공성 등 여러 책임이 섞여 있다.

직전 분석에서 확인한 제작부(약 5772~5789행)의 핵심 형태:

~~~java
for (int j = 0; j < materials.length; j++) {
    pc.getInventory().consumeItem(materials[j], counts[j]);
}
for (int k = 0; k < createitem.length; k++) {
    item = pc.getInventory().storeItem(createitem[k], createcount[k]);
}
~~~

직접 소모와 지급 사이의 실패 처리·원자성 검수가 필요하다. 기존 외부 락 및 호출자 계약까지 확인한 후 문제가 있는 경로만 변경한다. 추가로 일부 NPC 대상 검사보다 앞서 실행되는 명령, 변신 주문서 소모 결과 미확인, 예외 미기록 경로를 발견했다. 특수 UI 명령처럼 NPC가 없는 것이 정상인 경우를 별도로 분류해야 한다.

## 6. NC → RKern 적용 설계 (제안, 아직 구현하지 않음)

| NC 역할 | RKern 대응 |
|---|---|
| CUserSocket::HtmlCMDPacket | CS_NpcAction: 패킷 읽기, 최소 검사, 명령 전달 |
| HtmlCmdHandler | 명령 단위 디스패치 및 분류 |
| User::ChangeTalkTarget | 기존 User/캐릭터 대화 대상 관리 |
| TalkChannelCache | 기존 대화 세션과 명령 권한 검증 경로에 결합 |
| CNPCEventFactory / NPC 이벤트 처리 | 기존 NpcServer 및 NPC 기능별 실행 담당 |
| AtomicJobCompositor | L1InventoryTransaction |
| CDB / CacheD | 기존 CacheD receipt, 영속 확정·복구 |

목표 흐름:

    CS_NpcTalk / CS_NpcAction
        -> User / 기존 캐릭터 실행 소유권
        -> 명령 분류 + 대상·채널 검증
        -> 기존 NPC 이벤트 및 기능별 실행
        -> 필요 시 L1InventoryTransaction
        -> CacheD 확정 및 소유 실행 문맥 완료 처리

NC는 별도 L2NPC 프로세스를 쓰지만 RKern은 이를 그대로 모방할 이유가 없다. 기존 CIO와 NpcServer의 실제 실행 소유권을 확인하고 활용한다. 패킷별 신규 스레드/큐/작업 객체를 만들지 않는다. NC 클래스명은 실제 책임이 일치할 때 사용하며 이름만 맞추기 위한 클래스 증설은 피한다.

### 실행·메모리 원칙

- 기존 명령 우선순위, 문자열 비교 방식(대소문자/접두사/정확 일치), 특수 UI, 반환 및 예외 실행 순서를 최초 단계에서 보존한다.
- 명령 문자열을 이미 읽었다면 중복 파싱, split, substring, 람다 캡처, 임시 Map/List 생성 등을 측정하여 제거한다.
- 정적 명령 테이블이 필요하면 초기화 시 1회 구성, per-request 객체 생성을 최소화한다. NC의 선형 비교 방식을 무조건 성능 최적이라고 간주하지 않으며 실제 명령 분포를 기준으로 판단한다.
- NPC 대상은 ID 및 현재 세션으로 재검증한다. 무기한 static 캐릭터/NPC 참조, ThreadLocal 잔류, 세대가 바뀐 지연 콜백을 피한다.
- 기존 CIO owner의 실행 순서 유지. DB 대기 때문에 CIO 스레드를 블로킹하지 않는다. 콜백은 기존 소유권·receipt 계약을 따른다.
- NPC AI 이동/전투, 타이밍 휠, 네트워크 엔진, 방송, CacheD 코어는 이 작업의 기본 수정 범위에서 제외한다.

### 제작·교환 처리 원칙

1. 현재 플레이어와 명령/NPC 유효성 확인.
2. 재료 ID·수량·강화 등 기존 조건 검증, 중복 요구 재료 합산 및 수량 오버플로 방지.
3. 기존 인벤토리 트랜잭션 계약으로 재료·보상·수량·용량 및 관련 영속 상태를 함께 계획.
4. 기존 CacheD receipt의 커밋/거부/불명확 상태 및 replay 정책을 적용.
5. DB 확정과 메모리 반영 후 성공 메시지·효과·HTML 응답을 처리. 실패 시 성공 UI/보상 금지. ACK 유실 후 무조건 재시도하지 않는다.
6. 로그아웃·재접속·NPC 소멸·대상 변경 후 지연 완료를 별도로 검증한다. 이미 확정된 영속 작업을 단순히 세션 종료로 취소했다고 간주하면 안 된다.

이는 구현 목표이며 현재 모든 NPC 명령에 적용됐다는 의미는 아니다.

## 7. 구현 순서 및 완료 기준

| 단계 | 작업 | 완료 조건 |
|---|---|---|
| 1 | 243차 CS_NpcAction/CS_NpcTalk 모든 명령 및 선행 분기 목록화 | 누락 없이 호출·반환 순서 기준선 확보 |
| 2 | 패킷 진입/명령 디스패치 역할 분리 | 기존 명령과 패킷 출력 등가성 |
| 3 | 대화 대상·채널·거리·권한 검증 | 정상 UI는 유지, 부적절한 명령만 차단 |
| 4 | NPC 대화·선택·메뉴 이벤트의 실행 책임 분리 | 기존 NpcServer/CIO 소유권 및 재진입 보존 |
| 5 | 제작·교환·퀘스트 보상 원자화 | 소비/지급·실패·중복 요청 검증 |
| 6 | 텔레포트·상점·버프·공성·기타 독립 기능 정리 | 기존 기능 분기와 부수 효과 보존 |
| 7 | 메모리·중복 호출 개선 | 프로파일링 및 참조 수명 검증 |
| 8 | 전체 검수 | 의존성 포함 Java 21 컴파일, 모의 및 실제 DB/인게임 검증 |

회귀 항목: 정상 NPC 대화, 대상 없음/삭제/다른 맵/거리 이탈, Idle/사망/텔레포트 중 사용, 명령 충돌·중복 접두사·특수 UI, 대화창 재열기, 동시 제작/교환, 중복 재료, 부족 재료, 인벤토리 용량, 비정상 수량, DB rollback/ACK 유실/replay/재시작, 접속 종료, 미확정 작업 이후 재로그인, 기존 제작 확률 및 강화·속성·보상 유지, 장시간 객체 참조 수명과 JFR 할당량.

검증은 기존의 구문 검사나 추출 메서드 모의 테스트만으로 완료 처리하지 않는다. 전체 타입 컴파일과 실제 환경 검증 결과는 별도로 기록한다. 각 차수의 최신 SRC와 변경 범위만 정확히 추적한다.

## 8. 남은 바이너리 분석 / Cutter 재현 작업표

아래 주소는 이전 분석의 시작점이다. Cutter에서 동일 PE를 열고 함수 분석과 디스어셈블리를 확인한다.

L2Server(10).exe:

~~~text
af @ 0xA1C58C
pdf @ 0xA1C58C
af @ 0xA1CB64
pdf @ 0xA1CB64
af @ 0x6D9CE8
pdf @ 0x6D9CE8
af @ 0x6DA258
pdf @ 0x6DA258
af @ 0x9BA410
pdf @ 0x9BA410
af @ 0x568638
pdf @ 0x568638
af @ 0x971744
pdf @ 0x971744
af @ 0xA1EF58
pdf @ 0xA1EF58
af @ 0xA190C0
pdf @ 0xA190C0
af @ 0xA15D30
pdf @ 0xA15D30
af @ 0xA1E7E0
pdf @ 0xA1E7E0
af @ 0x7D3894
pdf @ 0x7D3894
af @ 0x9A2508
pdf @ 0x9A2508
af @ 0x5E1AD4
pdf @ 0x5E1AD4
~~~

L2NPC(3).exe:

~~~text
af @ 0x4BB698
pdf @ 0x4BB698
af @ 0x4BB850
pdf @ 0x4BB850
af @ 0x487A04
pdf @ 0x487A04
af @ 0x501684
pdf @ 0x501684
af @ 0x50110C
pdf @ 0x50110C
af @ 0x500190
pdf @ 0x500190
af @ 0x5007B4
pdf @ 0x5007B4
af @ 0x501A18
pdf @ 0x501A18
~~~

추가 분석 항목:
- 외부 클라이언트 opcode → HtmlCMDPacket 정확한 엔트리.
- 65개 HTML 명령 전체 테이블, 플래그의 의미, prefix 경계 및 비교 우선순위.
- CTalkAction 시작·종료, 대화 대상 변경과 무효화, 채널 및 HTML 캐시 수명.
- L2NPC 내부 0x06/0x1A/0x0C 핸들러·이벤트 생성 함수의 VA와 인자 구조, 이벤트 큐/실행 스레드.
- 개별 제작/교환/퀘스트/상점 하위 함수와 예외·취소 분기.
- 0x3D 메시지 포맷, item manipulation 검증, CacheD 실제 commit/ACK 및 실패 응답 처리.

## 9. 결론과 현재 상태

NC의 HTML 명령 테이블, 별도 대화 이벤트, 아이템 조작 집합 처리라는 세 가지 핵심 구조를 RKern의 기존 구성으로 적용할 수 있다. CS_NpcAction을 단순히 작은 클래스 여러 개로 분리하는 것보다 이 책임 분리 구조를 우선한다.

현재 사용자 요청으로 NPC 수정은 중단된 상태이다. 이 문서는 직전 분석 내용을 보존하기 위한 보고서이며 Java 소스 변경, 신규 스레드/큐 생성, 운영 DB 변경, NC 실행 파일 업로드는 하지 않는다. 모든 바이너리 하위 함수 및 CacheD 응답의 완전 역복원은 아직 완료되지 않았다.
