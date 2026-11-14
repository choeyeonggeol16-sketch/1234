# 원본 네트워크 최종 차이 확인 — 2026-11-14

대조 기준은 배포 commit `5bd039b43e09f2623f855024375dd53e09e10949`의 소스와 DLL이다. 제공된 x64 원본을 다시 읽어 **24개 함수 본문, 실행 코드 영역으로 경계를 판정한 가상함수 테이블 3개, 구체적 명령 24개**를 대조했다. 원본 EXE는 실행하지 않았다. 이번에는 분석·재현·보고서만 추가했으며 production 소스·DLL·배포 ZIP·PSS는 변경하지 않았다.

**새로 확정한 차이는 가상함수 2개 누락, 송신 검사 슬롯의 인자·동작 차이, 원본 필드 일부의 미반영이다.** 아래 결과는 전체 네트워크 함수와 모든 분기의 전수 일치 판정이나 복원율이 아니다.

## 1. CUserSocket 가상함수: 원본 26개, 배포 DLL 24개

원본 테이블 `0xdc1c18`을 기존의 상수 24개에서 멈추지 않고 첫 비실행 포인터까지 읽었다.

| 원본 슬롯 | 함수 주소 | 확인된 본문 | 현재 구현 |
|---|---|---|---|
|24 (`+0xc0`)|`0xa01790`|`mov rax,[rcx+0x4f0]; ret`|슬롯 없음. 해당 필드는 L2 프로필 예약 영역. 반환 대상의 전체 의미와 원본 함수 이름은 확정하지 않음|
|25 (`+0xc8`)|`0xa01798`|`mov eax,[rcx+0x4e8]; ret`|소켓 번호는 이미 있고 `SocketNumber()`로 조회하지만 비가상 함수라 이 슬롯은 없음|

슬롯 26 위치의 값 `0x119930522`는 실행 코드가 아니다. 원본 CSocket은 24개, Server는 16개로 기존 결과와 같다. 배포 DLL의 실제 MSVC RTTI에서 CUserSocket 테이블을 별도로 찾아 **24개**임을 확인했다. 원본 user timer에서도 `0xa0d253`의 `[rax+0xc0]` 호출을 확인할 수 있다.

이전 `REPORT_OBJECT_MEMORY_ABI.md`의 “원본 CUserSocket 24슬롯과 일치”는 잘못된 판정이었다. `audit_object_memory.py`가 원본에서 24개만 읽고 `verify_msvc_rtti.py`가 그 길이를 정답으로 재사용했기 때문이다. 이번에는 원본과 DLL의 테이블 끝을 각각 판정했다. 슬롯 25는 기존 번호 조회의 가상 계약을 맞추는 범위이고, 슬롯 24는 L1 세션과의 연결 계약을 정한 뒤 반영해야 한다. L2 객체 주소를 Java 객체 주소로 간주해서는 안 된다.

## 2. 송신 검사 슬롯 14·15: 원본 조건 확인, 현재 계약 차이

원본 이름은 확정하지 않았으므로 아래 이름은 분석용 역할명이다.

| 항목 | 원본 | 현재 |
|---|---|---|
|14 / `0x9ef544`|유효 SOCKET 검사, `[this+0x20] <= 0x200000` 검사. 실패 시 원본 상태·진단 처리|`Slot14SendAllowed()`는 소켓 유효성과 closing만 검사|
|15 / `0x9ef620`|추가 `int margin`을 받아 `[this+0x20]+margin <= 0x200000` 검사|`Slot15SendAllowed()`에 margin 인자가 없고 예약량만 검사|
|일반 송신의 호출|`0x90e26f`에서 슬롯 14 호출. 실패 경로 `0x90e2ab`에서 기존 Close 호출|현재 `send()`/`ExecuteSend()`는 별도 admission을 사용하며 두 슬롯을 호출하지 않음|
|선택 송신의 호출|`SafeSend`의 `0x9ef493`에서 슬롯 15 호출|Java `beginSafeDirectFrame`에서 별도 margin 검사|

`ReproduceSendingCheckGap.cpp`는 현재 선언된 함수를 정상적인 타입으로 호출하고, 원본 명령에서 확인한 조건과 비교한다. 잘못된 함수 포인터 캐스팅으로 원본 호출을 흉내 내지 않았다. ASan/UBSan 재현 결과:

```text
slot15: field+0x20=1572865, margin=524288 -> original condition=false, current method=true
slot14: field+0x20=2097153 -> original condition=false, current method=true
production send(): reservations=2MiB에서 추가 1바이트 거절
fixture 정리 후 live 객체·바이트 0
```

**현재 송신 상한 보호와 Java SafeSend는 이미 구현돼 있다.** native `send()`는 FIFO 잔여량+예약량+새 요청을 같은 lock 아래 제한하고, Java `canAdmitBytes()`도 outstanding+reserved를 계산한다. `SC_Emblem`은 512KiB margin으로 `beginSafeDirectFrame()`을 사용한다. 따라서 기존 SafeSend 전체를 미구현이라고 다시 세면 안 된다. 확인한 누락은 원본 슬롯 15의 인자 계약과 슬롯 14·15의 동작·호출 연결이다. 원본의 signed 32비트 덧셈 overflow까지 따라 할 필요는 없다.

기존 ObjectAbiTest는 invalid SOCKET에서 두 슬롯이 false인지 확인했지만, margin 인자와 용량 경계의 차이를 검사하지 못했다. 그 테스트 통과는 이 계약의 원본 동일성을 증명하지 않는다.

## 3. 원본 필드 배치: 크기·일부 위치는 같지만 전체 상태가 같지는 않음

앞서 미확인으로 설명했던 Server 영역 일부의 역할을 이번 재대조로 확인했다.

| 원본 위치 | 근거 | 현재 |
|---|---|---|
|Server `+0x40`|ctor의 트리 sentinel 초기화와 Add/Remove의 컨테이너 접근|별도 `socketMap`이 담당. 원본 위치는 예약 바이트|
|Server `+0x60`|`InitializeCriticalSectionAndSpinCount(...,4000)`, Add/Remove의 Enter/Leave, dtor의 Delete|별도 `socketMapGate`가 담당. 원본 위치에 CRITICAL_SECTION을 구성하지 않음|
|Server `+0x58`|dtor에서 이 값을 CloseHandle에 전달|핸들의 전체 생성·사용 의미는 아직 미확정|
|Server `+0x38`|Run에서 listening SOCKET을 복사|원본 위치는 예약 바이트|
|Server `+0x88`|Run의 bool 인자 저장, CreateSocket의 접속 IP 분기|원본 위치와 동일한 모드 없음. 다음 항목 참조|
|socket `+0xa8`|Write의 `0x90e2e1`에서 큐 후미 검사, `0x90e2f1`에서 첫 후미 저장|`originalA8_=0` 예약값. 실제 FIFO 후미는 외부 SendChain에 존재|
|socket `+0x20`|원본은 pending write 누적값|현재는 예약 중인 송신 바이트. 실제 잔여량은 외부 FIFO와 합산|

연결 map·잠금·송신 FIFO 기능 자체는 현재도 있다. 다만 크기와 알려진 포인터 위치를 맞췄다는 사실을, 원본 객체의 모든 필드가 같은 상태를 유지한다는 뜻으로 확대하면 안 된다. 역할을 연결해 원본 형태를 더 맞출 수 있으며 중복 map이나 두 번째 송신 큐를 추가할 필요는 없다. `+0x28~0x8f` 전체를 여전히 미확인이라고 설명하는 것도 이제 부정확하다. 일부는 위처럼 확인됐고 나머지 의미는 미확정이다.

## 4. 원본 접속 IP 제한 모드: 확인된 정책 차이

`Server::Run`은 bool 인자를 `+0x88`에 저장한다. `CreateSocket`은 이 값이 참일 때 peer IPv4의 첫 두 바이트가 `10,10`인 경우만 생성 경로로 진행하고, 그 밖의 주소는 null을 반환한다. 일반 IP별 접속 수 제한과는 다른 기능이다.

현재 factory에는 이 원본 모드가 없다. 이를 도입한다면 운영 설정으로 제한 대역을 명시하는 정책이 적합하며, 공인 접속을 받는 L1 서버에 `10.10`을 기본 강제하면 안 된다. 원본 모드의 정확한 이름·운영 목적 전체를 복원한 것은 아니다. 기존 IP/MAC 개수 제한은 이미 있으며 새로 만들 항목이 아니다.

## 5. 기존에 명시한 적응·제외 범위

- 원본 user timer와 release verifier는 L2 유저 필드·상태 및 클라이언트 서비스에 연결된다. 현재의 L1 deadline/정리 대기/5초 검사+조건부 25초 재검사는 이미 동작하며 원본 모든 메시지 분기를 이식한 것은 아니다. 새로 발견한 타이머 엔진 누락으로 세지 않는다.
- 원본 PDH/CSV/mail 전체, 실제 클라이언트 MAC/HWID 공급·검증은 기존 제외 범위다.
- IOCP 최대128 배치, 16바이트 할당 prefix, 세대 핸들, 명시적 취소, 손상 블록 격리·실패 반환은 의도적인 차이다.
- 종료 이후 늦은 완료 통지 등 모든 오류·경쟁 분기의 원본 동등성은 여전히 미입증이다. 이번 선택 함수 대조로 새로운 별도 입출력 엔진 누락을 확인하지는 못했지만, 원본 전체에 없다고 증명한 것은 아니다.
- 현재 배포 DLL의 실제 게임·DB 장기 부하와 원본 대비 성능 우열은 이번 검사 대상이 아니다. 원본 x86 전체 재검수도 하지 않았다.

우선 맞출 범위는 **슬롯 24·25, 송신 검사 슬롯 14·15 계약과 연결, 확인된 원본 필드의 반영**이다. 접속 IP 모드는 별도 운영 정책으로 판단할 부분이다. 분석 결과로 production 코드가 수정됐다고 표시하지 않는다.

## 근거·재현

- 원본 x64 SHA256: `9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588`
- 배포 DLL SHA256: `9b8992db78c851d76522e30406a8049b84e08b5a53af395689d5847154dad6a1`
- `research/final-gap-audit-20261114/EVIDENCE.json`: 원본/DLL의 독립 테이블 경계, 명령, source hash.
- 같은 디렉터리의 함수별 asm, `audit.log`, `reproduction.log` 및 재현 코드.
- 원본은 정적으로만 분석. 송신 검사 재현은 현재 native 코드의 Linux fixture에서 ASan/UBSan으로 실행했으며 Windows 커널이나 실제 클라이언트 검사를 새로 수행한 것은 아니다.

full source 루트에서 아래처럼 재현한다. GitHub reports 폴더에 있는 증거 디렉터리를 full source의 `research/final-gap-audit-20261114`로 복사하면 같은 명령을 사용할 수 있다.

```sh
python research/final-gap-audit-20261114/audit.py --binary '/path/to/L2Server(10).exe' --source . --out research/final-gap-audit-20261114
g++ -std=c++17 -O0 -g0 -pthread -Inative -Itests -fsanitize=address,undefined -fno-omit-frame-pointer research/final-gap-audit-20261114/ReproduceSendingCheckGap.cpp -o build/final-gap-repro
ASAN_OPTIONS=detect_leaks=1:halt_on_error=1 UBSAN_OPTIONS=halt_on_error=1 build/final-gap-repro
```

첫 명령에는 pefile/capstone이 필요하다. 감사 시점에 존재한 차이를 확인하는 재현이므로 해당 production 계약을 수정한 뒤에는 결과와 테스트를 함께 갱신해야 한다.
