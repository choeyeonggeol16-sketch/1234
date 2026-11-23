# NC L2Server 구조 분석 → RKern/L1J 이식·안정화 통합 보고서
**작성일:** 2026-11-24  
**대상:** RKern Java 21 / 최신 분석 기준 브랜치 `rkern/pc-unused-autohunt-20261123`  
**NC 비교:** [Open_LU4_L2OFF](https://github.com/luannbr/Open_LU4_L2OFF/tree/abbb2e5f474d40cf12539a8e97fddc52474ed95e), commit `abbb2e5f474d40cf12539a8e97fddc52474ed95e`  
**범위:** 인스턴스 던전/레이드, 승인 요청, 친구, 창고. NPC AI, AuthD, 네트워크 코어, 이미 구현된 거래·창고 CacheD 트랜잭션 재작성 제외.

> 이 문서는 **코드 분석·구현 지시서**이며 Java 코드를 수정하거나 실행 테스트한 결과가 아니다. NC 경로는 공개 복원 프로젝트의 현재 코드 기준이다. 원본 NC 바이너리에서 특정 함수가 복원되었다는 단서와 후대 복원본의 보강 코드를 혼동하지 않는다. 아래의 "확인"은 열람한 소스에 존재하는 행위를, "제안"은 RKern에 새로 적용할 설계를 뜻한다.

## 0. 결론 및 우선순위

| 순위 | NC 기능 | RKern 수정 위치 | 판단 |
|---|---|---|---|
| **P0** | `CInstantZoneManager`, `CInstantZone` 생명주기 | `InstanceSpace.java`, `InstanceObject.java`, `RaidSpace.java` | 맵 ID 회수·재사용, 종료/재로드 동시성; **실제 구조상 위험 확인** |
| **P1** | `CReplyChecker` 요청-응답/만료 | `CS_InvitePartyTarget.java`, `CS_JoinPledge.java`, `CS_Duel.java`, 실제 응답 핸들러 | 요청 덮어쓰기/오래된 응답 방지; **도입 가치 높음** |
| **P2** | `CFriends` ID 직접 조회·온라인 이벤트 | `L1Buddy.java`, `BuddyTable.java`, `CS_AddBuddy.java` | 온라인 전체 순회 제거 + DB 실패 시 메모리 불일치 수정 |
| **P3** | `CWareHouseDeposit/Withdraw` 이용 NPC/종류 소유 | `CS_Result.java`, `WarehouseTransactions.java`, 창고 목록 송신부 | 기존 원자적 이체 보존, 요청 세션 검증만 추가 |
| 후속 | `CacheD` 조회 책임 | `CS_JoinPledge.java`, `CharacterTable.java` | 혈맹 가입 시 직접 JDBC 조회 제거 |

**공통 제약:** 기존 CIO 샤드 소유 실행, CIO 타이밍 휠, CacheD 순서형 작업 및 영수증/트랜잭션 유지. 새 네트워크 스레드·중복 큐·필요 없는 다수 클래스를 추가하지 않는다. 동일 기능의 상태는 기존 소유 구조 안에 배치한다.

## 1. P0 — 인스턴스 던전 생명주기: NC `CInstantZoneManager`

### 1-1. NC 구현 확인

원본 비교: [NC L2Server/src/InstantZone.cpp](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/InstantZone.cpp).

- **L920–1026 `CInstantZone::TimerExpired`, `Copy`**: 인스턴스 타이머와 복제 객체 수명.
- **L1256–1298 `CInstantZoneGroup::IsAlreadyBoundUser`, `_IsAlreadyBoundUser`**: 기존 입장 바인딩·횟수·리셋 검사.
- **L1352–1412 `CInstantZoneCluster::GetLowestDensityZone`, `AddDensity`**: 클러스터별 밀도 관리.
- **L1875–1916 `SetInzoneDBIndex`, `BeInstantZoneCreate`, `FinishInstantZone`, `DeleteInstantZone`**: 인스턴스 ID/생성 가능 상태/완료/삭제 책임 분리.
- **L2000–2045 `CreateInstantZone`**: 템플릿 복제 후 컨테이너 할당 성공을 확인하고, 실패하면 삭제.
- **L2079–2110 `GetInstantZoneToEnter`**: 기존 연결 인스턴스를 먼저 확인하고 동일 인스턴스 재입장과 다중 입장을 구분.
- **L2208–2325 `EnterInstantZone`**: 입장 조건 검사와 사용자 연결, 실패 시 인스턴스 회수·계수 복원.

NC의 3D 인스턴스 컨테이너/클러스터를 그대로 이식하지 않는다. **객체 수명과 반납 순서**만 L1 2D 복제 맵에 대응시킨다.

### 1-2. RKern 현재 실행 경로

대상: [InstanceSpace.java](../rkern/src/l1j/server/server/model/world/instance/InstanceSpace.java), [InstanceObject.java](../rkern/src/l1j/server/server/model/world/instance/InstanceObject.java), [RaidSpace.java](../rkern/src/l1j/server/server/model/world/instance/raid/RaidSpace.java).

| 위치 | 현재 동작 | 확인된 위험/확인 사항 |
|---|---|---|
| `InstanceSpace.java` L19–42 | 지연 singleton, `release()`, `reload()` | singleton 생성이 동기화되지 않음. `reload()`는 새 인스턴스를 게시한 후 기존 것을 `clear()`함. 종료 중 인스턴스가 있으면 수명 정책 충돌 가능. |
| L44–64 | 복제 맵 ID를 `ArrayDeque<Integer>`에 보관 | 풀 자체는 재사용하지만 동시성 경계는 별도 검증 필요. |
| L65–97 `popMap`, `pushMap` | 잠금으로 할당/반환, `_objMap.put(id, null)` 후 ID를 큐에 재삽입 | 동일 객체에서 release가 두 번 호출돼도 반환 완료 여부를 검사하지 않음. 같은 ID가 큐에 중복 들어갈 가능성. HashMap에 `null` 값 잔류. |
| L99–119 `startInstance` | 잠금 밖 `_mapQ.isEmpty()` 확인 → `popMap` → `obj.init()` → `ThreadPool.execute(obj)` | 검사와 실제 할당이 분리. 스케줄링/초기화 실패 시 반환 보장이 `dispose()` 구현에 의존함. |
| L121–165 `releaseInstance` | NPC 삭제, **전역 `getAllItem()`를 배열로 복사**하여 대상 맵 아이템 정리 → `pushMap` → `dispose` | 인스턴스 하나 종료마다 전체 아이템을 스캔하며 배열 할당 발생. 플레이어/펫/서먼/지연 작업의 종료 장벽이 보이지 않은 상태에서 맵 ID가 재사용될 수 있음. |
| L183–217 | `_objMap.get`, `keySet`, `values`, `getOpens` | HashMap 읽기·컬렉션 노출이 잠금 밖. 생성·반환과 병렬 실행 시 일관성 위험. |
| `InstanceObject.java` L6–43 | `_copyMap`, `init/run/close/dispose` 추상 메서드 | 인스턴스 상태, generation, release-once가 공통 계약으로 정의되지 않음. |
| `RaidSpace.java` L24–110 | 별도 복제 맵 풀, 비동기 동기화 밖 singleton/reload | 같은 유형의 수명 위험. **특히 `startInstance()`는 `popMap(obj)` 실패 여부를 확인하지 않고 `obj.init()` 수행**. |
| `RaidSpace.java` L110–146 | 전역 아이템 복사/정리 후 맵 반환 | 동일한 종료 비용과 회수 문제. |

`clear()`가 `_objMap.values()` 순회 도중 같은 키에 `null`을 다시 넣는 것은 **그 자체만으로 반드시 ConcurrentModificationException이 발생한다는 의미가 아니다**. 문제는 순회 중 맵에 대한 공유 변경, 외부 동시 접근 및 회수 상태의 모호함이다.

### 1-3. 요구 동작 / 최소 수정안

1. 인스턴스 수명은 `FREE → ALLOCATED → RUNNING → CLOSING → RELEASED`로 관리한다. 이름은 상태 문서상의 표기이며, 구현은 기존 `InstanceObject` 안의 원시 상태값/세대 번호와 소유 잠금으로 충분하다.
2. 맵 ID의 **예약·생성 결과 확인·공개·반환**을 하나의 일관된 소유권 프로토콜로 구성. `popMap` 실패 시 `init` 또는 `ThreadPool.execute` 금지.
3. release는 정확히 한 번만 성공하도록 하고 `_objMap`에서 원래 소유 객체와 ID가 일치할 때만 제거. 중복 반환 금지; 반환 뒤에는 `obj.setCopyMap(null)` 등으로 수명 종료 표기.
4. **PC가 해당 맵에 남아 있거나 퇴장·텔레포트·오브젝트 삭제가 완료되지 않았다면 반환 보류.** 실패 시 기본 귀환 지점을 적용하되 기존 게임 입장·전투·보상 순서를 보존.
5. CIO/월드 소유 실행에서 정리 완료를 확정하고, 회수 시점 전에 기존 타이머·스폰·던전 재진입 콜백이 취소되거나 세대 검사로 무효화되도록 함.
6. `_objMap`, `_mapQ` 읽기/쓰기의 잠금 또는 명시적 owner 경계를 통일. `getOpens()` 등 **살아 있는 가변 컬렉션의 직접 노출**을 없애되 운영 관리 호출에만 스냅샷 비용을 허용.
7. 인스턴스 종료 시 `L1World.getAllItem()` 전역 배열 복사 대신 해당 mapId의 기존 월드/타일 객체 인덱스를 이용한 지역 정리. 원소 삭제 중 반복자 무효화 규칙은 기존 월드 인덱스 API에 맞춰 검증.
8. `reload()`, `release()`는 사용 중인 인스턴스를 강제로 무조건 폐기하지 않고, 진입 차단 → 기존 인스턴스 정리 → 소유권 반환 → 구성 교체 순으로 처리.
9. `InstanceSpace`, `RaidSpace`의 중복 코드를 무조건 새 프레임워크로 묶지 말고 두 경로에 **동일한 수명 불변식** 적용.

### 1-4. P0 필수 회귀

- 맵 1개만 남았을 때 2개 인스턴스 동시 생성: 동일 ID 이중 할당 0.
- `popMap` 실패/clone 실패/`init` 예외/작업 제출 거부 각각에서 ID 유실·중복 반납 0.
- `releaseInstance` 연속 2회와 동시에 2회 호출: ID 반환 정확히 1회.
- PC 잔류, 펫·서먼, 바닥 아이템, NPC 스폰 타이머가 있는 상태에서 종료: 다음 사용자에게 잔재 노출 0.
- 종료 중 재접속·재입장, reload/close 경합, stale 콜백: 기존 던전 복구 및 재생성 상태 정합.
- 다수 인스턴스 동시 종료 시 world-item O(전체 아이템 수) 스캔/임시 배열이 제거되었는지 JFR 또는 할당 계측.

## 2. P1 — NC `CReplyChecker` 요청·응답·타임아웃

### 2-1. NC 확인

출처: [NC L2Server/src/ReplyChecker.cpp](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/ReplyChecker.cpp).

- **L47–97** `IsUserWaitingReply`, `GetRequesterFromReplier`, `GetReplierFromRequester`: 요청 양쪽의 동일 checker 정합성을 확인.
- **L99–181** `TimerExpired`: 요청 유형별 만료 시 양쪽 메시지/상태 정리.
- **L183–209** `AttachReplyChecker(type, requester, replier, timeoutSeconds)`: 두 캐릭터 중 하나라도 이미 대기 중이면 등록 실패. 두 ID를 **동일 요청 객체**에 묶고 타이머 등록.
- **L211–251** `FinishWaitingReplyByReplier`, `FinishWaitingReplyByRequester`: 양쪽 매핑을 동일 객체인지 확인한 뒤 제거. 늦은 종료가 새 요청을 제거하지 않도록 설계된 부분.
- **참고:** NC는 요청마다 checker 객체/타이머를 생성한다. RKern에는 그대로 신규 타이머 워커/큐를 복제하지 않는다.

### 2-2. RKern 현황

- [CS_InvitePartyTarget.java](../rkern/src/l1j/server/server/model/network/core/cs/packet/CS_InvitePartyTarget.java) **L56–139** `handleNormalPartyInvite`: `targetPc.setPartyID(pc.getId())` 다음에 `SC_MessageYN.send`. **L141–176** 채팅 파티 역시 `setPartyID`.
- [CS_JoinPledge.java](../rkern/src/l1j/server/server/model/network/core/cs/packet/CS_JoinPledge.java) **L181–202**: 수동 혈맹 승인 대기 `target.setTempID(player.getId())` 후 Y/N 전송.
- [CS_Duel.java](../rkern/src/l1j/server/server/model/network/core/cs/packet/CS_Duel.java) **L66–80**: 결투 응답 전 양쪽 `setFightId`.
- 위 **요청 진입부**에는 요청별 타임아웃, 고유 세대, 양쪽 예약의 원자 등록이 없음. 응답 핸들러와 캐릭터 종료·이동 처리를 추가 추적해야 종단 간 결함을 확정할 수 있음.
- [CS_AskExchange.java](../rkern/src/l1j/server/server/model/network/core/cs/packet/CS_AskExchange.java)와 [CS_TradeAccept.java](../rkern/src/l1j/server/server/model/network/core/cs/packet/CS_TradeAccept.java)는 **이미 거래 결산용 양쪽 게이트와 CacheD 완료 경로가 있음**. 이것을 checker로 덮어쓰지 말 것.

### 2-3. 요구 동작 / 최소 수정안

- 이름은 원본에 맞춰 `CReplyChecker` 역할을 제공하되, 구현은 기존 CIO 타이밍 휠과 소유 실행부를 활용. 상태를 한 곳에 모으고, 파티·혈맹·결투 공통의 **승인 대기 수명**만 관리한다.
- `AttachReplyChecker`: 요청자/응답자 양쪽을 확인하고 원자 등록. 한 쪽이라도 기존 초대가 살아 있으면 실패/정책에 따라 명시적 교체; **조용한 덮어쓰기 금지**.
- 등록 상태는 최소 `type, requesterId, replierId, generation, expiresAt`. 재접속 후 동일 objectId라도 이전 요청의 응답/타이머는 무효.
- `FinishWaitingReplyByReplier` / `...ByRequester`: 응답 처리 시 양쪽 상태가 같은 generation인지 검사. 수락/거절/만료/로그아웃/상대 사망·이동 정책으로 정리.
- 타이머의 임의 스레드가 직접 두 PC 상태를 변경하지 않게 하고, 기존 CIO owner/해당 게임 상태 직렬화 경로에서 commit. **다른 CIO 샤드 소유자 2명**이 얽힐 때는 양쪽을 무턱대고 잠그거나 owner 스레드를 블로킹하지 않도록 기존 조정 방식과 대조.
- 패킷과 게임 의미는 L1 그대로: `SC_MessageYN`의 응답 번호와 파티/혈맹/결투 기능 동작 보존.

**테스트:** 양쪽에서 동시에 초대, A→B 및 C→B 경합, 만료 직전 승낙, 만료 뒤 응답, 재접속·로그아웃·이동, 응답 재전송, 동일 ID의 새로운 세대 요청, 파티 해체/혈맹 상태 변경, 기존 거래 결산 무변경.

## 3. P2 — NC `CFriends` ID 중심 관리 + RKern 친구 DB 정합성

### 3-1. NC 확인

[NC L2Server/src/Friends.cpp](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Friends.cpp):

- **L72–233** `HasFriend`, `AddFriend`, `RemoveFriend`, `ChangeFriendName`: `charId → FriendInfo`와 `name → charId` 양방향 인덱스를 함께 갱신.
- **L290–323 `MakeFriendList`**: 전체 플레이어를 순회하지 않고 **친구 목록을 순회하면서** `g_UserDBIDMap.FindObject`로 현재 온라인 여부 확인.
- **L324–357 `SendLogonMsgToFriend`, `SendLogoffMsgToFriend`**: 관련 친구에게 접속/종료 알림.

### 3-2. RKern 현황 / 확인된 개선점

- [L1Buddy.java](../rkern/src/l1j/server/server/model/L1Buddy.java) **L12–88**: `HashMap<Integer,String> _buddys`. `containsName()`, `remove(String)`는 전체 친구 이름 순회. **L49–57 `getOnlineBuddyListString()`는 `L1World.getAllPlayers()` 전체 순회 + 반복 `String +=`**.
- [BuddyTable.java](../rkern/src/l1j/server/server/datatables/BuddyTable.java) **L18–49**: 캐릭터별 친구 정보를 DB에서 시작 시 로드. **L53–66 `getBuddyTable`**: Trove 맵을 잠금 밖에서 읽다가 잠금 내부에서 새 항목을 삽입; 이 맵은 동시성 보장을 확인해야 함.
- [CS_AddBuddy.java](../rkern/src/l1j/server/server/model/network/core/cs/packet/CS_AddBuddy.java) **L80–98**: 기존 `buddyList`가 존재하면 **DB 작업 전에 `buddyList.add(objectId,name)`을 실행**.
- [BuddyTable.java](../rkern/src/l1j/server/server/datatables/BuddyTable.java) **L69–93 `addBuddy`**: DB INSERT 성공 후 메모리 등록, SQL 예외는 로깅. 따라서 상위 핸들러가 미리 넣은 경우 **DB 실패에도 메모리에는 친구가 남는 불일치 가능성이 소스상 확인**.
- [CS_QueryBuddy.java](../rkern/src/l1j/server/server/model/network/core/cs/packet/CS_QueryBuddy.java): 캐릭터 ID로 목록 조회.

### 3-3. 요구 동작 / 최소 수정안

1. 이름은 필요 시 `CFriends`로 정리할 수 있지만 **새 객체를 이중 보유하지 말고 기존 `L1Buddy`를 재구성**. 공개 `HashMap`과 `getBuddy()`의 외부 수정 의존 관계 검사.
2. 온라인 조회는 친구 항목만 돌면서 `L1World.findObject(friendId)` 또는 확인된 인덱스를 통해 PC 활성 여부 조회. 문자열은 요청 단위 `StringBuilder`를 한 번 사용. 순서/공백/대소문자 등 패킷 출력과 동일성 테스트.
3. `CS_AddBuddy`의 **DB 선행 없는 메모리 변경 제거**. CacheD의 적절한 순서 키로 INSERT를 확정한 뒤 owner 콜백에서 메모리 반영; 실패 시 사용자에게 실패 알림, 불일치 없음. 제거도 성공/실패 경계 통일.
4. 동일 대상 중복 추가, 이름 변경, 접속 종료, 재접속을 다룰 때 두 인덱스의 동시 갱신 정책을 명시. 이름 역인덱스는 실제 조회 빈도·메모리 비용을 측정해 도입 여부 결정.
5. DB 테이블을 게임 중 매 조회하지 않도록 기존 메모리 캐시 유지. 이 항목은 **아이템 거래 트랜잭션과는 별개**.

**테스트:** 친구 0/1/100명에서 온라인 목록 동일, 동접 5,000명일 때 온라인 목록 비용, SQL 실패/재시도, 중복 친구 추가, 친구 삭제 동시성, 이름 변경, 접속 상태 이벤트, DB 재시작 일관성.

## 4. P3 — NC `CWareHouseDeposit/Withdraw`의 NPC/세션 바인딩

### 4-1. NC 확인

[NC L2Server/src/WareHouse.cpp](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/WareHouse.cpp):

- **L31–73** `CWareHouseDeposit`: actor, 창고 NPC ID, 창고 종류, 소유자 보유.
- **L143–167 `GetNPCSP`**, **L311–374 `IsInRange`**: 소유한 NPC를 재조회하고 접근 유효성을 검사하는 기능 보유.
- **L375–671 `Request`**, **L672–950 `RequestEx`**: 입고 요청 처리. `RequestEx` 안에 NPC 생존·작업 중복 여부 확인이 있음.
- **L951–1085 `CWareHouseWithdraw` 생성/actor/NPC**, **L1266–1314 `IsInRange`**, **L1361–1585 `Request`**, **L1586–1849 `RequestEx`**: 출고 처리. 작업 중복 플래그가 있음.
- NC의 `IsInRange` 함수 **존재 자체가 모든 요청 경로에서 매번 호출된다는 뜻은 아니다**. RKern 이식 시 실제 호출부/패킷 경계를 검증.

### 4-2. RKern 현황

- [CS_Result.java](../rkern/src/l1j/server/server/model/network/core/cs/packet/CS_Result.java) **L61–72**: `npcObjectId`의 지도와 X/Y 각 12타일 제한 확인. **L159–173**: 창고 종류를 NPC 타입 및 결과 타입으로 판별해 `WarehouseTransactions.execute` 호출.
- [WarehouseTransactions.java](../rkern/src/l1j/server/server/model/network/core/cs/packet/WarehouseTransactions.java) **L22–131**: 100개 선택 제한, 거래 중 거부, 창고 종류 및 수수료, 아이템 개수 확인, 일괄 입·출고 호출.
- **L143–184**: 개별 입고 조건과 단일/출고 이체 처리.
- [CS_WarehouseControl.java](../rkern/src/l1j/server/server/model/network/core/cs/packet/CS_WarehouseControl.java) **L14–90**: 게임 비밀번호 인증 및 목록 요청.
- [PlayerShopTransactions.java](../rkern/src/l1j/server/server/model/network/core/cs/packet/PlayerShopTransactions.java)는 이미 복수 판매 항목 검증·`L1InventoryTransaction`/COMMIT 경로를 보유하므로 이 작업에서 변경 제외.

### 4-3. 요구 동작 / 최소 수정안

1. 목록을 연 순간 **창고 이용 권한**을 `CUserSocket` 또는 기존 PC 상호작용 상태에 부여: `keeperObjectId`, `warehouseType`, `ownerId`, `sessionStamp`, expiry. 원시값 위주로 구성; 새 요청별 스레드/큐 금지.
2. 실제 입출고에서 사용자가 **바로 앞에서 연 NPC와 현재 접근 가능한 NPC가 동일**한지, 동일 세션·창고 종류·혈맹 권한인지 검사. 단순 거리 판정만으로 대화 세션의 기원을 보장했다고 보지 않음.
3. 텔레포트, 지도 변경, 로그아웃, NPC 삭제, 창고 창 종료, 세션 교체 때 권한 무효화. 클라이언트가 임의의 old `npcObjectId`, type, itemId를 재전송하면 실패.
4. **기존 `tradeWarehouseDepositBatch`, `tradeWarehouseWithdrawalBatch`, `Warehouse.tradeItem`, CacheD 영수증/원자적 이체는 보존**. 인증 검사는 commit 이전, commit 성공 이후 응답으로 유지.
5. **L159–173의 분기 순서 테스트**: 다중 선택은 창고 분기가 먼저, 단일 선택은 `processChangeItems` 확인 뒤 창고 분기로 내려감. 이로 인해 실제 정상 창고 동작이 가로채지는지 확인해야 하며, 무조건 버그라고 단정하지 않는다.

**테스트:** 창고 NPC와 멀어지기, 다른 NPC로 열기, 이동/텔레포트 후 재전송, 패킷 type 위조, 혈맹 탈퇴, 로그아웃, 다중 선택 1–100개, 중복 아이템 ID, 수수료 미납, CacheD COMMIT 실패·결과 불명, 아이템 복제/유실 0.

## 5. 연결된 후속 항목: 혈맹 가입 직접 DB 조회

- [CS_JoinPledge.java](../rkern/src/l1j/server/server/model/network/core/cs/packet/CS_JoinPledge.java) **L110–162 근방**은 동일 계정 캐릭터의 혈맹 ID 검사에 `CharacterTable.CharacterClanCheck(player.getName())`를 사용.
- [CharacterTable.java](../rkern/src/l1j/server/server/datatables/CharacterTable.java) **L313–331** `CharacterClanCheck`는 직접 JDBC로 `SELECT clanid FROM characters WHERE account_name = (SELECT account_name FROM characters WHERE char_name = ?)`를 실행.
- **제안:** 가입 요청을 CacheD 계정 순서 키에 배치하고, 조회 결과를 기존 CIO owner 콜백으로 가져와 현재 요청 세대/양쪽 캐릭터/혈맹 상태를 재확인한 뒤 승인 메시지 발행. 원래 계정 내 혈맹 제한·성공/실패 메시지 보존. 이 항목은 승인 요청 관리 구현과 함께 처리 가능.

## 6. 이전 조사 후보(이번 작업과 혼합 금지)

아래는 별개로 분석되었던 NC 후보이다. **이번 구현 범위에 자동 포함하지 않는다.**

| NC 함수/모듈 | 관련 RKern | 목적 |
|---|---|---|
| `CQuestSystem` / [Quest.cpp](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Quest.cpp) | `model/L1Quest.java` | 퀘스트 진행 저장의 직접 JDBC 경로 최소화 |
| `CParty::RouteAdena` / [Party.cpp](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Party.cpp) | `model/character/Item/Drop/DropShareEngine.java` | 분배 실패를 성공으로 간주하지 않도록 처리 |
| `CSkillReuseDelay` / [SkillReuseDelay.cpp](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/SkillReuseDelay.cpp) | 스킬 쿨타임 처리부 | 변경된 재사용 대기만 저장하는 dirty 처리 |
| `CCreature::TriggerSkillByAttack/Damage/Avoid/Block/Kill` / [CreatureTrigger.cpp](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/CreatureTrigger.cpp) | `CAttackAction`, `CCreatureDamageReaction` | 이벤트별 발동 조건과 실행 순서 |
| `AreaBasedRule` / [AreaBasedRule.cpp](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/AreaBasedRule.cpp) | `L1Map`, 전투 판정 | 지역별 행동 제한 일관성 |
| `CObjectDB` / [ObjectDB.cpp](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/ObjectDB.cpp) | `DropTable.reload()` | 기존 읽기 참조를 깨뜨리지 않는 데이터 재로드 |
| `CPledge` / [Pledge.cpp](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/CacheD/src/Pledge.cpp) | `L1Clan` | 혈맹 멤버 ID 인덱스/읽기·쓰기 책임 |

## 7. 실행 단계 / 완료 판정

1. **P0 수명 계약 확정:** `InstanceSpace`와 `RaidSpace`의 생성→회수 호출부, `InstanceObject/RaidObject.dispose()` 실제 동작, PC·NPC·아이템 이탈 경로까지 추적. 현재 소스와 중복된 로직/호출자 목록을 먼저 확정.
2. **P0 최소 수정:** 두 인스턴스 풀의 생성/종료/재사용 및 실패 회수 원자성, 지연 콜백 세대 장벽. ID 중복 할당·맵 사용 중 재사용 방지 검증.
3. **P1 승인 요청:** 모든 `SC_MessageYN` 응답 수신 분기·취소·로그아웃 호출부 추적. 원본 `CReplyChecker` 명칭/역할에 맞춰 기능 묶고 기존 타이밍 휠로 timeout 처리.
4. **P2 친구 목록:** 전체 온라인 스캔 제거 → DB 성공 뒤 메모리 반영 → 대소문자/이름 변경/접속 변동 검증.
5. **P3 창고:** 목록을 열 때의 권한과 실제 요청을 바인딩하고, 기존 거래/창고 영수증 경로를 유지하며 공격적 재전송 테스트.
6. **공통 컴파일·테스트:** 수정된 Java 타입 컴파일, 패킷 호환, 소유 스레드 외 변경 금지, stale 콜백/재접속, SQL 장애, 총 아이템 수 불변, 20+ producer concurrency/부하 검사.
7. **배포 기준:** 실패 중복, 반납 중복, 접속 끊김 후 유령 상태, 아이템·아데나 복제/유실, 정상 기능 회귀가 0인지 확인 후만 운영 반영. 검증 없이 '버그 없음' 또는 '5,000명 보장'이라고 선언하지 않는다.

**작업 상태:** 2026-11-24 분석 보고서 작성만 수행. Java 생산 소스 수정 **없음**, 코드 컴파일/인게임/DB 장애/성능 테스트 **미실시**.
