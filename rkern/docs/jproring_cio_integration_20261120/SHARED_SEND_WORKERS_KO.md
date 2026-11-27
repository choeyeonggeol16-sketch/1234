# RKern 셀렉터의 공유 송신 워커

RKern 누적 서버 소스 `d6edf7a6`을 기준으로 구현했다. JVM 옵션 `-Drkern.network.sharedSendWorkers=true`로 켤 수 있다. 기본값은 꺼짐이다. 실행 중 인계 횟수는 리스너의 `ownerDiagnostics()`가 돌려주는 워커별 `sharedWorkRuns()`로 확인할 수 있다.

## 참조 코드에서 확인한 내용

Open LU4 복원본의 `IOThread_common`은 여러 워커가 완료 작업을 기다렸다가 해당 객체의 `OnIOCallback`을 호출한다. 이 소스는 원본 실행 파일이 사용한 `GetQueuedCompletionStatus`의 대체 구현을 명시한다. 복원본 자체에서는 내부 완료 큐를 사용한다. [워커 루프](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/Shared/src/IOCL2Server.cpp#L933-L985) · [복원본 완료 큐](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/Shared/src/Runtime/IOThreadRuntime.cpp#L117-L144)

소켓은 송신 버퍼를 순서대로 연결한다. 첫 버퍼의 쓰기가 끝나면 다음 버퍼를 진행한다. RKern에는 이미 소켓별 FIFO, 패킷 묶음 쓰기, 세션별 순차 암호화가 있다. 적용한 부분은 한 소켓에 실행 가능한 송신이 쌓였을 때 다른 워커가 그중 일부를 가져가는 방식이다. [LU4 소켓 송신](https://github.com/luannbr/Open_LU4_L2OFF/blob/abbb2e5f474d40cf12539a8e97fddc52474ed95e/Shared/src/IOC.h#L1637-L1666)

## RKern에서 어떤 일이 이동하는가

- 한 연결에서 FIFO 앞쪽 최대 32개 슬롯을 확인해 처리 가능한 송신 작업이 16개 이상일 때 인계를 시도한다. 다른 생산자가 아직 완성하지 않은 앞 슬롯을 추월할 수 없다. 16과 32는 이 구현에서 선택한 값이다.
- 셀렉터에서 대기하려는 기존 워커가 작업 하나를 수락한다. 소켓의 FIFO 실행권을 잡은 상태로 넘기므로 암호화와 송신 순서는 한 실행자가 책임진다. 쉬는 워커가 없으면 현재 스레드가 처리한다.
- 동료 워커는 32개 티켓을 처리한 뒤 남은 작업을 원래 소켓 워커로 돌려보낸다. 반환이 거절되면 실행권을 유지한 채 계속 진행한다. 패킷마다 작업 객체를 만들지 않고 소켓의 인계 콜백을 재사용한다.
- 채널 읽기·쓰기, 셀렉터 키 변경, 게임 패킷 핸들러, 세션 타이머는 원래 소유 스레드에서 실행한다. 게임 상태 접근과 송신 순서를 보호하는 기존 규칙을 유지하기 위해 활성 송신 묶음이나 소켓 락을 쥔 구간도 현재 실행자가 마친다.

## 검증 범위

Java 21에서 실제 TCP를 사용했다. `SelectorSharedWorkerRegression`은 독립형 셀렉터와 게임 워커에 붙인 셀렉터, 단일 워커, 바쁜 워커의 거절, 종료와 동시에 수락된 작업의 단 한 번 실행을 확인한다. `SharedSendSerializerRegression`은 실제 암호화 연결에서 송신 70개의 순서와 내용을 검사하고, 원래 워커와 인계받은 워커를 구분해 32개 처리 후 반환을 확인한다. 연결 종료 후 수신·송신 버퍼 반환도 검사한다.

`BroadcastDeliveryRegression`의 `fanout-load`는 두 셀렉터 워커에 접속한 수신자 300명에게 5바이트 이벤트를 보낸다. 여덟 이벤트씩 발행하고 모든 수신자가 확인할 때까지 다음 묶음을 기다린다. 128회 예열 후 512개 이벤트, 총 153,600회 전달을 측정한다. p99의 단위는 이벤트 발행부터 300번째 클라이언트의 복호화 확인까지의 시간이다. 클라이언트와 서버는 CPU 두 개 상당의 실행 한도를 공유했다.

`SharedSendSerializerRegression backlog-load`는 단일 연결에서 2,112바이트 송신 64개를 예약하고 역순으로 커밋한다. 맨 앞 티켓이 준비되는 순간 뒤에 대기 중인 송신을 실제 경로로 처리한다. 96묶음 예열 후 256묶음을 측정했다. p99는 64개를 준비하기 시작해서 클라이언트가 해당 묶음 전체의 바이트와 순서를 확인하기까지의 시간이다.

같은 테스트의 `skew-load`는 한 셀렉터에 연결된 활성 소켓 4개와 다른 셀렉터에 연결된 유휴 소켓 4개를 사용한다. 네 활성 소켓에 각각 64개를 예약하고 커밋한 뒤 256개 모두를 확인한다. 64묶음 예열 후 160묶음을 측정했다.

## 측정값

두 모드를 각각 새 JVM에서 꺼짐 → 켜짐 → 켜짐 → 꺼짐으로 실행했다. 표의 두 값은 실행 순서상의 개별 결과다. 리눅스 루프백, Java 21.0.13, 워커 2개, CPU 한도 2개, 힙 192~512 MiB 조건이다.

| 부하 | 공유 | 초당 전달 또는 프레임 | p99 (ms) | 측정 중 인계 횟수 |
| --- | --- | --- | --- | --- |
| 300명 방송 | 꺼짐 | 122,696 / 139,797 | 44.404 / 43.990 | 0 / 0 |
| 300명 방송 | 켜짐 | 146,656 / 127,596 | 40.923 / 44.483 | 0 / 0 |
| 한 연결에 64개 누적 | 꺼짐 | 111,082 / 116,354 | 1.314 / 1.050 | 0 / 0 |
| 한 연결에 64개 누적 | 켜짐 | 71,083 / 81,055 | 4.373 / 8.173 | 254 / 251 |
| 한 셀렉터에 활성 연결 4개 집중 | 꺼짐 | 98,034 / 81,856 | 13.664 / 6.654 | 0 / 0 |
| 한 셀렉터에 활성 연결 4개 집중 | 켜짐 | 78,957 / 139,757 | 11.460 / 11.943 | 367 / 322 |

300명 방송에서는 각 수신자의 송신이 제때 빠져나가 공유 인계가 발생하지 않았다. 이 처리량 차이에서 공유워커 효과를 추정할 수 없다. 한 연결에 작업을 모으면 인계가 실제로 실행됐고 두 차례 모두 처리량과 p99가 악화됐다. 한 세션의 암호화 순서를 유지하는 동안 워커 간 왕복이 발생한다. 이 측정만으로는 워커 전환, 캐시, OS 스케줄링 각각의 비용을 분리할 수 없다. 실제 게임 월드의 공성 처리나 동접 1만 명의 성능은 이 수치의 범위 밖이다.

활성 연결 4개를 같은 셀렉터에 몰았을 때는 두 번 모두 인계가 발생했지만 처리량과 p99의 개선 방향은 재실행에서 뒤집혔다. 현재 팩의 기본 송신 정책을 바꿀 만한 일관된 이득은 관측되지 않았다.

테스트 클래스와 서버가 같은 클래스패스에 있을 때 다음처럼 비교할 수 있다. `CP`에는 컴파일된 서버·테스트 클래스, 리소스와 기존 서버 라이브러리를 지정한다.

```sh
for enabled in false true true false; do
  java --enable-preview -ea -XX:ActiveProcessorCount=2 -Xms192m -Xmx512m \
    -Drkern.network.sharedSendWorkers="$enabled" -cp "$CP" BroadcastDeliveryRegression fanout-load
  java --enable-preview -ea -XX:ActiveProcessorCount=2 -Xms192m -Xmx512m \
    -Drkern.network.sharedSendWorkers="$enabled" -cp "$CP" SharedSendSerializerRegression backlog-load
  java --enable-preview -ea -XX:ActiveProcessorCount=2 -Xms192m -Xmx512m \
    -Drkern.network.sharedSendWorkers="$enabled" -cp "$CP" SharedSendSerializerRegression skew-load
done
```
