# 남은 네트워크 signature 9개 원본 함수 대조

2026-11-13. 기준 배포 `e870c63e2c7c99f578c1797b7a8edeabf50eac02`의 미확정 문자열 9개를 원본 x64의 실제 함수에 연결했다. **이번 9개에서 추가 이식해야 할 정상 네트워크 동작은 확인되지 않았다.** 기존 코드와 역할이 대응하는 부분, 메모리 관리·오류 정책이 다른 부분을 아래에 구분했다. 이번 변경은 분석 자료와 검색 도구뿐이며 DLL·Java·설정·배포 ZIP은 기준 배포와 같다.

## 확인 결과

| 원본 signature | 원본 진입점 | 문자열 참조 명령 | 현재 대응 |
|---|---|---|---|
| MemoryObject::operator delete | `0x401f3c` | `0x401fe2` | 일반 반환 함수가 아닌 금지된 delete 사용 진단. 현재 heap 객체의 최종 release와 구분 |
| CNewMwsrQueue<16384>::UnLock | `0x419e8c` | `0x419e96` | SpinLock::unlock / SpinGuard 소멸자 |
| CNewMwsrQueue<16384>::Push | `0x41c6f8` | `0x41c74a` | CIOBufferPool::recycle의 owner ring 반납 |
| CIOBufferEx<16384>::Alloc | `0x41e6e4` | `0x41e885` | CIOBufferPool::acquire(16384) |
| CNewMwsrQueue<8192>::UnLock | `0x45d3b4` | `0x45d3be` | SpinLock::unlock / SpinGuard 소멸자 |
| CNewMwsrQueue<8192>::Push | `0x45d67c` | `0x45d6ce` | CIOBufferPool::recycle의 owner ring 반납 |
| CIOBufferEx<8192>::Alloc | `0x45d7f8` | `0x45d999` | CIOBufferPool::acquire(8192) |
| MemoryObject::MemoryObject() | `0x7d6d5c` | `0x7d6da0` | MemoryObject의 초기 참조 수 1. 객체 ID 규약은 별도 |
| MemoryObject::MemoryObject(const MemoryObject&) | `0x7d6e1c` | `0x7d6e60` | 원본은 새 참조 수·새 ID를 생성. 현재 I/O 객체 복사는 금지 |

현재 대응 파일은 [native/cio_core.hpp](../../patches/ctransport_core_20261112/native/cio_core.hpp)다. 초기 참조 수는 14~19행, 잠금은 33~39행, 대여는 125~133행, 최종 반납은 136~145행에 있다. 원본 주소별 실제 명령은 `remaining_<entry>.asm.txt`, 참조 명령·바이트·함수 범위·직접 호출 근거는 `REMAINING_REFS.json`에 보존했다.

7개 문자열은 앞에 UTF16 TAB이 붙은 오류 분기의 진단 문자열이었다. 이전 검색은 TAB 다음 글자에서 문자열을 식별했지만 코드가 참조한 TAB 주소를 대조하지 못했다. 나머지 생성자 2개는 unwind/pdata가 없는 작은 함수여서 실제 직접 호출 대상과 도달 가능한 leaf CFG로 경계를 확인했다. 오류 문자열만 보고 별도 미구현 함수라고 판단하지 않았다.

기존 142개 진입점 중 3개와 중복되고 6개가 새로 연결되어 **목록은 148개 진입점, 선택한 signature 집합의 미연결 문자열은 0개**가 됐다. 148개에는 이전에 분리한 L2 게임·운영 함수도 포함된다. 148개 모두 새로 구현했다는 뜻이나 원본 전체 복원율은 아니다.

## 원본과 현재 코드의 정확한 차이

**8K/16K 풀.** 원본 Alloc은 owner ring에서 꺼낸 블록의 ref/used/next를 검사하고 owner를 기록한 뒤 ref를 1로 만든다. Push는 잠금 아래 빈 반환 슬롯을 확인하고 포인터를 넣은 뒤 반환 인덱스를 증가시킨다. 현재 코드에도 이 정상 대여·최종 반납 역할이 있다. 다만 현재는 FFM 외부 생산자도 있으므로 읽기와 쓰기를 같은 gate로 보호한다.

원본 Alloc은 손상된 비어 있지 않은 항목을 꺼낸 경우 진단 후 다음 항목을 시도한다. 현재는 무결성 오류를 세고 해당 대여를 거절하며 인덱스를 전진시키지 않는다. 고갈 시에도 원본은 `io_buffer_crash` 관련 진단과 공통 오류 처리 `0x66db28`을 호출하지만 현재는 실패를 반환한다. UnLock의 잠금 상태 불일치 진단도 완전히 같지 않다. 이번에는 그 오류 정책을 바꾸지 않았다. 참조가 잘못된 블록을 자동으로 건너뛰거나 다시 회수하는 동작을 추가하면 늦은 반납·중복 사용 문제와 풀 계수 변경을 함께 검증해야 한다. 해당 공통 오류 처리의 최종 프로세스 종료 여부는 이번 9개 대조에서 확정하지 않았다.

**MemoryObject 생성.** 기본·복사 생성자 모두 객체 `+8`의 참조 수를 0에서 interlocked 증가시켜 1로 만들고, `+0x10`에 전역 증가 카운터에서 얻은 새 객체 식별자를 넣는다. 복사 생성자가 원본 객체의 참조 수를 복사한다는 해석은 잘못이다. 현재는 초기 ref 1을 사용하지만 atomic을 포함한 I/O 객체와 ObjectRef를 복사하지 않는다. 현재 연결 번호·slot generation은 별도 소유권 규약이며 원본의 모든 MemoryObject 식별자와 동일한 필드·번호 체계로 복원된 것은 아니다. 실제 사용 근거 없이 이 복사 연산이나 ID 필드를 네트워크 객체에 추가하지 않았다.

**operator delete.** 확인한 본문에는 `Never Call Operator Delete!!!` 진단과 공통 오류 처리 호출이 있다. 이는 정상 버퍼 반환 구현이 아니다. 현재는 slab 블록은 풀로 반납하고 heap 기반 CIOObject는 최종 release 시 삭제한다. 원본의 금지된 delete 진단을 현재의 정상 해제 경로에 그대로 붙이면 기존 수명 관리 계약을 깨뜨리므로 이식하지 않았다.

## 변경·검증 범위

- 수정: TAB 접두 참조 검색, pdata 없는 생성자 확인, census의 보충 근거 재검증, 함수 목록·보고서.
- 재검증: 고정 SHA의 원본에서 9개 signature 참조를 다시 연결하고 잠금 해제·반납·ref 초기화의 실제 명령 10개를 바이트와 함께 검증했다. census도 독립적으로 참조 바이트·RIP 대상·함수 경계를 다시 검사한다.
- 보존: `patches`와 `downloads`의 Git tree 및 주요 DLL/ZIP SHA를 기준 커밋과 대조한다. 결과는 `UNCHANGED_RUNTIME.json`이다. 실행 코드 변경이 없어 새 DLL 빌드나 전체 Windows 테스트를 반복하지 않았다. 기존 실행 검증은 Windows run `43412834686`, 테스트 소스 `cf696369047f30d0eff8b67d42a2d642369a7f2d`다.

원본 EXE는 실행하지 않았다. 상세 대상은 x64 `L2Server(10).exe`, SHA256 `9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588`, image base `0x400000`이다. 생성자 범위는 검증된 직접 호출 대상에서 512바이트 안의 도달 가능한 leaf CFG이며 PE unwind로 확정한 경계는 아니다. x86에 대한 새 전수 검증도 아니다.

## 남은 범위

이번 목록의 9개 미연결 항목은 해소했다. 이 범위에서 필수 정상 I/O 기능의 추가 누락은 발견하지 못했다. 원본 모든 미명명 함수·간접 호출·희귀 오류 분기가 동일하다는 검증은 남아 있다. 특히 위의 풀 오류 처리와 MemoryObject allocator/식별자 규약은 원본과 다르다.

이전부터 열린 항목인 실제 클라이언트 MAC/HWID 공급·검증 연동, CPerfMon의 전체 PDH/CSV/mail 출력은 이번에 구현하지 않았다. GQCSEx 128개 배치, monotonic64 타이머, L1 패킷·세션 경계는 현재 선택한 구조로 유지한다. 게임 로직·PSS·매니저 기능은 이번 작업 대상에 넣지 않았다.

## 재현

Python `pefile`, `capstone`을 설치한 뒤 이 폴더에서 실행한다. `PREVIOUS_CENSUS_INPUT.json`은 이전 142개 함수 경계와 미확정 9개 목록을 보존한 입력이다. 갱신된 미확정 0개 목록을 재입력으로 잘못 사용하지 않는다.

```sh
python remaining_refs.py --binary '/path/to/L2Server(10).exe'
cp REMAINING_REFS.json ../ctransport_full_network_census_20261113/FINAL_REF_RESOLUTION.json
python ../ctransport_full_network_census_20261113/census.py --binary '/path/to/L2Server(10).exe'
```

9개 연결 검증과 `VERIFIED.json`의 10개 명령 assertion이 통과해야 한다. 생성된 census는 148개 진입점과 미연결 0개를 출력한다. 각 폴더의 `SHA256SUMS`로 보관 자료의 무결성을 확인할 수 있다.
