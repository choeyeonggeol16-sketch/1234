# RKern 아이템 185~188차 — 거래당 할당 최적화 / 안전성 검증

## 범위
- 기준: 184차 전체 SRC.
- 변경 생산 파일: `src/l1j/server/server/model/L1InventoryTransaction.java` **1개**.
- NPC AI / 네트워크 / 방송 / CacheD / DB SQL 구현은 변경하지 않음.

## 185차 — 단일 행 Entry 배열 할당 제거
이전에는 아이템 하나만 변경하는 영속 거래에도 `Entry[2]`를 생성했다.
지금은 `Object entries` 필드에 첫 `Entry`를 직접 보존한다. 두 번째 항목
추가 순간에만 `Entry[2]`로 승격한다. 외부 재사용 풀·전역 캐시는 없다.

## 186차 — 다중 거래의 추가 필드 제거
초기 실험에서 `singleEntry` 별도 필드를 추가하면 다중 거래당 8B가 늘어났다.
최종본은 기존 `Entry[]` 참조 필드를 `Object`로만 바꾸는 태그형 저장을
채택해 다중 거래 객체 크기를 유지한다. 불변식: 0행=null, 1행=Entry,
2행 이상=Entry[]. 9행 이상 생성하는 `TIntObjectHashMap<Entry>` 정책 유지.

## 187차 — 오류 콜백 바인딩 객체 제거
기존 두 생성자의 `move.onError(this::sqlFailed)`는 거래마다 메서드 참조
Consumer를 생성했다. 최종본은 거래 자체를 `Consumer<Throwable>`로 사용하고
`accept(failure) -> sqlFailed(failure)`로 연결한다. 오류 종류·CIO post·
격리·롤백 완료 처리 방식은 동일하다. CacheD 구현은 수정하지 않았다.

## 188차 — 비교·검증
- 실제 `entry/findEntry/addEntry/debit/grant/transfer` 메서드 추출·Java 21 타입 검사:
  **57,504개 조건 통과** (1, 2, 8, 9, 10, 144행 등 경계 및 인덱스 승격).
- 실제 184차 및 신규 `commitInternal` 추출·Java 21 모의 SQL/콜백 검사:
  **40,015개 조건 통과** (DB 사전값 검증·잠금·쓰기·재생 순서,
  SQL staging 실패, 성공/실패, release-after-effects 두 모드).
- 전체/부분 차감 용량 검사 과거 기준 대비 결과: **99,799개 조건 통과**.
- `sqlFailed` 동일 생산 메서드 및 신규 `accept` 추출·Java 21 오류 주입:
  **18,000개 조건 통과** (확정 롤백·불확실 결과·실패 알림 예외).
- 강화 보호 주문서 실행 710개, 무기 강화 1,536개, 특수 강화 거래 21개,
  장착 후처리 127개 검사 통과. 오래된 별도 테스트 2종은 현재 생산 코드의
  의존성 스텁 누락으로 실행 실패했으며 이 결과는 통과로 산입하지 않음.
- 전체 `src` Java 1,923개 JDK 21 파서 오류 0.

## 객체 할당 마이크로벤치 (Java 21, mock Entry, -XX:-DoEscapeAnalysis)
반복 600,000회, 스레드 할당량 `ThreadMXBean` 측정.

| 행 수 | 184차 | 최종 | 차이 |
| ---: | ---: | ---: | ---: |
| 1 | 64B/거래 | 40B/거래 | **-24B** |
| 2 | 80B | 80B | 0B |
| 3 | 128B | 128B | 0B |
| 8 | 256B | 256B | 0B |
| 9 | 784B | 784B | 0B |

오류 콜백을 별도로 모델링한 모의 측정에서 48B -> 32B (**-16B**).
이는 두 개의 독립 마이크로벤치 결과이며 실제 거래 총 절감량이나 GC
성능이 아니다. 실제 서버·CacheD 부하 측정과 구분해야 한다.

## 유지한 경계 / 미검증
`ItemSnapshot` capture 및 `verifyItemPreimage`, 기존 DB-stage 복사·
committed replay image 분리, owner gate, FIFO, CacheD receipt·오류 격리는
유지한다. 아이템 객체 재사용 풀, 새 스레드/큐 및 SQL 제거를 하지 않았다.
실제 MySQL 장애/ACK 손실·재접속, 전체 의존성 컴파일 및 인게임은 미검증이다.
코드 변경으로 카드 수량 자동 복구·일반 강화 전체 원자성·펫 월드 생성 보상
등의 미완성 항목이 해결된 것은 아니다.

## 재검증
이 폴더의 `test_*.py`는 ZIP 최상위 `src`와 기존 baseline 파일을 읽으며
Java 21/Javac가 필요하다. `/mnt/data` 경로에 의존하지 않는다.
