# RKern 텔레포트 실행 구조 개편 지시서 — NC OFF 월드 이탈·진입 모델

작성일: 2026-11-29 (KST)
RKern 조사 기준: [rkern/gameplay-optimization-20261129](https://github.com/choeyeonggeol16-sketch/1234/tree/rkern/gameplay-optimization-20261129)
NC 기준: [Open_LU4_L2OFF abbb2e5](https://github.com/luannbr/Open_LU4_L2OFF/tree/abbb2e5f474d40cf12539a8e97fddc52474ed95e) (공개 OFF 복원 소스)
이번 커밋은 보고서만 추가한다. 구현 시 최신 게임 작업 브랜치와 다른 진행 중인 패치를 확인하여 기존 변경을 보존한다.

## 1. 결론 및 필수 원칙

기존 L1Teleport 중심의 분산 실행을 NC처럼 캐릭터의 텔레포트 실행과 CWorld의 월드 이동 책임으로 재편한다. 목적은 이동 성공/실패의 일관성, 중복 AOI 및 상태 처리 제거, 기존 게임 기능 보존이다.

- 클래스/메서드 명칭은 NC 원본에서 확인한 이름을 우선 사용하고 텔레포트 전용 코드는 전용 패키지에서 관리한다.
- 실제 NC에서 CTeleporter는 CNPC 파생의 텔레포터 NPC다. 일반 캐릭터 이동 클래스의 이름으로 오용하지 않는다. TeleportManager.cpp라는 파일명만 근거로 CTeleportManager 클래스를 만들지 않는다.
- 제안 패키지: model.character.teleport. 기존 model.item.teleport의 일반 이동 책임은 이관하고 아이템 전용 사용 규칙은 아이템 경로에 유지한다. 기존 동일 역할 패키지/클래스가 있다면 통합한다. 이름 변경만 하거나 빈 wrapper를 만들지 않는다.
- User 역할(현 L1PcInstance/공통 캐릭터): TeleportToLocation, OnTeleport, 이동 완료/실패 및 세션 상태.
- CCreatureController: 현재 행동 중단 및 월드 이탈 전 캐릭터 실행 상태 정리. NC의 Disappear 의미를 조사하여 기존 컨트롤러 책임에 적응한다.
- CWorld: 좌표, 실제 맵 인스턴스, 월드 등록, 가시성 인덱스의 구조적 전이. 기존 relocateVisibleObject 등 구현을 재사용한다.
- 인스턴스 던전, 공성, 파티, 동행물, 낚시, 버프 등은 각 기존 소유자의 책임으로 호출한다. 별도 클래스/worker/queue를 무조건 추가하지 않는다.
- **기존 TileOccupancy 점유 시스템, L1 2D 8방향 이동 규칙, CIO 타이밍 휠과 owner 실행 순서를 유지한다.** 점유 알고리즘 자체 개편, 일반 이동/공격/네트워크 구조 변경은 범위 밖이다.
- NC의 L2 패킷, 3D geodata, 월드 재등장 프로토콜을 L1에 그대로 복제하지 않는다.

## 2. NC 실제 실행 구조

[UserWorld.cpp L1883–1917](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/UserWorld.cpp#L1883-L1917):
User::OnTeleport에서 StopStore, StopMove, DoNothing, DetachAllChannelling, 낚시 종료, 의자/정지 상태 등을 정리한다.

[UserWorld.cpp L1954–2090](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/UserWorld.cpp#L1954-L2090):
User::TeleportToLocation은 목적지 geodata 및 캐릭터 상태를 확인하고, GetController()->Disappear(), 비활성 상태 및 다음 인스턴스 ID 설정, 방향 갱신, 텔레포트 패킷, 동행 소환물 이동 요청, CWorld::LeaveWorld 순서로 실행한다. LeaveWorld 실패 시 active 상태를 복원하는 분기가 있다.

[UserWorld.cpp L2091–2150](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/UserWorld.cpp#L2091-L2150):
User::OnAppearingPacket은 삭제 및 중복 재등장 상태를 검사하고 다음 인스턴스 관련 처리를 거친 뒤 CWorld::EnterWorld를 호출하며 캐릭터 패킷을 갱신한다.

[User.h](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/User.h#L1515-L1527): TeleportToLocation 선언.
[User.h L1798–1805](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/User.h#L1798-L1805): pending teleport item 관련 선언.
[UserBookmark.cpp](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/UserBookmark.cpp): 기억 장소 이동이 TeleportToLocationWithItem으로 연결되는 경로.
[TeleportManager.h L41–65](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/TeleportManager.h#L41-L65): CTeleporter는 실제 텔레포터 NPC.

NC의 핵심은 캐릭터 실행과 월드 진입/이탈의 책임 분리다. 위 호출의 내부 CWorld 구현 전체, OnTeleport와 Disappear의 상세 호출 연결, 비용 확정 순서 및 실패 복구 전체는 이번 조사에서 확인하지 못했다. 이를 확정 NC 원본 실행 순서로 확대 해석하지 않는다.

## 3. 최신 RKern 직접 확인 사항

### 3.1 월드 이동 실패가 무시되는 경로 (우선 수정)

[L1Teleport.java L174–201](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/item/teleport/L1Teleport.java#L174-L201), [L526–539](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/item/teleport/L1Teleport.java#L526-L539).
[CWorld.java L532–580](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/CWorld.java#L532-L580).

- L1Teleport.performPhysicalMove와 NPC teleport는 CWorld.relocateVisibleObject(object,x,y,int mapId)의 void overload를 호출한다.
- CWorld에는 실제 성공 여부를 반환하는 L1Map destination overload가 이미 있다. 내부 relocateLocked는 destination null, 잘못된 객체 identity, 미등록 위치, 잘못된 좌표, 교체/재활용된 맵 인스턴스, mapObjects.move 실패 시 false를 반환한다.
- void overload는 boolean 결과를 버린다. 호출부는 이후 PC의 SC_MAP_ID 전송/TileOccupancy.teleportRelaxed/경험치 보정 또는 NPC 방향/NpcEventFactory/점유 갱신을 계속한다. 실제 좌표 이동 거부 시 서로 다른 위치가 반영될 위험이 있다.
- 신규 월드 registry를 만드는 대신 기존 boolean 전이 API 및 정확한 L1Map 객체 identity를 이용하여 성공 경로만 진행한다.

### 3.2 중복 AOI 및 동행물 처리

[L1Teleport.java L79–172](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/item/teleport/L1Teleport.java#L79-L172), [L223–313](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/item/teleport/L1Teleport.java#L223-L313), [L315–411](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/item/teleport/L1Teleport.java#L315-L411).
- subjects에 PC를 먼저 추가하고 refreshVisibility에서 해당 PC의 onWorldMove(oldMap,oldX,oldY)를 호출한 뒤 subjects 반복에서 PC의 onWorldMove를 다시 호출한다.
- clearOwnedCombatTargets는 이동 전/동행물 이동 후 두 번 실행되며 companion.allTargetClear는 processCompanions 안에서도 실행된다. 다만 기존 주석은 이동 중 AI가 깨어나는 경쟁을 막기 위한 의도라고 명시한다. 실행 소유/AI handoff가 보장되기 전에는 무작정 중복 삭제하지 않는다.
- follower마다 주변 플레이어 탐색, subjects 등록 시 선형 중복 검사, 각 subject에 다시 onWorldMove 호출. 실제 AOI reconcile 책임과 겹치는 작업을 추적한다.
- TL_SUBJECTS는 재사용 리스트로 정상/예외 경로에서 clear한다. 재진입 가능성과 전체 주변 갱신 필요성을 확인하며 불필요한 새 리스트/Set/박싱을 추가하지 않는다.

### 3.3 맵별 부가 처리 및 시작 단계 부수 효과

[L1Teleport.java L422–478](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/item/teleport/L1Teleport.java#L422-L478), [L518–522](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/item/teleport/L1Teleport.java#L518-L522).
- 일반 맵 이동에서 cleanupIndunItems를 호출하고 updateQuestState에서도 동일 아이템 ID의 삭제 경로가 존재한다. 실제 조건, 중복 및 receipt 경쟁을 검토한다.
- updateQuestState에 oldMapId가 전달된다. 특정 맵 이탈/진입 판정이 실제 의도인지 확인하고 old/new/base map의 의미를 혼용하지 않는다.

[L1PcInstance.java L7356–7493](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/Instance/L1PcInstance.java#L7356-L7493).
- tpBeginStart는 _tpLock CAS와 GameUtils.isNonAction 확인 후 updateClanEinhasadBuff(targetMapId)를 먼저 실행한다. 목적지 이동/타이머 등록 성공 이전에 버프 상태 및 패킷이 달라질 수 있다.
- 해당 메서드는 매번 buffmaplist.split(",") 및 Integer.parseInt를 수행한다. 기존 혈맹 버프 정의 로딩 시 primitive membership으로 준비할 수 있는지 확인한다.
- tpCommitTeleport는 예외를 출력하지만 catch에서 tpEndFinish를 직접 보장하지 않는다. 내부 teleportInternal의 finally까지 도달하지 못한 예외의 경우도 검증한다.
- heading 인자 및 isAlways overload의 실제 사용 의미도 전체 호출 조사에서 확인한다. 기존 미사용 인자를 임의로 새 동작으로 바꾸지 않는다.
- _tpLock, teleportTimerEpoch, teleportDestinationZone, 기존 CInstantZoneManager의 목적지 identity/admission은 이미 존재하는 안전장치다. 무조건 제거하거나 대체하지 않는다.

조사 시 blob: L1Teleport 993797dee53c68636ca5557ace180148ec615558, CWorld 7ac1df15a02c7f58362c338ffa32ee7598e58e94, L1PcInstance a746fea8bfdedc36569d4e35998543ff264f1ec9.

## 4. 구현 지시

### P1. 전체 호출 및 소유 경로 조사
```sh
rg -n 'L1Teleport|start_teleport|do_simple_teleport|doTeleportation|doTeleport\(|tpBeginStart|tpCommitTeleport|tpEndFinish|teleportTimerEpoch|schedulePcTeleport|relocateVisibleObject|teleportRelaxed|teleportNpcOcc|onWorldMove|CompanionTeleportHandler|clearOwnedCombatTargets' rkern/src
```
일반/랜덤/귀환/소환/기억 장소/GM/NPC/인스턴스 입퇴장/공성/사망 및 동행물 이동을 모두 분류한다. 최신 코드에서 이미 이관된 경로는 중복 작업하지 않는다. NC 명칭과 기존 클래스 대응표를 작성한 뒤 이동 관련 코드만 수정한다.

### P2. 캐릭터 실행 및 월드 전이 책임 분리
- TeleportToLocation → OnTeleport(기존 행동 정리) → 기존 owner/timing wheel에서 실제 이동 확정 → 가시성 및 부가 상태 동기화 → 완료/실패 종료라는 L1용 실행 구조로 정리한다.
- 실제 L1 클라이언트의 텔레포트 잠금, 효과, 기존 30/280ms 지연 정책 및 해제 패킷을 보존한다. NC의 OnAppearingPacket 수신 단계를 L1에서 지원한다는 증거 없이 추가하지 않는다.
- 일반 텔레포트 시작과 즉시 이동 경로가 같은 전이 검증을 공유하도록 한다. 기존 호출부의 성공/실패 의미 및 즉시 이동 요구는 유지한다.
- CWorld의 기존 boolean relocateVisibleObject 및 실제 목적지 L1Map identity를 활용한다. 기존 CInstantZoneManager.destination/enterDestination의 인스턴스 lease/재활용 검증을 우회하지 않는다. ID만 재조회하여 재활용된 다른 맵으로 입장시키지 않는다.
- 기존 월드/좌표/점유/인스턴스 소유권이 정상적으로 전이된 뒤에만 후속 효과/패킷을 확정한다. CWorld와 TileOccupancy의 실제 락 순서와 성공/실패 경계를 확인한다. TileOccupancy 알고리즘 자체는 변경하지 않는다. 부분 실패는 기존 안전한 복구/세션 종료 정책과 연계한다.
- CWorld의 LeaveWorld/EnterWorld 이름은 기존 책임과 실제로 대응할 때만 적용한다. 일반 텔레포트를 객체 DB 등록 해제/재생성으로 구현하면 ID/세션/아이템/캐릭터 lifetime이 깨질 수 있으므로 금지한다. 기존 spatial index 전이를 이용한다.

### P3. 가시성 및 동행물
- 목적지 이동이 거부되면 새 목적지 기준 점유/맵 패킷/버프/인스턴스 퀘스트 아이템 처리/동행물 이동을 실행하지 않는다.
- 기존 old-viewer 제거와 self map reset, self put, ground sight suspend/resume, known object, new-viewer publish 순서를 검증한다. 제거 후 이동 실패 시 기존 맵에서 가시성을 복구하거나 안전한 세션 종료를 수행한다.
- 동일 PC onWorldMove 중복을 제거하되 동행물 이동 후 새로 필요한 reconcile은 해당 동행물/affected viewer 기준으로 정확히 수행한다. 기존 AOI 인덱스를 활용하고 주변 전체 재탐색을 최소화한다. 공유 SEND/브로드캐스트 코어 변경 금지.
- 동행물의 타겟/욕구/추격 상태 종료는 기존 NPC 소유 스레드에서 처리한다. 이동 중 과거 타겟을 다시 획득하지 않도록 기존 epoch/handoff 또는 실제 검증된 직렬화 정책을 사용한다. 단순히 clearOwnedCombatTargets 호출 횟수를 줄여 경쟁을 재발시키지 않는다.
- 펫/서먼/컴패니언/인형/서포트의 맵 제한, 위치 선택 및 실제 PC와 NPC 이동 성공 여부를 구분한다. removePetGracefully 등 이름과 실제 동작이 다른 경로는 기존 기능을 조사한 후 수정한다. NPC 일반 AI 및 점유 규칙은 유지한다.

### P4. 부가 상태 및 저장
- 혈맹 축복 맵 적용은 검증·확정된 실제 목적지 기준으로 한 번 반영한다. 맵 목록은 공유 설정에 준비하고 텔레포트마다 문자열 split/parse하지 않도록 한다. 혈맹 데이터 로더 변경은 해당 조건 인덱스에만 제한한다.
- 안전지대, 낚시, ghost, 투명, 버프, 파티, 공성, 인던, 퀘스트, 던전 타이머, 상점 UI 등 기존 효과를 소유 책임별로 이관한다. 필요한 호출은 누락하지 않고 동일 처리 중복만 제거한다.
- 아이템 정리의 중복 ID 및 조건을 합치되 기존 삭제 정책을 바꾸지 않는다. CacheD/인벤토리 receipt의 성공/실패, pending 소유권 및 재시도 일관성을 보존한다. 귀환 주문서/기억 이동 비용의 현재 확정 순서를 조사하며 임의 환불/무료 이동 정책을 새로 만들지 않는다.
- 시작 실패, 타이머 거절, destination lease 실패, stale callback, 예외, 로그아웃/사망 시 _tpLock과 teleport 상태가 정확히 한 번 해제되도록 한다. 이전 epoch의 종료가 새로운 이동을 해제하지 않도록 한다. heading/효과/이동 상태/패킷 갱신을 정확한 소유 순서에서 수행한다.

### P5. 패키지 및 레거시 정리
- NC에서 확인한 User/CCreatureController/CWorld의 역할과 이름을 기존 Java 구조에 대응시킨다. 실제 텔레포터 NPC가 존재하고 필요할 때 CTeleporter를 사용한다.
- 기존 L1Teleport에 남은 순수 이동과 아이템/콘텐츠 책임을 이관하고 전용 패키지에서 정리한다. 클래스 전면 교체가 완료되면 L1Teleport와 불필요한 static/singleton 위임, 중복 API를 삭제한다. 모든 호출 참조를 이관하며 호환용 레거시 클래스는 남기지 않는다.
- CIO 일반 실행기/네트워크/공격/DB 공통 구조, 점유 시스템 및 무관한 기능은 수정하지 않는다.

## 5. 검증 및 완료 기준

- Java 21 전체 의존성 타입 컴파일 및 관련 기존 회귀 테스트.
- 동일 맵/다른 맵/랜덤/귀환/기억 장소/소환/NPC/동행물/GM/인스턴스 전이의 기존 기능과 패킷 비교.
- 목적지 없음, 좌표 불가, 재활용된 맵 identity, 객체 삭제, mapObjects.move 실패, timer rejection, enterDestination 실패/예외 등 강제 실패. 실제 위치와 spatial index/점유/클라이언트 맵이 일치하며 잘못된 성공 부수 효과가 발생하지 않는지 확인.
- 동행물 AI 경쟁, old target 재획득, 이전 타이머 callback, 중복 텔레포트, 이동 중 로그아웃/사망/재접속 및 새 텔레포트 시작 경쟁.
- old viewer/new viewer의 제거/등장 패킷, ground sight, known object, invis/ghost/GM 상태, 파티, 공성, 인던/낚시/상점의 동작 유지.
- 인던 아이템 삭제/비용 receipt 실패 및 ACK 유실, 재시도 후 이중 차감/무료 이동/아이템 유실이 없는지 실제 저장 경로 검증.
- 동일 부하에서 AOI 조회 수, onWorldMove 호출 수, 동행물 타겟 초기화 수, 패킷 송신 수, 할당률 및 CPU/p95/p99를 A/B 측정한다. PC 5,000명 목표에서 랜덤텔/다수 동행물/인스턴스 입퇴장을 재현한다. 성능 미실측 수치 주장 금지.
- 최종 완료: 전이 실패 무시 경로 0, 정상 동작 유지, 레거시 전용 클래스/호환 wrapper 제거, NC 이름 및 전용 패키지, 기존 점유/CIO 유지. 부분 검증은 완료와 구분한다.

## 6. 확인 범위

최신 11월 29일 게임 작업 브랜치의 L1Teleport/CWorld/L1PcInstance와 NC UserWorld 및 관련 선언을 직접 확인했다. RKern 전체 호출 그래프, NC CWorld 내부 전이 상세, 실게임/DB/부하 검증은 미완료다. 구현 전 최신 소스에서 다시 확인하고 원본에서 확인된 사실과 L1 적응 정책을 분리한다. 이번 커밋은 보고서만 추가한다.
