# PSS 독립 연결 재활성화

기준53640924b8750e37f7a2456dddbe4c8e7b77218c. 사용자 지시에 따라 PSS 전용 연결을 다시 활성화하고 게임용 CTransport와 분리했습니다. 이전 설명의 "PSS가 현재 별도 IOCP를 이미 사용 중"이라는 주장은 잘못이었습니다. 당시 PlaySupportWindow는 in-band 설정창 명령을 전송했고 토큰 등록은 비활성화돼 있었습니다.

## 변경

- PlaySupportWindow의 REGISTER_START/16-byte REGISTER_BYTE/REGISTER_COMMIT와 세션 stamp 검증을 복구했습니다. 기존 .psslink 수동 등록도 복구합니다.
- 설정서는 인증된 PSS peer.requestOpen()으로만 창을 엽니다. 현재 아이템 소유/수량 검사와 재사용 동작을 유지합니다. 사냥 시작/종료/설정 게임 로직을 변경하지 않습니다.
- 기존 PSS 전용 NIO2 전송을 복원했습니다. AsynchronousChannelGroup2개 스레드와 별도 만료 executor1개를 가지며 CTransport 클래스/DLL에 의존하지 않습니다.
- Server.startLoginServer에서 PSS를 먼저 별도 활성화합니다. 기본 GAME_SERVER_PORT+1, pss.uiBridge.port 지정 가능, 게임 포트 재사용 거절. PSS 포트 bind 실패는 로그로 분리하고 게임 기동은 계속합니다.
- 게임 listener 종료와 PSS listener 종료는 별개입니다. PSS stopIndependent는 게임을 종료하지 않으며 프로세스 종료 hook에서 독립 자원을 정리합니다.
- CTransport.dll/C++/게임 FFM 코드는 변경하지 않았습니다. 게임 native IOCP8 workers/풀/타이머 구조 그대로입니다. PSS만 예외로 독립 NIO2를 사용하며 게임 engine fallback은 없습니다.

## 클라이언트

기존 PSS_2019_Idle_Roaming_V1_20261105.zip의 companion Pss2019.dll을 변경 없이 재사용했습니다. root client/pss2019의 Pss2019.c/PssBridge.c/PssRoaming.c/PssRoamCore.c와 원본 ZIP의 소스도 byte-for-byte 같음을 확인했습니다.

DLL SHA256:35b476815c56b578e5e12a3d500589e72220ec6493bbc0041da541c588b861fa. 게임 연결의 기존 capability 등록과 전용 HELLO/BOUND/OPEN/PING 연결을 사용합니다. UI·사냥 코드 수정 없음. 이미 해당 companion을 사용 중이면 교체할 필요가 없습니다. in-band 전용 DLL 사용자는 독립 연결용 기존 DLL로 맞춥니다. 기존 Pss2019.ini 설정은 보존합니다.

## 검사

- 생산 PSS transport 실제 TCP9개 그룹: 등록/HELLO 순서 양쪽, 분할 HELLO, 정확한 BOUND/OPEN, 잘못된 HELLO/IP/nonce replay 거절, 만료/로그아웃, capacity, 동시 OPEN 직렬화.
- 생산 PlaySupportWindow + 실제 TCP: 자동 listener 시작,16-byte 등록/HELLO/OPEN, 소유 설정서 재사용, 사냥 소유권 유지, 로그아웃 해제, PSS-only 종료/포트 충돌 시 게임 세션 유지, 재시작. 게임 세계 객체만 대역입니다.
- 위 독립성 검사는 CTransport/FFM DLL 없이 실행합니다. 게임 전송과의 의존성이 없음을 확인합니다.
- 전체 Java1,698개 타입 컴파일, 게임 DLL 해시/ABI 검사.
- 실제 Windows의 게임 FFM/독립 PSS 및 원본 PE32 client loader 결과는 최신 Windows 실행 기록으로 별도 보관합니다. 원본 DLL의 PssClientInit export와 잘못된 실행 파일 자동 attach 거절을 확인합니다.

실제2019 게임 창의 렌더링/자동사냥/운영 DB·장시간 부하는 별도 검증 대상입니다. 추가 SQL 없음.
