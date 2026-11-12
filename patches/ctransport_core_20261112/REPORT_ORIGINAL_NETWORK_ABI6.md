# CTransport ABI6 — 원본 네트워크 구조 반영 및 클래스 역할 정리

기준: ABI5 감사 커밋 `9a3ccb8bd1c91c57257e8a2599b583744ec0c955`. 원본 x64 `L2Server(10).exe` SHA256 `9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588`, x86 `L2Server.exe` SHA256 `8eecd317fb152b76f32a0a6a65fbf916e39d20511e203b4a0ea602fe041a22f5`.

원본 바이너리에서 확인한 접속 수락 구조와 실제 native 클래스 역할을 L1 서버에 반영했다. ABI5에서 이미 구현·검사된 풀/FIFO/COW/타이머/네트워크 deadline/codec/packet lease/종료 경계는 이어 사용한다. 이번 변경을 원본 C++ 텍스트 추출 또는 L2 서버 전체 완전 복원으로 표현하지 않는다. ABI5 감사 33행을 전체 복원율의 분모로 사용하지 않는다.

## 실제 변경

이전 `AcceptEx` 사전 게시 경로를 제거하고 다음 경로를 실제 DLL에 연결했다.

```text
CIOServer::Create: listen(backlog5), WSACreateEvent, WSAEventSelect(FD_ACCEPT)
 -> WaitThread::RegisterWait (최대64개, 참조 보유)
 -> ListenThread (실제 별도 스레드, TLS/풀 owner17)
 -> WaitForMultipleObjects -> CIOServer::OnWaitCallback (WSAResetEvent)
 -> PostQueuedCompletionStatus (server key, NULL OVERLAPPED)
 -> IOThreadCallback::IOThread_common -> CIOServer::OnIOCallback -> accept
 -> Server::CreateSocket -> IServerCallback/ServerCallbackAdapter
 -> CUserSocket::Allocate -> 생성/유저번호 색인 -> Server::Add
 -> L1 Java owner/codec seed 초기화
 -> SO_RCVBUF/SO_SNDBUF=0 -> IOCP 연결 -> 초기 read
```

원본 확인 주소: `0x7066dc / 0x706564 / 0x705e4c / 0x704d1c / 0x705508 / 0x8b0738 / 0x8af8e8 / 0xa0d07c / 0xa0ccc4 / 0x8b0404 / 0x545260`. x86의 `0x53d740 / 0x53c7e0 / 0x53bed0`에서도 이벤트 수락을 교차 확인했다. 명령·vtable 근거는 `research/network-audit/`에 보존했다.

수락 중복 게시를 한 건으로 제한하고, accept의 FD_ACCEPT 재활성화와 게시 슬롯 해제를 같은 잠금 안에서 수행한다. 이벤트 등록 해제 전에 OS wait가 끝나도록 보장하며, 등록 변경 대기자가 wait 루프의 재잠금 때문에 기아 상태가 되지 않도록 했다. 이벤트 reset/게시 실패는 진단 후 listener를 닫는다. 종료는 미완료 수락 콜백을 기다린 후 게임 소켓들을 닫는다. 이 잠금·오류 처리의 세부 구현은 안전한 L1 이식이며 원본 기계어의 동일한 재현을 주장하지 않는다.

## 클래스명과 실제 담당

| 계층 | 현재 클래스 | 실제 역할 |
|---|---|---|
| C++ | `CIOObject` | 참조 보유 및 완료 콜백 기반 |
| C++ | `CIOServer -> Server` | listener/event/수락 콜백 기반 -> 게임 소켓 factory/등록/종료 |
| C++ | `CIOSocket -> CIOSocketEx<16384> -> CSocket -> CUserSocket` | overlapped I/O/수명 -> 버퍼 크기 -> peer/접속 제한 -> L1 parser/codec/유저번호 |
| C++ | `CIOBufferEx<8192/16384>`, `CIOBufferPool` | 원본에서 확인한 두 버퍼 계열과 소유자별 풀 |
| C++ | `WaitThread`, `ListenThread`, `IOThreadCallback` | 실제 wait 등록과 별도 listener/IOCP worker 실행 |
| C++ | `CExecutionSerializerFast` | AddExecutable ticket ring/단일 drain owner/동일 owner 재진입; write/terminal 콜백 연결 |
| C++ | `CObjectMap<CUserSocket,8192>` | 유저번호별 8192 bucket/RW 잠금/순서 map/참조 보유 |
| C++ | `CSocketLimiter` | IP/MAC 제한·예약·close-start 시 한 번 반납 |
| Java | `Server`, `CIOServer`, `CSocket`, `CUserSocket` | L1 admission façade, FFM 연결, 소켓 wrapper, 게임 유저 owner |

Java `CTransportServer/Socket/Endpoint/Task/Config`는 `CIOServer/CSocket/CIOServerEndpoint/CIOObject/CIOConfig`로, `CIOUserSocket`은 `CUserSocket`으로 정리했다. 이전 Java `CIOServer` façade는 `Server`로 이동했다. 별도 `CUserSocketDispatch`의 L1 상태 검사는 `CUserSocket`에 통합하고 호출부도 고쳤다. C++ 클래스 계층은 실제 상속이며 Java façade는 FFM 수명 관리상 composition을 사용한다. Java `CIOObject`는 작업 인터페이스이므로 원본 native 객체의 ABI/가상함수 배치와 동일하지 않다. `CUserSocketParser`는 L1 전용 helper이며 원본 클래스명이라고 주장하지 않는다.

DLL 이름은 요청한 `CTransport.dll`을 유지했다. 기존 C ABI/FFM 함수명의 CTransport/CIO 접두사는 호환 경계다. 이 이름을 바꿔 게임 클래스까지 엔진 구현으로 오인하게 만들지 않는다.

## 등록·제한·운영 진단

- 원본 `Server::Add/Remove/Shutdown`의 raw SOCKET map을 별도로 구현했다. close-start에서 raw map을 제거하고 참조를 반납한다. 외부 API의 slot+generation map은 packet lease와 실제 I/O 완료까지 유지한다.
- 원본 유저번호(+0x4e8)와 map variant26에서 확인한 보조 색인을 추가했다. 32-bit 유저번호와 64-bit 외부 핸들을 구분한다. 보조 색인은 최종 packet lease 반납까지 객체를 보유하고, 번호 조회는 active 소켓만 반환한다. 실제 원본의 전역 단일 객체 대신 서버 인스턴스별 map을 사용한다.
- native IP 제한은 기존 `NETWORK_CLIENT_PERMISSION` 설정을 시작 전에 연결했다. 기존 L1 계정/필터 검사는 유지한다. MAC 예약·상한·반납 API도 구현했지만 **현재 L1 접속 프로토콜에는 신뢰할 MAC 공급 연결이 없다. 운영에서 MAC 제한이 자동 적용된다고 해석하면 안 된다.** 추측한 MAC이나 새로운 PSS 메시지를 만들지 않았다. 제한값0은 비활성이다.
- worker는 요청 수와 CPU 수 및8 중 최솟값으로 제한하고 CPU affinity를 설정한다. 실제 worker 실행 주체는 `IOThreadCallback`이다. owner17에 별도 timer wheel/풀을 연결하고 만료 작업은 IOCP worker로 게시한다.
- 송신 대기 상한을 원본 확인 값2MiB로 맞췄다. L1 Java SEND 순서 직렬화는 rolling cipher/게시 순서를 위해 유지한다.
- `ReportSocket`은 retained snapshot으로 유저별 상태/peer/큐 크기를 읽는다. `GetThreadDiagnostics`는 18개 owner의 콜백 수/누적시간/실행 시작 시각을 제공한다. L2 운영 프로그램 전체의 로그·설정 화면을 복원한 것은 아니다.

추가 exports7개, 총56개; ABI `0x60000`. `CIOConfig`가 DLL SHA256과 ABI를 검사한다. 추가 SQL은 없다.

## 원본과 구분하는 부분

| 항목 | 현재 상태 |
|---|---|
| 원본 GQCS 단건 완료 | 앞서 요청한128개 배치 GQCSEx 유지; NTSTATUS는 entry별 변환 |
| L2 packet/암호/월드·DB | L1 wire/opcode, 기존 `CIOPacketCodec.dll`, 기존 게임 handler/계정/월드 유지 |
| 원본 포인터/참조·시간·에러 정책 | 외부 generation 핸들, packet lease, monotonic64 시간, 취소 색인 유지; 의도적인 null write/exit 재현 제외 |
| 원본 WriteCallback 길이 불일치 | 원본은 로그/return 분기; 현재 부분 송신 진행·재게시 유지 |
| CObjectMap variant27/28 | 키 의미·전체 소비자 미확정; account/MAC map이라고 추정하여 추가하지 않음 |
| 조건부 thread15 | `0x725e30 -> 0x725488`에 `Perfmon in mailing mode` 로그를 추가 확인. 다른 분기 `0x82c408`의 전체 업무 의미는 미확정; 게임 accept worker로 가정하지 않음 |
| 독립 PSS | 전용 NIO2 연결/활성화/클라이언트 유지; 게임 DLL에 합치지 않음 |

`CObjectMap`의 bucket 해시/표준 라이브러리 node 배치, WaitThread의 mutex, thread affinity 실패 처리, native/Java 진단 API는 이식 구현이다. RTTI·함수 signature와 호출/필드 사용에서 확인한 역할에 맞춘 것이며 원본 C++ 클래스의 크기·vtable·모든 예외 경로까지 바이너리 호환이라는 뜻은 아니다. L2 Auth/NPC/DB/원격 운영 서비스는 이번 L1 게임 transport 범위가 아니다.

## 검증과 적용

- ASan/UBSan 및 TSan: 실제 생산 C++를 Windows API fixture로 검사. 동시 serializer8000건/재진입/504상한, RegisterWait64, burst48 수락과 재활성화, owner17 실행, raw/secondary/generation 등록, IP/MAC 거절·반납과 ReportSocket 추가. 기존 120접속/600동시 close/10회 lifetime, batch128/codec/lease/타이머/취소/종료 검사 유지.
- 전체 Java21 소스1701개 컴파일 및 DLL 강제 ABI/hash 검사. Windows FFM suite와 실제 production CUserSocket suite도 별도 컴파일하고 L1 policy/registry/close 계약을 검사한다.
- PE64/56 exports/ABI/해시, FD_ACCEPT/wait/accept/affinity와 IOCP imports 확인.
- 독립 PSS 계약 통과. 8개 PSS 관련 원본 파일의 해시와 codec DLL이 동일하다. 미사용 실험 패킷 `SC_PssNativeWindow`만 삭제된 `CIOUserSocket`의 import/지역 타입2곳을 새 `CUserSocket`으로 바꿨으며 로직은 그대로다. CIOClient의 PSS 필드/메서드도 원본과 일치한다.
- 실제 Windows 실행 결과와 테스트 커밋/해시는 `WINDOWS_VERIFICATION.json`을 따른다. ABI5 이력은 ABI6 성공으로 사용하지 않는다. 실게임 클라이언트·운영 DB·장시간 동접·NIO2 대비 성능은 미검증이다.

전체 src ZIP과 core ZIP의 DLL을 함께 적용하고 clean build한다. 부분 적용은 `changed-files.txt`와 `deleted-files.txt`를 모두 반영해야 한다. 이전 이름의 소스/클래스 파일을 남기지 않는다. 기존 L1 codec DLL, PSS 클라이언트/설정 및 Java21 preview/native-access 실행 옵션을 유지한다.

실제 ABI6 Windows Server2022/Temurin21 검사가 통과했다: [실행43323490468](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43323490468), 검사 소스 `52294c65e76e4cb9b44dcbea574c575a1dbf6977`. 최종 묶음은 이 소스·DLL과 동일하며 이후 커밋은 검증 기록을 추가한다.
