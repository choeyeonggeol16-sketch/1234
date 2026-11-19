# RKern 아이템 164~168차 — 특수 강화 성공·실패 영속 거래 통합

기준: `RKern_ItemOnly_Enchant_Completion_MultiPhase159_163_FULL_SRC_20261118.zip` (163차). 2026-11-18.

## 변경 구조

- **164차:** `L1InventoryTransaction.changeEnchantLevel` 도입. 강화 대상 아이템의 원본 객체·사전 강화 레벨을 검사한 후, *실제 인벤토리를 선변경하지 않고* 준비 이미지의 강화값만 +1 한다. 현재 장착된 대상도 강화 가능하며 템플릿·장착 상태 변경은 금지한다.
- **165차:** `ItemReceiptOperations.UseAtomicEnchantSuccess` 도입. 강화 주문서 한 장의 차감과 강화 대상 아이템의 +1을 하나의 기존 CacheD receipt로 제출한다. 오래된 dirty 컬럼은 FIFO 순서로 미리 제출한다. SQL 실패/롤백이면 양쪽 메모리 상태를 변경하지 않으며 성공 보너스를 실행하지 않는다. 기존 CIO 소유 실행 정책과 `CDB.RequestUseItem`을 재사용한다.
- **166차:** 무기 특수 강화 주문서 `68076`의 성공 분기를 위 거래로 전환하고 실패 분기는 영속 주문서 소비 뒤 실패 안내를 실행한다. 기존 확률/RNG/GM 메시지 의미를 유지한다.
- **167차:** 방어구 특수 강화 `98077`, `68077`, `3000380`, `4000518`, `3000518`의 성공·비파괴 실패 처리, `3000517`의 성공 처리를 같은 거래 방식으로 전환한다. `3000517` 파괴 실패 처리는 기존 두 재료 동시 차감 receipt를 보존한다.
- **168차:** `EnchantmentOutcomeEffects.SuccessEnchantCommitted`가 커밋된 강화값을 다시 저장·증가시키지 않고 장착 중인 AC/MR 후처리와 알림만 실행하도록 분리했다. 기존 `SuccessEnchant` 호출자는 기존 영속 저장 경로를 유지한다. 아이템 사용시각/딜레이는 receipt 커밋 후에만 반영한다.

### 생산 Java 변경 (5개)

1. `src/l1j/server/server/model/L1InventoryTransaction.java`
2. `src/l1j/server/server/model/item/function/ItemReceiptOperations.java`
3. `src/l1j/server/server/model/item/function/WeaponEnchantScrollResolution.java`
4. `src/l1j/server/server/model/item/function/ArmorEnchantScrollResolution.java`
5. `src/l1j/server/server/model/item/function/EnchantmentOutcomeEffects.java`

기존 NPC·네트워크·방송·CacheD 공통 코드/스레드/큐는 변경하지 않는다. 위 7종 주문서는 사용 빈도가 낮은 영속 강화 거래로, 매 공격·매 물약 사용 hot path에 신규 DB 왕복을 추가하지 않는다.

## 검증

`bash docs/item_multi_phase164_168_20261118/run_tests.sh` 를 실행한다.

- 실 생산 무기/방어구 분기 + Java21 typed mock 회귀: 무기 **288**, 방어구 **336** 조건
- 실 생산 `UseAtomicEnchantSuccess`, `changeEnchantLevel` 메서드 추출 + Java21 typed mock: **21** 조건
- 실 생산 `SuccessEnchantCommitted`와 레거시 SuccessEnchant 컴파일·실행: **127** 조건
- 기존 아이템 완료 타이밍: **1,980** 조건
- 기존 강화 결과·롤백: **13 + 14** 조건
- 이전 영속 소비·교환 경로: **12** 조건 (이전 fixture에 없는 신규 강화 메서드만 시험용 컴파일 사본에서 제외; 신규 메서드는 별도로 테스트)
- JDK21 Compiler API 전체 생산 Java **1,923개 구문 파싱**
- ZIP CRC 및 변경 5개 외 원본 항목의 byte-identical 대조는 패키지 생성시 수행.

모의 DB 성공/롤백/패킷 예외 주입 및 Java 메서드 타입 검증이다. 전체 프로젝트 의존성 컴파일과 실제 MySQL·Windows·다중 세션·인게임 검증은 별개이며 **실시하지 않았다**. 역사적 테스트 중 옛 직접 삭제/성공 즉시 반환을 전제하는 fixture는 새 회귀로 대체한다.

## 남은 문제

- 일반 강화 주문서 전체의 성공/실패/장비 파괴를 포함한 단일 receipt 완성은 미완료. 이번 변경은 명시한 7개 특수 강화 주문서 분기만 대상이다.
- DB 커밋 이후 AC/MR 효과 적용 자체가 실패하면 기존 owner quarantine이 작동하며 자동 재구성은 미완료.
- 카드 DB↔메모리 자동 재동기화, 펫 월드 생성 실패 보상, 고빈도 HP 포션 영속성, 실제 통합 DB 검증은 미완료.
