# RKern 아이템 기능별 클래스 분리 7차 — 2026-11-17

## 기준과 변경 범위
- 기준: `RKern_ItemFunction_DomainSeparation_Phase6_FULL_SRC_20261117.zip` 전체 SRC.
- 생산 Java 변경: `EnchantmentItems`, `PotionEffects`, `TransformationItems`, `RuneRewardPackages` **정확히 4개**.
- 신규 생산 Java **22개** (강화 효과 7개, 물약 효과 5개, 변신/랜덤 강화 5개, 레벨별 룬 보상 5개).
- 테스트 컴파일 대상 보완: `docs/item_domain_split_20261117/run_tests.sh` 하나.
- 네트워크/브로드캐스트/CIO/DB/CacheD/NPC/전투 생산 클래스는 수정하지 않았다.

## 분리 상세
- `EnchantmentItems`: 속성 주문서, 휘장·견갑·문장·룸티스·스냅퍼·오림 보호 주문서의 **11개 메서드**를 7개 전용 클래스에 이동. 외부 API를 유지하는 전달 메서드만 남김.
- `PotionEffects`: 경험치·가속·MP·지혜·가호·실명·캐시 주문서의 **10개 메서드**를 5개 전용 클래스에 이동. 기존 `usePotionCase` 분기는 유지.
- `TransformationItems`: 변신 주문서·물약·레벨 변신·랜덤 강화 수치·대상 변신의 **5개 메서드**를 5개 전용 클래스에 이동. 외부 static API는 보존하고 난수 공급원 `CreatureItemActions._random`은 동일한 인스턴스를 참조.
- `RuneRewardPackages`: **25개 아이템 ID**를 90·85·80·70·55레벨 룬 보상 전용 클래스 5개로 분리, 라우터는 기존 `stage()`를 유지. 지급 조건 및 `stageSealedReward` 호출 본문은 그대로 이동.
- 추가 객체/스레드/큐·DB SQL 변경 없음. 기존 소모 및 CacheD receipt 확정 경계 유지.

## 검증
- 신규 이동 **26개 원본 메서드**의 메서드 선언·실행 본문 SHA-256 일치. 전달 메서드를 원본으로 복원하면 기존 3개 원본 클래스가 byte-identical.
- 룬 보상 **25개 분기 본문**의 SHA-256 원본 대비 일치 및 라우터 분기 ID 일치.
- 기존 패키지 보상 **1,755개 입력 이벤트 스트림 원본 비교** 통과 (신규 룬 분리본까지 테스트 대상에 포함).
- Java 21 실제 typed test: 룬 보상 및 기존 패키지 지급 컴파일, 입력·실패 모드 검증 통과. 기존 라우터 243건/794,118 ID 분기 검증 통과.
- Java 21 생산 소스 **1,829개 구문 검사 오류 0**; 기존 아이템 및 방송 114차 회귀검사 통과.
- 전체 의존성 타입 컴파일, 실제 MySQL/CacheD 연결, 인게임 소모·변신·강화 장기검증은 별도 검증 필요.

## 재검증
```bash
bash docs/item_domain_phase7_20261117/run_tests.sh
```
