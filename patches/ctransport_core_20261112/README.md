# CTransport.dll — 네이티브 IOCP 코어

게임 접속과 PSS UI 제어 연결 모두 CTransport.dll을 필수 사용합니다. HP-Socket/RKern 구버전 DLL 선택값과 PSS NIO2 경로를 제거했습니다. 실행 시 전송 엔진 선택 옵션은 필요 없습니다.

## 적용

1. 기존 프로젝트의 src를 전체 src ZIP의 src로 교체하고 clean build합니다. 옛 src 위에 겹쳐 복사하면 삭제된 클래스가 남으므로 주의합니다.
2. 서버 실행 폴더에 build/CTransport.dll을 넣습니다. 새 Java와 DLL을 함께 적용합니다.
3. 구버전 CIOWinTransport.dll, CIONcTransport.dll, RKernCIOTransport.dll과 이전 전송 엔진 옵션은 제거합니다. 옛 CIOWinServer/CIOWinSocket/CIOTransportConfig 소스와 빌드 산출물도 남기지 않습니다.
4. 기존 CIOPacketCodec.dll은 L1 프로토콜용으로 유지합니다. 추가 SQL 없음. 기존 Java21 --enable-preview와 --enable-native-access=ALL-UNNAMED 옵션 유지.

CTransport.dll이 없거나 해시/ABI가 다르면 기동 실패합니다. 구버전 엔진으로 돌아가는 경로는 없습니다. ABI는 0x20000, C API는 기존 CIO 함수 이름과 CTransport 작업/타이머/버퍼 API를 제공합니다.

## 구현 범위

- 원본에서 확인한 객체 참조 수명, completion-key 가상 디스패치, 수신/송신 OVERLAPPED 분리.
- TLS 소유자별 CAS 원형 버퍼 풀, 원래 소유 풀로 반납. 별도 8KB/16KB 버킷.
- 공유 수신 버퍼 retain/lease, ref>1 복사와 단독 소유 버퍼 압축. lease는 접속/서버 종료 후에도 해제 전까지 유지.
- 송신 FIFO, 대기 tail 합치기, 부분 완료 재제출, 최대128개 completion 배치와 개별 NTSTATUS 처리.
- 연결별 1회 socket retire, SO_LINGER(1,0) reset 종료, 커널 I/O와 콜백 완료 후 terminal callback.
- 일반 작업 객체를 IOCP에 게시. 50ms/200칸 타이머 휠+장기 예약 heap, 취소와 종료 시 참조 정리.
- 게임용 Java 어댑터와 PSS UI 인증/OPEN/PING 연결을 동일한 DLL에 연결. PSS 만료 검사는 native timer에서 실행.

## 원본과 적용 팩의 경계

제공된 x86/x64 실행 파일은 L2Server 계열입니다. 그 게임의 패킷 ID, 암호화, 게임 객체/DB 처리를 L1에 넣지 않습니다. 파서의 2바이트 길이/본문 상태 처리는 원본에서 확인했으며, 실제 L1 프레이밍·암복호화는 기존 CIOPacketCodec.dll을 사용합니다. Java의 세션/게임 owner 실행은 유지합니다.

새 C++로 동작을 재구현한 결과입니다. 원본 C++ 전체 복구, 원본과 완전 동일한 메모리 배치/성능/안정성을 뜻하지 않습니다. 외부 FFM 스레드 등록, 풀 고갈 거절, 취소 인덱스, 64비트 단조 시계, 종료 콜백 시점은 현재 팩의 수명 계약에 맞춘 구현입니다. 원본의 1,000개 단위 타이머 블록 저장 배치까지 동일하게 복제한 것은 아닙니다. 128 배치는 기존 전송 DLL을 참고한 확장입니다.

## 빌드/검사

python native/build_native.py --zig /path/to/zig --write-java-hash
python tests/run_native_tests.py
python tests/run_native_tests.py --tsan
python tests/verify_native_binary.py

Windows: JDK21을 설치하고 tests/run_windows.ps1을 실행합니다. 배포 DLL을 직접 로드하여 C++ loopback, 생산 Java FFM/소켓 어댑터, native 작업/타이머 취소와 PSS UI 프로토콜을 검사합니다. 게임 owner 의존성만 검사 대역을 사용하므로 실제 게임 클라이언트/운영 DB 부하 검증과는 구분합니다.

실제 검사 결과와 원본 함수 근거는 reports/CTRANSPORT_CORE_20261112.md 및 GitHub Windows 검사 결과를 확인합니다.

실제 Windows Server2025/JDK21 검사도 통과했습니다: https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43234341462
C++ DLL loopback, 생산 Java FFM/게임 소켓 어댑터, PSS UI 프로토콜/타이머를 확인했습니다. 상세 결과는 WINDOWS_VERIFICATION.json과 logs/windows-actual-test.log입니다. 실제 게임 클라이언트/DB 부하 검사는 별도입니다.
