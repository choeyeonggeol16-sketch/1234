# 메인서버 프로토 이름 정리 및 수신 파서 수정 — 2026-11-14

GitHub `main`의 `reports/mjbin-pss-2019/mainsrv_full_20261114/`에 올라온 복원 스키마와 기존 구현을 대조했습니다. 확인된 타입 116개에 `LIN` namespace와 메시지 이름을 적용하고, 필드 접근자 363개와 생성자 이름 81개, 사용처를 함께 수정했습니다. 기준 배포는 `1bb2bd706a217c53c9ef44ec265e66e00a4b4a40`입니다.

## 적용한 이름과 구조

| 기존 | 적용 |
|---|---|
| `Protocol.Packet` 아래 확인된 메시지 | `package LIN` |
| `CS_UserPlayInfo` | `LIN.CS_USER_PLAY_INFO_REQ` |
| `SC_UserPlayInfo` | `LIN.SC_USER_PLAY_INFO_NOTI` |
| `SC_SocialAction` | `LIN.SC_SOCIAL_ACTION_NOTI` |
| `SC_SiegeInjuryTime` | `LIN.SC_SIEGE_INJURY_TIME_NOIT` |
| `get_objId()` / `set_objId()` | `obj_id()` / `set_obj_id()` |
| 별도 파일의 `Craft` 및 하위 타입 | `LIN.SC_CRAFT_LIST_ALL_ACK.Craft`와 스키마의 중첩 계층 |
| `CraftInputItemSlotInfo` | `LIN.CS_CRAFT_MAKE_REQ.CraftInputItemSlotInfo` |
| `eCraftMakeReqResultType` | `LIN.SC_CRAFT_MAKE_ACK.eCraftMakeReqResultType` |

기존 109개 파일을 최상위 타입 96개와 중첩 타입 13개로 재배치했습니다. 기존 중첩 타입 7개도 `spellpassivedata`, `FATIGUE_STATUS`, `display_position`, `ePARTY_OPERATION_TYPE`, `SIEGE_KIND`, `SIEGE_ZONE_KIND`, `MAP_TIME_LIMIT_INFO`로 맞췄습니다. 전체 src는 **1,699파일 / Java 1,698파일**입니다. 원본의 `NOIT`, `ACk`, 필드의 대소문자 등은 확인된 스키마 표기를 따릅니다. Java 예약어인 필드는 `_` 접미사를 사용합니다.

`research/protocol-names-20261114/mapping.json`, `classes.tsv`, `nested-renames.json`, `methods.tsv`, `path-mapping.json`에 전체 변경표가 있습니다. Javac 심볼을 해석해 소유 타입이 일치하는 선언·호출·메서드 참조를 변경했습니다. 동명 게임 함수까지 문자열 치환하지 않았습니다.

자료는 클라이언트의 FileDescriptorProto에서 복구한 프로토 스키마입니다. `LIN`을 Java package로 적용한 호환 구현이며, 원본 서버의 Java 소스나 처리 함수 이름을 확인한 자료는 아닙니다. 서버 전용 `handle`, 캐시·전송 함수와 프로토 인프라는 현재 역할에 맞는 이름을 유지합니다.

`CraftOutputItemResult`는 성공 여부와 레시피 출력 아이템을 묶는 서버 헬퍼이므로, 별도 스키마 타입인 `SC_CRAFT_MAKE_ACK.CraftOutputItem`으로 이름을 바꾸지 않았습니다. `CS_PartyControl`, `GludioDungeonPackets` 등 개별 스키마 메시지와 일대일 대응하지 않는 12개 파일도 기존 이름을 유지합니다. 이번 작업은 기존 구현의 대응 이름 정리이며, 미구현 메시지 전체를 추가하는 작업은 포함하지 않습니다.

## 코드 개선

`ProtoReader.varint64()`의 기존 반복문은 10번째 바이트도 일반 7비트 값으로 처리해 overflow를 허용했고, continuation이 붙으면 11번째 바이트까지 읽었습니다. 9바이트 처리 후 마지막 1비트만 허용하도록 고쳤습니다.

`ProtoInputStream.readRawVarInt64()`에도 같은 마지막 바이트 검사를 적용했습니다. 이 경로는 끝에 도달했을 때 `readRawByte()`가 반환하는 0을 값의 일부로 받아들일 수 있었으므로, varint 내부에서 프레임 끝을 검사해 잘린 입력을 IOException으로 처리합니다. 두 수정 모두 정상 int64·음수·기존 송신 형식을 유지합니다.

기존 메시지 번호, 필드 번호, 직렬화 형식과 존재 여부 규칙은 유지합니다. 스키마의 타입 표기만 보고 운영 wire 형식을 일괄 변경하지 않았습니다. 제작의 레시피 출력 모델과 ACK 출력 모델도 현재의 별도 직렬화 규칙을 유지합니다.

## 검증

- JDK 21 preview로 **전체 Java 1,698파일 clean compile 통과**. 기존 deprecated API 경고 5건이 있습니다.
- 변경 전 배포의 실제 컴파일 결과에서 확보한 **90개 타입 / 직렬화 상태 228건**과 변경 후 결과가 바이트 단위로 일치합니다. 0·양수·음수·존재 비트·문자열·하위 메시지 상태 중 직렬화 가능한 사례를 비교합니다.
- 두 varint64 파서에서 경계값과 난수 **정상 값 100,011개**, 잘림·10번째 바이트 overflow·프레임 경계 **265개**를 통과했습니다. 11번째 바이트를 소비하지 않습니다.
- 실제 `CPacketHandler`와 변경된 `CSProto` 라우팅을 사용한 디코더 검사를 통과했습니다.
- 변경 전후 AST 타입 본문과 호출부 토큰을 이름·한정자·중첩 static 추가만 정규화해 대조했습니다. 두 파서 수정은 별도 해시로 제한합니다.
- native 소스, `CTransport.dll`, codec DLL, 독립 PSS 소스와 DLL은 이전 배포와 동일합니다.

새 Java 변경은 Linux/JDK 21에서 검증했습니다. 기존 Windows 실행 `43580772728`은 동일 native DLL의 검증 근거로 유지하며, 이번 Java 변경까지 해당 실행에서 시험했다고 표시하지 않습니다. 실제 게임 클라이언트 접속과 DB를 사용하는 플레이 검증은 실행하지 않았습니다.

## 적용과 재현

**최신 전체 src로 교체한 뒤 clean build**합니다. 패치 적용 시 `deleted-files.txt`도 적용해 예전 프로토 파일을 제거합니다. 최신 `CTransport.dll`은 그대로 사용하며 SQL·설정 변경은 없습니다.

전체 소스와 의존 JAR 디렉터리가 있는 환경에서:

```sh
python tests/run_protocol_names.py --libs /path/to/lib
python tests/verify_protocol_names.py
```

원시 근거: `logs/protocol-names-suite.log`, `logs/protocol-names-wire.log`, `logs/protocol-names-varint.log`, `research/protocol-names-20261114/`의 스키마·매핑·변경 전 직렬화/AST 스냅샷. 새 ABI나 DLL 재빌드는 필요하지 않습니다.
