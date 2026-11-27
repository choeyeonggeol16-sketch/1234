# RKern 제작 및 장비 교환 서버 검증 감사 — 3개 개선 과제

작성일: 2026-11-28 (KST)  
대상: RKern / Java 21  
기준 브랜치: `rkern/full-source-20261127`  
검토 커밋: `48c0e498626a70c2a75c1a593116f794612ee1f5` (Separate SPR action timing from runtime execution)  
상태: 정적 코드 검토 및 수정 설계. 실제 게임 환경 재현, DB 장애 테스트 및 코드 수정은 미실시.

## 요약

최근 SPR 변경이 포함된 최신 브랜치에서 세 문제의 관련 소스를 재조회했다. 일반 상점, 개인상점, 창고 및 거래의 기존 View/receipt 방어 구조를 활용하여 아래 세 영역만 개선한다.

| ID | 개선 과제 | 우선순위 |
|---|---|---|
| C01 | 제작 필수 재료 전체 검증 | P0 |
| C02 | 제작 NPC와 레시피의 서버 권한 검증 | P0 |
| C03 | 장비 교환권 처리의 소유권 검증 및 원자적 정산 | P0 |

우선순위는 경제 데이터 보호 관점의 정적 검토 결과이며, 실제 운영 환경에서 악용 또는 아이템 복제가 재현되었다는 뜻은 아니다. 이 문서는 방어 및 수정 계획만 다루며, 조작 패킷이나 재현용 공격 절차는 포함하지 않는다.

## C01. 제작 필수 재료 검증

### 확인된 문제

제작 요청을 검증할 때 서버 정의의 필수 재료 그룹 전체가 충족되어야 한다. 현재 `CraftInputList.calculateSlotAndIncreaseProb()`의 종료 조건과 `CS_CRAFT_MAKE_REQ.handleRequest()`의 최종 판정은 제출된 슬롯 수와 결합되어 있어, 모든 필수 그룹이 만족되었음을 독립적으로 보장하지 못한다.

기존 구현에는 서버 정의 수량의 재설정, 재료 속성 검사, 인벤토리 수량 재검증, 다중 제작의 단일 receipt 및 중복 재료 소비 방어가 있다. 이 보호는 유지한다. 문제의 핵심은 소비 트랜잭션 이전에 전체 레시피 충족 여부를 증명하는 절차다.

관련 소스:
- [CraftInputList.java](https://github.com/choeyeonggeol16-sketch/1234/blob/48c0e498626a70c2a75c1a593116f794612ee1f5/rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/Craft/CraftInputList.java)
- [CS_CRAFT_MAKE_REQ.java](https://github.com/choeyeonggeol16-sketch/1234/blob/48c0e498626a70c2a75c1a593116f794612ee1f5/rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/CS_CRAFT_MAKE_REQ.java)
- [CraftInputItemSlotInfo.java](https://github.com/choeyeonggeol16-sketch/1234/blob/48c0e498626a70c2a75c1a593116f794612ee1f5/rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/Craft/CraftInputItemSlotInfo.java)

### 수정 설계

1. 서버에 적재된 레시피에서 필수 슬롯 그룹과 같은 슬롯 내 대체 재료의 의미를 확인한다. 그룹 개수는 클라이언트 요청 수와 독립적으로 결정한다.
2. 모든 필수 그룹의 충족 여부, 슬롯 번호 및 개수, 허용 대체 재료, 중복 선택과 미정의 슬롯을 검사한다. 선택 재료는 실제 레시피 정의의 의미에 맞게 별도 판단한다.
3. 소비 수량과 확률 보너스는 서버 정의로 확정한다. 강화·축복·속성 계승 값은 실제 소비할 인벤토리 아이템에서 확정하고, 요청 값은 선택 조건으로만 사용한다. 기존 와일드카드와 룸티스 예외 등 정상 정책은 보존한다.
4. 전체 제작 횟수, 재료별 누적 소비, 결과 수량 및 인벤토리 용량을 기존 L1InventoryTransaction 경계 안에서 검증한다. 기존 다중 제작 원자성, 실패 처리, 확률 정책을 유지한다.
5. 모든 검증이 완료되기 전에는 재료 차감, 아이템 지급, 성공 응답 또는 효과가 발생하지 않도록 한다.

### 회귀 검증

정상 단일/복수 필수 그룹, 대체 재료, 선택 재료, 잘못된 슬롯, 누락·중복·초과 입력, 재료 부족, 착용 중인 재료, 강화/축복/속성 불일치, 다중 제작의 수량 경계 및 DB 롤백을 테스트한다. 실패 시 재료·보상·재화 및 성공 응답의 변화가 없어야 한다. 정상 레시피 결과와 확률 보너스가 유지되어야 한다.

## C02. 제작 NPC 및 레시피 권한 검증

### 확인된 문제

`CS_CRAFT_MAKE_REQ`는 NPC의 존재, 타입, 동일 맵, 거리, 제작 ID 존재 및 재료를 검사한다. 다만 검토한 실행 경로에서는 NPC별 제공 레시피 목록과 실제 제작 요청 간의 서버 권한 검증을 확인하지 못했다. Craft 객체에 존재하는 직업·퀘스트·스프라이트·요구 아이템 제한 역시 해당 실행 경로에서 일관되게 강제되는지 감사가 필요하다.

`SC_CRAFT_LIST_ALL_ACK`의 NPC별 제작 목록은 이미 존재한다. 이 목록을 단순한 화면 표시 데이터에 그치지 않고, 서버 실행 권한과 일치시키는 것이 목표다.

관련 소스:
- [CS_CRAFT_MAKE_REQ.java](https://github.com/choeyeonggeol16-sketch/1234/blob/48c0e498626a70c2a75c1a593116f794612ee1f5/rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/CS_CRAFT_MAKE_REQ.java)
- [CS_CRAFT_ID_LIST_REQ.java](https://github.com/choeyeonggeol16-sketch/1234/blob/48c0e498626a70c2a75c1a593116f794612ee1f5/rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/CS_CRAFT_ID_LIST_REQ.java)
- [SC_CRAFT_LIST_ALL_ACK.java](https://github.com/choeyeonggeol16-sketch/1234/blob/48c0e498626a70c2a75c1a593116f794612ee1f5/rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/SC_CRAFT_LIST_ALL_ACK.java)
- [Craft.java](https://github.com/choeyeonggeol16-sketch/1234/blob/48c0e498626a70c2a75c1a593116f794612ee1f5/rkern/src/l1j/server/server/packets/Protocol/MainServer/Proto/Craft/Craft.java)

### 수정 설계

1. NPC별 레시피 설정을 로딩할 때 전송용 ACK와 서버 검증용 데이터를 같은 기준으로 구축한다. 기존 NPC ID와 레시피 ID의 관계를 재사용하고, 요청마다 DB 조회나 문자열 파싱을 하지 않는다.
2. 제작 시작 전에 현재 게임 세션, 캐릭터 상태, NPC의 유효한 런타임 식별, 맵 및 거리, 해당 NPC의 레시피 제공 권한을 검증한다. 정상적인 특수 제작 UI가 있는지 먼저 확인하고 기존 정책을 보존한다.
3. 직업, 퀘스트, 스프라이트, 요구 아이템, 이벤트/시간 제한 등 실제 사용 중인 레시피 속성의 의미를 확인하여 적용한다. 미사용 필드나 추정 조건을 임의로 활성화하지 않는다.
4. 제작 목록 reload와 NPC 재사용, 세션 종료 및 비동기 정산 사이의 경합을 확인한다. 기존 소유 실행 구조와 receipt를 활용하여 필요한 시점에 권한을 재검증한다.
5. 배치 요청 ACK가 실제 제작 허가를 의미하는지 검토한다. 단순 UI 응답과 경제 트랜잭션의 승인 경계를 혼동하지 않는다.

### 회귀 검증

정상 제작 NPC, 미등록 레시피, 허용되지 않은 NPC, 거리·맵·세션 불일치, NPC 재생성, 목록 변경, 실제 활성화된 직업/퀘스트/아이템 제한, 정상 특수 제작 UI, 중복 요청 및 제작 중 접속 종료를 테스트한다. 거부된 요청에서 DB 변경과 성공 응답이 없어야 한다.

## C03. 장비 교환권 처리의 원자성

### 확인된 문제

`CS_Result.processChangeItems()`는 캐릭터의 기존 교환 상태를 이용하는 장비 교환 경로다. 일반 장비 프리셋 스왑과 별개다. 교환 시 강화·축복·속성 강화·아이템 레벨 등의 계승을 처리한다.

현재 확인된 경로에서는 새 장비 지급과 교환 재료 및 기존 장비 삭제가 하나의 receipt 안에서 정산되지 않는다. 기존 장비의 정확한 인벤토리 인스턴스 소유권과 유효한 교환 UI 세션에 대한 재검증도 강화해야 한다. 구체적인 교환권 표시 이름은 이번에 DB에서 확인하지 않았다.

별도 `LegacyEquipmentExchangeReceipt`에 구현된 원자적 교환 경로는 이미 debit/grant 및 커밋 후 처리를 사용하므로, 동일한 작업을 중복 구현하거나 해당 정상 경로를 되돌리지 않는다.

관련 소스:
- [CS_Result.java](https://github.com/choeyeonggeol16-sketch/1234/blob/48c0e498626a70c2a75c1a593116f794612ee1f5/rkern/src/l1j/server/server/model/network/core/cs/packet/CS_Result.java)
- [LegacyEquipmentExchangeReceipt.java](https://github.com/choeyeonggeol16-sketch/1234/blob/48c0e498626a70c2a75c1a593116f794612ee1f5/rkern/src/l1j/server/server/model/item/function/LegacyEquipmentExchangeReceipt.java)
- [L1InventoryTransaction.java](https://github.com/choeyeonggeol16-sketch/1234/blob/48c0e498626a70c2a75c1a593116f794612ee1f5/rkern/src/l1j/server/server/model/inventory/L1InventoryTransaction.java)
- [NpcShopView.java](https://github.com/choeyeonggeol16-sketch/1234/blob/48c0e498626a70c2a75c1a593116f794612ee1f5/rkern/src/l1j/server/server/model/shop/NpcShopView.java)

### 수정 설계

1. 기존 교환 UI 개설부와 종료부, 교환권 사용 및 캐릭터 상태 설정 호출을 전수 추적한다. 기존 교환 종류와 재료 매핑, 교환 가능한 장비 목록 및 계승 정책은 보존한다.
2. 서버가 열린 교환 UI의 소유 세션, 교환 유형, 대상 장비 객체 ID와 허용 결과 목록을 관리하도록 한다. 기존 NpcShopView 또는 동등한 소유 구조를 재사용할 수 있는지 검토한다.
3. 정산 시점에 원본 장비의 정확한 객체/소유권, 현재 수량, 착용 여부, 교환 제한 및 재료 보유량을 재확인한다. 원본 아이템의 현재 서버 상태에서 계승 속성을 결정한다.
4. 기존 L1InventoryTransaction의 단일 receipt로 원본 장비 차감, 교환 재료 차감 및 결과 장비 지급을 원자적으로 준비·커밋한다. 개별 지급/삭제 및 사후 보상 방식은 사용하지 않는다.
5. 성공 응답, UI 종료 및 로그는 확정된 커밋과 메모리 반영 후 처리한다. DB ACK 유실·불확정 결과에서는 기존 CacheD 격리/receipt 복구 절차를 보존하며 임의 재지급하지 않는다.
6. 이전 교환 UI 응답이 새 UI에 영향을 주지 않도록 기존 세션/세대 검사를 활용한다. 실패·취소·접속 종료 시 정확히 해당 교환 상태만 정리한다.

### 회귀 검증

정상 장비 교환과 속성 계승, 재료 부족, 원본 장비 변경/삭제/착용, 잘못된 선택, 교환창 종료 및 재개, 중복 응답, 인벤토리 용량 부족, 거래 경합, 접속 종료, SQL 롤백, COMMIT ACK 유실 및 재시작 복구를 검증한다. 정상 성공은 원본 및 재료의 정확한 차감과 결과 장비의 정확한 한 번 지급을 보장해야 한다. 실패는 부분 정산을 남기지 않아야 한다.

## 공통 구현 원칙 및 완료 기준

- 기존 CIO 소유 실행 구조, CacheD receipt 및 트랜잭션을 활용한다. 새 워커, 스레드, 중복 큐를 추가하지 않는다.
- 정상 게임 로직과 UI/패킷 호환성을 유지한다. 기존 SPR 변경, 일반 상점, 개인상점, 창고, 거래, 네트워크 코어는 작업 범위 밖이다.
- C01과 C02를 함께 완료한 뒤 C03을 처리하는 순서를 권장한다. 운영 반영 전에 세 영역의 회귀 테스트와 전체 Java 21 타입 컴파일을 완료한다.
- 실제 MySQL 커밋/롤백, receipt 복구, 접속 종료·재접속 경합을 검증하기 전에는 보안 문제가 완전히 해결되었다고 선언하지 않는다.
- 이번 커밋에는 보고서만 추가한다. 소스 코드, DB, 설정 및 테스트는 변경하거나 실행하지 않았다.
