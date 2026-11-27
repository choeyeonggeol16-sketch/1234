# 유저 간 교환과 개인상점 검증 · 2026-11-27

기준 소스는 누적 서버의 `d205e100`이다. 직접 교환은 기존 `CTrade`의 양측 확인과 CacheD 정산을, 개인상점은 기존 주문 목록과 아이템·아데나·잔여 주문 수량을 함께 기록하는 DB 트랜잭션을 사용한다.

## 대조와 변경

- **직접 교환.** LU4의 `CTrade::PressDone`은 양쪽 확인을 모으고 DB에 거래 확정을 요청한다. RKern에서는 기존 초대, 제안, 확인과 정산 직전 검사를 보강했다. 양쪽의 로그인 준비가 끝나고 같은 실제 맵 객체에 있어야 하며 기존 1칸 거리 조건을 적용한다. 5분 비활동 타이머는 거래 취소나 정산 시작 시 기존 CIO 타이머에서 즉시 회수한다. 활동 중인 거래는 남은 시간으로 다시 예약한다. [LU4 Trade.cpp](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Trade.cpp#L1067) · [RKern CTrade](../../src/l1j/server/server/model/inventory/CTrade.java)
- **개인상점.** LU4는 판매·매입 주문을 구분해 물품, 거래 상대, 총 대금을 DB에 넘긴다. RKern 개인상점은 손님에게 송신한 목록의 행 번호를 실제 판매 물품 또는 매입 주문과 연결한다. 매입 주문 하나에 맞는 손님 아이템이 여러 개면 각각 표시한다. 손님이 같은 묶음에서 가격이 다른 두 주문을 채우면 주문마다 표시 가격과 주문 잔량을 확인하고, 물품은 합산 수량만큼 한 번 이동한다. 목록은 실제 인코딩 크기와 최대 255개 행을 모두 만족하는 범위까지만 전송한다. 서버가 보관하는 행은 전송에 성공한 목록과 일치한다. 부분 거래와 기존 12칸 거리 규칙을 유지한다. [LU4 판매 정산](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/User.cpp#L9701) · [LU4 매입 정산](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/User.cpp#L10188) · [RKern 개인상점 정산](../../src/l1j/server/server/model/network/core/cs/packet/PlayerShopTransactions.java)
- **세션과 거래 정책.** 목록을 받은 캐릭터의 세션, 상점의 월드 객체와 개점 버전, 주문 방향, 실제 맵을 다시 확인한다. 취소는 일치하는 목록에만 적용한다. 운영자의 `notradable` 설정을 등록과 정산에서 확인하며 기존 GM 예외와 아이템 자체 거래 제한을 구별한다. 목록을 다시 읽다가 DB 오류가 나면 직전 정상 설정을 유지한다. [목록 식별](../../src/l1j/server/server/model/shop/PlayerShopView.java) · [거래 제한 목록](../../src/l1j/server/server/datatables/ItemRestrictionTable.java)

## 검증 결과

| 시험 | 확인한 결과 |
|---|---|
| Java 21 | 운영 소스 1,694개 컴파일 통과 |
| 직접 교환 · 실제 TCP 2개와 MariaDB | 교환 제안 후 한쪽만 확인하고 취소했을 때 양쪽의 아이템 수량이 보존됐다. 순차 확인과 동시 확인은 각각 거래당 영수증 한 건만 저장했고 메모리·DB 수량이 일치했다. 동일한 숫자 ID를 가진 다른 맵 객체로 이동한 경우에는 정산되지 않았다. |
| 개인상점 · 실제 TCP 3개와 MariaDB | 부분 구매, 한 묶음으로 가격이 다른 두 매입 주문 채우기, 나머지 주문 소진을 확인했다. 결제 대기 중 다른 구매자와 상점 닫기를 겹쳤을 때 한 번만 차감됐고 완료 후 닫기 재시도에 성공했다. |
| 개인상점 · 저장과 재로딩 | 네 건의 거래 영수증이 기록됐다. 세 캐릭터의 인벤토리를 DB에서 다시 읽어 아데나 합계 1,400, 두 상품 수량 합계 각 10개·18개를 확인했다. 화면 표시 후 운영자 제한을 추가한 건은 아이템, 잔액, 주문 수량이 유지됐다. |
| 상점 목록 패킷 | Java 인코더에서 짧은 설명 255행은 7,285바이트, 350바이트 설명 174행은 65,488바이트였다. 다음 행이 65,535바이트 한도를 넘는 경우 표시 목록과 서버의 행 번호가 함께 잘리는지 확인했다. |
| 타이머와 목록 회귀 | 종료·취소 시 타이머 회수, 취소와 재예약의 동시 실행, 오래된 화면·무인상점 세션 정리, 정산 실패 시 수량 보존, 처리 중에 계정 식별 정보가 없는 상점 요청의 정산 거절을 확인했다. |

실제 운영 데이터와 게임 클라이언트는 사용하지 않았다. 서버가 승인한 테스트 세션과 임시 MariaDB 스키마를 사용했다. 직접 교환 시험은 암호화된 응답을 두 연결에서 읽었고, 개인상점 시험은 각 세션의 CIO 소유 스레드에서 운영 패킷 핸들러를 호출했다. 부하 성능과 장시간 메모리 증가율은 이번 검증 범위 밖이다.

### 재현

Java 21에서 서버 클래스와 의존 JAR를 준비하고, `rkern/tests/network/CioSyntheticPopulation.java`, `rkern/docs/jproring_selector_transport_20261120/SelectorTransportTest.java`, 아래 두 통합 테스트를 같은 테스트 출력 경로에 컴파일한다. 실행 클래스패스에 `rkern/src`를 포함해 아이템 표기 스크립트를 읽을 수 있게 한다. 각 테스트를 별도의 빈 작업 디렉터리에서 실행한다. 세 인수는 테스트용 JDBC URL, DB 사용자, 참조 스키마 이름이다. 비밀번호는 `RKERN_STORAGE_TEST_PASSWORD` 환경변수에서 읽는다. 각 테스트가 참조 스키마의 테이블 정의를 별도의 임시 스키마로 복사하고 종료 시 임시 스키마를 삭제한다.

[직접 교환 통합 테스트](../../tests/trade_lifecycle/DirectTradeIntegrationRegression.java) · [개인상점 통합 테스트](../../tests/trade_lifecycle/PrivateShopSettlementRegression.java) · [거래 수명주기와 화면 회귀 테스트](../../tests/trade_lifecycle)

## 후속 작업 · 캐릭터가 소유하는 개인상점의 개점과 폐점

LU4에서는 플레이어 `User`가 판매·매입·도매 컨테이너를 멤버로 갖는다. 주문을 새로 등록할 때 기존 항목을 지우고 새 항목을 등록한다. 상점 동작은 캐릭터 컨트롤러의 직렬화 경로에서 전환한다. RKern에서는 리니지 1의 판매·매입 동시 등록과 기존 CIO·인벤토리 실행 경로를 유지했다. [LU4 User.h](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/User.h#L3674) · [판매 목록 교체](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/User.cpp#L8889) · [매입 목록 교체](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/User.cpp#L9148) · [컨트롤러](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/CreatureController.cpp#L2210)

- **개점.** 일반 패킷과 ActionUI가 입력을 검증하고 캐릭터의 같은 개점 메서드를 호출한다. 캐릭터에서 처음 개점할 때만 `PlayerPrivateStore`를 생성한다. CacheD가 저장 작업을 접수하면 판매·매입 목록, 간판 문구, 개점 번호를 한 번에 공개한다. 접수가 거절되면 직전 상태가 유지된다. GM이 만든 광고용 캐릭터는 상품이나 DB 작업 없이 간판만 게시한다.
- **폐점·재개점.** 폐점 시 저장 목록 삭제가 접수되면 해당 개점의 주문과 문구 참조를 놓고, 두 번째 폐점 요청은 추가 삭제를 접수하지 않는다. 다음 개점은 같은 `PlayerPrivateStore`에 새로운 주문 목록과 개점 번호를 게시한다. 손님이 예전에 보았던 가격 또는 이전 개점에 예약된 10초 타이머가 새 상점을 변경할 수 없다. 월드의 캐릭터 표시와 앱센터의 판매·매입 목록도 같은 개점 상태를 읽는다.
- **무인상점.** 상인 연결이 무인상점 모드로 분리돼도 월드 캐릭터와 주문은 유지된다. 손님과 진행 중인 정산이 있는 무인상점의 GM 추방은 거래가 끝난 뒤 다시 요청할 수 있다. [상점 상태](../../src/l1j/server/server/model/shop/PlayerPrivateStore.java) · [캐릭터 개점·폐점 메서드](../../src/l1j/server/server/model/Instance/L1PcInstance.java)

후속 Java 21 빌드는 운영 소스 1,695개를 컴파일했다. 격리 MariaDB와 실제 TCP 연결 3개로 개점·중복 폐점·새 가격 재개점·매입 전용 재등록·무인 전환 후 매입·폐점을 확인했다. 이전 7아데나 견적은 거절됐고 새 11아데나 견적으로 한 번만 거래됐다. 이 확장 시나리오의 영수증은 6건이었다. 세 캐릭터 인벤토리를 다시 읽은 뒤에도 아데나 1,400개와 상품 10개·18개가 보존됐다. 인증은 합성 세션을 사용했으며 실제 게임 클라이언트 화면과 전체 재로그인은 이 통합 시험의 범위 밖이다. [통합 검증](../../tests/trade_lifecycle/PrivateShopSettlementRegression.java) · [개점 회차와 지연 타이머 검증](../../tests/trade_lifecycle/test_private_store_lifecycle.py) · [무인상점 정리 검증](../../tests/trade_lifecycle/test_private_store_retirement.py)
