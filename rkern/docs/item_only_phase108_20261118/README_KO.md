# RKern 아이템 108차 — 카드 합성 커밋 후 UI 선택 충돌의 안전한 처리

기준: `RKern_ItemOnly_CardSynthesis_Selection_Consistency_Phase107_FULL_SRC_20261118.zip`.

**생산 변경은 1개:** `src/l1j/server/server/model/item/Card/CardAlchemyReceipt.java`.

## 원인 및 수정

107차는 카드 합성 커밋이 완료된 뒤, 계정의 카드 선택 슬롯(A/B/C/D)이 다른 세션에 의해 바뀐 것만으로 `IllegalStateException`을 발생시켰습니다. `L1InventoryTransaction`의 커밋 후 완료 경로는 해당 예외를 격리(quarantine)하여, 재화·보상은 DB에 확정되었는데 사용자가 거래 진행을 계속할 수 없는 문제가 있었습니다.

108차는 **선택 UI만 변경된 경우**에 한해, 카드 차감·보상·아데나 등 이미 확정된 거래 결과를 정상 반영하고 이후 선택값을 지우지 않습니다. 기존 DB 측 선택 슬롯 4개 초기화는 유지됩니다. 카드 선택값은 현재 코드에서 별도 저장 호출이 없는 임시 UI 상태이며, 리로그 시 최신 선택 UI가 유지된다고 보장하지 않습니다.

반면 카드 **수량의 메모리 preimage가 변경된 경우** 또는 계정 세션이 바뀐 경우에는 기존 예외 및 거래 격리를 유지합니다. 이런 경제 상태 불일치는 임의로 덮어쓰거나 자동 재처리하지 않습니다. 즉, 이 수정은 커밋 후 **UI-only 경합 완화**이며 모든 계정 상태의 자동 재동기화를 구현한 것은 아닙니다.

별도 worker/queue, 새 락/클래스, 일반 네트워크/CIO/CacheD 공통 로직, NPC AI, 방송 코드는 수정하지 않았습니다. SQL 카드 차감 조건·비용·보상 확률 및 RNG 호출 순서는 그대로입니다.

## 검증

- `docs/item_only_phase108_20261118/test_108.py`: production `CardAlchemyReceipt.java`와 3종 합성 호출자 Java 21 타입·실행 모의 시험 25,371 checks 통과. 3티어×4슬롯=12 UI 단일 슬롯 경합, 전체 슬롯 동시 변경 3회, 경제 카드 수량 오류 3회 확인.
- 103차 카드·수호성 등록 테스트: 2,498 assertions 통과.
- 104차 네 확인 응답 테스트: 65 assertions 통과.
- 105차 메뉴 가드 테스트: 410 Java 조건 + 중앙 메뉴 2개 통과.
- 106차 자동 선택 독립 테스트: 16,019 checks 통과. 원본 106차 전체 스크립트는 103~105차를 다시 반복하며 시간 제한이 걸려, 반복 구간을 별도로 실행했습니다.
- Java 21 syntax-only parser: production Java 1,923/1,923, 오류 0.

**미검증:** 전체 의존성 타입 컴파일, 실제 MySQL/CacheD ACK 손실·동시 계정 세션·재접속·서버 재시작·인게임 검증. 이 자료만으로 5천명 운영 안전성을 보증하지 않습니다.
