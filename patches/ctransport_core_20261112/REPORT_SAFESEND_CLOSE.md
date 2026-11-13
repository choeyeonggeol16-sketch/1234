# SafeSend 문장 송신 제한과 종료 사유 전달

2026-11-13. `32b7f46`의 분석 보고서에 제시한 두 항목을 L1에 적용했다. 원본 근거는 `SafeSend 0x9ef41c`, 용량 검사 `0x9ef620`, 문장 호출 3곳의 margin `0x80000`, `ForceClose 0x9fcbf0`의 사유 필드 저장이다. L2 패킷 규약이나 확인되지 않은 enum 번호를 이식하지 않았다.

## 동작 변경

**혈맹 마크(`SC_Emblem`)만 선택적 송신을 사용한다.** `beginSafeDirectFrame(size, margin)`은 outstanding+reserved와 margin/실제 크기를 같은 socket lock 안에서 검사하고 실제 FIFO·바이트 예약까지 수행한다. margin은 원본 호출과 같은 512KiB다. 대기량 1.5MiB까지 허용하고, 이를 초과하면 마크 전송은 false로 끝나며 연결을 종료하지 않는다. 동시에 예약 중인 인코더도 대기량에 포함한다.

선택적 경로의 바이트·물리 큐·serializer·pool 부족은 연결을 유지한다. 인코딩 전 거절은 ticket/lease/cipher를 소비하지 않으며, pool 획득 실패처럼 이미 예약한 경우에는 바이트와 ticket을 원래 FIFO 순서로 반납한다. 선택적 frame의 명시적 abort도 연결을 유지하고, pool 재사용 시 optional 표시를 초기화한다. 거절 횟수는 별도 카운터로 집계해 마지막 필수 송신 실패 사유를 오염시키지 않는다. 매니저 소켓 보고서에서 `선택적 송신 생략`으로 조회한다.

정상 마크의 wire 형식과 암호화 순서는 동일하다. wire 9바이트를 포함해 65535를 넘는 파일은 보내지 않는다. 생략한 마크의 자동 재전송 큐를 만들지는 않았다. 클라이언트가 다시 요청하면 그 시점의 여유에 따라 처리한다. 이동·거래 등 필수 패킷은 기존 hard-cap·실패 시 종료 정책을 유지한다. 인코딩 오류나 이미 종료 중인 연결을 정상 송신으로 취급하지 않는다.

**종료 기록에는 Java 이유와 native 상태를 따로 보관한다.** native가 이미 보관하는 최초 operation/error를 `CIOServer.onClosing/onClose` → `CSocket` → `CUserSocket` → 기존 `CNetworkDiagnostics.Trace`로 전달한다. owner가 늦게 연결되어도 상태를 전달하며, 늦은 취소·terminal 콜백이 최초 상태를 덮어쓰지 않는다. native terminal/packet lease/game cleanup fence와 종료 순서는 유지한다.

Java의 송신 한도/거절/pool/인코딩/패킷 실행·정책 오류, graceful 요청 및 Java deadline 판단에는 해당 이유를 기록한다. 별도 사유가 없는 일반 local close는 `SERVER_CLOSE`로 남긴다. native 종료 뒤 Java가 정리하면서 호출한 일반 close는 서버가 먼저 끊은 것으로 표시하지 않는다.

매니저의 **소켓** 및 **종료 사유·최근 패킷 128개** 조회에 다음 필드가 나타난다.

```text
javaReason=SEND_LIMIT nativeCause=TRANSPORT_CLOSE operation=5 error=0
javaReason=NONE nativeCause=PEER_EOF operation=2 error=0
javaReason=NONE nativeCause=PROTOCOL_ERROR operation=2 error=13
```

위 세 줄은 필드 해석 예시다. nativeCause는 기존 native callback의 operation/error로 분류한다. `TIMEOUT`은 Winsock 10060, `READ_ERROR`/`WRITE_ERROR`는 해당 I/O 상태를 뜻한다. native 내부에서 먼저 만료된 시간 제한은 `TIMEOUT`으로 표시하며 로그인/부분 수신의 세부 종류를 추측하지 않는다. 모든 기존 close 호출부에 세부 사유를 붙였다는 의미도 아니다. Java 이유와 native 상태는 별도 관측값이며 하나의 전역 사건 순서를 보장하는 로그가 아니다.

기존 최근 패킷 128개, 종료 기록 최대64, 연결 진단 상한과 비활성 설정을 재사용한다. 본문·비밀번호·채팅·게임 객체를 추가 보관하지 않는다. 진단 비활성 시 Trace를 만들지 않는다. DB/SQL 및 새 config는 필요 없다.

## 검증과 배포

전체 Java 1709개 컴파일, 기존 진단/방송 계약, provider copy/close 경쟁, late owner bind/취소 시 최초 상태 보존을 로컬에서 검사했다. `tests/CUserSocketSafeSendWindowsTest.java`는 production CUserSocket/frame/pool/마크 인코더와 실제 DLL/FFM을 사용하는 Windows 검사다. 실제 1.5MiB 예약으로 경계값, 마크 거절의 FIFO/lease 불변, plain-first/암호화 순서, 16개 동시 생산자, pool 부족 rollback, 필수 전송의 기존 종료 정책, EOF/잘못된 프레임/RST와 종료 기록을 검사한다. 최종 Windows 결과와 실행 커밋은 `WINDOWS_VERIFICATION.json` 및 `logs/safesend-windows-actual.log`에 고정한다.

소스 변경은 CUserSocket/CIOServer/CSocket/CIOFrame/CNetworkDiagnostics/SC_Emblem/chocco의 7개다. `verify_safesend_scope.py`로 이전 전체 src의 나머지 **1703개 파일이 byte-identical**인지 검사한다. native C++·CTransport.dll·코덱·독립 PSS에는 변경이 없다. ABI는 `0xa0000`, exports는67개다. DLL 재빌드나 새 SQL 없이 새 전체 src를 clean build해 적용한다. 배포 ZIP은 소스와 검증 자료를 함께 갱신한다.

실제 게임 클라이언트·운영 DB·장시간 실게임 부하 및 Swing 창을 직접 클릭하는 검사는 이번 검증 범위에 포함하지 않았다. 기존 Windows의 bounded loopback 처리량은 최고 동접이나 실게임 성능으로 해석하지 않는다.
