# RKern 아이템 31차 — 신규 영입 스타터 패키지 원자성

기준: `RKern_Item_Phase30_RegressionAudit_FIX_FULL_SRC_20261118.zip`.

## 원인 및 수정

- 기존 `RecruitStarterKit.clickItem`은 `checkItem → consumeItem(3000366,1) → 직업별 봉인템()` 순서로 라이브 아이템을 수정했다. 이후 `checkAddItem` 실패, 아이템 템플릿 누락, DB 쓰기 실패 시 이미 소비된 패키지와 먼저 지급한 장비를 하나의 거래로 취소할 수 없었다.
- 이번 수정은 기존 `User.CheckAndSetPendingUseETCItem` / `L1InventoryTransaction` / `CDB.RequestUseItem` / `CacheD` receipt 경로를 그대로 재사용한다. 소비할 **원본 인벤토리 객체가 현재도 같은 객체인지** 확인하고 `tx.debit(source,1)`과 모든 `tx.grant(detachedReward)`를 **한 건에 준비**한다. 단일 커밋 확인 후 기존 CIO owner에서만 장비를 적용하고 메시지를 보낸다.
- `ItemTable.createItem()`이 월드에 미확정 객체를 등록하던 동작을 없애고 `new L1ItemInstance(template,count)`로 detached image를 준비한다.
- 각 직업의 기존 239개 `봉인템` 호출에서 itemId, count, enchant, blessing, attr 및 지급 순서를 보존했다. 식별 상태는 기존 헬퍼가 최종적으로 항상 `true`로 설정하므로 그대로 유지한다.
- 실패 시 이미 준비했던 다른 보상이 부분 지급되지 않고 소모도 확정되지 않는다. 직업 식별 불가능한 경우도 내용 없는 패키지를 소모하지 않는다.
- 메모리 관점: 불필요한 미확정 L1World 엔트리 생성을 피하지만 거래 준비 이미지/배치 비용은 추가되므로, 이번 차수에서 전체 힙 할당이나 성능이 줄었다고 주장하지 않는다. 이미 사용 중인 기존 거래 구조만 사용하며 새 스레드/큐/클래스는 없다.

## 검증

- Java 21 전체 `src` 1,926개 소스 구문 검사 0건.
- 신규 실제 생산 클래스 2개를 테스트용 Java 21 게임/CacheD 모형에 연결하여 타입 컴파일 + 실행 521건. 8직업 전체 조합 256건, 각 직업 지급 중간 단계 239건, 템플릿 누락·DB 롤백 16건, capacity rejection 8건, stale/wrong-ID 2건 = 521건.
- 239개 원본 보상 호출의 인수 및 순서가 정확히 일치한다는 정적 검사 통과.
- `item_domain_phase30_audit_20261118/test_route_differential.py`, 30차 registry·format·scope, 19차 빙수·이동물약, 20차 룬·타워·대상검사, 16차 보석, 11차 무기강화 타입 검증 통과.
- 방송 114차 오류 주입 및 5,000 수신자 모의검사 통과. 생산 방송 코드는 변경 없음.
- 과거 19차 스타터 테스트는 **즉시 지급/선소모를 기대**하므로 이번 안전성 수정에는 적용하지 않고, 새 31차 원자성 테스트로 대체했다.

## 남은 검증

- 전체 게임 의존성 타입 컴파일(외부 JAR 누락), 실제 클라이언트·인벤토리 UI, 실제 MySQL/CacheD/ACK 손실/재접속/재시작 장애 주입은 수행하지 못했다.
- 변동 사항: 기존에는 중간 지급 실패 시 일부 성공·패키지 선소모가 가능했으나, 이번에는 **전체 거부·패키지 보존**으로 정책이 달라진다. 거래 안전성 개선을 위한 의도된 변경이다.
