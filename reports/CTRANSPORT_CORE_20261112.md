# CTransport ABI3: 종료·작업 분배·풀·타이머 후속 복원

구현 기준749a305e3b662852733fee766825e645870c33fc(ABI2). 사용자 요청의4개 후속 범위를 바이너리에서 더 추적하고 현재 팩에 구현했습니다. 새 DLL은 CTransport.dll, ABI0x30000/37개 export이며 유일한 전송 엔진입니다.

## 실제 확인한 원본과 이번 변경

| 대상 | 원본 근거 | 현재 구현 |
|---|---|---|
| 종료 시작 |0x45d534 socket retire→vslot+0x98 hook→linger(1,0)→closesocket. CUserSocket hook0xa12dec는 parser 상태2, 등록값 xchg0, 등록 해제와 후속 객체 생성 | 종료 시작 callback을 socket gate 밖에서1회 실행. Java 활성 조회에서 먼저 제거하고 closing registry에 보관. 커널 I/O/콜백 종료 뒤 terminal callback으로 최종 정리 |
| 후속 정리 |0xa12674는 socket 참조를 잡고 게임 상태에 따른 후속 작업/예약을 구성 | L2 필드/게임 상태를 복제하지 않고 현재 CIOUserSocket owner/세션 정리 경로에 연결. 최종 통지 전 버퍼·codec/arena 해제 금지 |
| 버퍼 생성 |0x6b5094는N*0x2038 연속 영역+N*8 pointer ring을 만들고0초기화.0x6b4fb8은0x4038.0x7045dc는 worker별8K300개/설정된16K총량 분배.0x730300의 owner17은8K300/16K200 | 관찰된 payload/length/next/ref/owner offset과 stride를 가진 slab/포인터 ring. native worker는 시작 시 고정 pool 생성, 외부 FFM 생산자는 등록 시 고정 pool 생성. acquire 중 개별 new/동적 증가 제거 |
| 고갈/손상 |0x45d7f8 할당 검사,0x45d67c 반환 검사.0x66db28은 실제로 null 주소에 byte1을 써서 접근 위반 | 고갈·초기화 실패·중복 반납/손상 진단. 부족한 시작 설정 거절. 송신/수신 자원 실패는10055로 해당 연결 종료. 프로세스 강제 접근 위반은 이식하지 않음 |
| 소유권·분배 |0x926bf4 등록은 TLS owner의 wheel 선택.0x925374는 owner17 전체 재게시, 일반 owner는 counter 이전값100일 때 재게시/초기화, 나머지 직접 실행 | timer owner를 토큰 modulo에서 등록 worker/외부 생산자 TLS로 변경. 원본100 inline+1 repost 분기. 일반 PostTask는 IOCP 객체 게시 유지 |
| 타이머 block |0x92533c: 객체1000개(+0),message1000개(+0x1f40),argument1000개(+0x2ee0),count+0x4e20,next+0x4e28.0x926bf4/0x926844는 가득 차면 block을 선두에 연결.0x9256d4는 chain 순서 처리 후 초과 block 반납 | sizeof0x4e30/static_assert로 배치 확인. 선두 확장/배열 순서/블록 회수. 취소를 지원하기 위한 인덱스와 빈 슬롯 압축 추가 |
| timer block 풀/장기 heap |0x925498/0x925630의 CAS 공유 ring, 나머지 연산4009.0x92611c의32-byte heap record | 공유 사전 할당 block slab+CAS ring.18 worker 구성에서4009개가 되는workers*200+409 capacity.32-byte deadline/object/message/argument record와 min heap |

원본C++ 텍스트·EXE의 명령 바이트를 가져온 것이 아니라, 실제 명령/RTTI/필드 접근으로 확인한 동작을 새 C++로 재구현한 결과입니다. 정적 분석은 입력 SHA256을 검사하며 실행 파일을 실행하지 않습니다. research/scope, trace_restore_scope.py, find_core_initializers.py에 재현 근거를 포함했습니다.

## 현재 팩과의 연결·유지 사항

CTransport_SetClosingCallback을 Java FFM에 연결했습니다. 활성 lookup에서 제외된 socket도 최종 callback까지 closing registry가 참조합니다. 수신 중 보유 데이터/진행 중 송신의 자원은 terminal까지 보호합니다. PSS도 동일한 early closing/terminal 경계를 사용합니다.

CTransport_Server_GetDiagnostics는 uint64[8]: allocatedBlocks,exhaustions,initializationFailures,corruptions,timerInlineDecisions,timerPostDecisions,closeStarts,closeTerminals입니다. Java 시작 실패에는 할당 수/풀 초기화 실패를 포함합니다.

최대128개 배치는 사용자가 요청한 기존 전송 DLL 참고 확장입니다. 제공 EXE의 worker는 단일 GQCS였습니다. 기존 fallback/엔진 선택/PSS NIO2는 복원하지 않습니다. 현재 L1 프레이밍/암복호화는 기존 CIOPacketCodec.dll, 게임·세션 실행은 기존 Java owner를 사용합니다. 추가 SQL 없음.

## 원본과 다른 정책·설정

- 원본의 고의 접근 위반 대신 연결/시작 실패와 진단으로 대응합니다. 풀을 다른 engine으로 바꾸거나 고갈 후 늘리는 경로는 없습니다.
- 원본16K총량 INI 기본값80000은 확인했지만 그대로 강제하지 않습니다. native pool은 현재 maxConnections와 workerCount로 총량 max(2*maxConnections,200*workers)를 정하고 worker 수로 나눕니다.8K는 worker별300. 외부 생산자 profile은8K300/16K200. 전체 block 상한65536. 서로 다른 owner의 남은 buffer를 빌려오지 않으므로 편중/대규모 동접에서 용량 측정이 필요합니다.
- 사전 할당으로 대기 시 메모리 사용량이 늘어납니다. maxConnections5000/workers8이면16K slab 약164MB,8K slab 약20MB, timer block slab 약40MB에 view/외부 pool/JVM 메모리가 추가됩니다. PSS 별도 endpoint도 자신의 pool을 가집니다.
- 외부 JVM 스레드에는 원본의 TLS 번호 체계가 없어서64개 native 슬롯과64개 외부 등록 슬롯을 사용합니다. 외부 producer는 등록 슬롯을 native wheel에 매핑합니다. owner17의 분기는 보존하되 그 숫자만 보고 별도 업무 역할의 스레드를 만들지 않습니다.
- 취소 인덱스/압축과64비트 단조 clock은 현재 API의 즉시 취소·수명 계약을 위한 추가 구현입니다. 원본의 모든 게임 객체/allocator/등록 관리자를 복구한 결과는 아닙니다.

## 검사

ASan/UBSan 및 TSan으로 생산 C++ 검사 통과:

- 기존128배치/혼합 NTSTATUS/다중 WSABUF/부분 송신/종료/lease/COW 검사 유지.
- 실제 slab stride/원형 FIFO/고갈 후 미증가, 중복 반납과 불충분한 사전 할당 거절.
- 1,001개 timer의 추가 block과 원본 선두 chain 처리 순서, 취소 슬롯 압축, block pool 고갈,199개만 확보되는 휠 초기화 rollback.
- owner17 및101번째 재게시, 같은 생산자의 token 값과 무관한 owner, 다른 생산자 등록, callback에서 등록하는 timer의 worker 소유권.
- 종료 훅 재진입/차단 중 OS socket 유지, 신규 수신 금지, 훅 후 linger/close, 마지막 I/O 후1회 terminal.

Java1,698개 전체 타입 컴파일/새 해시·ABI, PE64/37개 export/import 검사와 Windows smoke 컴파일도 통과했습니다. 실제 Windows의 배포 DLL/FFM/게임 소켓 어댑터/PSS 및1,000개 timer burst 결과는 WINDOWS_VERIFICATION.json과 실행 기록을 기준으로 합니다. 이전 ABI2 검증을 ABI3 검증으로 취급하지 않습니다.

실제 게임 클라이언트 로그인/사냥, 운영 DB, 장시간 동접·성능 비교는 이 검사 범위 밖입니다.
