# RKern 아이템 사용 기능별 클래스 분리 — 2026-11-17

## 기준과 범위
- 기준 전체 SRC: `RKern_ItemFunction_DomainContinuation_47Methods_FULL_SRC_20261117.zip`.
- 변경은 `src/l1j/server/server/model/item/function` 내에서만 수행했다. `docs/item_domain_split_20261117/tests/stubs`의 테스트 전용 클래스를 새 이름에 맞게 변경했다.
- 네트워크·브로드캐스트·CIO·DB·CacheD·NPC·전투 생산 소스 및 Native DLL 수정 없음.
- 아이템 효과, 조건, 소비, 보상, SQL, COMMIT/ACK 및 커밋 후 메시지 코드 본문은 그대로 옮겼다. **성능 최적화나 비즈니스 규칙 변경 없음.**

## 실행 결과
- `DurableItemActions`, `WorldItemHelpers`의 28개 Java 메서드(오버로드 포함)를 20개 전용 클래스로 이동. 해당 2개 범용 클래스 제거. 호출부가 새 클래스를 참조하도록 연결.
- `WorldItemActions`의 97개 아이템 ID, 85개 처리 그룹을 7개 클래스에 분리. 기존 원본 ID 분기 메서드는 라우팅만 담당.
- `TravelAndRecoveryItems`의 43개 ID, 33개 처리 그룹을 8개 클래스로 분리. 9개 기존 좌표·자동 버프 배열은 `TravelUseTables` 한 군데로 옮기고 데이터 보존.
- `CraftAndBuffItems`의 28개 ID, 18개 처리 그룹을 9개 클래스로 분리하고 `SupplementalCraftRouter`로 교체.
- `ConsumableRewardItems`의 59개 ID, 44개 처리 그룹을 8개 클래스로 분리하고 `SupplementalRewardRouter`로 교체.
- **총 신규 생산 Java 55개**: 도메인 담당 클래스 52개, 라우터 2개, 테이블 1개. 삭제 4개. 수정 70개. 기준 1,756개에서 1,807개 Java로 증가.

## 검사
- 수정 전/후 **28개 전체 메서드 본문 동일**(정확한 신규 클래스 명칭으로의 심벌 참조 변경만 허용).
- **180개 switch 처리 그룹의 전체 코드 문자열 및 227개 ID 라벨 일치**. 대상 거래 코드의 중첩 SQL/트랜잭션 본문도 원본 그대로 옮겼다.
- **Java 21 라우터 실제 컴파일·243개 ID 분기 실행 테스트 통과**(원래 존재하지 않는 ID 포함).
- 기존 분기 203개 그룹, 794,118개 ID, 패키지 지급 1,755개 입력, 주문서/대화 회귀검사 통과.
- 기존 Native 방송 114차 회귀검사 통과.
- Java 21 구문 파서: 생산 소스 1,807개 오류 0건.
- 최종 ZIP 파일 엔트리/CRC/재추출 검사 결과는 최종 패키징 시 재확인.

## 남은 검증
- 전체 외부 의존성 타입 컴파일, 실제 MySQL/CacheD, Windows DLL 및 인게임 아이템 장기 검증은 수행하지 않았다. 코드 구조 검증을 통과해도 실서버에서 버그가 절대 없다는 뜻은 아니다.
- 이미 기능별로 구분된 전용 클래스(`ArmorEnchantScrollActions`, `RuneRewardPackages`, `TransformationItems`, `PotionEffects` 등)는 이번 범위에서 무리하게 다시 분해하지 않았다. `SupplementalItemActions` 및 `ItemActionDispatcher`는 기존 진입·검증과 라우팅 경계로 유지했다.
- 이전 `item_domain_continuation`의 일부 테스트는 삭제된 클래스와 이전 호출부의 *과거 SHA*를 검사하는 용도여서, 새 6차 분리본에는 `docs/item_domain_phase6_20261117`의 새 소스·라우터 검증을 사용한다.

## 재검증
```bash
bash docs/item_domain_phase6_20261117/run_tests.sh
```
