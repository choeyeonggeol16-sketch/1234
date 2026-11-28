# RKern 아이템 재사용 대기시간 개선 지시서 — NC OFF 시각 비교 방식

작성일: 2026-11-29 (KST)
RKern 조사 기준: [rkern/gameplay-optimization-20261129](https://github.com/choeyeonggeol16-sketch/1234/tree/rkern/gameplay-optimization-20261129)
NC 기준: [Open_LU4_L2OFF abbb2e5](https://github.com/luannbr/Open_LU4_L2OFF/tree/abbb2e5f474d40cf12539a8e97fddc52474ed95e) (OFF 복원 소스)
이번 커밋은 보고서만 작성한다. 구현 시 최신 작업 브랜치와 진행 중인 다른 개편을 먼저 확인한다.

## 1. 결론

현재 RKern의 단순 아이템 재사용 제한은 아이템 사용 시 만료 이벤트를 CIO timing wheel에 등록하고, 만료 시 CCreatureController의 상태를 삭제한다. NC의 아이템 재사용 시각 관리처럼 이를 사용 가능 시각(deadline) 저장 및 조회 시 비교 방식으로 교체한다.

- 단순 아이템 재사용 제한에 한하여 타이머 등록, 만료 callback, generation 및 실행 이벤트 제거.
- 기존 delayId 공유 그룹, 아이템 사용 조건, 실제 지속시간, 패킷 및 경제 트랜잭션 보존.
- 별도 worker, queue, scheduler, 주기 청소 타이머 추가 금지.
- 스킬 시전 게이트, 버프/디버프, 이동/공격, 투명 효과, 실제 만료 행동을 수행하는 타이머는 변경하지 않는다.
- 기존 캐릭터 소유 구조와 CIO owner 순서를 유지한다. 네트워크 공통 코어 및 DB 공통 실행기 수정 금지.

## 2. NC 실제 구현 근거

### 2.1 CCreature의 아이템 재사용 시각

[Creature.h L1937–1940](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Creature.h#L1937-L1940)
[CreatureServer.cpp L13132–13207](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/CreatureServer.cpp#L13132-L13207)

- GetItemReuseDelay: m_UsedItemsSect 보호 하에 m_UsedItemsLastTimeMap.find(itemId)로 재사용 가능 시각 조회.
- GetItemRemindTimeMs: 저장된 reuseEndTick과 현재 GetTickCount 차이를 계산하여 남은 시간 또는 0을 반환.
- SetItemReuseDelay: 재사용 가능 시각을 갱신하고 PC에서 변경되었으면 RequestSetItemReuseDelay를 호출한다. 영구 저장용 시각 변환 경로도 존재.
- InsertItemReuseDelay: 전달된 delay를 시각으로 바꾸어 해당 ID에 저장.
- 확인된 위 단건 저장/조회 경로는 만료 callback을 실행하여 항목을 지울 필요가 없다. NC 전체에 관련 타이머가 전혀 없다고 확대 해석하지 않는다.

### 2.2 공유 아이템 재사용 그룹

[User.h L787–799](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/User.h#L787-L799)
[UserItem.cpp L393–474](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/UserItem.cpp#L393-L474)

NC의 SharedReuseDelaysOfItems는 공유 그룹 ID → 재사용 종료 시각을 관리한다.
- AddReuseDelay: 그룹별 시각 등록/갱신 및 최대 등록 수 제한.
- GetReuseRemainTime: 현재 시간과 종료 시각을 비교하여 남은 시간 반환.
- UseDelayShareItem: 락 내부에서 기존 deadline을 검사하고 만료되었을 때만 새 시각 설정. 성공 시 CacheD 저장 요청.
- RemoveReuseDelay/RemoveReuseDelayAll: 그룹별 제한 초기화 및 저장.

RKern의 delayId는 이미 공유 그룹 의미가 있으므로 아이템 클래스 ID별 별도 자료구조를 근거 없이 추가하지 않는다. 실제 데이터/호출에서 개별 아이템 ID와 공유 그룹을 구분할 필요가 있을 경우에만 NC의 두 책임을 분리한다. NC의 영구 저장 및 wall-clock 사용 정책, 특정 시각 초기화 특수값을 L1에 일방적으로 도입하지 않는다.

## 3. 최신 RKern에서 확인한 기존 경로

[CCreatureController.java L716–719](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/character/action/CCreatureController.java#L716-L719),
[L1093–1227](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/character/action/CCreatureController.java#L1093-L1227).
확인한 blob: 16bd58add39160ce628df6c0cca544bb80bb0665.

- itemDelayIds / itemDelayGenerations 두 primitive 배열과 itemDelayCount/seed를 유지.
- AddItemDelay: 선형 검색, 신규 ID/세대 등록, schedulePcItemDelay 요청. 요청 거절 시 등록 항목 제거.
- HasItemDelay: 시각 비교 없이 배열에 존재하는지 확인.
- RemoveItemDelay/ItemDelayExpired: 선형 검색 후 swap-remove. 만료는 generation 일치 검사.
- ClearItemDelays: 배열 초기화와 세대 갱신.
- EXEC_ITEM_DELAY, AddExecutableItemDelay 및 실행 분기([L283–288](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/character/action/CCreatureController.java#L283-L288), [L620–621](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/character/action/CCreatureController.java#L620-L621))가 존재한다. 단순 제한에 불필요한 실행 경로다.

[CIOThreadCallback.java L1025](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/network/thread/CIOThreadCallback.java#L1025), [L1777](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/network/thread/CIOThreadCallback.java#L1777), [L1906–1925](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/network/thread/CIOThreadCallback.java#L1906-L1925), [L2654–2705](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/network/thread/CIOThreadCallback.java#L2654-L2705): PC_ITEM_DELAY_TIMER, onPcItemDelayTimerSchedule, schedulePcItemDelay 확인.

[L1ItemDelay.java L19–53](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/L1ItemDelay.java#L19-L53): etc item의 delayId/delayTime으로 pc.addItemDelay를 호출한다. 반환값을 무시하는 현행 호출이 존재한다. 이 메서드는 투명 장비의 beginInvisTimer도 별도로 호출하므로 전체를 단순 쿨타임과 동일시하여 삭제해서는 안 된다.
[L1PcInstance.java L7613–7635](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/Instance/L1PcInstance.java#L7613-L7635): 컨트롤러 clear/add/remove/has 위임 확인.
[L1Character.java L994–1007](https://github.com/choeyeonggeol16-sketch/1234/blob/rkern/gameplay-optimization-20261129/rkern/src/l1j/server/server/model/L1Character.java#L994-L1007): 스킬 공통 시전 게이트는 이미 nanoTime deadline 비교. 이번 대상 아님.

## 4. 구현 지시

### P1. 실행 경로 확인 및 책임 정리
최신 전체 checkout에서 아이템 사용 패킷 → 조건 검사 → 아이템 효과/소비 → 지연 등록, 실패, 비동기 receipt, 접속 종료/초기화의 모든 호출을 추적한다.
```sh
rg -n 'L1ItemDelay|onItemUse|addItemDelay|hasItemDelay|removeItemDelay|AddItemDelay|HasItemDelay|RemoveItemDelay|ClearItemDelays|ItemDelayExpired|EXEC_ITEM_DELAY|PC_ITEM_DELAY_TIMER|schedulePcItemDelay|onPcItemDelayTimerSchedule|AddExecutableItemDelay|itemDelayIds|itemDelayGenerations|itemDelayGenerationSeed' rkern
rg -n 'get_delayid|get_delaytime|delayId|delayTime' rkern/src
```
타이밍 휠 내부 event type/dispatch/거부 경로, 테스트, 설정 및 모니터링 참조까지 확인한다. 단순 제한 만료에 실제 알림/효과가 있는지 먼저 조사한다.

- 클래스명은 NC에서 확인된 SharedReuseDelaysOfItems를 공유 그룹 관리에 우선 사용한다. 전용 패키지는 기존 구조에 맞춰 model.character.itemreuse로 구성하는 것을 제안한다. 이미 같은 책임을 가진 패키지나 소유 구조가 있으면 중복 생성하지 않는다. 별도의 CItemReuseDelay 등 확인되지 않은 명칭을 NC 원본 이름이라고 주장하지 않는다.
- 소유자는 기존 CCreature 역할(현 L1Character/PC 소유 구조) 또는 실제 User 전용 그룹 소유 구조. 현재 클래스의 실제 책임과 NPC 사용 여부 확인 후 배치. NPC마다 사용하지 않는 배열/맵을 할당하지 않는다.
- API는 NC 명칭에 맞춰 GetItemReuseDelay / GetItemRemindTimeMs / SetItemReuseDelay / InsertItemReuseDelay 및 공유 그룹의 UseDelayShareItem / GetReuseRemainTime / RemoveReuseDelay / RemoveReuseDelayAll 중 실제 필요 역할만 사용한다. PC 위임 래퍼의 중복은 제거한다.

### P2. deadline 자료구조
- JVM 내부 제한은 System.nanoTime() 기반 long deadline으로 저장한다. 비교는 signed difference 방식(now - deadline >= 0)으로 처리하고 duration 단위/오버플로 경계를 검증한다. nanoTime 절대값은 DB에 저장하지 않는다.
- 현재 primitive 배열의 낮은 메모리 비용을 유지할 수 있는지 실제 delayId 개수/분포/동시 활성 그룹 수를 측정한다. 적은 그룹에서는 int[] + long[] 재사용 배열이 유리할 수 있고, 밀집 그룹이면 인덱스 압축 배열, 희소 그룹이 많으면 기존 primitive int-to-long map을 비교한다. 무조건 HashMap<Integer,Long>/TreeMap으로 직역 금지. 조회당 객체 생성 없음. 단건 조회를 꼭 O(1)로 바꾸기 위한 과도한 메모리 증가는 실측으로 판단한다.
- 만료된 항목은 조회 시 inactive로 취급한다. 신규 삽입/갱신 시 만료 슬롯을 재사용하거나 필요한 경우 소유 실행 안에서 정리한다. 장기간 사용으로 만료 ID가 누적되어 저장 공간이 무한 증가하지 않도록 제한. 정상적으로 가능한 모든 그룹을 수용하며 가득 찼다는 이유로 제한을 우회하거나 아이템을 소비하지 않는다.
- 소유 스레드에서 check+reserve를 일관되게 처리한다. 외부 스레드가 있다면 기존 owner 전달 또는 검증된 가시성/락 정책 사용. 배열 publication이나 락 제거를 임의로 변경하지 않는다.
- 기간 0/음수, 그룹 0/음수, 동일 그룹 재사용, 최초/중복 등록, 수동 초기화의 반환 의미를 기존 호출자와 맞춘다.

### P3. 아이템 처리 및 트랜잭션 안전성
- 재사용 검사는 아이템 사용의 실제 승인 시점에서 이루어져야 한다. 동시에 같은 그룹을 사용하는 요청이 모두 통과하지 않도록 기존 owner 실행에서 검사와 승인/예약을 연결한다.
- 소비/사용 실패 시 제한을 시작할지 여부는 기존 L1 정책과 실제 호출 순서를 먼저 확인한다. 경제 receipt를 사용하는 아이템에서 DB 실패, 중복 요청, ACK 유실, pending 중 동일 그룹 사용을 검증한다. 실패로 소비되지 않은 아이템에 의도치 않은 쿨타임이 남거나 성공 아이템에서 쿨타임이 생략되지 않도록 한다.
- 기존 onItemUse에서 addItemDelay 반환값을 무시하는 경로는 호출 순서를 확인 후 안전하게 정리한다. 단순 boolean 교체만으로 실패를 성공으로 처리하지 않는다.
- 투명 장비 beginInvisTimer 등 실제 효과 타이머는 기존 책임 위치로 유지/이관한다. 이 분기를 지우면 안 된다.
- 현재 쿨타임의 사망/로그아웃/재접속/직업 변경 정책을 확인하고 그대로 유지한다. 영구 저장이 이미 필요하다고 확인된 그룹만 CacheD 경로에서 기존 정책대로 처리한다. NC처럼 재접속 후 제한을 유지하는 신규 정책은 사용자 승인 없이 추가하지 않는다. 필요 시 저장 시각은 epoch time, 복원 시 remaining duration을 nanoTime deadline으로 변환한다.

### P4. 불필요한 타이머 경로 삭제
단순 재사용 제한의 deadline 이관과 모든 호출자 검증 후 다음 전용 코드만 제거한다.
- itemDelayGenerations, itemDelayGenerationSeed, ItemDelayExpired 및 관련 만료 callback.
- EXEC_ITEM_DELAY와 AddExecutableItemDelay, 전용 dispatch 분기.
- PC_ITEM_DELAY_TIMER / schedulePcItemDelay / onPcItemDelayTimerSchedule 및 timing wheel 내부 전용 event 경로.
- 타이머 등록 성공/거부/세대 일치 등 이 기능에만 존재하던 중복 상태 및 테스트.
공용 event type/숫자 opcode를 다른 이벤트와 공유할 경우 의미가 바뀌지 않도록 한다. 공용 timer admission, 기타 PC/NPC 타이머, 스킬 효과, 스킬 게이트, 이동/공격 및 네트워크 실행 구조는 보존한다. 기존 타이머를 재등록하는 주기성 스캔으로 대체 금지.

## 5. 검증 및 종료 조건

- Java 21 전체 의존성 타입 컴파일 및 기존 회귀 실행.
- 단일 그룹, 서로 다른 그룹, 여러 아이템의 동일 delayId, deadline 직전/정각/직후, 매우 긴 기간, 반복 사용, 제한 없는 아이템, 수동 초기화, 사망/로그아웃/재접속.
- 시간 함수 단위 테스트로 nanoTime wraparound, ms→ns 변환, 음수/0/최대 기간 처리 검증. 시스템 wall-clock 변경이 실행 중 제한을 바꾸지 않는지.
- 연속 동일 그룹 사용, 다른 스레드 호출, owner FIFO, 아이템 사용 실패, 소비 receipt 실패/ACK 유실/재시도 검증. 기존 아이템 기능 및 소비 정확성 유지.
- 만료 후 동일 그룹 재사용, 장시간 및 여러 희소 ID 사용에 따른 메모리 증가 제한, 다른 아이템/캐릭터의 쿨타임 오염 0.
- 타이머 경로 제거 후 투명 장비, 스킬 효과, NPC 타이머 및 공용 timing wheel의 회귀 검증.
- 기존/신규 A/B 실측: 실제 그룹 개수에서 check/add/remove 비용, CPU, 할당률, retained heap, timer admission/expiration 수, CIO 실행 이벤트 수 및 p99. 5,000 PC 목표 부하에서 비교. 제거한 단순 제한의 timer admission/expiration 이벤트 수 0 확인. 속도 개선 수치 미실측 상태에서 추정값을 사실로 기록 금지.
- 최종 호출 검색에서 제거 대상 전용 타이머와 generation 경로 0, 사용하지 않는 레거시 클래스/위임 코드 0. 기존 L1 아이템 지연·스킬·효과·패킷 정책 유지.

## 6. 확인 범위

NC의 아이템 시각 저장/조회 및 공유 그룹 관리, RKern 2026-11-29 게임 작업 브랜치의 관련 컨트롤러/PC/아이템/스케줄러 코드를 근거로 작성했다. 전체 아이템 호출 그래프, timing wheel 내부 전체 분기, 실제 DB 영구 저장 요구와 성능 실측은 미확인이다. 구현 전에 최신 checkout에서 검증한다. 이번 커밋은 보고서만 추가하며 실제 소스 수정 및 테스트 완료를 주장하지 않는다.
