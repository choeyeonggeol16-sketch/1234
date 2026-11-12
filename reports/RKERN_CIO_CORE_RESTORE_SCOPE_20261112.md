# RKern CIO 명칭 정리와 추가 복원 범위

기준 a4b3435dba7dc21725821d4415b3150bd2d3d913. 현재 배포 이름을 **RKernCIOTransport**로 통일했습니다. 실제 DLL·self-test·C++ source/header/namespace·버전 export·Java 브리지를 함께 변경하고 재빌드했습니다. 기존128개 배치 처리와 I/O 수명 동작은 유지합니다. 분석 출처를 숨기거나 원본 C++ 전체를 복구했다고 표현하지 않습니다.

## 이번 이름 변경

| 항목 | 현재 이름 |
|---|---|
| DLL | RKernCIOTransport.dll |
| Windows 검사 EXE | RKernCIOTransport_SelfTest.exe |
| C++ 구현/헤더 | RKernCIOTransport.cpp / RKernCIOTransport.h / cio_core.hpp |
| 내부 namespace / 검사 매크로 | rkern_cio / RKERN_CIO_TEST |
| 버전 export | RKernCIO_Version |
| 실행 옵션 | -Drkern.cio.transport=rkern |

기존 nc 문자열 입력은 호환 별칭으로만 받습니다. 새 RKern 파일·해시·export를 사용하며 옛 파일로 fallback하지 않습니다. 원래 hpsocket 기본값도 유지합니다.

## 어디까지 더 복원할 수 있는가

고정된 복원율을 제시할 근거는 없습니다. **네트워크 기반부의 실행 흐름은 더 넓게 복원할 수 있지만,원본 소스·서버 전체의 동일성을 보장할 수는 없습니다.** 아래는 이번에 실제로 더 읽은 x64 함수와 현재 상태입니다. x86과의 전체1:1 대조는 이 추가 범위에서 아직 하지 않았습니다.

| 대상 | 이번에 확인한 사실 | 추가로 넣을 수 있는 형태 / 남은 조건 |
|---|---|---|
| **스레드별 버퍼 풀** |0x45d7f8은 TLS 스레드 번호로 풀을 고르고 ring 위치/용량을 사용. 버퍼+0x2034에 소유 풀 번호를 기록.0x45d67c 반납은 cmpxchg 잠금과 ring 슬롯/카운터를 사용 | 현재 전역 mutex free-list를 대신할 소유자별 pool/원래 pool 반납 구조를 재구현 가능. Java 호출 스레드는 원본의 등록된 서비스 스레드와 달라 별도 등록/외부 스레드 fallback 설계 필요 |
| **8KB/16KB 변형** | 이전 RTTI에서 두 template family와 vtable 확인. 현재 엔진은8KB만 사용 |16KB pool/bucket와 크기별 선택 추가 가능. 메모리 상한·프로토콜 최대 길이·성능 측정 후 기본값 결정 |
| **공유 수신 버퍼 수명** | 기존 read 함수는 ref>1이면 새 버퍼로 남은 데이터를 복사하고,ref1이면 같은 버퍼에서 이동 | shared block/retain/COW 구조 재구현 가능. 현재 Java/codec가 미완성 프레임을 소유하므로 중복 복사 계층을 추가하는 대신 소유권부터 조정해야 함 |
| **세부 종료 정책** |0x45d534는 socket handle을 xchg(-1)하여1회만 닫고,가상 hook 후 SO_LINGER(1,0)와 closesocket 호출 | 강제 reset/종료 hook 순서를 더 복원 가능. 원본의 linger0은 현재 송신 드레인·종료 응답 정책과 같지 않아 그대로 기본 적용하지 않음 |
| **일반 completion 작업 전달** |0x704248은 객체를 completion key로 PostQueuedCompletionStatus에 넘기며 실패 시 ref 해제 | 소켓 I/O 이외의 작업 전달·참조 수명 계약 재구현 가능. 현재 Java packetIngress/owner 실행과 역할을 한 곳으로 정해야 함 |
| **50ms 주기 작업·타이머 연계** | 이전 worker의 GetTickCount/50ms 분기와 이번0x705330→0x70482c 경로 확인. 실제 타이머 구조 전체는 아직 미확정 | 등록/취소/만료/참조 해제까지 의존 함수를 더 추적한 뒤 연계 가능. 현재 Java timer/shard와 중복 실행하면 안 됨 |
| **패킷 수신 파서 계층** |0x45de2c는 EOF 종료,buffer 길이 누적,통계 후 가상 함수+0xa0로 위임. 실제 packet framing은 더 위의 파생 socket을 봐야 함 | 길이 검증/분할·합친 수신/세션별 파서 흐름 복원 가능. 제공된 L2Server의 게임 packet ID·암호화를 L1 규칙으로 그대로 사용하지 않음 |

0x704248을 파일명만 보고 타이머 등록이라고 단정하지 않았습니다. 이번 명령 확인 결과는 **일반 completion 게시 함수**입니다. timer 관련 짧은 엔트리 뒤의 구현은 더 추적해야 하므로 우선 후보로 구분합니다. 구조가 미확정인 timer wheel/heap 종류나 정확한 상수를 발명하지 않았습니다.

실제 추가 명령은 research/scope/*.asm.txt와 scope-functions.json,재현 스크립트는 trace_restore_scope.py에 있습니다. 입력 SHA256을 확인하고 바이너리를 실행하지 않는 정적 분석입니다.

## 추천 우선순위

1. **버퍼 풀과 소유 풀 반납**을 먼저 복원: 현재 구현과의 차이가 명확하고 명령 근거도 구체적입니다. 외부 Java 스레드·pool exhaustion·다른 worker 반납을 검사해야 합니다.
2. **16KB 변형·종료 예외 경로**: 기존 client protocol과 send-drain 정책을 보존하면서 비교할 수 있습니다.
3. **작업 큐·타이머 연계**: 범위가 게임 샤드 실행 소유권으로 확장되므로 더 긴 의존성 추적이 필요합니다.

새 worker를 추가하거나 Java 게임 타이머를 native에서 두 번 실행하는 것을 추가 복원으로 간주하지 않습니다. 실제 Windows DLL/FFM 접속 검증도 병행해야 합니다.

## 이번 구현/검증의 정확한 범위

이번에 구현한 것은 **명칭·API query·선택값 변경과 구버전 옵션 호환**입니다. 위의 추가 pool/타이머/파서 기능은 아직 구현하지 않았습니다. 네이티브 실행 코드는 이름 치환 후 이전과 동일함을 비교했습니다.

- Windows x64 새 DLL/self-test cross-compile 성공.
- 실제 PE27개 export/RKernCIO_Version/배치 IOCP import/새 Java SHA256 일치.
- 생산 C++의 Windows API fixture 기반 ASan/UBSan 검사 통과.128배치/혼합 NTSTATUS/종료 신호/기존 수명·FIFO 검사 포함.
- Java1,697개 전체 타입 컴파일과 새 이름·기본값·호환 옵션·잘못된 값 거절·DLL 해시 검사 통과.
- 이번 이름 변경에서는 TSan/전체 게임 회귀를 다시 실행하지 않았습니다. 배치 구현 직전 버전의 통과 이력은 이전 보고서에 있습니다.
- 실제 Windows DLL/FFM/게임/운영 DB/부하 실행은 미검증입니다. 선택형 실험본 분류 유지.

## 적용 파일

새 DLL과 전체 src를 함께 반영하고 `-Drkern.cio.transport=rkern`을 사용합니다.기존 codec/설정/Java21 preview/native-access 유지.추가 SQL 없음.

- [전체 src](../downloads/RKern_Aden_CIO_CORE_FULL_SRC_20261112.zip)
- [RKern CIO DLL·C++·검사 자료](../downloads/RKern_CIO_CORE_20261112.zip)
- [적용 안내](../patches/rkern_cio_core_20261112/README.md)
