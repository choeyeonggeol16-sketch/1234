# V4 이하 서버에서 처음 적용할 때

이미 서버 로그에 `step-v5 sprite-cadence`가 표시되면 이 작업은 필요 없습니다. 게임을 종료하고 V6 DLL만 교체하세요.

이전 서버를 사용 중이면 V6 전체 소스 ZIP의 아래 3개를 기존 서버 프로젝트에 함께 반영하고 기존 빌드 환경으로 재빌드·재시작합니다.

- `src/l1j/server/AutoHuntSystem/NativeStepSession.java`
- `src/l1j/server/AutoHuntSystem/AutoHuntController.java`
- `src/l1j/server/CharacterAction/Walk/WalkActionHandler.java`

서버 소스는 V5와 동일합니다. 정상 WALK의 다음 승인 간격은 캐릭터의 원래 sprite interval을 따릅니다. 서버 표시 `step-v5 sprite-cadence`와 클라이언트 표시 `native-tile-input-v6-20261111`의 조합이 맞습니다.

서버 실행 JAR·외부 라이브러리는 제공하지 않습니다. 기존 프로젝트의 SWT 등 의존성을 사용해야 합니다. 다른 서버 버전이나 별도 변경이 있는 프로젝트에는 전체 src를 덮어쓰기보다 이 3개 변경을 비교해 반영하세요.
