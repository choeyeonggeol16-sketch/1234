# 서버 버전 핸드셰이크 정리 — 2026-11-04

프로토콜 직접 송신 수정본 `9e633d7` 다음 변경입니다. 이번 운영 소스 변경은 **Java 3개**이며 `SC_ServerVersionInfo`의 중복 직렬화·전달 래퍼·거절 종료 처리에 한정했습니다.

## 변경 결과

- `SC_ServerVersionInfo`를 상태 없는 정적 송신 헬퍼로 정리했습니다. `ProtoPacket` 상속, `_accept` 인스턴스, `body`/`writeFields` 중복, `sendCtor`/`sendConstructed`/`sendFields`/`encodeFields`, 무의미한 임시 변수를 제거했습니다. 공개 진입점 `send`와 `denial`은 유지했습니다. 파일은 116줄에서 58줄입니다.
- 정상 17개 필드와 거절 `version_check=1`, 필드 순서, signed/unsigned varint, 메시지 ID, 끝의 0 두 바이트를 유지합니다. 실제 Unix 초와 서버 시작 시간의 의미를 보존했고, 현재 시각 캐시는 추가하지 않았습니다.
- `denial`마다 만들던 람다·`RunnableWrapper`·`ScheduledFuture`를 없앴습니다. 거절 응답이 기존 풀의 직접 프레임에 정상적으로 기록된 뒤 소켓의 지연 종료를 요청합니다.
- 종료가 요청되면 새 수신 처리와 신규 송신 admission을 즉시 차단합니다. 이미 예약한 encoder와 FIFO 앞부분의 송신은 계속 유효합니다. 같은 TCP 읽기에 들어 있는 후속 요청도 처리하지 않습니다.
- 기존 **3초 유예는 최소 유예**로 유지합니다. 3초가 지나고 이미 수락한 송신이 모두 끝나면 닫습니다. 느린 송신은 기존 30초 graceful-close 제한을 적용하므로 응답 완료를 기다리며 무한정 연결을 남기지 않습니다. 송신/스케줄 admission 실패, 실제 I/O 오류, 상대 종료는 즉시 종료할 수 있습니다.
- 새 타이머 스레드를 만들지 않습니다. 연결 때부터 존재하는 약한 참조 `SocketDeadline` 객체를 재사용하고, 필요할 때 기존 5초 검사만 앞당깁니다. 기존 50ms 휠을 사용하므로 실제 종료 시점에는 휠/스케줄 지연이 있을 수 있습니다. 앞당기기 전의 오래된 휠 이벤트가 두 번째 주기 검사 루프를 만들지 않게 합쳤습니다.
- 일반 최종 패킷의 graceful close에는 3초 유예를 추가하지 않았습니다. 거절 처리만 새 진입점을 사용합니다. 데드라인을 등록할 수 없는 경우에는 연결을 닫습니다.

스레드풀의 작업 객체를 없앤 변경입니다. 기존 시간 휠의 노드 풀은 그대로 사용하며, 풀 부족 시의 노드 생성 등 서버 전체의 할당을 없앴다는 의미는 아닙니다. 다른 62종의 호환 빌더/직렬화 정리는 이번 범위에 포함하지 않았습니다.

## 변경한 운영 Java

1. `src/l1j/server/server/packets/Protocol/Packet/SC_ServerVersionInfo.java`
2. `src/l1j/server/server/model/network/core/CIOUserSocket.java`
3. `src/l1j/server/server/model/network/core/CIOSocketFactory.java`

## 검증

- 전체 서버 Java **1,723개** 컴파일 통과. 기존 removal/deprecated 경고 5개.
- 원본 핸드셰이크와 **정상 64건 + 거절 64건** 바이트 일치. 버전 정수의 0, -1, 127/128, 최소/최대값 및 실제 Unix 시간 범위를 검사했습니다.
- 종료 계약 **16개 그룹** 통과. 부분/인라인 WRITE, 미완료 encoder 예약, FIFO 순서, 후속 수신 차단, 반복 거절 1,000회, 데드라인 거부, 풀 소진, 상대 종료, 30초 제한, 기존 일반 graceful close, 오래된 휠 이벤트, 실제 3초 유예를 포함합니다.
- WRITE 완료·데드라인·강제 종료 경쟁 **100회**에서 중복 종료/버퍼 누수 없이 완료했습니다.
- 실제 **localhost TCP**로 거절 응답을 받고 3초 유예 뒤 정상 EOF를 확인했습니다.
- 기존 프로토콜 회귀: 다른 62종 **1,970건**, 생성자/팩토리 94개 오버로드 **1,504건**, 패시브 **80건** 바이트 일치. 직접 프레임 계약 **44개 단언**도 통과했습니다.
- 기존 AST 해시 **126개** 유지. 이번 클래스의 과거 본체 2개만 테스트 fixture로 옮겼으며 다른 124개는 운영 소스에서 비교합니다.

검사는 실제 송신기·`CIOUserSocket`·버퍼 풀·writer를 사용했습니다. 데드라인 작업은 운영 소스에서 그대로 가져오고, 게임 세션/스케줄 admission과 제어 I/O는 대역을 사용합니다. localhost 검사는 실제 비동기 TCP 소켓을 사용했습니다. 실제 게임 클라이언트·운영 DB·Windows IOCP 통합 및 운영 처리량은 실행하지 않았습니다.

## 적용과 재현

`FULL_SRC`에는 이전 프로토콜·아이템 사용 V3·NPC·자동사냥·스프라이트 수정이 포함됩니다. `PATCH`는 **9e633d7 대비 Java 3개 전체**와 새/수정 테스트·원시 결과를 포함합니다. 다른 버전에는 전체 소스 기준으로 병합하세요.

기존 프로젝트를 백업하고 서버를 내린 다음 3개 파일을 모두 적용합니다. **JDK 21 / UTF-8 / `--enable-preview`로 전체 Clean/Rebuild·재시작**하세요. 새 소켓 API와 내부 인터페이스가 있으므로 일부 class 파일만 섞지 않습니다. 추가 DB 변경은 없습니다. 테스트의 `stubs`와 frozen fixture는 운영 `src`나 classpath에 넣지 않습니다.

```bash
python3 tools/build_item_refactor.py --output /tmp/version-build
python3 tests/server_version/test_server_version.py --classes /tmp/version-build/classes
python3 tests/server_version/test_server_version.py --classes /tmp/version-build/classes --loopback
python3 tests/protocol_direct/test_source.py
python3 tests/protocol_direct/test_wire.py --classes /tmp/version-build/classes
python3 tests/protocol_direct/test_transport.py --classes /tmp/version-build/classes
```

원시 로그와 변경 파일 목록·전체 소스 해시·적용 diff는 `validation/server_version_*`에 있습니다. 문서 날짜는 한국 시간이며 실행 로그에는 호스트 UTC가 표시됩니다.
