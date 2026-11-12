# 게임 CTransport ABI3 / PSS 독립 연결 재활성화

게임 접속은 기존 CTransport.dll ABI3(8 native workers)를 유지합니다. PSS 설정창 제어는 전용 포트와 독립 NIO2 그룹2개 스레드·만료 스레드1개를 사용합니다. 게임 전송 엔진/풀/타이머에 합치지 않습니다.

## 적용

- 기존 src를 전체 src ZIP의 src로 교체하고 clean build합니다. Java21 preview/native-access와 기존 CIOPacketCodec.dll을 유지합니다. 추가 SQL 없음.
- 게임용 CTransport.dll은 수정하지 않았습니다. SHA256 d149b3bd1835eb966ff590eecdb34890ca8798e033c43a58c498b0bee586e827.
- 서버 시작 시 PSS listener를 별도로 활성화합니다. 기본 포트는 GAME_SERVER_PORT+1(게임5000이면PSS5001). JVM -Dpss.uiBridge.port=5001로 지정할 수 있으며 게임 포트와 같으면 거절합니다.
- 게임 listener 종료는 PSS listener를 종료하지 않습니다. PSS bind 실패도 게임 기동을 막지 않습니다. 프로세스 종료 시 PSS 전용 shutdown hook이 정리합니다.
- PSS는 기존16-byte 토큰 등록/HELLO/BOUND/PING/OPEN을 사용합니다. 설정서 OPEN은 게임 연결의 SC_PssNativeWindow를 사용하지 않습니다. 사냥 시작·종료·설정 게임 프로토콜/로직은 수정하지 않았습니다.

## 클라이언트

기존 독립 연결용 Pss2019.dll과 소스를 byte-for-byte 재사용합니다. 소스/설정창/사냥 코드는 변경하지 않았습니다. 기존 DLL이 이미 독립 연결용이면 그대로 사용합니다. 게임 연결 전용(in-band) DLL을 쓰고 있다면 함께 제공한 독립 연결용 기존 DLL이 필요합니다.

원본 companion DLL SHA256:35b476815c56b578e5e12a3d500589e72220ec6493bbc0041da541c588b861fa.
기존 Pss2019.ini를 유지하고 [PssBridge] GamePort/WindowPort를 서버와 맞춥니다. 포트5001은 별도 TCP 연결입니다. 클라이언트 프로토콜 등록만 인증된 게임 세션에서 수행하며 창 제어는 전용 연결입니다.

## 검사

생산 PssWindowTransport의 실제 TCP9개 계약 그룹, 생산 PlaySupportWindow의 시작/토큰 등록/설정서 OPEN/로그아웃/재시작/포트 충돌 격리를 검사합니다. 게임 세계 객체만 검사 대역입니다. 전체 Java1,698개 컴파일과 Windows에서 기존 CTransport/새 독립 PSS/원본 PE32 client loader 검사를 수행합니다.

실제 게임 클라이언트의 창 렌더링·자동사냥·운영 DB·장시간 부하는 이 검사에 포함되지 않습니다. 최신 Windows 결과는 WINDOWS_VERIFICATION.json과 GitHub 실행 기록을 확인합니다.

실제 Windows Server2025/Temurin21에서 게임 DLL·생산 FFM·독립 PSS9개 TCP 그룹·생산 PSS 활성화/등록/종료/재시작·원본 PE32 클라이언트 로딩까지 모두 통과했습니다: [실행43283920839](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43283920839). 검사 소스d3073f5, 전체 src SHA256:e7abd02679f3422b5748aa0a06df4f108653e6f5148c57d708a1775a6fb11576.
