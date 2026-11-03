## 2026-11-03 — V3: 사용하지 않는 광고 서버 비활성화

- [V3 전체 소스 다운로드](https://raw.githubusercontent.com/choeyeonggeol16-sketch/1234/main/downloads/Netty42_Monster_Burst_Fix_V3_20261103_FULL_SRC.zip)
- [V3 패치 다운로드](https://raw.githubusercontent.com/choeyeonggeol16-sketch/1234/main/downloads/Netty42_Monster_Burst_Fix_V3_20261103_PATCH.zip)

사용하지 않는 광고 서버의 초기화·종료 호출을 GameServer에서 제거했습니다. V2 적용자는 GameServer.java만 교체하고 재빌드하세요. 게임 Netty 송신 설정은 유지합니다. 전체 소스 참조 검사와 Java 구문 분석 통과, 전체 게임/DB 기동은 별도 검증이 필요합니다.

---

## 2026-11-03 — V2: 광고 서버 기동 오류 수정 포함

- [V2 전체 소스 다운로드](https://raw.githubusercontent.com/choeyeonggeol16-sketch/1234/main/downloads/Netty42_Monster_Burst_Fix_V2_20261103_FULL_SRC.zip)
- [V2 패치 다운로드](https://raw.githubusercontent.com/choeyeonggeol16-sketch/1234/main/downloads/Netty42_Monster_Burst_Fix_V2_20261103_PATCH.zip)

이전 몬스터 순간 송신 수정 후 `AdCheckManager.init`에서 발생한 `globalOutboundBytes must be in 2097152..17179869184` 오류를 수정했습니다. 광고 리스너에 별도 세션 예산 512KiB/512개를 명시합니다. 게임 세션 2MiB/8192개는 유지합니다. 이전 수정본 사용자는 AdCheckManager.java를 교체하고 재빌드하면 됩니다. 실제 광고 서버 초기화·LE32 TCP 요청·연결 정리 회귀를 통과했으며 게임 DB 통합은 별도입니다. ZIP 내부 적용 안내를 확인하세요.

---

## 2026-11-03 — Netty 몬스터 대량 생성 송신 제한 수정

- [수정 전체 소스 ZIP 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Netty42_Monster_Burst_Fix_20261103_FULL_SRC.zip)
- [교체 파일 패치 ZIP 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Netty42_Monster_Burst_Fix_20261103_PATCH.zip)
- [전체 소스 SHA-256](downloads/Netty42_Monster_Burst_Fix_20261103_FULL_SRC.zip.sha256) · [패치 SHA-256](downloads/Netty42_Monster_Burst_Fix_20261103_PATCH.zip.sha256)

사용자가 제공한 `Netty42_Mailbox_Progress_ReadResume_20261103_FULL_SRC.zip` 기준입니다. 기존 송신 한도 512개/512KiB에서 정상 순간 송신도 연결 종료되는 문제를 재현해 기본값을 8,192개/2MiB로 조정하고 제한 원인 로그를 추가했습니다. 실제 Netty TCP 6,000개 패킷 전달·입력 진행·동시 송신·예산 반환 등 6종 검사를 통과했습니다. 실제 게임 AI/DB 대량 생성 부하는 미재현이며, 먹통의 유일한 원인으로 확정하지 않습니다. 아래 이전 통합본과 기준 소스가 다릅니다.

**적용:** ZIP 내부 `APPLY_MONSTER_BURST_FIX_KO.md`를 확인하고 JDK 21로 재빌드하세요. 기존 `config/network.properties`에 설정이 있다면 다른 항목은 유지하고 `outboundPackets=8192`, `outboundBytes=2097152`로 변경해야 합니다. 테스트 코드와 로그도 ZIP에 포함했습니다.

---

**최신 전체 소스 통합본 — 2026-11-02**

**[전체 src 970개 + Ant 빌드 설정 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_Netty_4.2.19_Final_ALL_SRC_970_20261102.zip)**

지금까지 수정한 내용을 모두 합친 전체 소스입니다. Java 파일 970개를 모두 포함합니다. 기존 프로젝트의 src를 백업한 뒤 ZIP 안의 src 전체와 build.xml로 교체하고, 기존 비-Netty lib 및 게임 데이터를 보존하세요. 전체 Ant 컴파일과 JAR 생성 성공: 오류 0, warning 0. 사용자 Eclipse의 실제 호환 오류 및 GUI/DB 기동은 미재현입니다.

아래는 이전 통합본과 부분 수정 기록입니다.

**2026-11-02 — Netty 4.2.19.Final 네트워크 수정본**

[XML helper의 SAXException 명시 참조 수정 누적 패치](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_Netty42_SAX_Reference_Fix_20261102.zip) — private 메서드의 throws를 Exception으로 통일하고 예외 전파 유지. 전체 970개 컴파일 및 실제 XML 정상/오류 입력 검사 통과. 사용자 Eclipse 오류 자체는 미재현.

[삭제 예정 생성자 경고 45곳 수정 패치](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_Netty42_Warnings_Fix_20261102.zip) — Java 26개 파일과 Ant 빌드 파일. 서버 970개 전체를 `-Xlint:removal -Werror`로 컴파일하고 JAR 생성 성공: 오류 0, warning 0. 일반 deprecated API 안내 Note는 남아 있습니다.

[Ant build.xml 수정본 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Netty42_Ant_Build_Fix_20261102.zip) — Netty/JAXB 빌드 클래스패스 수정. 실제 서버 Java 970개 전체 컴파일 및 JAR 생성 성공(오류 0, 경고 45). GUI/DB 기동은 미검증.

[Netty JAR 5개·Windows 실행 예제 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Netty_4.2.19_Final_libs.zip)

**[전체 수정 소스 ZIP 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_Netty_4.2.19_Final_Full_Source_20261102.zip)** · [검증 보고서](RKern_NETTY42_20261102_VALIDATION.md) · [SHA-256](downloads/RKern_Netty_4.2.19_Final_Full_Source_20261102.zip.sha256)

마지막에 업로드한 src.zip 기준으로 자체 NIO를 Netty 4.2.19.Final로 교체했습니다. JDK 21이 필요합니다. 전체 수정 소스, Netty JAR 5개, 설정, 재현 테스트와 로그를 포함합니다. 실제 TCP 동시 연결 1,000개·암호화 요청/응답 20,000회와 18개 네트워크 회귀 검사를 통과했습니다. 원본 게임/DB 의존성이 없어 전체 게임 서버 빌드·기동 및 실제 게임 동접은 미검증입니다.

---

# RKern FIFO/group·batch/READ 패치 재시험 — 2026-10-28

[최신 재시험 보고서](RKern_P0_P2_RETEST_REPORT_20261028.md) · [코드·원시 결과·회귀 시험 ZIP 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_P0_P2_retest_evidence_20261028.zip)

직전 업로드 대비 기본 작은 묶음 +50.5%, 1,024연결 +30.1%, 4KiB 송신 +14.2%. FIFO 오류 재현 조건은 통과했습니다. 별도 batch 호출은 Netty epoll 처리량과 동률권이지만 실제 게임 호출 연결은 아직 없고, 4KiB p99 및 임시 SEND 버퍼 회귀가 남았습니다. 프로파일 없는 성능 실행 72회, 정합성 실행, 실패 단언과 정제 프로파일 근거를 포함합니다. 업로드 소스를 추가 수정하지 않은 검증 자료이며 전체 서버 배포 ZIP은 아닙니다.

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
