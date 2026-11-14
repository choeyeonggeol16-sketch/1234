# MJBin ↔ MainServer 수정 작업 기준 (2026-11-14)

## 1. 정확한 자료 범위

이 디렉터리의 **MJBin / mjlin은 사용자 제공 MJ 프리서버 클라이언트 분석 자료**다. NC 본서버 정식 원본으로 확정하지 않는다.

* `mjlin.bin`: x86 클라이언트 실행 바이너리, 8,354,488 bytes. SHA-256 `9c82129efed1d93981782069e82de79869920769aa0f67bc1183ca75cf621823`
* `mjlin.zip`: 해당 실행 파일을 포함한 원본 ZIP, 8,073,673 bytes. SHA-256 `4cf1a213a9903b02225309048646bf6ee13b6041cbc929bcbe1c0676a0e15edc`
* `mjlin_loaded_code_00400000_0180D000.bin`: 실행 중 코드 이미지로 추출한 21,024,768-byte 스냅샷. SHA-256 `256f7886e98d7d2c573ab76917f78e30dc4f34cb723a002d604602cdb2f6cbdb`
* `mjlin.bin.3860.dmp`: 프로세스 메모리 덤프, 46,149,837 bytes. 민감 정보가 섞일 수 있어 공개 배포 전 점검 필요.

**MainServer.exe나 MainServer 서버 소스는 이 원본 묶음에 없다.** `MainServer_Client_PlaySupport.proto`는 클라이언트에서 복원한 **서버 통신 규약의 이름**이다. 서버 실행 코드가 아니다.

## 2. 깃허브에 이미 보존된 자료

* [MainServer PSS proto](schemas/MainServer_Client_PlaySupport.reconstructed.proto)
* [PSS 설정·행동 proto](schemas/PlaySupport.reconstructed.proto)
* [원본 바이너리 역분석](REVERSE_ENGINEERING_20261113.md)
* [Ctrl 두 번 자동 공격 DLL 연동 연구](LEGACY_CLIENT_CTRL_DOUBLE_AUTOATTACK_DLL_FULL_REPORT_20261113.md)
* [자동 마법인형 소환](AUTO_SUMMON_MAGIC_DOLL_REVERSE_ENGINEERING_20261113.md)
* [바이너리 파일 목록/해시](BINARY_INVENTORY_SHA256.md)

## 3. MainServer 측 프로토콜 계약 (클라이언트에서 복원한 사실)

| 클라이언트 → 서버 | 서버 → 클라이언트 | 확인된 필드 |
|---|---|---|
| `CS_START_PLAY_SUPPORT_REQ` | `SC_START_PLAY_SUPPORT_ACK` | `result`: VALID=0, INVALID_MAP=1, INVALID_LEVEL=2, TIME_EXPIRE=3, UNKNOWN_ERROR=99 |
| `CS_FINISH_PLAY_SUPPORT_REQ` | `SC_FINISH_PLAY_SUPPORT_ACK` | `remain_time` (uint32) |
| 서버 발신 알림 | `SC_FORCE_FINISH_PLAY_SUPPORT_NOTI` | `reason`: INVALID_MAP=1, INVALID_LEVEL=2, TIME_EXPIRE=3, USER_DEAD=4, MAP_TIME_EXPIRED=5; `remain_time` |
| `CS_PSS_TIME_CHECK_REQ` | `SC_PSS_TIME_CHECK_ACK` | `remain_time` (uint32) |
| 서버 발신 알림 | `SC_UPDATE_PLAY_SUPPORT_TIME_NOTI` | `type`: CHARGE_BY_EVENT=1, CHARGE_BY_ITEM=2; `remain_time` |

이는 **protobuf 메시지 형식** 확인이며 실제 wire opcode, MainServer 처리 함수 주소, SQL 테이블·저장 주기, 제한 시간 정책까지 증명하는 자료가 아니다. 그런 값은 서버 쪽 파일을 확보한 뒤 대조한다.

## 4. 다음 수정 작업의 기준

1. 클라이언트 `mjlin.bin`과 MainServer 소스/바이너리를 구분한다. 서버 원본이 확보되면 해당 빌드의 패킷 등록 테이블 및 프로토콜 번호부터 확인한다.
2. 시작 승인, 지도/레벨/시간 제한, 강제 종료, 잔여시간 응답의 실제 MainServer 분기와 영속성 정책을 검증한다. 추정 정책을 원본 확인 로직으로 기록하지 않는다.
3. RKern에 적용할 때는 기존 네트워크·CIO 소유 실행·CacheD 순서와 트랜잭션 안정성을 유지하고, 실제 차이만 수정한다.

## 5. 원본 바이너리 GitHub 게시 상태

**이 커밋은 문서/작업 기준만 게시한다. 실행 바이너리와 메모리 덤프는 GitHub에 업로드하지 않았다.** 현재 저장소는 공개 상태이므로 원본 실행 파일의 재배포 권한을 확인하고, 메모리 덤프와 실행 메모리 이미지는 민감 정보 여부를 검사해야 한다. 로컬 원본 번들 `MJBin_PSS_2019_Analysis_And_Binaries_All.zip`에는 위 파일들이 있으며 ZIP CRC 및 원본 SHA-256을 대조했다.
