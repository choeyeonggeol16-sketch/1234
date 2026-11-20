# RKern 상점·창고 후속 개선 보고서

- 기준: [cba4ccca18bdff1262716fe9c72c1a07f340f8c5](https://github.com/choeyeonggeol16-sketch/1234/commit/cba4ccca18bdff1262716fe9c72c1a07f340f8c5), 2026-11-20
- 범위: NPC 상점, 개인상점, 창고 및 거래 전용 CacheD 연동부. 네트워크, 방송, NPC AI, 전투 및 무관한 코드 변경 금지.
- 현황: 29개 변경 파일, +1871/-2560줄. CS_Result 역할 분리, primitive PlayerShopView, 개인상점 양쪽 인벤토리와 character_shop 재고의 원자 처리, 창고 배치 처리 유지.
- 검토 방식: GitHub 기준 정적 소스 검토. 신규 검증 스크립트 4개 확인. 직접 실행, 전체 타입 컴파일, 실제 MySQL 및 성능 측정 미실시. 아래 개선은 구조적 비용에 대한 제안이며 실측 병목으로 단정하지 않는다.

## 우선순위

| 작업 | 우선도 | 내용 |
|---|---|---|
| M01 | 정책 결정 우선 | 혈맹창고 부가 이력 제거 또는 제한 보존 |
| M02 | P0 | 개인상점 커밋 후 반영 및 상점 수명 경합 검증 |
| M03 | P1 | 단일 개인상점 거래 할당 제거 |
| M04 | P1 | 재고 SQL 왕복 감소 |
| M05 | P1 | NPC 상점 주문 저할당 및 가격/목록 일관성 |
| M06 | P2 | 다중 행 검사, 창고 배치 비용 |
| M07 | 필수 | 실 DB 장애, 회귀 및 부하 검증 |

## M01. 혈맹창고 이력 — 불필요하면 제거

근거:
- WarehouseTransactions.java:136–143, 214–267: clanAudit()는 별도 배열, 로그 ID, 문자열 및 콜백을 생성하고 입출금 확정 뒤 별도 CacheD 작업으로 clan_warehousehistory에 배치 INSERT한다.
- SC_ClanWareHouseHistory.java: sendAsync/create/writeImpl 경로에서 clan_id별 이력을 조회한다. SQL에 LIMIT가 없으며 모든 HistoryNode를 생성하고 패킷에 기록한다. S_EVENT subopcode 117, 행 개수 및 행 데이터 형식을 사용한다.
- 별도 이력 INSERT 실패나 커밋 직후 프로세스 종료는 실제 아이템 이동을 되돌리지 않지만 이력 누락은 발생할 수 있다.

사용자 정책: 부가 이력이 불필요할 수 있다는 지적. 이력을 무조건 거래 receipt에 편입하거나 새 영속화 계층을 만드는 것은 권장하지 않는다.

권장 A (이력 기능을 사용하지 않는 경우): clanAudit 호출·이력 전용 배열/로그 ID/문자열/콜백과 INSERT 제거. 조회 패킷 요청 실제 호출부를 전수 확인하여 UI가 요구할 경우 기존 형식의 0행 응답을 반환하도록 한다. 게임 내 혈맹창고 입출금, 수수료, 권한, 소유권 락, 아이템 receipt 및 복구 경로는 보존한다. 타 참조 및 운영자 기능 확인 전 테이블 DROP 금지. 테이블 삭제는 별도 승인/백업 후 결정.
권장 B (이력 기능 유지 시): 보존 기간/최대 행 수를 운영 정책으로 결정, 조회 LIMIT 및 안정적인 최신순 인덱스 검토, 패킷 최대 크기 준수. 재시작 후 이력 누락까지 금지하는 정책인 경우에만 기존 거래 SQL/receipt에 감사 행을 포함하거나 기존 복구 수단으로 보장. 별도 스레드나 신규 전역 큐 금지.
현재 정책 확정 전 소스 수정 금지. A/B 모두 인벤토리 및 창고 상태의 영속성과 거래 안전성을 약화시키지 않는다.

검증: 개인/혈맹/요정/부가 창고, 권한/수수료, 저장 공간 부족, DB 롤백/재시작, 이력 조회 UI, 이력 비활성 시 해당 테이블 SQL 0회.

## M02. 개인상점 커밋 후 반영 및 수명 경쟁

근거: PlayerShopTransactions.java:55–139에서 양쪽 인벤토리 transaction을 예약, listings monitor 아래 스냅샷/재고 확인, tx.sql 후 commit 콜백에서 재고 반영. L1InventoryTransaction.java:695–743에서 durable commit, CIO 반영, gate 해제와 실패 quarantine 수행. 콜백은 목록 identity가 변경되면 예외를 던진다.

확정 이후 상점 종료/재개설, 판매자·구매자 로그아웃, 객체 재사용, 캐시와 목록 버전 교체, 완료 콜백 실패 시 실제 적용/복구 경계를 끝까지 추적한다. 현재 구조에서 보호되고 있는 경로를 먼저 검증하고 실제 결함이 확인된 경우만 최소 변경. 이미 확정된 거래 재실행 금지, 오래된 목록에 대한 반영 금지, 재접속 시 DB authoritative 결과와 재고 일치. 양쪽 소유권 예약 및 기존 quarantine 유지. 타 샤드 상태 변경은 기존 소유권·거래 gate를 검증하고 임의 새 큐를 추가하지 않는다.
검증: 동시 2고객 동일 상품, 종료/재개설, stale view, ACK loss, COMMIT 직후 disconnect, post-commit 예외, 재시작. 아이템과 아데나 보존, 재고 일치, gate 미잔류.

## M03. 개인상점 단일 요청 할당

근거: PlayerShopTransactions.java:59–121. 단일 요청에도 Int2ObjectLinkedOpenHashMap 두 개, ListingDebit, copyListing, Transfer 생성 및 반복 순회.
- size==1에 기존 tx API를 쓰는 직접 경로 검토. 상품/아이템/수량/총액을 로컬 변수로 검증하고 기존 preimage 스냅샷 및 커밋 수명 보존. 단일 행 전용 Map, iterator 제거 목표.
- 다중 요청은 기존 순서를 유지하면서 primitive 배열 또는 기존 소규모 구조와 비교. 반복 행 합산, 동일 아이템 중복, 다른 listing 연결, nonstackable 제약 보존.
- 비동기 receipt에서 참조하는 값은 절대 조기 반환/재사용하지 않는다. 공용 객체 풀이나 ThreadLocal retained heap을 근거 없이 추가하지 않는다.
- Map 제거 때문에 금액·재고·인벤토리 사전 검증을 제거하거나 단일/다중 경로의 정책을 중복 구현하지 않는다.

## M04. 재고 SQL 왕복

근거: MJDShopStorage.consumeListing(): SELECT ... FOR UPDATE로 item_id/count/price/type/enchant/iden/attr 및 중복 행 확인, 조건부 UPDATE 또는 DELETE, affected rows==1 검증. PlayerShopTransactions가 각 debit에 호출.
- DB 스키마의 (objid,item_objid) 유일성·인덱스·NULL·자료형 및 기존 중복 자료를 먼저 확인.
- 조건부 UPDATE/DELETE 단일 문장으로 preimage 전체를 비교하는 방법 검토. 유일성, 비교/변환 의미, affected rows, deadlock/lock order, 복수행 검증 보존 시에만 적용. 조건이 충족되지 않으면 기존 SELECT 유지.
- 다중 상품은 동일 receipt 안에서 정규화된 lock order, prepared statement 재사용 또는 JDBC batch 검토. 성공 행별 판정을 유지하고 불명확한 SUCCESS_NO_INFO는 확정 근거로 사용하지 않는다.
- 아이템 및 아데나와 character_shop의 원자성 유지. 쿼리 수만 줄이기 위해 비동기 분리 금지.
- 검증: 중복키/부재/변경 preimage, 부분·전체 소비, 동시 구매, 롤백, SQL deadlock, JDBC batch 오류.

## M05. NPC 상점

근거: MerchantTransactions.java:64–123, L1ShopBuyOrderList.java:37–132. 주문 객체, ArrayList, 세금 계산 및 다중 비스택 객체 분리 사용. 구매 완료 로그는 primitive audit 스냅샷의 행 번호를 items 목록에 다시 조회한다.
- 목록 재로드 중 행 번호/가격 변경 가능 여부와 기존 불변 목록 보장을 확인. 실제 문제가 있을 때만 처리 시점의 상품 ID/로그 값 고정 또는 기존 스냅샷 버전 활용. 이미 표시한 가격·교환 규칙과 실제 차감 일치 확인.
- 단일 주문에 불필요한 배열, 목록, 래퍼 할당을 줄이는 방향으로 L1ShopBuyOrderList 소비자 전수 조사. 호환 모드의 비스택 개별 아이템 생성 의미를 유지하고, 상점 세금/특수 화폐/낚시 시간/경마/동적 상점 정책 보존.
- 현재 추가된 비스택 MAX_SIZE 조기 검증 유지. 실제 주문 상한, 총합, 가격 배율, 클라이언트 수량 부호 및 정수 경계 회귀.
- 기존 동작을 바꾸는 임의 세금/수수료/가격 변경 금지.

## M06. 다중 요청 검사 및 창고 배치

근거: MerchantTransactions.repeated(), WarehouseTransactions.java:108–134의 중복 검사에 선형 누적 탐색. CS_Result.java:34에서 요청 크기 최대 255.
- 최대 255행 한정 O(n²) 검사는 소량 거래에서는 오히려 유리할 수 있다. 1/2/8/32/128/255행으로 비교하여 이득이 생기는 구간에만 primitive 중복 검사를 적용. 입력 순서와 결과 메시지 우선순위 유지.
- 단일 일반 창고의 배열 미할당 경로와 배치 거래 원자성 유지.
- 혈맹창고 이력 제거 정책 확정 시 이력 전용 audited 배열과 콜백 제거. 기능 유지 시 최대 응답 크기 및 제한된 메모리 비용 검토.
- CacheD 배치 제출 전후의 수량, fee, 권한 변경, 만료, 용량, owner gate를 실제 경합 조건에서 확인.
- 현재 코드에 있는 clamp 동작 등 게임 정책은 별도 승인 없이 변경하지 않는다.

## M07. 검증 및 채택 기준

기존 검증 경로: rkern/docs/market_warehouse_receipts_20261120/ 의 test_buy_sell_decode.py, test_merchant_order_limit.py, test_player_shop_receipt.py, test_warehouse_receipts.py. 모의 검증에만 의존하지 말 것.

1. 최신 전체 SRC Java 21 타입 컴파일, 기존 회귀 전체 및 신규 실제 메서드 검증.
2. 실 MySQL 두 사용자 및 혈맹창고 공유 경합, 트랜잭션 롤백, DB 중단, commit ACK loss, 재접속 및 재시작.
3. 단일/일괄/중복/위조/만료/비정상 수량, 비스택, 특수 상점/화폐/수수료, 인벤토리 최대 용량 검증.
4. JFR 할당 bytes/op, objects/op, GC, CPU/op, CIO 샤드 지연 p95/p99, CacheD 대기, 상품당 실제 SQL 실행 수, DB 처리량 측정. 동일 JVM/DB/데이터로 전후 비교. 단일 거래 성능을 악화시키지 않고 다중 거래 개선 실측.
5. 아이템·아데나 총량 보존, 재고/인벤토리/창고 DB와 메모리 일관성, 실패 시 중복 지급/차감 없음, 보류 gate 누수 없음이 필수 통과 조건.
6. 소스 수정은 해당 상점 범위로 제한. 새로운 워커/큐/전역 락 도입은 근거와 승인 없이는 금지. 실패한 성능 실험은 기본 경로에 남기지 않는다.

## 참고 코드

- [MerchantTransactions](https://github.com/choeyeonggeol16-sketch/1234/blob/cba4ccca18bdff1262716fe9c72c1a07f340f8c5/rkern/src/l1j/server/server/model/network/core/cs/packet/MerchantTransactions.java)
- [PlayerShopTransactions](https://github.com/choeyeonggeol16-sketch/1234/blob/cba4ccca18bdff1262716fe9c72c1a07f340f8c5/rkern/src/l1j/server/server/model/network/core/cs/packet/PlayerShopTransactions.java)
- [WarehouseTransactions](https://github.com/choeyeonggeol16-sketch/1234/blob/cba4ccca18bdff1262716fe9c72c1a07f340f8c5/rkern/src/l1j/server/server/model/network/core/cs/packet/WarehouseTransactions.java)
- [SC_ClanWareHouseHistory](https://github.com/choeyeonggeol16-sketch/1234/blob/cba4ccca18bdff1262716fe9c72c1a07f340f8c5/rkern/src/l1j/server/server/model/network/core/sc/packet/SC_ClanWareHouseHistory.java)
- [MJDShopStorage](https://github.com/choeyeonggeol16-sketch/1234/blob/cba4ccca18bdff1262716fe9c72c1a07f340f8c5/rkern/src/l1j/server/MJDShopSystem/MJDShopStorage.java)

결론: 혈맹창고 부가 이력은 운영 정책부터 결정하고, 핵심 거래의 확정성은 그대로 보존한다. 개인상점 단일 거래 할당 및 상품별 SQL 비용을 다음 최적화 대상으로 삼는다. 본 커밋은 보고서만 추가한다.
