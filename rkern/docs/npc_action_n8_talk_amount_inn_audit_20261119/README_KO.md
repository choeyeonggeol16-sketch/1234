# N8 NPC 대화·수량·여관 진입 검수 (2026-11-19)

- CS_Amount: 모든 여관/경매/XML 분기 이전에 IN_GAME, 기존 HTML의 플레이어 상태와 동일 맵 Chebyshev 6타일 확인. 전역 수량 상한 20억. 경매 입찰은 L1AuctionBoardInstance, 매도는 해당 house keeper와 100,000 최소가 확인. 기존 여관 우선순위와 정상 경매 토큰 처리 보존. StringTokenizer 및 일반 XML 명령의 예외 경로 제거.
- NpcTalkHandler: 동일한 상태/거리 검사 후 타겟 효과와 NPC 실행. HtmlCmdHandler의 로컬 명령 선행 순서는 그대로 유지. 전체 NC TalkChannelCache 수명/링크 검증 구현은 아님.
- MJINNHelper: 수량 1~20, 홀 군주 제한, 재임대/receipt 진행 중/만실/누락된 맵 정보 차단. 잘못된 스택형 키 템플릿 차단. 지급 실패 시 미소유 월드 아이템 제거. hall fall-through break 추가. 기존 부분 임대 동작을 유지하며, 임대료·맵 생성·열쇠의 원자성 및 전역 예약 경쟁, 반환/실패 복구는 미해결. 여관 타이머와 DB는 변경하지 않음.
- NpcCastleActions: 매도 시 house 설정 누락 방어.

검증: 실제 CS_Amount/NpcTalkHandler/MJINNHelper 및 추출한 공통 검사 메서드를 typed stub으로 컴파일하여 4,692 시나리오/27,900 조건 통과. 직전 XML receipt 480 시나리오/4,325 조건 통과. 전체 Java 1,928개 parse 오류 0. 전체 javac 출력은 직전 baseline 로그와 byte-identical (531 errors, 5 warnings). 의존성 포함 전체 타입 컴파일/운영 DB/실게임 및 여관 동시성은 미검증. 실제 NPC ID·템플릿 및 원거리 메뉴 호환성은 운영 DB 검증 필요.
