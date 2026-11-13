# 현재 네트워크와 원본의 대조 결과

2026-11-13. 대상은 배포 **03b14538d2a813007f4b964ff207ae6aeb8f54ac**다. 최신 소스·DLL을 원본 x64와 대조했다. 이번 작업은 보고서·분석 도구만 추가하며 실행 코드·DLL·배포 ZIP은 바꾸지 않는다.

**접속 수락부터 송수신·직렬화·버퍼·타이머·등록·종료/회수까지 현재 실행 경로가 있다. 원본 구조의 핵심 역할을 L1에 맞춰 구현한 상태다. 원본의 모든 C++ 클래스 ABI·게임 프로필·오류 분기가 그대로 복원된 상태는 아니다.**

명확히 구분되는 미이식 진단은 **종료 뒤 양수로 남은 native 참조 확인**과 **CUserSocket 참조 종류별 카운터/호출 위치 추적**이다. 현재 정상 종료 처리가 없다는 뜻은 아니다.

## 주요 구간의 실제 상태

| 구간 | 현재 확인 | 원본과 남은 차이 |
|---|---|---|
| 접속 수락 | FD_ACCEPT → RegisterWait → IOCP → factory/admission/I/O 초기화 | L1 입장·seed/codec 정책 적용 |
| IOCP worker | 실제 kernel 완료 처리·항목별 오류·owner 참조 해제 | 원본 단건 GQCS를 요청한 GQCSEx128로 확장 |
| 수신·버퍼 | 8K/16K slab·필드/stride·owner 반환·COW/compaction | adapter view·외부 생산자·격리·고갈 실패 정책 |
| 송신 | native serializer512/504·암호화 순서·FIFO·부분 완료·재게시 | L1 codec·추가 안전 처리이며 원본 모든 분기와 동일하지 않음 |
| 타이머 | CThreadLocalTimer/CSlot/CMwmrQueue4009·50ms/200칸/1000개 블록·heap | uint64 clock·취소 인덱스·bool admission·L1 Dispatch 계약 |
| 소켓 등록·상태 | Add/Remove/Shutdown·번호/Playing/EnterChar 맵·세대 핸들 | 원본 MemoryObject 진단 ID와 연결 핸들을 분리 |
| 정상 종료·회수 | close-start·I/O drain·packet lease·native terminal·Java close/cleanup | 원본 ReleaseVerifier의 지연 참조 진단 전체는 없음 |
| 입장 제한 | IP/MAC 서버 카운터·검사·identity bind | 실제 MAC/HWID 공급·검증 클라이언트 미연동 |
| 운영 진단 | owner별 시간·소켓/큐·최근 metadata·풀/격리·MemoryObject 오류 | 원본 per-reason ref·모든 lock stack/payload·PDH/CSV/mail과 다름 |
| C++ 타입 | MemoryObject/CIOObject/CIOServer/CIOSocketEx/CSocket/CUserSocket 존재 | CIOSocketEx의 type 인자와 공통 CIOSocket 구현, allocator/vtable 전체는 다름 |

## 원본에 있는데 현재 그대로 없는 부분

### H01 — 종료 후 양수 잔여 참조 진단

원본 **CUserSocketReleaseVerifier** 생성자 `0xa12674`, TimerExpired `0xa1291c`의 본문과 이름을 확인했다. TimerExpired는 상태 메시지에 따라 재예약·맵 정리 등을 진행하고 마지막에 대상 socket의 virtual Release 결과를 검사한다.

- `0xa12a56`: virtual Release 호출.
- `0xa12a59`: 반환한 ref 결과를 test.
- `0xa12a5b`: 0 이하이면 진단을 건너뜀. 양수이면 account/refcount/caller와 `ORT - UserSocket Release failed`를 출력.

현재 MemoryObject::release는 **void**이고 ref가0인 객체에 추가 release가 들어오는 경우를 기록한다. 종료 뒤 양수 ref가 남는 경우를 검사하는 원본 절차와 다르다. native active/retiring 관리와 packet lease, Java 정리는 이미 있다. 따라서 다음 구현 후보는 기존 종료 처리 전체를 다시 만드는 것이 아니라 **그 처리 이후 남은 참조를 어떻게 관측할지**다. 원본 생성자의 L2 User 상태·고정 지연·메시지를 그대로 복제하면 현재 수명 관리와 중복될 수 있다.

### H02 — CUserSocket의 참조 종류별 추적

원본 `0x9f773c` / `0x9f7770`은 전체 ref 외에 subtype별 카운터와 마지막 add/release의 file/line을 다룬다. `0x9f7998`도 관련 진단 계층이다. 현재는 일반 MemoryObject ref와 마지막 ref0 오류, Java 작업/close 기록이 있으며 이 원본 필드·호출 인자 체계를 그대로 저장하지 않는다.

이 기능은 참조 미해제 원인을 좁히는 진단이다. I/O hold/완료 release가 미구현이라는 뜻이나 이번 audit에서 실제 운영 누수를 재현했다는 뜻은 아니다.

### 별도로 열려 있거나 선택이 필요한 부분

- **MAC/HWID**: 서버 API와 enforcement는 있다. 실제 클라이언트 송신·검증 연동이 미완이다.
- **원본 운영 출력**: CPerfMon의 전체 PDH/CSV/mail, 모든 TLS lock/signature stack·payload dump는 부분 대응이다. 필수 송수신 기능과 분리한다.
- **원본 전체 시작/정지 분기**: 현재 네트워크 Run/stop/restart는 검증됐다. L2 상위 서비스의 조건부 분기 전체가 네트워크와 어디까지 연결되는지는 완결하지 않았다.
- **동일한 내부 C++ ABI**: 같은 이름이 있어도 원본 타입 인자·vtable·allocator·모든 필드를 복제한 것은 아니다. MemoryObject의 원본+0x0c timer flag도 별도로 이식하지 않았다. 현재는 timer hold와 완료 release로 수명을 관리한다.
- **실운영 검증**: 실제 게임 클라이언트·운영 DB·장기 동접 검증은 이번 자동 검사 범위 밖이다.

L2 게임 opcode, Ship/Creature/Fishing/World actor serializer의 game 역할, AtomicAddTimer의 L2 job packet, 독립 PSS는 네트워크 미구현으로 세지 않는다. 원본의 의도적 NULL 접근 위반 대신 현재 L1 실패 반환 정책을 유지한 것도 이미 알려진 적용 차이다.

## 이번 대조에서 바로잡은 상태

이전 ABI5 보고서의 미이식 표시는 당시 snapshot이다. 현재 코드에서는 **사용자 번호/Playing/EnterChar 맵, FD_ACCEPT 접속 수락, socket buffer 옵션, worker affinity, listener17, MAC 제한 서버 API, 2MiB pending write/SafeSend, peer 진단**의 대응 코드가 있다. 과거 표를 현재 남은 일로 다시 제시하지 않는다.

또한 `IOThreadCallback`과 `CIOThreadCallback`은 원본에서도 구분된다. `0x705708`이 직접 참조하는 signature는 **IOThreadCallback::IOThread_common**이며 현재 worker 이름과 같다. 원본 **CIOThreadCallback**은 다른 RTTI 타입으로 World_Sync/garbage collect 본문을 가리킨다. 이 이름을 현재 worker에 붙이지 않은 것은 맞는 구분이다.

기존 census의 일반적인 CORE_ROLE_MAPPED 표시는 함수 원형/모든 분기가 동일하다는 판정이 아니었다. 이번에는 game actor serializer·Atomic job packet과 공통 소멸자·빈 virtual 기본 구현까지 구분했다. IOThread_debug도 확인한 본문에서는 공통 worker를 호출하는 trace wrapper이며 별도의 미구현 I/O 엔진이라고 세지 않는다.

## 고정한 대조 범위와 수치의 의미

- 이전 식별 목록148개 + 확인한 timer/verifier6개 + 추가 worker 진입점2개 = **선택한156개 함수 진입점**.
- 저장한 함수 범위의 원본 명령 **14,852개**를 고정 SHA의 PE에서 다시 decode하여 보관 disassembly와 대조했다. pdata 함수 범위와 pdata 없는 leaf의512바이트 내 도달 범위도 다시 확인했다.
- 원래33개 기능 행을 현재 코드로 재판정하고, ID/오류 정책/이름/클래스 ABI/CPerfMon5개 행을 추가해 **38개 대조 행**으로 고정했다.

이 수치는 **전체 원본 함수 수나 복원율이 아니다.** 156개에는 L2 game/profile·compiler wrapper도 있으며38개 기능 행은 서로 겹친다. 함수의 module 역할을 연결하고 기계 명령을 재검증했다는 것과 현재 구현의 모든 branch가 같은 것은 다르다. 이름이 없는 전체 PE 함수·동적 간접 호출·모든 예외 경로를 빠짐없이 식별한 감사도 아니다. 새 x86 전수 대조도 하지 않았다. 대상은 현재 게임 접속 transport와 연결된 공통 core다. 원본 CAuthSocket/CDBSocket/CLogSocket 등의 모든 L2 서버 간 통신 프로필은 이156개 목록에 포함한 전수 대상이 아니며, 현재 L1의 DB/인증/로그 연동이 그 클래스대로 복원됐다고 주장하지 않는다.

`FUNCTION_INVENTORY.csv`에는156개 각각의 주소/이름/검토 상태/원본 명령 파일이 있다. `CURRENT_COMPARISON.json`에 원본 주소 검증·현재 소스 행·기능별 차이·검증 근거를 넣었다. `CONFIRMED_GAPS.json`에는 H01/H02와 기존에 있는 역할을 구분했다. `VIRTUAL_REVIEW.json`은 별도로 읽은13개 vtable 연관 함수의 역할과 한계를 기록한다. 그중 원본 메서드 이름과 지원 루틴의 세부 의미를 확정하지 않은 주소 `0xab9010`은 여전히 미확정으로 표시했다.

## 38개 기능 행 현황

| ID | 대조 항목 | 현재 판정 | 현재 코드 근거 |
|---|---|---|---|
| N01 | IOCP dequeue와 완료 분배 | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L884) |
| N02 | 8K/16K slab·소유 풀 반환 | 핵심 역할 있음 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/cio_core.hpp#L91) |
| N03 | 공유 수신 COW·유일 버퍼 compaction | 핵심 역할 있음 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/cio_core.hpp#L200) |
| N04 | 송신 FIFO·완료 길이·버퍼 반환 | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/cio_core.hpp#L223) |
| N05 | 상위 송신 serializer와 암호화 순서 | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/execution_serializer.hpp#L9) |
| N06 | 일반 completion 작업 게시와 참조 | 핵심 역할 있음 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L155) |
| N07 | 50ms/200슬롯·1000-entry block·overflow heap | 핵심 역할 있음 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/timer_wheel.hpp#L61) |
| N08 | timer message/argument·취소·clock | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/timer_wheel.hpp#L130) |
| N09 | 종료 시작 hook·활성 해제·linger0 | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L279) |
| N10 | Server::Add / Remove의 잠금·참조 | 핵심 역할 있음 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L859) |
| N11 | Server::Shutdown snapshot의 참조 수명 | 핵심 역할 있음 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L877) |
| N12 | 수신 parser·opcode 분배 | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/user_socket.hpp#L14) |
| N13 | 코덱 직접 연결 | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/l1_codec.hpp#L31) |
| N14 | packet lease·세션 owner·L1 deferred cleanup | 원본 절차 미이식 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L309) |
| N15 | native 핸들·세대·retiring 조회 | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/user_registry.hpp#L9) |
| N16 | 8,192-bucket 사용자번호 CObjectMap | 핵심 역할 있음 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L96) |
| N17 | FD_ACCEPT→RegisterWait→IOCP 접속 수락 | 핵심 역할 있음 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/io_server.hpp#L87) |
| N18 | factory→생성→등록→I/O 초기화 순서 | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L1006) |
| N19 | SO_RCVBUF/SO_SNDBUF=0 및 listen 정책 | 핵심 역할 있음 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L1017) |
| N20 | IO worker 번호/TLS/owner 식별 | 핵심 역할 있음 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L890) |
| N21 | CPU 수 기반 worker 수·affinity | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L887) |
| N22 | ListenThread owner17의 실제 실행 구조 | 핵심 역할 있음 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L953) |
| N23 | IP별 접속 제한·반환 | 핵심 역할 있음 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/socket_limiter.hpp#L24) |
| N24 | MAC별 소켓 limiter | 클라이언트 연동 미완 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/socket_limiter.hpp#L26) |
| N25 | 원본 2MiB pending write 허용 정책 | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L457) |
| N26 | Server::ReportSocket 전체 peer 진단 | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L1300) |
| N27 | 원본 thread별 I/O 시간/lock stack 진단 | 진단 일부 대응 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L1285) |
| N28 | native 네트워크 deadline | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L725) |
| N29 | 조건부 서비스 스레드와 전체 시작·종료 경로 | 원본 분기 경계 미확정 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L1065) |
| N30 | CObjectMap variant27/28의 모든 소비자 의미 | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L631) |
| N31 | L2 게임 opcode·월드·서비스 초기화 | 요청 범위 제외 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/io_server.hpp#L135) |
| N32 | 실게임·운영 DB·장시간 동접 검증 | 운영 검증 미수행 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/WINDOWS_VERIFICATION.json#L29) |
| N33 | 독립 PSS 보존 | 요청 범위 제외 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/src/l1j/server/server/model/character/pss/window/PssWindowTransport.java#L7) |
| N34 | MemoryObject ID·ref1 복사·참조 오류 | 핵심 역할 있음 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/cio_core.hpp#L14) |
| N35 | 풀 오류 처리·원본 강제 접근 위반 | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/cio_core.hpp#L160) |
| N36 | 확인된 타이머 타입·메서드 이름 | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/timer_wheel.hpp#L70) |
| N37 | C++ 클래스 계층·allocator·전체 ABI | L1 적용·정책 차이 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/CTransport.cpp#L162) |
| N38 | CPerfMon 전체 운영 출력 | 운영 출력 일부 대응 | [코드](https://github.com/choeyeonggeol16-sketch/1234/blob/03b14538d2a813007f4b964ff207ae6aeb8f54ac/patches/ctransport_core_20261112/native/perfmon.hpp#L11) |

핵심 역할 있음13행, L1 적용/정책 차이17행, 원본 절차 미이식1행, 클라이언트 연동1행, 진단 일부1행, 상위 분기 미확정1행, 범위 제외2행, 운영 검증1행, 운영 출력 일부1행이다. 이는 행의 분류 수이며 통과율이 아니다. 세부 차이·다음 판단은 JSON에 기록했다.

## 검증과 재현

현재 DLL SHA256 **01d389928506719781cdcbd1b9042f58a3b9d89786f6fdb6f0bf2572a63afb75**, ABI10/67 exports다. 원본 worker의 단건 GQCS 호출과 현재 DLL의 GQCSEx import를 각각 확인했다. 원본 x64 SHA256은 **9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588**이며 실행하지 않았다.

실제 Windows run **43491457850**, 테스트 source **8812c2e6019ad8380d5cef52801b3f6d40979d75**와 현재 배포의 native/src/config·DLL/자체 검사 EXE/코덱·PSS DLL이 동일한지 Git bytes를 비교했다. 현재 source와 지난 실행 검증의 연결을 확인한 것이며, 이번 report-only 작업에서 전체 Windows 테스트를 반복하지 않았다. 기존 ASan/UBSan/TSan·actual Windows loopback/FFM/타이머/종료/독립 PSS 기록은 배포 안에 있다.

이번 도구는 수동 검토 판정의 근거를 재현한다. 소스 문자열 존재를 이용해 함수 동등성을 자동 증명하지 않는다. 보고서의 현재 코드 SHA, 입력 자료 SHA와 실행 파일 tree를 SOURCE_INDEX.json에 고정했다.

```sh
python reports/ctransport_current_network_audit_20261113/compare_current.py --binary '/path/to/L2Server(10).exe'
```

Python pefile/capstone과 이 저장소의 대조 자료를 사용한다. 소스 target은03b1453의 runtime tree로 고정한다. 그 뒤 런타임을 수정한 checkout에서는 같은 결과를 재현하기 위해 해당 target tree가 필요하다. 이 폴더의 SHA256SUMS로 보관 자료 무결성을 검사할 수 있다.
