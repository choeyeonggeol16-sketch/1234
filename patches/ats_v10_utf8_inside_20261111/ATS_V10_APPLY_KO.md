# ATS V10 — 원 안의 ATS 표시 / Java 한글 UTF-8 정리

참고 영상처럼 **ATS 글자를 원 안에 고정**하고 둘레 화살표만 회전합니다. 남은 시간은 원 아래의 별도 글자입니다. V9의 창 밖 클릭·포커스 상실 중단 수정은 유지합니다.

![V10 HUD 미리보기](preview/hud-v10.gif)

같은 HUD 소스의 Wine64 미리보기입니다. 실제 게임 화면이 아니며 한국어는 Noto Serif CJK KR 대체 글꼴입니다.

## 한글이 깨져 보이던 원인과 변경

기존 `AutoHuntController.java`는 CP949였습니다. CP949로 읽으면 `자동 사냥을 종료 합니다.` 등의 한글이 정상이며, UTF-8로 열면 깨져 보이는 상태였습니다.

V10 전체 소스의 `src/`와 `tests/` Java 파일은 UTF-8(BOM 없음)로 통일했습니다. 982개 Java 파일 중 CP949 533개를 엄격하게 디코딩해 변환했습니다. **모든 운영 서버 Java 971개의 변환 전후 유니코드 문자열이 동일함을 검사했습니다.** 한글 식별자·게임 메시지·서버 로직은 바꾸지 않았습니다.

함께 포함한 설정:

- `.settings/org.eclipse.core.resources.prefs`: Eclipse 프로젝트와 컨트롤러의 UTF-8 지정.
- `.editorconfig`: Java 파일 UTF-8 지정.
- `javac-utf8.args`: javac의 `-encoding UTF-8` 옵션.
- `tests/run.sh`, `tests/ParseJava.java`: UTF-8로 컴파일·파싱하도록 갱신.

### 서버 소스 적용

1. 기존 소스를 백업합니다.
2. **전체 소스 ZIP의 `src/`를 함께 반영**합니다. Eclipse 설정은 기존 `.settings/org.eclipse.core.resources.prefs`에 위 UTF-8 항목을 병합합니다. 기존 파일별 MS949/CP949 지정이 있다면 해당 Java 파일도 UTF-8로 맞춥니다.
3. Eclipse에서는 Refresh 후 프로젝트 Text file encoding이 UTF-8인지 확인하고 Clean/Build합니다. IntelliJ/VS Code도 프로젝트 Java 소스 인코딩을 UTF-8로 설정합니다.
4. 외부 Ant/Maven/명령줄 빌드를 쓰면 그 빌드의 **소스 인코딩도 UTF-8**로 맞춥니다. 이 배포본에는 원 프로젝트의 `build.xml`/`pom.xml`이 없어 외부 빌드 파일 자체를 수정하지는 못했습니다.

```xml
<!-- Ant: 기존 javac 작업의 속성 -->
<javac ... encoding="UTF-8" ... />

<!-- Maven: 기존 properties에 추가 -->
<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
```

명령줄 javac는 `-encoding UTF-8` 또는 `@javac-utf8.args`를 사용합니다. 게임 실행 JVM·패킷·DB 인코딩 설정은 이번 변경 대상이 아닙니다.

V9 서버를 이미 적용한 경우 서버 기능은 동일해서 **HUD 수정만 쓰려면 서버 재빌드가 필요 없습니다.** 소스 편집/재빌드에는 위 UTF-8 설정을 사용하세요. V7/V8 서버는 V9의 중단 수정도 적용되도록 이 전체 소스를 반영하고 재빌드해야 합니다. V6 이하 신규 설치는 `ATS_V7_APPLY_KO.md`의 ATS 서버/DB 적용도 필요합니다.

## DLL 적용

1. 게임을 종료합니다.
2. `client-ready/dinput.dll`과 `client-ready/ats_bg.png`를 Mega.bin 옆에 복사합니다. 클라이언트 UI ZIP에는 루트에 있습니다.
3. 기존 `ats_hud.ini`의 위치·전체 크기는 보존해도 됩니다. `ShowSeconds=0`, `TextScalePercent=90`이 현재 기본값입니다.
4. 게임을 켜고 DLL 로그의 `native-tile-input-v10-ats-inside-20261111`을 확인합니다.

원 안의 ATS 글자는 고정하고 원의 화살표가 회전합니다. 원 아래에는 `남은시간: 19시간 33분` 형태로 표시합니다. 시간·분 표시는 `ShowSeconds=0`, 초까지 보려면 `1`입니다. 시간은 내부에서 초 단위로 차감하며 `TextScalePercent=90`은 시간 글씨 크기만 조절합니다.

V9의 수동 중단 분류와 무응답 경로 재계획은 그대로입니다. DB 변경은 없고 기존 V7/V8/V9 `character_ats_time` 잔액과 레벨·맵 제한을 유지합니다.

## 검증

- 배포용 x86 PE32 DLL/미리보기 빌드 통과.
- 같은 HUD 소스를 Wine64에서 실행해 ATS가 원 안에 표시되는 화면과 회전 애니메이션 확인.
- 서버 Java 971개 UTF-8 구문 파싱 통과. 전체 의존성 연결 빌드를 의미하지 않습니다.
- Java 세션의 이동/무응답 재시도/중단 회귀와 ATS 잔액·조건 검사를 UTF-8 빌드로 재실행해 통과.
- 운영 서버 Java의 유니코드 내용, DB/서버 설정, 네이티브 이동 코어/좌표 검사/네트워크가 V9와 같음을 확인.
- 새 DLL의 Windows Mega.bin 실게임, 전체 서버 의존성 연결 빌드, 실제 MySQL과 독점 전체 화면은 미검증입니다.

현재 검증은 `validation/`, V9 검증은 `historical-v9/validation/`에 있습니다.
