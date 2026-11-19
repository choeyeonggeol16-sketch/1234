# NPC Action N7 — 상점/창고·용병·결혼반지·유료 버프

기준: RKern_NPC_Action_N7_Elemental_Quest_Raid_Safety_FULL_SRC_20261119.zip. 기존 NC 분석 보고서의 명령별 실행 책임 분리 및 AtomicJob 대응 L1InventoryTransaction 사용 원칙 유지.

## 변경
- HtmlCmdHandler: 판매, 창고 6종, 결혼반지 충전, 호슈, 용병 2종, 장비 버프 2종, 펫 찾기 실행을 기존 기능 클래스에 위임. 4489 -> 4258줄. 기존 선행 대상/등록 액션 및 분기 순서 유지. 상점/창고의 최종 HTML은 담당 클래스에서 같은 인자와 순서로 송신.
- NpcMarketCommands: 판매 및 창고 실행 분리. 창고 용량 조건/메시지(기존 표시 수치 불일치 포함)는 이번 구조 분리에서 보존. 특수 상점의 중복 assessItems는 패킷 송신 구현을 변경하지 않기 위해 유지.
- NpcCastleActions: employ/demand를 공통화하고 공성전 진행/템플릿 존재 검사 후 300000 아데나 차감. 정상 용병 ID와 안내 보존. 생성자 예외 및 타 스레드 공성 시작과의 경합에 대한 비용 보상은 별도 미완료.
- NpcBuffCommands: encw/enca 장비 버프는 100 아데나 차감 성공 후 스킬 적용. 기존 장비 조건 유지. 스킬 자체 실패/예외 후 환급은 미구현.
- NpcTravelCommands: 호슈 10000 아데나 차감 실패 시 이동 차단. 이동 좌표/방향과 HTML 보존. 차감 후 실제 이동 실패 복구는 별도 검증 필요.
- NpcExchangeCommands: 결혼반지 1000 아데나 기부와 충전량을 동일 receipt에 기록, 확정 후 성공 메시지. 기존 반지 우선순위 40906>40905>40904>40903 및 비혼/반지 없는 경우의 기부 동작 보존. 실제 CacheD/DB rollback 및 세션 교체 검증 필요.
- NpcPetCommands: 펫 아뮬렛 존재 확인만 필요한 withdrawnpc에서 ArrayList 및 전체 스캔 제거, 첫 일치 시 기존 SC_PetList 송신. 삭제/수집 시 사용하는 스냅샷은 동시 수정 방지 목적이므로 유지.

## 검증
- test_commands.py: 실제 생산 메서드 추출, 직전 ZIP 원본의 판매/창고 분기를 Java 21 typed stub에서 대조. 판매 168009, 창고 10369, 용병 1816, 장비 버프 99, 이동 36, 반지 2560, 펫 확인 8 assertions, 총 182897 통과. receipt는 스텁으로 commit/rollback을 모델링한 것으로 실제 DB 검증이 아니다.
- 이전 정령력/변신/오크/레이드 회귀 test_commands.py 재실행 156425 assertions 통과.
- 전체 Java 1928개 JDK21 구문 오류 0. 동일 옵션 타입 컴파일은 531 errors / 5 warnings로 기존 의존성 등 오류에 의해 실패. 직전 버전의 compile log와 byte-identical.
- 생산 클래스 신규/삭제 없음. 기존 CIO, NpcServer, CacheD, 방송·네트워크·아이템 실행 코어 미변경. 전체 아이템 과거 테스트 전체 재실행, 실제 DB/인게임/JFR/Heap 미실시.

## 남은 핵심
HtmlCmdHandler 직접 실행부, XML 복합 액션의 동기 반환/비동기 receipt 호환, TalkChannelCache 대화 수명, 바닥 지급, 전역 이벤트 및 외부 부수 효과의 실패 원자성. 실제 NC 코드에 있는지 불명확한 새로운 실행 계층은 만들지 않음.
