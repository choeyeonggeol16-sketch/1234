Windows 네트워크 검증 팩 — 2026-10-29

실행 전에 ZIP 전체를 새 폴더에 압축 해제하세요.
필요 환경: Windows Server/Windows, JDK 21(java.exe), Python 3.10 이상, 논리 CPU 4개 이상.
Java 21과 Python은 PATH 또는 Windows py launcher에서 실행 가능해야 합니다.

1. Run_Windows_Benchmark.cmd를 실행합니다.
2. 완료될 때까지 기다립니다. 75회 × 예열/측정10초이므로 측정 구간만 약12분30초이며, 시작·연결·종료·preflight 시간이 추가됩니다.
3. 생성된 results_날짜_시간.zip을 채팅에 올려주세요. 실패한 경우에도 생성된 ZIP을 올리면 됩니다. ZIP이 생성되기 전 필수 환경 검사에서 멈추면 콘솔 오류를 보내주세요.

측정 구성
- P7: 직전 RKern P7 원본 네트워크 구현.
- P8: 최신 RKern P8 원본 네트워크 구현.
- NettyNIO: Netty 4.1.139.Final의 Windows NIO transport, read-complete flush.
- P7/P8은 WindowsAsynchronousChannelProvider(IOCP)를 실제로 확인한 후 실행합니다.
- localhost만 사용하고 검증 전용 하네스의 game/DB 경계에는 facade를 사용합니다. 게임 DB는 필요하지 않습니다.
- 실제 CIOUserSocket/CIOServer/버퍼 풀/Cryptor 및 동일 프로토콜을 실행합니다.
- 실제 TCP idle/READ pressure/RST 회귀와 세 엔진의 4KiB 전체 바이트 검사를 먼저 실행합니다.
- 단일/window1, 128연결/32응답(window8), 1024연결/window1, 64연결/4KiB(window4), 16연결/window256.
- 각 설정 새 JVM 5회, 예열5초/측정5초, 실행 순서 회전. 총75회 성능 시험.
- 서버 CPU 0,1 / 클라이언트 CPU 2,3, 각각 ActiveProcessorCount=2.
- 기본 mask는 0x3/0xc. 다른 CPU 배치를 쓰려면 Python 스크립트의 --server-mask/--client-mask 옵션을 이용합니다.
- CPU 그룹이 여러 개인 대형 Windows 서버의 affinity 적용 실패는 raw 로그로 남깁니다.
- 스레드별 코어 pinning, 전체 게임/NPC/DB 부하, 장시간 soak는 포함하지 않습니다.
- Netty epoll은 실행하지 않습니다. Windows 실사용 환경의 Netty NIO와 비교합니다.
- Linux와 Windows의 값을 한 개선율로 직접 섞지 않습니다.

예: 빠른 호환성 확인만
  py -3 windows_benchmark.py --smoke-only

예: 다른 Java 21 지정
  py -3 windows_benchmark.py --java "C:\Program Files\Java\jdk-21\bin\java.exe"

검증 상태
현재 연결된 실행 환경은 Linux이므로 이 팩의 native Windows 실행은 아직 수행하지 못했습니다.
Python 문법/패키지 무결성/Java21 class 버전/원본 class 해시는 확인했습니다.
Windows 결과나 성능 수치는 만들어 넣지 않았습니다. Windows에서 실행한 실제 raw.json과 로그로 판정해야 합니다.
SOURCE_MANIFEST.json에 원본 업로드 ZIP 및 CIO 소스·class SHA256을 기록했습니다.
