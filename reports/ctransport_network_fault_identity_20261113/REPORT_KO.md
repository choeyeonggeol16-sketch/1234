# 네트워크 풀 오류 경로와 MemoryObject 식별자 복원

2026-11-13. 기준 `581cfc4`에서 남긴 두 항목을 원본 x64와 대조하고 기존 네트워크 코드에 반영했다. 손상된 비어 있지 않은 풀 항목에서 할당이 막히던 경로를 수정하고, 원본 MemoryObject 식별자와 참조 오류 추적을 추가했다. 기존 풀·송수신 엔진·진단창을 다시 만들지 않았다.

## 원본 확인

| 대상 | 원본 주소 | 확인한 동작 |
|---|---|---|
| 공통 오류 함수 | `0x66db28` | `xor eax,eax; mov byte ptr [rax],1; ret`의 6바이트 본문. 의도적인 NULL 쓰기 접근 위반 |
| 예외 필터 설치 | `0x670808` | `0x670750`을 SetUnhandledExceptionFilter에 등록하고 이전 필터 저장 |
| 예외 필터 | `0x670750` | 진단 수집 후 이전 필터로 전달하거나 0(CONTINUE_SEARCH) 반환 |
| 호출 추적 출력 | `0x6704e0` | TLS callstack 출력, 요청한 경우 `L2Exception::sendCriticalCallstack` 호출 |
| MemoryObject 기본/복사 생성 | `0x7d6d5c` / `0x7d6e1c` | 같은 전역 카운터에서 새 32비트 ID를 얻어 객체 `+0x10`에 기록하고 ref를 1로 초기화 |
| MemoryObject Release | `0x7d6eb4` | ref 감소 결과가 음수인 오류 경로에서 객체 `+0x10` ID를 진단에 사용 |
| CUserSocket Release | `0x9f7770` | 하위 참조 추적 및 전체 ref 오류 진단, `0x9f792d`에서 객체 ID 읽기 |
| IOCP 게시 | `0x704248` | completion key로 객체 포인터 전달. 진단 ID를 연결 핸들로 사용하지 않음 |
| 16K/8K Alloc | `0x41e6e4` / `0x45d7f8` | 손상된 nonnull 항목을 소비하고 다음 항목 검사. null 항목에서는 위 오류 함수 호출 |

공통 오류 함수는 소켓 종료나 풀 복구 함수가 아니다. 일반적인 Windows 실행에서는 주소 0 쓰기로 예외가 발생하며 원본의 호출 다음 코드가 정상적으로 진행한다고 볼 수 없다. 등록된 unhandled filter도 I/O 복구를 구현하지 않는다. 이전 필터·외부 예외 처리기의 모든 동작까지 정적으로 증명한 것은 아니다.

`research/network-fault-identity/EVIDENCE.json`에 12개 실제 명령의 바이트, 두 생성자의 공통 ID 카운터 주소, CIOObject/CSocket/CUserSocket의 Release vtable 슬롯과 함수 범위를 보존했다. 원본을 실행하지 않았다. 입력 x64 SHA256은 `9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588`이다. 새 x86 전수 감사나 전체 미명명 함수의 의미 복원 완료를 뜻하지 않는다.

## 수정한 네트워크 동작

**손상 풀 항목 처리.** 이전에는 free ring 맨 앞의 ref/used/next가 비정상이면 같은 슬롯에서 매번 nullptr를 반환했다. 이제 원본처럼 그 nonnull 항목을 소비하고 다음 정상 항목을 시도한다. 읽는 슬롯 수는 기존 고정 용량으로 제한하며 풀을 자동 확장하지 않는다.

제외한 view는 격리 표시를 남긴다. 이후 이전 보유자가 최종 release를 하더라도 ring에 다시 넣지 않는다. 손상 필드를 억지로 초기화하거나 메모리를 강제로 해제하지 않는다. 격리 표시는 기존 raw slab 바깥의 view에 두어 8K `0x2038` / 16K `0x4038` stride와 ref/used/next/owner 위치를 유지한다. 정상 FIFO, owner 반납, 공유 ref와 COW 계약도 유지한다.

대여·반납 수와 사용중 수에서는 격리 슬롯을 제외하고 격리 수를 따로 표시한다. 확보 용량과 slab bytes에는 해당 메모리가 남아 있다. 격리는 용량 손실을 수반하며 자동 복구나 누수 해결을 의미하지 않는다. null 슬롯·잘못된 owner 등 모든 임의 메모리 손상을 자동 복구하는 기능은 추가하지 않았다. 원본의 의도적 크래시는 기존 L1의 실패 반환 정책으로 유지했다.

**MemoryObject 식별자·참조 오류.** 기존 MemoryObject를 확장해 원본과 같은 전역 증가 32비트 ID를 객체 `+0x10`에 둔다. 기본/복사 생성 모두 새 ID와 ref1을 얻는다. 복사 원본의 ref나 ID를 복제하지 않는다. 실제 소켓과 ObjectRef의 복사 금지는 유지한다. 원본 `+0x0c`의 timer 상태 필드를 별도로 중복 추가하지 않았으며 현재 timer의 참조 보유 계약을 유지한다.

ID는 참조 오류 원인 추적에 사용한다. 연결 번호·계정 ID·slot generation 핸들은 그대로이며 이 ID로 대체하지 않는다. 원본처럼 32비트 카운터는 wrap할 수 있으므로 영구 고유 식별자나 접근 권한으로 사용하지 않는다.

MemoryObject와 pool의 ref 감소에는 0 여부를 확인하는 CAS를 사용한다. 살아 있는 객체/슬롯에서 동시 잘못된 release가 발생해도 unsigned 카운터가 wrap하지 않도록 한다. MemoryObject 오류는 프로세스 누적 횟수와 마지막 객체 ID/감소 전 ref만 고정 크기로 보관한다. 이미 삭제된 객체의 dangling pointer 접근까지 안전하게 만드는 기능은 아니다.

## 기존 진단에 연결

매니저의 **보고서 → 풀·회수 진단**에서 Native 격리 슬롯 8K/16K, 프로세스 MemoryObject 참조 오류 횟수, 마지막 객체 ID와 감소 전 ref를 확인한다. 별도 창·로그 시스템·DB를 추가하지 않았다. 오류 이력과 정상 버퍼의 미반납 판정은 구분해 표시한다. 오류 레코드는 마지막 한 건만 유지하며 새 무제한 파일 로그를 만들지 않는다.

기존 `L1Interop_GetDiagnostics`를 확장했다. count8 prefix와 count18 풀 측정은 유지한다. count24 요청에서만 index18 marker `0x4354464100010000`과 index19~23의 격리8K/격리16K/프로세스 참조오류/마지막ID/감소전ref를 제공한다. 구 확장 marker가 없는 값을 정상 측정으로 오인하지 않는다. ABI10 / 67 exports는 같다. 여러 필드의 동시 조회는 전역 트랜잭션 snapshot이 아니며 마지막 오류와 누적 카운터는 순간적으로 다른 시점일 수 있다.

## 검증

- 원본 SHA와 실제 명령 12개, 두 생성자의 공통 카운터 및 네트워크 Release vtable 3개를 재검증했다.
- ASan/UBSan 및 TSan: ref1 복사·ID 위치, 4,000개 동시 생성 ID, 살아 있는 객체에 주입한 4,001개 참조 오류에서 카운터 wrap 방지.
- 양쪽 풀에서 손상 항목 3개를 한 번에 건너뛰기, 늦은 두 스레드 반납의 재사용 차단, 정상 블록 2,000회 재사용과 반납 후 사용중0, 8/18/24 요청의 출력 경계 canary를 검사했다.
- 기존 native 전체 ASan/UBSan 회귀를 통과했다. SEND·부분 송신·암호화·공유 broadcast·종료·타이머·serializer·registry가 포함된다. 이 Linux 검사의 Windows API는 fixture다.
- 실제 Windows run **43479323594**, job **132071365275**, 테스트 커밋 **1a16f1cde8cde4d6e2c7618f4d74e5d05ded764b**에서 새 DLL loopback, production Java FFM/codec/session/close, 풀 회수와 count24 marker·정상 오류0, 기존 SafeSend·broadcast·독립 PSS 회귀를 통과했다.
- 새 Java 진단은 이전18개 측정과 새24개 측정, 미지원 marker, 격리 수와 정상 대여 수의 분리 표시를 검사했다. 실제 게임 클라이언트·운영 DB·장기간 실서버 부하 검사는 이번 범위 밖이다.

실행 코드 변경은 native `cio_core.hpp` / `CTransport.cpp`, Java `CIOServer` / `CPoolDiagnostics` / DLL SHA pin인 `CIOConfig`다. 이전 전체 src의 나머지 **1,709개 파일(그중 Java 1,708개)**은 동일하다. 게임 규칙·GUI 구현·독립 PSS·코덱 DLL·SQL은 수정하지 않았다. `tests/verify_network_fault_scope.py`와 `SOURCE_BASELINE.json`으로 확인한다. 추가 ID atomic과 CAS의 실제 처리량 변화는 벤치마크하지 않았다.

## 적용과 재현

**새 전체 src와 CTransport.dll을 함께 적용한다.** native bundle에는 C++·검사·근거가 포함된다. 새로운 SQL이나 설정 변경은 필요 없다. DLL SHA256은 `7ea2195879cd1a414796ffbd17477e3c842bc419c475acf8345e36d861538afc`이며 Java pin과 일치한다. 이전 DLL을 새 소스와 혼용하면 SHA 검증에서 거절된다.

```sh
python tests/run_network_fault_tests.py
python tests/run_network_fault_tests.py --tsan
python tests/verify_network_fault_scope.py
python research/network-fault-identity/audit_error_identity.py --binary '/path/to/L2Server(10).exe'
```

원본 대조에는 pefile/capstone이 필요하다. 원본 EXE는 배포 묶음에 포함하지 않는다. 전체 src1712개 파일/Java1711개이며 현재 src는 기준 배포 위에 변경을 적용한 결과와 바이트 단위로 대조한다. 재현 도구와 로그는 native bundle 및 별도 대조 자료에 보존했다.
