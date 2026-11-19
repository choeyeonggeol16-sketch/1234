# RKern NPC Action N0 / N1 / N2 초기 — NC 기반 진입부 분리

기준: RKern_ItemUse_Final_MarbleCache_Phase241_243_FULL_SRC_20261119.zip
SHA-256: aa62c65dbee7903339605cbfbfe15b5cd6891699caed67ea7a7679905c60c9e0
설계: https://github.com/choeyeonggeol16-sketch/1234/blob/main/reports/RKern_NC_NpcAction_Development_Plan_20261119.md

## N0: 기존 실행 구조 대조
기준 ZIP의 생산 Java 1,913개를 JDK21 javac AST로 파싱했다. NPC 진입/보조, XML action, 액션 리스너 등 10개 소스의 분기/return/catch 레코드 2,817건, 메서드 호출 지점 8,227건, 메서드 선언 141건을 추출했다. TSV의 줄 번호는 243차 기준이며 호출 분류는 이름 기반 후보 표시이다. AST는 동적 디스패치의 실제 타겟이나 운영 XML/DB의 명령까지 확정하지 않는다. N0 자동 추출은 완료했으나 명령별 의미/실행 소유권 전체 수작업 대조와 운영 데이터 매핑은 미완료다.

## N1: 패킷과 게임 실행 분리
- CS_NpcAction.handle 기존 시그니처 → User.OnNpcActionPacket → HtmlCmdHandler.handle
- CS_NpcTalk.handle 기존 시그니처 → User.OnNpcTalkPacket → NpcTalkHandler.handle
- 기존 공개 마빈 경험치 지급 함수 2개의 호환 위임 유지.
- 이미 처리 중인 실행 문맥에서 동기 호출. 새 워커/큐/패킷 Context 없음.
- 접근 제어/기존 패키지 결합 위험을 줄이기 위해 두 실행 파일의 위치는 일단 기존 패키지에 유지했다. 추후 기능 단위 분리 때 도메인 패키지로 이동할 수 있다.

## N2 초기: 대상과 무관한 명령 분리
HtmlCmdHandler.dispatchLocalCommand(pc, s): 무시 명령, Neige 변신/카드/GM 명령, 폐기된 자동 명령, 기존 ActionControlHandler 진입을 원본 순서로 실행한다. true는 기존 즉시 return, false는 원래 본문 계속 실행이다. 원본 ActionControlHandler의 false 반환 및 부수 효과도 유지. 나머지 전체 실행 본문과 이전 트랜잭션 코드는 그대로 옮겼다. NC의 65개 테이블 전체를 구현했다고 주장하지 않으며 실제 L1 명령과의 연결/기능별 분리는 이어서 진행할 대상이다.

## 문제 후보 및 남은 단계
- 대상 NPC/PC의 소유권과 대화 채널 검증은 N3 대상. NPC 아닌 특수 UI에 임의 검사를 강제하지 않음.
- CS_NpcAcion2(2,935줄) 및 기존 HTML 명령 본문은 아직 미분리. 기존 XML/리스너/인스턴스 핸들러에 연결할 명령을 먼저 확정.
- 공통 제작 및 XML 제작 소비/지급을 원자화하는 N5/N6, 콜백 HTML 시점 검증은 아직 미작업. 이 차수에서는 의도적으로 게임 동작을 바꾸지 않음.
- 기존 변신 소비 결과 검사 누락, 빈 catch, 보조 처리 클래스의 미사용 필드와 싱글턴 초기화도 별도 검수 대상으로 유지.

## 검증
- 전체 구문: 원본 1,913개, 수정본 1,915개, 오류 0.
- 정확한 역변환 대조: 이전 CS_NpcAction/CS_NpcTalk 실행 본문 및 User 기존 코드 동일, 특수 명령 예외/return의 원래 범위 보존.
- 특수 명령 33종 × 상태 조합 16 × 레벨 4 = 2,112건 실제 추출 Java 메서드 스텁 실행 대조 통과. 스텁에 대한 로직 비교이지 실제 게임 테스트는 아니다.
- Java21 전체 타입 컴파일(--release 21 --enable-preview -proc:none): 양쪽 531 errors / 5 warnings. 진단 전체 multiset 정확히 동일, 새 진단 0. 외부 라이브러리가 없는 전체 빌드는 여전히 실패한다.
- 이전 아이템 회귀 스크립트 결과는 item_regressions.log 참조.
- 실제 운영 XML/DB, 인게임, 장시간 성능 및 메모리 검증은 미실시. 메모리 또는 처리량 개선 수치를 보장하지 않는다.

## 재현
원본 추출 후 javac --release 21 NpcAudit.java & java NpcAudit <원본 src> <출력 디렉터리> 로 N0 TSV 재생성. python test_npc_entry.py <243차 기준 ZIP> <수정본 추출 root> 로 실행 비교. python compare_compile.py 로 동봉한 전체 javac 로그 비교. 기존 아이템 회귀 스크립트는 해당 기존 docs 하위에 있다.
