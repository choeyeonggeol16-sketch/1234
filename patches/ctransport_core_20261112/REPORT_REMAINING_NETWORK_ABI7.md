# CTransport ABI7 — 남은 색인·thread15·MAC 분석과 L1 구현

기준 커밋 `612303945791e323f87368774ff0926286a53063`(ABI6). 원본 x64 `L2Server(10).exe` SHA256 `9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588`. PE unwind 함수 범위·실제 명령 경계·signature·호출 인자·키 getter·진단 문자열을 함께 대조했다. 원본 EXE는 실행하지 않았다.

**미확정이던 map27·28의 키와 역할 및 thread15의 두 분기 역할을 확인했다. 확인된 역할을 L1의 기존 인증·캐릭터 진입·재시작·종료에 연결하고, 실제 native CPerfMon을 구현했다. MAC은 원본 로그인 패킷에서 전달받는64-bit 식별자라는 경로를 확인했으며 L1 서버의 공급자 API·제한 설정·인증 강제 검사까지 추가했다. 실제 L1 클라이언트의 식별자 전송 코드는 제공 자료에 없다.**

## 바이너리 분석 결과

| 대상 | 원본에서 확인한 키·역할 | 주요 근거 |
|---|---|---|
| map26 | 전체 CUserSocket의 소켓 번호 | 기존 ctor+0x4e8와 번호 map 등록. ABI6 구현 유지 |
| map27 `UserSocketMapPlaying` | **로그인된 소켓을 소켓 번호로 색인**. 이름의 Playing을 IN_GAME 전용으로 오해하면 안 됨 | `_LoginPacket 0xa0fe54 -> IncUserCount 0xa08940 -> 0xa08498`. `vslot+0xc8 -> 0xa01798`은 `[socket+0x4e8]` 반환. DecUserCount `0xa08a48 -> 0xa08660`. Builder `_CheckImitation 0x50ea98`의 map 이름/키와 교차 확인 |
| map28 `UserSocketMapEnterChar` | **캐릭터 진입 소켓을 계정 ID로 색인**, 중복 진입 조회와 재시작/접속 종료에서 제거 | `User::OnEnterWorldPacket 0x982660`: socket+0x7e4에서 account ID → `0x97ec10` Insert. `0xa123e8`의 제거 로그가 `g_UserSocketMapEnterChar`와 `nAccountId`를 명시. RestartPacket `0xa12b14`도 제거 |
| thread15 | `CPerfMon`: 시스템/프로세스 메모리, PDH 성능 카운터, 사용자 수/CSV 보고 | entry `0x725e30`; 이 빌드의 조건 helper `0x53a8fc`는 true. mailing 분기 `0x725488 -> 0x722abc`도 **동일한 `0x82c408`을 반복 호출**한다. 대체 분기 역시 `0x82c408`. `0x82b04c` GlobalMemoryStatusEx/프로세스 메모리, `0x82b0dc` PDH, `0x82c0c0`의 `CPerfMon::ReportUserCount` signature와 perfmon.cpp 문자열로 역할 확인 |
| MAC 식별자 | L2 `LoginPacket`이 읽은64-bit 값을 socket+0xc88에 저장, 인증 시 제한 증가·close 시 감소 | `0xa10c00` packet parse(format `SdddddQd`의64-bit Q) → `0xa10c0a` 저장 → `_LoginPacket`의 `0xa10519/0xa10528` 제한 증가 → close `0xa12e87/0xa12e95` 감소. 바이너리가 이 값을 받는다는 사실이 물리 MAC의 진위 검증을 증명하지는 않음 |

`map27`까지 계정 ID map이라고 설명했던 초기 추정은 getter 대조로 정정했다. `research/remaining-network-audit/verified-instructions.json`에 실제 bytes를 포함한16개 단언을 저장했다. 원본에서 확인한 캐릭터32슬롯/24개 검사와 재진입은 `research/creature-serializer-audit/`에 별도로 보존했다.

## 구현

### 1. native 색인과 L1 수명 연결

- `CObjectMap<CUserSocket,8192,27>`과 `...28`을 실제로 추가했다. Playing의 키는 소켓 번호, EnterChar의 키는 기존 `Account.getAccountId()`다. 별도 DB ID를 만들거나 계정명을 hash하여 키로 사용하지 않는다.
- `CIOClient.setAccount`는 native 계정 바인딩과 Playing 등록을 수행한다. `setActiveChar`는 캐릭터 로딩 시작 전에 EnterChar의 계정 ID를 확보한다. 같은 계정에 기존 진입 소켓이 있으면 새 진입을 거절한다.
- AUTHENTICATED/OUT_GAME으로 돌아오면 EnterChar만 해제한다. 계정 해제와 CONNECTED/DISCONNECTED 전환은 계정·Playing·EnterChar를 정리한다. 정상 종료에서는 close-start에 두 보조 색인을 제거한다.
- 전체 번호 map과 generation registry는 실제 I/O·packet lease 종료까지 유지한다. 삭제는 예상 객체까지 비교하므로 예전 소켓의 늦은 close가 새 소켓의 같은 계정 엔트리를 지울 수 없다.
- `SetAccountIdentity`, `SetCharacterEntered`, `FindEnteringAccount`, `GetAccountRegistryDiagnostics`를 추가했다. 진단4개는 `[전체 retained 번호 수, Playing 수, EnterChar 수, 중복 진입 거절 누적]`이다. 기존 GetState는14 words까지 선택적으로 제공하며 마지막4개는 `[소켓번호,계정ID,캐릭터진입여부,MAC바인딩여부]`다.
- 기존 LoginController의 계정 인증·중복 로그인·DB 소유권은 이어 사용한다. C++ map을 추가했다는 이유로 L1 계정 관리 로직을 제거하지 않는다.

### 2. 실제 CPerfMon thread15

- IOCP worker 및 ListenThread17과 별도로 실제 native `CPerfMon` 스레드를 시작한다. native TLS/풀 owner15와16K buffer100개를 연결했다.
- 프로세스 CPU, working set/commit, 시스템 물리 메모리, 소켓·작업·Playing/EnterChar·콜백 수를 주기적으로 수집한다. native context와 monitor 수명은 shared_ptr의 원자적 publication으로 관리한다. 진단 조회가 서버 종료의 lifecycle lock을 기다리며 I/O callback을 막지 않게 했다.
- 원본의 PDH/운영 CSV/메일 출력 전체를 복사한 것은 아니다. L1 process CPU는 GetProcessTimes, 메모리는 GlobalMemoryStatusEx/K32GetProcessMemoryInfo로 측정한다. 사용 가능 bit가 없는 값0을 실제 측정0으로 해석하지 않는다. Linux API fixture는 OS 가용 bit0이며 실제 OS 값 검증은 Windows에서만 수행한다.
- `-Dctransport.perf.intervalMs=1000` 기본,100..60000ms. sampledAtMillis는 monotonic 시각이다. 각 카운터는 샘플이며 게임 세계와 하나의 transaction으로 읽은 값은 아니다.
- Java `CPerfMon.Snapshot`, 서버 façade `performance()`, root `l1j.server.Server.networkPerformance()`로 조회한다. root의 `networkAccountRegistryDiagnostics()`와 `findEnteringAccount(int)`도 운영 점검에 사용할 수 있다.
- listener 게시를 startup의 마지막 단계로 두어 monitor 시작 실패 뒤 이미 수락된 소켓이 남는 경우를 방지했다. stop은 monitor를 깨워 join한 뒤 수락·작업·소켓을 정리한다.

### 추가로 발견·수정한 native 실행기 경합

TSan이 기존 CExecutionSerializerFast의 producer 슬롯 쓰기와 consumer 정리 사이의 data race를 검출했다. 전역 pending을 예약한 생산자가 ticket 할당 전 지연되면, 나중에 재사용할 슬롯의 해제와 명확한 acquire/release 관계가 없는 경우였다. 슬롯별 available 세대 번호를 추가해 producer가 정확한 재사용 세대를 acquire하고 consumer가 정리 후 다음 세대를 release하도록 수정했다. 512칸/504개 수락/owner 재진입 정책은 유지한다. 원본 기계어를 동일하게 복사한 것이 아니라 C++ 메모리 모델에 맞춘 동기화 수정이다. 수정 전 검출 로그는 abi7-tsan-before-slot-fix.log에 보존했다.

### 3. MAC 공급자 경계와 강제 검사

- `CIOClient.bindVerifiedMacIdentity(long)` 및 실제 CUserSocket API를 제공한다. 서버 측 공급자가 확인한 **0이 아닌64-bit 값**을 인증 전에 바인딩한다. 동일 소켓 재바인딩을 거절하고 close에서 수량을 한 번 반납한다.
- `-Dctransport.macLimit=N` 기본0(비활성),0..100000. 양수이면 native 계정 인증 바인딩 시 MAC 없는 소켓을 거절한다. 따라서 옵션을 켠 뒤 식별자 공급이 빠져 있어도 무제한 통과하지 않는다.
- 현재 L1 로그인 handler에는 계정·암호만 전달되며 MAC/HWID parser나 클라이언트 송신 코드가 없다. 원본 업로드 aden.zip도 `src.zip`, `CIOPacketCodec.dll`, `CIOWinTransport.dll`만 포함한다. 검증된 공급자가 없는 상태에서 이 옵션을 켜면 인증이 거절된다. 기본 설정은 기존 클라이언트와 호환된다.
- IP/계정명/난수를 MAC으로 만들어 넣지 않았으며 기존 PSS 메시지나 클라이언트 DLL을 수정하지 않았다. **실제 L1 클라이언트 식별자 전송·검증 연동은 미구현**이다. 서버 공급자 API와 정책 구현을 클라이언트 연동 완료로 표현하지 않는다.

## 검사

- ASan/UBSan·TSan:16개 동시 계정 진입 중 한 소켓만 성공,128회 계정 재접속, 인증/캐릭터선택/재시작/로그아웃/close map 정리, 늦은 close와 held plaintext 수명, MAC 필수 인증·상한·종료 후 재사용, 실제 owner15·monitor restart·조회와 종료 경합. 기존 network/batch128/codec/타이머/풀/종료 검사도 유지.
- 전체 Java21 production1702개 소스 컴파일과 ABI/hash 강제 검사. Windows용 FFM·실제 production CUserSocket suite 및 production registry/close 계약도 검사한다.
- 실제 Windows에서 실행할 추가 검사는 `CTransportSessionWindowsTest`:32 동시 연결, 최소256접속 및30초 반복, 실제 L1 codec의128 frames/연결, 인증·진입·재시작·close 및 전체 색인0 확인, 실제 CPU/메모리 가용 bit 확인. **실행 결과·횟수·전송량은 WINDOWS_VERIFICATION.json과 abi7-windows-actual.log 기준**이다. 결과를 얻기 전에 성공으로 표시하지 않는다.
- 독립 PSS startup/pairing/OPEN/logout/restart/isolation 계약 통과. 이번 ABI7에서는 PSS 소스·활성화·클라이언트 DLL과 기존 L1 codec DLL을 변경하지 않았다. CIOClient의 기존 PSS 필드/메서드 영역도 바이트 대조했다.

## 실게임·DB·장시간 부하의 한계

현재 자료에는 실행 중인 Windows 게임 서버, 실제 L1 클라이언트, 전체 DB/schema/data, runtime config가 없다. 로컬에도 실행 중인 게임·DB 서비스가 없다. 원본 L2 EXE를 실행해 게임 환경을 대신하지 않는다. 짧은 Windows loopback과 fixture 기반 게임 의존성은 **실게임 로그인·사냥·DB 저장·운영 동접·NIO2 비교 성능 검사와 다르다.**

실운영 검증에서는 제공된 동일 src/DLL을 clean build한 뒤 기존 테스트 계정으로 인증 실패/성공/중복, 캐릭터 진입·선택 복귀·재접속, 전투/사냥 중 강제 끊김과 재접속, DB 저장·이전 세션 정리, 장시간 동접의 CPU/working set/큐·색인 수를 확인해야 한다. MAC은 실제 확인 가능한 클라이언트 공급자 연결이 준비된 환경에서 별도로 검사한다. 이 환경이 없다는 이유로 구현과 실행 가능한 검사를 중단하지 않았으며 미검증을 완료라고 기록하지 않는다.

## 적용

ABI `0x70000`, exports62개. **전체 src와 같은 묶음의 CTransport.dll을 함께 적용하고 clean build**한다. 추가 SQL 없음. 기존 `CIOPacketCodec.dll`, PSS 클라이언트·설정, Java21 preview/native-access 옵션을 유지한다. 부분 적용은 changed-files/deleted-files를 모두 반영한다. 원본 클래스의 메모리/vtable ABI 전체 일치, L2의 모든 서비스/게임 로직 복원을 의미하지 않는다.

실제 ABI7 Windows Server2022/Temurin21 검사 통과: [실행43331551485](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43331551485), 검사 소스 `b538267b0447fe1bd0b06b133cac78c518cb404a`. 동시32개로 30,122ms 동안 **누적53,376접속, 6,832,128 L1 frames, 3,498,049,536 C2S wire bytes**를 처리하고 매 round의 active/retiring/Playing/EnterChar 잔여0을 확인했다. 실제 계정 암호·DB·사냥을 포함한 검사는 아니다. 최종 배포의 소스·DLL·테스트는 이 검사 커밋과 동일하고 후속 커밋은 검증 기록을 추가한다.
