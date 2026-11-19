# N7 잔여 NPC 명령 및 피어스 메뉴 안전성

기준: RKern_NPC_Action_N7_Template_Dispatch_Quest_Receipt_FULL_SRC_20261119.zip
SHA256: 08a240c07e782eebe5e530212dc1297e041eba0b2e4a7be9df266a9eaf7bda7d

- HtmlCmdHandler의 19개 기존 보조 함수 책임 분리. NpcExchangeCommands의 동일 createNewItem 메서드를 재사용하여 중복 구현 제거. 바닥 지급 정책 보존.
- 기존 NpcQuestTransactions, NpcTravelCommands, NpcExchangeCommands, NpcExperienceCommands, NpcLocalCommands 활용. 독립적인 펫 구입 책임만 NpcPetCommands 추가. CS_NpcAction의 공개 마빈 경험치 호환 진입점 유지.
- 피어스 메뉴(70908)를 NpcExchangeCommands에 이전. 입력의 대소문자, 숫자 0~9 및 A~D, 기존 HTML과 비용·보상 ID 유지. 두 플래그를 스칼라 비트로 처리해 임시 int[2] 제거. 기존 배열 길이 19인데 20개를 지우던 오류 해결. 표시 가능한 10개를 넘겨 쓰지 않음. 메뉴를 새로 열거나 검색할 때 이전 선택을 지우고, 잘못된 재료/강화/보상/가격 조합은 거부. 정상 거래의 선택 상태는 성공 HTML 전 초기화.
- 비자야의 고정 보상 배열을 조건식으로 바꾸고 미등록 명령의 itemId 0 조회를 방지.
- 마르바의 지급 null 경로에서 성공 메시지 및 null 역참조 방지.
- 유료 변신(NPC/케플리샤)과 펫 구매에서 비용 차감 실패 시 효과/스폰 차단. 정상 비용 및 RNG 경로는 변경 없음.
- 신규 스레드/큐/패킷별 Context 없음. 네트워크 엔진, CacheD 코어, NPC AI, 기존 아이템 리팩토링은 변경하지 않음.

## 검증
- test_helpers.py: 이동/중복 제거한 메서드 12개의 본문 일치 대조 및 실제 추출 메서드 Java 21 typed stub 검사 71,972개 assertions 통과. 피어스 표시/선택/구 결과, 유료 변신, 비자야, 마르바 검사.
- test_pet_purchase.py: 실제 추출 펫 구입 메서드 typed stub 검사 76,858개 assertions 통과. 소지/차감 성공·실패, 클래스/CHA/펫코스트, 스폰·템플릿·인벤토리·아뮬렛 실패 경로.
- 직전 test_template_dispatch.py: 1,093 assertions 및 54개 유효 라우팅 비교 통과. CS_NpcAcion2 참조 없음.
- Java 21 전체 구문 1,928개 정상. 타입 검사 --release 21 --enable-preview -proc:none -encoding UTF-8 -Xmaxerrs 10000은 의존성 누락 등 531 오류/5 경고로 실패. 원본 ZIP에 기록된 동일 옵션 진단 로그와 바이트 동일. 전체 빌드 성공을 뜻하지 않음.

## 남은 위험과 개발 범위
피어스·비자야 등 기존 바닥 지급 호환 교환은 재료 차감과 보상 지급이 아직 원자화되지 않았다. 펫 구매는 비용 차감 이후 생성/지급 실패 시 보상 및 복구가 필요할 수 있으며 이번에는 차감 실패 시 스폰 방지만 수정했다. 이동된 퀘스트 및 카르마 기능은 실행부 분리만 했으며 개별 경제 안전성은 후속 검수 대상. 스탯 초기화의 반복 장착 해제는 기존 gate 재시도 의미를 먼저 확인해야 하므로 그대로 보존. XML 복합 제작, TalkChannelCache, 기타 NPC 직접 명령, 실제 DB·인게임·Heap/JFR, 전체 과거 아이템 회귀는 이번에 완료하지 않음.
