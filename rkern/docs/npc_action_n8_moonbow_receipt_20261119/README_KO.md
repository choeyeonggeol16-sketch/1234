# N8 달의 장궁 퀘스트 8단계 receipt 감사
- 직전 Inn Rental Return Receipt 전체 SRC 기준. 로빈후드/지브릴의 8개 명령을 하나의 기존 NpcQuestTransactions 실행부로 통합. 기존 대화 단계 0~7, 대소문자, 재료/보상 ID 및 수량, 성공/재료부족 HTML 유지.
- 실제 L1MerchantInstance 대화 조건과 일치하는 단계만 허용. 소개장 중복 수령 및 단계 건너뛰기 차단. 마지막 단계 후 0으로 초기화하는 기존 반복 제작 정책 유지. 두 보상 지급 메시지가 중복되던 경로는 한 번으로 정리.
- 기존 L1InventoryTransaction과 stageQuestStep 사용. 모든 재료/보상/DB 진행도를 한 receipt로 처리. 장착 재료 차감 금지, 용량 부족 시 전체 거부. 커밋 후 현재 owner에서 진행도 및 HTML 반영. 미확정 응답에서 성공 콜백 실행 안 함. 기존 소비 메서드의 스택 선택과 달리 receipt의 유효 스택 합산 규칙 적용.
- 실제 신규 함수 추출 typed-stub 검사(1,406 시나리오, 5,624 assertions), XML 회귀(480 시나리오, 4,325 assertions), Java 1,928개 구문 검사, 직전 javac 진단 byte-identical(531 오류 5 경고). 실제 DB/게임/장애복구 미검증. TalkChannelCache, 카르마 영속성 및 여관 맵 재사용 경합 미해결.
