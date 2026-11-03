# LC 초기 실행 확인

이 도구는 Windows에서 정상 실행되는 LC.exe의 파일 정보, 로딩된 DLL 메타데이터,
TCP 접속 주소·포트를 수집합니다. 클라이언트와 서버에 새 접속 설정을 적용하는 도구는 아닙니다.
명령줄·인증 토큰·비밀번호·패킷 내용·메모리 덤프·EXE/DLL 본문은 수집하지 않습니다.

1. ZIP을 풀고 `LC_First_Check.ps1`이 있는 폴더의 주소창에 `powershell`을 입력해 엽니다.
2. 다음 명령을 실행합니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\LC_First_Check.ps1
```

이 명령의 실행 정책은 해당 PowerShell 프로세스에만 적용됩니다.

3. 120초 동안 수집하는 사이에 평소처럼 퍼플에서 게임을 실행합니다.
4. 종료 후 바탕화면의 `LC_check_날짜_시간_식별자.zip`을 전달합니다.
   실행이 오래 걸리면 명령 뒤에 `-Seconds 300`을 붙일 수 있습니다.

결과에 들어가는 파일:

- `LC_report.json`: 실행 중 LC.exe의 버전·해시, DLL 이름/주소, TCP 접속 주소와 포트.
- `client_files_프로세스번호.json`: 실제 클라이언트 폴더 바로 아래의 파일 이름·크기.

이후 수집된 접속 경로를 기준으로 초기 통신 형식을 확인하고 현재 서버팩과 비교합니다.
실행 직후 종료돼 LC.exe가 포착되지 않았다면 그 사실과 종료 화면의 문구도 함께 알려주세요.
여기서는 Windows 실행 검증을 하지 못했으며 모듈 접근 제한 등은 결과 Notes에 기록합니다.
