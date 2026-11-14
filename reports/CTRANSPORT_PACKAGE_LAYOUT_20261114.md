# 네트워크 코어 역할별 Java 패키지 정리 — 2026-11-14

기존 `l1j.server.server.model.network.core` 바로 아래에 섞여 있던 26개 클래스를 8개 패키지로 옮겼다. 클래스 이름과 메서드 본문은 유지한다. 패키지를 넘는 기존 호출에는 필요한 접근 범위만 public으로 바꿨다. 네이티브 C++·DLL·외부 ABI10/67exports·패킷 규약은 변경하지 않았다.

| 패키지 | 역할과 클래스 |
|---|---|
|`core.transport`|DLL/FFM 연결: CIOServer, CSocket, CIOServerEndpoint, CIOPacketCodec|
|`core.connection`|연결·송수신 버퍼 수명: CUserSocket, CUserSocketRegistry, CUserSocketCloseTask, CIOBufferEx, CIOBufferPool, CIOSlotPool, CIOFrame|
|`core.session`|게임 세션·재접속 연결: CIOClient, DominationHybrid|
|`core.server`|접속 허용·생성·종료 조정: Server, IServerCallback, CDummyServerCallback|
|`core.broadcast`|공유 송신·대상 필터: CAsyncSendConstBuffer, CAsyncSendConstBufferExceptSelf, CSharedBroadcast, CTLMFilter|
|`core.diagnostics`|운영 조회: CNetworkDiagnostics, CPerfMon, CPoolDiagnostics|
|`core.execution`|실행 작업·샤드 표시: CIOObject, CIOShardContext|
|`core.config`|설정·DLL 검증 상수: CIOConfig|

소켓과 버퍼의 내부 상태를 함께 사용하는 클래스는 `connection`에 둔다. 버퍼의 변경 가능한 필드들을 다른 패키지에 공개하지 않았다. 기존 `core.cs`, `core.sc` 패킷 패키지와 실제 게임 스케줄러는 유지한다. PSS의 독립 네트워크 동작도 유지하며 이동한 세션 타입을 가리키는 import만 갱신한다.

## 소스·적용

- 전체 src는 이전과 같이 1,712파일(Java 1,711개+기존 리소스)이다. 26개 파일 경로가 이동하고 관련 참조를 갱신했다.
- full source의 471파일은 패키지/import/필요한 public 접근 범위 변경이 있다. 게임 로직·조건·순서·상수 값은 변경하지 않았다.
- `research/package-layout-20261114/BASELINE.json`은 이전 배포 commit `c0e13d8`의 경로·파일 hash·정규화한 코드 토큰 hash와 이전→현재 경로를 보존한다.
- `tests/verify_package_layout_scope.py`는 package/import/public 및 이동한 타입의 FQN만 정규화하고, 문자열·연산자·문장 순서를 포함한 Java 코드 토큰을 모두 대조한다. source의 추가/누락, native C++ 변경, DLL/PSS 클라이언트 변경도 거절한다.
- 네이티브 빌드 스크립트의 Java SHA pin 경로, 테스트 fixture, 테스트 실행 FQN, Windows workflow, 소스 패치의 삭제 목록을 새 경로로 맞췄다.

**전체 src로 교체하고 clean build한다.** 부분 패치를 사용할 경우 함께 제공하는 `deleted-files.txt`에 따라 이전 26개 위치의 소스를 제거한다. 오래된 core 루트 클래스와 새 하위 패키지 클래스를 함께 빌드하지 않는다. 현재 CTransport.dll은 이전 배포와 같은 바이너리다.

## 검증

- 전체 production Java 1,711개 컴파일 오류 0. 기존 deprecated API 경고 5개.
- Windows 실행 스크립트가 사용하는 9개 Java 소스 묶음의 독립 컴파일 통과.
- 방송 직렬화·필터, 진단 활성/비활성·확장 명령, 송신 중 close의 lease 보유·첫 종료 사유 보존, 실제 독립 PSS TCP 페어링·재시작 검사를 통과했다.
- Windows에서는 이동한 production 클래스와 같은 DLL로 FFM 송수신·세션·공유 송신·SafeSend·풀·참조 검사·독립 PSS·매니저 검사를 수행한다. 최신 실행 ID·소스 commit·동일 DLL hash는 WINDOWS_VERIFICATION.json에 기록한다.
- 실제 게임/DB 장기 부하와 원본 전체 복원율은 이번 패키지 정리의 검사 범위가 아니다.

```sh
python tests/verify_package_layout_scope.py
python tests/check_package_compile.py
```

두 번째 명령은 Linux의 Java21에서 Windows 테스트 소스 묶음을 컴파일한다. Windows DLL 실행 검사는 `tests/run_windows.ps1`을 사용한다. 과거 보고서·범위 검사의 baseline은 해당 이전 commit에 대한 기록이며 현재 패키지 범위 검사는 위 첫 명령이다.

Windows 검증: https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43559706222 — source `70d2cb5`, artifact `7e55707`, DLL SHA256 `62604a5b1b111526da30b0d06f224eb6d5f3e4aafca0292571aae7932b3f272e`.
