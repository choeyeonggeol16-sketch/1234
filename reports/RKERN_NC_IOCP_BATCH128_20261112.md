# 새 NC 동작 재구현 DLL: 최대128개 배치 완료 처리

기준: `d43e5a6ee397a0861247a0972178353d51eb0037`. 새 DLL은 기존 선택형 재구현의 GQCS 단일 완료 worker를 **GetQueuedCompletionStatusEx / 최대128개 완료**로 변경했다. ABI와 선택 옵션은 유지하고 Java DLL 허용 해시를 새 빌드로 갱신했다.

## 실제 바이너리를 참고했는가

**참고했다.** 두 NC EXE의 RTTI/vtable, I/O 호출, 버퍼 필드, 참조 확보·해제·송신 큐 명령을 해석하여 C++로 재구현했다. 원본 C++ 텍스트 전체를 복구하거나 함수 명령 바이트를 DLL에 붙인 것은 아니다.

| 구분 | 실제 분석 근거 |
|---|---|
| 객체 수명·완료 디스패치 | NC x64 retain0x7d6d50, socket completion0x45f028, dispatch0x7058d8; x86 dispatch0x53cbe3 |
| 8KB 버퍼·송신 FIFO | NC x64 read0x45e138, Write0x45e480, WriteCallback0x45e8a0; x86의0x2000 계산 |
| **128개 배치·종료 신호 재전달** | **기존 CIOWinTransport.dll**의0x180049646/0x180049659 및0x180049738~0x180049768 |
| 새 설계 | AcceptEx adapter,opaque 연결 ID,상한,FFM 연결,부분 송신 재시도,NTSTATUS 직접 변환 |

**NC 두 EXE의 worker는 단일 GQCS로 확인했다.** 128개 배치는 기존 HP-Socket 기반 DLL의 실제 구현을 참고한 확장이다. 구분과 원본 해시/명령은 패치 폴더 `research/BATCH128_BINARY_ORIGIN.md`, `hp-batch-worker-excerpt.txt`, `nc-rtti.json`에 있다.

## 변경한 동작

1. worker마다 stack의 OVERLAPPED_ENTRY[128]을 사용한다. 준비된 완료를 최대128개 꺼내며128개가 찰 때까지 기다리지 않는다. I/O 완료 개수이며 게임 패킷128개나128배 성능을 뜻하지 않는다.
2. 배치 dequeue의 BOOL 성공과 개별 I/O 성공을 분리한다. ENTRY.Internal의 NTSTATUS snapshot으로 항목별 성공/실패를 판단한다. 실패 상태는 ntdll의 RtlNtStatusToDosError로 변환한다. resolver를 구하지 못하면 시작을 거절한다.
3. callback이 같은 OVERLAPPED를 재제출할 수 있으므로 현재 OVERLAPPED.Internal을 다시 읽지 않는다. dequeue 시점 ENTRY snapshot을 사용한다.
4. 한 worker가 여러 종료 토큰을 가져오면 배치의 실완료를 전부 처리하고,자기 몫1개만 소비한 뒤 나머지 토큰을 다시 넣는다. 종료 토큰을 보자마자 나머지 완료를 버리거나 다른 worker를 잠든 채 두지 않는다.
5. 각 완료의 객체 I/O 참조는 처리 후 정확히1회 해제한다. 기존8KB pool/FIFO·부분 송신·pause·종료 장벽은 유지한다.

배치 인출/종료 토큰의 큰 흐름은 제공된 기존 전송 DLL과 같다. 그 DLL은 오류에 필요 시 WSAGetOverlappedResult를 사용하지만,새 코어는 ENTRY NTSTATUS를 직접 변환한다. 모든 세부 코드가 원본과 같다는 뜻은 아니다.

## 검증 결과

- Windows x64 DLL 및 Windows loopback self-test EXE cross-compile 성공.
- 실제 새 PE가 Ex/IOCP/Winsock를 import하며 단일 GQCS import는 없음.27개 export,기존 ABI0x10000,Java 새 SHA256 허용값 일치.
- 실제 생산 C++ worker에서273개 완료+종료1개를 **128/128/18**로 처리.
- **종료 토큰4개+실완료124개**를 한 배치에 넣어 실제 완료 누락 없이4개 worker 모두 종료.객체 참조 누수/중복 해제 없음.
- cancel/access-denied/informational/unknown NTSTATUS 혼합.배치 BOOL이TRUE인 실패 I/O도 실패로 처리.OVERLAPPED의 현재 상태를 일부러 다르게 넣어 ENTRY snapshot 사용 확인.
- ASan/UBSan 및 TSan 검사 통과.이전8분할56,000바이트 송신,17,000바이트 echo/37바이트 부분 완료,수신 callback 중 종료,즉시 실패,accept 거절,120연결/600회 병렬 close 검사도 같은 C++ 엔진에서 통과.
- Java1,697개 전체 타입 컴파일 성공,오류0·기존 removal 경고5.기존 기본값/선택 옵션/미지정 엔진 거절/새 DLL 해시 검사 통과.
- 이번 변경의 생산 Java 차이는 CIOTransportConfig의 해시1개뿐이다.게임 코드와 기존 브리지는 이전 ZIP과 바이트 단위로 동일하다.이번에는 전체 게임 회귀를 다시 실행하지 않았으며,이전 통과 이력은 직전 재구현 보고서에 있다.
- 전체 source ZIP CRC·파일별 바이트·SHA256 및 직전 src에 patch 적용 후 전체 일치 확인.

**ASan/UBSan/TSan 실행에서는 Windows completion API를 fixture로 대체했다. 실제 Windows 커널·DLL/Java FFM·실게임·운영 DB·동접/장시간 성능은 미검증**이다. Windows 검사용 EXE는 빌드만 했으며 여기서 실행하지 않았다. 실험본 분류와 기존 HP-Socket 기본값을 유지한다.

## 적용

새 source와 DLL은 함께 적용한다. 이전 Java 허용 해시로는 새 DLL 로드를 거절한다. 전체 src를 재빌드하고 새 `build/CIONcTransport.dll`을 서버 작업 폴더에 복사한다. 기존 CIOPacketCodec.dll과 원래 CIOWinTransport.dll은 유지한다. Java21 preview/native-access 설정도 그대로다.

선택 옵션은 동일하게 `-Drkern.cio.transport=nc`.옵션을 제거하거나 hpsocket을 지정하면 기존 전송을 사용한다.추가 SQL 없음.

- [최신 전체 src](../downloads/RKern_Aden_NC_IOCP_BATCH128_FULL_SRC_20261112.zip)
- [배치 DLL·C++·테스트 묶음](../downloads/RKern_NC_IOCP_BATCH128_CORE_20261112.zip)
- [적용·재빌드 안내](../patches/rkern_nc_iocp_batch128_20261112/README.md)

새 원본 EXE/사용자 제공 DLL을 재배포하지 않으며,새로 작성한 코어·분석 기록·선택된 명령 근거만 포함한다.
