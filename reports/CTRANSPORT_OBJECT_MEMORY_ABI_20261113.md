# 전용 객체 할당자·네트워크 객체 배치·MSVC 호출 규약 — 2026-11-13

> 2026-11-14 정정·후속: 원본 CUserSocket은 26슬롯이다. 이 문서의 이전 24슬롯 일치 주장은 원본 추출 길이 제한에 따른 잘못된 판정이었다. 후속 `REPORT_SLOT_SEND_FIX.md`에서 26슬롯과 슬롯 15의 margin 계약·송신 연결을 수정했다. 아래 Windows run과 DLL hash는 11월 13일 배포 이력이며 최신 검증은 WINDOWS_VERIFICATION.json을 참조한다. 일부 원본 필드 배치와 운영 모드는 변경하지 않았다.

요청한 전용 메모리 할당자와 네트워크 타입 배치·가상함수 호출 규약을 구현했다. 제공된 x64 L2Server(10).exe의 명령과 RTTI를 기준으로 한 L1 호환 구현이다. 이 바이너리의 출시 연도가2002년이라는 근거는 확인하지 않았으며, 특정2002년판의 완전 복제나 원본 모든 분기의 동일성을 주장하지 않는다.

**새 전체src와 CTransport.dll을 함께 적용하고 clean build한다.** DLL의 내부 C++ ABI를 맞추기 위해 배포 빌드를 **MSVC x64/C++20**으로 변경했다. Java는 기존 Java21 FFM을 사용한다. 외부 ABI10/67exports는 유지했다. Windows의 x64 Visual Studio Developer 셸에서 `python native/build_native.py`로 DLL·자체 검사·Java DLL SHA pin을 함께 만든다. Linux에서는 `python tests/run_native_tests.py` 및 `python tests/run_object_memory_tests.py [--tsan]`로 검사한다.

## 1. 실제 객체 할당 경로 교체

MemoryObject와 그 하위의 서버·소켓·타이머 작업·송신 요청·종료 검사기가 새 객체 할당자를 사용한다. 직렬 실행 배열과 L1 상태 확장도 같은 할당자를 사용한다. 기존8K/16K 입출력 버퍼 풀은 별도로 유지한다.

| 원본 근거 | 확인한 동작 | 이번 구현 |
|---|---|---|
|0x9ef21c→0x74f0f4→0x74e480 | CUserSocket 전용 new 경로가 공통 관리자로 진입 | MemoryObject의 new/delete와 생성자 실패 반환을 공통 ObjectAllocator로 연결 |
|0x74ef5c |49개 크기 등급, `(4+(i&3)) << ((i>>2)+1)` | 동일한49개 등급과0..32768 조회표 |
|0x74e48f/0x74e5dc/de | 요청 크기에4를 더하고 앞4바이트에 기록, 반환 포인터는+4 | 객체 바로 앞4바이트에 요청+4 기록. 정렬을 위한 실제 prefix 차이는 아래에 구분 |
|0x74e4d5·0x74e08c | 요청+4가32768 이하이면 작은 블록 큐, 그보다 크면 일반 heap | 같은 논리 경계와 할당/반납 경로. 큰 객체는 바로 heap으로 반환 |
|0x74d840·0x74dc8c | CSmartQueue 초기1개 블록/19개 슬롯, 증설 시 수용량2배·슬롯 증가 | 같은 초기값과 증설 규칙·슬롯 큐 재사용 |
|0x74e3d4·0x74dc0c | 큐 연산 최대18개, 독점 증설과 슬롯 교환/CAS 반납 | 같은 동시 연산 상한과 독점 증설. 모든 기존 블록 주소를 유지 |
|0x7d6f74..88 | 마지막 Release에서 가상 소멸자를 호출한 뒤 관리자로 반납 | 명시적 가상 소멸 호출 후 ObjectAllocator::Free. 생성 실패와 일반 C++ delete도 같은 반납기로 처리 |

`CSmartQueue` 이름은 원본 문자열에서 확인했다. `ObjectAllocator`, `ObjectAllocated`, `Alloc/Free` 일부 보조 이름은 L1 구현 이름이며 원본 심볼을 확인한 것으로 표시하지 않는다.

추가한 보호는 크기·정렬 overflow 거절, 생성자 예외 시 반환, 과도한 정렬을 요구하는 객체 처리, 검사 빌드의 ASan poison/unpoison이다. 일반 해제 후 작은 블록은 재사용되며, 프로세스/DLL 종료 때 보유 slab을 반환한다. 단순히 풀 확보량이 유지된다는 이유로 누수라고 판정하지 않는다.

## 2. 원본에서 확인한 타입 계층·메모리 위치

추가 중간 클래스였던 CIOSocket을 실제 타입 계층에서 제거하고, 원본 RTTI의 **CISocket**을 반영했다. `CIOSocket` 표기는 기존 L1 코드에서 사용하는16K 템플릿의 타입 별칭이다. MemoryObject의 할당 지원도 상속용 믹스인 대신 정적 연산자로 제공해 원본 계층에 추가 기반 타입이 끼지 않게 했다. CIOBufferEx는 원본 RTTI와 같은 class 태그, IServerCallback은 struct 태그를 사용한다.

- `MemoryObject → CIOObject → CISocket → CIOSocketEx<CIOBufferEx<16384>> → CSocket → CUserSocket`
- `MemoryObject → CIOObject → CIOServer → Server`
- `MemoryObject → CIOObject → CUserSocketReleaseVerifier`
- `IServerCallback → CDummyServerCallback`

| 타입·영역 | 구현·검사한 위치 |
|---|---|
| MemoryObject/CIOObject/CISocket |24바이트, ref+8/timer+0xc/ID+0x10 |
| CIOServer |40바이트, listening SOCKET+0x18/event+0x20 |
| Server의 확인된 앞부분 | factory 포인터+0x90. 확인되지 않은0x28..0x8f는 예약 영역이며 L1 운영 상태는 그 뒤에 둠 |
| CIOSocketEx의 앞부분 |0xb0바이트, SOCKET+0x18/32비트 송신 예약 지표+0x20/serializer+0x28 |
| 네이티브 OVERLAPPED | READ+0x58/WRITE+0x78. 실제 WSARecv/WSASend에 이 주소를 전달 |
| 원본 버퍼 포인터 위치 | READ+0x98/WRITE+0xa0. L1 버퍼 view의 원시 slab 주소를 기록 |
| CSocket |0x100바이트, IPv4 주소+0xb8, SetAddress/GetAddress 연결 |
| CUserSocket |0xcd8바이트, ORT 슬롯+0x100/count+0x110/add line+0x114/release line+0x118, 슬롯 간격32, 소켓 번호+0x4e8 |
| CUserSocketReleaseVerifier |40바이트, 대상 소켓+0x18/caller+0x20 |
| CDummyServerCallback |16바이트, factory 함수 포인터+8 |
| CExecutionSerializerFast |48바이트, head0/tail8/pending0x10/owner0x14/capacity0x1c/Args0x20/Funcs0x28 |

L1에만 필요한 mutex·레지스트리 상태·세대 핸들·codec 상태·정리 단계·추가 타이머 정보는 할당 prefix의 확장 포인터를 통해 별도 영역에 둔다. 원본의 알 수 없는 필드를 임의의 의미로 채우지 않았다. CUserSocket의 L2 게임 프로필 영역은 예약 바이트로 유지하며 L2 게임 동작을 구현한 것으로 세지 않는다.

## 3. 실제 가상함수·직렬 실행 경로

MSVC에서 실제 vtable 함수를 호출하는 검사로 인자 전달과 결과를 확인했다.

- MemoryObject: destructor0, retain1, release2, reference report3. 파일/행/참조 종류 순서와 release의 다섯 번째 인자 위치를 반영했다.
- CIOObject: timer4/6, timer 전달5/7, IOCP 게시8/11, wait9, `OnIOCallback(int,unsigned,OVERLAPPED*)`10. 기존 L1 오류 정보는 호출 문맥으로 전달한다.
- 소켓: Initialize12/OnCreate13/송신 허용14·15/Send16/SendV17/GracefulClose18/OnClose19/OnRead20/SetAddress21/GetAddress22/OnSending23.
- 서버: CreateSocket12/Remove13/Add14/Shutdown15. CIOServer에는 factory12까지, Server에13..15를 선언했다. L1 전용 알림은 별도 함수 포인터로 처리해 슬롯을 밀어내지 않는다.
- factory는 `CreateSocket(SOCKET)` 한 인자로 호출한다. L1의 여러 서버 인스턴스는 호출 범위 안의 TLS 문맥으로 소유 서버를 연결한다. 기존 정확한 소켓 일치 제거는 같은 방식으로 보존한다.
- GetAddress는 원본의 this·반환 버퍼 전달 형태를 명시적 출력 포인터로 구현했다.
- CExecutionSerializerFast는 분리된512개 Args/Funcs 배열과1 성공/-1 실패 반환을 반영했다. L1 bool 검사에는 TryAddExecutable 어댑터를 사용하며 실행 엔진은 하나다. 기존 세대별 게시 보호·참조 보유·504개 admission 한도는 유지한다.
- 원본0x70429c의 `c/C/h/d/Q/p/f/S/s/b` 포맷 조립을 연결했다. Send→SendV가 L1 프레임·기존 codec·FIFO를 거치며 OnSending은 암호화 전에 호출된다. 경계 초과와 잘못된 블록 길이는 거절한다.

원본 심볼을 확정하지 않은 슬롯14/15와 몇몇 전달 함수는 슬롯 역할을 표시하는 어댑터 이름을 사용한다. 기존 packet lease·Java 정리·독립 PSS를 다시 만들지 않았다.

## 4. 매니저 진단

기존 **보고서 → 풀·회수 진단**에 프로세스 전체 native 객체 사용량, 요청 바이트, 대형 객체, 누적 할당/반납, 실패, 반납 오류,49개 크기 등급별 사용·확보량을 연결했다. 고정 크기 snapshot을 요청할 때 조회하며 파일 로그를 추가하지 않는다.8/18/24/1720 word 진단 호출은 유지하고2030 word 호출에 객체 할당자 snapshot을 추가했다. 외부 export 추가는 없다.

## 5. 원본과 다른 부분·검증의 한계

- 원본은 포인터 바로 앞4바이트를 사용한다. 현재 C++ 원자 변수와 객체의 정렬을 보장하기 위해 실제 prefix는16바이트이며, 크기4바이트 위치와 논리 등급 기준은 유지한다. 과도한 정렬은 별도 큰 블록으로 처리한다.
- 큐 증설에서는 빈 pop 티켓이 지난 슬롯도 보존하도록 모든 비어 있지 않은 슬롯을 회수한다. 확보 메모리·live 수치와 L1 확장 상태 때문에 전역 관리자 전체의 이진 메모리 배치를 복제한 것은 아니다.
- ORT 파일 포인터/행 위치는 반영하되 예약 필드에 슬롯별 sequence를 사용해 위치를 일관되게 읽는다. 동시에 읽은 전체 참조 수와 종류별 합계는 단일 시점 snapshot이 아니다.
- 원본의 L2 게임 상태·타이머 메시지 분기·소켓 암호화/패킷 규약·전역 서비스 부팅 순서는 L1 정책을 사용한다. timer+0xc도 여러 L1 작업을 지원하는 횟수로 유지한다. 송신 예약 지표와 실제 FIFO 잔여량은 L1 admission에서 함께 계산한다.
- 원본의 의도적인 NULL 크래시·operator delete 금지 정책은 적용하지 않았다. 진단 가능한 실패와 정상적인 생성자 rollback을 유지한다.
- 기존 요청의128개 완료 배치·세대 핸들·명시적 취소·PSS 독립 실행은 유지한다. 원본 단건 IOCP를 그대로 되돌린 것은 아니다.
- 원본 기계어를 DLL에 삽입하거나 원본의 모든 함수를 현재 객체에 직접 연결할 수 있게 만든 DLL이 아니다. 확인된 배치·상속·호출 규약과 동작을 재구현한 것이다.
- MAC/HWID 클라이언트 공급, CPerfMon PDH/CSV/mail 전체, 실제 게임·DB 장기 부하는 앞서 제외한 범위다. 새 할당자가 더 빠르거나 전체 메모리를 줄인다는 비교 성능 수치는 측정하지 않았다.

## 검증 근거

- 원본 SHA256:9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588. audit_object_memory.py가15개 구체적 명령,49개 등급, 경계가 확인된 vtable·RTTI 계층을 검사한다. 원본 EXE를 실행하지 않았다.
- ASan/UBSan 및 TSan:49개 등급/32768 경계/정렬/생성자 실패/2,000개 동시 보유/12개 스레드120,000회 재사용/overflow, 실제 슬롯 호출·필드 위치·포맷·게시 실패 rollback·정상 회수. 소멸 후 live 객체·바이트0.
- 기존 전체 native 회귀 검사 통과. Linux 완료 API는 fixture다. 별도 참조 검사기·ORT 동시성 검사도 통과했다.
- 전체 Java1,711개 production source type compile 오류0. 게임 의존성 fixture를 사용하는 Windows owner 검사와 범위를 구분한다.
- **최종 실제 Windows CI:** https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43527192672, source commitd4f46c2caf98b842c713e5a185222e5f5e2635c0, artifact commit8136ae812e3f1204f8c54592d56e47b2d5daf042. MSVC 객체 ABI 검사와 실제 DLL/Java FFM 송수신·계정·codec·종료·공유 버퍼·참조 검사·독립 PSS·매니저 회귀 검사를 통과했다.
- DLL SHA256=9b8992db78c851d76522e30406a8049b84e08b5a53af395689d5847154dad6a1. ABI10/67exports. 배포 DLL과 Java pin은 해당 성공 run에서 생성·검사한 파일이다. 배포 시19개 native source와 Java pin을 Windows 체크아웃의 CRLF/LF 차이를 정규화해 대조했다. DLL·lib·자체 검사 EXE는 검사한 바이트 그대로다.
- 추가 PE 검사로 실제 DLL에 생성된 RTTI 계층과 vtable 길이를 원본과 대조한다. CUserSocket6개 기반 타입/24슬롯, Server4개/16슬롯, verifier3개/12슬롯, callback2개/2슬롯을 검사한다.
- fa68825 대비 변경은 network Java4개/native7개+신규3개이며 나머지 source1,708개, codec DLL·PSS DLL 바이트는 동일하다. 새 SQL은 없다.
