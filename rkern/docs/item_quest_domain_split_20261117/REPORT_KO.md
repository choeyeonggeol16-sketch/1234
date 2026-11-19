# 아이템 퀘스트 도메인 클래스 분리

기준: RKern_ItemFunction_DomainSplit_Refactor_FULL_SRC_20261117.zip

- 삭제: src/l1j/server/server/model/item/function/QuestItemCases.java
- 추가: QuestCraftingItems.java (7개 기존 메서드), QuestTravelItems.java (3개), QuestProgressItems.java (1개)
- 변경: ItemActionDispatcher.java (11개 호출 클래스명만 변경)
- 원본 메서드 11개 본문은 공백을 포함해 동일; 원래의 소모/보상/CacheD 트랜잭션·메시지·순서 변경 없음.
- 기존 클래스 참조 전수 검사: 0건.
- 전체 타입 컴파일/실제 DB 및 게임 서버 실행은 미검증.
- 메모리 최적화 및 네트워크/방송/DB/NPC 변경 없음.
