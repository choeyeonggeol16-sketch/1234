# RKern/L1J 110차 — 카드 합성 커밋 후 출력 실패 격리 분리

기준: `RKern_ItemOnly_CardGate_Preimage_Phase109_FULL_SRC_20261118.zip` (109차 최신 전체 SRC).

## 생산 변경: 아이템 패키지 Java 4개

1. `src/l1j/server/server/model/item/Card/CardAlchemyReceipt.java`
   - DB 확정 + 인벤토리 반영 + 계정 카드 차감/선택 초기화가 끝난 후, *출력/연출 전용* `afterCommit.run()`의 `RuntimeException`을 `WARNING` 로그로 기록하고 외부로 전파하지 않도록 변경.
   - 확정 거래를 패킷·이펙트·공지·UI 실패만으로 거래 격리(quarantine)하지 않음.
   - **중요:** 세션 교체 및 계정 수량 preimage 불일치, 카드 debit callback 실패 등 실제 경제 상태 위험은 기존처럼 그대로 예외로 전파해 격리 보호 유지. `Error`는 삼키지 않음.
2. `L1CardAlchemy1.java` — 전설 합성의 확률 증가 플래그 해제를 커밋 후 처리 첫 구문으로 이동. 패킷/공지 예외가 나도 확정된 보너스가 재사용되지 않게 함.
3. `L1CardAlchemy2.java` — 영웅 합성에 동일 적용.
4. `L1CardAlchemy3.java` — 신화 합성에서 GM 확률 메시지보다 먼저 확률 증가 플래그 해제.

**의도된 범위:** 위 afterCommit 호출자는 카드 합성 3종뿐이며, 이 Runnable은 확정 경제 기록을 새로 변경하지 않고 확률 증가 플래그 해제·결과 연출·UI·공지 등을 수행한다. 반환값/보상/확률/RNG/SQL CAS/DB 트랜잭션/선택 검증/인벤토리 소유권 구조는 변경하지 않았다. NPC·방송 코어·일반 네트워크·CIO·CacheD 코어는 변경하지 않았다.

## 실행 검증

- `test_110.py`는 **변경된 실제 생산 4개 Java 소스**와 typed Java 21 test doubles를 컴파일/실행: **25,335 checks** 통과. 커밋 이후 이펙트 송신, GM 메시지, 전역 공지 실패 시 카드 차감/보상/플래그를 검증. DB 롤백이면 플래그를 보존하고, 경제 메모리 preimage 충돌은 여전히 예외로 전파함을 검증.
- `regress_109.log`: 생산 `CardAlchemyReceipt`·세 단계 합성·`L1CardAdd` typed Java 21 컴파일, 합성 25,353 조건, 등록 2,464 + 유일 등록 34 + 선택 경합 546 검사 통과.
- `regress_108.log`: 합성 UI-only 경합 및 경제 충돌, **25,371 checks** 통과.
- `regress_106.log`: 카드 자동 선택 **16,019 checks**, 105차 **410 조건**·중앙 2곳, 104차 **65 assertions**, 103차 **2,464 + 34 assertions** 통과.
- `parse_all_java21.log`: 전체 production Java **1,923개** Java 21 구문 검사, 오류 0.
- 최종 ZIP CRC, 109차 대비 unchanged 파일 바이트 동일성, 변경 생산 파일 정확히 4개 확인.

## 검증 한계

Stub 기반 타입·동작 검사로 실제 MySQL/CacheD ACK 손실, 디스크 커밋 확인, 멀티 세션, 재접속, 인게임 복제/유실 없음까지 확인한 것은 아니다. 카드 수량 자체가 DB와 메모리에서 충돌하는 경우의 자동 재동기화는 **구현하지 않았으며** 기존 격리 정책을 유지한다. 전체 서버 외부 의존성 타입 컴파일·5000명 부하 역시 미실시.
