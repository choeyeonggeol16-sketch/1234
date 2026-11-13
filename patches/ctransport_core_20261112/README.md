# CTransport ABI9

원본 RTTI에서 CDummyServerCallback/IServerCallback과 MemoryObject 기반을 확인해 실제 C++/Java 호출에 연결했습니다. Java CIOSocketFactory는 CDummyServerCallback으로 바꾸고 CIOBufferEx를 독립 클래스로 사용합니다. 상수 입력을 보존하는 CSocket::__async_AsyncSendConstBuffer 경로를 실제 gather SEND에 연결했습니다.

1. 전체 src와 같은 묶음의 CTransport.dll ABI0x90000/64 exports를 함께 적용하고 clean build합니다. 구 export alias는 없으며 ABI8 Jar/DLL 혼용은 시작을 거절합니다.
2. deleted-files.txt를 반영합니다. CIOSocketFactory.java 및 예전 CIOSocketFactory.class, CIOBufferPool$Lease.class 등 이전 빌드 결과가 남지 않도록 합니다.
3. 기존 CIOPacketCodec.dll·독립 PSS·클라이언트·설정과 Java21 preview/native-access 옵션을 유지합니다. 추가 SQL 없음.
4. 원본 class/member 이름을 반영한 C ABI entry와 L1Interop 보조 API의 전체 대응은 research/abi9/export-name-map.json입니다. 원본 MSVC ABI를 복제한 것은 아닙니다.
5. 분석·호출 경로·검사·추가 미반영 항목은 REPORT_ORIGINAL_NAMES_CONST_ABI9.md, 실제 Windows 실행 상태·소스 커밋·해시는 WINDOWS_VERIFICATION.json을 따릅니다.

운영 DB·실게임·장시간 부하와 원본 서버 전체의 동일성은 미검증입니다. 이전 보고서는 당시 구현의 이력입니다.
