# RKern/L1J 전체 SRC — 아이템 누적 116차 (2026-11-18)

115차 전체 SRC를 기준으로 아이템 감정 주문서 처리(40126, 40098)를 수정했습니다.

## 생산 코드 변경 (2개)
- `src/l1j/server/server/model/item/function/IdentificationAndScrollItems.java` — 감정 대상의 식별 상태를 주문서 1개 차감과 동일한 CacheD inventory receipt에 등록합니다. 성공 패킷은 영속 COMMIT/메모리 replay 후에만 전송합니다. 다른 인벤토리의 물건, null, 동일 입력/대상 거부 및 미반영 dirty save 순서 보호를 포함합니다. 감정 완료 후 클라이언트 메시지 실패는 커밋 경제 상태를 격리하지 않습니다.
- `src/l1j/server/server/model/L1InventoryTransaction.java` — 장착된 아이템에도 적용 가능한 **감정 상태만의** prepared-image 변경 메서드 `identify(L1ItemInstance)` 추가. 기존 `change(...)`의 일반 필드 변경 정책은 수정하지 않습니다.

## 검증
- Java 21 구문: 전체 생산 1,923개, 오류 0.
- Java 21 타입 컴파일: **실제 IdentificationAndScrollItems.java**와 `L1InventoryTransaction.identify()`의 **실제 소스 추출 메서드**를 타입드 모의 의존성과 함께 컴파일.
- 신규 분기·장애 주입: 174개 검사 통과. 장착/미장착, 기감정, DB 성공/실패, 참조 변동, 패킷 전송 실패, dirty save, 소모 불가를 포함.
- 기존 112,113,114,115차 typed-double 회귀 각 통과.
- ZIP CRC와 115차 원본 대비 파일 바이트 동일성 검사 통과.

**한계:** 실제 전체 의존성 통합 컴파일, 실제 MySQL/CacheD 장애 실험, 네트워크/인게임/동시접속/재접속 시나리오는 수행하지 않았습니다. DB↔메모리 카드 수량 자동 재동기화 문제는 여전히 미완성입니다. 또한 아이템 외 NPC/네트워크/방송 코드는 변경하지 않았습니다.
