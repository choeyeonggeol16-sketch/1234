# 최신 RKern DB 설치와 업데이트

SQL은 MySQL 5.7과 MariaDB에서 사용할 수 있습니다. 예시의 `net`은 실제 DB 이름으로 바꾸세요.

## 새 DB

1. 빈 DB를 만든 뒤 `net_69_SRC_optimized_clean_install_20261031.sql`을 가져옵니다. 이 덤프에는 `DROP TABLE`이 들어 있습니다. 대상 DB가 비어 있는지 확인하세요.
2. [CacheD 저장 스키마](CacheD_storage_schema_migration.sql)를 적용합니다. 캐릭터 HP·MP 성장 이력, 각 창고의 장비 품질, 패키지 창고의 축복·아이템 레벨을 저장할 열을 추가합니다. 이 버전의 덤프에는 거래 영수증, 경매 미정산금, 레벨 보상 테이블과 필요한 InnoDB 엔진이 들어 있습니다.

```sh
mysql --default-character-set=utf8mb4 net < net_69_SRC_optimized_clean_install_20261031.sql
mysql --default-character-set=utf8mb4 net < database/CacheD_storage_schema_migration.sql
```

## 기존 DB

서버를 내리고 백업한 뒤 테스트 DB에서 다음 순서로 실행합니다.

1. 이전 경제 스키마 적용 이력이 없다면 [거래 영수증](CacheD_transaction_receipts_migration.sql), [경매 미정산금](CacheD_economy_credit_migration.sql), [경제 테이블 InnoDB 변환](CacheD_economy_tables_migration.sql)을 순서대로 적용합니다. InnoDB 변환은 테이블을 재빌드할 수 있으므로 이미 적용한 경우 건너뜁니다.
2. [CacheD 저장 스키마](CacheD_storage_schema_migration.sql)를 적용합니다. `rkern/sql/character_hp_mp_growth.sql`을 이전에 적용했어도 다시 실행할 수 있습니다. 빠져 있는 `character_level_gifts`는 기존 팩과 같은 복합 기본 키로 생성됩니다.
3. 거래 영수증, 아이템, 여섯 창고, 퀘스트·레벨 보상 테이블이 모두 InnoDB인지 확인합니다. `character_quests`와 `character_level_gifts`에는 각각 전체 `(char_id, quest_id)`, `(char_id, gift_level)` 고유 키가 필요합니다. 기존 테이블의 엔진이나 고유 키를 바꿀 때는 데이터를 검토해 별도로 조정하세요.
4. 필요하면 [CacheD 소유자 조회 인덱스](CacheD_owner_lookup_indexes.sql)를 적용합니다. 기존 DB에서 인덱스를 만드는 시간은 테이블 크기에 따라 달라집니다.

테스트 DB에서 [CacheD 저장 스키마](CacheD_storage_schema_migration.sql)를 두 번 실행해 에러 없이 반복 적용되는지 확인하세요. 캐릭터 로딩과 아이템 보관을 확인한 뒤 운영 DB에 적용합니다. 이 변경은 운영 DB에 적용되지 않았습니다.
