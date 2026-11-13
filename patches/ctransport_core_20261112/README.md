# CTransport ABI9

원본 RTTI에서 CDummyServerCallback/IServerCallback과 MemoryObject 기반을 확인해 실제 C++/Java 호출에 연결했습니다. Java CIOSocketFactory는 CDummyServerCallback으로 바꾸고 CIOBufferEx를 독립 클래스로 사용합니다. 상수 입력을 보존하는 CSocket::__async_AsyncSendConstBuffer 경로를 실제 gather SEND에 연결했습니다.

1. 전체 src와 같은 묶음의 CTransport.dll ABI0x90000/64 exports를 함께 적용하고 clean build합니다. 구 export alias는 없으며 ABI8 Jar/DLL 혼용은 시작을 거절합니다.
2. deleted-files.txt를 반영합니다. CIOSocketFactory.java 및 예전 CIOSocketFactory.class, CIOBufferPool$Lease.class 등 이전 빌드 결과가 남지 않도록 합니다.
3. 기존 CIOPacketCodec.dll·독립 PSS·클라이언트·설정과 Java21 preview/native-access 옵션을 유지합니다. 추가 SQL 없음.
4. 원본 class/member 이름을 반영한 C ABI entry와 L1Interop 보조 API의 전체 대응은 research/abi9/export-name-map.json입니다. 원본 MSVC ABI를 복제한 것은 아닙니다.
5. 분석·호출 경로·검사·추가 미반영 항목은 REPORT_ORIGINAL_NAMES_CONST_ABI9.md, 실제 Windows 실행 상태·소스 커밋·해시는 WINDOWS_VERIFICATION.json을 따릅니다.

운영 DB·실게임·장시간 부하와 원본 서버 전체의 동일성은 미검증입니다. 이전 보고서는 당시 구현의 이력입니다.

실제 ABI9 Windows Server2022/Temurin21 검사 통과: [실행43365489001](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43365489001), 검사 소스 `0ca18a8d10a84ede5db6d34ddbbd24cb29d0f811`. 새64개 entry의 DLL과 production Java FFM, 실제 gather 상수 송신/20K frame의16K 경계/기존 codec 일치/최종 packet·FIN/lease 해제를 확인했다. 동시32개 loopback은 30,134ms·누적54,304접속·6,950,912 L1 frames·3,558,866,944 C2S wire bytes를 처리하고 round별 색인 잔여0을 확인했다. 원본 이름 변경 외 소스1,684개 바이트 불변도 별도로 검사했다. 실게임·DB·운영 성능 보장은 아니다. 최종 native/src/tests/build는 이 검사 커밋과 동일하고 후속 커밋은 검증 기록을 추가한다.
