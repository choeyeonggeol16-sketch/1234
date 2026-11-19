# RKern 아이템 169~175차: 보호 주문서·추가 무기 강화 원자성

기준: `RKern_ItemOnly_AtomicSpecialEnchant_MultiPhase164_168_FULL_SRC_20261118.zip`

## 실제 수정된 생산 Java (8개)

- `EmblemProtectionScrolls.java`: 문장 확률·확정 보호 강화
- `InsigniaProtectionScrolls.java`: 휘장 확률·확정 보호 강화
- `OrimProtectionScrolls.java`: 오림 악세서리 확률 보호 강화
- `RoomtisProtectionScrolls.java`: 룸티스 확률·확정 보호 강화
- `ShoulderProtectionScrolls.java`: 어깨 확률 보호 강화
- `SnapperProtectionScrolls.java`: 스냅퍼 확률·확정 보호 강화
- `ItemReceiptOperations.java`: 기존 원자적 +1 강화 및 영속 소모를 재사용하는 보호 주문서 공통 실행 루틴 1개 추가. 새 큐·워커·스레드 없음.
- `WeaponEnchantScrollResolution.java`: `810003` (확률) / `4100148` (+8 이상 확정)의 비파괴 성공·실패 처리. 기존 RNG 호출 유지, 거래 제출 후 `STOP` 반환.

기존 10개 보호 주문서 메서드에서 성공 시 주문서 소모와 대상 강화값 +1을 **동일한 CacheD receipt**로 COMMIT한다. 비파괴 실패는 주문서 차감 1건만 영속 COMMIT한다. DB 실패/롤백이면 주문서·강화값·완료 시각을 바꾸지 않는다. 캐릭터 장착 상태 보정과 성공 메시지는 COMMIT 후 처리하며, 단순 안내 패킷의 예외는 거래 실패로 재분류하지 않는다.

변경된 보호 주문서 ID: `810013`, `3000428`, `4000428`, `3000430`, `4000430`, `3000546`, `4000546`, `3000547`, `4000547`, `4100034`.
추가 무기 ID: `810003`, `4100148`. 전체 일반 강화의 파괴·-1·+2/+3 경로는 수정하지 않는다.

## 검증

압축 해제 후 `bash docs/item_multi_phase169_175_20261118/run_tests.sh`.

- 보호 주문서 **실제 10개 메서드 본문** 추출 후 JDK 21 typed mock compile/execute: **710 checks** 통과.
- 무기 강화 `810003`/`4100148` **실제 WeaponEnchantScrollResolution 전체 클래스** typed mock compile/execute: **1,536 checks** 통과.
- 기존 특수 강화 회귀: 무기 **288**, 방어구 **336**, 거래 **21**, 장착 후처리 **127** 통과.
- 기존 영속 소비/교환 **12**, 아이템 완료 **1,980**, 강화 저장 거절 **14** 통과.
- 전체 생산 Java **1,923개 JDK 21 구문 파싱 오류 0**.
- 원본 ZIP 생산 파일 변경은 **8개**, 나머지 전체 항목 바이트 동일. ZIP CRC 확인.

**검증 한계:** DB/영속 거래는 typed mock으로 주입해 확인했다. 실제 MySQL·ACK 손실·동시 세션·인게임 강화 및 프로젝트 전체 종속성 컴파일은 수행하지 않았다. 직접 강화값 변경/장비 파괴를 포함한 일반 강화 전체 원자성, 펫 월드 생성 실패 보상, 카드 DB↔메모리 복구는 미해결이다.
