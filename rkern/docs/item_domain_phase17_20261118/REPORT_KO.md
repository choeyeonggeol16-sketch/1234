# RKern 아이템 기능별 리팩토링 17차 (2026-11-18)

## 작업 기준
- **기준:** `RKern_ItemFunction_DomainSeparation_Phase16_FULL_SRC_20261118.zip` (SHA-256 `672c5144e6d427aac220e9f86f3bd712562ab12c1a9055c2d851f809f8fd6fc7`).
- **변경 범위:** `src/l1j/server/server/model/item/function` 생산 Java와 17차 검증 문서, 루트 README만 변경. 네트워크·방송·NPC·DB 공통 코드는 수정하지 않음.
- **신규 클래스 13개, 수정 기존 클래스 13개, 삭제 기존 혼합 클래스 2개.** 생산 Java는 1,958개에서 **1,969개**로 증가.

## 분리 내용
1. **기존 `DurableSkillItems` 제거.** 마법 재료 영속 차감 `DurableSkillReagentTransaction`, 패시브 영속 습득 `DurablePassiveLearningTransaction`, 잠재력 등급 및 난수 추첨 `PotentialScrollSelection`, 잠재력 확정·SQL/receipt `PotentialScrollTransaction`, 커밋 후 능력치 적용 `PotentialSkillEffects`의 5개 소유자로 나눔. 스킬 사용 호출부는 새 클래스를 직접 사용. `debit → SQL/receipt → COMMIT 후 능력치/메시지` 실행 순서는 유지.
2. **기존 `ResolventTransactions` 제거.** 일반 용해 `OrdinaryResolventItems`, 드래곤 용해 `DragonResolventItems`, 대상/주문서 검증 `ResolventPairRules`, 원자적 소비·결과 지급 `ResolventSettlement`의 4개 책임으로 나눔. 기존 보너스 지급 후 기본 결정 지급, lastUsed 갱신, 커밋 후 메시지/딜레이의 실행 순서를 유지.
3. **`CreatureItemActions` 혼합 역할 축소.** 랭커 가호 두 종류는 `TopRankerBlessingItems`, 일반/GM/강화 아이템 생성 세 메서드는 `ItemCreationItems`, NPC 제거 지팡이는 `NpcRemovalWandItems`, 버그베어 제어 도구는 `BugRaceControlWandItems`로 분리. `CreatureItemActions.createNewItem`의 **기존 public 진입점은 그대로 유지**하며 직접 위임. 공통 `UseItem`/`cancelAbsoluteBarrier` 진입점도 유지.

## 검증
- **실제 JDK 21 파서:** 전체 생산 Java **1,969개, 구문 오류 0건**.
- **17차 구조 검사:** 기존 18개 메서드의 본문 완전 동일성(소유 클래스 호출만 정규화), 잠재력 등급 배열/효과 제거 SQL 목록 동일성, 디스패처 분기 무변경, 기존 클래스 참조 제거를 확인.
- **17차 Java 21 타입 컴파일 및 원본 대비 실행:** 실제 추출된 `DurableSkillItems` 및 용해 도메인 생산 메서드를 테스트용 게임/트랜잭션 모델에 타입 연결하여 **15,126개 입력에 대한 효과·debit·grant·후처리 호출 로그가 원본과 동일**함을 확인. 이 테스트에서 DB SQL 콜백은 실제 DB 실행 대신 등록 순서만 비교함.
- **16차까지의 아이템 테스트 전부 통과** (15차 62개 분기 본문, 14차 낚시/물약/레이드, 13차 빙수/텔레포트 등).
- **방송 114차 회귀검사 통과** (오류 주입 및 5,000수신자 테스트). 방송 생산 코드는 **변경 없음**.
- 최종 전체 SRC ZIP 항목 CRC, 경로 중복, 기존 파일 byte-identical 범위, 모든 신규·삭제 경로 검증 수행.

## 재실행
- `bash docs/item_domain_phase17_20261118/run_tests.sh` (JDK 21, Python 3 필요).
- `docs/item_domain_phase17_20261118/MANIFEST.json`: 18개 이전 메서드 SHA-256, 신규·수정·삭제 목록.
- `*.baseline.java`: 16차 원본 코드 조각 (검증용, `src` 바깥).

## 아직 검증하지 않은 것
- **전체 게임 외부 의존성 타입 컴파일**, 실제 MySQL/CacheD 성공·ACK 유실·실패 주입, 실서버 게임 내 사용 및 동접 5,000명 부하 테스트는 아직 수행하지 않음.
- `ItemCreationItems`·랭커/GM 지팡이 클래스는 소스 본문 동일성 검사 대상이며, 별도의 실제 게임 모델 타입 컴파일/인게임 시나리오 테스트는 미수행.
- 전체 아이템 영역의 모든 특수 효과·대형 클래스 분리가 완료된 것은 아님. 다음 차수에는 실수정 대상 및 실제 호출부를 다시 전수조사하여 진행해야 함.
