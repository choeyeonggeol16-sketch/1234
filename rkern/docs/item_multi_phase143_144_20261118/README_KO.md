# RKern 아이템 143~144차: 소환 아이템 영속 차감 및 커밋 경계 수정

기준: `RKern_ItemOnly_ReceiptCompletion_MultiPhase139_142_FULL_SRC_20261118.zip` (142차).

## 143차 — NPC 소환 지팡이(410014, 410015)

- 기존: NPC 생성/월드 등록 → 충전 횟수 메모리 갱신 → 아이템 저장, 사용 완료를 즉시 반환.
- 변경: 소유권·충전 상태·NPC 클래스 유효성을 사전 확인하고, 미저장 변경이 있다면 거래 예약 **전에** 기존 저장 큐에 제출. CacheD의 `L1InventoryTransaction.changeChargeCount`로 남은 충전을 갱신하며 마지막 충전에서는 기존과 같이 지팡이 전체를 `tx.debit`하여 삭제.
- `CDB.RequestUseItem` 커밋 콜백에서만 NPC 생성 및 `ItemActionCompletion.finishCommittedConsumable` 수행. 실패/롤백 시 소환·완료 처리 없음. 효과 패킷 실패는 출력 오류로만 로깅.
- `ItemActionDispatcher`에서 두 ID의 결과를 `COMPLETE`에서 `STOP`으로 변경하여 일반 동기 완료 꼬리 처리를 차단.

## 144차 — 몬스터 소환 소모품(3000124)

- 기존: `L1SummonInstance` 생성 → `removeItem(item, 1)`.
- 변경: 맵·전쟁·기존 펫 수·NPC 템플릿 검증을 유지하고 `UseDurableConsume`의 영속 차감 완료 후 한 마리를 소환. 성공 콜백에서만 사용 완료 처리.
- 기존 임시 펫 크기/Iterator 루프 제거. 원래 펫이 0일 때 소환 1마리, 펫 비용 0 및 `CommonUtil.random(3)`로 앞선 3개 NPC ID를 선택하던 동작은 유지.
- 반환을 `STOP`으로 바꿔 조기 재사용 시각 갱신 차단.

## 검증

- `run_tests.sh`는 **생산 메서드를 추출**하고 의존성만 typed stubs로 대체하여 Java 21로 컴파일 후 반복 모의 거래 4,400개 검증 조건을 실행한다. 매번 생산 소스에서 메서드를 추출하므로 변조된 복사본만 테스트하는 방식이 아니다.
- 커밋 전/성공/롤백, 마지막 충전, 미저장 변경 저장 실패, 잘못된 NPC 클래스, 맵·전쟁·펫 제한, 출력 실패, 커밋 후 실제 월드 소환 실패의 오류 전파를 포함.
- `ItemActionDispatcher`의 3개 `STOP` 조건을 정적 검사.
- Java 21 생산 전체 1,923개 구문 검사 통과. 이전 139~142차 typed stub 79개, 133~135차 특수 강화 153개·장비 교환 32개·텔레포트/범위 효과 33개, 139~142차 정적 게이트 42개 확인.
- 실제 MySQL·Windows·인게임·동시 세션 테스트 및 전체 프로젝트 의존성 타입 컴파일은 **하지 않았음**.

## 남은 위험 / 범위 제한

1. DB 커밋은 월드 소환의 완전한 원자성을 보장하지 않는다. 커밋 후 생성자·월드 등록·NPC 타이머 시작이 실패하면 아이템 차감은 확정된 상태이므로, 기존 커밋 후 예외 격리를 이용해 운영 검사를 요구한다. **자동 환불·재시도는 하지 않음.**
2. 커밋 대기 중 월드/펫 상태 변경과 다른 펫 소환 경로의 경쟁, 펫 호출 피리(41160)의 소모-월드 생성 경계는 별도 심화 검증이 필요하다.
3. 일반 강화의 주문서 소모와 장비 변경의 완전 원자성, 카드 DB↔메모리 복구, 고빈도 HP 포션 영속성 등은 이번 범위에서 변경하지 않았다.
4. 생산 Java 변경은 아래 3개 파일만, 모두 아이템 처리 패키지 내부다. NPC AI, 공통 네트워크, 방송, DB/CacheD 코어는 byte-identical하다.

```
src/l1j/server/server/model/item/function/NpcWandAndSpawnItems.java
src/l1j/server/server/model/item/function/PetAndSummonItems.java
src/l1j/server/server/model/item/function/ItemActionDispatcher.java
```
