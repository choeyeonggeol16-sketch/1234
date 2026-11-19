# RKern 아이템 105차 — 카드 합성 선택 UI의 in-flight 거래 격리

기준: `RKern_ItemOnly_CardConfirm_Receipt_Phase104_FULL_SRC_20261118.zip`.

## 생산 코드 변경 (정확히 4파일)

- `src/l1j/server/server/NeigeCard.java`: 영웅 합성 선택/취소/랜덤/시작(`rnalce*`, `rnlpolych*`, `rnlpolyfir`, `rnlpolyph`, `rnlpolysh`) 입력을 계정 아이템 거래 pending 동안 거부.
- `src/l1j/server/server/NeigeCard2.java`: 전설 합성 선택/취소/랜덤/시작(`rnllsce*`, `rnllch*`, `rnllfir`, `rnllpl`, `rnllsh`) 동일.
- `src/l1j/server/server/NeigeCard3.java`: 신화 합성 선택/취소/랜덤/시작(`rnmysce*`, `rnmych*`, `rnmyfir`, `rnmypl`, `rnmysh`) 동일.
- `src/l1j/server/server/model/npc/action/ActionControlHandler.java`: 중앙 NPC 액션의 합성 선택 초기화 `neifirl`, `neifirmy`도 동일 pending 체크. 또한 공통 초기화 함수에서 빠져 있던 `info_card_d`를 함께 0으로 설정해서 이전 4번째 슬롯이 다음 합성에 남지 않도록 수정.

기존 `L1InventoryTransaction`이 이미 점유하는 `pc.isItemTransferBusy()` 상태를 조회하며 새 락, 스레드, 큐, 객체, 필드를 추가하지 않습니다. 거래가 진행 중이면 해당 **합성 조작**만 `true`로 처리(액션 거절)하고, 폴리모프·일반 NPC 메뉴·읽기 전용 카드 보기는 이전과 같습니다. 거래가 없을 때는 기존 코드와 동일하게 정상 실행합니다. 카드 수량, SQL, 랜덤 성공 확률, 아이템 지급 처리는 변경하지 않았습니다.

## 이유 및 한계

104차의 `CardAlchemyReceipt`가 최종 COMMIT 응답을 기다리는 사이 일부 NPC 클릭으로 `Account.info_card_*` 선택값을 재설정할 수 있었습니다. 동일 `L1PcInstance`의 기존 CacheD 예약 구간에서 일어나는 이 문제를 막습니다.

**계정 전체 메모리 재동기화까지 해결한 것은 아닙니다.** 동일 계정의 다른 캐릭터/세션 및 직접 `Account` 필드를 변경하는 별도 코드는 이 차수에서 동기화하지 않습니다. 계정의 영속 상태와 메모리가 어긋난 경우 운영 중 자동 복구를 제공한다고 주장하지 않습니다. 실제 MySQL 실패/ACK 손실, 캐릭터 재접속과 운영 인게임 UI 검증도 수행하지 않았습니다.

## 검증

`python3 docs/item_only_phase105_20261118/test_105.py` 는 실제 생산 코드의 guard 조건을 추출해서 Java 21로 컴파일/실행합니다. 전체 합성 NPC 액션 205개(`NeigeCard` 83개, `NeigeCard2` 66개, `NeigeCard3` 56개)에 대해 idle와 busy 410개 조건을 확인하고, 중앙 2개 메뉴도 검사합니다. 포함된 103·104차 기존 모의 영속 거래 테스트(2,498 + 65 assertion)도 재실행합니다.

전체 Java 파싱:

```
javac --release 21 -d /tmp docs/item_only_phase105_20261118/ParseAllJava105.java
java -Xmx1g -cp /tmp ParseAllJava105 .
```

생산 Java 1,923개 파싱 오류 0. 전체 클래스패스 타입 컴파일은 별개이며 아직 검증하지 않았습니다.
