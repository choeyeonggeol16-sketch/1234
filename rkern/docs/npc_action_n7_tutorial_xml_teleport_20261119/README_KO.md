# N7 초보자 지원 및 XML 유료 이동 검수
기준: RKern_NPC_Action_N7_Gear_Exchange_Receipt_Audit_FULL_SRC_20261119.zip
- NPC 9274 A: 직업군별 기존 5개 방어구 중 미보유분만 기존 인벤토리 receipt로 지급. NPC 5086 a/b: 기존 30055 120개 / 30056 100개 지급 동일. 지급 실패 시 부분/바닥 지급 및 성공 HTML 차단. 보유분만 확인하는 기존 재수령 정책은 유지(이동/삭제 등으로 잃으면 다시 지급 가능). 두 곳의 기존 바닥 지급 정책은 제거.
- XML Teleport: 가격/방향/맵 유효성, 실제 removeItem 반환량 확인, 0원은 차감 안 함. 기존 동기 복합 액션 계약, 레벨 제한, 랜덤 위치 범위 유지. 고정 이동 L1Location 복사 제거. DB 원자화/이동 실패 환급은 아님.
- 신규 추출 실제 메서드의 Java 21 typed mock 테스트 29,844 시나리오, 133,467 assertions. 기존 gear 3,368 시나리오, 150,304 assertions. 전체 구문 1,928개 오류0. 전체 javac 기존 531 errors 5 warnings 진단 바이트 일치. 실제 DB/인게임/Heap 미검증. XML 제작 및 카르마 영속성 미해결.
