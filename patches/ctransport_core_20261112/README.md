# CTransport ABI8

원본 SEND/WRITE serializer와 Close/GracefulClose 경로를 대조하여 게임 송신의 암호화·큐·부분 완료를 native 실행기에 연결했습니다. 정상 종료는 마지막 송신 뒤 SD_SEND와 상대 FIN을 거쳐 linger0 없이 닫고, 오류·시간 초과는 강제 종료합니다. 복사 중 종료 때 Java source lease를 먼저 반환하는 경합도 수정했습니다.

1. 전체 src를 교체하고 clean build합니다. 부분 적용이면 deleted-files도 반영합니다.
2. 같은 묶음의 CTransport.dll ABI0x80000/66 exports를 적용합니다. ABI/SHA256 불일치는 시작을 거절합니다.
3. 기존 CIOPacketCodec.dll·독립 PSS 연결·클라이언트·설정과 Java21 옵션을 유지합니다. 추가 SQL 없음.
4. 원본 주소와 L1 호환 구현의 구분은 REPORT_SEND_CLOSE_ABI8.md를 확인합니다. 이전 보고서는 당시 구현의 이력입니다.
5. Server.networkSendDiagnostics()는 송신 접수·실행·프레임별 암호화·종료 모드·pending·소유권 위반을 조회합니다. 배열 순서는 보고서에 명시했습니다.
6. MAC 공급자 자료가 없어 제한 기본 꺼짐은 유지합니다. PSS 및 L1 codec DLL 바이트는 변경하지 않았습니다.

실제 Windows 실행의 최종 상태·커밋·해시는 WINDOWS_VERIFICATION.json 기준입니다. 운영 DB·실게임·장시간 부하는 미검증입니다.

실제 ABI8 Windows Server2022/Temurin21 검사 통과: [실행43338213554](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43338213554), 검사 소스 `292ef5bf3e21eb3851dce113a8a64c459dca3cea`. production CUserSocket의 최초 평문·최대65535 S2C·8 packed action·최종 패킷을 기존 codec과 비교하고, 실제 정상 EOF/상대 FIN/terminal과 graceful=1·abortive=0·pending=0·ownerErrors=0을 확인했다. 별도 동시32개 loopback은 30,128ms·누적55,456접속·7,098,368 L1 frames·3,634,364,416 C2S wire bytes를 처리하고 round별 색인 잔여0을 확인했다. 실계정·DB·사냥 검사가 아니며 운영 성능 수치로 해석하지 않는다. 최종 src·DLL·테스트는 이 검사 커밋과 동일하고 후속 커밋은 검증 기록을 추가한다.
