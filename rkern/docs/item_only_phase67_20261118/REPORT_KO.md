# RKern 아이템 전용 67차 — 변신 카드 표시·자동 선택 메모리 개선

**기준:** `RKern_ItemOnly_Consolidation_Memory_Phase66_FULL_SRC_20261118.zip` 전체 소스. 66차까지 누적 변경 유지.

## 수정 범위

- `src/l1j/server/server/model/item/Card/L1CardMythList.java`: 도감 열기마다 생성하던 `int[8][5]`(배열 9개)를 4개의 클래스 고정 이미지 표와 8개 카운트용 `int[]` 1개로 교체. 기존 계정 getter 평가 순서, 카드 이미지·수량 출력 순서, 성별 분기는 유지.
- `src/l1j/server/server/model/item/Card/L1CardMyPast.java`: 후보마다 생성하던 `CardData`를 201~211번 고정 불변 객체 11개로 재사용. 매 호출의 `ArrayList`와 `Collections.shuffle(List)`는 유지하여 셔플 호출·후보 순서·선택 로직 보존.
- `src/l1j/server/server/model/item/Card/L1CardLegendList.java`: 길이 0인 `new String[0]` 대신 기존 빈 인자 전송 오버로드 호출. 패킷 인자 개수는 동일.

**새 생산 클래스, NPC, 네트워크, 방송, DB, CacheD 코드 변경 없음.** `CS_NpcAction.java` 포함 66차의 다른 생산 파일들은 바이트 동일.

## 실제 검증

- JDK 21 전체 생산 Java 1,922개 **구문 파싱 오류 0**.
- 원본 66차와 67차의 3개 생산 클래스를 Java 21로 타입 컴파일하여 동작 비교: **120,000건 일치**. 신화 도감 카드 정보·목록, 전설 도감 카드 정보·목록, 신화 합성 자동 선택.
- 후보 카드 종류 0~11개 경계 **12건 일치**. 합성 비교의 RNG는 테스트 코드에서만 동일 시드 `Collections.shuffle(list, Random)`으로 주입. 실제 생산 코드는 기존 `Collections.shuffle(List)` 그대로 유지.
- 66차의 물약 클래스 통합·고정 배열·난수 회귀검사, 31차 스타터 521건 및 32차 선물상자 1,381건 통과.

## 할당량 (정해진 마이크로벤치 환경 한정)

JDK 21 `ThreadMXBean.getThreadAllocatedBytes`, 동일 테스트용 계정/패킷 모델, 호출 25,000회 예열 뒤 70,000회 평균.

| 메서드 | 기존 | 변경 | 차이 |
|---|---:|---:|---:|
| 신화 도감 `cardinfo` | 약 5,216B/호출 | 약 4,896B/호출 | -320B |
| 신화 카드 `L1PastSelct` | 약 504B/호출 | 약 328B/호출 | -176B |

이는 동일 테스트 모델의 할당량이지, 실제 게임 전체 GC/힙 절감량은 아니다. 클래스에 유지되는 정적 불변 표의 수명 비용은 별도로 존재한다.

## 미검증

외부 의존성을 모두 포함한 서버 전체 타입 컴파일, 인게임 실제 클라이언트/UI, 실제 DB 및 CacheD 장애 재현은 수행하지 않았다. 위 타입 컴파일은 수정 생산 클래스 3개와 테스트 모델의 의존 경계를 확인한 것이다.

## 재실행

```sh
bash docs/item_only_phase67_20261118/run_tests.sh
bash docs/item_only_phase67_20261118/run_tests.sh --bench
```
