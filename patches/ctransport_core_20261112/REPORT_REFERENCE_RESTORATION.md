# 네트워크 종료 참조 검사·종류별 추적·내부 규약 대조 — 2026-11-13

요청한1·2번을 기존 네트워크 실행 경로에 구현했고,3번의 원본 내부 규약을 재대조해 확인된 수명·타입 계약을 반영했다.4번은 제외했다. 원본 C++ 소스와 MSVC ABI를 완전히 복제했다는 뜻은 아니다.

새 전체src와 CTransport.dll을 함께 적용하고 clean build한다. SQL 변경은 없다. 매니저 **보고서 → 풀·회수 진단**에서 조회한다. 기존 정상 종료·레지스트리·타이머·풀을 재사용하며 별도 종료 엔진이나 로그 파일을 추가하지 않았다.

## 1. CUserSocketReleaseVerifier

- 소켓의 실제 OS close가 시작된 기존 retireSocket 경로에서 검사기를 등록한다. 등록/I/O/패킷 정리 자체는 기존 실행 경로가 담당한다.
- 첫 검사는5초 후 실행한다. read/write 진행, callback, 빌린 packet, 등록 해제 대기, pending SEND 등이 있으면 정상 보유 중으로 기록하고25초 뒤 한 번 더 확인한다. 그래도 보유 중이면 그 상태를 기록하고 검사를 마친다.
- 상태는 **추가 참조 없음 / 원인 미확정 잔여 참조 의심 / 정상 정리 대기 / 취소**로 구분한다. 의심은 누수 확정 판정이 아니다. 단기 API 참조도 관측될 수 있다.
- 검사기가 대상 소켓을 안전하게 보유하는 ORT_VERIFIER_RETAIN 참조1개를 잔여량에서 제외한다. 참조가 남았다는 이유로 객체를 강제 해제하거나 연결 종료를 다시 실행하지 않는다.
- 대상 objectId32·socket number·generation handle·마지막 account ID·최초 native 종료 사유와 각 참조 정보를 고정 크기 기록에 남긴다.
- 검사기는 서버당 최대128개, 최근 기록은16개, 매니저 표시는 최근8개다. 상한 초과는 생략 횟수로 남긴다. 기존 task table/타이머에서 실행하며 서버 종료 시 취소·회수한다.
- 검사기가 소켓을 잠시 보유해도 native I/O와 packet lease가 끝나면 수신 풀 버퍼는 finalizeRegistration에서 먼저 반환한다.

원본 근거: 생성자0xa12674, TimerExpired0xa1291c, 대상 Release 반환 검사0xa12a56/59/5b. 원본은 L2 User 상태에 따라5초/15초 등의 경로와25초 재예약을 사용한다. 이번5초+정리대기25초는 L1 종료 수명에 맞춘 정책이며 L2 게임 상태 분기를 이식한 것이 아니다.

## 2. 원본 ORT31종·획득/반납 위치

원본0xcc8430 포인터 테이블의31개 이름과 인덱스를 읽어 적용했다. ORT_TIMER=2, ORT_IO_RETAIN=3, ORT_IOCP_MESSAGE_RETAIN=4, ORT_IO_SOCKET_WRITE=20, ORT_VERIFIER_RETAIN=22, ORT_USER_SOCKET_PLAY_MAP=27, ORT_USER_SOCKET_CHAR_MAP=28, ORT_SERIALIZED_MESSAGE=29 등을 사용한다. 전체 목록은 native/reference_trace.hpp와 EVIDENCE.json에 있다. enum 타입명 ObjectReferenceType은 L1 어댑터 이름이다.

CUserSocket마다 종류별 count와 last add/release 파일ID·행을 유지한다. 원본 슬롯32바이트/count+0x10 형태를 따르되 포인터 대신 묶인64비트 파일ID·행으로 위치를 저장한다. 문자열/힙 할당과 추적용 전체 잠금은 없다. 잘못된 종류로 과반납하면 종류별 오류를 누적한다. 물리 참조의0 이하 감소 방지도 유지한다.

| L1 보유자 | 적용 종류 |
|---|---|
| 최초 생성 | ORT_CREATE_DELETE |
| 타이머/네트워크 deadline | ORT_TIMER |
| WSARecv/WSASend 완료 대기 | ORT_IO_RETAIN |
| IOCP 재게시 | ORT_IOCP_MESSAGE_RETAIN |
| 세대별 native registry | ORT_OBJECT_STORAGE |
| 조회 결과/RAII 임시 범위 | ORT_FIND_OBJECT / ORT_FUNCTION_LOCAL |
| 빌린 plaintext packet | ORT_SHARED_CONTAINER |
| SEND 요청 | ORT_IO_SOCKET_WRITE |
| raw socket map | ORT_SOCKET_MAP |
| socket number/Playing/EnterChar map | ORT_USER_SOCKET_MAP / ORT_USER_SOCKET_PLAY_MAP / ORT_USER_SOCKET_CHAR_MAP |
| 서버 일시 snapshot | ORT_TEMP_USE |
| 직렬 실행 큐 | ORT_SERIALIZED_MESSAGE |

이 표의 L1 보유자 배치는 이번 구현이다. 원본의 모든 callsite를 동일하게 복구했다고 주장하지 않는다. 기존 L1 맵에는 없는 ORT_ACCOUNT_MAP 등의 별도 맵을 진단 이름에 맞추려고 추가하지 않았다. RAII 반납 위치는 소유 scope 생성 위치다. 카운터·물리 참조의 조회 전체는 동시 실행 중 완전한 단일 시점 snapshot이 아니다.

## 3. 내부 규약 대조 결과와 반영 범위

| 항목 | 원본에서 확인한 사실 | 이번 결과 |
|---|---|---|
| MemoryObject retain/release |0x7d6d50은 this 반환,0x7d6eb4는 감소 후 참조 수 반환 | 현재 retain/release도 해당 반환 계약을 제공하고 CUserSocket에서 가상 추적한다. 마지막 감소 직전 추적을 끝내 다른 스레드의 최종 해제와 충돌하지 않게 한다. |
| timer 상태 | 원본+0x0c를 등록 시1로,0x404c88/94 전달 시0으로 바꾸고 마지막 release에서 검사 | 현재+0x0c에 등록 횟수를 유지한다. 다중 L1 deadline을 지원하며 등록/취소/dispatch/소멸 시 해제한다. timer에 연결된 마지막 참조는 해제 대신 오류를 기록해 보존한다. |
| 객체 배치/복사 | ref+8, timer+0xc, ID+0x10 | base24바이트 유지, copy는 ref1/timer0/fresh ID. 타이머 오류를 기존 reference fault와 새 보고서에 기록한다. |
| CIOSocketEx 타입 인자 | 원본 RTTI/signature는 CIOSocketEx<CIOBufferEx<16384>> | 숫자 인자 템플릿을 buffer type 인자로 바꿨다. 기존8K/16K slab layout과 L1 codec은 유지한다. |
| timer wrapper0x704190 |80ms 미만을80ms로 올림, ORT_TIMER retain, timer flag 설정, AddTimer 호출 | 참조·timer flag 수명은 반영한다. 현재 L1의 짧은 deadline/50ms wheel 정책을 바꾸기 위한80ms 전역 clamp는 적용하지 않는다. |
| allocator return0x74e3bc→0x74e08c | 객체 앞4바이트 크기 정보, 크기0x8001 경계, 작은 블록 반납과 큰 블록 free 분기 | 원본 전역 allocator와 L1 DLL의 new/delete·별도 버퍼 풀은 다른 계약이다. 원본 주소/메타데이터를 현재 객체에 적용하지 않는다. 전용 allocator 전체는 미복원이다. |
| vtable·삭제 wrapper | 기존 감사에서 확인한 wrapper·timer 전달 슬롯과 원본 전용 allocator가 연결됨 | 가상 소멸/retain/release는 구현했지만 MSVC의 슬롯 번호·L2 모든 virtual 원형·메모리 배치를 동일하게 만들지는 않았다. 외부 ABI10/67exports는 유지한다. |
| listener Create/Close |0x7066dc socket/bind/listen/event,0x704c7c closesocket/WSACloseEvent | 기존 native 구현과 역할을 재대조했다. L1 worker 자가 wait 방지·종료 drain·등록 수명 보호를 유지했다. Windows start/stop 회귀 검증을 통과했다. 원본 서비스 전체 부팅/종료 순서와 동일하다고 주장하지 않는다. |
|0xab9010 지원 루틴 | handler 호출 뒤 오류25 처리/중단 경로. 오류 테이블0x1015320의25번은 R6025 pure virtual function call | 별도 소켓 송수신 기능으로 취급하지 않는다. 정확한 원본 함수 심볼은 미확정이다. 명령·오류 문자열 근거를 함께 저장했다. |
| worker sync hook |0x705330은 timer dispatch와 가상 callback 호출 | 기존 worker/timer 실행 경로와 대응한다. 별도 CIOThreadCallback의 World_Sync/GC는 게임 계층이므로 건드리지 않는다. |

원본 확인 도구는 PE pdata/분기 및 명령 바이트를 사용한다. 첨부된 x64 L2Server(10).exe SHA256=9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588을 대상으로 했고, 원본 실행이나 이번 x86 전체 재검증은 하지 않았다. research/reference-restoration/audit_reference_contract.py로 명령·ORT 테이블·R6025 근거를 재검사할 수 있다.

## 검증·배포

- 전체 기존 native ASan/UBSan 회귀 검사 통과. 완료 API는 Linux fixture다.
- 새 참조 검사 ASan/UBSan 및 TSan 통과:80,000 동시 add/release 쌍, 위치 일관성, 종류별 음수 진단, timer+0xc/마지막 해제 보호, 검사기128/최근16 상한, 정상/의심/packet 대기, 계정 보존, 재검사 및 stop 취소,8/18/24/1720 word 출력 경계.
- 기존 풀 손상/MemoryObject 오류 주입, typed timer/queue 동시성 회귀 통과.
- Java1,711개 production source 전체 type compile 오류0. 매니저 보고서 모양/최근8 제한/구형 DLL fallback 검사 통과.
- **실제 Windows Server2022/Temurin21 CI 통과:** [run43509707411](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43509707411), tested commit4f77fd4ec11463c0153f140effe09228161ad855. 새5초 참조 검사·31종 균형·검사기 자체 참조 제외·계정 보존·종료 시 취소를 실제 DLL/FFM으로 확인했다. 기존 loopback/session/codec/송수신/공유버퍼/독립 PSS/매니저 검사도 통과했다. 기존 bounded session 부하는 실게임 장기 부하 검사가 아니다.
- DLL SHA256=b37e57e850ceb7134ff20c3a6950714973cad046a52a89243dec5dcda5ac4be1. ABI10/67exports 유지.
- 직전 runtime03b14538 대비 변경: network Java4개/native6개+header1개. 나머지 source1,708개, PSS DLL과 codec DLL 바이트 동일. 최종 runtime은 Windows-tested commit과 바이트 대조했다.

새 추적은 소켓마다 고정 크기 카운터와 원자 연산을 추가한다. 실게임 장기 부하에서의 추가 비용은 측정하지 않았다. 정해진 진단 상한에서 생략된 검사는 skipped로 확인한다. 과거 감사 문서는 각 문서의 당시 commit 기준이며 이번 결과는 이 보고서와 WINDOWS_VERIFICATION.json을 따른다.

요청대로4번인 실제 MAC/HWID 클라이언트 연동, 원본 CPerfMon PDH/CSV/mail 전체, 실게임 장기 부하는 추가 구현하지 않았다. 게임 로직·독립 PSS·패킷 codec·DB는 변경하지 않았다.
