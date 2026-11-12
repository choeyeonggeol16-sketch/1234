# CTransport.dll ABI3 — 원본 구조 후속 복원

게임 접속/PSS UI 연결의 유일한 전송 엔진입니다. 기존 엔진 선택·fallback·PSS NIO2는 없습니다. DLL과 전체 src를 함께 적용합니다.

## 적용

1. 기존 src를 전체 src ZIP의 src로 교체하고 clean build합니다. 옛 CIOWinServer/CIOWinSocket/CIOTransportConfig 소스·class를 남기지 않습니다.
2. 서버 실행 폴더에 build/CTransport.dll을 넣습니다. ABI는0x30000,37개 export입니다. 구버전 Java/DLL 혼용은 해시/ABI 검사에서 거절됩니다.
3. CIOWinTransport.dll/CIONcTransport.dll/RKernCIOTransport.dll과 옛 엔진 선택 옵션은 제거합니다.
4. L1 프레이밍·암복호화용 CIOPacketCodec.dll은 유지합니다. Java21 --enable-preview와 --enable-native-access=ALL-UNNAMED 유지. 추가 SQL 없음.

## 이번 구현

- 종료 시작 훅 → 활성 연결 조회 제거 → linger0/socket close → I/O·콜백 종료 → 최종 자원 정리로 분리했습니다. 훅은 socket mutex 밖에서 실행하고 재진입을 허용합니다.
- 원본0x2038/0x4038 stride의 연속 버퍼 slab와 포인터 원형 큐를 사전 할당합니다. 고갈 후 개별 buffer new/다른 풀 차용은 하지 않습니다. 외부 FFM 스레드는 등록 시 고정 ring을 초기화합니다.
- timer 등록은 토큰 hash 대신 현재 worker/등록된 외부 생산자 소유권을 따릅니다. owner17은 재게시, 다른 owner는100개 직접 실행 후101번째 IOCP 재게시 분기를 반영했습니다.
- 1,000개 객체/메시지/인자 배열과 count/next를 가진0x4e30 timer block,32-byte 장기 heap record와 공유 CAS block ring을 구현했습니다. 휠50ms/200칸, block 선두 확장/반납, 취소 슬롯 회수와 부분 초기화 rollback을 포함합니다.
- 풀 할당/고갈/초기화 실패/손상, timer 직접 실행·재게시, 종료 시작·최종 통지를 CTransport_Server_GetDiagnostics의8개 uint64로 확인합니다.
- 이전8K/16K, 공유 RX/COW, 송신 FIFO/부분 완료,128배치·NTSTATUS·종료 신호 처리는 유지합니다.

원본의 풀 고갈 함수는 고의 null write입니다. 현재 팩에서는 진단 후 요청/연결을 종료하며 프로세스 전체를 강제 크래시시키지 않습니다. 풀 용량과 외부 Java 스레드 번호,64비트 clock/취소 인덱스는 팩에 맞춘 설정입니다. 원본 게임 코드/패킷 ID/암호화는 이식 대상이 아닙니다. 세부 차이·메모리 정책·원본 주소는 GitHub 보고서에 기록했습니다.

## 검사

python native/build_native.py --zig /path/to/zig --write-java-hash
python tests/run_native_tests.py
python tests/run_native_tests.py --tsan
python tests/verify_native_binary.py

Windows/JDK21: tests/run_windows.ps1. 배포 DLL C++ loopback, 생산 Java FFM/소켓 어댑터와 PSS를 검사합니다. 게임 owner 의존성만 대역입니다. 실제 게임 클라이언트/운영 DB/장시간 부하와 구분합니다. ABI3의 Windows 결과는 WINDOWS_VERIFICATION.json을 확인합니다. 이전 ABI2 성공 이력을 새 ABI3 검증으로 사용하지 않습니다.

종료 시작/최종 callback에서 자기 server의 Stop/StopAccept/Wait를 호출하면 대기하지 않고 거절합니다. IOCP worker가 아닌 호출자 thread의 close-post 실패 대체 통지에서도 같은 가드를 적용하고 검사했습니다.
