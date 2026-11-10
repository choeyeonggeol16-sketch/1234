# Mega.bin 전용 네이티브 한 칸 입력 V4

이전 V1~V3는 사용자가 보고한 방향 불일치를 해결하지 못했습니다. 이번 V4는 추가로 제공된 **실제 Mega.bin**을 분석해 입력 경로를 바꾼 버전입니다. Windows 실게임 최종 검증은 아직 필요합니다.

## 적용

1. 게임을 완전히 종료하고 기존 `dinput.dll`을 백업합니다.
2. V4 DLL ZIP의 `dinput.dll` 또는 전체 소스 ZIP의 `client-ready/dinput.dll`을 **Mega.bin과 같은 폴더**에 넣습니다.
3. 게임을 다시 실행하고 캐릭터로 접속한 뒤 **49027 아이템**으로 시작합니다. 다시 사용하면 종료합니다.

**Ctrl+Alt+F9 보정은 사용하지 않습니다.** 기존 NativeStep.ini는 남겨 두어도 이번 입력 계산에 사용되지 않습니다. 서버는 첨부본 그대로 사용하며 재빌드하지 않아도 됩니다.

로그에서 `Mega SHA256 and native input signatures verified`와 **`Mega native-tile-input-v4-20261111 initialized`**를 확인합니다. 이어 `native next-tile plan verified`, `native next-tile input committed`, `native input released and target cancelled`가 기록됩니다.

## 실제 실행 파일에서 확인한 점과 변경

- 제공된 실행 파일의 DirectInput 생성 대상 GUID는 **키보드**입니다. 마우스 경로는 Win32 창 메시지의 좌표를 받아 `0x45E2B0`에서 해상도 모드에 맞게 변환합니다. 960×720 모드는 2/3 변환입니다.
- 플레이어의 현재 내부 타일과 실제 네이티브 이웃 타일 표를 읽고, `0x49F810`으로 승인 방향의 바로 다음 타일을 구합니다. 서버 좌표와 화면 좌표의 방향표를 추측해 회전시키는 방식을 사용하지 않습니다.
- 원본 `0x49F4C0` 투영과 `0x49F590` 역변환을 실행해 **클릭 지점이 정확히 그 다음 타일로 되돌아오는지 확인**합니다. 타일 경계의 ±2px도 검사합니다. 타일은 내부적으로 48×24px 마름모이며 투영의 왼쪽 꼭짓점에서 (24,12) 안쪽을 사용합니다.
- 원본 해상도 변환도 다시 실행해 창 좌표와 내부 좌표가 일치하는지 검사한 뒤 같은 게임 UI 스레드에서 `WM_MOUSEMOVE → WM_LBUTTONDOWN`을 처리합니다. **실제 OS 커서 이동이나 SendInput 버튼 주입을 사용하지 않습니다.**
- 로컬 플레이어가 승인된 한 칸에 도달하면 버튼 메시지를 해제하고, 자신이 설정한 이동 목표가 유지 중인 경우 원본 `0x4CC830` 목표 해제 함수를 호출합니다. 서버 CANCEL을 기다리는 논리 상태는 유지해 새 승인 없이 다음 걸음을 만들지 않습니다.
- 게임의 기존 걷기·애니메이션·카메라 경로를 사용합니다. 서버 C_MoveChar 패킷을 직접 만들거나 카메라 메모리 주소를 추측해서 수정하지 않습니다.

클릭이 UI에 소비되거나 원본 목표가 예상과 다르면 입력을 중단하고 이유를 기록합니다. 서버의 방향·출발 좌표 검사와 한 걸음 승인 정책은 유지합니다.

## 지원 파일과 검증 범위

지원하는 원본 Mega.bin SHA-256:

`09804d9579446fb9a630c39c1f1b2d08083718261d2f3e57b2e36f80ba865490`

원본 파일 해시와 메모리 코드 서명을 확인하며, 일치하지 않으면 입력을 시작하지 않습니다. **Mega.bin 자체는 수정하지 않았고 배포 ZIP/GitHub에도 포함하지 않습니다.**

- 실제 실행 파일에서 압축 해제한 x86 함수를 Unicorn에서 실행했습니다. 8방향 × 8모드값(4종 배율) × 3카메라 위치, **192개 경우**의 원본 이웃 타일·투영·역변환·목표 선택·방향 후보 선택·목표 해제 검사를 통과했습니다.
- 이 검사의 경계는 **대기 중인 캐릭터, 인접 8칸 통과 가능** 조건입니다. 충돌/대기 질의만 테스트 대체 함수를 사용합니다. 게임의 전체 지도·오브젝트·네트워크·애니메이션을 실행한 검사가 아닙니다.
- 기존 StepEngine 10,000회 승인/취소 및 ASan/UBSan 검사 통과. Windows PE32 DLL과 테스트 EXE 교차 빌드, DirectInput 7개 export/ordinal, 시스템 DLL 의존성 검사 통과.
- 지원하지 않는 실행 파일을 거절하는 Windows 호스트 검사는 빌드했습니다. 이 Linux 환경에서 Windows 호스트 EXE는 실행하지 않았습니다.
- **Windows 창 메시지 전달, 실제 게임의 C_MoveChar 송신·연속 사냥·카메라 추적은 아직 실게임 검증하지 못했습니다.** 테스트 성공을 실게임 완료로 보고하지 않습니다.

클라이언트 로그에 `SHA256 mismatch`, `native code signature mismatch`, `native plan rejected`, `did not select the verified next tile`이 나오면 해당 실행의 최신 로그로 막힌 지점을 구분할 수 있습니다.

## 개발 검증 재현

Windows 빌드: `nativestep-client/BUILD_WIN32.bat`. MinGW 빌드: `nativestep-client/BUILD_MINGW.sh`.

네이티브 코드 재현에는 사용자가 제공한 원본 Mega.bin이 필요합니다. 해당 실행 파일과 생성 메모리 이미지는 저장소에 넣지 않습니다.

```sh
python3 -m venv .venv-native
.venv-native/bin/pip install -r nativestep-client/tests/requirements-native.txt
.venv-native/bin/python nativestep-client/tools/unpack_for_native_tests.py --client /path/to/Mega.bin --output-directory native-replay-output
.venv-native/bin/python nativestep-client/tests/mega_native_replay.py --client /path/to/Mega.bin --image native-replay-output/stage1-image.bin
```

압축 해제 도구는 해당 해시의 파일만 처리하고, 가상 메모리와 API 대체 함수로 분석 단계까지만 실행합니다. 실제 Windows 실행이나 외부 네트워크 요청은 하지 않습니다.
