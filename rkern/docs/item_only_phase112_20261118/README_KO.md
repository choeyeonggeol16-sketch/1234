# RKern/L1J 아이템 112차 — 카드 합성 확률 포션 커밋 이후 패킷 장애 안전성

기준: `RKern_ItemOnly_CardRegistration_PostCommitSafety_Phase111_FULL_SRC_20261118.zip`.

## 변경 범위

생산 Java 변경은 **`src/l1j/server/server/model/item/function/CardFusionChanceItems.java` 한 파일**이다.

- 영웅 합성 확률 증가 포션 `5200101`, 전설 `5200102`, 신화 `5200103`: 기존 `UseDurableConsume` 영속 아이템 소모 경로 유지.
- 성공 콜백 진입 후 원래의 확률 증가 플래그를 **먼저 적용**한다. 성공 알림 `SC_SystemMessage`와 `SC_PacketBox`는 각각 독립적으로 `RuntimeException`을 로깅·격리하여, 이미 확정된 경제 영수증에 UI 실패가 전달되지 않도록 한다.
- 두 성공 메시지의 기존 문자열 및 GREEN_MESSAGE 패킷 유형 유지. 이미 활성화된 포션 재사용 방지 유지.
- 사용하지 않는 지역 배열 할당과 해당 파일의 불필요한 import 정리. 새 작업 큐/스레드/클래스/트랜잭션 경로 없음.
- NPC, 방송, 네트워크 송수신 코어, CIO, CacheD 및 일반 거래 공통 처리기 무수정.

## 검증

- `test_112.py`: **변경한 생산 Java를 그대로 Java 21 타입 컴파일**, 가짜 영속 receipt, 패킷 실패 주입. 3종 × 성공/메시지1 실패/메시지2 실패/동시 실패 × 64회, 롤백/중복 사용/알 수 없는 ID 검사. 합계 **6,925개 assertion 통과**.
- `test_111.py`: 실제 생산 카드 등록·보너스·기르타스 컴파일 검사 및 후처리 장애 검사 통과(118 + 24조건).
- `test_110.py`: 생산 합성 클래스 및 콜백 검사 25,335개 통과.
- `test_109.py`: 기존 카드 계정 gate 검증 통과(컬렉션·수호성 경합 546조건 등).
- `test_108.py`: 생산 합성 검사 25,371개 통과.
- Java 21 전체 production 파일 **1,923개 구문 파싱, 오류 0**.
- ZIP 원본 비교 및 CRC 검증 결과는 `VERIFY_LOG.txt`에 기록.

**제한 사항:** 테스트는 Java 타입 스텁으로 작성한 모의 receipt/패킷 경로이며, 실제 MySQL 커밋, CIO 콜백, 네트워크 소켓, 재접속·인게임, 전체 프로젝트 의존성 컴파일은 검증하지 않았다. 카드 수량의 DB/계정 객체 충돌을 자동 해결하는 기능은 이 차수에서 만들지 않았으며 기존 격리 정책을 유지한다.
