# N6 NPC 이벤트 및 경험치 교환 — 2026-11-19

기준: RKern_NPC_Action_N6_Remove_Arnold_Naruto_FULL_SRC_20261119.zip
SHA256: b0b0858ea5f0c15cac2000eee4aed17bf7203a5ed3039a2cc3bb97cccf92bf5f

## 수정 내용
- CS_NpcAcion2 (1834 -> 1155줄): 세실리아 소환, 투석기, 경험치 지원단·에킨스 실행부를 각 기능의 handler로 연결. 제거된 아놀드/나루터 분기는 복원하지 않았다.
- NpcGameEventActions: 고정 보스 소환 30개 및 랜덤 AA 명령 분리. 고정 명령은 정적 배열을 재사용하고 mobboss를 파싱하지 않는다. AA의 빈/잘못된 설정과 존재하지 않는 템플릿은 열쇠 차감 전에 차단. 고정 소환도 템플릿 존재 검사 추가. 기존 0..99 난수 modulo, 소환 좌표, 고정 소환 시간, 메시지 그리고 A/a B/b C/c D/d의 독립 매칭 순서를 유지. 이동해 온 랜덤 소환 helper 내부 로직은 유지했다.
- NpcCastleActions: 기존 투석기 처리와 피해 helper 이동. castleid 0의 잘못된 방향을 전쟁 조회 전에 차단하고 효과 생성 실패 시 null 역참조 방지. 방어혈 확인 함수는 기존 동일 구현 재사용. 기존 피해 맵 상수/좌표/공성 정책은 유지했다.
- NpcExperienceCommands: 경험치 지원단 및 신규 지원 기존 동작 이동. 에킨스 4개 교환의 재료 소비 및 아이템 보상을 기존 L1InventoryTransaction에 함께 stage. 경험치와 효과는 commitAfterInventoryRelease로 실행. 이전 경험치 계산 메서드 그대로 유지. NPC obj 대신 objectID 캡처.
- NpcExchangeCommands: debitLegacy의 접근 범위를 패키지 내부로만 확대해 재사용.
- 새 워커/큐/타이머/패킷별 Context 없음. 기존 AI, 네트워크, 방송, CacheD 코어 변경 없음. 방어구 바닥 지급 정책 유지.

## 검증
- Java21 전체 구문 검사: 1926개, 오류 0.
- 전체 javac --release 21 -encoding UTF-8 -proc:none -Xmaxerrs 3000: 원본/수정본 모두 실패(823 errors, 5 warnings), 로그 byte-identical. 의존성 누락 등을 포함한 진단이며 타입 컴파일 성공으로 간주하지 않는다.
- 실제 원본/수정 세실리아 분기 추출 비교 15616건, 잘못된 설정/템플릿 모의 테스트 8건, 에킨스 receipt 모의 테스트 11520건. 투석기 원본 본문과 이동된 본문은 두 문서화된 guard 외 일치. 경험치 공식 본문 동일.
- 기존 불골렘 무기 13833건/유료 이동 30건 재실행 통과. 이번에는 전체 이전 아이템 스크립트 재실행하지 않았다.

## 미해결 사항
- A/a 등 충돌 명령 4쌍은 실제 HTML 명령 정책 확인 전까지 기존의 두 독립 실행을 유지한다.
- 기존 void spawn/spawn2는 소환 실패를 내부에서 처리하므로 템플릿 유효성 이외의 소환 실패에 대한 열쇠 복구는 미해결.
- 에킨스 아이템 debit/grant는 동일 receipt지만 경험치 반영은 post-commit 효과다. 세션 종료/서버 장애 때 경험치까지 영속 원자화/복구되지는 않으며 실제 DB 검증이 필요하다. 용량 부족 시 트랜잭션 거부 정책은 이전 raw 경로와 달라질 수 있다.
- 신규 지원의 다중 지급, 불골렘 방어구의 바닥 보상, XML 복합 제작, TalkChannelCache, 잔여 CS_NpcAcion2 명령은 추가 검수 대상.
- 실제 인게임/DB/장기 Heap·JFR 검증 미실시.
