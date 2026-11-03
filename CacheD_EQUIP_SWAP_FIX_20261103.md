# CacheD 전송 대기 중 장비 슬롯 교체 오류 수정

기준: 사용자가 첨부한 NPC_AI_Passing_V6_AlternateEntrance_FULL_SRC_FIX.zip
기준 SHA-256: 1b382beb06af70d67ed4f5faa1d5124159b758367d3f945c4971ff5e56be4de1

증상: CS_Craft의 장비 슬롯 교체가 장착 상태·능력치·캐시를 변경한 다음 updateItem에서
`Item mutation refused while CacheD transfer pending` 예외를 던졌습니다.
클라이언트에는 실제 교체보다 먼저 성공 슬롯 패킷을 보냈습니다.

수정 내용:
- 기존 아이템 전송 CAS 게이트를 장비 교체 시작 전에 예약합니다. 대기나 DB 동기 호출은 없습니다.
- 이미 전송 중이면 장비·능력치·슬롯 번호·저장 프리셋을 변경하지 않고 재시도 안내만 보냅니다.
- 전송 완료 후 사용자가 다시 요청하면 정상 교체합니다. 자동 재실행 큐는 추가하지 않습니다.
- 슬롯 교체 전체 동안 예약을 유지해, 중간에 다른 스레드가 전송을 시작하지 못하게 합니다.
- 같은 스레드의 슬롯 교체→개별 장착 호출만 예약을 중첩 사용하며 finally에서 반환합니다.
- 일반 스레드의 변경 차단과 기존 CacheD 커밋 후 재생 예외는 유지합니다.
- 성공 슬롯 패킷은 교체가 완료된 뒤에 보냅니다. 거래 중 슬롯 교체도 거절합니다.
- NPC AI 소스와 DB 스키마, 의존성은 변경하지 않았습니다.

적용:
1. 서버를 종료하고 기존 소스와 배포 JAR를 백업합니다.
2. PATCH ZIP의 아래 4개 Java 파일을 모두 같은 경로에 교체하거나, FULL_SRC ZIP의 src를 사용합니다.
   - src/l1j/server/server/model/L1Inventory.java
   - src/l1j/server/server/model/L1PcInventory.java
   - src/l1j/server/server/model/character/slot/SlotChangeManager.java
   - src/l1j/server/server/model/network/core/cs/packet/CS_Craft.java
3. 기존 lib, config, 게임 데이터와 DB를 보존하고 JDK 21 / UTF-8로 Clean/Rebuild합니다.
4. 검증 서버에서 창고 이동·제작 등의 처리 중 슬롯 교체가 거절되고,
   완료 후 재요청 시 장비·능력치·선택 슬롯이 일치하는지 확인합니다.

검증 결과:
- 원본 소스: 동일 예외와 일부 상태 변경 재현. 공통 12개 시나리오 중 9개 실패 / 3개 통과.
- 수정 소스: 장비 슬롯 회귀 14개 시나리오 통과. 실제 수정 메서드와 전체 SlotChangeManager를 컴파일해 실행했습니다.
- 전송 대기·직접 장착·프리셋 보존·재시도·다른 스레드의 전송/변경 차단·정상 재생·예외 시 반환을 검사했습니다.
- 기존 NPC 남쪽→북쪽 우회 경로, AI 큐 포화/동시 시야 갱신, 자동사냥 진입 회귀 통과.
- Java 소스 1,701개 구문 분석 오류 0. 원본 Java 중 변경은 위 4개뿐입니다.
- 아이템·능력치·패킷 의존성은 테스트 대체 객체를 사용했습니다. 실제 DB/클라이언트 통합 검증이 아닙니다.
- 첨부에는 게임 의존 JAR, 런타임 설정/데이터와 DB가 없어 전체 서버 타입 컴파일·기동은 미검증입니다.

회귀 검사: 프로젝트 루트에서 `python3 tests/equipment_transfer/test_equipment_transfer.py`
Java 21 및 javac가 필요합니다. 원본 비교는 `--baseline-dir`에 원본 4개 Java 파일을 둔 폴더를 지정합니다.
검사 코드와 실제 실행 출력은 tests/equipment_transfer 및 validation에 포함했습니다.

클라우드의 기본 Java 21에는 컴파일러 모듈은 있지만 javac 실행 파일과 ct.sym이 없습니다.
/workspace/java-toolchain/bin/javac 보조 실행기를 준비했습니다. Java 21 대상에 한해
-source 21 -target 21로 설치된 Java 21 API를 사용합니다. 완전한 JDK 설치를 의미하지 않습니다.
