PSS 2019 / Ctrl+Z 앱센터 URL 연결 시험

대상: mjlin.bin / 19.02.15.1002 / PE32 x86 / ImageBase 0x400000

이번 기능:
- 2019 클라이언트의 기존 Ctrl+Z 앱센터 창 및 기존 웹뷰를 유지
- AppCenterUI의 0xBEBB80 홈 URL 호출 지점에서 주소 포인터만 재지정
- 기존 URL: http://g.lineage.plaync.com/ingame/index
- 기본 테스트 페이지: Pss2019AppCenter.html (DLL과 같은 폴더)
- HTML 안에 '앱센터 연결 성공' 표시
- 설치된 PSS·서버·DB·게임 인증 처리는 변경하지 않음
- 게임 클라이언트 EXE를 변경하거나 새로운 접속기를 요구하지 않음

설치:
1. 게임을 완전히 종료합니다.
2. 기존 Pss2019.dll을 백업합니다.
3. Pss2019.dll과 Pss2019AppCenter.html을 같은 게임 폴더에 복사합니다.
4. 기존 Pss2019.ini는 유지합니다.
5. 캐릭터 접속 후 Ctrl+Z로 앱센터를 엽니다.
6. 페이지에 '앱센터 연결 성공'이 표시되는지 확인합니다.

선택 사항 - 자체 서버 앱센터 주소 설정:
Pss2019.ini에 다음 섹션을 추가합니다. 현재 실제 주소가 없으므로 아래는 예시이며 그대로 사용하면 안 됩니다.

[AppCenter]
Enabled=1
; Url=https://<형 서버의 실제 HTTPS 도메인>/appcenter/

Url 항목을 생략하면 DLL과 같은 폴더의 로컬 HTML을 열도록 시도합니다.
Url을 입력할 때는 http:// 또는 https://로 시작하는 실제 웹페이지 주소를 사용합니다.
본 게임 서버 포트는 웹페이지를 제공하지 않으므로, 앱센터 페이지용 HTTP(S) 엔드포인트는 별도로 필요합니다.

동작 경계:
- 확인한 정확한 홈 호출: 0xBEBB80, push imm32 at 0xBEBBBE, Navigate call 0xC2B290.
- 패치 전 관련 코드 바이트 및 원본 URL 문자열을 확인합니다.
- 코드가 다르거나 페이지가 없으면 원본 주소를 변경하지 않습니다.
- Ctrl+Z 첫 열기에서 반드시 해당 함수를 호출하는지는 Windows 실게임 검증이 필요합니다.
- 앱센터 인증·SSO·게임 로그인, HTTP 세션 공유, 서버 API는 수정하지 않았습니다.
- 이 테스트는 기존 앱센터 홈 URL 경로만 검증합니다. Ctrl+Z에서 다른 URL 경로가 실행되면 별도 확인이 필요합니다.
- 기존 HP/MP 구현은 소스에서 변경하지 않았습니다. 사용하지 않는 경우 기존 INI의 [VitalsHud] Enabled=0을 적용할 수 있습니다.
- 디버그 로그: PSS2019_trace.txt에서 'AppCenter' 문자열 확인.

주의: 앱센터 최초 표시 및 파일 URL 지원 여부는 현재 Windows 실제 클라이언트에서 아직 검증되지 않았습니다.
