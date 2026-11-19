# RKern/L1J 아이템 누적 117차 — 요리 영속 소모·알림 분리 (2026-11-18)

기준: `RKern_ItemOnly_IdentificationScroll_Atomic_Phase116_FULL_SRC_20261118.zip`.

## 생산 Java 변경 (2개)
- `src/l1j/server/server/model/item/cooking/CookingUseService.java`
  - 요리 아이템 사용 전 영속 차감 없이 능력치/타이머를 먼저 적용하던 경로를 기존 `ItemTransactionActions.consume`(CacheD inventory receipt)로 이관.
  - DB 커밋 이후 요리 그룹의 종전 스킬 종료, 신규 버프·타이머 적용, 요리 출력 처리.
  - 개인 이펙트·상태 아이콘·성공 메시지의 출력 예외를 별도 처리해 이미 확정된 소모/버프가 출력 오류로 실패 판정되지 않도록 보호.
  - 요리 그룹·지속시간·아이콘·아이템 매핑·포만도 225 필요 조건은 변경하지 않음.
- `src/l1j/server/server/model/item/cooking/CookingRegistry.java`
  - 능력치가 이미 변경된 후의 HP·MP·SP/MR·속성·상태·파티 상태 갱신 패킷을 독립 예외 처리.
  - 앞선 패킷 전송이 실패해도 후속 알림은 계속 시도하도록 함. 실제 능력치 apply/rollback 흐름은 변경하지 않음.

## 검증
- Java 21 생산 `CookingUseService.java` typed-double 컴파일 및 모의 커밋/롤백/패킷 장애 **1,284개** 조건 통과.
- `CookingRegistry.sendUpdates` 실제 추출 메서드 Java 21 타입 컴파일 및 6종 출력의 예외 조합 **64개** 조건 통과.
- 전체 Java 1,923개 파일 Java 21 구문 파싱, 오류 0.
- 이전 113·114·115·116차 회귀 각각 통과(113: 54,150, 114: 232,459, 115: 154, 116: 174 조건).
- ZIP CRC 및 116차 원본과 모든 기존 항목의 바이트 비교: 본문 Java 2개, 루트 README만 변경. 신규 검증 자료 추가.

## 미검증 및 남은 위험
- 실제 MySQL·CacheD 장애, 캐릭터 재접속/다중 세션, 인게임 요리 효과, 전체 의존성 통합 컴파일은 미검증.
- DB COMMIT 후 *실제 요리 상태 적용 자체*가 실패하거나 런타임 요리 그룹 상태가 예상과 다를 경우에는 이미 확정된 소모를 무작정 롤백하지 않음. 로그로 표면화하며 재접속 복구/실환경 검증이 필요함.
- 기존 카드 DB↔메모리 수량 재동기화는 이번 범위에서 변경하지 않음.
- NPC·네트워크·방송·CacheD 공통 구현은 변경하지 않음.
