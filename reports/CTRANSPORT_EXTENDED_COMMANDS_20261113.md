# 매니저 확장 패킷 명령 조회

2026-11-13. 원본 `UserExPacket`/`ReportPacketLog`의 기본·확장 ID 기록 역할을 L1의 실제 decoder 분류에 맞춰 추가했다. L2 opcode0xd0이나 명령 상한0x97을 L1에 적용하지 않았다.

## 매니저에서 보는 위치

- **보고서 → 종료 사유·최근 패킷 128개**: 소켓 보고서의 진단 id로 조회한다. 기존 종료 사유와 함께 실제 디코더가 판별한 종류·명령 번호가 나온다.
- **보고서 → 프로파일**: 실행 중 패킷 작업에 같은 종류·명령 번호가 나온다. 요청이 끝나면 active 목록에서 제거되고, 중첩 요청이 끝나면 상위 요청 표시로 복원된다.

```text
#2 opcode=81 ageMs=12 protocol=ACTION_UI command=1002
#1 opcode=81 ageMs=15 protocol=ACTION_UI command=1001
...
thread-name id=42 opcode=81 protocol=ACTION_UI command=1001 elapsedMs=23
```

1001은 핑 응답, 1002는 환경 설정이다. 숫자는 10진수로 표시한다. 0x89의 EXTENDED, 0x51의 PROTOBUF·CRAFT·ACTION_UI를 구분한다. CRAFT는 기존 decoder가 선택한 1바이트 종류를 기록하며, 나머지 경로는 실제 16비트 ID를 기록한다.

기존 패킷 카운트·시간 분석은 **기본 opcode 단위**를 유지한다. 확장 ID별 누적 횟수·평균 시간 집계를 새로 만든 기능은 아니다. 활성 작업 elapsedMs는 필터/handler 실행 중 경과 시간이며 순수 업무 처리 시간이나 TCP 지연이 아니다.

## 구현과 범위

`CPacketHandler`의 네 decoder 분기에 진단 호출만 넣었다. 0x51은 기존 순서인 CSProto 등록 여부 → craftType → ActionUI를 그대로 따른다. 허용 여부·handler·wire 형식·read cursor·암호화 키는 바꾸지 않는다. 이 네 호출과 import를 제외한 decoder 소스 본문이 이전 배포본과 같은지도 검사했다.

`CNetworkDiagnostics`는 이미 시작된 작업의 최신128개 기록 **한 슬롯에 종류·명령을 추가**한다. 별도 이벤트를 만들어 패킷 수나 기록 용량을 줄이지 않는다. ticket/sequence로 슬롯을 검증해서 지연된 decoder가 순환 이후 다른 패킷의 기록을 덮어쓰지 못하게 했다. 서로 다른 스레드와 중첩 실행의 작업 문맥을 분리한다. 추적 깊이32를 넘은 중첩 작업은 상위 슬롯에 잘못 붙이지 않고 명령 기록을 생략한다.

헤더가 짧거나 필터에서 먼저 거절돼 실제 decoder에 도달하지 않은 요청은 명령을 추측하지 않고 기본 opcode만 남긴다. 완료된 작업은 보관하던 Trace 메타데이터 참조를 지운다. 기존 연결 상한·최근 종료64개·최근 패킷128개·작업256스레드/깊이32 상한을 유지한다. 본문·비밀번호·채팅을 저장하지 않는다. `ctransport.diagnostics.enabled=false`이면 명령 기록도 비활성이다.

운영 코드는 CNetworkDiagnostics/CPacketHandler/chocco **3개 파일**만 변경했다. 새 source 클래스는 없다. 이전 전체 src의 다른 **1707개 파일은 동일**하다. native C++·DLL·ABI·코덱·독립 PSS 및 SQL은 변경하지 않았다. 전체 src를 clean build해 적용한다.

## 검증

전체 production Java1709개 컴파일과 기존 진단·방송 검사를 수행했다. 새 진단 계약은 1001/1002 구분, 최근128개 한 이벤트 유지, 기본 카운트/시간 집계 유지, 중첩 복원, 깊이 초과 격리, 오래된 슬롯 갱신 방지, 8000개 동시 기록, 종료 후 유지, 완료 후 메타데이터 참조 반환과 진단 비활성을 검사한다.

로컬 JVM에서는 **실제 production CPacketHandler/CSProto**를 실행해 ActionUI·CRAFT·PROTOBUF·EXTENDED65535·짧은 헤더의 분류와 원래 cursor/bytes를 확인했다. 게임 client/DB 객체는 만들지 않았고, unchanged handler의 null-client 경계에서 의도적으로 종료한 decoder 계약 검사다. 실제 게임 요청 처리 전체를 수행했다는 의미는 아니다.

Windows 검사에는 새 진단 계약과 기존 실제 DLL/FFM·SafeSend·종료·레지스트리·독립 PSS 검사를 포함한다. 최종 결과/실행 커밋은 `WINDOWS_VERIFICATION.json`, 로그는 `logs/extended-windows-actual.log`에 보존한다. Windows의 owner 검사에서는 기존 game handler fixture를 사용하므로 actual production decoder의 경로 검증은 위 로컬 JVM 검사와 구분한다.

실제 게임 클라이언트·운영 DB·장시간 실게임 부하 및 Swing 창을 직접 클릭하는 검사는 이번 범위에 포함하지 않았다. 이 기능의 효과는 문제 원인 추적이며, 송수신 성능 개선으로 설명하지 않는다.
