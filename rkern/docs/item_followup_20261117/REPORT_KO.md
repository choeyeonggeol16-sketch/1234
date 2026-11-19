# RKern 아이템 Function 후속 정리 · 검증 보고서 — 2026-11-17

## 기준
- `RKern_ItemFunction_DomainHandlers_NoItemUse_FULL_SRC_20261117.zip` 전체 SRC 기준.
- **아이템 처리 코드만 변경**, 기존 아이템 기능 클래스 분리/이름 변경을 그대로 보존.
- CIO/CacheD/DB/네트워크/브로드캐스트/Native DLL은 변경하지 않음. 새 스레드·큐·워크 실행기 없음.

## 생산 Java 변경 (4개)
1. `ItemActionService.java`: 아이템 사용 허용·레벨 제한 확인과 스타터/보물상자 전용 선처리가 끝난 다음에만 `ItemActionContext`를 생성. 거부/전용 처리에서 불필요한 컨텍스트 객체 및 대상 인벤토리 조회를 없앰. FINEST 로깅의 필드값과 위치를 유지하도록 컨텍스트 없이 해석.
2. `ItemActionPreparation.java`: 스팅 선택의 이전 `setArrow(...)` 오기입을 `setSting(...)`으로 교정. 화살/스팅 현재 선택 조회 결과를 재사용해 `findItemId()` 재탐색을 줄이고, 널 이전 항목 송신을 피함. 기존 패킷 종류와 순서는 유지.
3. `ItemActionCompletion.java`: 충전 마지막 회 사용 시 실질적으로 저장하지 않던 `Timestamp` 할당을 제거. 남은 충전 수, 제거/저장 호출과 아이템 사용 지연 호출 흐름은 보존.
4. `MiscItemActions.java`: 광역 스턴 범위 안 대상마다 `new Random()`과 5원소 배열을 생성하던 루프를 호출당 `ThreadLocalRandom` 1회 조회와 계산식으로 변경. 2,000~4,000ms(500ms 간격) 5개 시간과 `<50 / 1..100` 확률 정책 유지. 개별 난수열은 동일하지 않지만 결과 분포를 유지하도록 검증.

## 검증
- `docs/item_followup_20261117/tests/test_hotpath.py`: **실제 생산 `ItemActionService`/`ItemActionPreparation`/`ItemActionContext`/`ItemActionCompletion`/`ItemActionResult`를 Java 21 typed stub와 컴파일·실행**. 거부 시 대상 조회 없음, 스타터/보물상자 대상 조회 없음, 정상 dispatch 1회 조회, 스팅/화살 선택, 충전 1회/잔여 회 사용 완료 검증. 통과.
- `docs/item_followup_20261117/tests/test_stun_rng.py`: 생산 스턴 확률/시간 계산식을 추출해 Java 21 500,000회 실행. 5개 지속시간 범위 및 확률 임계 동일. 대상 루프의 할당 구조 점검. 통과.
- 기존 `docs/item_function_refactor_20261117/run_tests.sh` 통과: Java 1,713개 구문 오류 0, 특수 마안 7·수표 3·레이드 3·1회 마안, 대상 선택, 9개 보상 배열, 설정 캐시 10만 회 검증. 114차 대표 방송 회귀 포함.
- 최신 기준 ZIP과 대조해 생산 Java 변경이 상기 4개뿐임을 확인. 배포 ZIP CRC 및 재추출 후 신규 검증 예정/결과 로그 별첨.

## 한계
- Java 1,713개 **구문 분석**은 완전한 의존성 타입 컴파일과 다름. 외부 JAR가 없어 전체 게임 타입 컴파일 미실시.
- 인게임 화살/스팅 장착, 실제 MySQL·CacheD 영속 원자성, 장시간 부하 테스트 미실시.
- Random 변경의 확률 *분포*를 검증했으며 난수열 동일성은 보장하지 않음. 성능·메모리 개선은 구조상의 할당/조회 제거이며 정량 벤치마크 미수행.
