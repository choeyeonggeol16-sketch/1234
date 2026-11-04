# 2111 GM 전송 시험 — 새 업로드 소스 기준

기준: `RKern_PSS_V10_InterServer_No2sDelay_ImmediateEnterWorld_FULL_SRC_20261104.zip`.
EXE/DLL 없이 Java 소스만 제공한다. 기존 PSS 클라이언트 확인 도구는 필요하지 않다.

`src`를 교체하고 기존 방식으로 빌드·서버 재시작 후 GM 캐릭터로 입력한다.

```text
.pss2111
```

본인 클라이언트에 protobuf ID **2111 / 0x083F**를 빈 본문으로 한 번 전송한다.
설정 본문을 넣어 시험하려면 다음 명령을 사용한다.

```text
.pss2111 2
```

이는 참고 팩의 `setting_info.mode=2` 구조를 담아 한 번 전송한다.
선택 가능한 값은 1~3이다. 잘못된 인수는 전송하지 않는다.
원래 2111은 클라이언트→서버 설정 변경 요청이므로, 이 명령은 사용자가 요청한
**반대 방향 전송 실험**이다. 설정창 열기에 성공한 것으로 확인된 패킷은 아니다.
클라이언트가 무시하거나 오류로 종료할 가능성이 있다.

서버에는 `[PSS-2111-TEST] S->C id=2111 ... admitted=true/false`가 기록된다.
`admitted=true`는 서버 전송큐 접수이며 클라이언트 수신·창 표시 완료를 뜻하지 않는다.
클라이언트가 실제 2111 설정 요청을 보내면 `[PSS-2019] recv 2111`로 설정값을 기록한다.
이 명령은 자동사냥/PSS를 시작·중지하거나 카메라 설정을 변경하지 않는다.

업로드한 즉시 입장 구현의 `DominationHybrid.java`, `ActionListenerLoader.java`를 포함해
아래 네 파일 외의 모든 `src` 파일은 업로드 ZIP과 바이트 단위로 동일하다.

- `GMCommands.java`: `.pss2111` 명령 연결.
- `PlaySupport2019.java`: GM 본인 전송 시험과 2111 수신 로그.
- `ProtoMsg.java`: 2111 ID 등록.
- `CSProto.java`: 2111 수신 처리 등록.

`src/META-INF/MANIFEST.MF`를 포함한다. Java 전체 컴파일 및 실제 프로토 직렬화·라우팅,
GM/세션 제한, 프레임 반환, 기존 자동사냥 소유권 유지 테스트를 통과했다
(1709개 소스 컴파일, 16개 테스트 그룹 / 1082개 검증).
실제 19205 Windows 클라이언트의 창 표시 결과는 아직 미검증이다.
