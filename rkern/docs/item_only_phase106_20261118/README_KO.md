# RKern 아이템 106차 — 자동 카드 선택 primitive 최적화 및 거래 중 선택 방지

**입력:** `RKern_ItemOnly_CardSynthesis_InFlightSelection_Phase105_FULL_SRC_20261118.zip` (105차 누적 포함)

**새 생산 변경 3개 파일:**

- `src/l1j/server/server/model/item/Card/L1CardLPast.java` (전설 23종에서 2종 선택)
- `src/l1j/server/server/model/item/Card/L1CardHPast.java` (영웅 36종에서 4종 선택)
- `src/l1j/server/server/model/item/Card/L1CardMyPast.java` (신화 11종에서 4종 선택)

## 변경 내역

- 자동 선택의 `ArrayList<Integer>` / `List<CardData>`, `Collections.shuffle`를 제거했습니다. 고정 길이 `int[]`를 한 번만 만들고 `ThreadLocalRandom.current()`로 **선택할 앞 2/4개에 대해서만** 부분 Fisher–Yates를 실행합니다. 선택되는 순서 있는 2/4카드 조합은 기존 전체 셔플과 동일하게 균등 분포를 가집니다.
- 전설 101~123, 영웅 1~36, 신화 201~211 등 총 **70개 자동 선택 ID의 getter·ID 대응 관계를 기존 소스와 1:1 대조**했습니다.
- 각 자동 선택 핸들러 자체에 `pc.isItemTransferBusy()` 검사를 추가해, 105차 NPC 메뉴를 우회해 직접 호출되더라도 CacheD 거래가 진행 중인 플레이어의 카드 선택 상태가 바뀌지 않게 했습니다.
- 보유가 2장 미만인 카드 제외, 부족한 카드 수 메시지, UI 종류와 선택 슬롯 정책은 유지했습니다. 확률표·합성 재화/카드 소모·보상 로직과 DB는 변경하지 않았습니다.

## 테스트

```
python3 docs/item_only_phase106_20261118/test_106.py
```

실제 생산 클래스 3개를 Java 21 타입 모델로 **컴파일하고 실행**해 16,019개 검사에 통과했습니다. 모든 ID 매핑 대조, 선택 카드의 중복 없음, 개별 카드 보유량=1 및 부족, 거래 중 선택 차단, null PC/Account 처리 등을 포함합니다. 105차의 410건 가드·중앙 2건, 104차 65건, 103차 2,498건 검사도 함께 통과했습니다.

전체 SRC Java21 `JavacTask.parse` 구문 검사: 1,923개 Java 파일, 오류 0건. 전체 앱 의존성 타입 컴파일·실제 MySQL/CIO/인게임·장시간 동시성은 아직 별도 검증이 필요합니다. 이 차수는 계정 전역 메모리/영속 상태 자동 재동기화 구현이 아닙니다.
