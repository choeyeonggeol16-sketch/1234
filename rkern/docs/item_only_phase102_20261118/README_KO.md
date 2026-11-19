# RKern 아이템 102차 — 카드 합성 임시 할당 절감

**기준:** 101차 전체 SRC ZIP. 생산 코드 변경: `CardAlchemyReceipt.java`, `L1CardAlchemy1.java`, `L1CardAlchemy2.java`, `L1CardAlchemy3.java` 정확히 4개. NPC·네트워크·CacheD·방송 공통 코드 변경 없음.

## 변경 내용

- 합성 매 호출에서 카드 ID용 `int[]`와 수량용 `int[]`를 생성하던 처리 제거.
- `id -> copiesFor(acc, id)` 계정 캡처 람다를 계정 객체와 ID를 인자로 받는, 클래스별 정적 `CardCount` 함수 참조로 교체.
- SQL 컬럼 화이트리스트, 계정 preimage `WHERE` 조건, `L1InventoryTransaction` debit/grant/SQL, 커밋 후 계정 메모리 갱신·결과 이펙트 순서 그대로 유지.
- 중복 카드 ID, 없는 카드, 수량 1 이하, DB 업데이트 0건, 영속 거래 실패 처리 보존.

## 재현 방법

Java 21 및 Python 3 필요. ZIP 압축 해제 후 프로젝트 루트에서:

```
python docs/item_only_phase102_20261118/test_102.py
python docs/item_only_phase102_20261118/run_ab102.py
```

검사 결과: 생산 코드/게임 모델 타입 컴파일 및 25,281개 조건 통과, 원본 101차/변경 102차 합성 36,000회 동일 난수 시드 결과 해시 일치. 전체 Java 구문 검사는 JDK 21 파서로 재검증.

할당 미세 측정에서는 클래스·JIT 워밍업 상태에 따라 원본/수정본 차이가 일정하지 않았으므로 실제 GC 절감률을 산출하지 않았다. 제거된 임시 객체 수만으로 성능 우위를 주장하지 않는다.

## 미완료

전체 의존성 타입 컴파일, 실제 MySQL/CacheD 장애 주입, 카드 선택 변경·로그아웃 경합, 실서버 인게임 검증은 아직 미완료. 비동기 DB 커밋 이후 계정 메모리 preimage가 바뀐 경우의 재동기화 정책은 이번 변경 범위 밖이며 별도 검토가 필요하다.
