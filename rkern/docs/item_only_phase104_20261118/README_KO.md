# RKern 아이템 104차 — 카드 확인 응답 우회 경로 통합

## 기준
`RKern_ItemOnly_CardCollection_Atomic_Phase103_FULL_SRC_20261118.zip` 원본을 그대로 유지하고,
생산 코드 `src/l1j/server/server/model/network/core/cs/packet/CS_Attr.java` **단 한 파일**만 수정했습니다.
NPC, CIO/READ/SEND, 방송, DB 공통 처리기, Account, 기존 카드 구현은 수정하지 않았습니다.

## 변경
`MSGCODE_622_Card2`(전설 9종), `Card3`(신화 10종), `Card`(영웅 16종),
`Tree`(수호성 11종)의 옛 카드 처리 경로에서:

- 아이템 직접 소모(`getInventory().removeItem`) 제거
- 계정 수량·점수·트리 메모리 선변경 및 비트랜잭션 `updateLegend/Myth/Hero/Tree` 제거
- 각각 103차 `L1CardAdd.legend1`, `myth`, `hero1`, `SkillTree`로 위임
- `c==MSGCODE_YES`에서만 요청 처리; 그 외 모든 응답은 선택된 아이템 참조만 해제
- 카드 ID/등급별 화이트리스트, 수호성 중복 차단, 계정 SQL preimage 검증,
  아이템 1개 차감의 CacheD receipt, 성공 후 CIO-owner 효과는 103차 처리기를 재사용

이는 확인 패킷 전용 카드 분기 통합이며 네트워크 프레이밍·해석·직렬화 로직은 변경하지 않았습니다.

## 재현 가능한 검증
전체 ZIP을 압축 해제 후 루트에서:

```
python3 docs/item_only_phase104_20261118/test_104.py
```

테스트는 103차의 **실제 생산 `L1CardAdd` Java 타입 컴파일** 및 카드 ID 70종,
수호성 ID 14종, 유일 카드의 기존 2,498개 검사도 재실행합니다.
`CS_Attr.java`에서 추출한 **수정된 실제 4개 switch 분기**만 동일 Java 코드로
별도 컴파일한 뒤, 65개 assertion으로 YES/취소/잘못된 응답 값/타 등급 선택/중복/DB 실패를 검사합니다.
`CS_Attr` 전체 파일도 Java 21 파싱 검사 대상으로 검증했습니다.

## 검증 범위의 한계
실제 MySQL·CacheD 동시성, 운영 ACK 손실과 재접속, Windows IOCP, 게임 내
확인 창·UI 패킷 통합 검증, 전체 외부 의존성을 포함한 타입 컴파일, 5,000명 부하
검증은 수행하지 않았습니다. `CS_Attr` 전체 클래스는 방대한 의존성 때문에
이번에는 파싱 검증 + 실제 분기 추출 타입 컴파일로 확인했으며, 서버 전체 타입
컴파일 통과라고 주장하지 않습니다.

기존 103차 보고서의 *커밋 이후 계정 메모리 preimage가 다른 경로에서 변경되는 경우*
운영 재동기화 한계는 여기서 해결되지 않습니다. 이 건은 공통 Account/CacheD
검증 없이는 변경할 수 없으며, 별도로 운영 통합 검증이 필요합니다.
