# RKern CIO 전송 코어

현재 이름은 `RKernCIOTransport.dll`입니다. 제품 파일명,C++ namespace/헤더/매크로,버전 export,Java 선택값을 RKern으로 변경했습니다. 최대128개 배치 완료·수신/송신·버퍼 수명 동작은 이전과 같습니다. 원본 바이너리 분석 출처는 research에 별도로 보존합니다.

## 적용

1. 전체 src ZIP을 기존 프로젝트에 반영하여 UTF-8/Java21 preview로 다시 빌드합니다.
2. `build/RKernCIOTransport.dll`을 서버 작업 폴더에 복사합니다. 기존 CIOPacketCodec.dll과 원래 CIOWinTransport.dll은 계속 보관합니다.
3. 기존 Java 실행 옵션에 다음을 추가합니다.

```text
-Drkern.cio.transport=rkern
```

기본값은 기존 hpsocket입니다. 구버전 `nc` 입력은 호환 별칭으로만 허용하며 동일한 새 RKern DLL을 선택합니다. 옛 DLL 파일명으로 fallback하지 않습니다. DLL 해시와 RKernCIO_Version을 검사하므로 **새 DLL과 src를 함께 반영**합니다. 새 SQL 없음.

`build/RKernCIOTransport_SelfTest.exe`는 실제 Windows loopback 검사 프로그램입니다. 이번 환경에서는 빌드만 했고 실행하지 않았습니다. 기존 preview/native-access 실행 옵션을 유지합니다.

## 소스와 재빌드

- `native/RKernCIOTransport.cpp`: Windows IOCP/AcceptEx/수신·송신·종료/C API.
- `native/cio_core.hpp`: 객체 참조 수명,8KB buffer pool/FIFO.
- `native/RKernCIOTransport.h`: C API 선언.
- `native/build_native.py`: Zig0.14.1 Windows x64 빌드 및 Java hash pin 갱신.

```bash
python native/build_native.py --zig /path/to/zig --write-java-hash
python tests/run_native_tests.py
python tests/verify_native_binary.py
```

Java 선택 검사와 전체 타입 컴파일은 test_java_batch.py에 --src,--libs,--build,--native-dll 경로를 지정합니다. pefile2024.8.26/capstone5.0.6이 PE 분석에 필요합니다. Windows x64/UCRT 대상입니다.

## 추가 복원 범위

스레드별 buffer ring/소유 풀 반납,16KB 변형,수신 공유 버퍼 수명,세부 종료 정책,일반 completion 작업 전달까지 추가 분석·재구현할 수 있습니다. 현재 확보한 함수 근거와 아직 추적하지 않은 의존성은 상세 보고서에 정리했습니다. 이번 버전은 이름 변경이며 그 추가 기능들을 구현했다고 주장하지 않습니다.

Windows API fixture에서 생산 C++ 검사와 Java 컴파일/선택/해시 검사는 통과했습니다. **실제 Windows DLL/FFM/게임/부하 실행은 미검증**인 선택형 실험본입니다. 성능 우위나 원본 전체 복원을 뜻하지 않습니다.
