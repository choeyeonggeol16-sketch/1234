# 현재 ABI10 이후 추가 복원 후보: SafeSend와 종료 사유 전달

2026-11-13. 비교 기준은 `ctransport-core-20261112`의 `abb71a8d4042edd53addfcddb48ed36da921a648`이다. **이번에는 정적 분석과 보고서만 추가했다. 소스·DLL·배포 ZIP은 수정하지 않았다.**

추가 가치가 확인된 항목은 **문장 이미지의 선택적 송신 제한**과 **종료 사유의 Java 진단 전달**이다. 두 항목 모두 기존 IOCP·송신 큐·종료 엔진을 다시 만드는 작업이 아니다.

| 우선순위 | 원본에서 확인한 동작 | 현재 L1과의 차이 | 제안 범위 |
|---|---|---|---|
| 1 | `CUserSocket::SafeSend(int margin, ...)`는 pending+margin이 2MiB를 초과하면 로그를 남기고 false를 반환한다. 이 실패 분기에서는 SendV나 Close를 호출하지 않는다 | 일반 송신의 2MiB 제한과 예약량 계산은 이미 있다. 그러나 `SC_Emblem`은 일반 `beginDirectFrame`을 사용하며, 그 API는 용량 부족 시 close한다. 문장 이미지에 별도 여유분을 적용하는 정책은 없다 | 문장처럼 생략 가능한 데이터에만, 인코딩·FIFO 예약 전에 같은 lock 아래 소프트 제한을 검사하는 API와 호출부 추가 |
| 2 | `ForceClose(SOCKET_CLOSE_REASON)`는 socket+0x53c에 사유를 저장한 뒤 기존 Close를 호출한다 | native는 이미 최초 `operation/error`를 보관해 closing/close 콜백으로 전달한다. Java `CIOServer.onClosing/onClose`는 이 인자를 연결 진단에 저장하지 않으며, 최근 패킷 Trace는 closed 여부만 보관한다 | 기존 종료 순서·수명 fence는 유지하고, Java 종료 사유·native operation/error를 기존 연결 기록과 매니저 조회에 전달 |

## 1. SafeSend: 실제 사용처와 정확한 조건

문자열만 보고 추정한 결과가 아니다. 다음 호출과 가상 함수 슬롯을 확인했다.

- `SafeSend` 함수 시작은 **0x9ef41c**다. `0x9ef493`에서 vtable+0x78을 호출하고 false이면 `0x9ef4e3`에서 false로 끝난다. true이면 vtable+0x88인 **SendV 0x90ee9c**로 진행한다.
- CUserSocket vtable **0xdc1c18**의 +0x78은 **0x9ef620**이다. 이 leaf는 유효 SOCKET을 검사하고, socket+0x20의 pending에 인자 margin을 더해 **signed `<= 0x200000`**을 검사한다. 경계와 같은 값은 허용한다.
- 확인한 직접 호출은 세 곳이다. **User::OnPledgeEmblem 0x97e518**, **User::OnPledgeCrest 0x97e6d0**, **AllianceCrestPacket 0xa06c68**. 실제 SafeSend 호출은 각각 **0x97e62b / 0x97e7af / 0xa06d8d**이며, 모두 margin **0x80000 = 512KiB**를 전달한다.
- 따라서 이 호출들에서는 pending이 **1.5MiB를 초과하면 문장 전송을 거절**한다. 확인한 호출부는 반환값으로 재시도·대기 큐를 만들지 않는다. 자동 재전송이나 필수 패킷의 전달 보장 기능으로 설명하면 틀린다.

현재 `CUserSocket.canAdmitBytes()`는 outstanding+reserved를 함께 제한하고, `enqueueConstBuffer(..., pendingLimit)`에는 이미 별도 방송 생략 조건이 있다. **송신 제어가 전혀 없는 것이 아니라, 일반 문장 전송에 같은 선택 정책을 제공하지 않는 차이**다. 기존 공유 방송 경로를 새로 구현할 필요가 없다.

L1에 반영한다면 기존 `SC_Emblem` wire 형식은 유지한다. 소프트 검사와 실제 크기/FIFO 예약 사이의 경쟁을 막고, 거절 시 lease·ticket·암호화 상태를 건드리지 않아야 한다. 필수 이동·로그인·인벤토리·거래 패킷에 일괄 적용하면 안 된다. 원본의 32비트 덧셈을 그대로 옮기지 말고 margin 범위 검사와 overflow 없는 계산을 사용한다. 512KiB는 원본 호출값이며, L1의 적정값을 측정했다는 뜻은 아니다.

구현 후 필요한 검사는 1.5MiB 경계 전후, 거절해도 연결 유지, 동시 예약에서 한도 초과 방지, 거절 전후 cipher/FIFO 순서, lease 반환, 종료 경쟁이다. 이 문서에서 해당 기능을 구현하거나 검사를 통과했다고 주장하지 않는다.

## 2. 종료 사유: 새로운 Close 엔진이 아니라 전달 누락

**0x9fcc52**의 `mov [rcx+0x53c], edx`, **0x9fcc58**의 기존 Close **0x45d41c** 호출로 원본 동작을 확인했다. 동일 필드는 일반 write 검사 **0x9ef544**에서도 읽고 갱신한다. 실제 enum의 전체 이름·숫자 매핑을 복원했다는 의미는 아니다.

현재 native `CIOSocket::failLocked`는 closing 중이면 기존 사유를 유지한다. `closeCallback()`도 `closeOperation/closeError`를 넘긴다. 이 기능을 중복해서 만들 필요는 없다. 빠진 부분은 Java의 `onClosing/onClose` → `CSocket` → `CUserSocket`/`CNetworkDiagnostics.Trace` 전달이다. 기존의 전역 SEND 거절 사유 카운터는 연결별 최종 종료 사유와 의미가 다르다.

반영한다면 원래의 원인을 후속 `ERROR_OPERATION_ABORTED` 등으로 덮어쓰지 않도록 해야 한다. Java가 먼저 시작한 종료 사유와 native I/O 상태를 별도 필드로 보관하고, 정상 종료·원격 EOF·타임아웃·프로토콜 오류·송신 한도를 L1의 실제 분기에서 분류한다. L2 enum 숫자를 추측해 붙이지 않는다. 게임 객체나 패킷 본문을 추가로 보관할 필요도 없다.

## 이번 후보에서 제외한 항목

- **RequestNetPing 0xa01240**: outstanding 필드+0x544를 지우고 응답 값을 파싱한다. `GetTickCount`와 GameGuardAgent 객체를 사용한 뒤 별도 보고 함수를 호출한다. 일반적인 IOCP 엔진 누락으로 분류하지 않았다. L1에는 이미 `SC_Ping`과 GM ACK 경로가 있다. 원본 요청 생성·패킷 등록·클라이언트 규약 전체를 추적하지 않은 상태에서 별도 heartbeat 복원이 필요하다고 단정하지 않는다. 직접 호출 xref가 0이라는 이유만으로 미사용 함수라고 판단하지 않았다.
- **OnSending**: signature 문자열은 있지만 이번 RIP 참조/함수 경계 검색에서는 사용 함수를 연결하지 못했다. 문자열 존재만으로 전송 hook 누락을 주장하지 않는다.
- **CheckMaxPacketAccept**: 이전에 확인한 이 빌드의 통계 함수이며 항상 true 반환한다. 이미 있는 L1 rate limit을 다시 만드는 항목이 아니다.
- IOCP128·accept·8K/16K 풀·serializer·부분 송신·공유 입력 방송·타이머·등록 색인·graceful/abortive 종료는 이미 구현한 범위다. PSS는 독립 상태를 유지한다.
- native worker 식별 정보와 OS 완료량 계측의 세부 확대는 과거 보고서의 알려진 범위다. 이번에 새로 발견한 복원 대상으로 재집계하지 않았다.

## 근거 및 재현 범위

입력은 x64 `L2Server(10).exe`, image base **0x400000**, SHA256 **9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588**이다. 원본 EXE는 실행하지 않았다. x86 재대조나 전체 간접 호출망·모든 네트워크 분기의 감사를 완료했다는 의미가 아니며, 복원율 숫자를 산출하지 않았다.

`trace_candidates.py`는 SHA를 고정하고 UTF16 signature의 실제 RIP 참조, PE pdata/unwind 함수 경계, vtable 3개 슬롯, 실제 명령 12개를 검증한다. `SafeSendCapacity`만 pdata가 없는 leaf이며 entry에서 도달 가능한 명령 범위를 별도 표시했다. `candidates.json`, `extra-functions.json`, `verified.json`과 함수별 asm이 근거다. `source-index.json`에는 비교한 현재 소스의 경로·해시를 고정했다.

동봉한 helper와 pefile/capstone이 있는 Python으로 재현한다. 원본 EXE는 사용자가 제공한 파일을 별도로 사용한다.

```sh
python trace_candidates.py --binary '/path/to/L2Server(10).exe' --helper audit_network_abi5.py
```

정적 대조 결과이며 Windows 커널·실제 L1 클라이언트·DB·장시간 부하 검사를 이번 작업에서 추가 수행한 결과는 아니다. 기존 검증된 DLL을 교체할 이유는 없다.
