# N8 XML 제작 receipt / continuation

기준: `RKern_NPC_Action_N7_Tutorial_XML_Teleport_Audit_FULL_SRC_20261119.zip`.

- `L1NpcMakeItemAction`: 정확한 강화 수치·미착용 재료를 중복 항목 합산 후 실제 객체로 stage; 보상 강화 수치 설정 후 detached grant; 기존 `L1InventoryTransaction`으로 한 receipt에 확정. 성공 보상 메시지는 커밋과 소유권 해제 후 발송. 실패 시 부분 지급 없음. AmountInputable=false 수량 2 이상 차단. 기존 999회, 180슬롯/무게 보수적 사전 검사 유지; receipt의 최종 용량 검증 추가 적용. 별도 엔티티 삭제가 필요한 토큰 재료는 기존 receipt 정책에 따라 거부.
- `L1NpcAction`, `L1NpcListedAction`: 임시 DEFERRED identity marker와 기존 owner 완료 콜백. 복합 XML은 자식 순서, 각 자식의 조건 재평가, 마지막 non-null HTML 반환을 유지하며 receipt 완료까지 중단. 실패 확정이면 Fail branch, 불명 응답이면 콜백을 실행하지 않는 기존 receipt 정책. 신규 스레드/큐/클래스 없음.
- `L1NpcXmlParser`: Succeed/Fail 검색을 직접 자식으로 제한 (중첩 하위 분기 잘못 선택 방지).
- `HtmlCmdHandler`, `NpcTalkHandler`, `CS_Amount`: 세 실제 입구를 continuation dispatch로 통일. 일반 leaf 동작·amount leaf의 기존 null 동작 유지.

검수: 실제 XML 액션 클래스, 파서, factory를 Java 21로 컴파일하고 모의 PC/inventory/receipt 및 packet 구현 사용: 480 시나리오, 4,325 assertions. 기존 support/XML 수량 4,728 assertions 및 tutorial/teleport 29,844 시나리오/133,467 assertions 재실행 통과. Java 1,928구문 오류 0건. 동일 옵션 전체 javac 원본과 동일 531 errors / 5 warnings (로그 byte-identical); 완전한 타입 컴파일 성공이 아님.

제약: 각 MakeItem 원자화이며 여러 MakeItem/SetQuest/Teleport로 구성된 XML 전체를 하나의 DB 트랜잭션으로 만들지는 않는다. SetQuest 등 후속 부수효과의 영속성, pending 상태에서 대화 변경에 따른 오래된 HTML/대상 수명, 실제 운영 XML/DB/ACK loss/재접속/Heap은 추가 검증 필요. 표준 receipt의 강화/축복/시간제 보상 초기화 및 스택 병합 정책 적용; 특수 운영 템플릿 호환성은 실제 데이터에서 확인 필요. 이번 변경은 XML 및 진입부만 수정.
