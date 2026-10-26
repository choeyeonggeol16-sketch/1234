# RKern v3 — 피드백의 커밋/CIO 경계 수정과 DB SQL 3개

이번 변경은 사용자가 마지막으로 지정한 범위인 **P0·P1 소스 수정 및 기존 net DB용 보완 SQL 3개**다. 전달했던 v2 전체 소스에 아래 Java 6개 변경을 더했다. 전체 DB를 다시 작성하거나 기존 계정·캐릭터를 초기화하지 않았다. 제공된 net 원본 덤프와 플레이어 행은 GitHub 게시물에 포함하지 않는다.

## 소스 수정

| 파일 | 수정 |
|---|---|
| `storage/CacheD.java` | `WarehouseMove`의 SQL 실행/결과 확인과 커밋 후 인덱스·콜백 실행을 별도 예외 경계로 분리. `commitConfirmed`와 `CommittedReplayException`으로 후자의 실패를 반드시 격리. RuntimeException, Error, 조기 실패 ACK, 성공 ACK 뒤 예외, 실패 통보 중 예외에도 양쪽 FIFO를 열거나 SQL을 재실행하지 않음. 확정 롤백은 기존처럼 종료 가능. |
| `model/L1InventoryTransaction.java` | 새 커밋 후 반영 실패 표시를 cause chain에서도 식별. PC 아이템 게이트를 유지하고 ‘아이템이 이동되지 않았다’는 롤백 안내/콜백으로 잘못 처리하지 않음. |
| `model/network/thread/CIOThreadCallback.java` | `completeSession`을 소켓 affinity의 기존 CIO 타이밍 휠로 전달. 원래 소켓·계정·actor·session stamp·접속 단계·월드 세대 재검사. admission과 반영 완료를 구분하며, 거절·취소·예외도 한 번 결과를 통보. 전역 메타데이터는 기존 CIO shard 0에서 `completeWorldMutation`으로 반영. 새 워커/큐/스레드 없음. |
| `model/network/core/cs/packet/REnterWorldHandler.java` | 로그인 완료를 CIO 경계로 전달하고 거절된 준비 데이터 정리. 저장/템플릿 북마크 SQL은 DB 준비 단계로 이동하며 로그인 차단 SQL도 기존 CacheD에 게시. |
| `model/network/core/cs/packet/RCreateCharHandler.java` | 생성 SQL을 기존 receipt/FIFO 예약에 연결하고 캐릭터 인덱스의 CIO 게시 ACK까지 계정/생성 lane 유지. 원래 접속이 종료돼도 확정된 인덱스를 게시하되 오래된 세션에 성공 응답하지 않음. 게시 실패는 격리. |
| `model/L1AuctionTransaction.java` | 커밋 후 일반 ThreadPool에서 경매 메모리를 변경하던 처리를 기존 CIO 전역 경계로 이동. 월드·집·게시물·혈맹의 원래 참조/상태를 반영 전에 확인하고, CIO 완료 결과 뒤에만 owner ACK. |

경로는 `src/l1j/server/server/` 기준이다. 이전 v2의 나머지 변경과 기능 제한은 계속 포함된다. 이 변경으로 전체 경제 경로의 복제·유실이 모두 해결됐다고 판정하지 않는다.

## SQL 3개와 적용 순서

원본 `net.sql`을 가져온 기존 net DB에, 서버를 내린 상태에서 백업 후 다음 순서로 적용한다. 처음 만드는 DB라면 먼저 Navicat 등에서 net DB를 만들고 사용자가 가진 **원본 net.sql 전체를 임포트**해야 한다. 아래 3개만으로 NPC·아이템 등 전체 게임 DB를 새로 생성하는 것은 아니다.

1. `CacheD_transaction_receipts_migration.sql` — 거래 영수증 `cached_transaction_receipts` 생성 및 필수 아이템/창고 엔진 확인.
2. `CacheD_economy_credit_migration.sql` — 경매 미지급 정산금 `character_pending_credits` 생성.
3. `CacheD_economy_tables_migration.sql` — 관련 기존 테이블 19개의 엔진을 InnoDB로 지정.

받은 DB에서 엔진 변환이 필요한 테이블은 `clan_data`, `house`, `board_auction`, `character_teleport`, `character_teleport_item`, `character_tams` 6개였다. 나머지 대상과 필수 창고 테이블은 이미 InnoDB였다. 신규 테이블 2개는 원본에 없었다.

SQL 3개에는 기존 행을 지우거나 덮어쓰는 DROP/DELETE/TRUNCATE/REPLACE가 없다. ALTER ENGINE은 테이블을 재구성하고 암묵적으로 커밋하므로 유지보수 중 적용한다. 이미 존재하는 receipt/채권을 비우면 안 된다. `IF NOT EXISTS`는 이름이 같은 잘못된 기존 구조를 고치지는 않는다. SQL 3개는 `database/` 폴더와 최신 전체 소스 ZIP 안의 `database/`에 동일하게 포함했다.

## 완료한 검증

- **전체 Java 1,657개 컴파일 성공**, exit 0, 기존 deprecation/removal 경고 10개. 최종 소스 SHA-256과 ZIP의 모든 Java 항목을 대조했다.
- **분리 회귀 스크립트 14종 성공.** 신규 커밋 경계 70개, CIO 완료 경계 24개, 경매 CIO 반영 39개 단언을 포함한다. 실제 클래스/메서드를 사용하지만 이 테스트들의 SQL·월드·CIO 협력 객체는 통제된 stub이며 전체 게임 실행이 아니다.
- **실제 Oracle MySQL Community 5.7.18에서 7종, 총 149개 단언 성공.** 제공 덤프의 Source/Target 버전 5.7.18과 같은 DB 버전이다. 트랜잭션 18 + 경제 도우미 25 + 계정 상점 제한 20 + 북마크 18 + 확인 비용 12 + 신규 커밋 경계 45 + 삭제 생명주기 11.
- 신규 실제 SQL 검증은 **DB COMMIT 이후 RuntimeException/Error/반영 admission 실패/커밋 응답 유실 뒤 콜백 실패**에도 실제 영수증과 SQL 결과가 남고 양쪽 FIFO가 격리되며, SQL이 다시 실행되지 않는지 확인한다. 확정 롤백과 불확실 결과도 구분한다.
- **실제 MySQL 5.7.18 강제 종료·재시작 복구 성공.** 테스트 DB에 미커밋 아이템 차감을 준비한 뒤 해당 컨테이너만 SIGKILL하고 같은 datadir로 재시작했다. 확정 영수증·아이템·채권의 행 해시와 대상 19개 테이블 11,903행의 검증값이 유지됐고 미커밋 차감은 롤백됐다. 게임 서버 프로세스의 강제 종료/재접속 검증과는 구분한다.
- **받은 DB의 373개 테이블 DDL과 대상 19개 테이블의 실제 데이터 11,903행**을 작업 전용 MySQL schema에 가져와 SQL 3개를 실행했다. 적용 전후 테이블별 행 수와 전체 행 CRC 집계값이 같았고, 대상 엔진은 모두 InnoDB가 됐다. 재실행 시 기존 테스트 영수증/채권 행도 보존했다. 원본의 나머지 모든 데이터 행을 가져온 전체 서버 기동 검증은 아니다.

DB 응답 유실은 실제 JDBC 연결에 주입한 장애를 포함한다. 주입한 오류/격리 시나리오의 SEVERE 로그를 정상 플레이 성공으로 계산하지 않았다. 실제 게임 서버·클라이언트 로그인/경매 UI, 모든 로그인 부수 효과와 종료·재접속 경합까지 통합 실행한 것은 아니다. 검증용으로 격리한 localhost DB만 사용했으며 운영 DB에는 연결하지 않았다.

주요 기록은 작업 공간의 `validation/work-build.json`, `validation/round3-test-logs/`, `validation/mysql/logs/results.json`, `validation/mysql/migration-result.json`에 있다. 신규 테스트는 `tests/v3_commit_boundary/`, `tests/v3_session_owner/`, `tests/v3_auction_owner/`다.

## 전달

- 최신 전체 소스: `downloads/RKern_full_source_v3.zip`
- 보완 SQL: `database/`의 위 3개 파일
- 이전 v2 ZIP과 보고서는 과거 기록으로 유지하며, 이번 피드백 수정은 v3 ZIP에 포함된다.

GitHub에는 요청한 수정 소스와 SQL 3개 및 설명만 게시한다. 원본 net 덤프의 계정·캐릭터 데이터를 게시하거나 초기화하지 않는다.
