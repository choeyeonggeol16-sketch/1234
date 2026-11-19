# JProRing Selector 수신과 direct 버퍼 검증

N8 전체 소스와 N9 카르마 수정이 포함된 `2afe014`에서 시작했다. 한 Selector 스레드가 수신 프레임과 direct 버퍼를 소유한다. `SelectorFrameReader`는 길이가 헤더를 포함하는 2바이트 리틀엔디언 프레임을 읽으며, 기존 `ReadBuffer` 하나를 연결별로 재사용한다. 프레임 개수 제한이나 핸들러의 일시정지 뒤에는 남은 완성 프레임을 추가 소켓 읽기 없이 이어서 처리할 수 있다.

`SelectorReceivePool`은 소유 스레드마다 4KiB 및 64KiB 슬롯을 설정 개수만큼 허용하고 처음 사용할 때 direct 버퍼를 할당한다. 큰 프레임을 소비하면 해당 슬롯을 반환한다. 할당된 backing 메모리는 풀에서 재사용하며, 슬롯 사용량과 direct backing 할당량은 각각 관측할 수 있다. 출력되는 상한은 이 수신 풀의 backing 메모리 범위다.

`rkern` 디렉터리에서 JDK 21로 실행한다.

```sh
classes=$(mktemp -d)
javac -encoding UTF-8 -d "$classes" \
  src/l1j/server/server/model/network/buffer/ReadBuffer.java \
  src/l1j/server/server/model/network/core/transport/selector/*.java \
  docs/jproring_p1_selector_20261120/SelectorFrameReaderTest.java
java -cp "$classes" SelectorFrameReaderTest
```

Linux loopback에서 분할 헤더·본문, 합쳐진 프레임, 프레임 예산, 최대 65,535바이트, 큰 슬롯 고갈, EOF, 잘못된 길이, 콜백 오류와 콜백 중 종료를 검사했다. 실행당 재접속 256회 뒤 대여와 반환 수가 같고 사용 중인 슬롯은 0개였다. 테스트 풀의 할당량은 워밍업 이후 69,632바이트로 유지됐고 설정 상한은 73,728바이트였다. 네이티브 라이브러리나 preview 옵션 없이 일반 JDK 21로 컴파일하고 실행했다.

현재 범위는 독립 수신 구성요소다. 게임 서버의 멀티 Selector 이벤트 루프, 송신, 암호화, 기존 클라이언트 접속, Windows 부하와 p99 및 프로세스 메모리 검증은 남아 있다.
