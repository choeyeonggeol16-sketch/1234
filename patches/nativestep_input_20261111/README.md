배포 파일의 적용 안내입니다. 아래 경로와 명령은 전체 소스 ZIP을 압축 해제한 폴더를 기준으로 합니다. 검토용 C++ 소스는 이 폴더의 `nativestep-client`에도 포함되어 있습니다.

# 49027 자동사냥 클라이언트 입력 수정본

목표는 캐릭터가 화면 중앙을 유지하고 배경이 이동하는 정상 걷기 경로를 사용하는 것입니다. 서버 좌표만 강제로 이동시키거나 카메라 메모리 주소를 추측해서 패치하지 않습니다. 실제 Mega.bin과 게임 실행 환경은 첨부되지 않았으므로, 게임에서의 이동·카메라 추적 성공까지 확인한 수정본은 아닙니다.

## 바로 적용

1. 게임을 완전히 종료하고 기존 게임 폴더의 `dinput.dll`을 백업합니다.
2. `client-ready/dinput.dll`을 **Mega.bin과 같은 폴더**에 넣습니다. Windows 시스템 폴더에는 넣지 않습니다. DLL은 이미 32비트로 빌드되어 있어 직접 빌드하지 않아도 됩니다.
3. 게임을 켜고 캐릭터에 접속합니다. 자동사냥을 끈 상태에서 평소처럼 잠깐 걸어 캐릭터가 화면 중앙으로 돌아오는지 먼저 확인합니다.
4. **마우스를 자기 캐릭터의 발바닥/그림자 중심에 놓고 Ctrl+Alt+F9를 한 번 누릅니다.** 클릭하지 않아도 됩니다. 현재 창 크기와 발 위치를 `NativeStep.ini`에 저장합니다. 이 보정을 하지 않으면 이번 DLL은 추측 좌표로 클릭하지 않고 자동입력을 중단합니다.
5. 게임을 앞에 둔 채 **49027 아이템**으로 자동사냥을 시작합니다. 다시 사용하면 종료합니다.

창 크기나 게임 해상도를 바꿨다면 자동사냥을 끄고 4번을 다시 실행합니다. 캐릭터 발 위치가 바뀌는 UI/화면 배치 변경 때에도 다시 보정합니다. 보정 파일을 쓸 수 없는 폴더에서는 현재 실행 중에만 적용되므로 게임 재실행 후 다시 보정해야 합니다.

이번 변경은 클라이언트 C++ 코드에 한정됩니다. 첨부 ZIP의 서버 Java 파일은 바이트 단위로 보존했습니다. 첨부 원본의 서버가 이미 실행 중이라면 서버를 다시 빌드할 필요는 없습니다.

## 수정한 부분

- 창 높이 42%라는 고정 추측 대신 실제 캐릭터 발 위치를 클릭 원점으로 사용합니다. 현재 해상도와 보정 해상도가 다르면 입력을 중단합니다.
- 커서 위치를 준비하는 단계와 실제 `SendInput` 누름 단계를 구분했습니다. 실제 누름 성공 시점부터 최대 300ms를 계산합니다. 준비만 하다가 만료·취소된 클릭은 실행하지 않습니다.
- `CANCEL → 다음 방향`이 UI 처리 전에 연달아 도착해도 이전 클릭을 해제하고 새 방향을 보존합니다.
- 클릭 준비 중 창 이동·리사이즈·포커스 상실이 발생하면 이전 화면 좌표로 클릭하지 않습니다.
- 뷰포트 밖으로 벗어나는 좌표를 화면 가장자리로 강제 보정하지 않습니다. 방향이 바뀌는 잘못된 클릭을 거절합니다.
- 서버의 기존 1회 승인/1회 이동 및 방향·출발 좌표 검사를 유지합니다.

8방향 타일 투영의 기본 반폭/반높이 36/23px은 첨부본 값을 유지했습니다. 이 값은 실제 Mega.bin에서 확인된 보편 상수가 아닙니다. 발 위치 보정 후에도 방향이 맞지 않으면 임의로 서버 방향 검사를 풀지 말고 `client_io.log`와 실제 게임 화면을 확인해야 합니다. 다른 배율에서는 `NativeStep.ini`의 `HalfTileWidth`, `HalfTileHeight`를 실제 타일 치수에 맞출 수 있으며 변경 후에는 게임을 다시 시작하고 발 위치를 재보정합니다.

## 안 움직일 때 확인할 로그

Mega.bin 옆 `client_io.log`에서 순서대로 확인합니다.

1. `module loaded` / `ui hook installed`: DLL 로딩과 UI 후크 확인.
2. `confirmed-press-20261111 control initialized`: 이번 DLL 버전 확인.
3. `player ground anchor calibrated and saved`: Ctrl+Alt+F9 보정 성공.
4. `paired to server`: 기존 게임 연결과 제어 채널 연결 성공.
5. `item START received` / `first direction received`: 아이템과 서버 이동 명령 수신.
6. `cursor staged` / `physical click down` / `physical click up`: Windows 입력 단계 도달.

`movement blocked: calibrate feet...`는 보정 누락/창 크기 불일치입니다. `waiting for game TCP connection` 또는 `connect control port failed`가 계속되면 입력 좌표보다 기존 연결 설정부터 확인해야 합니다. 첨부본처럼 게임 포트 5000, 제어 포트 18841을 사용합니다. 로그가 없거나 어느 단계에서 끊기는지에 따라 원인이 다르므로, 이 DLL만으로 모든 연결 문제까지 해결된다고 볼 수는 없습니다.

## 검증 범위

- Linux에서 실제 C++ 코어 테스트 실행: 기존 10,000회 승인/취소 반복과 새 지연 클릭·만료·취소 후 다음 명령·보정 좌표 검사를 ASan/UBSan으로 통과했습니다.
- 기존 코어에서 지연 준비와 연속 CANCEL/다음 방향 문제를 별도 재현했습니다.
- MinGW GCC 14로 Windows PE32 DLL과 테스트 EXE를 교차 빌드했습니다. DirectInput 내보내기 7개와 ordinal 1~7, 추가 MinGW 런타임 DLL 불필요 여부를 검사했습니다.
- 실제 Windows 입력 테스트 EXE, Mega.bin 내부 입력 해석, 게임 이동·카메라 추적은 실행하지 못했습니다. 검증 결과는 `validation` 폴더에 있습니다.

문제가 생기면 게임을 완전히 종료하고 백업한 `dinput.dll`로 되돌립니다. 계속 안 움직인다면 이 안내의 로그 단계가 보이도록 `client_io.log`를 보내면 다음 원인을 구분할 수 있습니다.

## 직접 빌드

Windows/MSVC: `nativestep-client/BUILD_WIN32.bat`.

MinGW: `nativestep-client/BUILD_MINGW.sh` (`CXX`로 32비트 MinGW g++ 지정 가능).

호스트 코어 테스트: `g++ -std=c++17 -I nativestep-client/include nativestep-client/src/step_core.cpp nativestep-client/tests/step_core_test.cpp -o step_core_test` 후 실행합니다.

기존 README와 NATIVESTEP 안내는 이전 수정 이력이므로 이번 적용은 이 문서를 우선합니다.
