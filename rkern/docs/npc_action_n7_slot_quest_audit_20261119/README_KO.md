# N7 슬롯 개방 / 퀘스트 지급 검수

기준: RKern_NPC_Action_N7_Paid_Tower_Spy_Audit_FULL_SRC_20261119.zip.
- NPC 5162 슬롯 5종: 기존 쿠폰/아데나 가격, 레벨, 대소문자, 패킷 순서 유지. 비용과 character_quests 진행도 한 receipt에 확정. 정상 개방 후 실패 HTML 중복 전송 제거. 기존 stageQuestStep의 DB preimage 검증 재사용.
- NPC 71005: 토큰 41213 1개와 물약 40029 20개 receipt 처리. 확정 후 143 및 HTML. 실패 시 차감/보상 없음.
- NPC 71005/71025/80084/71126/80076: 기존 무상 지급 정책과 재수령 조건 유지, null 지급 시 성공 응답 차단, 임시 배열 제거. NPC 200015 지급 null 방어. 무료 지급 자체의 영속화 방식은 기존 그대로.
- 기존 NPC 분기 ID 순서 대조, 실제 신규 메서드 추출 JDK21 모의 테스트 82,216 assertion, 직전 테스트 122,657 assertion. 전체 1,928 Java 구문 검사 통과. 전체 타입 컴파일은 기존 오류 531건/경고 5건, 직전 로그와 byte-identical. 실제 MySQL, 통합 빌드, 인게임 및 heap 측정 미실시. 슬랏/물약 receipt는 기존 capacity 검증을 적용하므로 지급 불가 시 거절한다. 무료 지급의 반복 허용 정책은 운영 HTML 확인 없이 바꾸지 않음. 카르마/바닥 지급/XML/TalkChannelCache 미해결.
