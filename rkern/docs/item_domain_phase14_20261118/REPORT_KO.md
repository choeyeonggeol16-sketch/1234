# RKern 아이템 기능별 분리 14차 검증 보고서 — 2026-11-18

## 기준과 수정 범위

기준: `RKern_ItemFunction_DomainSeparation_Phase13_FULL_SRC_20261118.zip` SHA256은 `MANIFEST.json` 참조.

- **기능별 전용 클래스 12개 신규 생성**, 혼합 클래스 `MaanRaidTransactions.java` 삭제.
- 기존 생산 Java **3개 수정**: `MovementPotionEffects.java`, `FishitemService.java`, `SpecialConsumableRouter.java`.
- `MovementPotionEffects` 공개 메서드 3개는 호환용 진입점으로 유지하고, 헤이스트·용기·열매 효과를 각각 전용 클래스로 이동.
- `FishitemService` 공개 메서드 2개는 호환용 진입점으로 유지하고, 말하는 섬 낚시·낚시터 낚시·사용 검증·시작 세션·낚싯대 UI 설정을 분리.
- 기존 마안 일회성 영속 소모/스킬 사용을 `MaanSkillConsumables`로, 혈맹 레이드 영속 소모를 `ClanRaidTransactions`로, 보스 설정 캐시/파싱을 `ClanRaidBossSelection`으로, NPC 소환을 `ClanRaidNpcSpawn`으로 분리.
- `SpecialConsumableRouter`의 11개 Maan/raid 호출만 새 전용 클래스로 변경.
- 아이템 소모/영속 커밋 경계, RNG 호출 위치, 클랜 레이드 보스 선택 및 이동·소환·쿨타임 순서, 낚시 거리/맵/충전/미끼/상태 검증과 UI 갱신 순서를 변경하지 않음.
- 방송/네트워크/NPC/DB 공통 처리, 새 작업 스레드/큐/태스크는 변경·추가 없음.

## 검증

`bash docs/item_domain_phase14_20261118/run_tests.sh`

- 23개 실제 생산 메서드 본문 13차와 바이트 동일. 레이드에서 2개 함수 호출의 소유 클래스명만 변경한 것을 정규화해서 대조.
- 낚싯대 설정 6종의 배열 내용/순서, `RodSpec` 본문 정확히 동일.
- Java 21 타입 컴파일 및 원본 대비 실행 결과: 물약 **17,280**개 시나리오(상태·효과·패킷), 낚시 **14,400**개 시나리오(장비·이동·미끼·좌표·패킷·등록).
- Java 21 타입 컴파일 및 원본 대비 실행 결과: 보스 설정/CSV **1,500**개, 마안/레이드/소환 **271**개. 영속 receipt 및 커밋 이후 호출 순서 일치.
- 기존 13차 이하 아이템 회귀와 방송 114차 회귀 통과.
- 전체 생산 Java 21 구문 검사: **1,906개, 오류 0**.
- ZIP CRC/파일 중복/동일 기준 소스의 미변경 파일 바이트 동일성 확인.

## 남은 검증

전체 의존성 타입 컴파일과 실제 MySQL/CacheD 연결, 인게임 낚시·버프·레이드 성공 및 5천 명 실부하는 아직 미검증. 본 차수는 아이템 역할 분리이며 동작 규칙이나 확률을 개선한 차수가 아님.
