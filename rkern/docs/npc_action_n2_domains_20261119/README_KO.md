# NC NPC Action N2 — 도메인 실행부 분리

기준: RKern_NPC_Action_N0_N1_N2_Entry_FULL_SRC_20261119.zip
SHA-256: 50e588aac1f8c8f4aeeb43b59fed7fc23921d63dad32338607d476ddb4a0f306
설계: https://github.com/choeyeonggeol16-sketch/1234/blob/main/reports/RKern_NC_NpcAction_Development_Plan_20261119.md

## 구현 범위
- HtmlCmdHandler 기존 공개 진입부 유지. 기존 68개 보조 메서드 중 46개를 아래 5개 package-private 실행 클래스로 이동했다. 기존 결과, 문자열 비교 및 우선순위, 오류 처리, receipt 및 RNG 호출은 그대로 보존한다.
- NpcLocalCommands: 대상 독립 UI/변신/카드/관리자/ActionControlHandler 명령.
- NpcMarketCommands: 기존 buy 명령 본문 및 판매 전용 NPC 검사. boolean true는 이전 조기 return, false는 장외 후속 처리까지 이어짐을 의미한다.
- NpcCastleActions: 세금 화면, 아지트 및 성문·공성 하위 실행 함수. 세금 화면 호출 뒤 기존 명령 분기 계속 실행.
- NpcGameEventActions: UB/유령의 집/데스매치/펫매치/레이싱 및 코마 버프 하위 함수.
- NpcQuestTransactions: 기존 퀘스트 및 교환 receipt 실행 함수. 쿼리/트랜잭션/callback 내용 변경 없음. 기존 HtmlCmdHandler._random 단일 인스턴스를 공유한다.
- HtmlCmdHandler.dispatchTarget / dispatchRegisteredAction: 기존 NPC/PC 대상 검사 및 onFinalAction 부수 효과, inn/XML 처리 순서를 독립 함수로 추출. 각각 반환 의미가 다르므로 호출부의 early return에 주의한다.
- 추가 런타임 Context, 워커, 큐, 별도 RNG, DB 호출 없음. 5개 클래스는 static helper만 갖는다. 실제 처리량 개선은 측정하지 않았다.

## 검증 및 재현
- JDK 21 구문 검사: 생산 Java 1,920개, syntax.log 참조.
- 이전 메서드 68개 전부의 body 비교, 이동된 46개 메서드 검증. 4개 새 추출 블록과 전체 main의 역변환 비교. 공백/주석과 신규 클래스 이름 qualifier를 제외한 비교이다. test_domain_split.py 및 domain_regression.log 참조.
- 실제 원본/수정 추출 메서드에 typed Java doubles 적용: 구매 ID/레벨 아님(무게·랭킹·NPC 명칭) 4,530건 및 특수 UI 2,112건. 실제 인게임 테스트는 아니다.
- 이전 N0/N1 test_npc_entry.py는 당시 파일 위치와 본문을 대상으로 한 원형 비교 스크립트이다. 이번 구조 변경 후에는 test_domain_split.py가 해당 body 이동을 검증한다. 이전 결과를 새 실행 결과로 간주하지 않는다.
- 새 NpcAudit.java는 원본 10개 대상에 실제 분리된 7개 클래스도 포함한다. branch_inventory.tsv, call_inventory.tsv, method_inventory.tsv는 AST 기반 대장이다. 런타임 호출 대상 및 운영 XML/DB의 실제 명령 검증까지 포함하지 않는다.
- 전체 타입 컴파일 및 기존 아이템 회귀 테스트 결과는 동봉한 로그를 참고한다. 전체 의존성 빌드, 실제 MySQL/CacheD, 인게임, JFR 및 부하 검증은 아직 미완료다.

## 남은 작업 / 확인된 위험
- HtmlCmdHandler 본체 및 CS_NpcAcion2의 개별 NPC ID/명령 분기가 여전히 크다. N2 계속 진행.
- PC를 대상으로 하는 기존 분기는 요청자 identity 대신 objid로 대상 PC를 결정한다. 모든 SC_ShowPolyList 사용처는 pc.getId()를 보내고 있어 외부 대상 변신/소환을 막아야 하는 위험 후보가 있다. 운영 UI 및 GM 예외 여부와 owner 경계를 확인해 N3에서 별도 의도적 수정으로 처리한다. 이번 차수는 구조 이동 등가성을 보존하기 위해 동작을 변경하지 않았다.
- 기존 숫자 소환 요청에서 정의되지 않은 숫자는 summoncost=0으로 남아 0 나눗셈이 발생할 수 있다. N3 소환 명령 검증 대상이며 이번 차수에서는 기존 정책을 보존했다.
- 실제 TalkChannelCache 세션·페이지 유효성 구현, NPC 이벤트 전체 연결, 공통 제작 소비/지급 원자화 및 XML 비동기 완료 정책은 아직 적용하지 않았다. 게임 정책이나 기존 DB 트랜잭션을 임의로 변경하지 않았다.
