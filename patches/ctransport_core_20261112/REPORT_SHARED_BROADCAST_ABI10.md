# CTransport ABI10: 원본 공유 평문 방송을 L1 FIFO에 연결

2026-11-13. 구현 기준 `9255cca609b204c5e58ac8ae4bffe50863188c07`.

**원본에서 확인한 “평문 한 번 작성 → 여러 수신자가 참조 → 소켓별 암호화 출력”을 구현했다. 액션·이펙트 방송에 적용했으며, 기존 Java 송신 FIFO와 L1 코덱을 사용한다.** 실제 Windows 검증을 통과했다. 운영 환경의 무결함이나 성능 향상폭을 보장하는 결과는 아니다.

## 변경 내용

- 실제 원본 이름인 `CAsyncSendConstBuffer`, `CAsyncSendConstBufferExceptSelf`를 Java 방송 역할에 사용한다. source 객체 ID로 자신을 제외하며, 기본 클래스의 자기 자신에 대한 방송 대기량 검사 우회도 지원한다.
- `SC_DoActionGFX`의 두 방송 진입점, `SC_SkillSound`의 일반/fast/M/wide 및 연결된 packed 방송에서 평문 프레임을 한 번 작성한다. M의 이펙트 설정, wide의 범위 50, 기존 맵/화면 조회와 자기 전송 규칙을 보존한다.
- 기존 native `CIOBufferEx<16384>` 풀에 입력을 한 번 복사해 고정한다. 각 네이티브 요청은 같은 블록의 참조를 유지한다. 이 경로는 수신자별 입력 vector 저장 공간을 할당하거나 복사하지 않는다.
- Java 쪽도 기존의 제한된 송신 풀에서 수신자별 큐 항목을 빌린다. 공유 항목은 직접 버퍼를 추가 할당하지 않고 읽기 전용 뷰를 사용한다. 큐 항목/뷰와 소켓별 암호화 출력은 여전히 필요하다.
- 각 수신자의 `SEND_ENQUEUE`가 기존 FIFO ticket을 사용한다. 이미 예약했지만 작성 중인 앞 패킷을 추월하지 않는다.
- 공유 입력은 변경 가능한 packet packing에 넣지 않으며, 일반 gather와의 경계를 유지한다. 일반 송신/gather, 65,535바이트 프레임 및 16 KiB 블록을 넘는 기존 const 송신은 유지한다.

## 참조 수명과 실패 처리

```text
방송 생성자: source 참조 1개
  └─ 수신자 FIFO 항목마다 Java 참조 유지
       └─ 순서가 되었을 때 참조를 FFM 호출에 이전
            ├─ native 요청이 CIOBufferEx 블록을 직접 retain
            ├─ 호출 복귀 후 Java 수신자 참조 반환
            └─ native 직렬 실행에서 소켓 출력으로 암호화/복사 후 블록 참조 반환
방송 순회 종료: 생성자 참조 반환
```

따라서 원본처럼 **공유 네이티브 입력은 wire 완료까지 기다릴 필요가 없다.** 나중에 도착하는 부분 송신/완료는 독립적인 소켓 출력 큐가 처리한다. Java의 읽기 전용 뷰는 JVM 배열을 참조하며, 해제된 네이티브 입력 주소를 읽지 않는다.

직렬 큐 포화, 잘못된 프레임, 닫힌 소켓, 생성/등록 실패 때 참조와 예약량을 회수한다. 원본 방송 가상 함수에서 확인하지 못한 `AddExecutable` 실패 후 선행 참조 회수도 명시적으로 처리한다.

FFM downcall 중 close가 발생해도 원본 입력 핸들은 호출 복귀까지 유지된다. 아직 발행 중인 방송 생성자가 남아 있으면 서버/DLL 해제를 기다린다. 종료 예산이 끝나면 메모리를 보존한 채 오류를 반환하고, 핸들을 반환한 후 종료를 재시도할 수 있다. 이 경로도 Windows에서 검증했다.

## L1에 유지한 정책

- 실제 L1 암호키와 프레임별 키 갱신을 사용한다. 공유 평문을 직접 암호화하지 않는다.
- 일반 `ServerPacket`/`WritablePacket`은 기존의 수신자별 작성 경로를 유지한다. 수신자별 내용이 달라질 수 있기 때문이다.
- 초기 handshake, 다른 서버 인스턴스, 공유 입력 풀 가용성 때문에 공유가 불가능하면 기존 직접 작성 경로를 사용한다. **두 경로 모두 동일한 필수 CTransport IOCP 백엔드다.** 다른 네트워크 백엔드로 전환하지 않는다.
- 원본의 방송별 pending limit 인자는 구현했지만, 적용한 L1 액션/이펙트 호출은 `Integer.MAX_VALUE`를 사용한다. L2의 20/512 KiB 생략 정책을 새로 적용하지 않았다. 기존 2 MiB byte 제한, 물리 lease 제한과 거절 정책은 유지한다.
- PSS의 독립 NIO2 연결/활성화와 기존 `CIOPacketCodec.dll`은 변경하지 않았다. 게임 로직·DB 스키마 변경도 없다.

L2 월드 격자나 L2 프로토콜을 통째로 옮긴 결과는 아니다. 바이너리에서 확인한 방송 역할/참조 흐름을 L1의 기존 세계 조회와 Java/native 경계에 맞춘 구현이다.

## 검증 결과

[Windows 실행](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43371588194): Windows Server 2022, Temurin 21.0.13+9, job `131708341702`, **success**.

새 공유 방송 검사에서 확인한 항목:

1. 16 KiB 동일 입력을 서로 다른 L1 rolling key를 가진 수신자들에게 전송하고, 기존 코덱으로 계산한 wire와 일치.
2. IOCP 완료 스레드를 막아 실제 WRITE 완료를 지연한 동안 공유 입력 핸들 수가 0으로 돌아가며, 이후 수신 바이트도 일치.
3. 미발행 앞 encoder → 공유 방송 → 일반 패킷 순서 유지. 자기 ID 수신자 제외.
4. 한 수신자를 닫고 미발행 encoder를 취소해도 다른 수신자 전송과 암호키가 정상.
5. 방송별 대기량 조건, 최종 source/lease 회수, 열린 생성자를 둔 종료의 메모리 보존 및 재시도.

기존 Windows 송수신/프레임·코덱·graceful close·계정/캐릭터 레지스트리·독립 PSS 검사도 통과했다. 기존 세션 검사는 32개 동시 연결로 30,144ms 동안 누적 53,920연결, 6,901,760프레임을 처리하고 레지스트리가 비었다. **최대 동접이나 실게임 부하 수치가 아니다.**

로컬 검증:

- ASan/UBSan 및 TSan 전체 네이티브 검사 통과. 새 검사는 생성자 조기 반환, 입력 불변성, 수신자 취소, 504-slot 큐 포화 거절 시 회수, 503개 대기 요청 취소와 서버 종료 후 핸들 반환을 포함한다.
- Java 21 preview 전체 운영 Java 1,706개 파일 컴파일 통과.
- 기존 single/gather copy-close 경합 검사와 PSS 독립 동작 검사 통과.
- ABI9와 비교해 허용된 기존 소스 8개 수정, 네트워크 클래스 2개 추가. 그 밖의 **1,697개 소스가 바이트 단위로 동일**하다. `tests/verify_abi10_scope.py`로 재검사한다.

실제 게임 클라이언트·운영 DB·장시간 부하, 공유 경로의 성능 향상폭은 측정하지 않았다. 작은 액션/이펙트는 기존에도 직접 작성했으므로, 참조/뷰 비용과 공유 항목의 송신 경계를 포함한 전후 비교가 필요하다.

## 적용

**전체 src와 같은 묶음의 CTransport.dll을 함께 적용하고 clean build한다. ABI9 Jar/DLL과 혼용하지 않는다.** 기존 Java 21 preview/native-access 실행 옵션, `CIOPacketCodec.dll`, PSS 파일과 설정은 유지한다. 추가 SQL은 없다.

- 전체 소스: `downloads/CTransport_Aden_FULL_SRC_20261112.zip`
- DLL·C++ 소스·변경 소스·검사/로그: `downloads/CTransport_CORE_20261112.zip`
- ABI `0xa0000`, exports **67**. 새 연결 함수 3개는 원본 메서드라고 주장하지 않는 `L1Interop_*` 보조 API다.
- DLL SHA-256: `de61cd0f15ec1895dfd91340156b3ba91c880837aeb66e034ab6d21666f29014`
- 원본 동작 근거: `reports/ctransport_shared_send_audit_20261113/REPORT_KO.md`
- 실행 증거와 source ZIP SHA: 패키지의 `WINDOWS_VERIFICATION.json`, `logs/abi10-windows-actual.log`, `SHA256SUMS`.
