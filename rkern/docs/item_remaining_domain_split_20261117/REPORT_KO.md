# 아이템 처리 도메인 분리 — 후속 단계

기준: `RKern_ItemFunction_QuestDomainSplit_FULL_SRC_20261117.zip`

- 제거: `ConsumableItemCases.java`, `UtilityItemCases.java`, `FallbackItemCases.java`
- 신규: 도메인 파일 17개 (`CombatBuffConsumables`, `FoodAndPotionConsumables`, `PcRoomConsumables`, `BlessingConsumables`, `CharacterResetConsumables`, `IdentificationAndScrollItems`, `SocialInteractionItems`, `PetAndSummonItems`, `FishingUtilityItems`, `ClanMarkDisplayItems`, `CharacterServiceItems`, `CelebrationSoundItems`, `UnlistedMagicScrolls`, `CookingAndFurnitureItems`, `ClassSpellbookItems`, `WorldDestinationItems`, `UnlistedItemRouter`).
- 변경: `ItemActionDispatcher.java`의 정적 호출 대상만 조정.
- 기존 메서드 34개 전체 본문과 기본 경로 14개 실행 블록의 공백 포함 텍스트를 유지.
- 기본 경로 13개 조건식의 원문·평가 순서·괄호·우선순위 보존. 신규 예외 정책, 대기열, 스레드, 트랜잭션 호출 순서 변경 없음.
- 전용 검증: 원본 핸들러 SHA-256 fingerprint, 14개 기본 처리 실행 블록 SHA-256 fingerprint, 실제 신규 라우터 Java 21 컴파일 및 구/신 분기 794,118개 아이템 ID 비교, 15개 특수·경계 ID 시험.
- 전체 Java 21 구문 검사, 이전 아이템 및 방송 114차 회귀 테스트 수행. 전체 외부 의존성 타입 컴파일·실제 CacheD/MySQL/게임 내 검증 미수행.
