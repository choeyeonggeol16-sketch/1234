# PSS 2019 빈 시작 요청 호환 V8

기준: 매니페스트를 보충한 V7 `5155817`.

실제 게임 로그에서 2101 요청이 `bytes=0`으로 도착하는 것이 확인됐다. V7은 리마스터의 mode 필드가 있다고 가정해서 `missing PSS mode`로 거절했다. 중지 2103의 `remain=0`은 시작 승인 세션이 없었기 때문이다.

## 수정

- 검증된 0바이트 요청만 GM 시험용 시작 요청으로 허용한다.
- 처음 시작하는 빈 요청은 서버 내부 시험 상태에 LOCAL(2)를 사용한다. 클라이언트의 실제 PSS 설정을 읽거나 변경한 값이 아니다. 시작 ACK에는 mode 필드가 없으며, 클라이언트는 자신의 설정으로 동작한다.
- 실행 중인 세션에 빈 요청이 다시 오면 기존 mode와 마감 시각을 유지한다.
- 본문이 있는 요청은 기존 mode 해석/검증을 유지한다. 불완전한 본문·mode=0·미지원 mode·잘못된 슬라이스를 빈 요청으로 간주하지 않는다.
- GM/월드 준비/사망/거래 상점/서버 자동사냥 동시 실행 검사, 600초 시험 응답, 종료/세션 정리는 V7 그대로다.

Java 변경은 `PlaySupport2019.java` 하나다. 나머지 Java 1,708개와 매니페스트는 V7과 동일하다.

## 적용

FULL_SRC의 `src`를 전체 반영하고 Clean/Rebuild·서버 재시작한다. 기존 lib·config·data·DB는 유지한다.
이미 V7이면 PATCH의 Java 파일 하나만 교체해도 된다. FULL_SRC/PATCH 모두 `src/META-INF/MANIFEST.MF`를 포함한다.

기존 서버 자동사냥을 끈 GM으로 접속한 뒤 받은 PSS 시험본으로 다시 시작한다.

정상 시작 요청에서는 다음 정보가 보인다.

```text
[PSS-2019] recv 2101 ... bytes=0 modeSource=legacy-empty-trial
[PSS-2019] send 2102 result=0 admitted=true ...
```

이후에도 움직이지 않으면 DLL 실행 폴더의 `PSS2019_trace.txt`에서 `2102 VALID callback reached`, `Start skipped`, `PSS start sequence completed` 기록을 확인한다. 서버 송신 승인은 실제 사냥 완료를 뜻하지 않는다.

## 검증

전체 Java 1,709개 컴파일, PSS 테스트 12개 그룹·987개 단언 통과. 실제 CSProto로 0바이트 2101 프레임을 처리해 성공 ACK 바이트를 비교했다. 빈 요청의 권한/상태 차단, 기존 mode와 마감 시각 보존, 기존 파싱·상호 배제 회귀를 확인했다.
실제 2019 클라이언트의 승인 이후 이동·공격은 이번 환경에서 실행하지 않았다.
