# RKern 아이템 사용 리팩토링 198~203차 (2026-11-19)

기준 ZIP: `RKern_ItemUse_DomainConsolidation_Memory_Phase195_197_FULL_SRC_20261119.zip`.
기존 1~197차 전체 생산 코드를 보존하면서 아이템 사용 처리의 소형 라우터/중복 동작을 축소하였다.

## 변경 내역

- **198차 / 장비 사용 경로**: `ItemActionService`가 `type2 != 0` 장비 사용 시 대상 아이템 조회 및 `ItemActionContext` 할당 없이 `ItemActionDispatcher.useEquipment()`로 직접 실행한다. 종료는 기존과 같은 `L1ItemDelay.onItemUse`를 호출한다. `type2==0` 소모품은 기존 컨텍스트와 준비/완료 흐름을 그대로 사용한다.
- **199차 / 강화 주문서**: `WeaponEnchantScrollActions`와 `ArmorEnchantScrollActions`를 `EnchantScrollActions`로 통합. 공통 소유 검사 이후 장비별 타겟·레벨·강화 적용 순서를 보존했다.
- **200차 / 귀환·순간이동**: `TravelAndRecoveryItems`에서 기존 `ReturnAndTeleportItems`의 13개 ID를 직접 분기한다. 중복 중간 라우터 클래스를 제거했다.
- **201차 / 용해 주문서**: `ItemDissolutionScrolls`의 두 가지 단일 위임 분기를 `ItemActionDispatcher`에 직접 반영. 기존 STOP 반환 유지.
- **202차 / 보상·위탁**: `HeroBundleItems` 및 `ConsignmentVoucherItems`의 무상태 단일 아이템 래퍼를 제거. `SupplementalCraftRouter`에서 동일 receipt/등록 함수를 직접 호출.
- **203차 / 용해제 도메인 정리**: `DragonResolventItems`를 `OrdinaryResolventItems`에 통합. 드래곤볼 ID 경계, 결정 수 확인, 메시지와 settlement 인자를 보존.

생산 Java: 기존 파일 수정 5개(`ItemActionService`, `ItemActionDispatcher`, `TravelAndRecoveryItems`, `SupplementalCraftRouter`, `OrdinaryResolventItems`), 신규 1개(`EnchantScrollActions`), 삭제 7개. 순감 6개 클래스, 관련 아이템 사용 서비스의 장비 경로에서는 컨텍스트 임시 객체 1개/호출 제거.

## 회귀 검증

`python3 docs/item_use_refactor_phase198_203_20261119/test_phase198_203.py`:

- Java 21 typed harness에서 **640개** 아이템 서비스 시나리오 원본/변경 실행 결과 비교. 테스트 장비 성공 경로에서 신규 컨텍스트 할당 0회 확인.
- 실제 원본/변경 강화 스크롤 단계 메서드 컴파일·비교 **512개** (검사·처리 순서, STOP/COMPLETE).
- 귀환·회복 아이템 ID 라우팅 **44개** 원본/변경 동일.
- 용해 주문서 **2개** 기존 STOP 및 호출 동일.
- 영웅 패키지·위탁 증서 포함 기타 아이템 라우팅 **30개** 동일.
- 드래곤 결정 용해 **70개** 경계/실패/성공 경우 원본/변경 동일.
- 삭제된 7개 클래스에 대한 생산 소스 참조 0건.
- JDK 21 `JavacTask.parse()` 전체 생산 Java **1,910개**, syntax errors **0**.

## 엄격한 범위

게임 아이템 ID, 강화 성공률/성공·실패 효과, CacheD 영속 트랜잭션, NPC, DB, 네트워크, 방송, 타이밍 휠 수정 없음.

검증한 것은 소스 구문 및 **모의 실행 경로 등가성**이다. 생산 전 의존성 전체 타입 컴파일, 실제 MySQL/CacheD 장애·동시접속, 인게임 동작, JFR 할당량/성능 실측은 수행하지 않았고 통과했다고 주장하지 않는다.
