# RKern 아이템 139~142차: CacheD 영속 거래 완료 시점 감사

## 기준

- `RKern_ItemOnly_Enchant_Pet_Wand_MultiPhase136_138_FULL_SRC_20261118.zip`의 전체 SRC.
- 136~138차에 반영된 강화 결과 후처리, 펫 소환권 검증, NPC 소환 지팡이 수정은 **그대로 유지**.
- 변경 생산 코드는 `src/l1j/server/server/model/item/function/` 내부 **14개**만 해당.
- 이 ZIP의 생산 변경 외에, 136~138차 검사 스텁에 누락됐던 `getInventory`와 `getItemId` 메서드만 검사 파일에서 보정.

## 139차: 단일 소모/효과 커밋 경계

`QuestRepairScrollUse`, `ExperienceRecoveryConsumables`, `CombatBuffConsumables`, `BlessingConsumables`, `UnlistedMagicScrolls`, `WorldDestinationItems`.

- `UseDurableConsume` 또는 `UseDurableRepair` 제출 직후 `COMPLETE`를 반환하던 경로는 `STOP`으로 변경.
- `ItemActionCompletion.finishCommittedConsumable`을 성공 커밋 콜백의 `finally`에 배치하여 조기 재사용 시간 기록을 방지.
- 경험치 상자는 MP 회복을 안내 메시지보다 먼저 적용하며, 결과 메시지·효과음은 별도로 오류 처리.
- 숫돌은 AC 변경 후 상태·결과 메시지 예외를 따로 처리.

## 140차: 조각·재료 교환/강화

`QuestEarringCollection`, `QuestVoucherExchange`, `GemConversionItems`, `QuestTreasureFragmentCombine`, `QuestSummoningFragmentCombine`, `DollPotentialEnhancementScrolls`.

- 재료 2개 차감과 보상은 기존 CacheD 원자적 거래를 그대로 이용.
- 거래 제출 즉시 `COMPLETE` 대신 `STOP`; 커밋 성공 시에만 `finishCommittedConsumable` 실행.
- 대상 오류/소유권 실패에서도 완료 처리를 건너뜀.
- 커밋 후 메시지 실패가 확정된 보상을 거래 실패로 잘못 해석시키지 않도록 처리.
- 조합 확률·결과 보상·원래 아이템 ID는 변경하지 않음.

## 141차: 다중 재료·충전형 거래

`QuestAncientBookCombine`, `FishingUtilityItems`.

- 고대 주술서 4종 → 연금술사의 돌 거래 완료 처리를 성공 콜백으로 이동.
- 낚시 릴 신규 교환과 100회 충전은 커밋 뒤에만 사용 완료 처리.
- 미변경: 낚시 시작 자체는 기존 동기 COMPLETE 경로를 그대로 유지.

## 142차: 실패 분기·회귀 검증

- 출력을 제외한 상태 변경 예외는 덮어쓰지 않음.
- 패킷 출력 실패만 별도로 로깅하고 콜백 완료를 보장.
- 변경된 생산 Java 8개 클래스 메서드의 본문을 *수정 없이* 타입 스텁과 함께 `javac --release 21`로 컴파일, 커밋/롤백/메시지 장애 등 **79개 검사 조건 통과**.
- `test_scope_and_commit.py`로 변경 대상의 즉시 완료 반환·커밋 콜백 유무 정적 검사.
- 원래 136~138차 검증 스텁에서 현재 생산 `PetWithdrawalItems`의 소유권 확인에 필요한 `getInventory`/`getItemId`가 빠져 컴파일 실패하던 테스트 구성 오류를 보정함. 생산 코드는 이에 맞추어 바꾸지 않음.

## 검증 범위의 한계

- Java 21 전체 SRC 구문 파싱은 가능하나 **전체 의존성 타입 컴파일은 아직 확인되지 않음**.
- Java 거래 실행 검사는 독립 스텁/지연 콜백 시뮬레이션이며, 실제 CacheD/MySQL·로그아웃/재접속·부하 테스트를 대체하지 않음.
- 일반 강화의 주문서 소모/장비 결과 완전 원자성, 펫·소환 월드 효과의 DB 경계, 고빈도 HP 포션과 카드 DB↔메모리 재동기화는 남은 문제.
- 운영 서버 적용 전 실제 MySQL 장애·인게임 검증이 필요.
