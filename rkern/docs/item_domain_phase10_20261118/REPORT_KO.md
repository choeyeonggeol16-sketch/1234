# RKern 아이템 기능별 클래스 분리 10차 — 검증 기록

**원본:** `RKern_ItemFunction_DomainSeparation_Phase9_FULL_SRC_20261118.zip`.
**변경 범위:** `src/l1j/server/server/model/item/function/` 15개 Java 파일만 (신규 13, 기존 수정 2).

## 방어구 강화
- `ArmorEnchantScrollActions.use`는 기존 디스패처 시그니처를 유지한다.
- `ArmorEnchantTargetRules.validate`: 대상 아이템 종류, 허용 주문서, 장착·교체·축복 조건 및 이력 검증.
- `ArmorEnchantLevelLimits.validate`: 강화 단계별 상한 처리. 기존 특정 주문서 소모/감소 효과까지 원본 위치 그대로 보유.
- `ArmorEnchantScrollResolution.apply`: 실패/성공 및 특수 아이템 효과 원본 분기.
- `ArmorEnchantChanceCalculation.calculate`: 악세서리·방어구 확률 및 GM 디버그 메시지. 원본 9,164자 블록을 그대로 이동.
- **검사 순서:** TargetRules → LevelLimits → Resolution; 어느 단계든 STOP이면 다음 단계 미실행. per-use 객체 생성 없음. RNG 사용 시점 및 캐시·효과·아이템 차감 분기 유지.

## 레벨 변신
- `LevelPolymorphScrollEffects.useLevelPolyScroll`의 공개 호출 시그니처 유지.
- 30·40·52·55·60·65·70·75·80레벨 주문서(9개)를 전용 `LevelNNPolymorphForm`으로 분리.
- 기존 캐릭터 클래스/성별 변신 ID 계산식과 기본값 `polyId=0`, 지속 1,800초, MORPH_BY_ITEMMAGIC 동작을 유지.
- 다른 변신/마법/패킷 처리 파일 변경 없음.

## 검사
1. 9차 소스에서 잘라낸 방어구 대상·상한·실행/확률 코드 조각 및 9개 레벨 변신 블록의 원문 동일성을 확인.
2. 실제 10차 변신 진입점/전용 9개 클래스와 9차 진입점을 Java 21에서 동시 컴파일한 뒤 **560개 입력 조합**(9개 유효 ID + 5개 무효 ID × 클래스 10개 × 성별 4개) 호출 결과와 doPoly 인자를 비교해 전부 일치.
3. 실제 ArmorEnchantScrollActions 라우터를 Java 21 typed stubs와 컴파일하여 STOP/COMPLETE 네 가지 후속 호출 경계 확인.
4. Java 21 전체 생산 소스 파싱: 1,869개 Java 파일, 오류 0건.
5. 9차 메서드 이전 회귀, 8차 라우터 및 인형 잠재력 4,129입력, 방송 114차(실패 10 시나리오 + 5,000 수신자) 재검증 통과.
6. 최종 ZIP CRC 및 9차 대비 무변경 항목 바이트 동일성 비교.

참고: `docs/item_domain_phase7_20261117/verify_extraction.py`는 7차 당시 `useLevelPolyScroll` 본문 SHA256 고정 비교 테스트이므로 10차에선 현재와 맞지 않는다. 10차는 원문 비교 및 실 Java 실행 대조 테스트로 검증한다.

**아직 미확인:** 외부 라이브러리 및 전체 게임 의존성 타입 컴파일, MySQL/CacheD 실제 거래, 인게임 주문서 사용·전투·강화 동시성 장기 테스트. 위 테스트가 전체 런타임 안전성을 보증하지는 않는다.
