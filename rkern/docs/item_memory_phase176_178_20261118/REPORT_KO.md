# 아이템 176~178차 — L1InventoryTransaction 할당 최적화

기준: 아이템 175차 최신 전체 SRC (`RKern_ItemOnly_AtomicProtection_Enchant_MultiPhase169_175_FULL_SRC_20261118.zip`).

## 변경

실제 생산 Java 변경 파일은 `src/l1j/server/server/model/L1InventoryTransaction.java` 한 개.

1. 거래 준비 이미지가 기존 인벤토리 행의 **소유자를 유지**하고, **아이템 템플릿과 단위 무게가 동일**하며, **수량이 증가하지 않는 경우**, 전체 인벤토리 capacity 검증 및 `TIntLongHashMap` 생성 생략. 해당 거래에서 슬롯 수, 총 무게, 템플릿별 총 수량은 증가하지 않는다. `entries`가 빈 SQL-only 거래도 생략. 보상 지급·거래/이관·템플릿/무게 변경·수량 증가에는 기존 검증 그대로 실행.
2. 커밋 준비용 `ArrayList`/backing-array 대신 `Entry[]` 한 개로 정렬. 비교자는 클래스 초기화 시 재사용하고, 아이템 ID 오름차순 잠금 순서는 유지. SQL preimage·원본 prepared image의 복사·제출/콜백/격리 코드는 수정하지 않음.
3. `grant(int, count)`에서 내부에서 새로 만든 보상 이미지의 불필요한 재복사 제거. 비중첩 여러 개 지급 시 각 새 행을 한 번만 복사하도록 변경. 외부 입력 `grantCopy(...)`는 그대로 방어적 복사를 수행. 외부 아이템 가변 참조나 영속 커밋 이미지 스냅샷은 종전처럼 안전하게 유지.

**제약:** 기존 인벤토리 전체에 이미 허용량을 초과한 이상 상태가 있더라도, 이번 거래가 상한을 증가시키지 않을 경우 상한 검사를 재실행하지 않음. 정상 인벤토리 불변식을 전제로 한 증분 용량 검증이며, 전체 인벤토리 정합성 진단 기능을 대체하지 않음. 롤백, 복제 및 DB preimage 검증은 그대로 유지.

## 검증

`python3 docs/item_memory_phase176_178_20261118/test_capacity_java.py` 및 `python3 docs/item_memory_phase176_178_20261118/test_grant_java.py` 로 재현 가능.

- 변경된 생산 소스에서 `validateCapacity`, `capacityCannotIncrease`, `addProjectedAmount`, `weight`를 추출, 175차 원본의 `validateCapacity`와 함께 실제 JDK 21 typed stub으로 컴파일·실행.
- 30,000건 유효한 랜덤 인벤토리 및 수정 거래 시나리오에서 기존/신규 승인 결과 대조.
- 수량 차감 fast path 맵 생성 0·전체 인벤토리 순회 0 보장, grant·transfer·템플릿/무게/수량 증가 시 전체 검증 복귀 확인.
- 커밋 아이템 ID 정렬 순서 동일성 검증. **총 99,799개 조건**.
- 실제 `grant`/`grantCopy` 생산 메서드를 변경 전후 추출해 Java 21 타입 컴파일, ID·수량·준비행·외부 입력 불변성 및 복사 횟수 **14,320개 조건** 비교.
- 기존 175차 회귀: 보호 주문서 710, 무기 주문서 1,536, 무기 특수 288, 방어구 특수 336, 강화 거래 21, 장비 효과 127, 영속 소모 12, 완료 처리 1,980, 강화 복구 14개 모두 통과. JDK 21 전체 생산 Java 1,923개 구문 오류 0.
- 이외 기존 175차 회귀 실행 결과 및 Java 전체 파싱은 실행 로그 참조.

실제 프로덕션 할당률(JFR), GC, MySQL, Windows IOCP 및 장기간 인게임 동접 검증은 별개로 남는다.
