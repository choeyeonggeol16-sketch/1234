# 제공된 두 EXE에서 복원한 I/O 동작

원본 C++ 텍스트/변수명 복구가 아닌, RTTI·vtable·I/O 호출·필드 접근 명령의 동작 복원이다. 실제 실행 가능한 재구현은 `native/cio_core.hpp`, `native/RKernCIOTransport.cpp`이며 원본 명령 바이트를 포함하지 않는다.

## 객체 / 완료 디스패치

| 항목 | x86 L2Server.exe | x64 L2Server(10).exe |
|---|---|---|
| CIOObject vtable | 0x5ccf0c | 0xc71c18 |
| 소켓 vtable | CIOSocket 0x61d72c | CIOSocketEx<CIOBufferEx<8192>> 0xb54c18 |
| 다른 버퍼 크기 | 이 클래스는8192 | 16384 변형 vtable0xbbe878도 존재 |
| ref 확보 | slot1 / 0x5188e0 | slot1 / 0x7d6d50, lock add [this+8],1 |
| ref 해제 | slot0 / 0x53aa90 | slot2 / 0x7d6eb4 |
| GQCS 호출 | 0x53cb6f | 0x7057c9 |
| completion key virtual dispatch | 0x53cbe3, vtable+0x30 | 0x7058d8, vtable+0x50 |
| 소켓 completion 구현 | slot12 / 0x53bdb0 | slot10 / 0x45f028 |

두 바이너리의 vtable 슬롯/레이아웃은 같지 않다. 구조체 오프셋이나 함수 주소를 새 DLL에서 재사용하지 않는다.

공통 실행 계약: I/O를 제출하기 전에 소켓 참조를 확보한다. 동기 성공0과 IO_PENDING997은 완료가 올 것으로 처리한다. 즉시 실패한 요청은 close 및 참조 해제로 정리한다. Worker는 completion key 객체의 가상 완료 함수를 호출하고 read/write OVERLAPPED를 구별한다. x64 소켓 완료 함수0x45f028은 read OV=this+0x58, write OV=this+0x78을 비교하고 해당 완료 처리 후 ref 해제를 호출한다.

재구현: CIOObject의 retain/release, Operation의 OVERLAPPED와 owner, Server.worker의 completion-key 가상 dispatch. 매 kernel/synthetic 요청당 정확히1개의 I/O 참조를 확보·해제한다. 종료는 posted I/O와 실행 중 콜백이 모두 끝난 뒤 전달한다.

## 읽기 / 남은 데이터 수명

x64 read 함수0x45e138은 기존 버퍼에서 남은 데이터를 앞으로 옮긴다. 참조 수가1보다 크면 새 버퍼로 복사하여 다른 소비자가 보는 데이터를 덮어쓰지 않는다. 참조가1이면 같은 버퍼 안에서 이동한다. 남은 길이를 저장하고 `8192 - 남은길이`만큼 WSARecv를 제출한다(호출0x45e35b).

x86에서도 WSARecv0x53b4ec 앞에서 0x2000에서 저장된 길이를 뺀다. 양쪽의 읽기 상태 보존은 동일한 방향이다.

재구현은 기존 RKern 계층을 보존한다. native read 배열은 callback 반환까지 독점하며 readDelivering 동안 다음 WSARecv를 금지한다. 이후 Java의 기존 direct buffer와 codec Feed/Next가 미완성 프레임을 소유한다. NC의 공유 수신 버퍼 구조체와 copy-on-write 자체를 불필요하게 한 겹 더 넣지는 않는다. 이는 같은 바이트 수명 보장을 현재 계층에 맞게 구현한 것이다.

## 쓰기 / 블록 FIFO

x64 Write0x45e480에서 이미 송신 중이면 대기 tail에 추가한다. 활성 head가 아닌 tail에 공간이 있으면 합쳐서8192 이내로 유지하고, 그렇지 않으면 next로 연결한다. 완료 처리0x45e8a0은 head를 다음 블록으로 이동하고 대기 블록의 WSASend를 시작한다(0x45ea2f). 마지막 블록이면 tail도 비운다.

관측된 x64 버퍼 레이아웃: payload+0x10, 길이+0x2010, next+0x2018, 참조 수+0x2020. x86은 payload/길이 오프셋은 같지만 next+0x2014로 포인터 폭이 다르다.

재구현: 8192-byte CIOBufferPool과 SendChain. 활성 블록을 수정하지 않고 비활성 tail만 합친다. 다중 WSABUF는 완전한 소유 복사본을 준비한 뒤 원자적으로 연결한다. 완료 콜백이 참조하는 블록은 callback 반환까지 보관한다.

원본 WriteCallback은 완료 길이가 head 길이와 다르면 로그 후 정상 dequeue 경로로 진행하지 않는다. 재구현은 부분 완료 시 offset을 갱신하고 나머지를 재제출한다. 0 또는 남은 길이를 초과한 완료는 연결을 종료한다. 오래된 불일치 정책까지 그대로 복제하지 않는다.

## 의도적으로 달라진/범위 밖인 부분

- x64의16384-byte 변형은 확인했지만 이번 기본 블록은 두 파일에서 공통으로 확인되는8192를 채택했다. Java 요청 receive chunk4096은 유지한다.
- AcceptEx, opaque64-bit ID,64개 registry bucket,4MiB/연결 송신 상한,65536개 전역 블록 상한, 종료 deadline·재진입 guard는 RKern에 맞게 새로 구현한 부분이다. NC 원본의 정확한 설정이라고 주장하지 않는다.
- 최초 재구현은 NC에서 관측한 GQCS 단일 완료 방식이었다. 이번 버전은 기존 HP-Socket DLL의128개 배치/종료 신호 재전달을 참고해 확장했다. 성능 우위는 측정하지 않았다.
- 원본 worker의50ms 타이머 부대 처리, 게임 패킷/인증/암호화 규칙, 모든 게임 객체의 memory manager는 이식하지 않았다. 기존 Java 샤드·타이머·CIOPacketCodec.dll을 사용한다.
- NC의 raw layout, 메모리 주소, EXE 함수/명령 바이트에 의존하는 실행 코드는 없다. 재구현의 ABI/메모리 관리는 독립적이다.

`recover_server_core.py`는 두 EXE에 실행 권한을 주거나 로드하지 않고 RTTI/가상 함수 표와 선택된 명령을 추출한다. 해시와 vtable 결과는 server-rtti.json, 명령은 각 .rtti-disasm.txt에 있다. 구형 x64 COL signature0과 신형 signature1의 RVA RTTI를 구별한다.
