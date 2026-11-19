# NPC Action N7 지원품·XML 수량 검수
- 기준: RKern_NPC_Action_N7_House_Pet_Grant_Target_Audit_FULL_SRC_20261119.zip.
- NPC 5112 b/c: 지원품(30071, 30070, 200000)과 별도 촛불을 기존 inventory receipt로 지급. 원본 레벨/소지 검사 및 정상 HTML ID 유지. 바닥 지급을 제거하여 확인서만 바닥에 떨어지거나 부분 지급되는 경로를 차단. 인벤토리 부족 시 전체 거부. 기존 확인서를 판매/폐기/이동할 수 있는지는 아이템 설정에 의존하므로 영구 1회 지급을 보장하지 않음.
- NPC 80079 명령 3: 보유한 점술 재료 3종 회수의 all-or-nothing receipt. 미확정 시 성공 HTML 없음.
- XML 제작: 실제 MAX_AMOUNT(20억), 999회 상한, 동일 아이템/강화 재료의 누적 비용 및 미착용·정확 강화 재료 수량 반영. 동일 재료 행의 독립 검사 우회 차단. XML 제작/복합 액션 전체 원자화는 미완료; 기존 consume/store 계약은 변경하지 않음.
- 생산 Java 3개만 수정. 기존 CIO, DB 코어, 인벤토리 코어 및 다른 게임 로직 미변경.
- test_support_xml.py: 실제 추출 메서드 JDK21 스텁 모의 검증 4728 assertions. 이전 grant/target 회귀 13896 assertions 및 정적 검사. 전체 Java 구문 1928 정상. 전체 javac 진단 직전과 동일(531 errors, 5 warnings). 실제 DB, 장애 복구, 인게임, Heap 측정 미실시.
- 이전 N2/N3 산술 테스트는 당시 Integer.MAX_VALUE 계약을 고정해 둔 역사적 스크립트로, 이번 MAX_AMOUNT 강화의 검증은 신규 테스트에서 수행함.
