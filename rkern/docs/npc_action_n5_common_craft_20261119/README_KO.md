# NC NPC Action N3 및 N5 공통 제작 전환

기준: `RKern_NPC_Action_N2_N3_ScrollExchange_Safety_FULL_SRC_20261119.zip`.

## 변경
- `HtmlCmdHandler`: 공통 재료/보상 처리 본문을 NpcCraftCommands로 이전. 186개 고정 배열 생성 지점을 98개 private static final 배열로 치환(동일 값 공유). 가변 토템 배열은 유지. 토템 교환 보상 10곳 및 여관 반환 금액에 Math.multiplyExact 적용. 동일 40312 검사로 도달 불가능했던 49016 반환 분기 제거. Alice 재료 순서 배열 재사용.
- `NpcCraftCommands`: 일반 HTML 제작의 재료·보상 배열 검증, 템플릿 존재 확인, 기존 180칸/무게 사전 제한 유지, 기존 L1InventoryTransaction에 전량 debit/grant를 stage, 기존 owner/CacheD 완료 경계에서 성공 메시지와 성공/후속 HTML 전송. 신규 스레드/큐 없음. NPC 자체를 완료 콜백에서 캡처하지 않음. 준비 실패/DB 롤백이면 성공 HTML을 보내지 않음. 기존 실패 HTML이 없으면 실패 안내 또는 재료 부족 메시지. 인벤토리 gate 및 unknown/replay 정책은 기존 L1InventoryTransaction에 의존.
- `CS_Amount`: 일반 XML amount action 진입 직전 IN_GAME/ghost/shop/auto clan 및 동일 맵/6타일 검사 추가. 기존 HTML 대상과 공통 범위 함수 사용. 여관과 경매의 선행 분기는 별도 계약이므로 변경 없음. full TalkChannelCache, 페이지/세션/발급 명령 수명은 아직 구현하지 않음.

## 주의 및 범위
- 일반 HTML 제작에 한정한다. XML MakeItem의 동기 반환 및 ListedAction 후속 액션, CS_NpcAcion2 및 다른 직접 제작 경로는 그대로 유지한다.
- 기존 조합 선택, RNG, 재료/보상 ID/수량은 유지한다. recipe 진입 이전의 퀘스트 상태 변경 등 외부 효과는 아직 동일 트랜잭션에 포함되지 않았으므로 NPC 퀘스트 전체 원자성은 보장하지 않는다.
- 기존 transaction의 stack merge, 최대 수량, 특수 실체 토큰 debit 제한 및 영속 초기화 정책을 사용한다. 실제 운영 템플릿과 이전 storeItem 간 세부 속성/용량 차이는 추가 인게임 검증 필요. 일반 레시피에 지원하지 않는 실체 토큰이 등장하면 transaction 준비에서 안전하게 실패한다.
- 이미 제출된 성공/롤백 처리는 기존 owner 세션 계약에 의존한다. NPC 변경·재사용 후 HTML 응답 억제는 전체 TalkChannelCache 단계에서 점검할 항목이다.
- 원본 소스의 일반 게임 동작과 호출 순서 보존을 목표로 하지만 모든 정상 경로 등가성 실증을 의미하지 않는다. 신규 실제 DB 및 장시간 메모리 측정 미실시.

## 검증
- test_common_craft.py: 실제 신규 handler 코드를 typed fake 환경에서 실행, 3,929개 모의 케이스(준비 실패, commit/rollback, 중복 재료, 용량, 오류 HTML). 186개 literal 벡터의 원본/수정본 값 및 순서 동일(도달 불능 49016 분기 제외). 실제 DB 테스트 아님.
- test_amount_target.py: 실제 범위 함수와 호출 순서 검사, 1,324개 조건.
- prior scroll/followup harness 재실행 통과.
- Java 21 구문 1,924개 오류 0. 전체 javac 의존성 누락 등의 531 errors/5 warnings, 직전 기준의 진단 로그와 바이트 동일. 빌드 성공 아님.
- 기존 아이템 회귀 스크립트 실행 결과는 item_regressions.log 참조.
- 네트워크, NPC AI, 방송, 아이템, CacheD 코어 변경 없음.
