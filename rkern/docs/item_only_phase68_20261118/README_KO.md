# RKern 아이템 전용 68차 — 과분리 추가 없음 / 할당 감소 검증

**기준:** 67차 전체 SRC. **범위:** `ItemEffectRegistry`, `ClanRaidBossSelection`, `ConsignmentItemActions`의 기존 코드 3개 수정. NPC 퀘스트, AI, 네트워크, 방송, CacheD 공통 구현은 변경하지 않음. 신규 생산 클래스 없음.

- `ClanRaidBossSelection`: `String.split(",")` 및 토큰별 `trim()` 문자열, 임시 배열 생성 대신 원본문자열 내 구간을 `Integer.parseInt(CharSequence, begin,end,10)`로 읽도록 변경. 숫자 파싱 실패·양수 필터·순서·캐시 동작은 그대로 유지. 쉼표만 많이 들어온 설정에서 과도한 배열을 예약하지 않음.
- `ItemEffectRegistry.Def.aggregatedStatsFor`: 정상적인 합산 값이 0이 아니면 `EnumMap.values().removeIf` 반복자를 만들지 않도록 변경. 0 상쇄, 음수, int overflow, 직업별 분기 동작을 원본과 비교.
- `ConsignmentItemActions.register`: 실패 조건들을 모두 지난 후에만 날짜 문자열을 생성하도록 이동. 지급·거래 로직과 인형 목록 스냅샷은 유지.

## 실행 검증 (테스트용 게임 스텁)

- `python3 docs/item_only_phase68_20261118/run_phase68.py` — JDK 21 필요.
- 레이드 CSV 파싱: 원본과 150,015개 입력 결과 일치. 일반 ID 12개 파싱 할당량 840B/호출 → 64B/호출 (ThreadMXBean, Java 21 테스트).
- 아이템 효과 집계·적용: 216,000개 원본 비교 일치. 툴팁 일반 케이스 96B → 80B/호출 (같은 방식).
- 위탁판매: 실제 생산 메서드 타입 컴파일 + 1,200개 유효/거절 입력 시나리오 출력·트랜잭션 호출 순서 비교 일치.
- 기존 30차 레지스트리 259,200개, 66차 물약 최적화, 67차 카드 120,000개, 31차 스타터 521개, 32차 선물상자 1,381개 회귀검사 통과.
- 생산 Java 1,922개 Java 21 구문 검사 오류 0건.

**검증 한계:** 테스트용 스텁으로 변경 메서드의 타입·호출·결과를 비교했으며, 외부 JAR 전체 의존성 타입 컴파일, 실제 서버 5,000명 부하, 실 MySQL/CacheD 장애 주입은 아직 미실시. 미세 벤치마크 수치를 서버 전체 GC 효과로 환산하면 안 됨.
