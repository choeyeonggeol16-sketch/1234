# 아이템 function 구조 변경 및 테스트 보고서 — 2026-11-17

## 기준과 범위
- 114차 RKern Native Broadcast 전체 SRC를 그대로 기반으로 함.
- **아이템 사용 처리의 Java 클래스 이름과 위임 구조만 변경**. 패킷 포맷·방송·SEND/RECV·CIO·CacheD/DB 구현은 건드리지 않음.
- `model/item/function`의 `CItemUse` / `ItemUse*` **68개 클래스**를 기능별 의미가 드러나는 클래스 이름으로 변경. 이전 클래스 파일 제거.
- `CItemUse`의 보상·용해·제작·인형·혈맹 등 단순 위임 메서드를 제거하고 원래 처리 클래스에 직접 연결. 특수 마안/레이드/수표 진입만 `SpecialConsumableRouter`에 유지(원래 분기 순서 보존).
- 아이템별 실제 효과/소모/보상/확정(COMMIT)/쿨타임 구현은 그대로 유지.

## 할당·조회 최적화
- 전역 중앙 클래스의 9개 정적 보상 테이블을 `EnchantBoxRollTables`, `WeaponTransformationTables`, `ClassGiftBundleTable`로 분리. 관련 경로 최초 사용 시에만 초기화되는 **지연 클래스 초기화**를 사용; 모든 아이템 사용 시 무조건 보상 테이블을 로드하지 않음. 보상 ID/확률 배열 모두 원본 동일.
- 대상 오브젝트 ID가 0이면 `L1Inventory.getItem(0)` 선형 순회를 생략. IdFactory의 지속 오브젝트 ID 시작값은 `0x10000000`.
- 레이드 보스 CSV 설정은 변경되기 전까지 파싱 결과를 `volatile` 불변 스냅샷으로 재사용. CSV 내용이 변경되면 다음 호출에서 다시 파싱. 보스 선택 공식과 파서는 변경 없음.
- 불필요한 쓰레드, 큐, 동기화 실행기, per-item 컬렉션 생성은 추가하지 않음.

## 검증
- Java 21 JavacTask 파서로 생산 Java **1,713개** 전부 구문 검사: 0 오류.
- `ItemActionDispatcher`의 **203개 `case` 분기 그룹**을 114차 원본과 비교: 동일.
- `SpecialConsumableRouter` 실제 생산 Java를 typed stub과 `javac`로 컴파일 후 마안 7종, 수표 3종, 레이드 3종, 일회용 마안, 미지원 ID, 쿨타임 동작 검증: 통과.
- `ItemActionContext` 실제 생산 Java를 typed stub과 `javac`로 컴파일 후 `ITEM`, `DEFAULT`, 대상 없음(0) 검색/반환 동작: 통과.
- 9개 아이템 확률·보상 테이블을 114차 원본과 데이터 비교: 완전 동일.
- `MaanRaidTransactions.parseBossIds` 본문을 원본과 텍스트 비교: 동일. 새 캐시 생산 코드의 CSV 파싱/설정 변경/10만 회 조회 재사용 Java 21 회귀: 통과.
- `docs/phase114_broadcast_only/run_tests.sh`: 기존 방송 모의 회귀 통과.
- 68개 이전 클래스명의 잔존 Java 타입 참조 검사: 없음 (`CS_ItemUse`, `ItemUseUtil`, `L1ItemDelay.onItemUse` 등은 별도 기존 API로 보존).
- 변경된 `User.java`, `CS_ItemUse.java`의 차이는 클래스 이름 토큰 치환뿐임을 역치환으로 확인.

## 한계
- 게임에 필요한 외부 라이브러리 JAR가 포함되지 않아 전체 의존성 타입 컴파일은 수행하지 못함. Java 생산 핵심 2개 클래스의 typed-stub 컴파일만 수행.
- 실제 MySQL/CacheD 트랜잭션, 인게임 전체 아이템 ID, Windows IOCP 및 5천 명 접속 부하는 미검증.
- 공개 Java 클래스의 바이너리 이름이 바뀌므로 외부 JAR/플러그인이 이전 `ItemUse*` 클래스를 직접 참조한다면 해당 플러그인 재컴파일/이관이 필요. 본 SRC 내의 참조는 함께 수정함.
- 부하 시 RAM/GC 개선 수치는 실측하지 않았음. 위 변경은 특정 선형 조회·반복 파싱·초기화 범위를 줄이는 구조적 개선임.

## 재실행
`bash docs/item_function_refactor_20261117/run_tests.sh [114차_원본_SRC.zip]`
원본 ZIP 경로를 주면 분기/배열의 원본 대조도 실행함. 없으면 typed stub와 현재 SRC 구조 검증만 실행함.
