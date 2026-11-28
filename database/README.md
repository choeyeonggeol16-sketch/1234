# 최신 RKern DB 설치와 업데이트

SQL은 MySQL 5.7과 MariaDB에서 사용할 수 있습니다. 예시의 `net`은 실제 DB 이름으로 바꾸세요.

## 2026-11-29 전체 설치본

빈 DB에는 [Net.sql](Net.sql)을 한 번 가져오면 됩니다. 11월 26일 드라이브에 업로드된 Net 원본에 [통합 업그레이드 SQL](Net_upgrade_20261129.sql)과 몬스터북 매핑을 반영했습니다.

```sh
mysql --default-character-set=utf8mb4 net < database/Net.sql
```

공개 설치본에는 게임 스키마와 설정 데이터가 들어 있습니다. 계정, 캐릭터, IP, 거래, 플레이 기록은 제외했습니다. 별도 MariaDB 11.4 빈 DB에서 전체 설치를 검증했습니다. 기존 운영 DB에는 백업 후 통합 업그레이드 SQL 또는 아래 개별 마이그레이션 절차를 사용하세요.

## 2026-11-27 마을 정산

마을 정산은 `town`, `characters`, `cached_transaction_receipts`의 InnoDB 트랜잭션을 사용합니다. 기존 Net DB에는 [거래 테이블 엔진 변환](Net_transaction_engines.sql)을 적용하고 [마을 공헌도 조회 인덱스](Net_town_lookup_index.sql)를 추가합니다.

## 2026-11-26 Net 업데이트

이번 배포 기준은 이전에 전달한 `RKern_net_b59fa545_ready_to_import.zip`입니다. 새 비공개 설치본 `RKern_Net_SQL_20261126.zip`은 빈 DB에 `Net.sql`을 한 번 임포트하면 됩니다. 이전에 사용한 아이템 옵션 스크립트도 함께 들어 있습니다. 기존 운영 DB를 업그레이드할 때는 전체 덤프 대신 아래 순서를 사용하세요.

1. 서버와 DB에 쓰는 외부 프로그램을 중지하고 백업합니다. 기존 CacheD 저장 스키마와 소유자 인덱스가 필요한 구버전은 아래 기존 DB 절차를 먼저 적용합니다.
2. [게시판 통합](Net_board_posts.sql)을 적용합니다. 새 통합 테이블이 필요한 경우 기존 글 번호와 내용을 복사합니다. 기존 `board_posts`가 있는 DB는 그 테이블을 유지합니다. 수동으로 일부만 옮긴 DB는 기존 게시판과 대조한 뒤 누락분을 조정하세요. 임시 프로시저를 만들고 삭제할 권한이 필요합니다.
3. [PSS 일일 사용량](Net_runtime_tables.sql), [거래 테이블 엔진 변환](Net_transaction_engines.sql), [조회 인덱스](Net_lookup_indexes.sql)를 적용합니다. 엔진을 변환하는 동안 해당 테이블을 재작성할 디스크 공간이 필요합니다.
4. [폐기한 기능의 빈 테이블 정리](Net_retired_empty_tables.sql)는 지정한 목록의 실제 빈 테이블만 제거합니다. 커스텀 뷰, 외부 도구 등에서 해당 이름을 사용하는 운영 환경은 의존성을 먼저 확인하세요. 데이터가 있는 옛 펫·LFC 테이블과 기존 게시판 원본은 기존 DB에 보존합니다. 신규 설치본에서는 이관 전 테이블을 ZIP 안의 `archive/retired-tables.sql`로 따로 보관합니다.
5. Linux에서 테이블 이름을 혼용한 커스텀 DB는 현재 Net의 소문자 표기와 실제 테이블 이름을 대조한 뒤 새 코드를 실행하세요.

[Net 쿼리 변경과 검증 결과](../rkern/docs/net_sql_20261126/README.md)

## 이전 원본으로 새 DB 설치

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

## JDBC 연결 시간 제한

MySQL 및 MariaDB 드라이버에서 `config/Database.ini`의 다음 값을 밀리초 단위로 설정할 수 있습니다.

```ini
ConnectTimeoutMillis=5000
SocketTimeoutMillis=30000
```

첫 값은 DB 소켓 연결을 기다리는 시간이고 둘째 값은 DB에서 데이터를 읽을 때 응답을 기다리는 시간입니다. 생략하거나 0 이하로 지정하면 위의 기본값을 사용합니다. 기존 JDBC URL에 `connectTimeout` 또는 `socketTimeout`을 직접 지정했다면 해당 값을 우선 적용합니다. 드라이버 URL에서 `0`은 해당 시간 제한을 해제합니다.
