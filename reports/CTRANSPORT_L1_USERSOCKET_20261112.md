# L2 CUserSocket 동작의 L1 호환 재구현

기준 커밋:4c6fa2b1bf1e2184e07ff21e1ee0ebdd1acb1d4b. 요청한 ①수신 파서 ②패킷 실행 ③접속·세션 수명 ④스레드 역할을 현재 L1 팩에 반영했습니다. 새 게임 DLL은 CTransport.dll ABI0x40000/41 exports입니다. PSS 전용 연결·UI·사냥·클라이언트는 그대로 유지합니다.

원본 C++ 소스를 추출한 결과가 아닙니다. 제공된 L2 실행 파일을 정적으로 읽어 확인한 동작을 새 C++/Java로 작성했고, L1의 프로토콜·계정·캐릭터·DB 경계는 L1에 맞췄습니다. 원본 전체의 복원율이나 원본 서버 전체와의 동일성을 주장하지 않습니다.

## 원본의 유저소켓이 맡는 일

CUserSocket은 OS 소켓을 게임 세션과 연결하는 객체입니다. 기반 CIOSocket/CIOSocketEx의 비동기 송수신·버퍼 위에서 길이 헤더와 본문을 구분하고, 복호화 후 opcode 테이블과 세션 상태를 통해 게임 핸들러를 호출합니다. 종료 시 파서를 중지하고 등록 관리자에서 자신을 해제한 뒤, 참조를 가진 후속 작업이 상태별 정리를 수행합니다. 게임 캐릭터의 전투·DB 규칙까지 모두 이 객체에 들어 있다는 뜻은 아닙니다.

| 확인한 원본 명령 | 확인한 동작 | L1 반영 |
|---|---|---|
| CUserSocket vtable0xdc1c18, slot20→0xa0372c | +0xcc 상태0/1/2, LE16 길이-2, 본문 대기, opcode 테이블 +0xc0, handler 반환 후 종료 분기 | native/user_socket.hpp의 Header/Body/Stopped 파서. 완성 프레임만 FFM에 전달. L1은 길이2 empty, 정상 wire10..65535 허용 |
| 0xa0372c,0x9f7f3c,0x9ba4f8 | opcode 및 세션/actor 상태에 따른 실행 분기와 핸들러 호출 | CUserSocketDispatch의 L1 ConnectionState/계정/캐릭터/ready 정책. 기존 L1 opcode 표와 NetworkSafeLoad 검사 유지 |
| 0xa12dec→0x8b04fc | 파서 중지, 등록값 xchg0, 임계 구역 내 등록 제거·참조 해제 | closing 통지를 통해 활성 자격을 먼저 제거. CUserSocketRegistry가 I/O·패킷 처리와 L1 정리 완료를 모두 기다림 |
| 0xa12674→0xa123e8 | socket을 보유하는 후속 객체와 상태별 지연 정리 | CUserSocketCloseTask가 L1 로그아웃을 1회 실행. PC 연결 소유자·session stamp를 검사하고, 실패 시에도 최종 정리. 개인상점·자동혈맹 규칙 유지 |
| 0x705b70→0x705b38→0x9250f8,0x925118 | worker 시작 인자에서 TLS 번호 설정, 번호별 thread handle 등록 | 현재 native worker index를 조회·검증. 일반 게임 opcode는 IOCP READ 완료 스택에서 직접 실행. L1 owner에서 재개되면 IOCP 작업으로 게시 |
| 0x72f874 | 서비스 시작에서 owner17 설정, worker 생성과 여러 서비스 초기화·종료 | L1 시작/제어 경로와 실제 I/O worker 역할을 구별. owner17 숫자를 맞추기 위한 빈 worker를 만들지 않음 |

RTTI에서 x86 CUserSocket0x60639c와 x64 CUserSocket0xdc1c18을 모두 확인했습니다. 위 세부 함수·필드 근거는 현재 x64 팩에 맞춰 x64 L2Server(10).exe를 기준으로 했습니다. 서로 다른 빌드의 vtable slot/offset은 같다고 가정하지 않았습니다.

입력 SHA256: x64 9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588, x86 8eecd317fb152b76f32a0a6a65fbf916e39d20511e203b4a0ea602fe041a22f5. research/recover_user_socket.py가 입력 해시를 검사하고 research/user-socket에 명령·호출 근거를 생성합니다. 함수 역할 이름 일부는 분석용 표기이며 원본 심볼 이름이 아닙니다.

## 현재 실행 흐름

TCP/IOCP → native CUserSocket 프레임 분해 → 기존 CIOPacketCodec.dll의 L1 복호화 → L1 상태·명령 검사 → 기존 CPacketHandler → 기존 게임 로직.

- 정상 프레임이 현재 수신 버퍼에 모두 있으면 그 버퍼를 callback 동안 빌립니다. 분할 프레임만 최대65535-byte 조립 저장소를 사용합니다. 잘못된 길이는 본문을 기다리지 않고 연결 종료합니다. 부분 헤더·본문의30초 기한은 기존 L1 deadline 경로에서 확인하며, backpressure 정지 시간은 제외합니다.
- READ가 패킷 실행 완료를 기다릴 때는 프레임 경계에서 native 수신을 정지합니다. codec plaintext는 연결당1개만 실행에 빌려주고 종료·취소 뒤에 반환합니다. 오래된 PC/stamp에 속한 패킷은 새 세션에 실행하지 않습니다.
- 일반 게임 opcode는 native IOCP worker에서 직접 실행합니다. 다른 스레드에서 READ가 재개된 경우만 IOCP completion으로 넘깁니다. 로그인·캐릭터 진입·종료·다중 하위 프로토콜은 기존 L1 CIO owner에서 순서를 유지합니다. native worker와 CIO owner를 서로 사칭하지 않고 실행 지점을 검사합니다.
- native completion에 수락된 패킷 작업은 실행 또는 취소 중 하나로 끝납니다. 서버 종료 시 native에서 취소되어 callback이 오지 않는 작업도 Java lease를 정리합니다. native 큐는 기존65536 작업 상한이며 연결당1개 패킷만 대기합니다.
- 접속 정리는 close-start→I/O/packet drain→L1 deferred logout→final release 순서입니다. 실제 L1 계정 로그아웃 완료까지 정리 참조를 유지합니다. DB 처리는 기존 L1 executor에 둡니다. native IOCP worker에서 DB 정리를 강제로 수행하지 않습니다.

## L1 때문에 달리 적용한 부분

L2의 opcode 번호·암호화·0x4000 길이 제한·handler bool 의미·유저 상태 숫자·고정5/15초 로그아웃 지연은 복사하지 않았습니다. L1 handler의 bool은 기존 의미를 유지합니다. L1 다중 하위 프로토콜(Channel/Extended/Hypertext)은 기존 하위 명령 검사에 맡겨 로그인 단계·게임 단계 양쪽을 보존합니다. 무효 상태의 일반 게임 요청은 실행하지 않고 키 진행은 기존 codec에서 먼저 수행합니다.

등록 관리자의 자료구조는 원본 C++ tree 메모리 배치를 복사하지 않고 현재 Java 세션에 맞춘 동시성 map/상태 플래그입니다. private shop/auto clanjoin 상태의 L1 기존 처리와 세션 대체 방어를 유지합니다. L2의 thread 수와 CPU affinity를 고정 복제하지 않으며 게임 I/O worker8개를 유지합니다. 기존 native buffer-owner/TLS·타이머 휠과 L1 게임 owner는 각 역할을 유지합니다.128배치는 앞서 추가한 정책이며 원본 worker의 단일 GQCS와 구별합니다.

CTransport.dll과 별개인 기존 CIOPacketCodec.dll은 원본 제공 파일을 그대로 재사용합니다. crypto DLL을 새로 복원·변경했다고 표현하지 않습니다.

## 변경 파일

- native/user_socket.hpp, CTransport.cpp/.h: L1 프레임 상태·완성 프레임 전달·진단·worker 식별, ABI4.
- CTransportServer/Socket/Task: FFM 연계·프레임 경계 pause·native task 수락/취소 수명.
- CIOUserSocket/CUserSocketDispatch/CPacketHandler: L1 상태 정책·세션 guard·native 직접 실행/owner 역할·plaintext lease.
- CIOServer/CUserSocketRegistry/CIOSocketFactory: 등록→retire→I/O·L1 cleanup 완료 관리.
- CIOClient/CUserSocketCloseTask: 1회 후속 로그아웃, 이전 PC/stamp에 대한 정리만 실행.
- CIOThreadCallback.PacketTask: native completion 취소 계약 공유. 기존 L1 timer/game owner 구현 유지.

PSS 소스·SC_PssNativeWindow·PSS 시작 경로·CSProto 등록·클라이언트 파일은 직전 커밋과 동일합니다. CIOClient 내부 PSS 관련 구간도 동일함을 비교했습니다. PSS listener·NIO2·5001 기본 포트·토큰·설정창·사냥 코드는 변경하지 않았습니다. logs/user-socket-pss-preservation.log에 확인 기록을 포함합니다.

## 검증과 적용

- 생산 C++ ASan/UBSan·TSan: 기존 전송/종료/풀/타이머 검사와 L1 분할/합친/최대 프레임·invalid length·기한·정지/재개·동시 종료·실제 Connection 연결 검사가 통과했습니다. 이 검사의 Windows API는 fixture입니다.
- 생산 Java1,702개 전체 타입 컴파일, ABI4·DLL 해시·PE64/41 exports 검사.
- 생산 L1 정책·등록/정리 클래스: 로그인/선택/게임 상태, 다중 프로토콜,400개 동시 종료 경합, 새 연결 보호, 정리 1회·실패 후 정리 검사.
- Windows 검사는 배포 DLL·생산 FFM/socket·변경 없는 실제 L1 codec으로 프레임/복호화 결과와 rolling key 일치, 프레임 경계 backpressure, worker 식별,64개 대기 작업 종료 취소를 검사합니다. 실제 결과는 WINDOWS_VERIFICATION.json과 실행 링크를 기준으로 합니다. 이전 ABI3 실행을 새 DLL 검사로 취급하지 않습니다.

실제 게임 클라이언트 로그인/사냥·운영 DB·장시간 동접은 여기서 실행하지 않았습니다. FFM/socket 검사의 game-world CIOUserSocket은 대역이며 실제 전체 게임 핸들러가 로그인/전투를 실행한 검사가 아닙니다. 처리 경로가 바뀌었으므로 성능 개선율을 단정하지 않습니다.

전체 src와 build/CTransport.dll을 함께 적용하고 clean build합니다. 기존 Java21 preview/native-access 및 CIOPacketCodec.dll을 유지합니다. ABI3 DLL과 ABI4 src를 섞으면 시작을 거절합니다. PSS 클라이언트·INI를 교체할 필요가 없고 추가 SQL도 없습니다.

게임 DLL SHA256: 3a7cca0ab0eb0a4c521ccefd9252247af96342888de72e8625ee200501cff1ae
기존 codec DLL SHA256:60bde93c2ba20057e7e307594edef69ced273b69f923535ab658769e09f0cea9.

## 실제 Windows 결과

[실행43295597820](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43295597820), job131452962516: **success**. Windows Server2025/Temurin21.0.13에서 검사 소스6c03b4dc64e9588b09109b3ca929857168dc9784와 배포 ABI4 DLL을 사용했습니다.

20개 native/FFM 접속·4MB echo, 기존 게임 socket adapter, 새 L1 native parser+실제 기존 codec의67개 plaintext/rolling-key 일치, 최대/empty/합친 프레임, 프레임 경계 backpressure·재개, invalid length 종료·실제 native I/O terminal flag, IOCP TLS 및64개 대기 패킷의 종료 취소, L1 상태·등록/정리400경합 검사가 모두 통과했습니다. 변경하지 않은 독립 PSS의 프로토콜·생산 활성화·기존 PE32 client loader 검사도 통과했습니다.

검사한 전체 src ZIP SHA256:01d7a542546df700924c8004a02c75de132d9183e78d8f07aa85df91fa9ff807. 이후 결과 기록 커밋은 문서·로그·패키지 메타데이터만 갱신합니다. 위에 명시한 실게임/DB/전체 핸들러 실행 검증의 한계는 유지합니다.
