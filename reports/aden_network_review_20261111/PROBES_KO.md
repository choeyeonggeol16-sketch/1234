# 로컬 재현 검사 실행 방법

이 검사는 서버를 실행하거나 게임 서버에 접속하지 않습니다. 사용자 원본 Java 중 검사 대상 클래스를 임시 디렉터리에 복사해 컴파일합니다. native DLL·게임 실행기를 대신하는 최소 가짜 구현을 사용합니다. 전체 서버 빌드나 Windows IOCP 실행 검증을 대체하지 않습니다.

## 준비

Python 3.10 이상, OpenJDK 21이 필요합니다. 검사 스크립트는 임시 디렉터리에만 검사 클래스와 가짜 구현을 만듭니다. 검사할 원본 소스는 다음처럼 배치합니다.

```text
보고서폴더/
  input/
    src/                       # aden.zip 안 src.zip을 추출한 원본
      l1j/server/server/...
  probes/
    run_source_probes.py
    run_await_probe.py
  logs/
```

`CIOWinTransport.dll`과 `CIOPacketCodec.dll`은 이 검사에 필요하지 않습니다.

## 실행

```bash
python3 probes/run_source_probes.py
python3 probes/run_await_probe.py
```

스크립트가 `java -m jdk.compiler/com.sun.tools.javac.Main`으로 컴파일합니다. JRE만 설치되어 해당 compiler module이 없으면 JDK 21을 설치해야 합니다. FFM 검사에는 Java 21 preview/native-access 옵션을 사용합니다. 실행 환경에서 `--release 21`용 ct.sym이 없어도 실행할 수 있도록 `-source 21 -target 21`을 사용했습니다.

## 결과 해석

- `stash-source-probe.log`: 원본 `CIOWinSocket`의 부분 stash 읽기/추가 수신에서 바이트가 달라지는 현상을 관찰합니다. 가짜 pause 구현으로 특정 콜백 순서를 만들었으므로 실제 DLL에서 발생하는 빈도를 의미하지 않습니다.
- `stash-suggested-fix-probe.log`: 임시 복사본의 재배치 조건 하나만 바꿔 동일 입력을 다시 검사합니다. 기대 바이트와 전체 결과가 같으면 PASS입니다. 원본 파일은 수정하지 않습니다.
- `registry-source-probe.log`: 원본 `CIOWinServer` 클래스의 실제 register/find/forget을 reflection으로 실행합니다. 생성자는 native DLL을 로드하므로 Unsafe로 생성을 우회하고 테이블 필드만 설정합니다. live 연결 0개에서 null sentinel이 소진되는 상태를 확인합니다. 실행시간 성능 벤치마크가 아닙니다.
- `await-source-probe.log`: 원본 `CIOServer.awaitTermination`에 가짜 native lifecycle을 붙여 종료 대기 동작을 확인합니다. 이 클래스도 생성자를 우회합니다.

`REPRODUCED`는 원본의 보고된 동작이 재현됐다는 뜻입니다. 해당 항목이 수정돼 통과했다는 의미는 아닙니다. 스크립트는 그 동작이 사라지면 재현 실패로 종료할 수 있습니다. `PASS suggested fix`만 수정 제안이 검사용 입력을 보존했다는 의미입니다.

실제 DLL 내부의 partial frame·복호화·pause·메모리 수명, 실제 PC/인벤토리와 로그아웃 경쟁은 별도의 Windows 통합 검사가 필요합니다.
