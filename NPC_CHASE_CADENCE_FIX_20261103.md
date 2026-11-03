# NPC 추적 중 800ms 강제 멈춤 제거 — 2026-11-03

기준: 공개 main 06e4ade, 직전 NPC 공격 타깃 전환 수정 포함.
Java 변경은 src/l1j/server/server/model/npc/ai/action/SetMoving.java 한 파일입니다.

확인한 원인: tickInternal은 경로 탐색 후 한 칸 이동에 성공했는데도
consumeInitialChaseAstarTransition이 true이면 정상 보행 간격을 최소 800ms로 덮어썼습니다.
그 최초 표시가 ThreadLocal PathFinder에 있고 NPC ID & 4095 슬롯에 저장돼 있어,
작업 스레드가 바뀌거나 4096 차이의 NPC ID가 같은 슬롯을 사용하면 재설정될 수 있습니다.
같은 타깃/좌표를 계속 추적해도 이 대기는 다시 나타날 수 있으며, 타깃 상실과 별개입니다.

수정: 이동 성공 후 최초 표시만 소진하고 보행 간격은 유지합니다.
800ms는 경로 계획 구간 상한에 계속 사용합니다. 길막/경로 실패의 재시도,
충돌·예약 검증, 스킬/행동 딜레이, 타깃 판정과 어그로는 변경하지 않았습니다.

검증:
- 실제 tickInternal 및 실제 PathFinder 최초 표시 메서드로 5개 회귀 시나리오 통과.
- 100/150/250/1000ms 보행 속도, NPC ID 충돌(1/4097), 실제 스레드 3개 교대,
  표시 소진, 경로 실패/막힌 이동의 기존 조건 검사.
- 원본은 1개 통과 / 800ms 대기 관련 4개 실패. 수정본은 5개 모두 통과.
- 종전 test_chase_interval의 '800ms를 기다려야 한다' 검사를 정상 보행 속도 유지 검사로 변경했습니다.
- 새 검사와 기존 이동·우회·추월·공격 타깃 전환·AI 검사, 총 13개 스크립트 모두 통과했습니다.
  직전 공격 타깃 전환 15개 시나리오도 포함합니다. 원시 결과는 validation에 있습니다.
- 전체 Java 1,701개 타입 컴파일 결과도 validation/chase_cadence_full_compile.log에 있습니다.

월드/경로 생성/이동 경계는 결정적인 테스트 대체 객체를 사용했습니다.
실제 서버에서 관찰한 멈칫함의 유일한 원인으로 확정한 것은 아닙니다.
확인 가능한 인위적 대기를 제거한 수정이며 실제 클라이언트 장면에서 재확인이 필요합니다.

적용: 서버 소스/JAR를 백업하고 PATCH ZIP의 SetMoving.java를 교체한 뒤
JDK 21 / UTF-8 / --enable-preview로 Clean/Rebuild하세요. DB 변경은 없습니다.
아이템 리팩토링 V2 사용 중에도 한 파일 패치를 적용할 수 있습니다.
FULL_SRC는 공개 main 기준이고 미게시 아이템 V2 전체를 포함하지 않습니다.

재현: python3 tests/npc_body_block/test_chase_pause_rearm.py
원본 비교: --baseline-root 에 수정 전 프로젝트 루트를 지정합니다.
전체 검사 목록/상태: validation/chase_cadence_test_summary.json
