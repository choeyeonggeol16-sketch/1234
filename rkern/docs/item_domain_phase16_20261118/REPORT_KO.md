# RKern 아이템 기능 분리 16차 (2026-11-18)

## 기준 및 수정 범위

- 기준: `RKern_ItemFunction_DomainSeparation_Phase15_FULL_SRC_20261118.zip`
- 범위: `src/l1j/server/server/model/item/function/` 패키지 내 생산 Java 5개 수정, 15개 신규 클래스.
- 서버 공통 네트워크, 방송, NPC, DB, CIO, 스레드·큐 구현은 수정하지 않았습니다.

## 실제 분리

1. **`RecruitStarterKit`**: 기존 8개 직업별 장비 지급 목록을 `RecruitStarterWarriorKit`, `RecruitStarterKnightKit`, `RecruitStarterDragonknightKit`, `RecruitStarterCrownKit`, `RecruitStarterWizardKit`, `RecruitStarterBlackwizardKit`, `RecruitStarterElfKit`, `RecruitStarterDarkelfKit`으로 분리. `RecruitStarterItemGrant`에 아이템 생성·인벤토리 검사·저장·축복 적용·서버 메시지를 담당하는 기존 `봉인템` 메서드를 이동. 기존 `if` 8개의 순서 및 독립 조건을 유지했습니다. **스타터 패키지를 먼저 소모하고 지급하는 기존 순서는 의도적으로 변경하지 않았습니다.** 
2. **`PassiveSkillBooks`**: `PassiveSkillLearningRules`에 27개 불변 습득 조건 및 아이템 ID 라우팅, `PassiveSkillLearningEffects`에 27개 습득 후 효과 콜백을 분리. 기존 `canLearn` 검사 뒤 `learn` 호출 구조와 Runnable 생성 지점을 유지했습니다.
3. **`PolymorphScrollEffects`**: `RankingClassPolymorphNames`, `MythicClassPolymorphNames`, `AlternateRankingPolymorphNames`에서 세 종류의 캐릭터 직업·성별별 이름을 선택. 이름 변환 후 랭킹 권한 검사, 원형 복원 및 실제 변신 실행 순서를 보존. 기존 이름/성별 분기에 존재하는 특수 문자열들도 변경하지 않았습니다.
4. **`AinBlessingItems`**: `AinBlessingGemConsumables`에 드래곤 보석·에메랄드·토파즈의 사용 조건, 효과, 메시지 처리 메서드를 이전. `BlessingConsumables`는 새 클래스를 직접 호출하며, 기존 `AinBlessingItems.useGemCase` 공개 메서드는 ABI 호환용 단일 위임 메서드로 유지. 실제 `consume`의 `L1InventoryTransaction` debit, SQL `UPDATE accounts`, CacheD account FIFO, `CDB.RequestUseItem` 완료 후 효과 적용 및 `atMaximum` SQL 조회는 **한 줄도 변경하지 않았습니다**.

## 검증

- Java 21 전체 생산 소스 **1,958개 구문 파싱, 오류 0건**.
- 기존 15차 / 14차 / 13차 / 12차 / 10차 도메인 회귀검사, 11차 무기 강화 타입 컴파일 테스트, 방송 114차 회귀검사 통과 (`run_tests.sh` 경로).
- 원본 15차와 분리 후의 동일 Java 21 테스트 스텁에서 실행 비교: 스타터 지급 **6,144개**, 패시브 **1,920개**, 변신 **78,408개**의 결과/실행 로그 비교 일치. 이 세 항목은 본 단계 코드 생성 직후 실행했으며, 이후 해당 클래스와 호출부를 수정하지 않았습니다.
- 아인 보석 조건 및 후속 효과 **7,168개** Java 21 타입 컴파일·원본 비교 일치 (재실행: `test_phase16_gems.py`).
- 총 **93,640개 비교 입력** (6144+1920+78408+7168). 이것은 테스트용 모델에서의 원본 대비 실행 비교이며, 실제 게임 / DB 부하 실험 건수가 아닙니다.
- 원본 15차 메서드·분기 15개 영역의 SHA256 해시와 생산 본문 동일성, 호출 순서, 27개 패시브 조건·효과 수, 기존 SQL/영속 트랜잭션 본문 무변경 확인 (`test_phase16_structure.py`).
- ZIP 전체 CRC, 중복 경로, 변경 5개 생산 Java 외 이전 파일 바이트 동일성 검증.

## 미검증 및 기존 동작상의 유의점

- 전체 외부 의존성 타입 컴파일, 실서버 클라이언트 아이템 사용, 실제 MySQL/CacheD 장애·재접속/ACK 유실 검증은 수행하지 않았습니다.
- 스타터 지급은 원본처럼 패키지 소모 후 개별 아이템을 저장하며, 인벤토리 실패 시 이미 소비한 패키지를 되돌리는 트랜잭션 기능은 **이번 분리에서 추가하지 않았습니다**. 이를 원자 거래로 변경하려면 별도 작업·DB 검증이 필요합니다.
- 특수 아이템의 다른 혼합 라우터 및 아이템 전 영역 검증이 완료된 상태는 아닙니다.
