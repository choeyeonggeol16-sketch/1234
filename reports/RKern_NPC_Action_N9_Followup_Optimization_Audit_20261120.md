# RKern NPC Action N9 이후 개선 감사 및 구현 지시서
작성일: 2026-11-20
감사 기준: [N9 2afe014b31c6130b5d1208093434c3a34d9e7bb2](https://github.com/choeyeonggeol16-sketch/1234/commit/2afe014b31c6130b5d1208093434c3a34d9e7bb2)

## 0. 결론 및 작업 범위

N9는 패킷과 실행 책임 분리, 공통 제작/거래 receipt, 일부 안전성 개선까지 달성했다. 그러나 HtmlCmdHandler에 수천 줄의 레거시 분기와 부수 효과가 남아 있으며 일부 경제/파티/스폰 경로는 여전히 원자적이지 않다. 단순 클래스 이동을 완료로 간주하지 말고 아래 N10~N16의 실제 실행 경로를 정리한다.

NC 스타일의 User → HtmlCmdHandler → 실행부 구조와 기존 CIO owner, CacheD, NpcServer 및 기존 관리자 소유권을 유지한다. 신규 범용 워커, 큐, 요청별 Context, 명령별 객체/람다 레지스트리, 무조건적인 클래스 증설 금지. NPC AI/전투/일반 네트워크/브로드캐스트/아이템 사용 코어는 이 작업의 범위 밖이다. 공통 DB 원자성 변경이 불가피하면 별도 영향 분석 후 승인받는다. 확인되지 않은 L1 정책을 NC 확정 로직이라고 표현하지 않는다.

### 실제 확인한 N9 소스 상태

기준 경로: rkern/src/l1j/server/server/model/network/core/cs/packet/
- HtmlCmdHandler.java 3,213줄, getNpcTemplate().get_npcId() 문자열 출현 206회, equalsIgnoreCase 호출 표기 287회, new 표기 25회.
- NpcQuestTransactions.java 1,348줄, NpcExchangeCommands.java 903줄, NpcCastleActions.java 727줄, NpcGameEventActions.java 652줄, NpcTravelCommands.java 637줄.
- 위 수치는 원문 텍스트 집계이며 주석을 포함할 수 있다. 패킷당 실행 횟수/실제 할당량이 아니다.
- N9 보고서상 임시 의존성 클래스패스 사용 JDK21 생산 Java 1,928개 컴파일 오류 0/기존 경고 5, 카르마 모의 154 시나리오/766 검사 통과. 이번 감사에서는 직접 재실행하지 않았다.

## 1. N10 — NPC 명령 입장권/보상/카르마 안전성 [P0]

### A. 오림 파티 입장: 명확한 차감 결과 무시
NpcGameEventActions.java: 536~580, orim()
파티의 맵/아이템 사전 검사 이후 컨트롤러 시작 및 방송, 멤버별 addPlayMember, consumeItem(410096,1), teleport. 실제 consumeItem 반환값을 무시한다. 리더 CIO에서 타 멤버 인벤토리/상태를 직접 변경하는 경로의 소유권도 확인해야 한다.
- 먼저 기존 OrimController의 예약/수명/실행 스레드와 L1InventoryTransaction의 다중 PC 거래 지원(동 파일 119행 주석)을 추적. 지원 범위와 각 owner 전달 및 rollback을 입증한 뒤 이용한다. 단순 개별 성공 콜백을 묶어 전체 원자성이라고 부르지 않는다.
- 실제 참가자 스냅샷, 맵/파티/리더/아이템 재검증, 기존 컨트롤러의 배타 예약, 전체 차감 확정, 입장/방송 순서로 재구성. 취소 및 재접속/장애 처리 명시.
- 기존 2명 이상 조건과 안내문 3~5명 불일치는 정책 결정 사항이므로 무단 수정하지 않는다.
- 시험: 멤버 1명 차감 실패/인벤토리 gate busy/탈퇴/로그아웃/두 파티 동시 입장/ACK loss.

### B. 카르마 역방향 교환 및 저장 경계
HtmlCmdHandler.java: 1095~1136 (NPC 80050), 1287~1326 (80064): 결정 1/10/100 차감 후 addKarma 및 성공 페이지. N9 NpcQuestTransactions.exchangeKarmaCrystal(936행)은 지급→카르마 사후 반영이지만 카르마 자체의 durable 저장은 별도다.
- NpcQuestTransactions의 기존 실행부로 양방향 교환을 모은다. 부호, 배율, 메시지 1078/1079/1080/1081, meet 페이지, 상한을 보존한다.
- 캐릭터 저장 경로와 CTransaction receipt SQL/owner replay/inventory release를 추적하여 아이템 행과 카르마 변경을 하나의 durable 경계로 묶을 수 있는지 검토. 동일 SQL 트랜잭션 내 조건부 preimage 검증 및 owner 반영, 또는 기존 지속 가능한 복구 수단을 사용. 오래된 캐릭터 저장으로 카르마가 덮이는지 검사.
- 프로세스 종료 시점(DB commit 전/후, ACK 전/후, owner 반영 전/후), 재접속, 동시 카르마 변화 검증. 공통 저장 구조 변경 필요 시 범위 승인. 성공 콜백을 추가하는 것만으로 장애 원자성이 해결되었다고 보고하지 않는다.

## 2. N11 — 훈련소/레이드/펫 생명주기 [P0]

- NpcGameEventActions.java:430~469. 훈련소: 99개 검사 후 차감, blankMapId, startRaid, 4/8/16개 키 개별 지급·KeyTable 저장. 중간 지급 실패 시 기존 코드는 안내하고 반환하지만 이미 차감/방 생성/일부 지급이 남을 수 있다. 기존 관리자에서 예약 충돌을 방지하고 결제/키 메타데이터/키 행을 가능한 동일 receipt로 확정. 실패 시 예약 해제, 부분 키 불가, 재시작 시 예약 및 키의 정합성 검증.
- 같은 파일 498~535: 발라카스 준비/입장. 개수 검사와 startReady/startRaid 사이 경쟁, 자원 소비 후 방 생성 실패, 검 지급 후 스폰 실패 후보. 기존 관리자 구현과 동시성 계약 확인 후 예약→결제 및 지급→활성화/복구. 단순 count 검사 추가는 경쟁 해결이 아니다.
- NpcPetCommands.java:27~73: CHA 산정 후 비용 차감→스폰→템플릿/생존/인벤토리 확인→아뮬렛 저장→펫 생성. 차감 후 여러 조기 반환이 존재. 사전 템플릿/수용량/기존 소유권 검증을 가능한 먼저 하고 펫/아뮬렛/비용의 durable 관계와 실패 정리 정책을 확립한다. 월드 스폰 자체를 SQL 원자성으로 가장하지 않는다. 스폰 실패/풀 인벤토리/DB 실패/펫 객체 생성 실패 검사.
- NpcGameEventActions 및 NpcTravelCommands에 남은 직접 consume/store는 모두 분류한다. 특히 보스키(346~359), 차원의 문(509~568), 카너스(485~503), 오림 항아리(583~588). 카너스는 5000 아데나 차감 결과를 무시하고 현재 사용 불가 안내를 내는 기존 정책; 비용 자체를 제거할지는 운영자 확인. 일반 무료 지급이나 의도된 바닥 지급까지 일괄 복제/버그라고 규정하지 않는다.

## 3. N12 — 대화 채널과 명령 유효성 [P0/P1]

HtmlCmdHandler.java:283, 297~304, 3139~3210. 대상 독립 명령은 canInteractWithNpc보다 앞서 처리된다. 일반 NPC는 같은 맵 및 X/Y 각각 6칸 이내 확인 후 onFinalAction과 던전 초기화/사운드를 실행하고, 이후 inn/XML/레거시를 디스패치한다. 거리 확인은 채널/페이지/명령 권한과 동일하지 않다.
- CS_NpcTalk → User → NpcTalkHandler 및 일반 HTML, 수량 입력, XML, 여관, 직접 UI 진입을 전수 매핑한다. 대상 독립 UI는 운영상 필요한 명령을 별도로 명시하되 기존 정상 동작을 막지 않는다.
- NPC ObjectId와 spawn generation 또는 기존 수명 식별자, 맵, 대화 세대, 페이지/명령 종류를 CIO owner에서 확인. 기존 TalkChannelCache 구현 여부를 먼저 확인; 미존재 시 필요한 최소 primitive 상태를 기존 세션/캐릭터에 두는 방식을 비교한다. 모든 NPC에 요청별 객체, 전역 ConcurrentHashMap, 전체 HTML 파서 실시간 사용을 금지한다.
- 거리, 죽음/소멸/재사용, 맵 이동, 로그아웃, 새 대화, 수량 입력 재전송, GM/특수 UI, 다중 단계 제작을 시험. 기존 XML/ActionListener/HTML 데이터의 명령 목록을 실제 운영 자료로 검증하기 전 전역 강제 정책으로 바꾸지 않는다.
- 비동기 완료: NpcCraftCommands.complete/fail와 각 receipt callback은 경제 결과와 UI 유효성을 분리한다. 커밋된 아이템은 정확히 반영하되 이전 대화나 재사용 NPC에 대한 성공 HTML은 현재 대화 세대 정책으로 제한. 오래된 이벤트가 새 창을 덮지 않는지 시험.
- onFinalAction/던전 초기화/사운드 호출 순서는 현재 호환 기준으로 기록하고 허용되지 않은 요청의 부수 효과 차단은 별도 의도 변경으로 검증한다.

## 4. N13 — HtmlCmdHandler 디스패처 저비용화 [P1]

현 상태는 책임 클래스는 분리했지만 3,213줄 디스패처와 연쇄 조건이 유지된다. 무작정 메서드별 클래스를 추가하지 않는다.
- 일반 NPC가 확정된 지점에서 L1NpcInstance npc, templateId를 지역 변수로 한 번 추출. getNpcId와 getNpcTemplate().get_npcId() 의미 동일성/템플릿 변경 가능성 검토 후 통합; 콜백 등 변경 가능한 경계를 넘겨 무조건 캐싱하지 않는다.
- AST로 현재 분기 순서/조건/부수 효과/return/HTML precedence를 대장화. 동일 ID 반복, 도달 불가, 독립 if와 else-if 차이, listener/XML/inn fallback을 확인.
- 고정 templateId 그룹을 기존 NpcTemplateCommands 쪽 primitive int switch 또는 정적 작은 디스패치로 옮기고, 기능 본문은 기존 NpcTravelCommands/NpcBuffCommands/NpcQuestTransactions 등을 재사용. Java 컴파일러의 tableswitch/lookupswitch와 실제 측정으로 선택. 희소 NPC ID에 max-id 크기의 배열 금지. Map<Integer,Function>, 명령 객체 생성, 박싱, 전체 입력 toLowerCase 할당을 추가하지 않는다.
- 명령 문자열 비교는 대소문자 정책을 보존한다. 빈 문자열/숫자/ASCII/비ASCII/이전 특수 명령 테스트. NpcCastleActions.sendTaxScreens(307행)처럼 독립 부수 효과와 mayer3, buy 조기 반환을 순수 라우팅처럼 바꾸지 않는다.
- NpcTemplateCommands 54개 유효 분기 및 listener 실패 fallback 유지. NpcQuestTransactions는 우선 기존 클래스 내부 공통화; 새 클래스는 실제 독립 책임과 코드 규모가 충분할 때만.
- 완료 기준: 분기별 결과/호출 순서 동일, 요청별 할당 증분 0, 대표 NPC dispatch CPU/요청 및 p99 개선 실측. 줄 수 목표는 보조 지표이지 최종 성공 조건이 아니다.

## 5. N14 — 제작/교환 데이터 구조와 임시 할당 [P1/P2]

- HtmlCmdHandler 945~948/1624~1627: 토템 제작 등 요청별 4개 int 배열. 2485~2487 등 단일 재료 배열. 변하지 않는 recipe는 불변 primitive 데이터로 class 초기화 시 한 번 구성하고 reload되는 데이터는 기존 안전한 게시 방식 사용. 동적 수량은 기존 공통 제작 실행부의 스칼라 단일 재료/보상 경로로 처리하는 방안 검토. 비동기 callback이 공유 mutable 배열을 참조하지 않게 한다.
- NpcCraftCommands 20~107: 선행 재료/보상 템플릿 및 소지 검사, 실제 receipt debit/grant, 완료 이름 조회. 중복 template 조회 및 인벤토리 스캔을 측정한다. 필수 사전 검사/실패 메시지/receipt preimage 검증/슬롯·무게 검증을 제거하지 않는다. 중복 재료 ID 합산과 중복 stackable 보상 슬롯 계산은 현재 보수 정책 및 정상 운영 recipe 확인 후 처리. static recipe에 변경 가능한 게임 인스턴스 캐싱 금지.
- HtmlCmdHandler 645, NpcPetCommands 30: values().toArray() 스냅샷 할당. 기존 petList의 소유권/동시 변경 계약 확인 후 직접 순회나 기존 primitive traversal 재사용. 스냅샷 안전성을 잃게 되면 유지한다.
- HtmlCmdHandler 3103: String.format("a%d", 클래스번호) — 제한된 유효 클래스 번호별 불변 문자열 또는 단순 결합 비교. 3098의 오류 메시지는 cold path로 후순위. 251 EMPTY_BYTES 및 기존 Random 공유, 이미 제거된 임시 배열은 중복 작업하지 않는다.
- NpcExchangeCommands.createNewItem(488행)의 inventory/floor 경로와 NpcQuestTransactions의 잔여 직접 지급 목록을 분류. 실제 바닥 지급 정책을 보존하면서 재료 debit과 바닥/인벤토리 지급의 실패 복구를 검증. 이미 receipt인 거래를 불필요하게 다시 구현하지 않는다.
- 전체 receipt callback 재사용을 무조건 강제하지 않는다. 거래별 mutable 상태가 필요하며 수명/재사용 경쟁이 발생할 수 있다. 먼저 불필요한 배열·포맷·반복 조회를 줄인다.

## 6. N15 — CIO 지연 및 DB 직접 접근 감사 [P1]

HtmlCmdHandler.dispatchTarget 3158~3178에는 DungeonTimeProgressLoader.delete/update, account.updateHero21이 onFinalAction 전에 있다. 실제 SQL 동기 실행인지 구현/호출 흐름 추적이 필요하며 이름만으로 확정하지 않는다. 훈련소 KeyTable과 기타 상점/공성/퀘스트 경로도 점검.
- 기존 CIO owner에서 상태·순서 관리, CacheD의 이미 존재하는 적절한 경로로 영속화. 기존 직렬화/장애 정책이 없는 곳은 무리하게 비동기화하지 않는다.
- JDBC, 대기, sleep, 잠금, 반복 월드 스캔, 문자열 포맷, 과도한 예외 로그를 분류. 정상 처리에서 새 큐/worker 없이 측정된 병목만 제거. 실패 로그는 진단을 없애지 말고 기존 로깅 정책 활용.
- 공유 Random 경쟁이 실제로 측정될 경우에만 owner-local RNG 등의 검토. 기존 확률/호출 횟수와 결정 순서를 보존할 수 없으면 유지한다.

## 7. N16 — 통합 성능·회귀 종결 [P0 검증 게이트]

- N9 기준과 수정본 동일 JDK21/라이브러리/DB/부하/CPU/힙. 전체 production type compile, 기존 NPC N0~N9 및 아이템 회귀. AST 경로 매핑 + 실제 추출 로직 테스트 + 운영 HTML/XML/DB 데이터 대조.
- 실제 MySQL/InnoDB 및 CacheD를 사용한 정상/rollback/ACK loss/중복 receipt/프로세스 강제 종료/재접속 검증. 게임 상태, 인벤토리, 키/퀘스트/카르마 등 durable 상태와 패킷 대조.
- 1/1024/5000 접속 부하의 NPC 대화/메뉴/제작/교환 혼합 요청 및 실제 유휴 비율을 명시. 5000명 모두 매초 대량 NPC 클릭하는 인공 부하와 실제 시나리오는 구분.
- 측정: CPU ns/command, 요청 처리량, CIO 처리 p50/p95/p99/p99.9, CacheD queue/receipt 완료 지연, alloc B/op, GC, retained heap, SQL/거래. warm-up 및 5회 이상 비교, JFR 할당/CPU/locking 프로파일. 모든 수치는 동일 환경 실제 측정 후 보고. P0 정확성 실패 또는 기존 동작 회귀 시 패치 반려.
- 기존 실행 순서 및 소유권을 근거로 변경 파일 목록과 검증 로그를 보관하고 실패도 보고. 임의 새 스레드/큐/클래스 또는 JVM 옵션을 사용자에게 요구하지 않는다.

## 8. 실행 순서와 감사 한계

N10 오림/카르마 → N11 예약/펫 → N12 대화 채널 → N13 디스패치 → N14 할당 → N15 DB 경로 → N16 통합. 구조 변경과 의도적 게임 동작 변경을 서로 분리해 검증한다. 현재 소스에서 직접 확인한 위험과 후속 분석 후보를 구별했다. 전체 호출 그래프/운영 XML/DB/컨트롤러 구현/실제 장애 및 성능은 이번 정적 감사에서 완전히 검증하지 않았다. 이번 커밋은 보고서만 추가하며 생산 소스 변경 및 테스트 실행 없음.
