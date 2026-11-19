# RKern 아이템 기능별 클래스 분리 11차 — 검증 기록 (2026-11-18)

**기준 전체 SRC:** `RKern_ItemFunction_DomainSeparation_Phase10_FULL_SRC_20261118.zip`.

## 변경 사항

- `WeaponEnchantScrollActions`(기존 448줄) : 진입점/STOP 정책만 남김. 원본 코드를 `WeaponEnchantTargetRules`(대상/주문서 적합성), `WeaponEnchantLevelLimits`(레벨 상한·특수 차감), `WeaponEnchantScrollResolution`(소모·강화 실행), `WeaponEnchantChanceCalculation`(강화 확률 및 GM 진단 메시지)로 분리.
- `PotionEffects`(기존 451줄) : `usePotionCase`를 93개 ID 라우팅만 하도록 축소. 기존 분기 코드를 `HealingPotionConsumables`(회복 38종), `ExperiencePotionConsumables`(술/경험치 5종), `MovementPotionConsumables`(이동/가속 35종), `ManaProtectionPotionConsumables`(마나/지혜/호흡/투명 15종)으로 이동.
- 기존 `PotionEffects`의 10개 공개 효과 헬퍼는 그대로 유지. `WeaponEnchantScrollActions.use`와 `PotionEffects.usePotionCase` 호출 계약 유지.
- 인스턴스/스레드/큐/새 자료구조 생성 없음. 원래 `UseDurableConsume` lambda나 `removeItem` 발생 순서 변경 없음.

## 원본과 동작 비교

1. 10차 기준 무기 강화 대상·레벨·효과·확률 본문은 원본 문자열 비교로 동일. 확률 블록은 호출 하나로 교체 후 재결합하여 전체 효과 본문과 비교. `STOP`, 랜덤 추출, `SuccessEnchant`/`FailureEnchant` 위치가 동일.
2. 실제 `WeaponEnchantScrollActions`를 typed Java 21 stub과 함께 컴파일해 STOP/COMPLETE 4개 정책 확인.
3. 실제 `WeaponEnchantChanceCalculation`과 10차 원본을 Java 21에서 동시에 실행하여 **1,050입력**에서 반환값, 확률 테이블 호출 및 GM 진단 메시지 순서 동일성 확인.
4. 실제 무기 강화 분리 **생산 Java 5개 파일**(진입·대상·레벨·효과·확률)을 fake typed game model과 컴파일. null, 장착 중, 무기 아닌 대상, 기본 정상 강화의 4개 경계 동작 확인.
5. 물약 분기 원문 일치, 전체 **93개 ID 중복/누락 없음**, 기존 공개 효과 헬퍼 10개 변경 없음. 실제 11차 생산 라우터·4개 효과 클래스와 10차 원본을 Java 21에서 동시 컴파일 후 **20,592입력**(93종 + 무효 ID 6개 × 맵 13종 × 직업 8종 × 물약 조회 실패 여부 2종)에 대해 반환값 및 효과/소모 순서 비교: 모두 동일.
6. Java 21 전체 생산 Java 파일 구문 분석 **1,877개 오류 0건**.
7. 10차·9차·8차 아이템 회귀, 인형 4,129입력 비교, 방송 114차 실패 10시나리오/5,000 수신자 검증 재통과.
8. ZIP CRC, 기준 SRC 대비 변경 범위, 무변경 파일의 바이트 동일성 확인.

## 한계

전체 외부 의존성을 포함한 게임 전체 타입 컴파일, 실제 MySQL/CacheD 거래·재접속·인게임 강화/물약·5,000명 동접 검증은 수행하지 않았다. 테스트용 fake 모델은 게임 DB나 온라인 상태를 모사한 것이 아니다. 기존 사업 로직 버그 가능성을 새 구조가 자동으로 해결했다는 뜻은 아니다.
