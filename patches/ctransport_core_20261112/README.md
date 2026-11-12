# CTransport ABI5

native 접속 관리자·기존 L1 코덱 직접 호출·네트워크 timer wheel을 현재 L1 팩에 연결했습니다. L2 원본에서 관찰한 동작을 L1에 맞게 새로 구현했으며, 원본 소스 추출·서버 전체 완전 복원을 뜻하지 않습니다.

1. 전체 src를 교체하고 clean build합니다.
2. build/CTransport.dll ABI0x50000/49 exports를 같은 실행 폴더에 적용합니다. 이전 DLL과 섞으면 시작을 거절합니다.
3. 기존 CIOPacketCodec.dll·Java21 preview/native-access 옵션을 유지합니다.
4. PSS source·클라이언트·INI·전용 연결과 활성화를 유지합니다. 추가 SQL 없음.

native가 접속 등록/조회/retire와 packet lease를 소유합니다. wire는 C++에서 바로 기존 codec Feed/Next로 들어가며 Java에는 plaintext만 전달합니다. 로그인60초·부분 수신/송신 정체30초·정상 종료 유예는 native 네트워크 timer에서 검사합니다. 게임 handler/계정/월드와 일반 L1 게임 timer는 기존 역할을 유지합니다.

원본 주소·L1 적용 차이·변경 파일·검사 범위는 REPORT_L1_USERSOCKET.md를 확인하세요. 새 실제 Windows 결과는 WINDOWS_VERIFICATION.json을 기준으로 합니다. 실게임·운영 DB·장시간 동접·비교 성능은 미검증입니다.
