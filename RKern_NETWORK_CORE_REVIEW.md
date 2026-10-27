# RKern 네트워크 코어 검토

검토일: 2026-10-27 (한국 시간). 대상: 공개한 v4 네트워크 소스와 연결된 실행기·타이머.

평가는 **객체별 직렬 실행, 버퍼 소유권, 과부하 제어를 직접 구성한 숙련된 서버 시스템 구현**입니다. 아래 설계와 검사를 근거로 한 평가이며, 원본 리니지2 바이너리와의 일치도나 실서비스 최대 동접을 판정한 것은 아닙니다.

## 확인한 설계

| 항목 | 실제 구현과 의미 | 검증 범위 |
| --- | --- | --- |
| 완료 기반 I/O | NIO.2 완료 통지와 READ pump/WRITE serializer를 분리하고, 연결마다 READ/WRITE 진행 상태를 관리합니다. 즉시 완료되는 콜백도 재귀 호출이 깊어지지 않도록 처리합니다. | 기존 회귀 238개 검사에 분할 수신·즉시/중첩 완료·부분 WRITE 포함. |
| 객체별 실행 순서 | 소켓은 512슬롯 ticket serializer, 캐릭터는 별도 32슬롯 실행기를 사용합니다. 실행 중인 스레드가 토큰을 보유하고, 외부 요청은 예약한 슬롯을 게시합니다. 같은 소유 실행 스택의 재진입은 즉시 실행하는 별도 정책입니다. | 인코딩을 멈춘 첫 번째 생산자보다 나중 생산자가 먼저 전송되지 않는 것, 8개 생산 스레드의 전송 24,000건을 확인했습니다. 캐릭터 실행기는 소스 검토 범위입니다. |
| 버퍼 수명 | direct buffer와 Lease/WriteBuffer를 재사용합니다. RECV 4KiB, SEND 16KiB, 큰 프레임 64KiB로 나누고 슬롯 수를 제한합니다. 종료 통지는 진행 중인 I/O와 생산자 예약이 끝난 뒤 발생합니다. | close 중 인코딩, close 뒤 늦은 READ/WRITE 완료의 두 순서, direct frame 취소, 큰 SEND의 반환을 확인했습니다. |
| 밀림 제어 | 프레임 수와 wire byte 수로 수락을 제한합니다. 상·하한이 다른 워터마크로 수신을 중단/재개하고, 전송이 쌓였을 때 이미 수신한 패킷을 보관합니다. | SEND 정체 후 buffered READ 재개와 풀 고갈 시 명시적 수락 실패를 확인했습니다. |
| 전송 비용 | owner-local burst, 작은 패킷 packing, 최대 8개 gather WRITE, lazy buffer allocation을 조합합니다. 전체 경로가 무할당/무복사라는 뜻은 아닙니다. | packed/gather 전송의 패킷 순서와 독립적인 참조 암호화 구현의 바이트 결과를 비교했습니다. |
| 타이머/구세션 방어 | timing wheel, 세션 stamp/actor epoch, 포화된 실행기로 전달하지 못한 타이머의 재등록을 사용합니다. | 소스 흐름 확인. 타이머 전체에 대한 실제 게임 부하 검증은 수행하지 않았습니다. |

소켓/캐릭터 단위 직렬 실행은 특정 고정 스레드에 모든 일을 묶는 방식과 구분해야 합니다. 완료 스레드에서 핸들러가 실행된다는 사실만으로 잘못된 설계라고 판단할 근거는 없습니다. 이 구조는 추가 작업 전달 비용을 줄이는 선택이며, 오래 실행하는 핸들러가 공유 워커를 점유하는 정도는 부하 측정으로 평가해야 합니다.

슬롯 풀 일부는 CAS 기반이지만, 코어에는 CYieldLock과 synchronized도 있습니다. 전체를 lock-free 또는 wait-free라고 분류하지 않습니다. 클래스 줄 수는 품질 등급의 근거로 사용하지 않았습니다.

## 확인한 문제

### P2 — I/O 워커 설정과 실제 개수·로그가 불일치

- `src/l1j/server/Server.java:64`는 `cio.ioWorkers`를 최대 32개로 읽습니다. 같은 파일 79행에서 요청한 값을 그대로 출력합니다.
- `src/l1j/server/server/model/network/core/CIOServer.java:62`는 다시 `min(요청값, 8, CPU 수)`로 제한합니다.
- 실제 CIOServer를 요청값 32로 생성했을 때, 이 환경의 CPU 수 4개에 따라 생성된 완료 워커가 4개임을 확인했습니다.
- 영향: 운영자가 출력된 워커 수를 실제 값으로 오해하고 튜닝할 수 있습니다. 8개 상한 자체가 성능 결함이라는 판정은 아닙니다.
- 권장: 실제 적용한 개수를 노출하고 로그에 출력하며, CPU/8개 상한 정책을 설정 설명과 일치시킵니다.

### P2 — MaxPacketsPerCompletion이 현재 READ 처리 개수를 제한하지 않음

- `src/l1j/server/server/model/network/utill/ConnectionConfig.java:41`은 한 수신 완료의 최대 프레임 수로 설명하며 기본값은 32입니다.
- `src/l1j/server/server/model/network/core/CIOUserSocket.java:1193`은 버퍼에 완성된 프레임이 있는 동안 처리하고, 이 설정을 읽어 개수를 제한하지 않습니다.
- 현재 남은 설정 참조는 `CIOThreadCallback.java:516`의 ingress capacity 정규화입니다.
- 제어한 I/O 완료 1회, 입력 1,000바이트에서 핸들러 100개가 실행되는 것을 확인했습니다. 테스트용 패킷 속도 제한은 높게 설정했습니다.
- 영향: 운영자가 이 옵션으로 수신 완료 한 번의 작업량·공정성을 조절할 수 없습니다. 실제 다른 연결의 지연 증가까지 측정한 결과는 아닙니다.
- 권장: 처리량 제한을 구현하려면 버퍼 보존·다음 실행 예약·암호화 상태를 함께 검증해야 합니다. 현재 정책을 유지한다면 옵션 설명과 사용 범위를 정리해야 합니다.

## 이번에 실행한 검사

- 기존 실제 transport/buffer/cipher 회귀: 238개 단언 통과.
- 추가 코어 검토: 188개 단언 통과. 8개 생산 스레드로 총 24,000건 전송, 슬롯 할당·반환 총 80,000회 포함.
- 실제 CIOServer 생성으로 워커 개수 확인: 2개 단언 통과. 임시 로컬 리스너는 검사 후 종료했습니다.
- 새 검사 대상 소스의 SHA-256과 실행 기록을 대조했습니다. 게임 핸들러, 세션과 일부 의존성은 분리한 테스트 구현입니다.
- 전송 경쟁 검사는 동시에 미완료인 SEND를 8개로 제한했습니다. 수락 성공은 전송 완료를 뜻하지 않습니다. 무제한 생산에서도 전부 수락해야 한다는 초기 하네스 가정은 bounded queue 계약과 맞지 않아 수정했고, 그 실행 기록은 별도 보존했습니다.

추가 검사에서 예약한 전송의 순서 훼손이나 위 종료 시나리오의 조기 버퍼 반환은 재현되지 않았습니다. 임의의 모든 스레드 순서가 안전하다는 증명이나 실제 동접 부하 시험은 아닙니다.

작업 공간의 실행/증거:

```text
tests/network/run.py --label network-review-regression
tests/network/run.py --review --label network-core-review
tests/network/run.py --review --loopback --label network-core-workers
validation/network-review-regression/regression.json
validation/network-core-review/review.json
validation/network-core-workers/review-loopback.json
```

## 원본과 이번 검토의 범위

장점으로 평가한 CIOBufferPool, CIOSlotPool, CIOServer, CIOSocketFactory, CThreadLocalTimer, Cryptor, CCreatureController는 처음 받은 원본과 현재 파일이 동일함을 확인했습니다. CIOUserSocket의 v4 변경은 앞서 공개한 수신 경로 정리이며, 위 핵심 실행 모델을 새로 만든 변경이 아닙니다.

이번 검토에서 게임 소스·SQL을 추가로 수정하지 않았습니다. 현재 work의 모든 배포 파일은 공개한 `RKern_full_source_v4_network.zip`과 바이트 단위로 동일합니다.

실제 게임 핸들러를 포함한 높은 동접 부하, 장시간 실행, 느린 클라이언트·회선 단절이 섞인 지연 분포, OS별 NIO.2 동작, 원본 바이너리와의 동작 일치도는 별도 검증이 필요합니다. 이전 v4 합성 측정에서도 v3 대비 속도 향상은 입증되지 않았습니다.
