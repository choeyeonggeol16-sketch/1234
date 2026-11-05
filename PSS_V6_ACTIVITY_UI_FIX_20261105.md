PSS V6 — 실행 표시 초기화 수정 / 전체화면 DirectDraw 표시 시험본

게임을 완전히 종료하고 기존 DLL을 백업한 뒤 Pss2019.dll만 교체하세요.
기존 Pss2019.ini는 유지하세요. 이미 자동 로딩을 적용했다면 Apply 재실행은 필요 없습니다.
V5 서버를 적용한 상태라면 이번 UI 변경으로 서버를 다시 빌드할 필요는 없습니다.
새 DB 테이블이나 새 아이템은 필요 없습니다. 기존 아이템 900000901을 사용합니다.

확인한 버그: 자동 연결은 작업 스레드에서 시작했는데 표시 초기화는 게임 UI 스레드만
허용하여, V5가 로드되어도 표시가 생성되지 않았습니다. 실제 게임 메시지 스레드로
초기화를 옮겼습니다. 회전 표시를 주황색 원 안의 ▶와 '플레이 서포트 동작 중'으로 바꿨습니다.
이미지는 한 번 만들고 재사용하여 매 프레임 GDI 객체를 생성하지 않습니다. ON 버튼은 없습니다.

전체화면에서는 외부 창 대신 DirectDraw의 프레임 복사/뒤집기 직전에 그립니다.
기존 클라이언트 UI 리소스를 추출한 것은 아니며 모양과 위치는 참조 이미지와 차이가 있습니다.
게임 화면 중앙 기준 배치로 캐릭터 렌더 좌표에 직접 연결한 방식은 아닙니다.
실제 클라이언트의 DirectDraw 인터페이스 연결 및 전체화면 표시 여부는 게임에서 시험해야 합니다.
창모드용 클릭 통과 표시도 보조 경로로 유지했습니다.

시작 로그:
PSS V6 UI-thread initialization fix loaded
PSS activity initialized on the game UI thread
PSS native DirectDraw frame hook installed; awaiting presentation

실행 중 그리기 성공 시:
PSS native DirectDraw activity painted into the game frame
이 로그는 그리기 호출의 성공이며 실제 화면 표시를 보증하는 응답은 아닙니다.
'hook unavailable'이 나오면 전체화면 그리기 연결에 실패한 것입니다.

기존 INI의 설정은 유지합니다. 표시를 끈 경우만 Enabled=1로 수정하세요.
[PssIndicator]
Enabled=1
OffsetX=0
OffsetY=20
OffsetY를 줄이면 위로 이동합니다. 게임을 다시 실행하면 적용됩니다.

이번 변경은 표시 수정입니다. 아이템 사용 확인창이 계속 뜨는 원인은 아직 확정하지 못했습니다.
기존 V5 ItemTable의 사용 형식 30과 직접 창 열기 처리는 포함하지만, 확인창 제거가 검증됐다고
볼 수 없습니다. DB 아이템 재생성이나 다른 사용 형식으로의 임의 변경은 필요하지 않습니다.

통합본 src는 마지막 업로드한 1,688 Java 소스 기준으로 V5와 동일합니다.
일일 이용시간/DB 정산 설정 코드는 그대로 보존했습니다. 상세 결과는 validation을 참고하세요.
스레드 초기화, COM 전송 경로 및 최종 DLL 로드 검사를 통과했습니다.
실제 Windows 전체화면 표시 시험은 완료하지 못했습니다.
