# CTransport 네트워크 코어 재구현·팩 통합

기준 c76e7b2753876fc8bb1aac4075e78a5d31bf522f. 사용자의 요청에 따라 CTransport.dll을 유일한 전송 엔진으로 연결했습니다. 기존 선택형 엔진은 제거했고, PSS 보조 통신의 NIO2도 같은 DLL로 교체했습니다. 게임 로직/게임 owner와 L1 프로토콜은 팩의 규칙을 유지합니다.

## 이번 실제 구현

| 부분 | 바이너리 근거 | 현재 구현 |
|---|---|---|
| 객체 수명·completion dispatch | x64 retain0x7d6d50, socket0x45f028, worker0x7058d8 및 x86 대조 | CIOObject retain/release와 가상 completed, I/O 요청당 정확히1개 참조 |
| 소유자 버퍼 풀 | x640x45d7f8 TLS/pool/owner 기록,0x45d67c CAS ring 반납 | worker 고정 소유자, 외부 FFM 등록 슬롯, 원형 슬롯과 소유 풀 반납. 전역 mutex free-list 제거 |
| 8KB/16KB | vtable0xb54c18/0xbbe878 및 user socket16KB 길이+0x4010 | 두 크기의 실제 저장 공간/별도 bucket. Java receive 설정16KB,4KB 읽기 어댑터 호환 |
| 공유 수신 수명 |0x45e138 ref>1 복사, ref1 압축 | ReceiveWindow/참조 수, 남은 바이트 COW 또는 in-place compact; 보유 lease API |
| 송신 큐 |0x45e480/0x45e8a0 | 활성 head 고정, 대기 tail 합치기, FIFO, 부분 송신 재제출 |
| 배치 | 기존 CIOWinTransport.dll0x180049659 | 최대128개, ENTRY.Internal 상태 snapshot, 종료 신호 재전달. 제공 EXE는 단일 GQCS이며 배치는 확장 |
| 연결 종료 |0x45d534 socket retire, hook, SO_LINGER(1,0), close | gate로1회 retire, linger0/reset, 취소 I/O/콜백 이후 Java terminal 통지 |
| 일반 작업 |0x704248 PostQueuedCompletionStatus + 실패 시 release,0x705d54 가상 dispatch/release | 별도 작업 객체를 completion key로 게시, 실행·취소·종료 참조 계약 |
| 타이머 |0x926844의0x32 간격/0xc8 slot/32-byte overflow heap,0x92533c bucket append,0x9256d4 dispatch,0x92611c heap pop | worker별50ms/200칸 휠+장기 heap, 등록·만료·취소·종료 정리. 만료 작업은 IOCP에 게시 |
| 파서 확인·L1 호환 | CUserSocket vtable0xdc1c18 slot20→0xa0372c, 2-byte LE length-2, payload1..0x3ffe, 상태0/1 | 해당 L2 opcode/crypto를 옮기지 않고 L1 native codec로 기존 프레이밍/암복호화 유지 |

research/scope에 각 함수 전체 추적과 직접 호출 목록을 저장했습니다. unwind 항목이 없는 함수의 덤프는 다음 함수까지 포함할 수 있으므로 실제 ret/분기를 기준으로 판단합니다. AtomicAddTimer라는 RTTI 이름만으로 타이머 구현을 단정하지 않았습니다. 실제 휠 근거는0x926844 경로입니다.

## 교체 파일과 필수 경로

- CIOWinServer/CIOWinSocket/CIOTransportConfig 제거 → CTransportServer/CTransportSocket/CTransportConfig.
- CTransportEndpoint는 같은 FFM 브리지를 원시 보조 연결에 제공합니다.
- PssWindowTransport의 AsynchronousChannelGroup/AsynchronousSocketChannel/ScheduledExecutor 제거. 동일 HELLO/token/IP/BOUND/OPEN/PING 프로토콜을 IOCP+native expiry timer로 처리합니다.
- DLL 파일/해시/ABI0x20000를 항상 확인합니다. 선택 옵션/legacy alias/fallback 없음.
- 이전 DLL은 새 배포물에 포함하지 않습니다. 기존 src 위에 overlay하면 삭제 파일이 남을 수 있으므로 전체 src 교체/clean build 필요.
- CIOPacketCodec.dll은 기존 L1 규칙을 위한 독립 codec로 유지합니다. 추가 SQL 없음.

## 원본과 다른 적용 설계

원본 C++ 텍스트/전체 게임 서버를 복구한 결과가 아닙니다. 원본 instruction byte나 함수 주소를 실행에 사용하지 않습니다. 현재 자료에서 확인한 네트워크 구조를 새 C++로 작성했습니다.

외부 Java 스레드 등록은 원본 TLS 환경에 없는 적용 설계입니다.64개 native worker 슬롯과64개 외부 슬롯을 사용하며 외부 슬롯은 등록 수가 많으면 공유 잠금으로 보호합니다. 여러 server에 중첩 호출해도 worker의 소유자 TLS를 덮어쓰지 않습니다. 풀 용량/전체 block 상한과 고갈 시 요청 거절은 적용 정책이며 원본의 fatal 진단 동작을 복제하지 않습니다.

타이머는 원본의50ms/200-slot/overflow heap 구조를 사용하되,1,000개 단위 블록 메모리 배치와 특수 thread17/매101건 dispatch 최적화까지 같은 구현은 아닙니다. 취소 인덱스와64비트 단조 시계는 새 적용 설계입니다. game Java 타이머를 native에서 중복 실행하지 않습니다.

원본의 종료 hook은 close 이전이며, 현재 Java terminal 통지는 메모리를 참조하는 모든 I/O/콜백이 끝난 뒤입니다. 이는 현재 FFM arena/codec 수명 계약을 지키기 위한 차이입니다. 원본과 동일한 성능/장시간 안정성은 별도 실측 대상입니다.

## 검사

- 생산 C++를 Windows API fixture에 연결한 ASan/UBSan 및 TSan 통과.
- 기존128배치/혼합 NTSTATUS/종료 신호/다중 WSABUF/부분 송신/재진입/반복 종료 검사 유지.
- 원형 ring5,000회 wrap, 다른 스레드 반납,12개 생산자 반복, 여러 server 중첩 TLS 보존.
- 두 크기의 풀/FIFO, 공유 버퍼의 남은 데이터 COW/단독 압축, lease가 후속 수신·연결 종료·서버 파괴 후 유지되는지 확인.
- 가상 시계에서50ms 경계와 장기 예약 heap 이동/취소, native512작업/1,000취소, 실행 중 작업과 Stop의 수명 fence.
- Java1,698개 전체 타입 컴파일 및 고정 DLL/ABI/hash 검사. 전송 engine selector와 NIO2 accept/socket 경로 제거를 확인.
- 실제 PE64/35개 export/IOCP·Winsock import/자바 hash pin 검사.
- Windows 실제 DLL/FFM 검사는 .github/workflows/ctransport-windows.yml 결과와 추가 기록으로 구분합니다. 검사 소스는 생산 FFM/소켓/PSS를 사용하며 게임 owner 의존성만 대역입니다.
- 실제 게임 클라이언트 로그인·사냥·운영 DB·장시간 부하는 이 검사에 포함되지 않습니다.

## 실제 Windows 실행 결과

[Windows 검사 run43234341462](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43234341462), job131237594019: **success**. Microsoft Windows Server2025/Temurin JDK21에서 배포 DLL을 직접 로드했습니다. 검사 commit은 ef2e4fd0d0d9394dff248bf9dc19ed5c7fda6d65이며, 이후 기록 추가에서는 검사한 C++/Java/DLL을 바꾸지 않았습니다.

- C++ 실제 DLL:20개 접속/4MB 분할 echo와 순서·종료 통지.
- 생산 Java FFM 브리지:20개 접속/4MB echo,512개 작업, 타이머 만료·취소·종료.
- 생산 게임 소켓 어댑터:3개 접속/300KB, native16KB→Java4KB 읽기, stash/pause/resume/순서 송신.
- 생산 PSS UI 연결:분할 HELLO/PING/BOUND/OPEN, 원격 종료 detach, 잘못된 HELLO 거절, native 만료 timer 종료.

Windows 검사에서 게임 owner 의존성만 대역을 사용합니다. 실제 게임 클라이언트·운영 DB·장시간 성능 검사는 포함하지 않습니다. 검증한 DLL SHA256은 `5bf718c9df9e3dbce2eee824bbb3928430a2e142ee06abb45f14ae4156e33fa6`입니다. 로그 발췌와 WINDOWS_VERIFICATION.json을 배포 묶음에 포함했습니다. build/native-build.json은 cross-build 시점 기록이며, 이후 실제 실행 결과는 별도 검증 기록이 기준입니다.
