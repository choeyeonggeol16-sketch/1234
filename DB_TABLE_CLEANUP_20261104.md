# DB 테이블 기준 소스 정리 — 2026-11-04

첨부 `net.zip` 안의 `net.sql`에서 CREATE TABLE **252개**를 읽어 공개 main **63aca89**의 소스와 비교했습니다. DB에 없는 **32개 테이블**의 SQL·로더·관련 진입점을 제거했습니다. 운영 Java **39개 수정, 32개 삭제**, 남은 운영 Java는 **1,691개**입니다.

## 변경된 동작

- 없는 밸런스·특정 맵/몹·추가 드랍·맵별 스킬 제한 설정의 로더, 보정/보상 호출, 이벤트 스레드 및 리로드 명령을 제거했습니다. 기본 전투·경험치·드랍 처리는 유지합니다.
- 없는 상점 1회 제한/사용 내역과 NPC별 화폐 변경 설정을 제거했습니다. 일반 상점은 기존 기본 화폐 경로를 사용하며, CacheD 거래·저장·완료 후 지급 구조를 유지합니다. `shop_cash`가 없는 캐시 NPC는 상품 목록을 제공하지 않습니다. `spawnlist_npc_cash_shop`은 DB에 있으므로 삭제하지 않았습니다.
- 시간제 아이템 3종의 없는 보상 테이블 조회/프리로드를 제거했습니다. 해당 사용권은 사용 불가 메시지를 보내며 소모·보상 지급을 하지 않습니다. 없는 버프 구슬 설정의 사용 분기도 제거했습니다.
- 드래곤키 게시판(`board_free_key`)의 저장소 API를 제거했습니다. 해당 NPC는 빈 목록/본문을 제공하고 글쓰기를 차단합니다. 일반·공지·건의·LFC 게시판은 유지합니다.
- 전차 랭킹 스냅샷/보상과 없는 매입 로그 저장 기능을 제거했습니다. 공용 Auction/royal/royal2/meep 처리는 유지합니다. 초기화 명령에서도 없는 테이블 쿼리만 제거했으며, 명령을 실행하거나 DB를 변경하지 않았습니다.
- `touchme_reward` 설정 덮어쓰기는 제거했습니다. 시즌 레벨 보상은 원래 있던 클래스별 기본 보상과 거래 저장 절차를 유지합니다.
- `spawnlist_boss_hot`과 두 번째 보스 시간표, 계정·캐릭터·아이템·창고·거래 영수증·지급 대기 테이블은 DB에 있으므로 유지합니다. 아이템/NPC/스킬 ID나 컬럼을 기준으로 데이터를 삭제하지 않았습니다.

## 제거한 테이블 참조

- `board_free_key`
- `character_balance`
- `characters_copy`
- `door_gfxs`
- `event_boss_time`
- `expranking`
- `log_meep`
- `ncoin_give_monster`
- `party_map_info`
- `play_support_info`
- `rank`
- `reamin_data_account`
- `royal_info`
- `shop_cash`
- `spawnlist_boss`
- `touchme_reward`
- `몬스터밸런스`
- `상점계정1회제한`
- `상점제한유저정보`
- `상점화폐변경`
- `소지시템드랍`
- `스킬제한맵설정`
- `시간제아이템1`
- `시간제아이템2`
- `시간제아이템3`
- `전체밸런스`
- `착용시템드랍`
- `특정맵드랍`
- `특정맵설정`
- `특정몹설정`
- `특정템버프`
- `특화사냥터`

## 검증

- 전체 운영 Java **1,691개** JDK 21 / UTF-8 / preview 컴파일 통과. 기존 deprecated/removal 경고 5개.
- SQL 정적 분석과 런타임 테이블 계열 5종(창고, 투견 아이템 저장소, NPC 상점/스폰, 출석, 게시판)을 검사했습니다. 참조 테이블 **244개**, DB에 없는 참조 **0개**, 해석되지 않은 테이블 변수 **0개**입니다. `information_schema` 조회는 시스템 스키마로 분리했습니다.
- 아이템 사용 파이프라인 **12개 그룹**, 거래/저장 계약 **27개 그룹** 통과. 삭제된 버프 구슬 fixture는 제거하고 기존 탄약·박스·시작 지원·일반 사용 순서는 계속 검사합니다.
- 기존 아이템 효과 AST 검사: **204개 분기·860개 라벨**, 확장 처리 순서 및 패킷/거래 API 4개 유지 확인.
- 검사 도구의 누락 테이블 탐지, 컬럼/문자열 오인 방지, 시스템 스키마, 쉼표/명시적 JOIN 및 미등록 동적 테이블 거부 검사 통과.
- 원시 diff의 삭제/수정 적용과 소스 해시 일치, ZIP CRC·SHA-256 검증을 함께 제공합니다.

실제 게임 클라이언트·Windows IOCP·운영 MySQL 기동 검사는 실행하지 않았습니다. 이 검사는 테이블 존재를 확인하며 컬럼·데이터 호환성 검사를 대신하지 않습니다. 업로드한 DB 덤프나 플레이어 데이터는 GitHub/소스 ZIP에 포함하지 않았으며, 재현용 목록에는 테이블 이름만 있습니다.

## 적용

**FULL_SRC 권장:** 서버를 내리고 기존 프로젝트를 백업한 다음 **기존 src 폴더를 이 ZIP의 src 폴더로 교체**합니다. 단순 덮어쓰기는 삭제된 Java 파일을 남깁니다. JDK 21 / UTF-8 / `--enable-preview`로 전체 Clean/Rebuild 후 재시작하세요. 이전 NPC·자동사냥·아이템 V3·프로토콜·서버 버전 수정도 포함합니다. DB 수정은 필요 없습니다.

**PATCH:** 63aca89의 전체 소스 기준입니다. 변경 파일을 모두 반영하고 `deleted_files.txt`의 Java 32개를 제거해야 합니다. Git 작업 폴더에서는 아래 원시 diff로 수정과 삭제를 한 번에 적용할 수 있습니다. 다른 버전에 억지로 적용하지 말고 FULL_SRC 기준으로 병합하세요.

```bash
git apply --check --whitespace=error-all validation/db_table_source.patch
git apply validation/db_table_source.patch
python3 tools/build_item_refactor.py --output /tmp/db-table-build
python3 tools/audit_db_tables.py
python3 tests/db_schema/test_auditor.py
python3 tests/item_processing/test_use_pipeline.py
python3 tests/item_processing/test_transactions.py
```

`tools/db_schema/net_tables.json`은 테이블 이름 252개만 담은 기준 목록입니다. `validation/db_table_changed_files.json`, `db_table_source_sha256.json`, `db_table_*` 로그/결과를 참고하세요. 날짜는 한국 시간 기준입니다.
