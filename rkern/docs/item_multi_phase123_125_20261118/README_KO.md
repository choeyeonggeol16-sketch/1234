# RKern 아이템 123~125차 누적 변경 — 2026-11-18

**기준:** 122차 `RKern_ItemOnly_DurableConsumables_MultiPhase118_122_FULL_SRC_20261118.zip`.

## 123차 — 스킬북 원자성

변경 3파일: `SkillBookLearning.java`, `GeneralSpellbookLearning.java`, `WarriorSkillBookItems.java`.

- 흑정령·정령·기사·군주·용기사·환술사 학습, 일반 마법서, 전사 기술서의 영속 DB 등록과 스킬북 1개 차감을 하나의 `L1InventoryTransaction` 영수증으로 묶음.
- CacheD가 기존 소유 lane에서 `characters` 행 FOR UPDATE로 직렬화하고 `character_skills`에 중복 스킬이 없는지 검증해 저장함. DB INSERT가 실패하면 준비한 차감도 롤백.
- 성공 COMMIT 뒤 캐릭터/SkillCheck 숙련도를 게시하고, 스킬 목록 및 소리 출력을 독립 시도. 패킷 실패가 커밋된 거래를 실패로 간주하지 않게 함.
- 일반 마법서에서 누락된 스킬 템플릿 때문에 null 역참조하던 경로와 매 반복 임시 책 이름 문자열 생성 제거; 미일치/누락 스킬은 책을 차감하지 않음.
- 일반 스킬 목록의 원래 30개 필드 및 ELF 속성 와이어 길이 유지.

## 124차 — 대상 변신 지팡이

변경 1파일: `TargetPolymorphWands.java`.

- 대상 변신보다 앞서 차감/충전 잔량을 영속 거래로 확정함.
- 40008·140008의 마지막 충전 시 예전 `removeItem(item)`과 동일하게 남은 인스턴스 전체 차감. 충전이 남으면 기존 영속 charge 필드만 변경.
- 일반형 40410 등은 기존 1개 차감을 영속 소모로 전환.

## 125차 — 부활 주문서

변경 1파일: `ResurrectionConsumables.java`.

- PC 부활 동의 요청 및 NPC 부활을 기존 CacheD 차감 완료 콜백 뒤로 이동.
- PC 동의 패킷 실패는 별도 기록하고 DB 커밋 실패로 간주하지 않음.
- 기존 실패 시 차감되는 대상 처리, 맵/성/대상 제약, NPC 점유 갱신 로직은 그대로 둠. NPC가 커밋 전에 이미 살아났다면 부활을 중복 실행하지 않음.

## 안전성/성능 경계

- HP 회복 포션 `L1HealingPotion.java`는 고빈도 실행 경로. 개별 포션마다 새 영속 거래를 만드는 실험은 DB 업무량/지연을 키우므로 **이번 패치에 채택하지 않았고 122차 소스를 그대로 보존**함.
- `SkillBookLearning`은 저빈도 경제 거래용이며 새로운 워커/큐/스레드를 추가하지 않음.
- 인게임 NPC 부활 후처리/변신 실제 성공, 실제 MySQL/인덱스/다중 세션/재시작 및 전체 서버 의존성 빌드는 **미검증**. 모의 거래 테스트와 타입 검사가 이를 대체하지 않음.
- NPC AI, 네트워크 코어, 방송, CacheD 공통 구조는 변경 없음.

## 회귀 검사

`python docs/item_multi_phase123_125_20261118/run_tests.py`

테스트는 실제 Java `SkillBookLearning`·`GeneralSpellbookLearning` 생산 파일, 두 효과 처리 클래스의 생산 body 및 전사 스킬 학습 생산 메서드 추출본을 Java 21로 타입 컴파일해 실행함. 고의 SQL/패킷 장애·롤백·커밋 전 상태 보존 등 총 26개 assert 조건 검증. SQL 자체는 모의 JDBC 이므로 실 DB 시험은 아니다.

기존 122차 production 소비 관련 Java 21 검사 419,631 + 변신 165개, 116차 감정 주문서 174개, 117차 요리 1,284 + 64개 회귀가 통과함. 전체 `src/` 1,923개 Java 구문 파싱 오류 0. ZIP CRC 및 122차 바이트 비교 별도 검사.
