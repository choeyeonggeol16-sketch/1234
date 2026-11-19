# 아이템 179~181차 — L1InventoryTransaction 임시 할당 추가 절감

## 기준과 변경 범위

- 178차 전체 SRC 기준 **생산 Java 1개 파일만 수정**:
  `src/l1j/server/server/model/L1InventoryTransaction.java`.
- `TIntLongHashMap` 용량 검증, 영속 `ItemSnapshot`/prepared item image, 소유권 확인,
  CacheD SQL preimage/receipt, row-ID 오름차순 잠금, FIFO 콜백/격리 정책은 유지한다.
- NPC AI, CIO, 네트워크 송수신, 방송, CacheD 공통 구현은 그대로 유지한다.

## 최적화

1. **179차: 소규모 거래 Entry 저장소**
   기존 `LinkedHashMap<Integer,Entry>`를 추가 지연 생성 배열로 대체했다.
   보통 1~2행 거래에서는 Map/노드/Integer 박싱 객체를 만들지 않는다.
   Entry 삽입 순서, 원본 아이템 참조 및 소유권 재검증, 직렬 ID 정렬은 유지.
2. **180차: 대량 거래 인덱스 조건부 활성화**
   9행부터 기존 Trove `TIntObjectHashMap<Entry>`를 생성해서 primitive row ID로 조회한다.
   작은 거래는 배열 선형 탐색, 큰 거래는 인덱스 탐색. 신규 워커·스레드·큐 없음.
3. **181차: 복사 중복 제거 2곳**
   분리된 외부 보상 `grant(L1ItemInstance)`의 2차 사본을 내부 private prepared 경로로 전달하고,
   `transfer()`에서 이미 private으로 복사한 carry를 다시 복사하지 않도록 변경했다.
   외부 입력의 최초 방어적 복사와 실제 DB 제출 후 commit 이미지 복사는 유지한다.

## 검증

- `test_entry_primitive_java.py`: **56,521개 조건 PASS**.
  178차 원본과 실제 `entry/debit/grant/transfer` 메서드를 각각 Java 21 typed stub에 넣어
  1~6인, 1~144행, 8→9행 인덱스 활성화, 아이템 ID/수량/소유자/삽입 순서,
  보상 병합, 일부 이전, 정렬 잠금 순서 및 복사 횟수를 비교함.
- `test_capacity_latest.py`: **99,799개 조건 PASS**.
  전체 인벤토리 projected capacity와 순수 차감 fast path 결과를 기존본과 비교함.
  테스트 fixture는 `entries` 배열을 기존 map과 같은 논리 순서로 어댑트하여 실행함.
- 과거 169~175차 `run_tests.sh`: 보호 강화 710, 무기 1,536, 특수 무기 288, 방어구 336,
  거래 21, 장착 효과 127, 재료 12, 사용 완료 1,980, 강화 복구 14 조건 통과.
- 과거 146~154차 `run_tests.sh`: 커밋 인벤토리 재생 62, 커밋 후 콜백 81 조건 통과.
- JDK 21 javac parser 기준 전체 `src` Java **1,923개 구문 오류 0건**.
- 전체 ZIP CRC, 기존 항목 바이트 동일성 검증은 ZIP 생성 후 수행.

## 제한

실제 서버 JFR·GC 할당률과 CPU, 전체 의존성 타입 컴파일,
실제 MySQL/CacheD 장애, 5,000명 부하, 재접속·재시작은 미검증.
원자성·경제 데이터 자체의 보존을 줄이는 최적화는 수행하지 않았다.
기존 178차 전용 역사적 테스트 스크립트는 해당 차수의 컬렉션 레이아웃을 전제로 한다.
최신 차수 전용 회귀는 위 `test_entry_primitive_java.py`/`test_capacity_latest.py`를 사용한다.
