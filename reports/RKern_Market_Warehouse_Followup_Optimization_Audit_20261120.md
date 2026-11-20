# RKern 상점·창고 개선 작업 지시서

기준: [cba4ccca](https://github.com/choeyeonggeol16-sketch/1234/commit/cba4ccca18bdff1262716fe9c72c1a07f340f8c5) (2026-11-20). 최신 브랜치 변경을 보존하고 아래 작업만 수행한다. 네트워크, 방송, NPC AI, 전투 및 무관한 코드 변경 금지.

## M01. 혈맹창고 입출금 이력 기능 삭제 (필수)

사용자 요청: 혈맹창고 부가 이력을 DB에 남기지 않는 방향으로 제거한다. 이력 보존, 신규 감사 시스템, 별도 파일 로그 등 대체 기록 기능을 추가하지 않는다.

- WarehouseTransactions.java: clanAudit(), 이력 전용 audited 배열, ID 발급, 문자열 생성, 콜백, CacheD 감사 작업 제출, clan_warehousehistory INSERT 제거. 단일 혈맹창고 입출금도 불필요한 배치 배열을 생성하지 않도록 개선.
- SC_ClanWareHouseHistory.java: 이력 SELECT 및 DTO 생성 제거. 요청 진입점과 호출부를 확인해 이력 UI 요청에는 기존 S_EVENT/117 형식의 0행 응답만 반환하고, 불필요한 조회 경로 제거. 클라이언트 호환 유지.
- 전체 소스 및 SQL 스키마의 clan_warehousehistory 참조 전수 확인. 관련 런타임 INSERT/SELECT/DELETE 및 초기화 의존성 제거. 신규 설치용 스키마에서 불필요한 이력 테이블 정의 제거 가능 여부 확인. 운영 DB의 기존 테이블은 자동 DROP하지 말고 미사용 확인 후 명시적 마이그레이션으로 분리.
- 실제 창고 아이템 소유권·수량·수수료·권한·용량·거래 receipt·CacheD 복구 경로는 변경하거나 제거하지 않는다.
- 완료 조건: 혈맹창고 정상 입출금, 이력 요청 정상 응답, 해당 이력 SQL 0회, 이력 전용 할당 0건. 실제 아이템 거래는 기존 원자성 보존.

## M02. 개인상점 단일 거래 저할당화 (P1)

PlayerShopTransactions.java:59–121의 단일 요청에서도 생성되는 Int2ObjectLinkedOpenHashMap 2개, ListingDebit, Transfer 및 반복 순회 비용을 줄인다.
- size==1의 상품/수량/가격/총액 검증은 로컬 변수와 최소 불변 preimage로 처리. 기존 transaction API와 receipt를 유지하면서 단일 행의 Map/iterator 할당 제거.
- 다중 요청은 필요 시 primitive 배열 기반으로 비교 최적화. 중복 행 합산, 비스택 제약, 동일 아이템/상품 연결, 금액 한도, 기존 처리 순서 유지.
- 비동기 커밋에 필요한 스냅샷 수명 보장. 조기 풀 반환 금지. 불필요한 신규 클래스, 전역 풀, ThreadLocal, 워커, 큐 추가 금지.

## M03. 개인상점 재고 SQL 절감 (P1)

MJDShopStorage.consumeListing(): 현재 상품별 SELECT FOR UPDATE와 UPDATE/DELETE 각각 수행.
- (objid,item_objid) 유일성 및 인덱스, NULL, 중복 기존 행, 각 필드 비교 의미 확인.
- 전체 preimage(item_id,count,price,type,enchant,iden,attr)를 조건에 포함한 단일 UPDATE/DELETE로 교체 가능한 경우에만 적용. affected rows == 1을 강제. 유일성/검증 의미가 보장되지 않으면 기존 SELECT 유지.
- 다중 상품은 기존 receipt 내 prepared statement 재사용, 안정적 잠금 순서, JDBC batch 가능성 검토. 행별 성공 판정 불가 시 안전한 기존 방식 유지.
- 아이템, 아데나, 상점 재고 원자성 유지. 별도 비동기 DB 작업으로 분리 금지.

## M04. NPC 상점 주문 저할당화 (P1)

MerchantTransactions.java:64–123 및 L1ShopBuyOrderList.java:37–132.
- 단일 구매·판매 주문의 불필요한 ArrayList/주문 래퍼/임시 배열 생성 제거. 기존 소비자 확인 후 최소 변경.
- 상품 목록 재로드 시 표시된 행과 실제 거래 상품 및 로그 값의 일관성을 검증하고 결함 확인 시 처리 시점의 불변 상품 값 고정.
- 비스택 개별 아이템 생성 의미, MAX_SIZE 조기 검증, 세금, 특수 화폐, 경마, 낚시, 동적 상점 및 기존 가격 정책 보존.

## M05. 다중 요청 및 창고 배치 비용 절감 (P2)

MerchantTransactions.repeated(), WarehouseTransactions.java:108–134.
- 최대 255행 중복 검사에서 소량 요청은 기존 선형 경로 유지, 대량 요청에서 실측 이득이 있을 경우만 primitive 중복 검사 도입. 입력 순서와 실패 우선순위 보존.
- 단일 일반·혈맹창고 거래에서 불필요한 배치 배열 및 이력 콜백 제거. 다중 입출금의 배치 원자성 유지.
- 수량, 권한, 수수료, 만료, 용량, 예약 gate 및 기존 clamp 정책 보존.

## M06. 개인상점 완료 처리 안전성 (P0)

PlayerShopTransactions.java:55–139 및 L1InventoryTransaction.java:695–743.
- 커밋 이후 상점 종료/재개설, 구매자·판매자 로그아웃, 객체 재사용, 목록 identity/버전 교체, 완료 콜백 실패를 검증. 실제 결함만 수정.
- 이미 확정된 거래 재실행 금지. 재접속 시 DB authoritative 값과 메모리 재고 일치. 기존 양쪽 인벤토리 예약 및 quarantine 유지. 소유권 경합 검증 없이 락 제거 금지.

## 완료 검증

- Java 21 전체 타입 컴파일, 기존 상점/창고 검증 4개 및 신규 회귀.
- 실제 MySQL 동시 구매/혈맹창고 공유 경합, 실패/롤백, ACK loss, COMMIT 직후 연결 종료, 재시작, stale view 및 상점 종료/재개설.
- 단일/다중/중복/위조/수량 경계, 특수 화폐, 비스택, 최대 용량 및 수수료 검증.
- JFR 할당 bytes/op, objects/op, CPU/op, CIO p95/p99, CacheD 대기, SQL 실행 수 전후 비교. 실패한 최적화는 기본 경로에 채택 금지.
- 아이템/아데나 보존, DB와 메모리 일치, 중복 지급/차감 및 gate 누수 없음, 혈맹창고 이력 SQL 제거 확인.

실행 순서: M01 삭제 우선, M06 안전성 확인 후 M02–M05 최적화, 전체 검증. 기존 실행 구조와 거래 안전성을 유지한다. 본 문서는 정적 검토 기반 작업 지시이며 실제 코드 수정·테스트 실행·성능 측정을 완료한 보고서가 아니다.
