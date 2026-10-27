# RKern 네트워크 재비교 — 2026-10-28

[Netty epoll을 넘기 위한 구현 지침](RKern_NETTY_EPOLL_IMPLEMENTATION_PLAN_20261028.md) · [추가 진단 근거 ZIP](downloads/RKern_netty_epoll_plan_evidence_20261028.zip)

후속 진단에서 owner SEND의 순서 우회, READ 완료의 간접 executor 경로, batch self-pending fallback을 확인했습니다. 수정 위치·순서·유지 조건·합격 시험과 승리 목표를 정리했습니다. 진단 ZIP은 배포용 패치가 아닙니다.

[업로드한 개선 코어와 Netty epoll 비교 보고서](RKern_NETTY_EPOLL_REBENCH_20261028.md) · [재현 코드·원시 결과 ZIP](downloads/RKern_netty_epoll_rebench_20261028.zip)

이전 v5·새 업로드·Netty epoll을 같은 환경에서 다시 측정했습니다. 유효 성능 실행 54회와 별도 고부하 정합성 오류 기록을 포함합니다. 업로드 소스는 수정하지 않았으며 기본 설정과 보완 설정의 결과를 구분했습니다.

# RKern 최신 수정본 v5 — NPC AI 최신 소스 + 네트워크 개선

**[전체 소스 v5 ZIP 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_full_src_NPC_AI_network_v5_20261027.zip)**

[수정·검증 보고서](RKern_NPC_AI_NETWORK_v5_REPORT.md)

[L2NPC.exe와 현재 NPC AI 비교 분석](L2NPC_AI_COMPARISON.md) — Desire 핵심 대응, 실제 호출 경로, 원본과 다른 스킬·배회·파라미터 처리 및 검증 범위.

[RKern v5 vs Netty 실제 TCP 비교](RKern_NETTY_BENCHMARK_REPORT.md) — 동일 CPU·패킷·암호화로 NIO/epoll 비교, 54회 성능 측정 및 재현 자료. NPC·게임 소스 변경 없음.

[네트워크 프로파일링·수정 작업 지침](RKern_NETWORK_PROFILE_GUIDE.md) — 소형 패킷의 pool/등록 비용, 4KiB 암호화 비용, p99 추가 계측과 단계별 수정·검증 순서.

새로 업로드한 NPC AI 소스 기준입니다. 짧은 액션의 임시 SEND 버퍼 대여·반환을 줄이고 v4 수신 정리를 병합했습니다. NPC AI 본체를 보존했으며, 원본의 구·신 desire 타입 충돌 11개는 호환 위임으로 해결했습니다. 전체 Java 1,670개 컴파일과 네트워크 회귀·실제 로컬 TCP 검사를 통과했습니다. DB 추가 변경은 없습니다.

이하 이전 v4/v3 기록입니다.

# RKern v4 기록 — 네트워크 경로 정리

**[전체 소스 v4 ZIP 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_full_source_v4_network.zip)**

[네트워크 수정·검증 보고서](RKern_v4_NETWORK_REPORT.md)

v3 기준 네트워크 수신 파일 1개를 49줄 줄였습니다. 전체 컴파일, 제어 I/O 회귀 238개, 실제 로컬 TCP 검증을 통과했습니다. 합성 측정에서 처리량 향상은 확인되지 않았습니다. v3의 경제/로그인 수정과 기존 SQL 3개를 포함하며 추가 DB 변경은 없습니다.

이하 v3의 DB 적용 안내와 기존 검증 기록입니다.

# RKern v3 기록

**[전체 소스 v3 ZIP 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_full_source_v3.zip)**

[수정·검증 보고서](RKern_v3_REPORT.md)

## DB 보완 SQL 3개

원본 net.sql을 가져온 DB를 백업하고 서버를 내린 뒤 다음 순서로 실행합니다. 계정·캐릭터·아이템 데이터를 초기화하지 않습니다.

1. [거래 영수증 테이블](database/CacheD_transaction_receipts_migration.sql)
2. [경매 미지급 정산금 테이블](database/CacheD_economy_credit_migration.sql)
3. [관련 테이블 InnoDB 변환](database/CacheD_economy_tables_migration.sql)

SQL 3개는 v3 소스 ZIP의 database/에도 들어 있습니다. 새 DB라면 먼저 net DB를 만들고 사용자가 가진 원본 net.sql을 임포트해야 합니다. 이 SQL 3개는 기존 게임 DB 보완용입니다.

## 이번 수정

- COMMIT 후 인덱스·콜백 실패를 확정 롤백과 구분해 양쪽 FIFO/아이템 게이트를 격리합니다.
- 로그인·캐릭터 생성·경매의 게임 상태 반영을 기존 CIO 소유 실행 경계로 전달합니다.
- 전체 Java1,657개 컴파일, 분리 회귀14종, 실제 MySQL5.7.18의149개 단언을 통과했습니다.
- 받은 DB의 대상19개 테이블11,903행을 보존하며 SQL3개 적용을 확인했고, 실제 DB 강제 종료/재시작 복구도 통과했습니다.
- 실제 게임 서버·클라이언트 전체 통합은 미실행이며 이전의 미해결 경로/기능 제한은 보고서를 확인하세요.

원본 net 덤프와 플레이어 데이터는 GitHub에 게시하지 않았습니다. 이전 v2 ZIP/보고서는 역사적 기록이며, 위 v3가 이번 피드백 수정본입니다.
