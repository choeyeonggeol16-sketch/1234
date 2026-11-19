# RKern 아이템 107차 — 합성 카드 선택 preimage 검증 및 4슬롯 정리

기준: `RKern_ItemOnly_CardSynthesis_SelectionSafety_Primitive_Phase106_FULL_SRC_20261118.zip`.

생산 변경 (2개):
- `src/l1j/server/server/model/item/Card/CardAlchemyReceipt.java`
- `src/l1j/server/server/NeigeCard3.java` (카드 메뉴 디버그 콘솔 출력 한 줄 제거)

## 수정

1. 전설 2장 합성도 성공 커밋 시 `accounts.info_card_a/b/c/d` **4개 모두 0으로 저장**하며, 성공 후 Account 메모리의 a/b/c/d 4개도 초기화합니다. 이전에는 2장 합성의 d 슬롯이 DB와 메모리에 남을 수 있었습니다.
2. 합성 제출 시 Account 선택 슬롯 네 개의 값 스냅샷을 유지하고, 아이템·DB 커밋 완료 후 계정 메모리에 적용하기 전에 선택 슬롯 preimage를 다시 확인합니다. 변경을 감지하면 새 선택을 지우거나 카드 수량을 임의 차감하지 않고 예외를 발생시켜 기존 `L1InventoryTransaction`의 커밋 완료 후 격리(quarantine) 경로로 넘깁니다. **이 경로는 자동 복구·재동기화가 아닙니다.** 원인 파악 후 계정 상태 재로드가 필요할 수 있습니다.
3. `NeigeCard3.NeigeMyth`의 매 메뉴 클릭 `System.out.println`을 제거했습니다. 정상 메뉴 분기·카드 등록·합성 확률·송신 동작은 유지합니다.

추가된 워커, 큐, 락, 트랜잭션 매니저, 새 production 클래스 없음. NPC AI, 일반 송수신/방송, CacheD 공통 구조 무변경.

## 검증

- `python3 docs/item_only_phase107_20261118/test_107.py`: 생산 CardAlchemyReceipt 및 3종 호출자를 Java 21 타입 컴파일·실행; **25,332 checks**. 특히 2장 합성 후 C/D 잔존 상태, DB 4슬롯 정리, a/b/c/d 선택 경합 **3티어 x 4슬롯=12경로** 검증.
- `python3 docs/item_only_phase103_20261118/test_103.py`: 카드 70종, 수호성 14종 및 유일 카드, 총 **2,498 assertions**, 통과.
- `python3 docs/item_only_phase104_20261118/test_104.py`: 카드 확인 응답 4경로, **65 assertions**, 통과. (103차 재검증 포함)
- 105차의 독립 Java guard 검증: **410 conditions**, 중앙 NPC 메뉴 2개, 통과.
- 106차의 독립 생산 Java 선택기 타입/실행 검증: **16,019 checks**, 매핑 70개, 통과.
- Java21 javac 파서: **1,923/1,923 생산 Java 파일**, syntax errors 0.

`docs/item_only_phase102_20261118/test_102.py` 내 JDBC 모의 검사의 판정 기준을 **4번째 슬롯을 지우는지 여부가 아닌 실제 차감 카드 수**로 고쳐 107차 불변식에 맞췄습니다. 이는 production 코드 변경이 아닙니다. 기존 105·106차 중첩 테스트의 전체 스크립트(내부적으로 104·103차를 재반복)는 타임아웃으로 중단됐으며, 103·104차를 별도 실행하고 105·106차 독립 검사 부분을 직접 실행해 각각 확인했습니다.

## 한계

타입 검사는 카드 합성 production 코드와 스텁 계약을 대상으로 했으며 전체 외부 의존성 애플리케이션 타입 컴파일을 대체하지 않습니다. 실제 MySQL·CacheD ACK 손실·동시 세션 재접속·서버 인게임/5천명 검증은 실시하지 않았습니다. 동일 계정의 다른 세션이 DB 커밋 사이에 선택 값을 변경하는 문제는 preimage 검출로 격리할 수 있지만 **지속적인 계정 동기화나 자동 재시도는 구현하지 않았습니다**.
