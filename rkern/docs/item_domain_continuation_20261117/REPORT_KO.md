# RKern 아이템 사용 클래스 분리 후속 단계 — 2026-11-17

## 기준

`RKern_ItemFunction_RemainingCases_DomainSplit_FULL_SRC_20261117.zip`에서 **아이템 기능 패키지 코드와 아이템 기능 전용 호출부만** 변경했다. 아이템 처리 정책·보상확률·DB SQL·트랜잭션 확정 시점·CacheD·네트워크·CIO·방송은 변경하지 않았다.

## 분리

기존 기능 혼합 클래스 6개 삭제: `EnchantmentItemCases`, `RewardItemCases`, `TransformationItemCases`, `TravelItemCases`, `MiscItemActions`, `RewardItemTransactions`.

신규 도메인 클래스 25개:
- `GemConversionItems`, `ItemCurrencyExchanges`, `TransformationCollectionCards`, `GiftPackageConsumables`, `ExperienceRecoveryConsumables`
- `DollPotentialEnhancementScrolls`, `ItemDissolutionScrolls`
- `TransformationListItems`, `TransformationScrollItems`, `TransformationPotionItems`
- `ResurrectionConsumables`, `SpecialMapEscapeItems`, `TargetPolymorphWands`
- `WandInspectionItems`, `CombatSkillItemEffects`, `DogFightEnhancementItems`, `NpcWandAndSpawnItems`, `WarriorSkillBookItems`, `PcRoomPassTransactions`, `CashBuffTransactions`
- `MoneyBoxTransactions`, `ExperiencePotionTransactions`, `ClassGiftRewardTransactions`, `GuardianFluteTransactions`, `RandomEnchantBoxTransactions`

원본 메서드 **47개를 실행 본문·선언까지 그대로 이동**하고, 해당 메서드의 호출 대상 클래스와 static import만 교체했다. 모든 분기·조건식·반환·메시지·아이템 소모·스킬 발동·SQL·COMMIT·ACK 처리 코드는 원본 그대로다.

## 소스 보존 검증

1. 메서드별 **원본 대비 SHA-256 내용 동일**: 47/47.
2. 바뀐 기존 생산 소스 22개 파일을 대상으로 호출 대상과 static import 치환을 원복하면 **원본과 바이트 일치**: 22/22.
3. 삭제한 클래스 6개에 대한 생산 코드 참조: 0.
4. 전체 Java 21 구문 파서: 1,756개, 오류 0.
5. 이전 아이템 도메인 검증: Dispatcher 203개 case, 기본 라우팅 794,118개 ID, 보상 1,755개 입력, 퀘스트 대화 63개 및 스크롤 회귀 통과.
6. 기존 방송 114차 테스트 통과.

위 테스트는 `run_tests.sh`로 재실행한다. `METHOD_MAP.json`과 `BASELINE_CALLER_SHA256.json`은 **원본 소스에서 채집한 비교 근거**다.

## 검증 한계와 남은 클래스

전체 게임 의존성 타입 컴파일·실제 MySQL/CacheD·Windows IOCP·인게임 실사용 검증은 미수행이다. 메서드 본문 보존은 리팩토링으로 인한 행위 변경 위험을 줄이지만 운영 무결성을 완전히 보증하지 않는다.

`DurableItemActions`, `WorldItemActions`, `WorldItemHelpers` 등은 아직 규모가 크다. 이들은 공통 트랜잭션 헬퍼·월드 아이템 작업과 결합돼 있으므로, 기존 호출 흐름과 실제 테스트를 확보한 후 단계별로 분리해야 한다.
