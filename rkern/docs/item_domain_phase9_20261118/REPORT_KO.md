# 9차 아이템 기능별 클래스 분리 보고서

기준: `RKern_ItemFunction_DomainSeparation_Phase8_FULL_SRC_20261117.zip` (누적 전체 SRC)

## 변경 내용

- CacheD 거래: `DurableGoodsItems` 6개 메서드를 `TamPointBoxReceipt`, `UncurseScrollReceipt`, `LanternOilReceipt`, `HeroPackageReceipt`, `CharacterSlotReceipt`, `EquipmentSlotUnlockReceipt`로 분리.
- 아이템 전투 효과: `StunCombatItems`, `ShadowCombatItems`, `CommandCombatItems`, `MagicCombatItems`에 있던 8개 기술과 보조 MR 계산을 기술별 Java 파일 8개로 분리.
- 매직 실드·군주 광역 스턴·비전 텔레포트 3개를 별도 Java 파일로 이동하되 외부 정적 API `CombatSkillItemEffects`는 호환 가능한 전달자로 유지.
- 위 다섯 개 대형 묶음 클래스를 삭제. 기존 호출자 7개와 CombatSkillItemEffects의 호출만 수정.

생산 Java: 신규 17, 수정 8, 삭제 5 (총 30개 경로에 변화). 새 스레드·큐·별도 인스턴스 할당 없음. `Random` 필드는 원래 공유 `CreatureItemActions._random`을 계속 참조합니다.

## 검증

- Java 21 구문 검사: 1,856개 생산 Java, 파싱 오류 0건.
- 원본 8차 ZIP과 메서드 18개 본문을 **모두 바이트 단위 동일 비교**: 통과. 상세 SHA-256은 `MANIFEST.json`.
- CacheD 거래 호출자 6개: 호출 대상 클래스만 치환된 것을 원본 전체 본문 SHA-256으로 비교: 통과.
- `CombatAbilityItems` 공개 진입점 9개: 호출 대상만 변경된 것을 원본 SHA-256으로 비교: 통과. 실제 Java 21 타입 스텁을 사용한 운영 코드 파사드 컴파일/호출 검사 통과.
- `CombatSkillItemEffects` 외부 진입점 3개: 새 기술별 클래스 위임 및 외부 메서드 시그니처 보존 확인.
- `ItemActionDispatcher.java`: 8차와 byte-identical.
- 아이템 라우터 52개 ID 테스트, 룬/아이템 라우터 243개 ID 테스트, 인형 잠재력 4,129개 차분 실행, 스턴 확률 500,000회 범위 검사, 방송 113차 회귀검사: 통과.

> 기존 `docs/item_domain_continuation_20261117/test_domain_preservation.py`의 이전 caller-hash 기준표는 이미 8차에서도 이전에 삭제된 `WorldItemHelpers.java` 등을 포함해 최신 기준에서는 독립 실행이 불가능합니다. 9차의 분리 메서드 및 최신 호출 경로 동일성은 본 폴더의 `test_phase9.py`로 새 기준을 고정했습니다.

## 미검증

외부 의존성 JAR를 포함하는 전체 서버 타입 컴파일, MySQL/CacheD 실거래, 실게임 시전·장비·확률 검증, 5천 명 동접 검증은 수행하지 않았습니다. 아이템 로직 및 서버 공통 동작 자체는 의도적으로 변경하지 않았습니다.

## 실행

```sh
python docs/item_domain_phase9_20261118/test_phase9.py
python docs/item_followup_20261117/tests/test_stun_rng.py
python docs/item_domain_phase8_20261117/test_router.py
python docs/item_domain_phase8_20261117/test_doll_runtime.py
```
