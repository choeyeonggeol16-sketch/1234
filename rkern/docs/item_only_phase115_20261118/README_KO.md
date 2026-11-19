# RKern 아이템 115차 — 책갈피 확장 원자성 / 정상의 가호 커밋 후 처리

## 적용 기준 및 변경 범위
- 114차 전체 SRC (`RKern_ItemOnly_Food_HolyWater_PostCommitSafety_Phase114_FULL_SRC_20261118.zip`)
- 생산 Java 변경 정확히 2개: `QuestProgressItems.java`, `TopRankerBlessingItems.java`
- NPC·네트워크·방송·CacheD 엔진 공통 코드의 변경은 없음.

## 1. 책갈피 슬롯 확장 아이템 700022
**확인된 문제:** 이전에는 `Mark_Count` 메모리 증가 → UI 패킷 → 직접 `removeItem(1)` → 캐릭터 저장 순서로 실행되어, 아이템 소모와 `characters.Mark_Count`가 한 거래로 확정되지 않았다. 실패 지점에 따라 아이템·확장 수치가 어긋날 수 있었다.

**수정:**
- 현재 인벤토리의 객체 동일성, 아이템 ID(700022), 100개 상한을 먼저 검사한다.
- 기존 캐릭터 스냅샷/dirty input item 저장을 먼저 FIFO에 제출한다.
- `L1InventoryTransaction.begin(pc, item.getId())`에서 아이템 1개 debit 및 `UPDATE characters SET Mark_Count=? WHERE objid=? AND Mark_Count=?`를 동일 CacheD 거래로 제출한다.
- SQL `executeUpdate()!=1`이면 롤백되도록 CAS(preimage)를 강제한다.
- 성공 콜백에서 메모리 `Mark_Count`의 예상 이전 값 검증 후 갱신한다. 메모리 선행 변경 시 예외/격리 보호를 유지한다.
- 이미 SQL과 차감이 커밋된 뒤 실행되는 `pc.save()` / `SC_PacketBox` 실패는 기록하되 확정된 경제 상태를 롤백/격리로 오인하지 않는다.
- 원래 `before+10`, 100 이상 시 2930 메시지 정책을 유지한다.

## 2. 정상의 가호 아이템 55558
**확인된 문제:** `UseDurableConsume` 콜백에서 사운드를 먼저 전송하므로 출력 실패 시 영속 소모는 완료되지만 TOP_RANKER 버프 및 마지막 사용 시간이 적용되지 않았다.

**수정:**
- 커밋 콜백에서 버프 600,000ms, 마지막 사용 시간 메모리 적용을 먼저 실행한다.
- 이후 캐릭터 저장을 요청하고, 개인/주변 사운드를 독립 `try/catch`로 전송한다.
- 버프 적용 자체의 예외는 숨기지 않는다. 기존 1시간 재사용 제한은 동일하게 유지한다.
- 5558 일반 정상의 가호 경로는 변경하지 않았다.

## 검증
- 새 115차 **실제 생산 Java 2개 클래스**를 타입 스텁과 함께 Java 21로 컴파일, 모의 SQL 프리이미지·아이템 차감·커밋/롤백·중복 사용·상한·패킷 오류·save 오류·상태 충돌 등 **154개 assertion 통과**.
- 기존 114차 232,459개, 113차 54,150개, 112차 6,925개, 111차 142개, 110차 25,353개, 109차 경합 546개 및 103차 2,498개, 108차 25,371개, 105차 가드 410개, 106차 선택 16,019개 조건 통과 (중복 구성 스크립트는 각각 나눠 실행).
- 전체 Java 1,923개 소스 구문 파싱: 오류 0건. ZIP CRC, 114차 대비 바이트 보존 검사: `VERIFY_LOG.txt` 확인.

## 검증 한계
- JDBC 실제 MySQL·CacheD 스케줄러 실험이 아닌 타입 기반 모의 테스트다. 실제 서버, 멀티세션, 재시작·ACK 손실, 인게임, 전체 의존성 타입 컴파일 및 5,000명 부하 검증은 아직 수행하지 않았다.
- 캐릭터 경제 상태·카드 계정 메모리 불일치 자동 복구 문제는 이번 범위 밖이며 미완료다.

## 단독 테스트 재실행
```sh
SOURCE_ROOT=/path/to/extracted/full-src python docs/item_only_phase115_20261118/test_115.py
```
