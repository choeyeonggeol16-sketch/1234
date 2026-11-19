# RKern 아이템 33차 — 일반 지급 시 임시 월드 객체 정리 및 코드 통합

## 기준
- 32차 `RKern_Item_LegacyGiftBoxes_Atomic_Phase32_FULL_SRC_20261118.zip`에서 출발.
- 변경된 생산 파일: `ItemCreationItems.java`, `SealedEquipmentRewards.java` **2개만**.

## 실제 원인
- `ItemTable.createItem()`은 새 `L1ItemInstance`를 생성하면서 즉시 `L1World.storeObject()`를 호출한다.
- 기존 `ItemCreationItems`의 3종 지급과 `SealedEquipmentRewards`는 `checkAddItem`이 거부할 때 새 객체를 월드에서 정리하지 않았고, `storeItem(item)`이 기존 스택에 병합할 때도 임시 객체를 그대로 남겼다. 이 누적은 월드 객체 인덱스에 고아 엔트리를 남길 수 있다.
- `storeItem()` 도중 예외가 발생해 새 객체가 인벤토리에 남지 않는 경우에도 월드 정리 경로가 없었다.

## 변경
- `ItemCreationItems`의 일반·GM·강화 지급 3개 메서드는 기존 공개 서명을 유지하면서 정적 공통 메서드 하나로 본문을 통합. 새 클래스/스레드/큐는 없음.
- 두 파일 모두 `try/finally`에서 **최종 인벤토리가 새 객체 그 자체를 소유하지 않으면** `L1World.removeObject(item)`을 실행. 실제로 새 항목이 들어갔으면 제거하지 않음.
- 정상 지급의 아이템 ID·수량·강화·식별 플래그·축복 후처리·메시지 순서는 원본 그대로 유지. 거부·병합·예외에서는 이전에 남던 임시 세계 객체만 제거.
- `ItemCreationItems`는 `count<=0`, null PC 요청에서 새 객체를 생성하지 않고 실패 반환(명백히 잘못된 입력에만 해당).
- **주의:** 기존 동기 지급 API를 CacheD 트랜잭션으로 바꾸지 않았다. 기존 지급 경로 자체의 실제 DB 장애 원자성 문제까지 해결했다고 주장하지 않는다. `SealedEquipmentRewards`는 현재 생산 호출부가 없지만 같은 위험을 방지하도록 정리했다.

## 검증
- JDK21 실제 변경 생산 Java 2개 + typed stub 게임 모델 타입 컴파일 통과.
- 원본 32차 메서드 원문을 그대로 추출한 baseline과 **2,306개 검증 단언**: 일반·GM·강화·봉인 지급에 대해 수용/병합/거절/검증 예외/저장 예외에서 반환·메시지·메서드 호출·인벤토리 효과가 동일. 신규 검증은 실패/병합 시 월드 고아 엔트리 제거도 확인.
- JDK21 javac `JavacTask.parse()` 전체 Java **1,925개 오류 0**(타입 컴파일과는 별개).
- 32차 선물상자 1,381개, 31차 스타터 패키지 521개, 30차 아이템 효과 레지스트리 259,200개 및 기존 물약/빙수/룬/이동·대상 검사 통과.
- 방송 114차 오류 주입/5,000수신자 모의검사 통과(방송 생산 코드 변경 없음).
- 실서버 의존성 전체 컴파일·실제 MySQL/CacheD 장애 주입·인게임 동시 이용·실제 retained heap/GC 측정은 **미수행**.
