# NC I/O 동작 재구현 / 최대128개 배치 완료 처리

두 EXE의 RTTI/vtable/I/O 명령에서 확인한 객체 수명,8192-byte 버퍼,연결별 송신 FIFO,completion-key 가상 완료 처리 동작을 **새 C++ 소스로 구현**했습니다. NC 원본 C++ 전체 복구나 EXE 함수 바이트 추출본이 아닙니다. HP-Socket 함수에 대한 alias/의존성 없이 동작하는 별도 CIONcTransport.dll입니다.

## 구성

- `build/CIONcTransport.dll`: Windows x64 네이티브 전송 코어.27개 export.
- `build/CIONcTransport_SelfTest.exe`: 실제 Windows loopback 검사용 프로그램.
- `native/nc_core.hpp`: 참조 수명,8KB 재사용 버퍼 풀,송신 체인.
- `native/CIONcTransport.cpp`: IOCP worker,AcceptEx,WSARecv/WSASend,취소·종료·FFM C API.
- `src/.../CIOWinServer.java`, `CIOTransportConfig.java`: 기존 브리지와 명시적 선택/해시/ABI 검사.
- `research/NC_IO_CORE_RECOVERED.md`: 양쪽 EXE의 함수/필드/동작과 재구현 대응.
- `tests/`, `logs/`: 실제 C++ 코드의 가짜 Windows API 기반 메모리·스레드 검사 및 Java 회귀 결과.

## 실행 선택

기존 HP-Socket 전송이 기본값입니다. 이번 재구현으로 자동 전환하지 않습니다.

1. 전체 src ZIP을 기존 프로젝트에 반영하여 UTF-8,Java21 preview로 다시 빌드합니다.
2. `build/CIONcTransport.dll`을 기존 서버 작업 폴더에 복사합니다. 기존 CIOPacketCodec.dll은 계속 필요합니다. 원래 CIOWinTransport.dll도 보관합니다.
3. Windows에서 같은 build 폴더의 CIONcTransport_SelfTest.exe를 먼저 실행하여 실제 커널 IOCP loopback을 확인할 수 있습니다. 이 파일은 이번 Linux 환경에서 빌드만 했고 실행하지 못했습니다.
4. 기존 Java 실행 명령의 옵션에 다음을 추가하면 새 코어를 선택합니다.

```text
-Drkern.cio.transport=nc
```

`--enable-preview --enable-native-access=ALL-UNNAMED`와 기존 설정은 유지합니다. 해당 옵션을 제거하거나 `-Drkern.cio.transport=hpsocket`으로 지정하면 기존 전송을 사용합니다. 이름을 잘못 지정하거나 DLL의 해시/ABI가 다르면 시작을 거절합니다. 새 SQL 없음(기존 HPMP SQL은 이전과 동일).

## 네이티브 재빌드

검사한 컴파일러는 Zig0.14.1,target x86_64-windows-gnu,C++17입니다. Windows x64/UCRT 환경이 필요합니다. 빌드 로그의 import 목록을 참고하세요.

```bash
python native/build_native.py --zig /path/to/zig --write-java-hash
```

새 DLL 해시를 Java 허용값에 기록하므로 이후 Java도 다시 빌드해야 합니다. 검사 없이 해시 검사만 제거하지 마세요. 실제 IOCP/수신/송신 ABI가 그대로 맞는지 함께 검증해야 합니다.

## 검사 재실행

```bash
python tests/run_native_tests.py
python tests/run_native_tests.py --tsan
python tests/verify_native_binary.py
python tests/test_hpmp.py --src /path/to/full/src --libs /path/to/test-libs --native-dll build/CIONcTransport.dll
```

앞의 두 검사는 Linux g++와 ASan/UBSan/TSan을 사용합니다. 생산 C++ 소스에 연결한 Windows API fixture로 상태/수명/순서만 검증합니다. PE 검사는 pefile2024.8.26/capstone5.0.6이 필요합니다.

## 한계

**Windows 실DLL 실행·Java FFM 실제 접속·게임 클라이언트·실부하 검증은 미수행**입니다. 현재 결과는 빌드 가능한 선택형 재구현 실험본이며, 기존 코어와의 성능 우위나 운영 안정성을 보증하지 않습니다. 실제 Windows 테스트용 EXE도 제공했지만 그 통과만으로 전체 서버 검증을 대신하지는 못합니다.

이번 worker는 GetQueuedCompletionStatusEx로 최대128개 완료를 인출합니다. NC EXE에서 관측한 것은 단일 GQCS이며,128개 배치/종료 신호 재전달은 기존 HP-Socket DLL에서 확인한 동작을 참고한 확장입니다. 암복호화/패킷 규칙·게임 타이머/샤드/DB는 기존 것을 유지합니다. 세부 이식/미이식 표와 검증 범위는 보고서를 참고하세요.

## 이번 배치 확장

완료 ENTRY의 NTSTATUS snapshot을 항목별로 판별하고 RtlNtStatusToDosError로 변환합니다. 배치 dequeue 성공과 개별 I/O 성공을 혼동하지 않습니다. 여러 worker의 종료 신호를 한 worker가 받으면 실제 완료를 모두 처리한 뒤 자기 몫1개만 소비하고 나머지는 돌려줍니다.128개가 모일 때까지 기다리지 않으며, 준비된 완료를 최대128개 받습니다. 자세한 원본 근거는 research/BATCH128_BINARY_ORIGIN.md입니다.
