# RKern 아이템 103차 — 카드 컬렉션·수호성 영속 등록

## 기준과 범위

기준은 `RKern_ItemOnly_CardAlchemy_Allocation_Phase102_FULL_SRC_20261118.zip`(102차)이며, 이번 작업의 **생산 Java 변경은 정확히 두 파일**입니다.

- `src/l1j/server/server/model/item/Card/L1CardAdd.java`
- `src/l1j/server/server/model/item/function/TransformationCollectionCards.java`

네트워크/CIO, NPC, 방송, CacheD·Account 공통 구현은 변경하지 않았습니다. 기존 생산 파일은 102차와 바이트 단위로 동일합니다.

## 변경 사항

1. `L1CardAdd`: 전설 23종, 영웅 36종, 신화 11종 등 **컬렉션 카드 70개 ID**를 원본과 1:1 매핑했습니다. 기존 `Map<Integer, CardUpdate>` 5개와 람다 수십 개를 제거하고 `switch(int)`로 정적 조회합니다. 등록은 아이템 1개 감소, 계정 카드 수 +1, 최초 등록 시 등급별 컬렉션 점수 +1을 **하나의 L1InventoryTransaction + CacheD/SQL receipt**로 처리합니다. `accounts` 트랜잭션 테이블 확인, DB preimage WHERE 검사 및 실패 시 롤백을 사용합니다. 계정 메모리 반영/안내/컬렉션 보너스는 커밋 이후 CIO 소유자 콜백에서만 일어납니다.
2. `L1CardAdd.SkillTree`: **수호성 스킬 14개 ID**의 등록을 카드 1개 차감과 해당 `accounts.tree_*` 필드 0→1로 원자 커밋합니다. 이미 등록된 스킬 또는 유효하지 않은 ID는 아이템을 소모하지 않고 선택을 초기화합니다. 기존 네 종류의 UI 호출을 보존합니다.
3. `TransformationCollectionCards.handleUnlockCollection`: **기르타스 유일 카드 ID 875640295**의 아이템 감소, `info_kir_poly` 증가, `info_col_y=1`을 같은 receipt로 확정합니다. 패킷/컬렉션 효과는 커밋 이후만 실행합니다.

카드·점수의 기존 메모리 값이 SQL `WHERE`의 예상 값과 다르면 데이터베이스는 해당 UPDATE를 0건 처리하고 거래 전체가 실패합니다. 이 경우 카드 아이템의 영속 차감도 같이 롤백됩니다. 서로 다른 계정/카드 ID는 사용자 입력을 SQL 식별자로 사용하지 않습니다.

## 검증

- Java 21 **실제 변경 생산 2개 클래스**를 테스트용 타입 모델과 함께 컴파일하고 70개 매핑, 14개 수호성, 유일 카드의 성공/실패/최초 보너스/중복/잘못된 ID를 검사했습니다.
- 모의 영속 커밋/실패 시나리오 **2,498개 assertion** 통과: `L1CardAdd` 2,464 / 유일 카드 34. 컬렉션 모델 동작 462회 포함.
- 전체 SRC의 Java 21 구문 파싱 **1,923개 파일, 오류 0건**.
- 원본 102차 대비 2개 생산 파일만 수정, 그 외 기존 항목 동일, ZIP CRC 확인.

재검증: 전체 ZIP을 해제하고 루트에서 `python3 docs/item_only_phase103_20261118/test_103.py`. JDK 21 필요. 이 테스트의 SQL 및 트랜잭션은 Java 타입을 갖춘 **모의 객체**이지 실제 MySQL 서버가 아닙니다. 생성되는 `fixture_work`는 검증용이며 생산 소스에 포함되지 않습니다.

## 잔여 위험 / 미검증

- **별도 직접 등록 경로**: `src/l1j/server/server/model/network/core/cs/packet/CS_Attr.java`의 `MSGCODE_622_Card`, `Card2`, `Card3`, `Tree`에는 카드 아이템 차감과 계정 메모리/SQL 저장을 분리 수행하는 레거시 구현이 존재합니다. 네트워크 패킷 코드 변경 금지 범위 때문에 이번 차수에서는 건드리지 않았습니다. 그 경로의 거래 안전성은 **아직 해결되지 않았습니다**.
- 합성 101·102차에서와 마찬가지로, DB 커밋 이후 계정 메모리 preimage가 다른 쓰기 경로로 바뀐 경우 수동 재동기화/격리 정책은 공통 Account·CacheD 범위 검토 없이 해결할 수 없습니다. 새 콜백은 불일치를 감지하고 성공 안내를 중단하며, 이 상태의 운영 자동 복구는 아직 검증되지 않았습니다.
- 운영 MySQL/InnoDB 장애·ACK loss, 동시 카드 등록 경쟁, 전체 의존성 타입 컴파일 및 인게임 검증은 수행하지 않았습니다. 수치 차수만으로 전체 아이템 리팩토링 완료를 뜻하지 않습니다.
