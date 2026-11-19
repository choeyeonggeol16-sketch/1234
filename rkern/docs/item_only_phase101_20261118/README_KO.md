# RKern 아이템 101차 — 카드 합성 영속 거래 원자성

기준 소스: `RKern_Item_1to100_RegressionAudit_FIX_FULL_SRC_20261118.zip`

## 실제 변경 파일

- `src/l1j/server/server/model/item/Card/L1CardAlchemy1.java`
- `src/l1j/server/server/model/item/Card/L1CardAlchemy2.java`
- `src/l1j/server/server/model/item/Card/L1CardAlchemy3.java`
- `src/l1j/server/server/model/item/Card/CardAlchemyReceipt.java` (신규, 세 합성 공용 거래 책임)

기존 3개 합성 진입 메서드는 그대로 유지합니다. 일반 성공 시 RNG/확률 선택, 보상 ID, 기존 카드 수량, 안내/이펙트 순서는 유지합니다. 단일 `L1InventoryTransaction` receipt에서 아데나 차감, 계정 카드 수량 SQL 변경(원본 값 CAS), 결과 카드 아이템 지급을 함께 확정하도록 바꿨습니다. 계정 메모리 변경과 성공 이펙트는 `CDB.RequestUseItem`의 커밋 후 콜백에서 실행합니다.

계정 SQL은 `accounts` InnoDB 여부를 검사합니다. 카드 선택 정보 `info_card_a/b/c`(4장 합성은 `d`까지)도 같은 거래에서 초기화합니다. 기존 코드의 카드 선택 UI는 `d`값을 DB에 보장해서 저장하지 않았으므로, DB 선택 칼럼을 CAS 조건으로 사용하지 않습니다. 대신 실제 카드 보유 수량 필드를 SQL 조건으로 검사합니다. 계정 메모리 수량이 콜백 전에 바뀌면 이미 커밋된 거래를 임의 재시도하지 않고 기존 receipt 격리 정책을 따릅니다.

## 검증

- 실제 생산 Java 4개를 Java 21 타입 스텁과 함께 컴파일: 통과
- 100차 원본과 새 코드를 동일한 RNG seed로 실행한 **36,000개 합성 시나리오**: 반환값, 보상 아이템, 카드 수량, 버프 플래그, 알림·이펙트 시퀀스 해시 동일 (정상 승인 26,181건 포함)
- **25,281개 거래 모델 검증 조건**: 기본 합성, SQL 프리이미지 변경, SQL 예외, 아데나/보상 준비 실패, 커밋 전 메모리 미변경, 성공 후 한 번 반영
- 81개 계정 카드 SQL 컬럼 매핑이 실제 `Account.java`에 존재하고 중복이 없는지 정적 검증
- 전체 Java 21 **1,923개 소스 구문 검사, 오류 0건**
- 기존 NPC·네트워크·방송·CacheD 포함 미변경 파일 바이트 비교 및 ZIP CRC 통과

재실행 명령(전체 ZIP 압축 해제 폴더):

```
python3 docs/item_only_phase101_20261118/test_101.py
python3 docs/item_only_phase101_20261118/run_ab101.py
```

JDK 21의 `javac`와 `java` 필요. 테스트는 `fixture_work` 디렉터리에 컴파일용 스텁을 생성하며 생산 SRC를 수정하지 않습니다.

## 제한

**실제 MySQL/CacheD 장애 주입, 운영 게임 서버 전체 의존성 컴파일, 동접 부하 테스트는 수행하지 않았습니다.** 해당 검증 없이 무결성 문제가 전부 종결됐다고 볼 수 없습니다. 테스트의 SQL 연결은 `PreparedStatement`의 인자·리턴을 재현한 Java 테스트 모델입니다. 기존 `Account`의 다른 독립 SQL 저장 루틴과 경합하는 실DB 환경도 별도 검증이 필요합니다. 차수 숫자와 무관하게 확인되지 않은 문제는 다음 단계에서 다룹니다.
