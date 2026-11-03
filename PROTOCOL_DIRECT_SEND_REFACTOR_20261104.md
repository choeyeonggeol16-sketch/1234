# 프로토콜 직접 송신 리팩토링 — 2026-11-04

기존 프로토콜도 `ProtoPacket.writeImpl()`에서는 이미 `WriteBuffer`에 직접 기록했습니다. 이번 변경은 그 앞에서 매번 만들던 `SC_*` 패킷, setter 조립용 상태, nullable 숫자 박싱, 전송용 `ProtoPacketWriter` 래퍼를 줄입니다. 프로토콜 번호·필드 순서·누락 여부·기본값·MS949 문자열·varint·중첩 길이·끝의 0 두 바이트를 유지합니다.

## 변경

- 프로토콜 63종에 `writeFields`, `sendFields`, `encodeFields`를 추가했습니다. 선택 숫자 필드는 primitive 값과 presence bit로 구분해, **미설정과 명시적인 0/false**를 유지합니다. 실제 버프·스탯·슬롯·펫·월드 알림·파티·제작·도감 등의 호출부를 새 경로에 연결했습니다.
- `Proto.begin/commit/abort`가 기존 풀의 `CIOFrame`을 빌려 바로 기록합니다. 기존 네트워크 엔진의 FIFO 예약·예산·암호화·완료 처리는 그대로 사용합니다. 인코딩 실패, 반려된 commit, 중첩 송신에서도 lease를 한 번만 반납하며 ThreadLocal의 연결/프레임 참조를 해제합니다.
- 직접 writer의 중첩 재진입은 스레드별 최대 8개를 재사용합니다. 그보다 깊은 중첩에서는 임시 writer를 사용해 보관량을 제한합니다. 소유 중인 프레임을 `reset()`으로 잃어버리지 못하게 했습니다.
- 일반 인벤토리 추가는 `ItemInfo`, `CompanionCard`, 연결 리스트, 숫자 박싱을 거치지 않습니다. 펫 목걸이 상태와 이름, 특수 상태 바이트, 속성 강화, 귀속/거래/삭제 조건을 같은 필드로 기록합니다.
- 로그인 인벤토리는 기존 본문 스냅샷의 범위를 전송합니다. 순서·40개 제한·16KiB 분할 기준·큰 단일 아이템 처리를 유지하며 청크마다 패킷과 리스트 노드를 만들지 않습니다.
- 창고는 청크당 패킷/리스트와 아이템 정보 래퍼를 제거했습니다. 40개/12KiB 제한, 창고용 상세 설명 필드 생략, 빈 마지막 청크와 `serial_last`를 유지합니다. 길이 측정에 필요한 본문 `byte[]`는 유지합니다.
- 패시브 전체 알림은 기존 패시브 도메인 목록에서 직접 씁니다. 파티/방송의 공유 인코딩 본문도 `Raw`를 새로 씌우지 않고 전송합니다. 소스의 `new ProtoPacketWriter(...)`는 5곳→0곳, `new ProtoPacketWriter.Raw(...)`는 43곳→1곳입니다. 남은 1곳은 출석 그룹의 **1회 생성·공유 캐시**입니다. 외부 호출의 `SC_SpellBuffNoti.newInstance()` 80곳도 0곳으로 바꿨습니다.
- 작은 고정 패킷·파티·스킬·TAM·도감·제작 결과는 필드/원소 수에 따른 예약 상한을 사용합니다. 인벤토리/창고의 인코딩 본문은 실제 길이로 예약합니다. 일부 일반 복합 DTO API는 최대 wire 크기를 안전 상한으로 사용합니다.

기존 생성자·빌더·`encode()`는 외부 호환과 비교 기준으로 남겼습니다. 출석 저장 상태, 랭킹 캐시, 비동기 DB 결과 등 수명이 있는 데이터도 유지했습니다. **프로그램 전체의 객체 생성을 없앤 변경은 아닙니다.** MS949 문자열 바이트, 아이템 상태 바이트, 상점/도감 등의 하위 데이터 스냅샷, 캐시 본문에는 필요한 할당이 남습니다. CS 수신 파서와 게임 효과/거래 규칙을 별도로 개편하지 않았습니다.

## 검증

| 검사 | 결과 |
|---|---|
| 전체 서버 소스 | Java 1,723개 컴파일 통과, 기존 deprecated/removal 경고 5개 |
| 기존 직렬화 기준 보존 | 63종의 `body`/`msgId` 126개 AST 해시 일치 |
| 실제 기존 `encode()` 대 새 필드 직렬화 | 63종, 유효 입력 2,002건 바이트 일치 |
| 실제 생성자·팩토리 대 직접 송신 | 95개 오버로드, 1,520건 바이트 일치 |
| 인벤토리/펫/로그인/창고 | 3,441건 바이트 일치 |
| 패시브 도메인 직접 송신 | 80건 바이트 일치 |
| 직접 프레임 계약 | admission/실패/반려/반납/중첩/스레드 분리 44개 단언 |
| 기존 회귀 | 아이템 사용 12건, 거래 27건, 장비 교체 14건 |
| 버프 격리 할당 측정 | 200,000회: 기존 72.00 B/회 → 직접 송신 0.00 B/회 |

할당 측정은 **실제 프로토콜 직렬화 코드와 `WriteBuffer`**, 미리 할당한 모의 송신 풀을 사용했습니다. 기존 패킷이 큐 경계를 넘어가는 상황을 모형화했습니다. 문자열이 없는 버프 경로의 정상 반복 측정이며 서버 전체 힙 절감률·처리량·운영 GC 결과가 아닙니다. 프레임 시험도 실제 Windows IOCP 통합 시험을 대신하지 않습니다. 실제 게임 클라이언트·운영 DB·운영 서버 부하는 실행하지 않았습니다.

검증 코드와 원시 결과는 ZIP의 `tests/protocol_direct/`, `validation/protocol_*.log`에 있습니다. `test_source.py`는 독립된 기존 AST 해시를 보관하므로 비교 기준을 함께 잘못 바꾸는 것도 검사합니다. 장비 검사 double에는 새 정적 송신 진입점을 추가했고, 기존 상태/예약/응답 순서 단언은 유지했습니다.

## 적용

- `FULL_SRC`는 공개 아이템 사용 V3 서버 소스(`afb051f`, 문서/진단 도구 추가 직전 서버 코드)를 기준으로 한 전체 소스입니다. 기존 NPC·자동사냥·스프라이트·아이템 사용 V3 수정도 포함합니다.
- `PATCH`는 같은 기준 대비 변경 Java **115개 전체**, 변경한 장비 검사, 새 프로토콜 시험과 안내를 포함합니다. 다른 버전 소스에는 FULL_SRC 기준으로 병합하세요.
- 기존 프로젝트를 백업하고 서버를 내린 뒤 소스를 적용합니다. 단일 패킷 클래스만 교체하지 말고 **JDK 21 / UTF-8 / `--enable-preview`로 Clean/Rebuild 후 재시작**하세요. 실행 JVM에도 `--enable-preview`가 필요합니다.
- 추가 DB 변경은 없습니다. 처음에는 로그인 목록, 아이템 획득/장비/펫 상태, 버프 아이콘, 창고 분할, 파티/스탯 표시를 실제 클라이언트에서 확인하세요.

빌드/재현 예시:

```bash
python3 tools/fetch_item_dependencies.py
python3 tools/build_item_refactor.py --output /tmp/protocol-build
python3 tests/protocol_direct/test_source.py
python3 tests/protocol_direct/test_wire.py --classes /tmp/protocol-build/classes
python3 tests/protocol_direct/test_transport.py --classes /tmp/protocol-build/classes
python3 tests/protocol_direct/test_inventory.py --classes /tmp/protocol-build/classes
```

위 예시는 Java 21과 Python이 있는 셸 기준입니다. 아카이브의 `validation/protocol_changed_java.json`, `protocol_source_sha256.json`, `protocol_source.patch`로 변경 목록·해시·Java 적용 diff를 확인할 수 있습니다. `tests/protocol_direct/transport/`와 `inventory/`의 대역은 **검사용**이며 운영 `src`나 classpath에 넣지 않습니다.
