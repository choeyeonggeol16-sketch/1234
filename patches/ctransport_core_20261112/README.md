## 2026-11-14 추가 Windows 검증

동일 프로토콜에서 클라이언트 JVM 분리 비교,IOCP worker 대기 주입,180초/1,469회 재접속과5,876개 소켓 회수를 통과했습니다. 운영 src1712개와 DLL/PSS는 직전 배포와 동일합니다. REPORT_STABILITY_EXTENDED.md 및 research/stability-extended-20261114/에 수치와 원시 증거가 있습니다. 최신 범위 검사는 `python tests/verify_extended_stability_scope.py`, 추가 시험은 기존 Windows 검사 후 `tests/run_extended_stability.ps1`입니다.

---

## 2026-11-14 실제 Windows 부하·안정성 검사

송신 완료 큐의 깨우기 누락을 실제 부하로 재현해 소유권 해제의 메모리 순서를 수정했습니다. 전체 src를 교체하고 clean build합니다. CTransport.dll과 독립 PSS는 동일합니다. 수치·실행 이력·제한은 REPORT_STABILITY.md와 research/stability-20261114/에 있습니다. 최신 범위 검사는 `python tests/verify_stability_scope.py`, Windows 재현은 `./tests/run_windows.ps1` 후 `./tests/run_stability.ps1`입니다.

---

## 2026-11-14 네트워크 코어 패키지 정리

core 아래26개 클래스를 transport·connection·session·server·broadcast·diagnostics·execution·config로 분리했습니다. 전체 src로 교체하고 clean build합니다. 클래스 이름·메서드 본문·native DLL은 유지합니다. 상세 경로와 검증은 REPORT_PACKAGE_LAYOUT.md 및 WINDOWS_VERIFICATION.json을 확인하세요. 당시 패키지 정리 범위 검사는 `python tests/verify_package_layout_scope.py`입니다. 이후 안정성 수정은 아래 최신 검사를 사용합니다.

---

## 2026-11-13 전용 객체 할당자·MSVC 객체 규약

원본에서 확인한49개 크기 등급과 CSmartQueue 재사용 경로를 구현했습니다. MemoryObject/CIOObject/CISocket/CIOSocketEx/CSocket/CUserSocket 및 서버·팩터리·종료 검사기의 확인된 배치와 가상 호출 규약을 반영했습니다. 새 DLL은 MSVC x64로 빌드하며 전체 src와 함께 적용합니다. 매니저 보고서 → 풀·회수 진단에서 native 객체의 사용/반납/확보량을 확인합니다.

실제 Windows DLL/FFM 검사와 원본 RTTI 계층·vtable 길이 대조를 통과했습니다. 원본에서 확인한 사실, L1 호환 차이, 빌드·검사 명령은 REPORT_OBJECT_MEMORY_ABI.md에 있습니다. ABI10/67exports·codec·독립 PSS·DB는 유지합니다. Windows x64 Visual Studio Developer 셸에서 `python native/build_native.py`로 빌드합니다.

아래는 이전 배포 이력입니다.

---

## 2026-11-13 종료 참조 검사·ORT31종 추적

보고서 → 풀·회수 진단에 종료 후 잔여 참조, 종류별 카운터와 획득/반납 위치를 추가했습니다. 기존 타이머에서5초 후 검사하며 정상 정리 대기는25초 뒤 한 번 더 확인합니다. 검사기128개/기록16개/화면8개로 제한하고 종료 시 취소합니다. 강제 해제는 하지 않습니다. 새 src와 DLL을 함께 사용하세요. 원본 내부 규약 반영 범위와 의도적인 L1 차이는 REPORT_REFERENCE_RESTORATION.md에 있습니다. 실제 Windows 검증과 전체 Java 컴파일 통과, ABI10/67exports·PSS·codec·DB 유지.

아래는 이전 배포 이력입니다.

---

## 2026-11-13 바이너리 확인 타이머 이름 적용

원본 signature에 있는 CThreadLocalTimer / CSlot / CMwmrQueue4009::Pop / AddTimer / Dispatch를 기존 실행 코드에 적용했습니다. 기존 TimerWheel/TimerBlockPool 타입은 제거했습니다. 새 src와 CTransport.dll을 함께 적용합니다. 확인된 이름·L1 인자 계약·추정 보조 이름 및 추가 발견은 REPORT_VERIFIED_NETWORK_NAMES.md에서 구분합니다.

## 2026-11-13 네트워크 풀 오류 경로·객체 참조 진단

기존 풀의 손상 항목 건너뛰기와 격리, MemoryObject ID/ref 오류 추적을 반영했습니다. 기존 풀·회수 진단에서 확인합니다. 새 전체 src와 CTransport.dll을 함께 사용하세요. 원본의 의도적 NULL 크래시는 기존 L1 실패 반환 정책으로 유지합니다. 상세 근거·차이·검증은 REPORT_NETWORK_FAULT_IDENTITY.md를 참조하세요.

# 풀·회수 진단 / 매니저 메모리 제한

보고서 → 풀·회수 진단에서 Java/native 사용량·대여/반납·유효 보유자·최근60개 관측을 조회합니다. 확정 누수나 자동 회수 기능이 아닙니다. 화면 로그 대기열과 문서 크기를 제한하고 상태 갱신을 합쳤습니다. 실제 Windows 창 이동/크기 변경과 풀 회수를 검사했습니다.

새 CTransport.dll과 전체 src를 함께 적용하고 clean build합니다. ABI10/67exports,코덱·독립 PSS와 SQL은 그대로입니다. REPORT_POOL_MANAGER.md와 WINDOWS_VERIFICATION.json을 확인하세요.

아래 내용은 이전 배포 이력입니다.

---

# ABI10 후속 — 매니저 확장 명령 조회

보고서 → 종료 사유·최근 패킷128개와 프로파일에서 확장 요청의 종류·명령 번호를 함께 표시합니다. 예: opcode81 / ACTION_UI / command1001(핑 응답),1002(환경 설정). 실제 decoder 분류를 재사용하며 기본 카운트·시간 집계는 기존대로입니다.

전체 src를 clean build해 적용합니다. DLL/ABI/코덱/독립 PSS·SQL은 동일합니다. 검증 결과와 범위는 REPORT_EXTENDED_COMMANDS.md 및 WINDOWS_VERIFICATION.json을 확인하세요.

아래 내용은 이전 배포 이력입니다.

---

# ABI10 후속 — 문장 SafeSend·종료 사유 조회

혈맹 마크 송신에 512KiB 여유분을 적용했습니다. 송신 대기량이 1.5MiB를 초과하면 해당 마크 전송을 생략하고 연결을 유지합니다. 필수 패킷의 기존 송신 정책은 유지합니다.

매니저의 소켓 및 종료 사유·최근 패킷128개에서 Java 종료 이유와 native operation/error를 함께 조회합니다. 전체 src를 clean build해 적용하며 DLL/ABI/코덱/독립 PSS·SQL은 기존과 같습니다. 검증 결과와 범위는 REPORT_SAFESEND_CLOSE.md 및 WINDOWS_VERIFICATION.json을 확인하세요.

아래 내용은 이전 배포 이력입니다.

---

# ABI10 후속 — 매니저 진단·필터·공유 방송 정리

REPORT_NETWORK_FOLLOWUP.md 기준으로 전체 src를 clean build합니다. native DLL/ABI/코덱/PSS는 기존 ABI10과 동일합니다. config/ctransport.properties를 실행 폴더에 적용하고 변경 시 재시작합니다. -D 옵션이 파일보다 우선하며 추가 SQL은 없습니다.

매니저의 패킷 카운트·시간 분석·소켓·프로파일·참조/수명 상태·전송률을 연결했습니다. 최근 패킷 조회는 소켓 보고서의 진단 id를 사용합니다. 이동·방향·삭제와 기존 액션·이펙트의 공통 방송 경로 및 맵/거리 필터를 정리했습니다.

MAC 제한 기본0은 비활성입니다. 서버 제한/API는 있지만 실제 클라이언트 식별자 공급은 미연동입니다. 진단의 byte 수치는 복호화 본문/송신 접수량이며 TCP 완료량이 아닙니다. 원본 전체 복원·실게임/DB 장시간 검증·매니저 실제 클릭 검증을 완료한 결과는 아닙니다.

아래 내용은 이전 배포 이력입니다.

---

# CTransport ABI10 — 공유 평문 방송

전체 src와 같은 묶음의 CTransport.dll을 함께 적용하고 clean build합니다. ABI0xa0000 / 67 exports이며 ABI9 Jar/DLL 혼용은 시작을 거절합니다. 기존 삭제 목록(deleted-files.txt), Java 21 preview/native-access 옵션과 CIOPacketCodec.dll을 유지합니다.

액션·이펙트 방송은 CAsyncSendConstBuffer/ExceptSelf로 평문 하나를 공유하고 기존 Java FIFO를 거쳐 소켓별 출력에서 암호화합니다. 소스 ID 제외, 순서·참조 회수·종료를 실제 Windows에서 검증했습니다. 일반 패킷과 기존 송신 제한은 유지합니다. PSS는 기존 독립 연결로 활성화된 상태이며 추가 SQL은 없습니다.

자세한 변경/검증/제약은 REPORT_SHARED_BROADCAST_ABI10.md와 WINDOWS_VERIFICATION.json을 확인하세요. 실게임/운영 DB/장시간 부하 및 속도 향상폭은 미검증입니다. 아래 ABI9 이전 설명과 검사 기록은 당시 이력입니다.

---

# CTransport ABI9

원본 RTTI에서 CDummyServerCallback/IServerCallback과 MemoryObject 기반을 확인해 실제 C++/Java 호출에 연결했습니다. Java CIOSocketFactory는 CDummyServerCallback으로 바꾸고 CIOBufferEx를 독립 클래스로 사용합니다. 상수 입력을 보존하는 CSocket::__async_AsyncSendConstBuffer 경로를 실제 gather SEND에 연결했습니다.

1. 전체 src와 같은 묶음의 CTransport.dll ABI0x90000/64 exports를 함께 적용하고 clean build합니다. 구 export alias는 없으며 ABI8 Jar/DLL 혼용은 시작을 거절합니다.
2. deleted-files.txt를 반영합니다. CIOSocketFactory.java 및 예전 CIOSocketFactory.class, CIOBufferPool$Lease.class 등 이전 빌드 결과가 남지 않도록 합니다.
3. 기존 CIOPacketCodec.dll·독립 PSS·클라이언트·설정과 Java21 preview/native-access 옵션을 유지합니다. 추가 SQL 없음.
4. 원본 class/member 이름을 반영한 C ABI entry와 L1Interop 보조 API의 전체 대응은 research/abi9/export-name-map.json입니다. 원본 MSVC ABI를 복제한 것은 아닙니다.
5. 분석·호출 경로·검사·추가 미반영 항목은 REPORT_ORIGINAL_NAMES_CONST_ABI9.md, 실제 Windows 실행 상태·소스 커밋·해시는 WINDOWS_VERIFICATION.json을 따릅니다.

운영 DB·실게임·장시간 부하와 원본 서버 전체의 동일성은 미검증입니다. 이전 보고서는 당시 구현의 이력입니다.

실제 ABI9 Windows Server2022/Temurin21 검사 통과: [실행43365489001](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43365489001), 검사 소스 `0ca18a8d10a84ede5db6d34ddbbd24cb29d0f811`. 새64개 entry의 DLL과 production Java FFM, 실제 gather 상수 송신/20K frame의16K 경계/기존 codec 일치/최종 packet·FIN/lease 해제를 확인했다. 동시32개 loopback은 30,134ms·누적54,304접속·6,950,912 L1 frames·3,558,866,944 C2S wire bytes를 처리하고 round별 색인 잔여0을 확인했다. 원본 이름 변경 외 소스1,684개 바이트 불변도 별도로 검사했다. 실게임·DB·운영 성능 보장은 아니다. 최종 native/src/tests/build는 이 검사 커밋과 동일하고 후속 커밋은 검증 기록을 추가한다.
