# RKern 아이템 159~163차 누적 수정 / 2026-11-18

## 기준·변경 범위
기준: `RKern_ItemOnly_EnchantFailure_ReceiptNotice_MultiPhase155_158_FULL_SRC_20261118.zip` (158차).
생산 Java 5개 변경. 아이템 처리 패키지 이외의 생산 코드는 바이트 동일.

## 수정 내용

### 159차 — 무기 특수 강화 주문서의 효과·소모 순서
`WeaponEnchantScrollResolution.java`: 68076, 724, 810003, 4100148의 기존 직접 소모 분기에서 성공 강화나 결과 안내보다 주문서 차감을 먼저 시도하게 변경. 성공·실패 안내, GM 안내의 RuntimeException을 경제 처리와 분리. 724의 허용 무기 목록에 `targetItem != null` 괄호 적용. **기존 성공 확률·ID 목록·RNG 호출은 변경하지 않음.**

### 160차 — 방어구 특수 강화 및 경계 검사
`ArmorEnchantScrollResolution.java`: 98077, 68077, 3000380, 4000518, 3000518, 3000517의 직접 소모 분기에서 주문서 차감을 결과 처리보다 우선. 패킷 안내 실패 분리. 3000380은 `Config.accessoryLevel`과 같아도 추가 강화하지 않도록 경계값 수정 (`>`→`>=`). 할파스 방어구 ID 판정의 OR 그룹 명시. 기존 3000517 파괴 시 이중 CacheD receipt 경로는 그대로 유지.

### 161차 — 강화 대상의 인벤토리 잔존 수량 검사
`SpecialEnchantScrolls.java`: 동일 인벤토리의 동일 객체 확인 외에 주문서/대상 템플릿 유효성과 양수 수량 검증. 이미 소모된 수량 0 객체 재사용 차단.

### 162차 — 강화값 저장 요청 거절 시 메모리 복원
`EnchantmentOutcomeEffects.java`: 강화 수치를 변경한 뒤 CacheD 비동기 저장 제출이 즉시 예외를 내면 원래 강화값으로 복구하고 해당 예외를 전파. 성공한 비동기 제출의 DB 커밋 실패를 자동 복구한다는 뜻이 아님.

### 163차 — 충전 아이템 사용 시각/횟수 저장 및 알림 분리
`ItemActionCompletion.java`: 남은 충전 아이템의 chargeCount와 lastUsed를 함께 CacheD 저장 요청 (`COL_CHARGE_COUNT | COL_DELAY_EFFECT`). 완료 콜백에서도 아이템 상태 저장 요청을 전송보다 우선. 저장 제출 실패 시 메모리 시간/충전값을 복원하고 예외를 전파. 상태 패킷 전송 실패는 로깅한 뒤 item delay 진행. CacheD transfer gate 거절 예외는 숨기지 않음.

## 검증
- 실제 생산 파일을 Java 21 타입 스텁과 컴파일/실행: 무기 900, 방어구 321, 완료 처리 1,980, 강화 저장 14 조건 통과.
- 158차 기존 production method 검사(동일 변경본): ARMOR 18, ENCHANT 13, RECEIPTS 12 조건 통과.
- 기존 158차 패키지의 과거 펫·소환·아이템 회귀 러너는 기준 ZIP에서 통과. 현재 변경본과 무관한 이전 production 코드와 모의검사 로직은 보존.
- JDK 21 파서 전체 `src` 생산 Java 1,923개: 오류 0.
- 158차 ZIP과 항목 단위 byte comparison 및 최종 ZIP CRC 검증: 통과.

## 엄격한 한계
- **이번 변경은 주문서 차감과 장비 강화값 저장을 단일 CacheD receipt로 완전 원자화한 것이 아님.** 여전히 별도 비동기 저장 경로인 성공 강화 일부는 SQL 롤백/ACK 유실 시 복제·유실 위험이 남는다.
- DB 커밋 후 펫/소환 월드 생성 실패 보상, 계정 카드 수량 자동 재동기화는 미완성.
- 전체 프로젝트 의존성 컴파일과 실제 MySQL·인게임·동시접속/다중 세션 장애 검증은 미수행. Java typed tests는 모의 의존성 기반.
