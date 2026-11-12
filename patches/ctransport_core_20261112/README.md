# CTransport ABI6

L2 바이너리에서 확인한 이벤트 기반 접속 수락과 native 클래스 역할을 L1에 반영했습니다. `ListenThread17 -> RegisterWait64 -> FD_ACCEPT -> IOCP -> accept/factory/Add/Initialize`, 실제 Server/소켓 계층, 유저번호 보조 색인·IP/MAC 제한·직렬 실행·운영 진단을 연결했습니다. Java 클래스명과 호출부도 정리했습니다.

1. 전체 src를 교체하고 clean build합니다. 부분 적용이면 deleted-files.txt도 반영합니다.
2. 같은 묶음의 CTransport.dll ABI0x60000/56 exports를 적용합니다. SHA256/ABI가 맞지 않으면 서버 시작을 거절합니다.
3. 기존 CIOPacketCodec.dll과 Java21 preview/native-access 옵션을 유지합니다.
4. PSS 독립 연결·활성화·클라이언트를 유지합니다. 추가 SQL은 없습니다.

원본 근거·클래스 역할·L1 이식 차이는 REPORT_ORIGINAL_NETWORK_ABI6.md, 최신 Windows 실행 결과는 WINDOWS_VERIFICATION.json에 있습니다. MAC은 신뢰할 식별자를 전달하는 연결이 필요합니다. 실게임·운영 DB·장시간 동접·비교 성능은 미검증입니다. ABI5 보고서는 당시 구현의 감사 이력입니다.
