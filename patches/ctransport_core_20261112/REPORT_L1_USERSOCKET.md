# CTransport ABI5: native 접속 관리·L1 코덱·네트워크 타이머

이전 배포 기준: 2dfce4f93bfc7a28d3493867dbfe76efadec45be (ABI4). 요청한 다음 세 범위를 모두 구현했습니다. 새 CTransport.dll은 ABI0x50000/49 exports입니다. PSS는 독립 구현과 활성화를 유지합니다.

1. native 접속 관리자: 등록·조회 참조·활성 해제·I/O 종료·빌린 패킷 반환까지 native가 관리합니다.
2. native 유저소켓이 기존 L1 코덱 DLL을 직접 호출합니다. wire가 Java 버퍼를 거쳐 코덱으로 돌아가는 경로를 제거했습니다.
3. 로그인 대기·부분 수신·송신 정체·정상 종료 유예에 native 네트워크 타이머를 연결했습니다. L1 게임 타이머·PSS 타이머는 유지합니다.

제공된 L2 바이너리의 동작을 정적으로 분석해 새 소스로 구현했습니다. 원본 C++ 소스 추출이나 원본 전체의 완전 복원을 뜻하지 않습니다. 아래에서 실제 관찰과 L1 적용을 구분합니다.

## 추가로 확인한 원본 근거

분석 대상: x64 L2Server(10).exe, SHA256 9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588. x86 L2Server.exe는 RTTI 교차 확인 대상이며 상세 주소는 x64 기준입니다.

| 주소 | 명령으로 확인한 범위 | 이번 구현 |
|---|---|---|
| 0xa0ccc4 | CUserSocket 생성자: 기반 생성자0x90e548 호출, vtable0xdc1c18·opcode table +0xc0 설정, 별도 atomic 번호 +0x4e8 생성 | L1 native 유저소켓과 기존 코덱 컨텍스트를 접속당 생성 |
| 0x90e548 | 전달받은 RDX를 +0xb0에 저장, +0xcc 등 파서 필드 초기화 | OS 소켓과 외부 접속 핸들을 구분. +0xb0을 캐릭터/계정 ID라고 가정하지 않음 |
| 0x8b0f1c | manager+0x40 tree 초기화, +0x60 임계 구역 초기화(spin4000),0xb04410에서 전역 생성 호출 | native ordered map과 잠금. 원본 STL 메모리 배치·spin 수치는 복제하지 않음 |
| 0x7ad220→0x8b0404 | 전달된 key/socket object 등록. 임계 구역에서 virtual+8 참조 획득 후 key/pointer pair를 tree에 삽입 | native manager 등록 시 참조 획득, 조회 시 참조 획득, 정확한 객체만 제거 |
| 0xa12dec→0x8b04fc | +0xcc=2, +0xb0을 xchg0, manager tree에서 key 조회, virtual+0x10 참조 반환 및 erase | active→retiring 전환 후 종료 통지·OS close. 실제 I/O와 plaintext lease가 끝나면 manager 참조 반환 |
| 0x8b0954 /0x8b0bac | manager 잠금 안에서 tree 순회·참조 획득, 잠금 밖에서 대상 호출·참조 반환 | 서버 종료 snapshot도 참조를 확보한 뒤 잠금 밖에서 종료 |
| 0xa0372c | 헤더/본문/중지 상태0/1/2, 길이·복호화·opcode 호출 | 기존 ABI4 L1 파서를 유지하고 그 뒤 native codec Feed/Next 직접 연결 |
| 기존 CIO timer 분석 | 50ms/200슬롯 wheel·장기 heap·등록 스레드 소유·참조 보유 | 같은 native wheel에 연결별 네트워크 deadline 작업 등록 |

research/recover_registry.py가 입력 해시를 확인하고 research/registry에 함수 범위·호출·명령·manager/vtable 참조를 기록합니다. 이웃 함수는 분석 후보이며 모두 manager API라는 뜻이 아닙니다. 특히0x8b0008·0x8b0340·0x8b0674는 tree 삽입 계열 helper입니다. 0x8b0674를 공개 socket 조회 함수라고 단정하지 않습니다. 원본의 독립적인 공개 lookup API 전체를 복원했다는 주장을 하지 않습니다.

이전의 파서·handler·지연 종료·worker TLS 근거는 research/user-socket, 버퍼·timer 근거는 research/scope에 보존했습니다. 함수의 역할 이름은 분석용 명칭이며 복원된 원본 심볼이 아닙니다.

## native 접속 관리자

native/user_registry.hpp의 CUserSocketManager가 게임 네트워크 객체의 기준 등록소입니다. 64비트 slot+generation 핸들은 OS SOCKET 및 L1 캐릭터 ID와 구분하며 L1/FFM 적용을 위한 정책입니다. 종료 시작 시 활성 조회에서 빠지고 retiring에 남습니다. 송신·새 작업은 active만 조회하고 패킷 반환은 retiring도 조회합니다. 이전 generation·잘못된 sequence·다른 객체에 대한 제거는 거절합니다. 슬롯 세대는 서버 재시작·capacity 변경 시에도 보존합니다.

kernel I/O 종료 통지 후에도 빌린 plaintext가 있으면 native context를 유지합니다. 마지막 반환 시 정확히 한 번 등록 해제합니다. Java CTransportServer의 map은 FFM/게임 owner를 찾는 연결표이고, CUserSocketRegistry는 계정·월드 정리의 수명 관리입니다. native packet/socket 참조를 Java map에만 맡기지 않습니다. 계정·캐릭터·DB 처리는 기존 L1 코드가 담당합니다.

## 코덱 직접 연결과 실행

TCP/IOCP → C++ Header/Body/Stopped 파서 → C++에서 기존 CIOPC_Feed/Next → 빌린 plaintext 한 개 → FFM packet upcall → 기존 L1 정책/CPacketHandler → native 반환.

Java는 시작 시 기존 CIOPacketCodec.dll의 해시와 export를 확인하고 함수 주소 7개를 native에 넘깁니다. DLL은 global arena로 유지합니다. C++가 접속별 코덱을 생성·폐기하고 Feed/Next/Encrypt/Pending을 직렬화합니다. L1 암호 구현·wire 포맷·키 진행은 변경하지 않았습니다. 기존 codec SHA256은60bde93c2ba20057e7e307594edef69ced273b69f923535ab658769e09f0cea9입니다.

동시에 빌려주는 plaintext는 접속당 한 개입니다. 다음 Next·저장소 확장은 반환 뒤에만 가능하며, 이미 받은 READ의 나머지 프레임은 codec에 대기시킵니다. lease가 있으면 새 kernel READ를 게시하지 않습니다. 일반 게임 opcode는 IOCP에서 실행하고 로그인/세션 전환은 기존 L1 owner에 게시합니다. 외부 스레드의 반환은 IOCP Resume으로 이어집니다. SEND backpressure 대기·게시 대기 작업은 종료 시 취소하고 실행 중 handler는 finally에서 반환합니다. 세션 캡처 예외도 예약 중 lease를 반환합니다. native Stop 중 반환 downcall은 허용하고, Stop 및 반환이 모두 끝나야 FFM arena를 해제합니다.

L2 opcode·crypto·최대0x4000·handler bool·고정5/15초 월드 규칙은 가져오지 않았습니다. L1 wire는 empty2 또는10..65535이며 일반 L1 handler/세션 stamp/하위 프로토콜 검증을 유지합니다.128배치는 기존 추가 정책이고 원본 단일 GQCS와 구별합니다.

## 네트워크 타이머

CIOSocketFactory의 Java wheel LoginTimeout/SocketDeadline을 제거하고 native 연결별 deadline으로 교체했습니다. 로그인 전60초를 유지하며 최초 인증 상태 통지를 받으면 해제합니다. 부분 헤더/본문30초와 native SEND 진행30초를 native에서 확인합니다. 수신 backpressure로 멈춘 시간은 partial 기한에서 제외합니다.

Java에만 있는 송신 예약·정상 종료 유예·거절 응답 grace 조건은 native timer upcall에서 기존 상태 잠금을 통해 확인합니다. 정상 주기는 최대5초, 더 가까운 유예 종료는 requestEarlier로 앞당깁니다. 연결 종료 시 deadline을 취소하고 참조를 반환합니다. 별도 네트워크 전용 Java wheel 작업을 남겨 병행 실행하지 않습니다. 게임/스킬/버프/L1 owner의 일반 timer와 독립 PSS의 timer는 변경하지 않았습니다.

## 검증

- 실제 생산 native 엔진 ASan/UBSan·TSan 통과. Windows API는 fixture입니다. 기존 배치128·부분 송수신·풀·IO참조·timer·종료 검사에 generation/retire/재시작/3200개 동시 등록, 직접 codec lease, 종료 후 반환, 잘못된 sequence, Stop drain, native login/auth/partial/earlier deadline 검사를 추가했습니다.
- 생산 Java1,702개 전체 타입 컴파일 통과. 기존 사용 중단 API 경고5개, 컴파일 오류0개. PE64·ABI5·49 exports·정확한 Java DLL SHA pin 검사 통과.
- Windows 검사는 배포 DLL과 실제 기존 L1 codec으로67개 C2S plaintext/rolling-key 일치와8회 S2C 키 진행, 부분/empty/최대/합친 프레임, plaintext 유지·반환, retired 조회·이전 핸들 거절, login/auth/earlier timer를 검사합니다.
- 별도 Windows 검사에서 실제 생산 CIOUserSocket·buffer pool·ReadBuffer·Cryptor·FFM을 소스에서 컴파일합니다. 게임/계정/actor/scheduler/handler/filter만 fixture로 교체해 실행 중·owner queue·SEND pressure·세션 캡처 예외·종료 lease를 검사합니다. 전체 CIOUserSocket을 대역으로 바꾼 기존 smoke 검사와 구분합니다.
- PSS source/client9개 파일을 직전 배포와 SHA 비교했고 동일합니다. PSS 활성화/pairing/OPEN/logout/restart·격리 계약도 유지됩니다.

실제 Windows ABI5 검사도 통과했습니다. WINDOWS_VERIFICATION.json과 아래 새 실행 기록을 기준으로 합니다. 실제 게임 클라이언트 로그인/사냥·운영 DB·실제 전체 게임 handler·장시간 동접·성능 우열은 여기서 검증하지 않았습니다.

## 적용

전체 src와 새 build/CTransport.dll을 함께 적용하고 clean build합니다. ABI5 src와 ABI4 DLL을 섞으면 시작을 거절합니다. 기존 CIOPacketCodec.dll과 Java21 preview/native-access 옵션은 유지합니다. 추가 SQL 없음. PSS 클라이언트·INI·포트는 유지합니다.

진단: UserSocket_GetState uint64[10] = parserState, expectedWire, filled, frames, invalidLengths, partialAgeMs, borrowed, sequence, codecPendingBytes, L1sessionState. Server_GetRegistryDiagnostics uint64[6] = active, retiring, capacity, registered, removed, staleLookup. ThreadOwner는 현재 서버 worker 번호이며 외부 스레드는-1입니다.

## 실제 Windows ABI5 결과

[실행43309668114](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43309668114), job131502116672: **success**. Windows Server2022 / Temurin21.0.13+9.0에서 검사 커밋9c79d8a9507606d451adc11c33366c90a2e76035를 사용했습니다. 이 커밋의 생산 소스·DLL은 구현 커밋872b0d4와 동일하며 CI 이미지 선택만 다릅니다.

20개 native·20개 FFM 접속/4MB echo, 67개 C2S 복호화 및8회 S2C 키 진행, native registry/lease/login/auth/earlier deadline,64개 대기 완료 작업 취소가 통과했습니다. 별도 소스 컴파일한 실제 CIOUserSocket도 직접 IOCP handler와 실행 중 세션 handler의 종료 후 buffer 유지, owner queue/SEND pressure/owner yield 취소, 세션 캡처 예외 반환을 통과했습니다. 의도적으로 주입한 예외 로그는 오류 경로 검사이며 실패 결과가 아닙니다. L1 정책·400경합 정리와 변경하지 않은 PSS 계약·클라이언트 로드도 통과했습니다.

검사한 전체 src SHA256:8492da4473b92bf9c8f310e8c6e37d1e4e2a72a8ff414a67f7d69acf2491466a.
게임 DLL SHA256:c5160c9e6d93924e6b0ec9bc52d40af93c1e03df9183a3629dd71ba9bba839f2.
이후 결과 기록은 문서·로그·패키지 메타데이터만 갱신합니다. 실제 게임·DB·전체 handler·장시간 부하 검증의 한계는 위와 같습니다.
