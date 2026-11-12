# 원본 캐릭터 직렬 실행기 ↔ 현재 L1 CCreatureController 대조

x64 `L2Server(10).exe`를 직접 읽어 확인했다. SHA256 `9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588`, ImageBase `0x400000`. PE unwind의 함수 범위와 실제 명령 경계를 사용했다. EXE를 실행하거나 생산 Java/C++/DLL/PSS를 수정하지 않았다. 이 보고서는 x64 확인 결과이며 x86까지 같은 주소/배치라고 주장하지 않는다.

**32칸 실행기·24개 진입 검사·같은 owner의 즉시 재진입·단일 drain 및 작업별 호출은 원본에서도 확인된다. Java의 포화 처리·세션 방어까지 모두 원본과 동일한 것은 아니다.**

## 원본에서 직접 확인한 사실

| 항목 | 명령·호출 근거 | 현재 L1 대응 |
|---|---|---|
| 클래스와 32칸 | `0x466db7`이 참조하는 UTF16 signature에 `CExecutionSerializer<class CCreatureController,32>::AddExecutable` 명시. producer `0x466fc6` 및 consumer `0x4671cc`의 `and ebx,0x1f`는 index를0..31로 순환시킨다. | `EXECUTION_SLOTS=32`, mask31 |
| 컨트롤러별 고정 저장소 | 실제 RTTI vtable `0xbe0398`을 설치하는 ctor `0x5a844c`. `0x5a84a9/0x5a84b0`은 컨트롤러+`0x10d8`의 실행기 초기화 `0x5a6e00` 호출. 이 초기화는 인자 영역 `0xa00=32×80` bytes, 함수 주소 영역 `0x100=32×8` bytes를 초기화한다. | PC 생성 시 controller1개, 선할당 long/Object/ready 배열 재사용. Java 배열 배치가 원본 메모리 배치와 동일하다는 뜻은 아님 |
| 24개 진입 검사 | `0x466ef7`에서 pending을 읽고 `0x466efa: cmp eax,0x18`; `jl`이면 예약 진행. 조건을 넘으면 Overflow.1 로그 후 반환한다. 예약 후 `0x466ff3: cmp eax,0x20`의 추가 검사가 있다. | 기본 예약 상한24. Java는 tail/head와 CAS로 예약하므로 경쟁 시 경계까지 원본과 동일한 알고리즘은 아님 |
| 같은 owner 즉시 실행 | `0x466dc6..0x466dcd`가 owner와 TLS thread number를 비교. 같으면 인자 복사 후 `0x466edb: call rbp`로 바로 실행한다. | `Thread.currentThread()==executionThread`이면 `executeDirect` |
| 순번·게시·한 주체의 drain | `0x466fbd` 순번 lock xadd → ring 인자 복사 → 함수 주소 게시 → `0x466fe9` pending lock xadd. count1인 생산자가 drain을 시작하고 나머지는 반환. consumer가 다음 함수를 기다린 뒤 `0x46721a` 호출, 함수 주소 clear, owner reset, pending 감소 후 다음 항목 처리. | CAS ticket/ready release·acquire, executionWork의 첫 주체가 drain. 대기/게시 구현은 Java용 |
| 이동 | `0x572383`의 `Move` label과 `0x5723ae -> 0x466d44`. 인자와 callback 주소를 함께 전달한다. | `EXEC_ACTION`을 통해 Move 등 실행 |
| 공격·스킬 | `0x573390`의 `Attack` label → `0x5733ab`; `0x4da33a`의 `Skill` label → `0x4da355`; 둘 다 `0x466d44` 호출. | controller action 직렬화 |
| 타이머 | `CCreatureController::TimerExpired` `0x5ad544`에서 `AsyncTimerExpired`를 만들고 `0x5ad62f -> 0x466d44`. | `EXEC_TIMER`와 controller OnTick |
| 피해 메시지 | `CSerializedDamageMessage::OnHandle` `0x8af584`에서 `OnDamagedMessage` callback/인자를 구성하고 `0x8af63a -> 0x466d44`. | `OnDamaged -> executeOrPublish -> OnDamagedMessage`에 대응하는 역할 |

## 정확히 구분할 차이

1. Java의 `DamageOverflow`는 ring이 찼을 때 별도 ConcurrentLinkedQueue에 피해를 보관한다. 확인한 원본 범용 AddExecutable의24개 진입 거절 분기는 **로그 후 반환**이다. 원본의 `CSerializedDamageMessage` 존재와 피해가 직렬 실행기로 전달되는 것은 확인했지만, 그것이 Java의 **ring 포화 시 우회 큐**와 같다는 증거는 아니다. 원본 전체의 다른 메시지 계층에도 같은 정책이 전혀 없다는 주장도 하지 않는다.
2. Java의 `sessionStamp/actorEpoch` 비교, 시야 refresh 합치기, 일부 필수 작업의 park/retry는 현재 L1용 정책이다. 원본 실행기의 확인된 해당 분기에 이 Java 정책과 동일한 구현이 있다고 판정하지 않는다.
3. 원본의24개 검사는 pending을 미리 읽는 진입 검사이며 뒤에32개 추가 검사도 있다. Java의 엄격한 CAS 예약 상한과 경쟁 중 모든 결과가 같다는 증명이 아니다.
4. 원본은 같은 owner 재진입 시 깊이 카운터를 올리고4 초과 진단 후 실행을 계속한다. Java의 `MAX_PENDING_RECURSION=8`은 별도 pending action 전환에 사용되는 값이므로 동일 카운터라고 비교하지 않는다.
5. 소켓의512칸 `CExecutionSerializerFast`와 캐릭터의32칸 template 실행기는 별개다. 소켓 생성자 `0xa0ccdf`의 `r8d=0x200`이 CSocket/CIOSocketEx 생성 경로를 거쳐 fast serializer ctor `0x45c708`의 capacity로 전달된다. 이 ctor는 인자/함수 포인터 배열을 각각 capacity×8 bytes 확보한다. 캐릭터 실행기를512칸이라고 설명하면 잘못이다.

`findings-raw.json`에는 확인된 직접 호출 지점과 RTTI/생성자 근거, `verified-instructions.json`에는 실제 명령 bytes와 source SHA256을 저장했다. 생성된 asm은 함수 범위로 추출했으며 일부 leaf helper는 파일 머리말에 한정 범위를 표시했다. 최적화/간접 호출/전체 게임 경로의 완전한 등가성 증명은 아니다.

재현: Python에 pefile/capstone을 설치한 뒤 `verify_binary.py --binary <x64 EXE> --source-root <현재 소스 루트> --out <출력 폴더>` 실행. 기존 `research/audit_network_abi5.py`의 읽기 전용 PE 분석 helper를 사용한다.
