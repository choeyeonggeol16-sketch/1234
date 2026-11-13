# CTransport ABI8 — SEND 실행 계층과 Close/GracefulClose

2026-11-13. 이전 배포 `859396fe01585e72f88a750a1989d4279f886341`(ABI7)과 원본 x64 바이너리를 대조하고, 송신 요청·암호화·WRITE 완료의 실행 순서 및 정상/강제 종료를 구현했다. ABI `0x80000`, 66 exports. src와 같은 묶음의 DLL을 함께 적용해야 한다. 추가 SQL 없음.

## 원본에서 확인한 근거

대상은 제공된 `L2Server(10).exe`, image base `0x400000`, SHA256 `9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588`이다. PE unwind로 함수 경계를 잡고 실제 명령 경계에서 호출을 검증했다. 상세 명령·xref·주소는 `research/send-close-audit/`에 보존했다.

| 원본 경로 | 실제 주소와 관찰 | 이번 반영 |
|---|---|---|
| `SendV` | entry `0x90ee9c`, `0x90f025 -> 0x45c7ac` serializer 호출 | SEND 요청도 WRITE 완료와 동일한 native `CExecutionSerializerFast`에 투입 |
| `__async_AsyncSend` | entry `0x90ed10`, `0x90edf1 -> 0x90dda4` 암호화, `0x90ee18 -> 0x90e1d0` 송신 큐 | 실행기 소유 스레드 안에서 L1 프레임별 암호화 → 송신 FIFO → WSASend |
| WRITE 계층 | callback `0x545e38`, enqueue `0x545a18`, serializer `0x45c7ac` | 요청·부분 완료·다음 WSASend의 소유권 공유 |
| `CIOSocketEx<CIOBufferEx<16384>>::Close` | entry `0x45d41c`, OnClose virtual+0x98, `0x45d4c6` setsockopt `SO_LINGER(1,0)`, `0x45d4cf` closesocket | 오류·강제 종료는 abortive close 유지 |
| 같은 계열 `GracefulClose` | entry `0x545178`, OnClose virtual+0x98, `0x5451fc` closesocket, 해당 함수에 linger0 설정 없음 | 정상 종료에서는 RST용 linger0 설정 생략 |

원본 GracefulClose 함수만으로 **송신 큐를 모두 기다리는 알고리즘까지 확인했다고 말할 수 없다.** 아래의 송신 drain·최대 대기시간·FFM 수명 관리는 L1 호환과 안전한 종료를 위한 추가 구현이다. 기존 L2 암호나 패킷 핸들러를 L1에 이식하지 않았다.

## ABI7에서 달라진 동작

기존 게임 송신은 Java에서 native codec의 Encrypt를 별도 호출하고 바로 native 송신 큐에 넣었다. 이제 Java는 L1 프레임을 만들고 첫 키 패킷의 평문 여부만 지정하며, native 실행기가 요청을 받아 암호화와 큐 삽입을 순서대로 처리한다. 기존 Java 512 슬롯은 패킷 생성·세션 소유권·lease 순서를 위해 유지한다. native 512/504 실행기는 SEND와 WRITE 완료의 순서를 담당한다.

새 C API는 `CTransport_UserSocket_SendFrames`, `CTransport_UserSocket_SendFramePackets`(최대8 WSABUF), `CTransport_Server_GracefulClose`, `CTransport_Server_GetSendDiagnostics`다. 기존 raw Send/SendPackets도 같은 실행기를 거친다. 기존 Encrypt export는 호환·독립 비교 검사에 남아 있지만 실제 게임 송신은 새 프레임 API를 사용한다.

- 요청은 C API가 반환하기 전에 원본 바이트를 복사하고 소켓 참조를 가진다. 대기 중 요청도 2MiB 상한과 종료 시 수명 검사에 포함된다. inline 실패는 false, 지연 실행에 접수된 뒤 실패하면 종료 통지로 전달한다.
- 전체 LE16 프레임 경계를 먼저 검증한다. descriptor 경계를 가로지르는 프레임도 처리한다. 첫 평문 프레임 뒤에는 각 프레임의 2바이트 헤더를 제외한 본문만 한 번 암호화한다. 부분 WSASend 완료는 이미 만든 암호문만 이어 보내므로 키가 다시 진행되지 않는다.
- 잘못된 프레임은 암호 키를 진행시키지 않고 거절한다. 암호화·버퍼 확보·WSASend가 실패한 연결은 강제 종료하여 바뀐 키로 계속 보내지 않는다.
- 정상 종료는 새 송신을 막고 접수된 요청·실제 WRITE를 기다린 뒤 linger0 없이 닫는다. 게임의 마지막 패킷 전송 완료 뒤 정상 종료를 연결했다. native 최대30초, 명시된 짧은 기한 초과나 강제 종료는 abortive 경로로 전환한다.
- 종료 시작, OS I/O 완료, Java 패킷 lease 해제의 기존 단계를 유지한다. SEND downcall이 원본을 복사하는 중 Java가 닫히면 writer 완료 통지를 복사가 끝날 때까지 미룬다. 이전 방식은 원본 lease가 먼저 재사용될 수 있었다.

`Server.networkSendDiagnostics()`의 배열은 `[accepted, executed, encryptedFrames, plainFrames, gracefulCloses, abortiveCloses, rejected, pendingRequests, ownerErrors]`다. encrypted/plain은 처리 프레임 수이며 상대가 수신 확인한 횟수는 아니다.

## 검증

- ASan/UBSan 및 ThreadSanitizer: 실제 production C++와 Windows API fixture로 기존 전체 검사 및 새 SEND/종료 경합 검사 통과.
- 지연 실행 중 원본 재사용, 최초 평문+최대65535 프레임, descriptor 경계를 넘는 프레임, 37바이트 부분 완료, 잘못된 길이, 암호화 실패, 대기 SEND/WRITE 정상 종료, 시간 초과·강제 전환을 검사했다. 완료·참조·pending이 남지 않는지 확인했다.
- 실제 production CSocket을 사용한 single/gather 복사·종료 경합 검사: downcall 종료 전 lease 해제 없음, 원본 바이트 보존, 실패 완료1회 확인.
- 전체 Java21 production 1,702파일 컴파일, PE64 ABI/66 exports/Java SHA pin 확인.
- 실제 Windows DLL을 쓰는 production CUserSocket 검사에 최초 평문, 최대 S2C 프레임, 8개 packed action 각각 암호화, 마지막 패킷과 정상 EOF 비교를 추가했다. 최종 실제 실행 상태·검사 커밋·해시는 `WINDOWS_VERIFICATION.json` 기준이다. 이전 ABI7 성공은 `research/send-close-audit/ABI7_WINDOWS_HISTORY.json`에 별도 보존한다.
- 독립 PSS 소스·클라이언트 DLL 보존 및 activation/pairing/OPEN 계약 통과. 기존 L1 codec DLL과 PSS DLL 바이트 변경 없음.

실제 게임 클라이언트·운영 DB·사냥 통합은 제공 자료에 없어 검증하지 못했다. Windows 계정/게임 소유자 의존성 일부는 fixture이며, 반복 loopback 결과로 운영 최대 동접·NIO2 대비 성능을 보장하지 않는다. MAC 식별자 공급자는 기존대로 자료에 없어 기본 제한을 끈다. 원본 소스 전체를 복원했다는 의미가 아니다.
