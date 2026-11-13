# CTransport ABI9 — 원본 이름 대조와 상수 송신 경로

2026-11-13. 기준 `fbf430517a1adf2a2dfbcdb713707768fb1d23a0`(ABI8). 진단 기능 추가와 구분하여, 이번에는 원본 이름을 실제 호출부에 연결하고 `CSocket::__async_AsyncSendConstBuffer` 및 `MemoryObject` 기반 실행 인자 계층을 반영했다. ABI `0x90000`, export64개. 전체 src와 새 DLL을 함께 적용하고 clean build한다. 추가 SQL 없음.

## 실제 이름을 확인한 방식

원본 x64 `L2Server(10).exe` SHA256 `9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588`. PE unwind 함수 경계, 실제 명령 경계, RTTI CompleteObjectLocator의 type descriptor와 base class array를 대조했다. EXE를 실행하지 않았다.

이전의 `ServerCallbackAdapter`/감사 label `IServerCallbackAdapter`는 재구현·분석에서 붙인 이름이었다. vtable `0xd29ee8`, COL `0xec0be8`의 실제 RTTI 이름은 **`CDummyServerCallback`**이고 기반은 **`IServerCallback`**이다. `Server::Run`의 `0x8afa53`이 이 vtable을 설치한다. `0x8af8ee`는 저장한 생성 함수로 tail jump한다. CIOObject의 base class array에는 **MemoryObject**가 있다.

| 이전 | 현재 실제 사용 | 근거·역할 |
|---|---|---|
| C++ `ServerCallbackAdapter` | `CDummyServerCallback : IServerCallback` | 원본 RTTI 및 factory delegate |
| Java `CIOSocketFactory.java` / `Server.SocketFactory` | `CDummyServerCallback.java` / `IServerCallback.java` | 같은 allocation/admission 연결 역할. L1 설정·IP·세션 로직은 이어 사용 |
| Java `CIOBufferPool.Lease` | 독립 `CIOBufferEx.java` | 원본의 buffer family 이름에 맞춘 Java pooled view. 획득·게시·반납·generation 계약 유지 |
| refcount를 함께 갖던 C++ CIOObject | `MemoryObject -> CIOObject` | refcount 기반과 I/O callback을 분리. fast serializer는 MemoryObject 인자를 받음 |
| C++ `completed` callback entry | `OnIOCallback` | 실제 I/O·작업 dispatch가 호출. Java CIOObject 작업도 이 entry를 거쳐 Runnable로 연결 |
| C++ Server `start` | `Server::Run` | 원본 Run role와 호출 연결 |
| Java cipher/profile 초기화 이름 | `SetKey` | 기존 L1 seed와 codec/profile 초기화, native `CSocket_SetKey` 연결 |
| Java framed single/gather entry | `CSocket.SendV` / `SendConstBuffer` | 서로 다른 native 송신 경로를 실제로 호출 |

확인된 C++ 클래스는 원본과 같은 global scope로 배치했다. Java의 `Server/CIOServer/CSocket/CUserSocket` 등 이미 역할을 맞춘 이름은 이어 쓴다. `CIOBufferPool/CIOSlotPool/CIOFrame` 등 L1용 보조 타입과 L1 세션의 기존 이름까지 원본에 실제 존재하는 클래스라고 주장하지 않는다. JVM 객체 크기·상속 배치와 원본 MSVC C++ ABI가 같다는 의미가 아니다.

## DLL export와 FFM의 연결

옛 `CIO_Create_TcpServer*`, `CIO_Set_FN_Server_*` 등 HP 스타일의 외부 명칭을 제거하고 모든 Java lookup·C header·self-test·검사를 함께 변경했다. 무효 정책용 `SetSendPolicy/SetOnSendSyncPolicy` 두 entry도 제거했다. 구명칭 alias export는 남기지 않았다.

주요 현재 진입점:

```text
Server_Run
CIOServer_Close
CIOSocketEx_Close
CIOSocketEx_GracefulClose
CIOSocket__Write
CSocket_GetIPAddressString
CSocket_SetKey
CSocket_Encrypt
CSocket_SendV
CSocket___async_AsyncSendConstBuffer
Server_ReportSocket
```

원본은 EXE 내부 C++ 멤버 함수다. 위 entry들은 그 class/member 이름과 역할을 반영한 **L1 FFM용 C ABI wrapper**이며 원본 함수의 인자·포인터 레이아웃·mangling을 복제한 ABI는 아니다. 예를 들어 SendV에는 L1에서 이미 인코딩한 frame bytes를 넘기고, 상수 송신 entry는 최대8 WSABUF를 접수하여 같은 native serializer에 게시한다. 직접 실행 소유권을 건너뛰는 API로 바꾸지 않았다.

버전 확인·생성/해제·callback table·설정·lease·L1 상태·진단처럼 FFM에 추가한 기능은 **`L1Interop_*`**로 명확히 구분했다. 원본에 없는 메서드 이름을 원본이라고 만들어 넣지 않는다. 전체64개 전후 대응은 `research/abi9/export-name-map.json`에 있다. `CIOSocketEx_Close`는 force flag를 제거하여 항상 강제 Close하고, 정상 종료는 별도 GracefulClose를 호출한다.

## 추가로 반영한 상수 송신 동작

원본 `0x90e6c4`는 `CSocket::__async_AsyncSendConstBuffer(void*)`다. `0x90e812`의 encrypt-copy와 `0x90e843`의 memcpy는 원본 입력을 소켓 소유 출력으로 옮긴다. 원본도 모든 전송이 무복사인 구조는 아니다. 이전 ABI8은 모든 framed SEND에서 private request bytes를 제자리 암호화했다. 상수 입력을 유지하는 경로는 별도로 없었다.

ABI9에서는 실제 gather SEND를 상수 경로로 연결했다.

```text
L1 Java FIFO + CIOBufferEx
 -> CSocket.SendConstBuffer
 -> C ABI copies source before return
 -> CExecutionSerializerFast::AddExecutable(MemoryObject*, ...)
 -> CSocket::__async_AsyncSendConstBuffer
 -> socket-owned 16K pooled output에 복사·프레임별 암호화
 -> 준비한 chain을 WRITE FIFO로 이관
 -> 기존 부분 완료·다음 WSASend·종료 수명
```

- 원본 request 데이터는 암호화로 변경하지 않는다. 외부 Java 원본은 downcall 반환 전에 복사하며, 대기 요청은 소켓 참조와 2MiB 예약량에 포함한다.
- L1은 최대65535-byte frame이라 원본 L2의16K 한계를 그대로 적용하지 않았다. native pool 경계를 넘는 본문은 해당 프레임의 bounded scratch로 한 번 암호화한 뒤 출력 블록에 기록한다. 작은 contiguous 본문은 출력 블록에서 직접 암호화한다.
- 최초 평문, frame별 rolling key, descriptor를 넘는 header/body, 부분 완료 시 재암호화 금지를 유지한다. 전체 frame 검증 후 처리한다.
- 출력 준비가 끝난 chain을 FIFO로 이관한다. 기존 tail 병합 정책은 유지하며 전체 암호문 chain을 다시 복제하지 않는다. 암호화·할당·게시 실패 시 연결을 닫아 진행된 key stream으로 계속 보내지 않는다.
- single SendV와 상수 gather는 같은 native 실행기와 WRITE 완료 경로를 사용한다. 기존 source-copy/close 경합 수정과 FIN 정상 종료를 유지한다.

## 검사 범위

ASan/UBSan 및 TSan의 기존 전체 native 검사에 다음을 추가했다. I/O callback이 없는 MemoryObject 파생 객체의 fast-serializer 수락·가상 소멸, 상수 request 원문 보존, 16383/10/65535/8-byte frame 조합과 header/body의16K 경계 처리, 암호문·FIFO·참조 해제를 검사한다. 기존 close/타이머/색인/부하·NTSTATUS/128 batch 검사도 유지했다.

Java21 production1704개 전체 타입 컴파일, 새64개 export/ABI/Java SHA pin 검사와 소스·심볼 대응 검사를 통과했다. production CUserSocket Windows 검사에는 두 IO worker의 완료 처리를 잠시 막아 실제로 두 packet이 한 gather 요청이 되도록 만들고, 20K frame과 후속 frame의 암호문을 변경하지 않은 codec과 비교하는 검사를 추가했다. 실제 Windows 실행의 최종 상태·커밋·해시는 `WINDOWS_VERIFICATION.json` 기준이다. ABI8 성공은 `research/abi9/WINDOWS_ABI8_HISTORY.json`에 별도 보존한다.

독립 PSS의8개 원본 파일·활성화·pairing/OPEN 계약과 기존 codec/PSS DLL 바이트를 유지했다. 실제 클라이언트·DB·사냥·장시간 운영 성능은 기존과 같이 미검증이다. 클래스 이름·전송 역할의 대응을 원본 전체 바이너리 호환이나100% 복원으로 표현하지 않는다.

## 이번 추가 추적에서 남은 것

| 원본 항목 | 현재 상태와 판단 |
|---|---|
| `CIOObject::SetTimeStamp(long*)` `0x7048cc` | 현재 time 값이 저장값보다 큰 경우 xchg하는 helper를 확인. 원본 helper 자체의 이식은 없음. 현재 native wheel·deadline·monotonic64 갱신은 이미 있음 |
| `CIOObject::SetShortTimeStamp(unsigned long*,unsigned long)` `0x704990` | GetTickCount+인자가 저장값보다 큰 경우 xchg. 함수명만 보고 패킷 도배 차단이라고 분류하지 않는다. 실제 소비자별 용도를 더 대조해야 함 |
| 동일 immutable native buffer의 여러 유저 재사용·제외 대상 broadcast | 이번 상수 경로는 FFM 입력을 요청별로 소유한다. 원본 functor `CAsyncSendConstBufferExceptSelf`까지 그대로 이식한 것은 아님. L1의 기존 broadcast 기능과 별개로 공유 수명·암호 키별 출력 연결을 검토할 수 있음 |
| 실제 클라이언트 MAC/HWID 공급 | 서버 API·정책은 이미 있고 실제 클라이언트 전송/검증 자료가 필요한 별도 연동 |

최근128개 패킷 기록·추가 운영 계측은 앞선 선택적 진단 후보로 유지하며 이번 네트워크 실행 경로 작업에 섞지 않았다.

추가 범위 검사는 ABI8의 전체 소스 해시를 기준으로 네트워크 leaf 클래스와 root Server 외1,684개 파일의 바이트 불변을 확인한다. 첫 Windows 실행은 DLL ABI9와 self-test의 구 ABI8 기대값 불일치로 중단됐다. DLL/self-test가 같은 ABI 상수를 사용하고 빌드 시 Java ABI도 비교하도록 수정했다. 기록은 logs/abi9-windows-attempt1-failed.log.
