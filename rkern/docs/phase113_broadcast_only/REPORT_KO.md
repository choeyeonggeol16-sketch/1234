# 113차 — 방송 전용 잔여 엄격 송신 경로 검수 및 선택적 송신 전환

## 기준과 변경 범위
- **111차 FULL SRC**를 원본으로 하며, 112차 수정·테스트까지 이어받은 **누적 113차 전체 SRC**이다.
- 생산 Java **26개**만 변경. `CSharedBroadcast.java` 1개는 방송 전용 코어 확장·실패 격리이고, 다른 **25개**는 기존 `CSharedBroadcast.sendWritten*` 방송 호출문을 대응 `*BestEffort`로 바꾼 것뿐이다.
- 일반 `SEND`/`RECV`/`CIO` 코어, 세션, DLL, NPC AI, DB, 게임 상태 변경 **없음**. 모든 일반 생산 Java 호출부 변경은 기존 방송 API 명칭 뒤에 `BestEffort`를 추가한 것뿐임을 **자동 diff 검사**로 확인.
- 기존 엄격 API는 삭제하지 않으며, 이번에 실제 동작하는 선택적 시각효과·공지 호출부만 안전한 방송 전용 API로 전환한다.

## 112차에서 111차 대비 재현해 수정한 결함
1. 잘못된 선택적 공유 패킷 인코딩을 5,000명에게 5,000번 재시도하던 문제: 원본 생성 실패는 방송 단위에서 한 번 중단, 정상 생성 뒤 소켓 실패는 다음 수신자 계속 처리.
2. 20KiB 광역 방송에서 첫 수신자의 WRITE/연결 조회 오류가 뒤 수신자까지 차단: 동일한 원본을 사용하여 수신자별 오류를 격리.
3. SC 패킷(NPC 대사, 채팅, 시스템, 맵 공지, 문장, 혈맹 전쟁), 정기 공지, 이펙트/보물상자/채팅파티의 엄격 송신으로 뒤 수신자 누락: 방송 호출문만 전환하고 실제 생산 메서드 실패 주입으로 재현·대조.

## 113차 추가 분석·수정
1. `sendWrittenArray` 잔존 호출: 인던 레이드·가문/혈맹 알림·GM 공지·몬스터 보상 공지·UI·트랩 효과 등 **방송 알림만** `sendWrittenArrayBestEffort`로 전환. 아이템 지급·DB 확정·상태 갱신 코드는 그대로.
2. `sendWrittenObjectPlayers` 잔존 호출: 인던 참가자·레이드·보상·얼음 던전·하다인 참가자 알림 등을 `sendWrittenObjectPlayersBestEffort`로 전환. 입력 객체 타입 검사와 중복·배열 순서는 유지.
3. 자기 자신 우선 후 가시 플레이어에게 보내는 광역 효과는 `sendWrittenSelfAndVisibleBestEffort`로, 상자 중첩 AOI는 `sendWrittenNestedVisibleBestEffort`로, 전투장 팀 알림은 `sendWrittenMapTeamBestEffort`로 전환. 수신자별 실패를 격리하되 기존 목록·팀 필터·중복 수신·FIFO 순서 유지.
4. 호출부 정적 전수 검사에서 `sendWrittenRecipients`, `sendWrittenArray`, `sendWrittenObjectPlayers`, `sendWrittenSelfAndVisible`, `sendWrittenNestedVisible`, `sendWrittenMapTeam`의 엄격 전역 방송 호출이 남지 않았음을 확인(메서드 정의는 호환성 위해 유지).

## 검증
- `bash docs/phase112_broadcast_only/run_tests.sh`: 실제 생산 `CSharedBroadcast`/`CAsyncSendConstBuffer`를 typed stub와 함께 Java 21 컴파일 후 source 실패 5,000명·20KiB·SC 패킷 6종·추가 호출부 4종 테스트.
- `bash docs/phase113_broadcast_only/run_tests.sh`: 실제 생산 방송 메서드 Java 21 컴파일 후 자기 자신 우선·중첩 AOI·팀 필터·중복 순서·SEND 실패 격리 검증 + 잔여 엄격 방송 호출 정적 검사.
- 110·111차 대표 회귀: 기존 Native 모의 참조수, AOI/FIFO, 패킷 바이트 호환성, 5,000명 모의 방송.
- 모든 생산 Java 1,710개 `JavacTask.parse()` 구문 검사 **0건 오류**. 이 항목은 외부 의존성까지 포함한 전체 타입 컴파일과 다름.
- 111차 원본에 같은 실패 주입 적용 시 실패하고 수정본에서는 통과하는 로그는 `docs/phase112_broadcast_only/`에 보존.
- ZIP 내용 비교/CRC/재추출 후 테스트가 완료되어야 최종 배포본을 확정한다.

## 아직 검증되지 않은 것
- 실제 Windows DLL / IOCP, 운영 서버 장시간 5,000명, 외부 JAR 포함 전체 Java 타입 컴파일은 **실행하지 않았다**.
- 선택적 공지는 실패 수신자를 건너뛰는 정책이므로 필수 상태 동기화·게임 진행에 필요한 엄격 송신과 혼동해서는 안 된다. 이번 생산 변경은 각 호출문만 교체했다.
