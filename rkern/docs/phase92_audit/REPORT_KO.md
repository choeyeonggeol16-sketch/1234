# RKern 92차 — 전체 방송 실패 경계 재감사 및 미니게임 진행성 복구

기준: `RKern_NC_NativeBroadcast_Phase91_Regression_Fixes_FULL_SRC_20261117.zip`.

## 실제 수정한 production 파일 (정확히 3개)

- `src/l1j/server/server/model/L1HauntedHouse.java`
  - 공통 SEND 실패/소스 획득 실패/소스 반환 예외가 경기 준비 실패 시 1,000 아데나 환급, 퇴장 텔레포트, 참가자 제거를 막지 못하도록 알림과 상태 처리 분리.
  - 일반 시작 알림, 카운트다운 알림, NPC 안내문도 동일한 실패 격리. 정상 수신자 순회와 송신→보상·텔레포트 순서는 유지.
  - 최소 인원 미달인데 참가자가 0명인 경우에도 `STATUS_NONE`으로 복귀하도록 보강.
- `src/l1j/server/server/model/L1Racing.java`
  - 최소 인원 미달 환급/퇴장, 경기 시작 프레임/게임 목록, 전체 퇴장, 경기 결과/카운트다운/NPC 알림의 SEND 오류를 참가자별로 격리. 방송이 실패해도 다음 참가자 처리를 진행하고 게임 상태 전환을 유지.
  - 무인원 취소 시 `STATUS_NONE` 보장. 정상 송신 시 레이스·게임 목록 순서와 기존 패킷 Writer 유지.
- `src/l1j/server/server/model/L1DeathMatch.java`
  - 최소 인원 미달 환급, 개시 시간 안내, 우승/종료 공통 방송, 참가자별 종료·보상 후 송신, 공통 결과 메시지의 SEND 예외 격리.
  - 송신 실패와 별개로 보상·퇴장·참가자 목록/Count 정리를 진행. 정상 송신 시 기존 순서 유지.

선택적 알림에만 `openGameBroadcast`/`sendGameNotice`/`closeGameBroadcast`를 적용했다. 기존 `CAsyncSendConstBuffer`, `CSharedBroadcast`, `CUserSocket`, CIO, Native DLL, 패킷 내용, 필터 정책, DB 쓰기 로직은 변경하지 않았다. 새로운 워커/큐/공용 Fallback 없음. 실패시 알림 누락 자체는 기록하되 상태 진행은 유지한다.

## 재현/검증

1. **91차 원본**에서 실제 `L1HauntedHouse.readyHauntedHouse` 메서드를 추출해 타입이 있는 Java 모의환경에서 컴파일한 뒤 Native SEND 예외를 주입했다. SEND 예외가 위로 전달되어 해당 참가자의 환급·텔레포트가 건너뛰어지는 실패를 재현했다.
2. **92차 수정본**에서 실제 생산 메서드와 3개의 실패 격리 헬퍼를 추출·컴파일해 모의 실행했다. 각 미니게임에 정상/송신 실패/원본 획득 실패/원본 반환 실패를 주입하고, 환급·퇴장·종료 상태를 검사했다. 추가로 2명 퇴장 연속 처리, 데스매치 2명 보상 및 `Count` 초기화, 무인원 종료, 카운트다운 증가/감소 흐름, 레이싱 게임 목록 예외 이후 참가자 진행을 검사했다. 전체 **19개 실패·정상·경계 시나리오** 통과(아래 테스트 상세).
3. 기존 87~91차 `bash docs/phase91_audit/run_tests.sh` 전체 재실행 성공. Java production 1,710개 구문 검사 오류 0건, 이전 5,000명 모의 수신자·패킷 바이트·FIFO·Native 참조수·이벤트 메서드 실패 주입 검사 통과.
4. `docs/phase92_audit/tests/test_game_notice_failure.py`는 **실제 최신 생산 메서드를 문자열 그대로 추출**해 Java 21 typed stub로 빌드하는 재실행 테스트다. 개인별 패킷 송신과 Native 실패를 강제로 모의하는 테스트이며 실제 Windows 소켓 테스트가 아니다.

### 새 테스트의 검증 케이스 수

- 유령의 집: 정상 / SEND / source open / source close = 4, 카운트다운 SEND 실패 = 1, 무인원 취소 = 1 → 6.
- 펫 레이싱: 정상 / SEND / source open / source close = 4, 2인 퇴장 SEND 실패 = 1, 게임 종료 상태 SEND 실패 = 1, 무인원 취소 = 1, 게임 목록 알림 실패 = 1 → 8.
- 데스매치: 정상 / SEND / source open / source close = 4, 2인 종료 보상·퇴장과 공통 결과 방송 실패 = 1 → 5.
- **합계 19개**.

## 완료하지 못한 검증

- **외부 의존성 JAR 포함 전체 Java 타입 컴파일:** 제공된 ZIP에 의존 라이브러리 묶음이 없어 수행하지 못했다. 따라서 해당 항목을 PASS로 보고하지 않는다.
- **Windows IOCP·실제 CTransport.dll·MySQL·인게임 이벤트 실행·5,000명 실동접:** 이 Linux 컨테이너에서 수행하지 못했다.
- 현재 검증이 모든 게임 방송의 런타임 버그 부재를 보장하지는 않는다. 테스트 가능한 원본 기준과 운영 전 필요 검증을 분리한다.

## 테스트 재실행

```bash
bash docs/phase91_audit/run_tests.sh
python3 docs/phase92_audit/tests/test_game_notice_failure.py
```

테스트 실행 로그: `docs/phase92_audit/regression.log`, `docs/phase92_audit/failure_injection.log`.
