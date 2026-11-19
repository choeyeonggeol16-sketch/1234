# RKern 아이템 19차 — 과분리 통합 및 사용 경로 할당 감소

기준: `RKern_ItemFunction_Consolidation_Memory_Phase18_FULL_SRC_20261118.zip`

## 변경 범위

- 직업별 스타터 장비 클래스 8개를 `RecruitStarterKit`으로 병합. 8개 직업의 독립적인 `if` 분기 및 지급 순서는 그대로 유지. 실제 아이템 생성·인벤토리 저장 책임인 `RecruitStarterItemGrant`는 보존.
- `IceBuffAllUse`·`IceBuffSingleUse`·`IceBuffEffects`를 `IceBuffItem`으로 병합. 기존 `ItemReceiptOperations.UseDurableConsume`의 콜백 및 효과 적용 순서, 대상 ID/지속시간/스킬 목록은 변경하지 않음.
- `HastePotionEffects`·`BraveryPotionEffects`·`FruitPotionEffects`를 `MovementPotionEffects`로 병합. 기존 `PotionEffects` 호출 경로와 동작 본문 유지.
- 스킬 재사용 대기 및 일부 진단 메시지에서 `String.format(... %d ..., int)` 22개 호출을 primitive 문자열 결합으로 변경. Formatter와 가변인수 박싱에 따른 임시 할당을 회피함.
- 신규 스레드·큐·서비스·인스턴스 생성 경로 추가 없음. 방송·네트워크·NPC·DB 공통 코드는 변경하지 않음.

## Java 21 검증

| 검증 | 결과 |
|---|---|
| 전체 생산 Java 구문 분석 | **1,940개, 오류 0건** |
| 이전 18차 소스와 추출 메서드 본문 비교 | **21개 동일** |
| 병합 전후 스타터 클래스 실컴파일 및 실행 | **1,024개 입력 로그 일치** |
| 병합 전후 빙수 실컴파일 및 실행 | **960개 입력 로그 일치** |
| 병합 전후 이동·용기·열매 물약 실컴파일 및 실행 | **17,280개 입력 로그 일치** |
| `%d` 문자열 메시지 결과 비교 | **1,232개 입력, ko/US/JP/ROOT 로케일 일치** |
| 기존 17·18차 및 12·16차 관련 회귀검사 | 통과 |
| 방송 114차 실패 주입·5,000수신자 회귀검사 | 통과 |

## 할당 실측(별도 마이크로벤치)

JDK 21, `ThreadMXBean` 기반 코리안 스킬 재사용 대기 메시지 100,000회 실행 중 단일 스레드 할당 측정: 

- 기존 `String.format`: 약 **595.9 B/호출**
- 변경 문자열 결합: 약 **102.9 B/호출**

이 수치는 **해당 메시지 생성 구문의 단독 샘플**이며 실제 게임 서버의 전체 힙·GC·처리량 감소를 뜻하지 않음. 시스템 기본 로케일이 비라틴 숫자를 사용한다면 `%d`의 숫자 표기가 달라질 수 있으므로, 메시지 출력 경로가 한국어 JVM 설정이라는 전제에서 적용함.

## 유지 및 미검증

- 스타터 패키지는 원래 아이템 소모 후 장비를 지급한다. 인벤토리 장애 시 원자성 문제가 될 수 있는 **기존 실행 구조는 이번에 의도적으로 변경하지 않음**.
- CacheD debit/receipt/commit 이후 효과 처리, 난수 호출, 아이템 소모·지급·효과 적용 순서의 관련 코드 본문은 유지함.
- 전체 의존성 타입 컴파일, 실제 게임 서버·MySQL/CacheD ACK 손실·재접속, 5,000동접 실서비스 부하는 **미검증**.

검증 스크립트: 이 폴더의 `test_phase19_*`. 필요한 과거 생산 소스 조각은 `baseline/`에 동봉(직전 전체 ZIP 없이도 비교 재실행 가능). `test_phase19_structure.py`는 직전 18차 ZIP이 환경에 있으면 미변경 전체 파일까지 추가 검증함.
