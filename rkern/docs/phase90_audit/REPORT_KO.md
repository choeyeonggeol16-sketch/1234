# RKern 90차 — 89차 잔여 간접 방송 최종 재검수 및 안전한 중복 원본 통합

**기준:** RKern_NC_NativeBroadcast_Phase89_Full_Audit_Fixes_FULL_SRC_20261116.zip

## 생산 코드 변경 (Java 4개 파일, 9개 송신 경로)

1. `MJLFCObject.java` — LFC 양 팀 공통 알림 `closeForGM`, `waitCount`의 준비·초 카운트·시작, `checkSecond`의 종료 카운트에 공유 원본을 하나만 사용한다. `_red` 순회 후 `_blue` 순회, 리스트 중복 요소와 각 참가자별 FIFO를 변경하지 않는다. `close()`는 레드팀 알림 **후** 블루팀 사망·피해 점수 계산 **후** 블루팀 알림이라는 기존 실행 순서를 유지한 상태에서 동일 원본을 공유한다. 수신자 내용은 변경하지 않는다.
2. `MJLFCArenaTower.java` — 종료 공통 알림의 레드/블루 두 소스를 한 소스로 통합.
3. `MJLFCWarSquareTrap.java` — 종료 공통 알림의 레드/블루 두 소스를 한 소스로 통합.
4. `CS_LeavePledge.java` — `leaveClanBoss()`의 SQL COMMIT 뒤, 같은 `SC_ServerMessage` 269 / `playerName` / `clanName` 패킷을 혈맹원마다 새로 직렬화하던 코드를 하나의 공유 Native 원본으로 변경. 기존 `notify(member) -> ClearPlayerClanData(member)` 순서를 유지하며 개인별 예외는 격리한다. 공동 원본 해제 중 실패하더라도 커밋 후 매치메이킹/엠블럼 정리를 막지 않게 별도 기록한다.

새로운 워커·큐·전송 계층·Native ABI는 추가/수정하지 않았다. 개인별 메시지·타깃 ID·피해 처리·팀별 상태 변경은 수정하지 않았다.

## 잔여 간접 방송 검사

89차 `CLASSIFIED_567_LOOP_SITES.tsv`의 567건은 AST 반복문 **후보**로서, 구형 방송 567건이 아니다. 90차에서는 해당 분류를 다시 대조하여 실제 반복 직렬화의 잔여 사례인 LFC 양 팀 메시지 및 혈맹 해산 후 공통 알림을 추가로 교체했다. SC 패킷 디렉터리의 `broadcast*` 메서드 135개를 소스 범위 정적 재검색하여, `for/while` 수신자 반복 루프를 갖고도 `CSharedBroadcast`를 전혀 호출하지 않는 메서드는 0개였다. 이 검색은 메서드 구현에 대한 lexical 검사일 뿐, 모든 간접 호출 경로나 reflection을 포함한 완전성 증명은 아니다.

반대로 `LFCMessages` 개별 `sendSystemMsg`, NPC 이벤트별 `broadcastNpc`, `SC_SkillSound.broadcast`, `SC_War.broadcast*`, `L1World.broadcastServerMessage`는 내부 또는 호출 대상이 이미 공유 송신이거나 특정 이벤트별 별개의 패킷이라는 점을 확인했으므로 원본을 억지로 합치지 않았다.

보류/보존: 오림 트랩에서 플레이어별 난수 이펙트 및 반복 시퀀스, 발라카스 개별 오브젝트 ID 송신, NPC·스킬 사용자별 대상 정보, LFC 개인별 실패 통지, 혈맹원별 상태/DB 트랜잭션 등은 각각 수신자별 내용·실행 순서가 달라 공유 대상이 아니다.

## 검증

- 87차 회귀: 실제 production 공유 송신 구현 + 테스트 소켓, AOI/맵/팀/중복/예외/풀 반환, 5,000명 **모의** 수신자 × 2 프레임 통과.
- 88차 SC 패킷 직접 송신 대 Writer 바이트 검증 40,000건 통과.
- 89차 ProtoWriter/S_EVENT 바이트 검증 90,000건, Server/SystemMessage 40,000건 통과. 269 / 한글/두 문자열 필드는 동일 production `SC_ServerMessage.nativeWriter` 구성에 해당한다.
- 90차 새 테스트: 실제 production `CSharedBroadcast.sendWrittenGroups()` + Native 풀 모의 소켓을 사용하여 레드→블루 순서, 중복 수신자 보존, 끊긴 소켓 제외, 원본 인코딩 1회, 마지막 수신자 완료 후 참조수 1회 반환을 검증했다.
- production Java 1,710개 JavaParser 구문 검사 오류 0건 (87차 스크립트).
- 원본 89차 ZIP 대비 production 변경 Java 4개, ZIP CRC 및 변경되지 않은 파일 SHA256 대조.

**미검증:** 외부 JAR 포함 전체 production 타입 컴파일, 실제 MySQL/게임 상태, Windows IOCP, 5,000명 실동접 장시간 부하. 이 정적·모의 검증만으로 전 서버 구형 방송 0개나 무버그를 증명하지 않는다.

재실행: `bash docs/phase90_audit/run_tests.sh` (Java 21; FFMs preview 활성화).
