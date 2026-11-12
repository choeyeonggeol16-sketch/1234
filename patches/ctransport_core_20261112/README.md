# CTransport ABI7

원본의 Playing27(소켓 번호), EnterChar28(계정 ID), CPerfMon thread15 역할을 확인하고 L1 인증·캐릭터 진입·재시작·종료에 연결했습니다. 별도 native 성능 샘플러와 MAC 공급자 API·설정·인증 강제 검사를 추가했습니다.

1. 전체 src를 교체하고 clean build합니다. 부분 적용이면 deleted-files도 반영합니다.
2. 같은 묶음의 CTransport.dll ABI0x70000/62 exports를 적용합니다. ABI/SHA256이 다르면 서버가 시작을 거절합니다.
3. 기존 CIOPacketCodec.dll·PSS 독립 연결·클라이언트·설정과 Java21 옵션을 유지합니다. 추가 SQL 없음.
4. MAC 제한은 기본 꺼짐입니다. 실제 클라이언트 식별자 전송 코드는 자료에 없으며 확인된 서버 측 공급자 연동이 있어야 활성화할 수 있습니다. `-Dctransport.macLimit=N`을 켜면 식별자 없는 인증을 거절합니다.
5. 성능 샘플 기본1초: `-Dctransport.perf.intervalMs=1000`. root Server.networkPerformance()/networkAccountRegistryDiagnostics()로 조회합니다.

원본 주소·구현 차이·미검증 범위는 REPORT_REMAINING_NETWORK_ABI7.md, 실제 Windows 실행 결과는 WINDOWS_VERIFICATION.json을 따릅니다. 운영 DB·실게임·장시간 운영 부하는 미검증입니다. 이전 보고서는 당시 구현의 이력입니다.
