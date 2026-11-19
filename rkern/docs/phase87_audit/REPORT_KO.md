# RKern 87차 브로드캐스트 회귀 검증 (2026-11-16)

## 기준 및 적용 범위

- 기준 ZIP: `RKern_NC_NativeBroadcast_Phase86_Broadcast_Audit_Fixes_FULL_SRC_20261116.zip`
- 기준 ZIP SHA256: `0a566c828677967fdf5cf1106b4d6e1fa1c7c1be3da547bc0631f565325d7677`
- **production Java / Native / DLL 수정 없음.** 소스는 86차에서 그대로 보존하고 이번에는 검증용 보고서·재실행 테스트만 추가함.
- Java 21 (21.0.12.1) Linux 격리 환경에서 검사. 실제 Windows IOCP, MySQL/게임 통합 실행과 5천명 실동접 시험은 하지 않음.

## 실제 실행한 검사

1. `src/`의 Java **1,710개**를 JDK javac 파서로 구문 검사: **오류 0**.
2. 85차 대비 86차 ZIP 전체 byte SHA256 비교: **production Java 변경 26개**, 신규 감사 파일 10개, 기존 삭제 0개. 기존 나머지 항목은 byte-identical. `CHANGED_JAVA_FILES.tsv` 파일표 대조.
3. 실행 코드 정적 패턴 검색(주석·문자열 제외): `Broadcaster.*`, `broadcastPacket*`, `sharedWire()`, `sendPackets(new S_...)` 각 **0건**. 이 4개 패턴만 검사한 결과이며, 임의의 커스텀 반복 송신까지 모두 0이라는 뜻은 아님. `new S_...` 생성자는 4건 있지만 방송 API 호출이 아니라 별도 패킷 클래스 내부 일반 생성자임.
4. **실제 production `CAsyncSendConstBuffer`와 `CAsyncSendConstBufferExceptSelf`를 테스트용 CIO 소켓·Native 풀 스텁에 연결**: 3·8192·16384·16385·65535·65536 바이트 경계, 송신자 제외, 이종 owner, 풀 비공유 전송, 128개 병렬, 예외/수명, 5000명 모의 수신자 2프레임, 10,000건 FIFO/참조수 테스트 **통과**.
5. 추가 반복 스트레스: 고정 seed 870216으로 **5회 × 9개 패킷 크기 × 300개 모의 수신 시도**(크기 3,8,9,64,8192,16384,16385,32768,65535), 연결 종료·자기 자신 제외·Native owner 불일치·ConstBuffer 미지원·공유 lease 거절 경로 혼합: 필터·패킷 내용·참조수 회수 **통과**.
6. **실제 production `CSharedBroadcast`·`CTLMFilter`를 격리 월드 스텁과 함께 컴파일·실행**: 맵/AOI/팀 필터, sender 제외, 수신자 중복, explicit recipient FIFO, team frame interleave, global playable/GM, Proto raw snapshot, ground-item known-object admission, 예외 시 fast list 반환 **통과**.
7. 86차 변경 26개 파일의 before/after 경로 수동 재검토: 제거 패킷 `removeKnownObject` 후 송신, `onPerceive` 후 Action, 컴패니언 remove→put, 스팸필터 후 채팅 프레임 2개, 경기장 카운트다운 선후 관계, 투명 해제 self-first·put→invis 순서를 확인. 별도의 게임 런타임 검증은 아님.

## 발견 결함 및 처리

- **이번 검사로 재현된 production 방송 결함: 0건.** 무리한 변경으로 기존 순서를 훼손하지 않도록 production 소스 변경을 하지 않았음.
- 86차의 `HISTORICAL_322_CANDIDATE_TRIAGE.tsv`는 과거 322개 *후보*에 대한 1차 휴리스틱이며, 모든 322경로의 실제 게임 효과·사이드 이펙트를 수동 완전 검증한 결과는 아님.
- 전체 외부 JAR 타입 컴파일 및 Windows IOCP/실게임·실DB/5천명 soak는 이번 87차 검수 **통과 범위에 포함하지 않음**.
- 모의 스텁은 입출력 시스템을 단순화하므로 Native DLL 내부 동작과 실서버 암호화·접속 종료 순서·경쟁 상태를 보증하지 않음.

## 재실행

Linux Java 21에서 ZIP을 압축 해제한 후 `bash docs/phase87_audit/run_tests.sh`를 실행하면 같은 스텁 기반 회귀검사와 전체 Java 구문 검사를 재실행함. 테스트/스텁은 `docs/`에만 위치해 서버의 production 클래스에 영향을 주지 않음.
