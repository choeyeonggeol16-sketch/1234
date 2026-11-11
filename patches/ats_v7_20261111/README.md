# ATS V7 적용 안내

V6 소스를 기준으로 ATS HUD와 서버 이용시간·레벨·맵 제한을 추가한 버전입니다.

## 포함 기능

- 직접 만든 붉은 ATS PNG와 고정된 ATS 글자, 둘레 회전 효과.
- 초 단위 남은 시간 표시. `ShowSeconds=0`이면 영상처럼 시간·분만 표시.
- 자동사냥을 켠 동안만 차감. 정지·사망·접속 종료 시 HUD 숨김.
- 잔여 시간은 캐릭터 ID별 DB 저장. 재접속·다시 시작해도 새로 지급하지 않음.
- 최소·최대 레벨 및 허용 맵 검사. 실행 중 조건을 벗어나도 종료.
- 시간 0, 제어 연결 단절, 시간 저장 오류 발생 시 자동사냥 종료.
- 기존 49027 시작/정지와 V6 이동 엔진 유지.

## 적용 순서

1. 서버를 정지하고 기존 서버 파일·DB·클라이언트 dinput.dll을 백업합니다.
2. 게임 DB에 `db/ATS_V7.sql`을 실행합니다. 전용 `character_ats_time` InnoDB 테이블이 생성됩니다.
3. 서버 폴더에 `config/autohunt_ats.properties`를 복사하고 아래 규칙을 정합니다.
4. 동봉된 변경 Java 소스를 기존 서버 프로젝트에 반영해 **서버를 다시 빌드**합니다.
5. 게임을 종료한 뒤 `client-ready/dinput.dll`, `ats.png`, `ats_hud.ini`를 Mega.bin 옆에 함께 복사합니다.
6. 서버를 실행하고 게임에 접속한 뒤 49027 아이템으로 자동사냥을 시작합니다.

DLL만 바꾸면 서버 시간·제한 기능이 생기지 않습니다. SQL, 설정, 서버 소스, DLL을 함께 적용해야 합니다.
V6의 ‘서버 재빌드 불필요’ 안내는 V7에는 해당하지 않습니다.

## 서버 설정

```properties
Enabled=true
InitialSeconds=86400
MinLevel=1
MaxLevel=0
AllowedMaps=
```

- `InitialSeconds=86400`: 첫 이용 캐릭터에 한 번만 24시간 지급. 일일 지급이 아닙니다.
- `MaxLevel=0`: 상한 없음. 예를 들어 60레벨까지만 허용하려면 `MaxLevel=60`.
- `AllowedMaps=`: 모든 맵 허용. 예를 들어 `AllowedMaps=0,4`면 해당 두 맵만 허용.
- 설정은 자동사냥을 시작할 때 읽습니다. 변경 후 실행 중인 자동사냥을 껐다 켜야 새 규칙이 적용됩니다.
- 시간이 이미 저장된 캐릭터는 InitialSeconds를 바꿔도 다시 지급되지 않습니다.
- 설정 파일 누락·잘못된 값·DB 테이블 누락은 이용시간을 무제한으로 바꾸지 않고 시작을 거부합니다.

## 잔여 시간 조회·지급

자동사냥을 끈 상태에서만 DB 시간을 조정하세요.

```sql
SELECT char_id, remaining_millis / 1000 AS remaining_seconds
FROM character_ats_time;

-- 12345를 실제 캐릭터 객체 ID로 바꾸세요. 24시간으로 설정하는 예시입니다.
INSERT INTO character_ats_time (char_id, remaining_millis)
VALUES (12345, 86400000)
ON DUPLICATE KEY UPDATE remaining_millis=VALUES(remaining_millis);
```

캐릭터 이름이 아니라 서버의 캐릭터 객체 ID를 사용합니다. 자동 충전·일일 초기화·충전 아이템은 이번 범위에 포함하지 않았습니다.

## 클라이언트 배치

```ini
[ATS]
Enabled=1
Bottom=124
OffsetX=0
ScalePercent=100
ShowSeconds=1
```

`Bottom`과 `OffsetX`는 640×480 기준 좌표입니다. 게임 창 크기에 비례해 적용됩니다.
아이콘이 아래 UI를 가리면 Bottom을 높여 위로 옮기세요. 글자·아이콘 크기는 ScalePercent로 조절합니다.
창 이동·크기 변경에 따라 위치를 갱신하며 최소화하거나 다른 창으로 전환하면 숨깁니다.
마우스 클릭과 키보드 포커스를 가로채지 않는 창 모드용 투명 오버레이입니다. 독점 전체 화면 렌더링 후킹은 포함하지 않았습니다.

## 게임 없이 미리 보기

`client-ready/ATS_Preview.exe`를 실행하세요. 실제 DLL과 같은 HUD 그리기 코드를 사용합니다.
Space로 정지/재개, Esc로 닫기. 창 이동·크기 변경·최소화도 확인할 수 있습니다.
미리 보기의 19시간 33분은 시각 확인용 값이며 서버 이용시간에는 연결되지 않습니다.

## CSB 배치 파일

`reference-layout/`에 제공된 CSB와 HUD 배치 해석 JSON을 넣었습니다.
CSB는 Cocos 배치 정보이며 현재 Mega.bin에서 직접 실행한 것이 아닙니다.
원본 아틀라스 대신 직접 생성한 `ats.png`를 사용하며, 영상 기준으로 구형 클라이언트에 맞게 배치를 조정했습니다.
전체 ATS 설정창(ATSLayout.csb) 이식은 포함하지 않았습니다. 이번 화면 구현은 하단 상태 HUD입니다.

## 검증 범위

- 32비트 PE32 dinput.dll / ATS_Preview.exe 크로스 빌드 통과.
- 기존 C++ 이동 코어 10,000회 반복 회귀 테스트, ATS HUD 파서·카운트다운 ASan/UBSan 검사 통과.
- 기존 Java 이동 주기 회귀 및 활성 시간·정지·재시작·소진·레벨·맵 경계 테스트 통과.
- 실제 Netty EmbeddedChannel을 이용한 분할 메시지, ATS1 협상, 기존 V6 프로토콜 호환 테스트 통과.
- 실제 JDBC 저장 코드를 H2 MySQL 모드에서 첫 지급·재접속·환급·동시 예약 보존으로 검증.
- 새 Java 클래스는 실제 Netty 라이브러리와 게임 의존성 fixture로 타입 검사. 기존 대형 서버 클래스는 구문 검사.

**전체 서버 의존 라이브러리와 실클라이언트가 이 업로드에 없어 전체 서버 링크, 실제 MySQL, Windows 표시·게임 플레이는 아직 검증하지 않았습니다.**
완성된 서버 JAR은 제공하지 않습니다. 처음에는 테스트 서버에서 적용 확인이 필요합니다.

## 시간 저장 방식과 장애 시 한계

이미 사용한 시간이 서버 강제 종료로 되돌아가지 않도록 이용시간을 10초씩 먼저 예약합니다.
정상 정지 때 사용하지 않은 예약분은 DB에 환급합니다. 강제 종료나 DB 환급 실패에서는 정상 사용분 외에 최대 약 10초가 추가 차감될 수 있습니다.
일반 동작에서는 밀리초 단위 잔여 시간을 보존합니다. 긴 스케줄 지연도 경과 시간만큼 차감합니다.
ATS DB 접속은 기존 무한 재시도 경로와 분리하고 MySQL 접속·소켓 타임아웃을 설정했습니다.

## 변경 파일

신규 서버 클래스: `AtsRules`, `AtsBudget`, `AtsTimeStore`, `AtsUsageTimer`.
서버 연결: `L1PcInstance`, `AutoHuntController`, `NativeStepControl`, `L1DatabaseFactory`.
클라이언트: `ats_state`, `ats_hud`, 네트워크·UI 어댑터와 빌드 스크립트.
`step_core.cpp`, `mega_client.cpp`, `NativeStepSession.java`, `WalkActionHandler.java`는 V6 원본과 동일합니다.

전체 변경은 `validation/changes-from-v6.patch`와 `validation/report-v7.json`에서 확인할 수 있습니다.
`historical-v6/`의 로그와 안내는 원본 보관용이며 이번 빌드의 검증 결과가 아닙니다.
