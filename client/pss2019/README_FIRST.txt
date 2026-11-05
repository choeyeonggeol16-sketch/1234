PSS 2019 배회 코어 V3 — 교체 테스트 DLL

게임을 완전히 종료한 뒤 기존 Pss2019.dll을 백업하고 ZIP의 Pss2019.dll로 교체하세요.
기존 Pss2019.ini를 유지하세요. 이미 자동 로딩을 적용했다면 PSS_Client_Apply 재실행은 필요 없습니다.
서버 소스·DB 변경 없이 기존 Play Supporter 아이템(900000901)으로 설정창을 열고 시작합니다.
C++ 런타임도 DLL 안에 정적으로 포함하므로 다른 DLL을 추가할 필요 없습니다.

바뀐 부분:
- 업로드한 RoamCore.cpp의 sr_tick을 실제 연결했습니다. 별도의 RoamCore32.dll은 쓰지 않습니다.
- 배회 길은 기존 클라이언트 길찾기가 반환한 경로에서 수집합니다.
- 내부 좌표와 논리 좌표를 변환하고, 출발점은 캐릭터의 실제 내부 좌표를 사용합니다.
- 다음 칸으로 이동하기 직전 다시 길을 조회합니다. 길이 막히거나 잘못된 경로이면 이동 명령을 내리지 않습니다.
- 도착 여부를 실제 위치로 확인하고, 수락됐어도 이동이 없으면 2초 뒤 해당 간선을 잠시 제외합니다.
- 몬스터·아이템·고정 사냥·기록 경로·기존 귀환 동작은 기존 PSS 판단을 우선합니다.
- 기본 탐색 반경은 시작 위치 기준 20칸입니다. 한 틱의 네이티브 길찾기 호출은 최대 2회입니다.

선택 설정: 기존 Pss2019.ini 끝에 추가할 수 있습니다. 추가하지 않아도 아래 기본값으로 동작합니다.
[PssRoaming]
Enabled=1
Radius=20
SearchBudget=1024
RetryMs=300
StuckTimeoutMs=2000
CommandIntervalMs=300
IdleDelayMs=500

Radius는 4~32 범위입니다. 고정 시작점 주변을 탐색하는 코어이며 맵 전체를 순회하는 코어는 아닙니다.
CommandIntervalMs는 추가 제한이며 캐릭터의 원래 이동 속도 검사를 대체하지 않습니다.

확인 로그(PSS2019_trace.txt):
PSS imported RoamCore ready: normalized coordinates and native route cache
PSS idle walking attached; new targets retain native priority
PSS imported core map=... position=... result=... radius=20 queries=... rejected=...

검증: MinGW 32비트 경고 없는 빌드, 원래 코어 ASan/UBSan 검사, 좌표/경로 캐시 검사,
32비트 네이티브 호출 모형, 최종 DLL import 검사를 통과했습니다. validation에 기록이 있습니다.
실제 게임의 길찾기 함수·벽·동적 충돌과 카메라는 아직 이 버전으로 검증하지 않았습니다.
이번 파일은 실제 클라이언트에서 시험할 수 있는 빌드이며, 벽 비빔이 완전히 해결됐다고 확정한 버전은 아닙니다.
문제가 생기면 게임을 종료하고 백업 DLL로 교체하면 됩니다.

빌드: 32비트 MinGW-w64 GCC와 G++가 PATH에 있는 Windows에서 source\build.cmd 실행.
코어 출처 및 헤더 변경 사항은 source\vendor\roam\PROVENANCE.json 참조.
