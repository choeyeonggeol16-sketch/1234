# 아이템 사용 처리 리팩토링 195~197차 (2026-11-19)

기준: RKern_ItemOnly_InventoryTransaction_Preflight_MultiPhase192_194_FULL_SRC_20261119.zip

## 195차 - 물약 사용 경로 통합
- 기존 PotionEffects에 HP/경험치/이동/MP·보호 계열의 4개 소형 클래스를 통합.
- HP 회복 아이템의 중복 ID switch를 제거하고 기존 맵/직업/디케이 제한, 효과, COMPLETE/STOP 반환, 영속 완료 콜백을 유지.
- 삭제: HealingPotionConsumables.java, ExperiencePotionConsumables.java,
  MovementPotionConsumables.java, ManaProtectionPotionConsumables.java.

## 196차 - 인형 잠재력 해제 통합
- 4개 파일을 DollPotentialRemoval.java 한 클래스로 통합.
- 능력치/저항/전투/HP·MP 등 31개 효과 해제 순서와 모든 상태/아이콘 패킷 순서 유지.
- 반복되는 SC_SPELL_BUFF_NOTI 스칼라 구성 코드를 정적 공통 함수로 변경(새 데이터 객체 없음).
- 삭제: DollVitalDefenseEffectRemoval.java, DollAttributeEffectRemoval.java,
  DollCombatEffectRemoval.java, DollResistanceEffectRemoval.java.

## 197차 - 아이템 재사용 대기시간 계산
- ItemActionAdmission.java에서 Timestamp.getTime() 및 경과 초 계산 반복을 1회로 줄임.
- 거절 조건과 기존 사용자 메시지 문자열 유지.

## 검증
- 원본과 변경 생산 Java를 각각 JDK 21 typed stubs로 컴파일해 효과/패킷 이벤트 30,136개 비교: 완전 동일.
- 원본과 변경 ItemActionAdmission typed 실행 비교 1,344개: 완전 동일.
- 생산 소스 JDK 21 javac parse 1,916개: 오류 0.
- ZIP CRC 및 변경 범위/원본 보존 바이트 대조 통과.

## 범위 및 한계
- 생산 소스 기존 파일 8개 제거, 새 파일 1개 추가, 기존 파일 3개 변경; 순감 7개 클래스.
- DB 거래, NPC, 네트워크, 방송, 게임 확률/수치/아이템 ID 수정 없음.
- 이번 작업은 작은 클래스 통합, 클래스 메타데이터 및 중복 분기/계산 축소 중심이며
  총 heap 할당량/GC 절감률은 JFR 실측하지 않았음.
- 전체 의존성 타입 컴파일, MySQL 장애 및 실제 인게임 검증은 수행하지 않음.
