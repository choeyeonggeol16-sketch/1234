# 바이너리에서 확인한 네트워크 이름 적용과 추가 발견

2026-11-13. 기준 배포 `a1d8d5d`의 타이머 구현을 원본 문자열·실제 코드 참조에 대조했다. 확인된 이름을 기존 실행 경로에 적용했으며 추정 이름과 확인된 이름을 구분했다. 게임/PSS/코덱·DB는 이번 변경 대상이 아니다.

## 원본에서 확인한 이름

| 원본에서 확인한 이름 | 문자열 주소 | 실제 함수 진입점 | 이번 적용 |
|---|---|---|---|
| `CMwmrQueue<class CThreadLocalTimer::CSlot,4009>::Pop(void)` | `0xd75300` | `0x925498` | 실제 공유 슬롯 풀을 `CMwmrQueue<CThreadLocalTimer::CSlot,4009>`로 구현, 대여 경로는 `Pop` |
| `CThreadLocalTimer::Dispatch(CSlot*,__int64)` | `0xd75420` | `0x9256d4` | 슬롯 실행·회수 역할을 현재 Dispatch/인덱스 처리에 대응 |
| `CThreadLocalTimer::Dispatch(void)` | `0xd75810` | `0x926844` | worker/listener가 실제 호출하는 `CThreadLocalTimer::Dispatch`로 연결 |
| `CThreadLocalTimer::AddTimer(CIOObject*,const __int64,const int,const __int64)` | `0xd758d0` | `0x926bf4` | 실제 등록 함수명을 AddTimer로 변경하고 object/deadline/message/argument 순서 적용 |
| `CUserSocketReleaseVerifier::CUserSocketReleaseVerifier(CUserSocket*,const wchar_t*)` | `0xdc5be0` | `0xa12674` | 추가 분석 대상으로 확인. L2 종료 상태 절차를 그대로 이식하지 않음 |
| `CUserSocketReleaseVerifier::TimerExpired(int)` | `0xdc5df0` | `0xa1291c` | 재예약·참조 해제 검사 본문 확인. 아래에 현재 구현과 구분 |

`CThreadLocalTimer`, 내부 타입 `CSlot`, 템플릿 `CMwmrQueue`라는 이름은 위 signature에 직접 남아 있다. 현재 기존 TimerWheel은 CThreadLocalTimer로, TimerBlock은 내부 CSlot으로, TimerBlockPool의 공유 ring은 CMwmrQueue로 옮겼다. 이전 타입의 alias나 두 번째 timer/ring 구현은 남기지 않았다. `SlotPool`은 해당 템플릿을 가리키는 L1 소스의 편의 alias다.

원본 `0x925630`의 반환 동작과 `0x92533c`의 CSlot 항목 저장 동작도 존재하지만 해당 C++ 메서드 이름은 이번 증거에 없다. 이를 원본 Push/Append 이름이 확인됐다고 표현하지 않는다. 현재 `Return`, `takeDue`, `cancel`, Position/TimerHeapRecord/TimerDispatchPolicy 등의 보조 이름도 확인된 원본 이름으로 표시하지 않는다.

이름 대조는 MSVC 클래스 ABI나 메모리 레이아웃 전체의 동일성을 뜻하지 않는다. L1 FFM의 취소 token, uint64 monotonic deadline, 실패 시 bool 반환, NativeTask 실행 callback 계약은 유지한다. 원본의 void AddTimer 반환형이나 두 Dispatch overload의 정확한 인자 목록까지 그대로 이식했다고 주장하지 않는다. 현재 Dispatch는 두 원본 계층의 역할을 L1 callback과 기존 취소 인덱스로 처리한다.

## 구현 변경

- CSlot은 object1000개/message1000개/argument1000개와 count/next 구조다. 기존 검증된 `0x1f40/0x2ee0/0x4e20/0x4e28`, 전체 `0x4e30` 배치를 유지한다.
- CMwmrQueue의 pointer ring 길이는 실제 템플릿 인수4009를 사용한다. slab의 실제 블록 수는 기존 L1의 worker 수에 따른 예산을 유지하며 부족하면 실패를 반환한다. 원본의 8개 미만 여유 검사와 의도적인 접근 위반을 복제하지 않는다.
- 이전 ring 하나를 옮겼으며 별도 보조 ring을 추가하지 않았다. 취소에 필요한 live/read/leased 상태는 기존 sidecar에 둔다. 고정된 슬롯 범위/정렬·현재 대여 상태·반환 위치를 검사한 후 반납하여 null/외부/interior/중복 반환으로 원소나 인덱스를 바꾸지 않는다. 이 검사는 L1 안전 보강이며 원본 반환 함수에서 확인한 검사라고 주장하지 않는다. 반납 후 다시 대여된 슬롯에 대한 모든 stale raw pointer 사용을 검출하는 generation 기능은 아니다.
- AddTimer는 전달된 message와 argument를 기존 블록/heap에 기록한다. argument는 기존 L1 취소 key이며 object hold를 획득한 뒤 공개한다.
- worker/listener는 Dispatch를 통해 due 항목을 처리한다. callback 전에 timer 인덱스에서 제거하고 callback 이후 기존 timer 참조를 반납한다. 실행 중 재등록·취소가 wheel 잠금을 재진입하지 않도록 한다. listener도 한 번의 monotonic snapshot을 기준으로 한 배치를 처리한다. 기존 owner17 재게시와 일반 worker의 100 inline + 1 repost 정책을 유지한다.
- 기존 네트워크 외부 ABI10/67 exports는 그대로다. Java 변경은 새 DLL SHA pin인 CIOConfig 한 파일뿐이다.

## 추가로 확인한 두 가지

**CIOThreadCallback.** RTTI 타입 descriptor와 COL/vtable을 확인했다. vtable `0xc86678`의 함수 `0x72c3a8`은 `0x72c1e0`으로 점프한다. 본문에는 실제로 `World_Sync`, `garbage collect` 추적 문자열과 월드/객체 수거 호출이 있다. 따라서 현재 L1의 thread 소유 wrapper IOThreadCallback에 이 이름만 붙이면 서로 다른 역할을 같다고 표시하게 된다. 현재 wrapper는 유지하고 주석에 구분했으며, 원본 월드 동기화·수거 구현은 가져오지 않았다. RTTI에 이름이 있다는 사실과 현재 클래스의 역할이 같다는 판단을 구분한다.

**CUserSocketReleaseVerifier.** 생성자는 CUserSocket 참조와 호출자 문자열을 보관하고 socket 상태에 따라 후속 timer를 등록한다. TimerExpired는 메시지1/3/4에 따른 별도 처리와 재예약을 거친 뒤, 대상 socket의 참조 해제 결과가 남으면 `ORT - UserSocket Release failed`와 account/refcount/caller 진단을 남기는 경로가 있다. 자기 verifier 참조도 해제한다.

이 클래스에는 L2 User·socket 상태·메시지 처리와 고정 지연 절차가 섞여 있다. 현재 L1의 native active/retiring registry, I/O terminal, packet lease, Java actor cleanup과 역할을 대조해야 하며, 이 클래스가 없다는 이유만으로 같은 기능이 전부 빠졌다고 판단하지 않는다. 이번에는 이름과 실제 본문까지 확인했고 추가 verifier를 중복 실행하거나 기존 종료 대기 시간을 바꾸지 않았다. 다음 비교 대상은 이 절차와 현재 종료/잔여 참조 진단의 차이다.

## 근거와 재현 범위

입력은 제공된 x64 `L2Server(10).exe`, SHA256 `9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588`이다. 원본을 실행하지 않았다. `audit_names.py`가 SHA를 검증하고 UTF16 signature의 실제 RIP 참조를 pdata 명령 경계에 연결한다. RTTI는 type descriptor→COL→vtable code pointer로 검증하며, 이 바이너리의 signature0 COL도 처리한다. 이름이 없는 함수에는 원본 이름을 추측해 채우지 않는다.

`research/verified-network-names/VERIFIED_NAMES.json`에 6개 signature, 실제 참조 주소·바이트·함수 범위와 CIOThreadCallback의 역할 근거를 보존했다. 이전148개 census는 선택한 owner 이름의 목록으로, 원본 전체 함수 수가 아니다. 이번 timer/verifier 추출은 그 검색 범위를 확장한 것이며 새 복원율을 계산하지 않는다. 새 x86 전수 대조나 모든 미명명 함수 분석 완료도 아니다.

## 검증·적용

기존 전체 native ASan/UBSan 회귀를 통과했다. 새 ASan/UBSan 및 TSan 검사는 CMwmrQueue4009의 FIFO 10,000회 왕복과 ring wrap, null/외부/내부주소/중복 반납 거절, 재진입 Dispatch, 4,000건 동시 AddTimer/cancel/Dispatch의 참조 해제와 최종 대여0을 확인했다. 재현 명령은 다음과 같다.

```sh
python tests/run_verified_timer_tests.py
python tests/run_verified_timer_tests.py --tsan
python tests/verify_verified_names_scope.py
python research/verified-network-names/audit_names.py --binary '/path/to/L2Server(10).exe'
```

실제 Windows run **43491457850**, job **132113419130**, 테스트 소스 **8812c2e6019ad8380d5cef52801b3f6d40979d75**에서 통과했다. 함께 배포하는 `WINDOWS_VERIFICATION.json`에 기록한다. 실제 DLL loopback과 Java FFM/종료/타이머/암호화/독립 PSS 회귀를 사용하며, 운영 게임 클라이언트·장기 실부하는 별도 범위다.

기준 배포 전체 source1712개 중 Java DLL pin 한 파일만 바뀌고 나머지1711개 파일은 동일하다. native는 timer_wheel.hpp, CTransport.cpp, 역할 구분 주석을 추가한 io_server.hpp 세 파일만 변경한다. 원본과 같은 이름으로 정리했더라도 새 DLL과 Java SHA pin은 함께 적용해야 한다. 게임·PSS·코덱·SQL·GUI는 수정하지 않았다. 원본 바이너리는 배포 파일에 포함하지 않는다.
