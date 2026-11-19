# RKern 아이템 기능별 분리 8차 — 작업·검증 기록

## 기준

- 원본: `RKern_ItemFunction_DomainSeparation_Phase7_FULL_SRC_20261117.zip`
- 대상: `src/l1j/server/server/model/item/function/` 및 8차 회귀 테스트.
- 방송·네트워크·CIO·NPC·DB 구현과 그 외 게임 로직은 변경하지 않음.

## 변경

1. 기존 `ActiveSkillItems.java`(630줄) 제거. 버서크(70706), 프라임(70708), 매스 이뮨(70733), 할파스(70710), 포텐셜(70714), 버닝샷(44001), 리버레이션(70735) 사용 본문을 7개 클래스에 이전. `AdvancedAbilityItems`는 각 클래스를 직접 호출. 잠재력 주문서(880800)와 이뮨 블레이드(70740)는 기존 전용 처리기로 직접 호출. 프라임의 공성 판정 보조 함수는 프라임 클래스로 이전.
2. `SpecialEnchantScrolls`의 **공개 시그니처는 유지**하고 9개 주문서의 실행 본문을 악세서리 축복, 방어구 축복, 무기 축복, 무기 진화, 옵션/품질 부여라는 5개 클래스에 이전. 보호 대상·인벤토리 소유 판정/난수/CacheD 영수증/COMMIT 후 알림의 순서를 그대로 보존. 공개 공통 판정(`isOwnedScrollTarget`, `isAccessoryEnchantTarget`, `qualityBonus`)은 기존 클래스에 유지.
3. `DollEffectItems.RemoveDollPotential`의 31개 효과 해제를 활력·방어 7개, 기본 능력치 5개, 전투 8개, 내성·적중 11개의 4개 무상태 클래스로 순서대로 이전. 외부 공개 메서드 이름 유지. 마지막 `SC_OwnCharAttrDef.send(pc)`의 상대적 순서도 그대로 유지.
4. 신규 생산 Java **16개**, 변경 생산 Java **3개**, 삭제 생산 Java **1개**. 새로운 스레드·큐·동적 실행 객체 없음.

## 검증

- `verify_extraction.py`: 실제 이전 코드 **스킬 7개, 주문서 9개, 인형 효과 31개**의 원본 본문 SHA256 일치와 위임 순서 검증.
- `test_router.py`: Java 21 `javac`로 **실제 `AdvancedAbilityItems.java`**와 타입이 정해진 테스트 더블을 컴파일하고 ID **52개 전수** 및 미등록 ID를 실행 검증.
- `test_doll_runtime.py`: 실제 `DollEffectItems`와 신규 분리 클래스 4개를 Java 21로 타입 컴파일하고, **4,129개 스킬 마스크**를 실행하여 분리 전 7차 원본과 상세 상태·패킷 호출 로그 SHA256 완전 일치 검증 (`5f6b88cf860e2045c3e24d5916fac3e51793708b0456d407db654b5c311fdd17`).
- `docs/item_domain_phase7_20261117/run_tests.sh` 포함 이전 스위치 라우터, 상자 지급 **1,755개 입력**, 미등록 아이템 **794,118개 ID**, 특수 주문서, 방송 114차 모의 회귀 모두 통과.
- JDK 21 전체 생산 소스 구문 검사 **1,844개, 오류 0개**.
- ZIP CRC, 전체 원본 대비 변경 파일 검산, 재열기 및 파일 무결성 확인.

## 범위 밖

전체 게임 의존성 타입 컴파일, 실제 게임/Windows/실제 DB 트랜잭션 시험은 수행하지 않음. 이후 큰 단일 기능 파일(`ArmorEnchantScrollActions`, `LevelPolymorphScrollEffects` 등)은 미분리 상태이며, 다음 작업 시 각각 별도 검증이 필요.
