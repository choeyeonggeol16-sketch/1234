# RKern 스킬 습득 관리 전면 교체 지시서 — NC OFF 구조

작성일: 2026-11-29 (KST)
대상: RKern 리니지 1의 스킬 습득/보유/삭제/복원/저장 전 경로
RKern 확인 기준: [main dc157f61](https://github.com/choeyeonggeol16-sketch/1234/commit/dc157f61e57c23362f9ac85afcfa216dc69db0aa)
NC 비교 기준: [Open_LU4_L2OFF abbb2e5](https://github.com/luannbr/Open_LU4_L2OFF/tree/abbb2e5f474d40cf12539a8e97fddc52474ed95e) (OFF 복원 프로젝트, 현재 공개 소스에서 확인한 구현 기준).
이번 커밋은 보고서만 작성한다. 실제 구현에 착수할 때는 최신 작업 브랜치/커밋을 다시 확인하고 다른 작업을 되돌리지 않는다.

## 1. 작업 결론

기존 SkillsTable + SkillCheck + L1PcInstance의 분산 습득 관리 구조를 폐기하고 NC식으로 책임을 재배치한다.

- CSkillDB: 전역 스킬 정의 및 공유 메타데이터.
- CSkillAcquireDB: 전역 습득 조건과 습득 경로별 조회.
- CCreature: 보유 스킬의 단일 소유자, AcquireSkill / DeleteAcquireSkill / HaveSkill / GetAcquiredSkillLevel.
- User(현 L1PcInstance 역할): 캐릭터 전용 습득 정책, 비용 처리, 패시브 반영/스킬 목록 동기화, CacheD 요청.
- CacheD: 영구 저장, 복원, 경제 트랜잭션의 확정 및 실패 처리.

이름은 NC에서 실제 확인된 이름을 우선 사용한다. 기존 Java CCreature 소유 구조와 패키지부터 확인하고 새 상위 클래스나 중복 User 클래스를 만들지 않는다. 아래 명칭은 이관 대상 책임을 나타내며 기존 동일 책임 클래스가 있으면 통합한다.
NC의 L2 SP/서브클래스/종족/혈맹 등 게임 규칙을 그대로 가져오는 작업이 아니다. L1의 스킬 ID, 기존 클래스 제한/레벨/마법서/재화/자동 습득/패시브/클라이언트 프로토콜을 보존하면서 실행 구조를 교체한다. 전투 데미지·스킬 효과 공식, 일반 네트워크, CIO 및 CacheD 공통 실행기 구조는 범위 밖이다.

## 2. NC 실제 소스 구조와 근거

### 2.1 스킬 정의와 습득 규칙 분리

[SkillAcquire.h L86–150](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/SkillAcquire.h#L86-L150),
[SkillAcquire.cpp L27–165](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/SkillAcquire.cpp#L27-L165).

- CSkillAcquireDB::Load는 skillacquire.txt를 읽는다. 스킬 정의는 g_SkillDB에서 별도 조회한다.
- SkillAcquireInfo는 스킬 ID/레벨, 직업·종족·기타 습득 타입, 필요 레벨, 필요 아이템 및 수량, SP, 퀘스트, 자동 습득, 선행·대체 스킬을 보유한다.
- 직업+스킬 ID+레벨로 단건 조건 조회, 직업+스킬 ID로 최소 레벨 조회, 직업별 vector로 습득 후보 조회. 기타 타입/종족별 후보도 별도 인덱스.
- 매번 스킬 전체 테이블을 순회하여 조건을 찾지 않는다. 목록 생성은 해당 분류 후보를 순회한다.

### 2.2 CCreature가 보유 스킬을 소유

[Creature.h L1680–1691](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Creature.h#L1680-L1691),
[Creature.h L2558–2559](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/Creature.h#L2558-L2559),
[SkillReuseDelay.h L29–37](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/SkillReuseDelay.h#L29-L37).

NC의 소유 자료구조는 std::map<int, AcquiredSkillInfo> m_AcquiredSkills 및 CYieldLock m_AcquiredSkillsSect. AcquiredSkillInfo에는 ID, level, sublevel, reuse delay group, DB 저장 플래그가 존재한다. Java에 레벨/sublevel 개념이 불필요하면 무조건 복제하지 말고 실제 데이터와 프로토콜에 맞춘다.

[CreatureServer.cpp L6300–6462](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/CreatureServer.cpp#L6300-L6462),
[L6874–7015](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/CreatureServer.cpp#L6874-L7015),
[L7214–7218](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/CreatureServer.cpp#L7214-L7218),
[L7309–7343](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/CreatureServer.cpp#L7309-L7343).

- AcquireSkill은 보유 스킬 락을 획득한 뒤 _AcquireSkill을 호출한다.
- _AcquireSkill은 g_SkillDB에서 정의 확인 → 부적절한 습득 거부 → ID 기반 삽입 → 기존 스킬이면 replace 또는 실제 상향 조건 검사 → 이전 패시브 제거 예약 및 관련 재사용 처리 → 레벨/서브레벨/저장 플래그 변경 → 신규 패시브 적용 예약 순서다.
- HaveSkill / GetAcquiredSkillLevel은 map.find로 조회한다. O(log N), 전체 목록 순회 없음. HaveSkill(id, level)은 정확한 레벨 일치 검사다.
- DeleteAcquireSkill은 락 내부에서 항목을 찾아 삭제 전 패시브 해제 처리 후 erase한다.
- AcquireSkills는 묶음에 한 번 락을 잡고 후보의 필요 레벨/자동 습득/종족 조건을 검사한다. NC의 자동 스킬과 멘토 계열은 해당 경로에서 저장 플래그를 달리한다.
- DeleteAllAcquireSkills는 영구 스킬을 보존하는 모드를 갖고 있으며 정의의 저장 플래그를 이용한다. 실제 목록 재구축 시 전체 목록을 순회하는 것은 정상이다.
- [CreatureServer.cpp L6643–6681](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/CreatureServer.cpp#L6643-L6681)에서 ValidateSkillList는 액티브/패시브/토글/오라 목록을 분류하고 재설정한다.
- [UserSkill.cpp L2163–2175](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/UserSkill.cpp#L2163-L2175)에서 User::ValidateSkillList는 상태에 맞춰 목록을 재구성하고 SendSkillList를 호출한다.

### 2.3 패킷 및 아이템, CacheD 연동

[UserSocket.cpp L1944–2017](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/UserSocket.cpp#L1944-L2017): RequestAcquireSkill은 입력/캐릭터/습득 타입 및 전직 경쟁 플래그를 검사하고 User::OnRequestAcquireSkillPacket으로 위임한다. 해당 User 메서드의 전체 내부 분기 및 비용 확정 순서는 이번 확인에서 확보하지 못했으므로 추정으로 NC 원본 로직이라고 주장하지 않는다.

[UserSkill.cpp L837–844](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/UserSkill.cpp#L837-L844): AcquireSkillByItem은 공통 AcquireSkill로 진입하며 이 경로에서는 replace=false, saveToDatabase=false를 전달한다. 이는 장비 등 파생 스킬의 영구 습득과 구분해야 하는 근거다.
[UserSkill.cpp L898–905](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/UserSkill.cpp#L898-L905): SendAcquiredSkillToDB는 캐릭터 저장 및 일반 습득 스킬 저장 요청을 보낸다(특수 honors 예외).
[DB.cpp L490–512](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/DB.cpp#L490-L512): RequestSaveAcquiredSkills는 변경 스킬 묶음을 CacheD로 전달한다.
[DB.cpp L7033–7044](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/L2Server/src/DB.cpp#L7033-L7044): SendAcquiredSkill은 캐릭터 DB ID, 스킬 ID, 레벨, 서브레벨, 저장 관련 플래그, 서브잡 ID를 전달한다.
[CacheD/src/User.cpp L6654–6702](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/CacheD/src/User.cpp#L6654-L6702), [L6828](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/CacheD/src/User.cpp#L6828), [L7213](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/CacheD/src/User.cpp#L7213): CacheD::CUser의 SetAquireSkill / DeleteAquireSkill / SendAquireSkill 진입점 확인. NC 단건/묶음 저장의 존재가 모든 비용과 스킬의 원자성을 증명하는 것은 아니다. RKern에서는 기존 receipt 원자성을 유지하거나 강화한다.

## 3. RKern 현행에서 실제 확인한 문제

기준 커밋의 직접 확인 파일:
- [L1PcInstance.java L1640–1675](https://github.com/choeyeonggeol16-sketch/1234/blob/dc157f61e57c23362f9ac85afcfa216dc69db0aa/rkern/src/l1j/server/server/model/Instance/L1PcInstance.java#L1640-L1675), [L6298](https://github.com/choeyeonggeol16-sketch/1234/blob/dc157f61e57c23362f9ac85afcfa216dc69db0aa/rkern/src/l1j/server/server/model/Instance/L1PcInstance.java#L6298): ArrayList<Integer> skillList, contains/add/remove, 별도 mastery lock; 습득 및 삭제는 user state lock도 사용. 조회 O(N), 박싱 가능.
- [SkillCheck.java L28–93](https://github.com/choeyeonggeol16-sketch/1234/blob/dc157f61e57c23362f9ac85afcfa216dc69db0aa/rkern/src/l1j/server/server/SkillCheck.java#L28-L93): 전역 HashMap<Integer,List<Integer>>. AddSkill(List)는 전달된 리스트 참조를 그대로 저장하고, 단건 추가/조회는 선형 순회. 클래스 자체에는 동기화가 없다. 실제 모든 호출자의 스레드 소유는 추가 조사 필요. PC와 전역의 두 조회/갱신 경로 및 불필요한 전역 보존을 없애야 한다(리스트 공유 여부와 실제 retained heap은 실측).
- [SkillsTable.java L25–59](https://github.com/choeyeonggeol16-sketch/1234/blob/dc157f61e57c23362f9ac85afcfa216dc69db0aa/rkern/src/l1j/server/server/datatables/SkillsTable.java#L25-L59), [L109–175](https://github.com/choeyeonggeol16-sketch/1234/blob/dc157f61e57c23362f9ac85afcfa216dc69db0aa/rkern/src/l1j/server/server/datatables/SkillsTable.java#L109-L175): 정의 로딩과 캐릭터 습득/삭제/조회가 같은 클래스에 혼재. spellMastery는 CacheD ordered job에서 SELECT 후 INSERT, 등록 직후 월드에서 PC를 재조회하고 PC/SkillCheck 양쪽을 갱신. spellLost도 DB 작업 등록 후 양쪽 갱신. 이 코드에서 DB 커밋 ACK를 받은 뒤 갱신한다는 근거는 없다. 저장 job 수락은 영구 확정과 다르다. 온라인 spellCheck는 이미 메모리 조회지만 오프라인 fallback은 호출자에서 직접 JDBC SELECT.
- [SkillsTable.java L186–205](https://github.com/choeyeonggeol16-sketch/1234/blob/dc157f61e57c23362f9ac85afcfa216dc69db0aa/rkern/src/l1j/server/server/datatables/SkillsTable.java#L186-L205): 일부 아이템→스킬 ID switch. 조건 DB 쪽으로 이동 가능한지 조사하되 기존 매핑 그대로 유지.
- [CacheD.java L1070–1077](https://github.com/choeyeonggeol16-sketch/1234/blob/dc157f61e57c23362f9ac85afcfa216dc69db0aa/rkern/src/l1j/server/server/storage/CacheD.java#L1070-L1077): 기존 ordered DB 제출 API 존재. 별도 스레드/큐 만들지 않는다.

위는 직접 읽은 기준 main의 확인 사항이다. GitHub 코드 검색이 해당 저장소에서 결과를 반환하지 않아 전체 호출자 전수 확인은 아직 하지 못했다. 아래는 구현자가 반드시 수행해야 하는 범위이지, 모든 파일이 현재 미수정이라고 단정하는 목록이 아니다.

## 4. 구현 단계 — 부분 호환만 남겨 놓지 말 것

### P1. 전역 정의와 습득 규칙 분리
1. 최신 checkout에서 스킬 정의, 마법서/아이템→스킬 대응, 클래스별 습득 조건, 자동 습득 및 별도 패시브 시스템의 데이터 원천을 추적한다.
2. SkillsTable의 정의 책임을 CSkillDB로 이관하고 L1Skills의 공유 정의 역할은 필요시 CSkillInfo로 정리한다. 이미 동일 역할 클래스가 있으면 통합한다. 기존 primitive TIntObjectHashMap을 활용하고 읽기 hot path에서 객체/박싱을 만들지 않는다. 리로드는 새 데이터 완성·검증 후 안전하게 교체하며 실패 시 이전 데이터 유지.
3. CSkillAcquireDB에 스킬 ID 또는 직업+스킬 ID 인덱스, 아이템 ID→습득 규칙, 직업별 후보 배열을 구축. ID/클래스가 희소하면 primitive map 또는 압축 배열 사용. 기존 SQL/config/하드코딩 데이터를 동일 결과로 이관; L2 전용 규칙 추가 금지. 파일 포맷·DB 스키마 변경은 필요성이 입증된 경우만 한다.

### P2. 보유 상태 단일화
1. 기존 CCreature 소유 및 lifecycle을 확인하고 실제 스킬을 가진 객체의 보유 자료구조를 거기에 통합. NPC 고정 스킬 데이터가 클래스 상수로 충분하면 PC형 mutable 목록을 NPC마다 중복 생성하지 않는다.
2. 공개 AcquireSkill, DeleteAcquireSkill, HaveSkill, GetAcquiredSkillLevel을 단일 진입점으로 사용. 필요하다면 owner/lock 내부 구현을 _AcquireSkill 등으로 분리하되 래퍼 남발 금지.
3. Java에서 NC의 std::map을 TreeMap<Integer,...>로 직역하지 않는다. 실제 스킬 ID 분포와 최대값 측정 후, 작은 밀집 범위에서는 long[] 비트셋(영구 보유 여부만 필요한 경우), 희소 ID 또는 레벨 필요 시 기존 Trove primitive map/정렬 int 배열을 선택한다. 실제 필요 정보만 저장. 중복 소유 리스트, 박싱, 조회당 new, temporary collection 제거. 단건 조회는 전체 순회 금지.
4. 기존 CIO owner에서 mutation을 직렬화한다. 외부 스레드 읽기는 검증된 lock 또는 안전한 snapshot/가시성 규칙을 사용한다. owner 없는 락 제거 금지, user state lock/mastery lock 순서와 재진입 점검. 새 worker/queue/timer 금지.
5. SkillCheck의 모든 호출을 단일 CCreature/User API로 바꾸고 클래스/전역 map 삭제. L1PcInstance.skillList, setSkillMastery/removeSkillMastery/isSkillMastery 레거시 API는 전 호출 이관 후 제거. 기존 공유/상속 상태 및 로그아웃 해제 참조도 제거. 임시 호환 래퍼는 최종 산출물에 남기지 않는다.

### P3. 모든 습득/삭제 진입점 통합
- 마법서/기술서 사용, NPC 학습, GM 지급/회수, 퀘스트/보상, 캐릭터 생성/레벨업, 직업 변경, 로그인/재접속, 스킬 초기화/대체, 혈맹/장비/변신 등 기존 실제 경로를 inventory화한다. 존재하는 경로만 이관.
- 패킷/아이템 실행부에서 직접 리스트 추가/삭제, 직접 SQL, 임의 패시브 스탯 증가 금지. User의 습득 처리 경로로 모은다. 서버가 정의와 조건을 재검증하고 클라이언트 입력을 신뢰하지 않는다.
- 비용 검사와 중복 습득 검사는 같은 소유 순서 내에서 처리한다. 이미 습득한 동일 스킬은 중복 소비/패시브 중복 적용 없이 no-op 또는 기존 규칙대로 거부.
- 영구 습득과 장비/혈맹/변신에서 유래한 파생 스킬을 분리한다. 동일 ID의 여러 소스가 동시에 존재하는 경우 한 소스 해제 시 다른 유효 소스가 살아 있어야 한다. 무조건 DB 행 삭제 금지. 기존 버프/쿨다운/스킬 사용 정책과 보유 여부의 의미를 혼동하지 않는다.
- 삭제는 스킬 데이터 제거 및 패시브/장비/스탯 해제, 목록 동기화가 동일 성공 결과에 종속되도록 한다. 실제 규칙상 필요하지 않은 쿨다운 초기화는 추가하지 않는다.
- CCreature의 ValidateSkillList는 필요한 경우만 실행하고 User에서 기존 L1 클라이언트 패킷으로 갱신. 개별 습득마다 전체 목록 재구축/전체 인벤토리 순회가 필요한지 실제 호출을 조사. 단건 변경에는 증분 갱신을 우선하고 로그인/직업 전환 등 batch에서 1회 목록 재구축.

### P4. CacheD 영구 저장 및 경제 원자성 (필수)
- 기존 owner lane 및 L1InventoryTransaction/CacheD receipt 경로의 최신 구현부터 확인한다. 스킬 습득을 위해 아이템/아데나를 소비할 때는 소비+영구 습득 행을 같은 DB 트랜잭션/receipt에 결합한다. 같은 작업 ID 재시도는 효과가 1회만 발생하도록 한다.
- COMMIT 성공 전 성공 패킷/패시브 확정/영구 스킬 사용 가능 상태를 공개하지 않는다. 비동기 pending 중 중복 요청과 로그아웃/직업 변경을 제어. ACK 유실이면 receipt 조회로 확정 결과를 복구하고 단순 재실행으로 이중 차감 금지. old session ACK가 new session을 덮지 않게 기존 세션/owner 수명 검증.
- CIO 및 네트워크 worker에서 JDBC 및 Future.get/join 대기 금지. 오프라인 GM 조회/지급도 CacheD 담당 경로로 이관. 온라인의 영구 상태 조회는 메모리만 읽는다.
- character_skills의 실제 PK/유니크 인덱스 및 데이터 중복을 확인한 후 적합한 idempotent insert/upsert 또는 기존 transaction API를 사용해 SELECT→INSERT 왕복 제거. 오류를 무시하는 INSERT IGNORE 일괄 적용 금지. 이름/active/time 필드는 실제 복원/버프 의존성 확인 없이 삭제하지 않는다. DDL 변경 시 백업/이관/롤백 및 중복 검증.
- DB 로딩 시 단일 bulk 조회 → owner에서 한 번 보유 스킬 복원 → 파생 스킬 결합 → 패시브 및 스킬 목록 동기화. 복원에서 비용 차감, INSERT 재발행, 습득 성공 알림 금지. 실패한 로드를 빈 목록으로 처리해 기존 영구 스킬을 잃게 하지 않는다.
- 삭제/직업 전환/초기화는 해당 범위의 묶음 작업 및 원자적 저장으로 처리한다. 관계된 경제 정보와 성공 응답은 receipt 이후.

### P5. 전 호출 이관과 레거시 정리
최신 전체 소스 및 SQL에서 검색하고 각 호출의 실제 역할, owner, 영구/일시 여부 및 이관 함수 매핑을 보고한다.
```sh
rg -n 'SkillCheck|SkillsTable|spellMastery|spellLost|spellCheck|setSkillMastery|removeSkillMastery|isSkillMastery|skillList|character_skills' rkern
rg -n 'AcquireSkill|DeleteAcquireSkill|HaveSkill|GetAcquiredSkillLevel|ValidateSkillList|SendSkillList|MJPassive|MJItemSkill' rkern
```
동일 이름의 다른 역할(예: NPC 시전 후보 skillList, 효과 중인 버프)은 무차별 삭제 금지. 스킬 사용 허가, 패시브, 패킷 목록, GM/직업 변경/로그인/로그아웃/아이템 사용까지 이관. character_skills DML은 최종 CacheD 소유 경로 외에 남기지 않는다. 정의 로더와 저장 서비스의 책임을 다시 섞지 않는다. 레거시 클래스/호출/중복 상태/불필요한 호환 코드 제거를 확인한다. 파일 변경 전후 diff 및 보존 항목 명시.

## 5. 검증 및 종료 조건

- Java 21 전체 의존성 타입 컴파일, 테스트 실행. 구문 검사만으로 완료 주장 금지.
- 스킬 보유/조회/삭제, 중복 지급, 없는 스킬, 선행/직업/레벨 제한, 비용 부족, 모든 마법서/기술서, 자동 습득, GM, NPC, 패시브 재계산, 변신/장비/혈맹 출처 충돌, 직업 변경, 로그인/로그아웃/재접속, 목록 패킷 기존 동작 비교.
- 동일 캐릭터 중복 요청, 습득 vs 삭제/직업 변경/로그아웃, 외부 스레드 읽기 경쟁, 이전 세션 ACK, owner 재진입, DB queue rejection, SQL rollback, ACK 유실 후 재시도, CacheD 중단/재시작, 서버 재시작 후 DB/메모리/재화 일치 확인.
- 동일 조건 기존/신규 A/B: 1/32/128/256개 및 실제 최대 스킬 수, 보유/미보유 조회 및 습득/삭제, 5,000 PC와 실제 NPC 규모에서 retained heap, 할당률, CPU/ns per query, p95/p99, DB query 수, CIO 블로킹 및 lock contention 측정. 벤치마크 가상 수치를 결과로 기록하지 않는다.
- 최종 완료 기준: 온라인 단일 소유 상태, 전역 SkillCheck 제거, 조회 선형 순회 및 박싱 제거, JDBC 직접 호출 제거, 영구 습득/비용 정확히 한 번 확정, 로그인/패시브/패킷 동작 보존, 전체 호출 이관 및 검증 완료. 미검증 사항은 따로 표시한다.
- 최신 코드와 변경 이력 유지. 사용자가 요청하지 않은 네트워크, DB 공통 구조, 전투 공식, NPC AI 변경 금지.

## 6. 확인 범위

NC OFF 복원 소스의 명시된 클래스·메서드 및 RKern main의 위 4개 직접 확인 파일을 근거로 작성했다. NC의 내부 User 습득 패킷 분기 전체, RKern 전체 호출자 및 최신 별도 작업 브랜치, 실제 DB 스키마·실행 테스트는 미확인이다. 이번 작업은 구조 개편 지시서이므로 생산 코드/DB 수정이나 테스트 통과를 주장하지 않는다. 구현자는 P1 착수 전에 최신 전체 소스를 기준으로 이 항목들을 확인하고 보고서의 가정과 다른 부분을 수정한다.
