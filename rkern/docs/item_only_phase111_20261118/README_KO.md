# RKern/L1J 111차 — 아이템 카드 등록 커밋 후 출력 안전성

기준: `RKern_ItemOnly_CardSynthesis_PostCommit_EffectSafety_Phase110_FULL_SRC_20261118.zip`.

## 생산 Java 변경 3개

1. `src/l1j/server/server/model/item/Card/L1CardAdd.java`
   - 컬렉션 등록 커밋 후 카드 수량·점수 반영, 게임 능력치 적용을 성공 메시지보다 먼저 실행한다.
   - 최종 등록 채팅 출력이 실패해도 확정 등록을 거래 격리하지 않는다.
   - 수호성 등록: 확정된 스킬 상태 반영 후 UI 렌더링과 채팅을 각각 독립적인 optional 출력으로 격리한다.
   - **카드 수량/점수 preimage, 세션 오류 및 실제 능력치 적용 실패는 여전히 예외를 전파한다.**

2. `src/l1j/server/server/model/item/Card/L1CardCollection.java`
   - 유일/전설/영웅/신화의 능력치 적용 이후 전송하는 상태 패킷·축하 메시지에 한해서 `RuntimeException`을 기록하고 전파하지 않는다.
   - 실제 게임 보너스 계산과 적용 경로는 변경하지 않는다.

3. `src/l1j/server/server/model/item/function/TransformationCollectionCards.java`
   - 기르타스 유일 카드의 확정 채팅 출력 오류가 이미 확정된 등록을 거래 격리하지 않도록 한다.
   - 거래 소유권을 획득한 직후 계정 세션·유일 카드 수량·컬렉션 점수 preimage를 재검사하고, 변경됐으면 SQL 제출 전 거래를 중단한다.

## 실제 수행 검증 (2026-11-18)

- `test_111.py`: 변경 **실제 Java 21 생산 클래스 3개** + typed stub 컴파일, 확정 뒤 채팅·상태 패킷·UI 출력 장애 주입: 컬렉션·수호성 118개 조건, 기르타스 유일 24개 조건 통과. 능력치 적용 실패는 hard-failure로 유지되는지 확인. 유일 카드 gate 경합 3개 시나리오 포함.
- 103차 등록 2,464 + 기르타스 34, 104차 확인 65, 105차 410 + 중앙 액션 2, 106차 자동 선택 16,019, 108차 합성 25,371, 109차 gate 경합, 110차 합성 25,335: 모두 이번 111차 소스 기준 통과.
- Java 21 전체 production 구문 파싱: 1,923개 파일 / 오류 0.
- 110차 대비 생산 Java 변경 정확히 위 3개, 나머지 동일; ZIP CRC 검증.

**검증 범위 한계:** Java typed stub/모의 SQL 실행이며 실제 MySQL·CacheD ACK loss·다중 세션·재접속·인게임 능력치 표시·전체 의존성 타입 컴파일 및 5,000명 부하는 검증하지 않았다. 카드 수량 DB↔메모리 자동 재동기화는 아직 미구현이며 기존 불일치 격리를 유지한다.
