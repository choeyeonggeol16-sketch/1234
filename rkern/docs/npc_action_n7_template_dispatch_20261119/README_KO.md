# N7 NPC 템플릿 명령 디스패처 / 보조 패킷 클래스 제거

기준 RKern_NPC_Action_N7_Travel_Buff_Domain_Safety_FULL_SRC_20261119.zip
SHA256 84c661bbbe1d4f6462a720c5f5e7c63b19d8932e141f866f08ba5093a91dcc93

- CS_NpcAcion2의 55개 템플릿 라우팅(중복 50015 1개 포함)을 NpcTemplateCommands의 정적 디스패처로 이전. 기존 HtmlCmdHandler의 리스너 실패 후 호출 위치, 54개 도달 가능 분기의 조건/우선순위, 별도 공유 Random을 유지. 두 번째 50015는 첫 조건과 완전히 동일하여 도달 불가능하므로 제거했으며 은기사 이동 명령을 새로 활성화하지 않았다. 생산 코드의 기존 보조 클래스 참조 0개.
- 직접 실행부를 기존 NpcGameEventActions(훈련소, 발라카스 준비/입장, 오림), NpcQuestTransactions(고서, 햄, 클라우디아), NpcTravelCommands(PC방 이동), NpcExchangeCommands(불골렘 방어구/무기 분기)로 이전. 방어구 재료·강화·보상 및 바닥 지급 코드는 유지.
- 햄의 주머니 820000 지급과 QUEST_HAMO 종료를 기존 stageQuestStep 및 인벤토리 receipt에 포함. 정상 레벨 60, 소지 및 진행도 조건 유지. DB 확정 후 진행도 반영/HTML 전송. 실패 시 완료 처리하지 않는다.
- 오림 비밀 연구실: 시공의 구슬 410096 1개, 아데나 10000을 기존 receipt에서 함께 debit. 성공 완료 이후 기존 좌표/맵으로 이동 및 HTML 전송. 실패 시 이동 금지. 이동 자체의 장애/재접속 보상 문제는 미검증.
- 훈련소 예약 제출에도 기존 미리보기의 99개 제한 적용. 열쇠 발급 null 반환 시 명시적 실패 안내하고 성공 페이지 차단. 부분 지급, 예약과 키 저장의 원자성 및 동시 맵 예약은 미해결.
- 발라카스 준비 및 입장: 이미 활성 방 49개 이상이면 차감/검 지급 전에 거부. 검 지급 null 반환 시 입장 중단. 동시 방 예약·스폰 실패 시 보상/복구는 미해결.
- 신규 스레드/큐/요청별 Context 없음. 기존 보조 싱글턴 제거. 인벤토리 receipt 자체의 할당 구조는 그대로 사용. 네트워크, NPC AI, CacheD 및 아이템 코어 변경 없음.

## 검증
- 55개 조건 대비 54개의 도달 가능한 조건 순서 일치, 기존 위임 호출 유지, 생산 Java에서 CS_NpcAcion2 참조 0개.
- 실제 수정된 hamo, orimLaboratory, valakasReady, valakasRaid 메서드를 추출하여 Java 21 typed stub에서 1,093개 assertion 통과. 실제 SQL·실서버 테스트는 아니다. 테스트 스크립트는 이전 ZIP을 환경변수 RKERN_PREVIOUS_ZIP으로 지정할 수 있다.
- 전체 Java 1,927개 구문 오류 0. Java 21 --release 21 --enable-preview -proc:none -encoding UTF-8 -Xmaxerrs 10000 타입 컴파일은 기존 의존성 등의 531 오류/5 경고로 실패. 직전 N7 동일 옵션 진단 로그와 byte-identical 확인. 전체 성공 빌드가 아니다.
- 이전 테스트 일부는 삭제된 보조 클래스 소스 위치를 직접 참조하므로 이번에 재실행하지 않았다. 신규 테스트가 해당 라우팅 변경을 검증한다. 실제 MySQL·인게임·JFR·Heap 미검증.

## 다음 작업
훈련소 예약과 열쇠의 안전한 수명·영속 경계, 오림 파티 다중 소유권/차감, 불골렘 방어구 바닥 지급과 인벤토리 차감의 안전한 결합, XML 복합 제작, HtmlCmdHandler 잔여 분기, TalkChannelCache. 원자화 불가능한 경로를 완료로 표기하지 않는다.
