# 아이템 182~184차 — 영속 거래 커밋 준비 메모리 최적화

기준: 181차 최신 누적 전체 SRC.
변경 생산 파일: `src/l1j/server/server/model/L1InventoryTransaction.java` **1개**.

## 182차: 단일 항목 커밋 정렬용 배열 제거

기존의 `Arrays.copyOf(entries, entryCount)`는 1행 거래도 별도 배열을 생성했다.
1행은 이미 private인 `entries` 배열을 그대로 읽고 별도 정렬/배열 할당을 수행하지 않는다.
2행 이상은 기존처럼 복사본에만 ID 오름차순 정렬을 적용한다.

**중요한 실패 경계:** 다중행에서 내부 배열을 직접 정렬하면 prepareOwnedWrite/commit이
도중 실패했을 때 원본 삽입 순서가 바뀌므로 이를 피한다.

## 183차: 다중행 SQL preimage 검증 콜백 묶기

기존에는 row별 `stageWarehouseSql(con -> verifyItemPreimage(...))`를 제출했다.
다중행에서는 오름차순으로 정렬된 `ordered` 배열을 순서대로 확인하는
단일 SQL mutation으로 묶는다. 기존 SQL transaction 내부에서 검증은 **모든 쓰기 전에** 수행한다.
1행은 기존의 단일 callback을 그대로 유지한다. 신규 장수명 캐시나 스레드·큐는 없다.

## 184차: 실패·회귀 검증

- 기존 181차 `entry/debit/grant/transfer` Java 21 타입 검사: **56,521 조건 통과**.
- 기존 용량 검사와 빠른 차감 경로 검증: **99,799 조건 통과**.
- **생산 `commitInternal()` 메서드 양쪽 추출·Java 21 타입 컴파일 및 모의 SQL 실행 40,015 조건 통과**.
  - 단일/다중 행(1~128), 일부 신규·삭제·이전, DB preimage 잠금 순서, write 순서,
    성공 후 인벤토리 replay 순서, release-before-effects 두 모드,
    stale preimage 중단, staging 도중 예외, 기존 Entry 삽입 순서 보존.
- 169~175차 강화/영속 거래 실행 검사(보호 강화 710, 무기 1536/288,
  방어구 336, 거래 21, 장착 효과 127, 추가 receipt 12, 완료 1980,
  강화 복구 14): 통과.
- 146~154차 inventory replay 62, post-COMMIT callback 81: 통과.
- Java 21 `javac` parser: 전체 `src` **1,923개 Java, 오류 0**.
- ZIP CRC·181차 원본 항목 바이트 대조 및 최종 ZIP 재추출 검증: 정상.

## 유지한 안전 경계

CacheD 워커/FIFO·소유권 예약, `ItemSnapshot` 원본 검증, 정렬된 DB row 잠금,
prepared image 방어적 복사, owner apply, commit 효과/격리 정책은 유지.
커밋 후 ItemSnapshot이나 콜백을 pool로 재사용하지 않았다.

## 한계

실제 MySQL/CacheD 장애, 5,000명 동접, JFR 할당률·CPU, 전체 프로젝트 의존성
타입 컴파일과 인게임 테스트는 수행하지 않았다.
이 차수는 생산 코드 할당 감소를 목적으로 하며 전체 일반 강화 원자성이나
카드 수량 DB/메모리 복구 문제를 해결한 것이 아니다.
