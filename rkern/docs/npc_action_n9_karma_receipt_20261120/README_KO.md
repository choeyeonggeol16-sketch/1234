# N9 카르마 결정 교환 검수

입력은 `RKern_NPC_Action_N8_Coma_Gift_Receipt_Audit_FULL_SRC_20261120.zip`이다. SHA-256은 `07504b5ab24e4867bf0213de8087f42f2615923030fb5cfb029ba6bf992a15cc`이며 Git 기준 커밋의 3,334개 파일을 압축 원본과 바이트 단위로 대조했다.

피의 결정과 영혼의 결정 교환은 인벤토리 지급을 시도하기 전에 카르마를 변경했다. 지급이 실패해도 완료 메시지가 나올 수 있었다. 기존 `L1InventoryTransaction`으로 결정 지급을 준비하고, 지급 커밋과 인벤토리 반영이 끝난 뒤 CIO 소유 스레드에서 카르마와 완료 메시지를 적용한다. 명령별 수량, 카르마 한도, 보상 및 메시지 ID를 유지한다.

JDK 21에서 전체 생산 Java 1,928개를 타입 컴파일했다. 원본과 수정본은 오류 0개 및 동일한 기존 경고 5개였다. 전체 의존성 선언이 들어 있지 않아 Maven Central의 HikariCP 5.1.0, SLF4J 2.0.13, JCTools 4.0.5, Javolution 5.5.1, Trove4j 3.0.3, Agrona 1.23.1, Fastutil 8.5.15, JAXB API 2.3.1, Activation API 1.2.0, Caffeine 3.1.8을 임시 클래스패스로 사용했다.

`python3 docs/npc_action_n9_karma_receipt_20261120/test_karma_receipt.py`는 생산 소스의 교환 메서드를 꺼내 제어 가능한 거래 구현으로 실행한다. N8 원본은 지급 확정 전 상태가 변경되는 첫 검사에서 실패했고, 수정본은 154개 시나리오와 766개 검사를 통과했다. N8의 XML 제작, 여관, 달의 장궁, 코마 및 지급 모의 회귀도 다시 통과했다.

결정의 지급과 인벤토리 반영은 receipt를 사용한다. 카르마는 이후 기존 캐릭터 저장 경로를 사용하므로 DB 커밋부터 캐릭터 저장까지 서버가 중단되는 경우의 복구 정책은 미해결이다. 실제 MySQL·CacheD, 인게임, p99 및 메모리 실측은 후속 검증 범위다.
