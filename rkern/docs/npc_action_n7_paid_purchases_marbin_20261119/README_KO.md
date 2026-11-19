# NPC Action N7 — 개별 유료 교환 및 마빈 보상

기준: RKern_NPC_Action_N7_Market_Mercenary_Ring_Safety_FULL_SRC_20261119.zip. 기존 NC 명령별 실행부 분리와 AtomicJob 대응 receipt를 사용한다.

## 생산 변경 4개
- HtmlCmdHandler: 조우 티칼(500062), 샤르나(200075), 휴그린트(900146), 킬톤(777849), 메린(777848), 마빈(70000), 에스메랄다(50007)를 기존 기능 담당 실행부로 위임. 기존 NPC 분기 순서 보존. 티칼 500064의 직접 지급 null 참조 방지. 디에츠 복원 2종에서 매 클릭 Random 생성 제거; 확률 비교 연산과 범위 유지. 난수열 자체는 동일하지 않다.
- NpcExchangeCommands: 다섯 유료 구매/교환을 기존 L1InventoryTransaction의 단일 receipt로 처리. DB 확정 후 성공 메시지/HTML. 기존 비용, 보상 ID와 수량, 유효 명령 유지. 샤르나 30 미만은 이전 rewardId=0 경로 차단; sharna5를 실패 페이지로 사용. 기존 미인식 명령 기본 1개 구매는 호환을 위해 유지. 보상 생성 실패 또는 준비/확정 실패 시 재료 차감과 성공 효과 차단. 실제 ItemTable 설정 및 기존 storeItem과 receipt의 merge/초기화 차이는 실제 서버 검증 필요.
- NpcExperienceCommands: 마빈 a/c/d의 세 재료 및 아이템 보상, b의 무료 지급을 receipt로 연결. b의 기존 70레벨 조건과 재지급 정책 보존. 기존 경험치 계산 및 사운드 순서는 수정하지 않고 확정 후 실행. 경험치 자체는 durable receipt에 포함되지 않아 확정 직후 장애 시 경험치 복구는 미완료.
- NpcTravelCommands: 에스메랄다 16개 목적지의 2차원 정적 배열 재사용. 기존 좌표와 맵, 8초, 랜덤 선택 방식 유지.

## 검증
- test_purchases.py: 실제 수정 메서드 추출, 원본 샤르나 코드와 문자 65,536개 및 레벨 경계 대조, 원본 목적지/분기 순서/확률 수치와 정적 대조. 신규 모의 assertion 162055개 통과. receipt의 commit/rollback/failure는 typed stub 모형이며 실제 DB나 실제 CIO를 실행한 검증이 아니다.
- 직전 N7 시장/용병/반지 182897, 정령력/퀘스트/레이드 156425 assertion 재실행 통과.
- Java 21 전체 1928개 구문 검사 오류 0. javac --release 21 --enable-preview -proc:none -Xmaxerrs 10000 -Xmaxwarns 10000에서 531 errors/5 warnings. 직전 전체 타입 컴파일 로그와 byte-identical. 외부 의존성 누락으로 전체 타입 컴파일 성공이 아니다.
- 신규/삭제 생산 Java 없음. 네트워크/CIO/CacheD/NpcServer/일반 아이템 처리 변경 없음.

## 미완료
실제 DB, 인게임, 장애 복구, Heap/JFR 미검증. 나머지 직접 실행부(그림자/마족 신전 퀘스트, 오만 이동, 강화 교환, 바닥 지급, 이벤트 예약), XML 복합 액션 continuation, TalkChannelCache 대화 채널 관리 및 NPC 이벤트 구조는 후속 대상으로 유지.
