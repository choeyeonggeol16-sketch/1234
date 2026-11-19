# RKern NPC Action — N2/N3 부분 진행 및 N5/N6 선행 경제 수정

기준: RKern_NPC_Action_N2_Commands_DomainSplit_FULL_SRC_20261119.zip
SHA-256: 92d1fc9b5506c6b89f006339999d003641b7af2c90346392823ea0698b1cb689

## 변경 사항
- NpcPlayerMenuHandler: PC 기반 변신/소환 메뉴 분리. 자기 PC 객체만 대상 허용(기존 자기 UI의 object ID 발행 근거). 미정의 숫자 요청과 NPC 템플릿 누락 방어. 기존 소환 명령·레벨·카리스마·펫 비용·상한 수치는 유지했다. 숫자 검사에서 정규식 할당 제거. SmallIntMap의 동시 변화 위험 때문에 기존 펫 배열 순회는 유지. 소환 스킬/메뉴 발급 세션의 유효성까지 구현한 것은 아니다.
- NpcLocalCommands: 변신 주문서 consumeItem 반환 실패 시 변신 금지; 폐기된 숫자 자동 명령 regex 제거. 기존 지역 명령 우선순위 유지.
- HtmlCmdHandler: 중복 좌표 조회 삭제, 상위 예외의 빈 catch 대신 Logger 사용. 새 NPC 채널 만료/권한 정책은 아직 전체 적용하지 않았다.
- CS_NpcAcion2/NpcTravelCommands: 연속 16개 PC방 이동 NPC 분기를 기존 위치에서 통합, NPC 9000은 140100 1개, 나머지는 40308 1000 유지. consumeItem 반환 실패를 확인하며 이동 좌표 및 나머지 HTML 상태 보존. 기존 singleton의 경쟁 초기화를 final 인스턴스로 변경하고 사용되지 않는 배열 필드 제거. 기존 consumeItem API의 내부/영속 성공을 보장하는 원자화는 이 이동·변신 경로에서 실시하지 않았다.
- NpcQuestTransactions: NPC 535 프로톤 건 1/5/10 → 오림의 가넷 3/15/30 교환을 기존 인벤토리 receipt로 전환. 성공 메시지·HTML은 owner replay 이후, 실패는 기존 실패 페이지. 기존 비동기 callback은 PC와 scalar object ID를 캡처하고 NPC 객체는 캡처하지 않는다.
- NpcSpellScrollCommands: 젠도르 빈 주문서 25종 A–Y와 5개 제작 수량을 데이터로 정리. 기존 모든 보상 ID와 재료·수수료·특수 재료 조건 유지. 수량 선택 UI는 6개 문자열 배열을 초기화 시 생성 후 직접 전송, 성공/실패는 기존 6개 빈 placeholder 유지. 제작은 한 receipt의 debit/grant로 처리하고 확정 이후 HTML. 선택 수량은 허용값만, 요청 처리 시 0으로 초기화. 기존 마을 버프 4개 스킬의 임시 int[] 제거, 순서 보존, 비용 반환 실패 방어.
- L1NpcMakeItemAction: 재료 및 결과의 음수/0/곱셈 overflow 방지, 존재하지 않는 템플릿 방어, 슬롯 및 무게 long 계산, 공용 zero-length args. XML 제작의 즉시 반환 및 ListedAction의 후속 실행 계약은 아직 변경하지 않았으며 원자화되지 않았다.

## 테스트
- test_followup.py 실제 추출 메서드/스텁 검증: 7,881개 조건(숫자 비교 78, 정상 소환 5,115, 잘못된 요청 14, 이동 336, receipt 160, 수량 범위 64, 변신 소비 실패 2, 기존 지역 명령 2,112). 지역 명령 모의 테스트에서는 숫자 검사 부분을 regex 등가 표현으로 대체해 나머지 경로를 비교하고 실제 숫자 helper는 별도 직접 검증했다.
- test_scroll_craft.py 기존/수정 실제 추출 코드와 typed doubles: 2,055개 조건(정상·부족 1,240, 거래 실패/롤백 750, 버프·수량 미선택 60, 잘못된 수량/버프 차감 실패 5). 실제 DB/인게임 실행 아니다.
- JDK 21 전체 Java 1,923개 구문 검사 오류 0. 전체 타입 컴파일은 동일 531 errors/5 warnings(외부 의존성 등 기존 문제). 진단 헤더 비교 시 536개 동일, 추가 0/제거 0. 이는 전체 빌드 성공과 다르다.
- 기존 아이템 회귀 14개 스크립트 재실행 모두 exit=0. 이전 N2의 원본 역변환 테스트는 이번 의도적 정책 변경을 포함하지 않으므로 그대로 통과했다고 주장하지 않는다.

## 미완료 및 운영 확인 필요
아직 HtmlCmdHandler와 CS_NpcAcion2의 많은 직접 기능 분기가 남아 있다. N3 대화 세션/TalkChannelCache 전역, N4 이벤트 구조, N5 공통 제작 및 XML 비동기 후속 액션, 기타 퀘스트/교환의 원자화는 추가 작업 필요. 새 receipt는 기존 DB 및 owner 계약을 재사용하지만 실제 DB commit/ACK 유실/reconnect/재시작은 미검증. 원자화된 제작은 기존 raw store보다 projected capacity 및 아이템 속성/stack merge 검사가 엄격해질 수 있어 운영 아이템 템플릿과 대조해야 한다. 버프/이동의 비용 차감은 기존 동기 API 유지. 메모리 할당 제거는 정적 소스 수준 확인이며 JFR/Heap 장시간 누수 및 CPU 측정은 미실시. 신규 스레드/큐 없음; NPC AI, 네트워크 코어, 방송, CacheD 구현 및 기존 아이템 소스는 변경하지 않았다.
