# 238~240차 최종 오류 수정

237차 기준. 생산 Java 5개 수정, 생산 클래스 추가/삭제 없음.
- L1Tam.tamdel: 삭제된 helper 호출을 기존 SQL의 prepared statement로 복원. 기존 한 건 삭제 정책 유지.
- CSharedBroadcast.sendWrittenClanBestEffort: Stream iterator로 순회, 기존 순서/중복/close 보존.
- MJCTHandler: 캐릭터 구슬 생성/해제의 아이템 debit/grant, 계정 소유권 변경, 기존 TAM 예약 한 건 삭제, tb_mjct_mapping insert/delete를 기존 inventory receipt에 함께 stage. 계정/캐릭터/매핑 행 잠금, 대상 offline/CacheD pending 확인; 계정 슬롯 확인. 성공 후 UI 및 cache 갱신. 기존 caller 자체 TamTime을 잘못 지우는 부작용 제거. 구슬 item template은 비중첩이어야 함.
- ItemActionDispatcher: 구슬 사용 공통 완료를 커밋 후 수행; pending 중 즉시 완료하지 않음.
- MJCTSystemLoader: 매핑 DB 영속화 후 종료 시 캐시로 테이블을 재작성하지 않음. 초기 캐릭터 정보 목록 순회, 빈 스킬 큐, JDBC cursor 및 cache lock 수정.

검증: JDK21 syntax 1913/0. JDK21 --enable-preview 전체 타입 컴파일 533->531; 기존 두 오류 제거, 진단 (파일, 메시지) multiset 새 오류 없음. 외부 의존성 부재로 전체 빌드 실패. 9개 기존 아이템 회귀 스크립트 통과(204~210의 승인된 구슬 dispatch 변경만 whitelist). 신규 실제 production handler + typed game/JDBC/receipt doubles 검사 200개; TAM/broadcast 실제 메서드 typed scenarios 7개. 보조 미확인 ID 53개 기존 상태 보존.

운영 적용 전: 기존 서버 정상 종료와 tb_mjct_mapping 백업/대조 필수(구버전은 종료 시에만 매핑 저장). accounts, characters, tam, tb_mjct_mapping 및 기존 receipt tables InnoDB 필요. 매핑 marble_id/char_id 유일성 확인. 실제 DB 장애/ACK 유실과 재로그인 경쟁, 아이템 소비/지급, 메모리와 부하 미검증. 연결 종료로 owner callback이 생략되는 경우 SQL 매핑은 권위 있는 기록이지만 구슬 외형/정보의 인메모리 cache 반영이 지연될 수 있음(재시작 시 DB에서 다시 로드). 이전 TAM 예약 한 건 삭제 정책은 의도적으로 유지. 미연결 ID 및 다른 방송/네트워크 코어/CacheD 구현 변경 없음.
