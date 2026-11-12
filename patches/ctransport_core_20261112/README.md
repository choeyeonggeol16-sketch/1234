# CTransport ABI4 / L1 유저소켓

L2 CUserSocket에서 확인한 프레임 상태·패킷 분배·등록/후속 정리·worker 역할을 현재 L1에 맞춰 재구현했습니다. 원본 C++ 소스 추출본이나 L2 게임 로직 이식본이 아닙니다. PSS는 직전 독립 연결 구현을 그대로 유지합니다.

## 적용

1. 전체 src ZIP으로 src를 교체하고 clean build합니다.
2. build/CTransport.dll을 게임 서버 실행 폴더에 적용합니다. ABI0x40000/41 exports이며 이전 ABI3 DLL과 섞으면 거절합니다.
3. 기존 CIOPacketCodec.dll과 Java21 preview/native-access 옵션을 유지합니다. core에 포함된 codec DLL도 기존 제공 원본과 같습니다.
4. PSS 클라이언트·INI·전용 포트는 그대로 둡니다. PSS는 게임 CTransport와 독립입니다. 추가 SQL 없음.

## 동작

- native CUserSocket: Header/Body/Stopped, L1 wire2 empty 및10..65535, 완성 프레임만 전달. 정상 완성 프레임은 기존 수신 버퍼를 빌리고 분할 프레임만 조립합니다.
- 기존 native L1 codec → L1 상태/계정/캐릭터 검사 → 기존 opcode handler. 다중 프로토콜의 기존 하위 검증 유지.
- 일반 게임 요청은 IOCP 완료 스택에서 직접 실행. 다른 스레드에서 재개된 READ는 IOCP 작업으로 복귀. 로그인·세션 전환은 L1 CIO owner 유지.
- 연결당1개 plaintext lease. close-start에서 활성 조회를 끝내고 패킷/I/O drain·L1 deferred logout 완료 뒤 참조 해제. 종료 시 취소된 native packet task도 lease 해제.
- CTransport_UserSocket_GetState: uint64[6] = parserState,expectedWire,filled,frames,invalidLengths,partialAgeMs. CTransport_Server_ThreadOwner: 해당 server의 native worker 번호, 외부 thread는-1.

자세한 원본 주소·L1 적용 차이·변경 파일·검사 범위는 reports/CTRANSPORT_L1_USERSOCKET_20261112.md를 확인하세요. 실제 Windows 결과는 WINDOWS_VERIFICATION.json을 기준으로 합니다. 실게임 로그인/사냥·운영 DB·장시간 부하는 별도 검증 대상입니다.
