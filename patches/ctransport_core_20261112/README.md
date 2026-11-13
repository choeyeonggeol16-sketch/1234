# CTransport ABI8

원본 SEND/WRITE serializer와 Close/GracefulClose 경로를 대조하여 게임 송신의 암호화·큐·부분 완료를 native 실행기에 연결했습니다. 정상 종료는 마지막 송신을 기다린 뒤 linger0 없이 닫고, 오류·시간 초과는 강제 종료합니다. 복사 중 종료 때 Java source lease를 먼저 반환하는 경합도 수정했습니다.

1. 전체 src를 교체하고 clean build합니다. 부분 적용이면 deleted-files도 반영합니다.
2. 같은 묶음의 CTransport.dll ABI0x80000/66 exports를 적용합니다. ABI/SHA256 불일치는 시작을 거절합니다.
3. 기존 CIOPacketCodec.dll·독립 PSS 연결·클라이언트·설정과 Java21 옵션을 유지합니다. 추가 SQL 없음.
4. 원본 주소와 L1 호환 구현의 구분은 REPORT_SEND_CLOSE_ABI8.md를 확인합니다. 이전 보고서는 당시 구현의 이력입니다.
5. Server.networkSendDiagnostics()는 송신 접수·실행·프레임별 암호화·종료 모드·pending·소유권 위반을 조회합니다. 배열 순서는 보고서에 명시했습니다.
6. MAC 공급자 자료가 없어 제한 기본 꺼짐은 유지합니다. PSS 및 L1 codec DLL 바이트는 변경하지 않았습니다.

실제 Windows 실행의 최종 상태·커밋·해시는 WINDOWS_VERIFICATION.json 기준입니다. 운영 DB·실게임·장시간 부하는 미검증입니다.
