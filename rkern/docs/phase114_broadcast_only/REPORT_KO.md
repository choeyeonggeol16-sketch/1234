# RKern 방송 전용 114차 실패 주입·회귀 검증

## 기준 및 범위
- 원본: `RKern_NC_NativeBroadcast_Phase113_BroadcastOnly_Continued_Audit_FULL_SRC_20261117.zip`.
- 생산 Java 변경: **`src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java` 1개**.
- 기존 패킷 직렬화 인자, opcode, 수신자 방문 순서, Native 원본 클래스 및 소켓별 FIFO는 변경하지 않았다. 신규 워커/큐/스레드/DLL 수정 없음.
- 일반 SEND/RECV, CIO 공통 계층, DB, NPC 및 게임 상태 변경 없음.

## 원본 대비 실제 재현 및 수정
1. **이동·행동·전투·상태 효과/SC 광역 방송**(`sendMove`, `sendAction`, `sendExtended`, `sendFormatted`, `sendWritten`): 한 소켓의 `enqueueConstBufferConfirmed` 예외가 뒤쪽 정상 수신자까지 차단. 생산 메서드를 typed stub과 컴파일한 실패 주입에서 113차 `sendMove` 원본은 첫 수신자 오류로 종료되어 뒤 수신자가 누락됨을 재현했다. 수정본은 해당 수신자만 제외하고 같은 Native 원본을 뒤 수신자에게 송신한다. 조회 및 필터 예외도 수신자 단위로 격리한다.
2. **명시적 전투 이펙트 대상**(`sendExtendedObjects`): 동일 소켓 예외로 뒤 대상 송신이 중단되는 문제를 동일 정책으로 수정.
3. **바닥 아이템 AOI**(`sendWrittenGroundItem`): 소켓별 전송 실패가 뒤 수신자의 아이템 표시를 막는 문제를 수정. `viewers` 및 `addKnownObject(item)`은 **실제 SEND 승인이 성공한 수신자만** 기존처럼 처리하며 상태 갱신 코드 자체는 변경하지 않았다.
4. **전투장 다중 이펙트**(`sendWrittenMapTeamFrames`): 첫째 또는 둘째 프레임 실패 시 다음 팀원의 모든 프레임 송신이 누락되는 문제를 재현·수정. 실패한 팀원의 남은 프레임만 중단하고, 정상 팀원은 원래 프레임 순서를 유지한다.
5. **패킷 원본 자체의 문제는 격리 대상에서 제외**: Native source acquire가 실패하면 기존대로 오류를 전파한다. 지연 작성 SC writer 인코딩 실패나 팀 다중 프레임의 잘못된 writer도 즉시 중단한다. 잘못된 패킷을 수신자 수만큼 재인코딩하지 않는다.

## 새 회귀 시험
`bash docs/phase114_broadcast_only/run_tests.sh`
- 실제 생산 `CSharedBroadcast`, `CAsyncSendConstBuffer`, `CAsyncSendConstBufferExceptSelf`, `CTLMFilter`를 Java 21 `--enable-preview --release 21` typed stub과 함께 컴파일.
- 송신 수신자별 예외, 첫/두 번째 팀 프레임 실패, 필터 실패, writer 실패, KnownObject 회계, 원본 1회 구성·반환·FIFO 검사 등 10개 시나리오 통과.
- **5,000명 이동 방송 모의 시험**: 첫 소켓 실패 후 4,999명 수신, 원본 구성 1회·회수 1회 확인. 실제 Windows IOCP가 아닌 Java 모의 소켓임.

## 이전 회귀 재검증
- `phase87` (AOI/FIFO/5,000명 모의, Native 참조), `phase90` (수신자 그룹과 패킷 직렬화), `phase91` (게임 알림), `phase98` (Native 참조수): 실행 결과 통과.
- `phase111`, `phase112`, `phase113`: 구형 방송 잔존 검사, 대형 패킷/선택적 방송/중첩 AOI/팀 공지 회귀 통과.
- `phase99`의 7개 실패 주입 Python 테스트 개별 실행: 모두 통과. `phase92`·`phase93`의 미니게임/오림/LFC/원본 수명 경계 4개 테스트 개별 실행: 모두 통과.
- Java 21 `JavacTask.parse()` 생산 Java **1,710개 오류 0건**.
- `phase93`, `phase99`의 **전체 중첩 통합 셸 스크립트는 제한시간으로 완료되지 않았으므로 전체 통과로 표시하지 않음**. 위 명시된 개별 시험만 통과 판정.

## 미검증
- 외부 라이브러리를 포함한 전체 Java 타입 컴파일, 실제 Windows IOCP/DLL 송신 및 5,000명 운영 장시간 부하 시험은 수행하지 않았다.
- 엄격 상태 동기화용 일반 RAW 송신 경로는 방송만 수정하라는 범위에 따라 변경하지 않았다.
