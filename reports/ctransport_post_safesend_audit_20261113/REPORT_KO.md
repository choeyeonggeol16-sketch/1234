# SafeSend 적용 이후 추가 대조: 확장 패킷 식별 정보

2026-11-13. 비교 기준은 `ctransport-core-20261112`의 **76c06b5e7002c30b96c47dae04453d0c08a480f1**이다. 이번 작업은 정적 분석과 현재 진단 API의 누락 재현이다. **서버 소스·DLL·설정·배포 ZIP은 수정하지 않았다.**

추가 복원 후보로 확실히 확인한 것은 **최근 패킷 기록에 확장 명령 번호까지 보존하는 동작**이다. 이번 대조에서 큰 송수신 엔진의 새로운 누락을 확인하지는 못했다. 이미 구현한 IOCP128/풀/serializer/부분 송신/공유 방송/타이머/종료/SafeSend/종료 사유를 다시 미구현으로 세지 않는다.

## 원본의 실제 연결

원본 x64 `UserExPacket`은 **0xa035e4**다. 직접 CALL xref가 0이라는 이유로 미사용 함수라고 판단하면 안 된다. 초기화 함수 **0xb15c74**가 **0xb15c78**에서 이 함수 주소를 만들고, **0xb15c86**에서 opcode **0xd0**을 전달해 **0x9f8eac**의 `Bind(int id, Callback func)`를 호출한다. Bind는 범위를 검사하고 **0x9f8f0f**에서 실제 테이블 슬롯에 함수 포인터를 저장한다. 이 등록 경로를 별도 asm으로 보존했다.

일반 `OnRead 0xa0372c`는 최근128개 슬롯에 `기본 opcode << 16`을 기록한다. `UserExPacket`은 하위 명령을 파싱한 뒤 아래와 같이 **같은 최근 기록의 하위 부분을 갱신**한다.

```text
0xa0368c  sequence 읽기
0xa03692  sequence - 1
0xa03695  & 0x7f
0xa03698  하위 명령 번호 준비
0xa0369b  [socket + index*4 + 0x804] += 하위 명령
```

`ReportPacketLog 0x9fcc98`은 **0x9fce30**에서 하위16비트, **0x9fce33**에서 상위16비트를 분리하고, **0x9fce80**의 `C[%3d] C_EX[%5d]` 형식으로 출력한다. 기본/확장 ID를 함께 보관·조회하는 동작이 확인된다. 원본 자체가 확장 ID별 누적 시간·횟수 집계까지 제공한다고 이번 자료로 주장하지 않는다.

## 현재 L1의 차이와 재현

`CUserSocket.executeNativePacket()`은 `CNetworkDiagnostics.begin(trace, plaintext.get(0)&255, length)`만 호출한다. `CNetworkDiagnostics.begin()`은 opcode를 다시 `&255`로 제한하고, 최근 기록·카운트·시간·실행 중 작업에도 그 기본 opcode를 사용한다. 따라서 새로 추가한 종료 사유는 보여도, 같은 opcode 아래의 어떤 명령이 먼저 실행됐는지는 최근 기록으로 구분하지 못한다.

현재 L1의 `CPacketHandler.decodeExtendedMultiplexed()`는 opcode **0x51** 아래에서 실제로 다른 16비트 명령 **0x03e9(1001, 핑 응답)**와 **0x03ea(1002, 환경 설정)**를 구분한다. 원본 L2의 0xd0이나 확장 ID 제한0x97을 L1에 적용해서는 안 된다.

`TraceIdentityGap.java`는 production `CIOConfig/CNetworkDiagnostics`를 그대로 컴파일하고, 위 두 헤더에서 production 진입점과 같은 인자를 전달했다. 게임 handler나 실제 클라이언트 교환 전체를 실행한 검사는 아니다. 결과는 다음과 같다.

```text
Input:opcode0x51/command0x03e9 and opcode0x51/command0x03ea
#2 opcode=81 ...
#1 opcode=81 ...
opcode=81 count=2
```

두 명령이 하나의 opcode로 합쳐지는 것을 확인했다. `reproduction.log`에 실제 결과가 있다. 이 재현은 현행 진단의 한계를 확인한 것이며, 새 기능을 구현해 통과한 테스트가 아니다.

## 가져온다면 필요한 범위

기존 최근128개 기록과 종료 사유에 **프로토콜 종류 + 기본 opcode + 하위 명령**을 함께 표시하는 것이 우선이다. 종료 직전 어떤 확장 요청이 있었는지, 오래 걸리는 공통 확장 handler가 어떤 요청을 실행하는지 구분할 수 있다. 송수신 속도를 높이는 기능으로 설명하지 않는다.

L1 0x51은 단순한 16비트 명령 테이블만이 아니다. 기존 코드가 `CSProto.isRoutable(msgId)` → 1바이트 craftType → ActionUI 순서로 분기한다. 무조건 앞2바이트를 C_EX로 해석하면 craft 데이터가 명령 번호로 잘못 기록될 수 있다. **실제 decoder의 분류 결과를 재사용**하고 기본 opcode0x89의 별도 확장 경로도 확인해야 한다. 패킷 read cursor·복호화 키·handler 허용 여부는 변경할 필요가 없다.

이벤트마다 두 기록을 추가해128개가 사실상64개가 되거나, 동시/nested 작업에서 다른 슬롯을 수정하지 않아야 한다. 기존 횟수·시간 집계의 의미는 유지하고, 필요하다면 확장별 상세 집계는 명시적 상한을 둔다. 짧은/잘못된 입력은 ID를 추측하지 않는다. 패킷 본문·계정 정보 저장은 필요 없다.

## 함께 확인했지만 새 복원 후보로 세지 않은 것

- **CheckLoginTime 0x9fc9b4**: elapsed helper **0x9f8210**, 60단위 계산, 시간 안내 메시지와 CSV 보고 분기를 확인했다. 확인한 함수 본문 끝은 항상 true다. 일반적인 미인증 로그인 타임아웃 함수로 단정하지 않았다. 모든 하위 서비스 호출의 과금/운영 의미를 복원했다는 뜻은 아니다. 현재 native 로그인 제한과 Java deadline은 이미 있다.
- **User::PacketAcceptable 0x9ba4f8 / PacketExAcceptable 0x9ba6b0**: 실제 일반/확장별 허용 테이블 검사는 있다. 하지만 사용 중인 L2 필드·전역 비교값의 게임 의미를 이번에 모두 확인하지 않았다. 현재 L1에는 상태/세션 허용 검사, NetworkSafeLoadManager, 개별 handler 검사가 있으므로 L2 숫자와 테이블을 그대로 추가할 근거가 부족하다.
- **OnSending**: signature 문자열만으로 활성 함수나 누락 기능을 주장하지 않는다. 이번 참조 검색에서도 확정한 활성 본문이 없다.
- **native 출력 큐 예약량 표시 / native worker 작업 식별 정보 확대**: 과거 보고서의 알려진 남은 관측 범위다. 이번에 새로 발견한 복원 항목으로 재집계하지 않았다.

## 재현·범위

원본 x64 SHA256은 **9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588**, image base0x400000이다. 제공된 원본을 실행하지 않았다. `trace_additional.py`가 SHA, 실제 RIP 참조/함수 경계, 등록·기록·조회 명령13개를 검증한다. leaf 함수는 pdata 경계가 아닌 entry 기준 도달 가능한 범위로 표시한다. x86 재대조·전체 간접 호출망·실게임 부하 검증을 수행한 결과가 아니며 복원율을 산출하지 않았다.

```sh
python trace_additional.py --binary '/path/to/L2Server(10).exe'
python reproduce_identity_gap.py --source-root '/path/to/current/full/source'
```

첫 명령은 pefile/capstone을 사용한다. 둘째 명령은 Java21 및 production source에서 `CIOConfig/CNetworkDiagnostics`를 사용한다. 비교 소스의 SHA는 `source-index.json`에 고정했다. PSS와 기존 배포본은 그대로 유지했다.
