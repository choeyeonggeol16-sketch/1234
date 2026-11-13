# ABI8 이후 추가 네트워크 구현 후보 대조

2026-11-13. 현재 GitHub `ctransport-core-20261112`의 `833ddfdc6f1a47c953347692addbc3d4e4bdd097`을 확인했다. 검사에 통과한 ABI8 native/src/tests/build는 `292ef5bf3e21eb3851dce113a8a64c459dca3cea`와 동일하다. 이번 작업은 추가 정적 분석·진단 누락 재현이며 게임 소스·DLL 변경은 없다.

추가할 실익이 큰 부분은 **최근 패킷 기록, 트래픽·대기량 계측, 실행 중인 작업의 식별 정보**다. 원본에서 역할을 확인한 근거와 L1에 추가할 설계를 구분했다. 기존 accept/IOCP128/8K·16K 풀/COW/타이머/계정 색인/SEND 암호화·종료를 미구현으로 다시 분류하지 않는다.

## 비교 결과

| 순서 | 항목 | 원본 근거 | 현재 ABI8 | 추가 구현의 효과 |
|---|---|---|---|---|
| 1 | 유저별 최근 수신 패킷128개 기록과 조회 | `OnRead 0xa0372c`의 `sequence & 0x7f`, socket+0x804/+0xa04에 코드·시각 기록. `ReportPacketLog 0x9fcc98`이 두512-byte 배열을 복사하고128개를 역순 출력 | 전역 slow-opcode 횟수·최대시간은 있지만 소켓별 최근 패킷 순서·시각 기록은 없음 | 끊기기 직전 어떤 패킷이 들어왔는지 유저 단위로 추적 |
| 2 | 송수신 계측과 소켓별 전체 대기량 | read16 `0x545480/0x545489`, 송신 게시 `0x90e32c/0x90e335`, 다음 송신 게시 `0x545f62/0x545f6b`의 누적량·횟수. `0x1b58`(7000) 초과 별도 분기 | CPerfMon은 CPU·메모리·인원·콜백 수, SEND 진단은 접수·프레임·pending 요청 수 제공. 실제 완료 바이트/초·I/O 건수/초는 미제공. 소켓 보고서는 실행 전 송신 예약량 누락 | 송신 지연 원인이 입력량·송신 적체·부분 완료 중 어디인지 비교 |
| 3 | 실행 중인 native 작업의 식별 정보 | IO worker `0x7056ac`의 thread별 함수 signature stack 기록과 I/O 전후 LockStack 검사. `0x3e8` stride 및 `Invalid LockStack before/after io!!!` 문자열을 실제 명령에서 확인 | worker별 콜백 수·누적시간·현재 시작 시각은 있음. 활성 작업의 connection/operation/opcode·함수 stack은 미제공 | 오래 걸리는 worker가 어느 연결의 어떤 작업을 수행 중인지 조회 |

1번의 원본 기록은 패킷 **본문 전체 캡처**가 아니다. 코드와 시각의 고정 크기 기록이다. L1에 넣을 때도 계정 암호·채팅 등 본문을 저장할 이유가 없다. L2 C_EX 숫자와 메시지 형식을 그대로 L1에 적용하지 않고, 기존 L1 opcode와 다중화 프로토콜 식별자에 맞춰야 한다.

2번의 원본 카운터를 TCP 전달 완료량으로 부르면 부정확하다. read callback은 남아 있던 수신 버퍼의 누적 사용량을 더하고, write 경로의 카운터는 WSASend 게시 직전에 갱신된다. L1 구현에서는 **접수량 / 게시 시도량 / OS 완료량 / 논리 프레임 수**를 구분해야 한다. 원본의 계측 역할을 반영하되 원본 수치의 의미까지 임의로 바꾸어 설명하지 않는다.

3번은 원본의 자동 watchdog·자동 복구 정책을 확인했다는 의미가 아니다. 확인한 것은 함수 stack 기록과 LockStack 전후 검사다. 현재 시스템에 없는 lock 추적을 있는 것처럼 표시하거나, 원본의 강제 crash 동작을 추가할 필요는 없다. connection·operation·opcode·시작 시각을 안전하게 snapshot하는 진단이 우선이다.

## 현재 소스에서 재현한 대기량 표시 누락

`native/CTransport.cpp`의 송신 접수는 `reservedSendBytes`와 `pendingSendRequests`를 먼저 증가시킨다. 실제 실행 후 예약량을 줄이고 `SendChain`으로 옮긴다. 반면 `CTransport_Server_ReportSockets`의 row8은 `writes.size()`만 반환한다.

원래의 production C++를 그대로 포함하고 기존 Windows API fixture로 실행기를 잠시 막아 다음 상태를 재현했다.

```text
per-socket reportedWriteBytes=0
actualReservedBytes=256
actualPendingRequests=1
globalPendingRequests=1
SEND still completes correctly
```

따라서 이는 송신 데이터 유실이 아니라 **소켓별 관측 범위의 누락**이다. 전역 pending 진단에는1건이 잡히지만 어느 소켓에 몇 바이트가 있는지 기존 row8만으로 알 수 없다. `reproduce_queue_report_gap.cpp`와 `queue-report-reproduction.log`에 보존했다. 이 재현은 Windows 커널 실행 검사가 아니다.

수정 시 row8의 기존 의미를 명시적으로 갱신하여 `writes.size()+reservedSendBytes`를 전체 대기량으로 제공하거나, 기존 필드와 새 예약량·요청 수 필드를 따로 제공해야 한다. 같은 socket gate 안에서 snapshot한다. 물리 WRITE 잔여량과 암호화 전 대기량을 함께 보여주면 더 유용하다. Java의 SEND 준비 큐는 native 예약량과 별도이므로 중복 계산 없이 계층별로 구분한다.

## 구현한다면 필요한 계약과 검사

1. 최근 패킷 기록은128개 고정 상한으로 유지한다. 동시 READ/세션 handler/close 조회에서 반쯤 기록된 슬롯을 읽지 않게 한다. 유효 항목 수·sequence를 함께 제공하고 연결 교체 뒤 이전 기록이 섞이지 않게 한다. 정상 패킷마다 문자열·파일 로그를 만들지 않는다.
2. RX/TX OS 완료 바이트는 실제 성공 completion의 길이로 계산한다. 부분 송신을 논리 프레임 개수로 중복 계산하지 않는다. invalid completion·즉시 게시 실패·늦은 취소·정상 EOF·grace 중 버린 수신의 집계 정의를 고정한다. blocked serializer 상태의256바이트가 소켓별 보고서에 보이는지도 검사한다.
3. worker 진단은 operation·generation handle·필요한 opcode·시작 시각을 함께 publication한다. 완료 후 active 표시를 지우고 같은 worker에서 재진입한 작업의 상위 문맥을 복원한다. 이미 있는 handler 지연 카운터와 worker 시작 시각은 재사용한다. FFM 외부 호출 스레드를 native IO worker로 오인하지 않는다.

## 별도 판단한 후보

| 후보 | 확인한 내용 | 판단 |
|---|---|---|
| `CheckMaxPacketAccept(int)` | `0x9f7f3c`은 선택 opcode의 약1초 구간 통계와 최댓값·누적값을 갱신하고 `0x9f8024: mov al,1`로 항상 true 반환 | 이 빌드에서 도배 차단 함수라고 단정하면 틀린다. 현재 `CIOClient.checkPacketRateLimit` 및 NetworkSafeLoadManager는 이미 존재한다. 통계 추가 대상으로만 분류 |
| `__async_AsyncSendConstBuffer(void*)` | `0x90e6c4`은 입력 참조를 반납하고 전용 목적 버퍼로 복사 또는 복사하면서 암호화한다. `0x90e812` 암호화-copy, `0x90e843` memcpy 확인 | 원본도 무복사 전송은 아니다. 현재 vector→SendChain 복사를 줄이는 후보는 있으나 프로파일링 후 판단. 공유 plaintext를 소켓별 rolling key로 직접 덮어쓰면 안 됨 |
| MAC/HWID 실제 클라이언트 공급 | ABI7의 공급자 API·제한·인증 강제 검사와 기본 비활성은 이미 있음 | 실제 클라이언트 송신·검증 자료가 필요한 외부 연동. 독립 PSS에 임의로 붙일 항목은 아님 |
| PDH 전체 프로세스 목록·CSV·메일 | 원본 CPerfMon의 더 넓은 운영 출력은 앞서 확인 | L1에 필요한 운영 형식이 있을 때 선택. 성능/소켓 모니터 부재로 다시 분류하지 않음 |

## 근거와 한계

입력 x64 PE SHA256은 `9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588`, image base `0x400000`이다. 바이너리는 실행하지 않았다. `trace_candidates.py`가 원본 SHA를 검사하고 UTF16 signature를 실제 RIP 참조 및 PE unwind 함수 경계와 연결한다. `candidates.json`은12개 함수 경계·직접 호출·xref를, `verified.json`은12개의 실제 명령 bytes 단언을 담는다. 함수명만 보고 의미를 정하지 않았다.

이번 추가 대조는 x64의 위 함수 범위에 한정된다. x86 전체 재대조·간접 호출 전체·모든 L2 서비스 복원·실게임/DB/장시간 부하 검증을 뜻하지 않는다. source-index.json은 확인한 production 파일의 해시를 고정한다. 기능 수정이나 새 DLL을 배포한 결과로 이 보고서를 해석하지 않는다.

재현하려면 기존 core ZIP의 루트에 이 자료 디렉터리를 `research/post-abi8-audit/`로 놓는다. 원본 EXE는 별도로 보관한 제공 파일을 사용한다. 분석 스크립트는 기존 `research/audit_network_abi5.py`와 Python pefile/capstone을 사용한다.

```sh
python research/post-abi8-audit/trace_candidates.py --binary '/path/to/L2Server(10).exe'
g++ -std=c++17 -O0 -pthread -Itests research/post-abi8-audit/reproduce_queue_report_gap.cpp -o /tmp/queue-report-gap
/tmp/queue-report-gap
```
