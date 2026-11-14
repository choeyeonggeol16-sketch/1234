# MainServer 프로토콜 이름 정리·파서 수정 (2026-11-14)

기준: 마지막 네트워크 개선 검증 브랜치 `ctransport-stability-43580772728`, commit `a1d0ea7`.
기존 full-src ZIP의 DLL pin이 오래되어 같은 브랜치의 실제 Windows 검증 DLL과 설정으로 맞췄다.

패킷 클래스 파일 87개(이름 86종)의 생성자·import·호출부를 일괄 수정했다. 81종은 복원 proto의 정확한 메시지명이며 `NOIT`, `ACk`도 descriptor의 철자를 보존한다. 나머지 5종은 대응이 확인되지 않은 서버 자체 어댑터 이름이다. 요청 처리 22종, PSS 함수 6종, 인던 함수 9종을 설명 가능한 이름으로 통일했고, proto 상수 11개와 인던 상수 27개도 맞췄다. 실제 wire ID 숫자는 유지한다.

`class-names.csv`는 이전→현재 이름, `all-messages.csv`는 보고서의 최상위 메시지 429개 전체를 담는다. 빈 구현 칸은 이번 팩에 해당 패킷 클래스가 없다는 뜻이다. 원본 서버 함수 심볼은 보고서에 없어 임의의 이름을 원본이라고 주장하지 않는다. 서버 처리 함수명은 이 팩의 설명용 이름이다.

코드 수정: ProtoReader의 열 번째 varint 바이트 초과/다음 필드 소비와 잘못된 tag 범위를 거부한다. 설문 화면 알림의 RGB 길이 누락·128바이트 이상 문자열 길이 오류는 공통 SC_NOTIFICATION_MESSAGE 직렬화 코드로 연결해 수정한다. 기존 255바이트 문자열 상한과 전송 실패 버퍼 반납을 유지한다.

## 적용

전체 소스 ZIP의 `src/`로 기존 소스를 교체하고 Java21 clean build한다. ZIP 루트의 `CTransport.dll`을 서버 실행 폴더에 함께 교체한다. ABI10/67exports의 마지막 검증 DLL을 재사용한다. 기존 설정·DB·독립 PSS 클라이언트는 그대로 사용한다.

부분 적용 시 `src/` 파일을 복사하고 `deleted-files.txt`의 이전 경로 87개를 제거한다. 같은 클래스의 이전/새 파일을 함께 빌드하지 않는다. `runtime/CTransport.dll`도 서버 실행 폴더로 복사한다.

## 검증

전체 production Java 1,711개 컴파일 오류 0(기존 deprecated 경고 5개). 송신 직렬화 170개 입력 사례 바이트 동일, null DTO 예외 16개 동일. 파서 20,052개 단언과 알림 필드 15개 사례·문자열 상한·실패 버퍼 반납 통과. 두 오류 모두 변경 전 실제 코드에서 재현했다. 상세 로그·검증 의존성 hash는 `logs/`, 결과는 `VALIDATION.json`에 있다. 실제 Windows 게임·DB 통합 실행은 이번 수정 후 재검증하지 않았다.

`apply_protocol_names.py --base <이전 full-src.zip> --output <새 폴더>`로 동일 소스 변경을 재생성할 수 있다. `schemas/`는 보고서에서 복원된 계약이며 원본 서버 소스가 아니다.
