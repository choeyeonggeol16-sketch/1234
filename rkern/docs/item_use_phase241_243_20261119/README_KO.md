# 241~243차 아이템 최종 검수

- 241: 기존 CacheD 확정 콜백에서 `L1InventoryTransaction.onDurableCommit`을 통해 구슬 매핑 및 탐 캐시를 갱신한다. 세션과 무관하며 UI는 원래 CIO 완료 경로에만 남긴다. CacheD 구현 자체는 변경하지 않았다.
- 242: `MJCTObject`의 패킷 필드를 volatile로 안전하게 게시하고, 구슬별 작업 중복 방지, 실패 후 1초 냉각 및 다음 이름 조회 재시도, 삭제/교체/해제된 객체의 지연 작업 무효화, 일부 미완성 패킷만 재생성, 존재하지 않는 캐릭터 행 처리. 기존 instant pool 사용. 캐릭터 정보 날짜 포맷터의 동시 접근 보호.
- 243: 전체 Java 1,913개 구문 오류 0, 198~240차 및 신규 검증 스크립트 14개 성공(라우팅 감사 2개 포함). 구슬 SQL/receipt double 212 checks, 실제 loader/object + JDBC/executor double 224 checks. 전체 javac --enable-preview --release 21 -proc:none: 531 errors, 5 warnings. 240차 기록과 파일/오류 종류별 진단 수 동일. 전체 빌드 성공은 아님.

실제 MySQL, CacheD ACK loss, 실서버 동시 접속, JFR/heap soak, 운영 DB의 미연결 ID 53개 확인은 미수행. 구슬 매핑 초기 DB 읽기 실패/장애 복구는 실서버 점검 필요. 기존 DB 마이그레이션/백업 주의사항(240차) 유지. 새 스레드/큐/생산 클래스 없음.
