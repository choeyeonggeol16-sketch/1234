# JProRing 순수 Java 멀티 Selector 전송 코어

`SelectorTransport`는 Java 21의 `ServerSocketChannel`, `SocketChannel`, `Selector`를 사용한다. 지정 개수의 소유 스레드에 연결을 고정 배정하고, 연결 슬롯과 외부 명령 큐는 소유자별 상한을 갖는다. 외부 명령은 접수 뒤 해당 소유자에서 실행하거나 취소한다. 읽기·쓰기·암호화 상태는 연결 소유 스레드에서만 바뀐다.

### 송수신과 메모리

- 수신 버퍼는 4KiB·64KiB, 송신 버퍼는 16KiB·64KiB 슬롯을 처음 사용할 때 direct로 할당한다. 소유자별로 대여 수, 반환 수, 실제 할당량과 상한을 종료 뒤 검증할 수 있다.
- 짧은 송신 프레임은 아직 쓰기가 진행되지 않은 큐 끝의 남은 공간에 직접 인코딩한다. 여러 슬롯은 순서대로 gather write한다. 부분 송신은 `ByteBuffer`의 현재 위치에서 이어가고, 해당 슬롯의 모든 바이트가 소켓에 전달된 뒤 슬롯을 반환한다.
- 연결별 바이트·프레임 상한과 읽기 역압력, 쓰기 정체 시간 제한을 둔다. 닫힌 연결 핸들은 풀 참조를 놓는다. 종료 시 아직 실행되지 않은 명령은 취소한다.
- 기존 Java `Cryptor`를 사용한다. 첫 송신을 평문으로 보낼 수 있고 이후에는 각 프레임을 FIFO에 넣기 전에 한 번만 암호화한다. 송신 인코딩이 실패하면 앞서 접수한 프레임과 암호화 상태를 유지한다.
- 이벤트 루프는 접수·명령·프레임 처리를 제한된 분량으로 반복한다. 시간 예산은 명령이나 연결 처리 경계에서 검사한다. 오래 실행되는 개별 핸들러는 그 소유 스레드의 진행에 영향을 준다.

### 기능 검증

JDK 21에서 `rkern` 디렉터리 기준으로 실행한다.

```sh
classes=$(mktemp -d)
javac -encoding UTF-8 -d "$classes" \
  src/l1j/server/server/Opcodes.java \
  src/l1j/server/server/model/network/buffer/ReadBuffer.java \
  src/l1j/server/server/model/network/buffer/WriteBuffer.java \
  src/l1j/server/server/model/network/codec/Cryptor.java \
  src/l1j/server/server/model/network/core/transport/selector/*.java \
  docs/jproring_p1_selector_20261120/SelectorFrameReaderTest.java \
  docs/jproring_selector_transport_20261120/SelectorTransportTest.java
java -cp "$classes" SelectorFrameReaderTest
java -cp "$classes" SelectorTransportTest
```

실제 루프백 TCP로 수신 분할과 슬롯 반환, 세 소유자로의 연결 배정, 접속·송신 슬롯·명령 큐 고갈, 부분 송신 및 FIFO, 쓰기 시간 초과, 정상 종료, 명령 취소, 초기 `S_KEYBYTES`와 연속 암호화 송수신을 확인한다. 암호화 기준은 테스트의 바이트 단위 Java 구현이다.

### RKern 연결 경계

현재 전송 코어는 독립형이다. `GameServer`는 기존 `Server`와 `CDummyServerCallback` 경로로 실행된다. 게임 연결에는 접속 제한과 계정·캐릭터 등록, `CUserSocket` 송신 소유권, CIO 샤드와 게임 타이머, 방송 버퍼, 세션 종료를 함께 연결해야 한다. 전송 테스트에 사용한 소유 스레드는 기존 `CIOThreadCallback`과 별개다. 기준 ZIP에는 `CIOPacketCodec.dll`과 운영 DB·클라이언트 환경이 빠져 있어 네이티브 상대 바이트 대조와 실제 로그인 검증은 할 수 없었다. 비교 벤치마크는 게임 실행 경로를 연결하고 기능 검증을 통과한 뒤 수행한다.
