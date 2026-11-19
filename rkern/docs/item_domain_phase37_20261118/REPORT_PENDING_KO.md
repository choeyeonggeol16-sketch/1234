# 후속 감사 대상 (이번 패키지에서 미변경)

- `src/l1j/server/server/model/network/core/cs/packet/CS_NpcAction.java`: NPC 71198·71199 대화 분기에서 `consumeItem(...)` 이후 직접 `createItem/storeItem`을 호출하고, 지급 실패 여부와 별개로 퀘스트 진행 단계가 바뀔 수 있는 경로 확인. 선소모·보상 부분 확정 위험을 별도 트랜잭션과 퀘스트 상태 확정까지 포함해 설계·실검증 필요. 이번 35~37차에서는 아이템 ID·NPC 퀘스트 상태를 무리하게 변경하지 않음.
- `src/l1j/server/server/model/Instance/L1FollowerInstance.java`, `L1MonsterInstance.java`, `CS_NpcAcion2.java`의 다른 지급 메서드: 인벤토리·지면 대체 지급, 축복 저장/스택 병합 경계 추가 감사 필요.
- 전체 서버 JAR 의존성 타입 컴파일, 실제 MySQL·CacheD commit/ACK loss/재접속·재시작 장애 주입, 인게임 지급 테스트가 남아 있음.

이 문서는 이번 패키지에서 이 항목이 해결되었다는 주장이 아니며, 확인된 조사 목록이다.
