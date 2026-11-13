# 원본 네트워크 식별 함수 목록과 현재 대응 검토

2026-11-13. 현재 수정 소스와 원본 x64의 네트워크 signature·RTTI/vtable·기존 확인된 구조 진입점을 대조했다. **142개 함수 진입점**을 주소별로 묶어 실제 명령·호출·함수 경계와 대응 파일을 보존했다. 이것은 원본 EXE의 미명명 함수까지 모두 의미를 복원하거나 모든 예외 분기의 동등성을 증명한 전수 검수 완료 선언은 아니다.

## 결과

| 분류 | 진입점 수 | 의미 |
|---|---:|---|
| CORE_ROLE_MAPPED | 62 | IOCP/listener/풀/타이머/serializer/제한기 등 현재 대응 계층을 확인 |
| NETWORK_ADAPTER_MAPPED | 41 | 원본 소켓·유저 생명주기를 native와 Java L1 세션 경계에 대응 |
| L1_POLICY_ADAPTATION | 5 | 원본 L2의 상태/시간/허용 정책을 숫자·필드 그대로 이식하지 않음 |
| L2_GAME_PROFILE | 29 | 아이템·대화·시스템 메시지 등의 L2 게임 프로토콜. L1 handler로 유지 |
| PARTIAL_OPERATIONS | 1 | CPerfMon 전체 운영 출력은 일부만 대응 |
| ORIGINAL_STATS_ONLY | 1 | 원본 CheckMaxPacketAccept는 통계 후 true 반환. 차단기로 오해하지 않음 |
| ORIGINAL_INSTRUMENTATION_ONLY | 1 | OnSending의 확인된 실체는 TLS 추적 metadata 갱신 |
| L2_CLIENT_SERVICE | 1 | GameGuard 연계 ping. L1 자체 ping 프로필과 구분 |
| CLIENT_INTEGRATION_OPEN | 1 | MAC/HWID 서버 API는 있으나 실제 클라이언트 공급·검증 미연동 |

위 수치는 **목록의 분류별 개수**이며 복원율이나 검수 통과율이 아니다. 동일 함수의 여러 signature/vtable alias는 주소별로 묶었다. `FUNCTION_CENSUS.csv/json`에142개 각각의 주소·분류·대응 파일·원본 명령 파일이 있다.

원본의 GQCS 단일 처리와 선택한 GQCSEx128, 32비트 timestamp와 L1 monotonic64, L2 패킷 규약과 L1 codec/session 경계는 의도적인 차이다. 512/504 serializer와 pool 소유권 역할은 대응하지만 overflow rollback·stale generation·부분 송신 재게시·graceful 종료 안전성은 L1에서 보완한 부분이다. 원본 모든 구현 세부가 동일하다고 표현하지 않는다.

## 새로 본문을 확인한 사례

`CUserSocket::OnSending`은 **0x9f8080..0x9f80dc**의 pdata 없는 leaf다. 알고 있는 함수 진입점과 실제 직접 호출의 callee에서 도달 가능한 명령을 대조해 연결했다. 실제 body는 TLS signature stack의 depth/문자열 갱신 후 return하며 WSASend·암호화·송신 큐 동작은 없다. 따라서 이 문자열이 보인다고 추가 송신 엔진을 복원해야 한다는 주장은 근거가 없다. `fn_9f8080.asm.txt`가 근거다.

원본 `AtomicAddTimer::Disassemble`, `CIOServer::Create`, 소켓 getter/소멸자 등의 signature도 별도 함수 경계/기존 진입점과 다시 연결했다. 인라인된 문자열은 독립 함수 개수로 억지로 세지 않는다.

## 아직 확정하지 않은 범위

다음 **9개 signature 문자열**은 이번 검색에서 독립적인 활성 함수 본문으로 확정 연결하지 않았다. 함수9개가 빠졌거나 미구현이라는 뜻이 아니다. 인라인·공유 allocator 호출·코드가 제거된 문자열 가능성을 확인해야 한다.

- MemoryObject의 기본/복사 생성자, operator delete (3개)
- CNewMwsrQueue의8K/16K UnLock·Push (4개)
- CIOBufferEx의8K/16K Alloc (2개)

현재 native 풀의 slab/ring·retain/final-return 역할과 실제 회수 검사는 이미 있으며, 위 문자열을 이유로 풀을 다시 만들지 않았다. 이9개와 미명명 함수·동적 간접 호출 전체·모든 희귀 오류 분기까지 검수 완료했다고 주장하지 않는다.

native worker의 실제 connection/operation 식별 정보 확대와 출력 예약량 세분화는 알려진 선택 진단 범위다. 이번 풀 진단은 실제 pool 사용/반납과 보유 지표를 추가했고 강제 회수는 넣지 않았다. L2 게임/과금/서비스 제어·PDH CSV/mail 전체를 네트워크 필수 복원으로 끼워 넣지 않는다.

## 근거와 재현

입력 x64 SHA256 **9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588**, image base0x400000. 원본을 실행하지 않았다. `census.py`는 SHA를 확인하고 실제 RIP signature 참조·PE unwind 경계·검증된 RTTI/vtable 함수 pointer·기존 구조 진입점의 body를 덤프한다. pdata 없는 leaf는512바이트 내 도달 가능한 CFG라는 범위를 명시한다. 단순 문자열 검색 결과만으로 복원 완료 판정을 하지 않는다.

`server-rtti.json`은 두 제공 바이너리의 기존 구조 추출 자료다. 이번 상세 body/census의 대상은 x64이며 x86 전체 검수를 새로 끝냈다는 뜻은 아니다. 현재 대응 파일의 SHA는 `source-index.json`에 고정했다. 분류는 현재 계층과의 역할 대응이며 모든 기계 명령·오류 분기의 동등성 보증이 아니다.

```sh
python census.py --binary '/path/to/L2Server(10).exe'
```

Python pefile/capstone을 사용한다. 파일별 SHA256SUMS와 원본 입력 SHA를 확인할 수 있다. 안전한 적용·검증 결과는 별도 풀/매니저 구현 보고서와 Windows 실행 기록을 참조한다.
