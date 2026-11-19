# 아이템 155~158차 누적 수정 보고 (2026-11-18)

기준: `RKern_ItemOnly_ReceiptReplay_PostCommit_MultiPhase146_154_FULL_SRC_20261118.zip` (154차 전체 SRC).
실제 생산 Java 변경 파일 4개, NPC AI·네트워크·방송 코어·CacheD 공통 파일 무변경.

## 155차 — 할파스 룬 실패(3000517) 두 아이템 원자적 파괴
- `ArmorEnchantScrollResolution.java`: 성공 확률과 기존 강화 성공 로직 유지.
- 실패 시 대상 룬과 주문서를 각각 `removeItem` 하던 분기를 기존 `UseDurableConsumeTwoInput`(CacheD 단일 receipt)로 변경.
- 커밋 이전에는 둘 다 제거하지 않으며 롤백 시 두 아이템을 보존. 커밋 완료 후에만 소모 완료·실패 메시지와 GM 확률 안내 실행. 출력 오류는 게임 경제 처리 오류로 오인하지 않음.
- 일반 강화 전체의 원자성을 구현한 것은 아님. 특히 해당 주문서 성공 분기는 기존 비원자적 경로 그대로임.

## 156차 — 강화 성공/초기화 시 결과 저장과 출력 분리
- `EnchantmentOutcomeEffects.java`: `SuccessEnchant`가 `updateItem`(클라이언트 전송)을 실행한 다음 전체 인벤토리 `saveInventory()`를 두 번 호출하던 순서를 변경.
- 강화값을 해당 아이템 `COL_ENCHANTLVL`로 한 번 저장 요청한 뒤, 별도 전송을 시도. 단순 출력 실패는 기록하고 나머지 능력치/안내 처리 유지.
- `FailureEnchant`의 특정 장비 강화값 초기화에도 동일한 저장·출력 순서 적용.
- 강화 로그 출력 후처리 오류가 상태 알림을 막지 않도록 분리.
- 인벤토리 transfer-pending 거절 예외는 숨기지 않음.
- 이는 비동기 dirty-save 경로이며 강화 주문서 소모와 장비 강화의 **동일 트랜잭션 원자화**가 아님.

## 157~158차 — 아이템 영속 소모/교환 실패 통지 보호
- `ItemTransactionActions.java`: 단일·이중 소모 receipt의 실패 콜백 및 제출 예외 안내를 `failureNotice()`로 통일. 연결 끊김 등 출력 실패는 기록하고 콜백에서 재예외를 내지 않음.
- `ItemReceiptOperations.java`: 영속 교환·이중재료 교환 실패 메시지와 예외 안내 경로도 같은 방식 적용.
- 영속 거래 SQL·커밋·롤백 정책 자체는 변경하지 않음.

## 검증 및 한계
- 수정 생산 `EnchantmentOutcomeEffects` Java 21 typed 실행 13조건, `ArmorEnchantScrollResolution` Java 21 typed 실행 18조건, receipt helper typed 실행 12조건 통과.
- 154차 기존 인벤토리 커밋 replay 62, 후처리 콜백 81, 145차 펫 3,200, 143~144차 소환 4,400 검사 통과.
- Java 21 전체 SRC 1,923개 구문 검사 0 오류.
- 실제 MySQL 장애·전 인게임·전체 의존성 타입 컴파일·고부하 테스트는 수행하지 않음.
- 일반 강화 전체 원자성, 카드 DB↔메모리 자동 재동기화, 커밋 후 펫 월드 생성 실패 복구, HP 포션 고빈도 영속성 경로는 잔여 작업.
