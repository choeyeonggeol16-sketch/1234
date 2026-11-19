# RKern 아이템 220~225차 추가 검수

기준: RKern_ItemUse_CombatReagent_Audit_Phase214_219_FULL_SRC_20261119.zip. 아이템 사용 및 직접적인 낚시/텔레포트/요리 참조 수명만 수정. NPC/네트워크 코어/방송/CacheD 구현은 변경하지 않음. 생산 클래스 신규/삭제 없음.

## 수정
- 220: FishingRodSession, L1PcInstance: 낚시 시작 조건 통과 후 rod 참조 게시, UI/등록 예외 또는 즉시 등록 거절 시 정리, setFishing(false)에서 rod/time/ready 정리, offFishing에서 타이머 취소를 패킷 이전에 실행, dispose에서 참조 해제.
- 221: FishingTimeController: 죽음/잘못된 맵/소유권이 바뀐 낚싯대는 종료, 경제 트랜잭션/거래 중은 재시도, 낚싯대 삭제 실패 시 보상 낚싯대 지급 차단. 원본 낚싯대 종류를 유지한 채 종료하여 해당 종류 아이콘을 정리. L1Teleport: 이동 중 낚시 상태 및 타이머 종료, ThreadLocal 임시 대상 목록 finally 정리 강화.
- 222: ItemActionCompletion: 이미 소모된 객체에 delay timestamp를 저장하지 않음. 마지막 charge 삭제 실패 시 live charge 변경 금지. 성공한 사용의 delay 적용 유지.
- 223: TopRankerBlessingItems / ItemActionDispatcher: 55558 공통 완료를 receipt commit 이후 한 번 호출, pending 중 COMPLETE 방지. 5558 동일 분기 제거.
- 224: ResurrectionConsumables: target 1회 lookup, 기존 map/id/point/생존 검사와 동일한 조건으로 플레이어 목록 한 번 순회, ArrayList 결과 생성 및 중복 조회 제거. 부활 메시지/보상 규칙 유지.
- 225: L1TreasureBox: 새로운 정의 맵을 완성한 후 volatile publication. 삭제된 XML 항목은 구 버전에 남지 않고, 실패 시 기존 정상 스냅샷 보존. PC dispose 시 CookingRegistry weak-key 활성 상태 명시 제거. 실제 확률 테이블과 보상은 수정하지 않음.

## 검증
- JDK21 생산 Java 1913개 parse 0 errors (타입 전체 컴파일 아님).
- model/item 하위 Java 366개, static 필드 713개 AST 인벤토리. 타입명 기준 후보 22개, 캐릭터/아이템 타입 후보 2개. 후보는 실제 참조 경로 추가 검토. CalcExp.Work처럼 간접 참조도 있으므로 전체 heap 도달성 증명은 아님.
- ID 라벨 1658개(메인, 보조, 하위 라우터, target decoder) 정수 상수 해석/개별 switch 중복 없음. ID DB 사용 여부 검증과는 구분.
- 실제 생산 메서드를 추출한 Java 21 모의 테스트: 낚시 449, 완료/차감 136, 부활 점유 64, 보물상자 리로드 7 checks. 기존 115차 typed fixture를 확장한 154 assertions 통과(정상의 가호 commit/rollback 완료 호출 검사 추가). 일부 변경 메서드(실제 타이밍 휠 내부, 낚시 보상 전체, 실제 텔레포트, 전체 logout)는 전체 실행 검증되지 않음.
- 이전 198~203 및 214~219 모의 회귀 통과. 214~216 registry 1,000,002 probes, target decode 65, Elven 512+14, Magic Shield 130; 217~219 1538. 로그는 동일 폴더.

## 남은 확인
- TamFruitUseService: 탐 예약 INSERT의 SQLException 무시 뒤 소모가 진행될 수 있는 코드가 확인됨. 신규 활성과 소비도 별도 저장(Tam 업데이트 후 item remove)이고, 대상 계정 소속 검증이 보이지 않음. 단순 순서 교체 불충분; 동일 receipt 및 계정 소유권/동시성 검증이 필요한 별도 작업으로 분리. 변경하지 않음.
- CalcExp.Work.psi.partyMembersRef: 분배 후 finally가 acq/inScreen 배열은 지우지만 별도 파티 참조 배열은 다음 reset까지 유지할 수 있음. 전투 경험치 분배부이므로 이번 아이템 사용 범위에서 제외.
- 남은 보조 ID 53개 및 테베 이동/보물상자 비정상 XML 확률은 운영 아이템 DB/XML 부재로 정확한 의도 미확인. 임의 활성화/확률 재계산 안 함.
- 실제 장시간 JFR/heap dump, 전체 의존성 타입 컴파일, MySQL/CacheD 오류 및 인게임 검증은 미실시. 이 ZIP에 서버 외부 라이브러리 JAR/운영 DB/XML이 포함되지 않음. 메모리 누수 완전 해소라고 단정하지 않음.
