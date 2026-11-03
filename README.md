## 2026-11-04 — 첨부 DB의 테이블 기준 소스 정리

[전체 소스 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/DB_Table_Cleanup_20261104_FULL_SRC.zip) · [수정·삭제 패치 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/DB_Table_Cleanup_20261104_PATCH.zip) · [변경·검증·적용 안내](DB_TABLE_CLEANUP_20261104.md)

첨부 DB의 테이블 252개와 비교해 없는 테이블 32종의 SQL·로더·관련 호출을 정리했습니다. 운영 Java 39개 수정·32개 삭제, 전체 1,691개 컴파일과 아이템 사용 12개·거래 27개 회귀검사 통과. 정적 SQL과 동적 테이블 계열 검사에서 없는 참조 0건입니다. DB 덤프와 플레이어 데이터는 포함하지 않았습니다.

**FULL_SRC는 기존 src를 백업 후 교체하세요. PATCH는 63aca89 기준이며 deleted_files.txt의 32개 파일 삭제도 필수입니다.** JDK 21 / UTF-8 / `--enable-preview`로 전체 Clean/Rebuild 후 재시작하세요. 실제 게임 클라이언트·운영 DB 기동 검사는 미실행입니다.

---

## 2026-11-04 — 서버 버전 핸드셰이크 중복·거절 종료 정리

[전체 소스 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Server_Version_Handshake_Cleanup_20261104_FULL_SRC.zip) · [Java 3개 패치 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Server_Version_Handshake_Cleanup_20261104_PATCH.zip) · [변경·검증·적용 안내](SERVER_VERSION_HANDSHAKE_CLEANUP_20261104.md)

`SC_ServerVersionInfo`의 중복 직렬화·전달 래퍼를 제거했습니다. 거절마다 만들던 스레드풀 타이머를 없애고 기존 소켓 데드라인 작업을 재사용합니다. 신규 요청은 차단하고, 3초 최소 유예와 기존 송신 완료를 기다린 뒤 닫습니다. 느린 송신의 30초 제한과 일반 graceful close도 유지합니다.

핸드셰이크 128건 바이트 비교, 종료 계약 16개 그룹·100회 경쟁 검사, 실제 localhost TCP 응답→3초 유예→EOF, 다른 62종 프로토콜 회귀와 전체 Java 1,723개 컴파일 통과. 실제 게임 클라이언트·운영 DB·Windows IOCP 통합은 미검증입니다. **PATCH는 9e633d7 기준이며 Java 3개 모두 적용 후 JDK 21 / UTF-8 / `--enable-preview`로 전체 Clean/Rebuild·재시작하세요.**

---

## 2026-11-04 — 프로토콜 직접 송신·임시 패킷 객체 절감

[전체 소스 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Protocol_Direct_Send_Refactor_20261104_FULL_SRC.zip) · [Java 115개 패치 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Protocol_Direct_Send_Refactor_20261104_PATCH.zip) · [변경·검증·적용 안내](PROTOCOL_DIRECT_SEND_REFACTOR_20261104.md)

프로토콜 63종에 필드 직접쓰기 API를 추가하고 실제 버프·스탯·슬롯·펫·월드·파티·제작·도감 송신을 연결했습니다. 인벤토리/창고의 패킷·아이템 정보·청크 리스트 래퍼와 매번 만드는 Raw 전송 래퍼를 줄였습니다. 프로토콜 ID·선택 필드·0/false·MS949·중첩 길이는 유지하며, 보관 중인 출석/랭킹 데이터와 호환 빌더는 남겼습니다.

바이트 비교 7,043건, 프레임 계약 44개 단언, 아이템 사용 12건·거래 27건·장비 14건, 전체 Java 1,723개 컴파일 통과. 버프 격리 측정은 72→0 B/회이며 운영 서버 전체 절감률은 아닙니다. 실제 클라이언트·운영 DB/Windows IOCP 통합은 미검증입니다. **PATCH는 공개 아이템 V3 기준이며 Java 115개 모두 적용 후 JDK 21 / UTF-8 / `--enable-preview`로 Clean/Rebuild·재시작하세요.**

---

## 2026-11-04 — LC 클라이언트 초기 실행 진단 도구

[진단 ZIP 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/LC_First_Check_20261104.zip) · [사용법](LC_FIRST_CHECK_20261104.md)

Windows에서 도구를 실행한 뒤 120초 안에 퍼플로 게임을 실행하세요. LC.exe 버전·해시, 로딩 DLL 메타데이터, TCP 접속 주소/포트와 클라이언트 폴더의 파일 이름·크기를 바탕화면 ZIP으로 수집합니다. 접속 호환성 분석의 첫 단계이며 Windows 실행 검증은 아직 하지 않았습니다.

---

## 2026-11-04 — 아이템 사용 처리 리팩토링 V3

[전체 소스 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Item_Use_Refactor_V3_20261104_FULL_SRC.zip) · [누적 패치 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Item_Use_Refactor_V3_20261104_PATCH.zip) · [구조·검증·적용 안내](ITEM_USE_REFACTOR_V3_20261104.md)

4,768줄에 몰린 아이템 사용 흐름을 대상 해석·조건 검사·선행 처리·효과 분기·정상 후처리로 분리했습니다. 진행 순서는 35줄 서비스에서 읽을 수 있고, 효과 코드는 종류별 10개 모듈과 기존 함수에 남겼습니다. 확장 시스템 우선순위, 204개 분기의 실행 코드·게임 문자열·조기 종료 차이를 보존했습니다. 소모/예약 공통부 V2와 기존 NPC·자동사냥 수정도 포함합니다.

대상 해석 51,240개 입력 비교, 사용 흐름 12건·트랜잭션 27건·장비 14건, 전체 Java 1,723개 컴파일 통과. 실제 클라이언트·운영 DB의 전체 아이템 사용 및 운영 성능은 미검증입니다. **PATCH는 Java 45개(신규 21개 포함)를 모두 적용하고 JDK 21 / UTF-8 / `--enable-preview`로 Clean/Rebuild·재시작하세요.**

---

## 2026-11-03 — 자동사냥 타깃 안정화·NPC 예약 길막 대기 수정

[전체 소스 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Target_Stability_Chase_Pause_Fix_20261103_FULL_SRC.zip) · [Java 4개 패치](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Target_Stability_Chase_Pause_Fix_20261103_PATCH.zip) · [적용·검증 안내](TARGET_STABILITY_CHASE_PAUSE_FIX_20261103.md)

진행 중인 자동사냥 타깃을 유지하고, 실제 길막의 전환 대상을 좁혀 빈번한 교체를 억제했습니다. NPC는 예약 길막이 우회 탐색을 건너뛰던 문제와, 막힌 예약 경로에 300ms씩 기다리던 문제를 수정했습니다. 열린 다른 경로는 같은 틱에 재탐색하며 평상시 랜덤워크 주기는 유지합니다.

자동사냥 30건·NPC 경로 11건 및 관련 검사 스크립트 6개, 전체 Java 1,702개 컴파일 통과. 실제 클라이언트·운영 부하는 미검증입니다. **패치 4개 파일 모두 적용 후 JDK 21 / UTF-8 / `--enable-preview`로 Clean/Rebuild·재시작하세요.** 직전 8개 파일 추적 복구 패치 미적용 시 FULL_SRC를 기준으로 병합하세요.

---

## 2026-11-03 — 자동사냥 타깃 왕복·정지 / NPC 길막 추적 복구

- [전체 수정 소스 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Chase_Recovery_Fix_20261103_FULL_SRC.zip)
- [변경 Java 8개 패치 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Chase_Recovery_Fix_20261103_PATCH.zip)
- [원인·검증·적용 안내](CHASE_RECOVERY_FIX_20261103.md)

자동사냥 시작 때 이미 보이는 몬스터를 후보에 넣고, 뒤 몹 추적 중 공격 가능한 앞 몹을 우선하도록 수정했습니다. 막힌 대상의 즉시 재선택을 보류하고 실제 좌표의 정지·짧은 왕복을 감지해 THINK로 복구합니다. NPC는 근접/원거리의 몸체 충돌과 경로 실패에도 기존 우회 탐색을 연결해 앞 몹이 살아 있는 동안 열린 옆길로 접근합니다. 우회할 공간이 없는 한 칸 통로에서는 기존 충돌을 지킵니다.

자동사냥 새 회귀 24건·NPC 새 회귀 10건, 기존 검사를 포함한 17개 테스트 스크립트와 전체 Java 1,702개 파일 컴파일을 통과했습니다. 실제 DB·클라이언트 장면과 운영 부하는 미검증입니다. **패치의 신규 클래스 포함 Java 8개를 모두 적용하고 JDK 21 / UTF-8 / `--enable-preview`로 Clean/Rebuild·재시작하세요.** 전체 소스는 이전 NPC·스프라이트 수정도 포함하는 공개 main 기준입니다.

---

## 2026-11-03 — 변신/스프라이트 액션 배열 메모리 절감

- [한 파일 패치 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Sprite_Action_Memory_Fix_20261103_PATCH.zip)
- [기존 NPC 수정 포함 전체 소스](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Sprite_Action_Memory_Fix_20261103_FULL_SRC.zip)
- [실측·검증·적용 안내](SPRITE_ACTION_MEMORY_FIX_20261103.md)

`SpriteInformation.java`의 스프라이트별 256칸 고정 배열을 실제 최고 액션 번호까지만 할당하도록 수정했습니다. 비어 있는 테이블은 공유하고, 조회는 직접 인덱싱 O(1)·추가 할당 없이 유지합니다. 프레임 계산과 변신별 액션/레벨별 속도 코드는 유지합니다.

실제 Java 배열 크기 측정에서 0/1/8 액션을 가진 스프라이트 1만 개의 배열 점유는 10.4MB → 0.56MB, 최고 ID 99인 경우 4.16MB였습니다. 서버 전체 힙 절감률이 아니며 실제 DB 분포에 따라 달라집니다. 회귀 8개 그룹·조회 100만 회 추가 할당 0바이트·전체 Java 1,701개 타입 컴파일을 확인했습니다. 실제 서버 데이터/클라이언트 검증은 미실행입니다. **패치의 SpriteInformation.java를 교체하고 JDK 21 / UTF-8 / `--enable-preview`로 재빌드·재시작하세요.**

---

## 2026-11-03 — NPC 추적 중 800ms 강제 멈춤 제거

- [한 파일 패치 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/NPC_Chase_Cadence_Fix_20261103_PATCH.zip)
- [직전 타깃 전환 수정 포함 전체 소스](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/NPC_Chase_Cadence_Fix_20261103_FULL_SRC.zip)
- [원인·검증·적용 안내](NPC_CHASE_CADENCE_FIX_20261103.md)

`SetMoving.java`에서 한 칸 이동 성공 후 정상 보행 간격을 최소 800ms로 덮어쓰던 처리를 제거했습니다. ThreadLocal 경로 탐색기나 NPC ID 캐시 슬롯이 바뀌면 최초 표시가 다시 설정되어 같은 타깃 추적 중에도 멈칫할 수 있었습니다. 이제 표시는 소진하되 정상 보행 간격을 유지합니다. 길막/경로 실패의 대기와 타깃·어그로 판정은 유지하며, 실제 관찰된 모든 멈춤의 유일한 원인으로 확정한 것은 아닙니다.

전체 Java 1,701개 타입 컴파일을 통과했습니다. 실제 서버/클라이언트 장면은 미검증입니다. **기존 소스에 패치의 SetMoving.java를 교체하고 JDK 21 / UTF-8 / `--enable-preview`로 Clean/Rebuild하세요.**

---

## 2026-11-03 — NPC 공격 중 새 공격자로 타깃 전환 수정

- [수정된 전체 소스 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/NPC_Attack_Retarget_Fix_20261103_FULL_SRC.zip)
- [한 파일 패치 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/NPC_Attack_Retarget_Fix_20261103_PATCH.zip)
- [원인·검증·적용 안내](NPC_ATTACK_RETARGET_FIX_20261103.md)

A를 공격하는 NPC를 B가 때려 B의 공격 우선순위가 더 높아져도 A만 계속 공격하는 누락을 수정했습니다. 새 타깃의 예약·적용 처리가 추적 3종에만 있고 `ATTACK`에 빠져 있었습니다. `AiRuntime.java` 한 파일에서 공통 판정을 사용하도록 수정해 정상 CIO 공격 간격이 끝나는 시점에 새 대상을 적용합니다. 기존 어그로 계산과 시전/도주/대상 유효성 검사는 유지합니다.

실제 욕구 큐·피격 처리·CIO 사전 검사·공격 메서드를 연결한 새 회귀 15건, 기존 NPC·AI 검사 스크립트 11개, 전체 Java 1,701개 타입 컴파일을 통과했습니다. 게임·월드·스케줄러 경계는 테스트 대체 객체를 사용했으며 실제 클라이언트 통합은 미검증입니다. **패치의 AiRuntime.java를 교체하고 JDK 21 / UTF-8 / `--enable-preview`로 Clean/Rebuild하세요.** 전체 소스는 직전 공개 main 기준이며, 미게시 아이템 리팩토링 V2 전체는 포함하지 않습니다.

---

## 2026-11-03 — CacheD 전송 대기 중 장비 슬롯 교체 오류 수정

- [수정된 전체 소스 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/CacheD_EquipSwap_Fix_20261103_FULL_SRC.zip)
- [변경 파일 4개 패치 ZIP](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/CacheD_EquipSwap_Fix_20261103_PATCH.zip)
- [적용·검증 안내](CacheD_EQUIP_SWAP_FIX_20261103.md)

사용자가 첨부한 V6 AlternateEntrance 전체 소스 기준입니다. CacheD 전송 대기 중 슬롯 교체가 장비·능력치를 먼저 변경한 뒤 `Item mutation refused while CacheD transfer pending` 예외를 던지는 문제를 재현했습니다. 기존 전송 게이트를 교체 시작 전에 예약하고, 대기 중에는 변경 없이 재시도 안내를 보내며 성공 슬롯 패킷은 교체 완료 후 보냅니다. NPC AI 소스와 DB 스키마는 변경하지 않았습니다.

실제 수정 메서드와 전체 SlotChangeManager를 사용하는 회귀 14개, 기존 NPC·AI 대표 검사, Java 1,701개 구문 분석을 통과했습니다. 게임 의존성은 테스트 대체 객체를 사용했으며, 전체 서버 타입 컴파일·DB·클라이언트 통합은 미검증입니다. **패치의 Java 파일 4개를 모두 교체하고 JDK 21 / UTF-8로 Clean/Rebuild하세요.**

---

## 2026-11-03 — NPC 액션 방어 검증 패치 (호환성 제한 있음)

- [UTF-8 전체 소스](https://raw.githubusercontent.com/choeyeonggeol16-sketch/1234/main/downloads/NPC_HTML_Action_Guard_20261103_UTF8_FULL_SRC.zip)
- [UTF-8 패치](https://raw.githubusercontent.com/choeyeonggeol16-sketch/1234/main/downloads/NPC_HTML_Action_Guard_20261103_UTF8_PATCH.zip)
- [검증·적용 제한 상세](NPC_HTML_ACTION_GUARD_20261103.md)

이번 사용자 src.zip 기준 NPC 대화·대상·맵·거리·만료 검사, 일회성 서버 발급 변신 메뉴, 귀걸이/목걸이 보유 중 재지급 방어를 추가했습니다. 실제 helper/XML dispatch 25건과 실제 지급 메서드 추출 회귀를 통과했습니다. 게임 의존성은 테스트 대체 클래스를 사용했고 전체 게임/DB 기동은 미검증입니다.

**호환성 제한:** NpcId가 없는 첨부 XML 액션 254개는 담당 NPC를 지정하기 전까지 차단됩니다. 유료 NPC 변신은 허용 NPC 번호를 설정하기 전까지 잠깁니다. 정상 기능 제한을 확인한 뒤 검증 서버에서 적용하세요. 전체 HTML 명령 정책이나 창고 이동 후 재지급까지 완전히 해결한 패치가 아닙니다. 상세 문서와 ZIP 안의 CSV를 먼저 확인하세요. 모든 Java 소스는 UTF-8입니다.

---

## V3 UTF-8 — 한글 소스 인코딩 통일본

- [UTF-8 전체 소스 ZIP](https://raw.githubusercontent.com/choeyeonggeol16-sketch/1234/main/downloads/Netty42_Monster_Burst_Fix_V3_UTF8_FULL_SRC.zip)
- [GameServer.java UTF-8 파일](https://raw.githubusercontent.com/choeyeonggeol16-sketch/1234/main/downloads/GameServer_UTF8/GameServer.java)

기존 V3의 한글 Java 파일 533개는 MS949(CP949)였습니다. 한글 내용을 바꾸지 않고 UTF-8로 무손실 변환했습니다. 나머지 ASCII 파일은 그대로이며 전체 Java 970개 구문 분석을 통과했습니다. 전체 소스를 사용하면 프로젝트와 빌드 인코딩을 UTF-8로 설정하고 Clean/Rebuild하세요. GameServer.java만 교체하면 해당 파일의 인코딩만 UTF-8로 설정하고 다른 MS949 파일의 프로젝트 설정은 유지하세요. 전체 게임 타입 컴파일·기동 검증은 아닙니다. 콘솔 출력 인코딩이나 DB의 이미 손상된 문자 문제를 고친다는 의미는 아닙니다.

---

## 2026-11-03 — V3: 사용하지 않는 광고 서버 비활성화

- [V3 전체 소스 다운로드](https://raw.githubusercontent.com/choeyeonggeol16-sketch/1234/main/downloads/Netty42_Monster_Burst_Fix_V3_20261103_FULL_SRC.zip)
- [V3 패치 다운로드](https://raw.githubusercontent.com/choeyeonggeol16-sketch/1234/main/downloads/Netty42_Monster_Burst_Fix_V3_20261103_PATCH.zip)

사용하지 않는 광고 서버의 초기화·종료 호출을 GameServer에서 제거했습니다. V2 적용자는 GameServer.java만 교체하고 재빌드하세요. 게임 Netty 송신 설정은 유지합니다. 전체 소스 참조 검사와 Java 구문 분석 통과, 전체 게임/DB 기동은 별도 검증이 필요합니다.

---

## 2026-11-03 — V2: 광고 서버 기동 오류 수정 포함

- [V2 전체 소스 다운로드](https://raw.githubusercontent.com/choeyeonggeol16-sketch/1234/main/downloads/Netty42_Monster_Burst_Fix_V2_20261103_FULL_SRC.zip)
- [V2 패치 다운로드](https://raw.githubusercontent.com/choeyeonggeol16-sketch/1234/main/downloads/Netty42_Monster_Burst_Fix_V2_20261103_PATCH.zip)

이전 몬스터 순간 송신 수정 후 `AdCheckManager.init`에서 발생한 `globalOutboundBytes must be in 2097152..17179869184` 오류를 수정했습니다. 광고 리스너에 별도 세션 예산 512KiB/512개를 명시합니다. 게임 세션 2MiB/8192개는 유지합니다. 이전 수정본 사용자는 AdCheckManager.java를 교체하고 재빌드하면 됩니다. 실제 광고 서버 초기화·LE32 TCP 요청·연결 정리 회귀를 통과했으며 게임 DB 통합은 별도입니다. ZIP 내부 적용 안내를 확인하세요.

---

## 2026-11-03 — Netty 몬스터 대량 생성 송신 제한 수정

- [수정 전체 소스 ZIP 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Netty42_Monster_Burst_Fix_20261103_FULL_SRC.zip)
- [교체 파일 패치 ZIP 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Netty42_Monster_Burst_Fix_20261103_PATCH.zip)
- [전체 소스 SHA-256](downloads/Netty42_Monster_Burst_Fix_20261103_FULL_SRC.zip.sha256) · [패치 SHA-256](downloads/Netty42_Monster_Burst_Fix_20261103_PATCH.zip.sha256)

사용자가 제공한 `Netty42_Mailbox_Progress_ReadResume_20261103_FULL_SRC.zip` 기준입니다. 기존 송신 한도 512개/512KiB에서 정상 순간 송신도 연결 종료되는 문제를 재현해 기본값을 8,192개/2MiB로 조정하고 제한 원인 로그를 추가했습니다. 실제 Netty TCP 6,000개 패킷 전달·입력 진행·동시 송신·예산 반환 등 6종 검사를 통과했습니다. 실제 게임 AI/DB 대량 생성 부하는 미재현이며, 먹통의 유일한 원인으로 확정하지 않습니다. 아래 이전 통합본과 기준 소스가 다릅니다.

**적용:** ZIP 내부 `APPLY_MONSTER_BURST_FIX_KO.md`를 확인하고 JDK 21로 재빌드하세요. 기존 `config/network.properties`에 설정이 있다면 다른 항목은 유지하고 `outboundPackets=8192`, `outboundBytes=2097152`로 변경해야 합니다. 테스트 코드와 로그도 ZIP에 포함했습니다.

---

**최신 전체 소스 통합본 — 2026-11-02**

**[전체 src 970개 + Ant 빌드 설정 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_Netty_4.2.19_Final_ALL_SRC_970_20261102.zip)**

지금까지 수정한 내용을 모두 합친 전체 소스입니다. Java 파일 970개를 모두 포함합니다. 기존 프로젝트의 src를 백업한 뒤 ZIP 안의 src 전체와 build.xml로 교체하고, 기존 비-Netty lib 및 게임 데이터를 보존하세요. 전체 Ant 컴파일과 JAR 생성 성공: 오류 0, warning 0. 사용자 Eclipse의 실제 호환 오류 및 GUI/DB 기동은 미재현입니다.

아래는 이전 통합본과 부분 수정 기록입니다.

**2026-11-02 — Netty 4.2.19.Final 네트워크 수정본**

[XML helper의 SAXException 명시 참조 수정 누적 패치](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_Netty42_SAX_Reference_Fix_20261102.zip) — private 메서드의 throws를 Exception으로 통일하고 예외 전파 유지. 전체 970개 컴파일 및 실제 XML 정상/오류 입력 검사 통과. 사용자 Eclipse 오류 자체는 미재현.

[삭제 예정 생성자 경고 45곳 수정 패치](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_Netty42_Warnings_Fix_20261102.zip) — Java 26개 파일과 Ant 빌드 파일. 서버 970개 전체를 `-Xlint:removal -Werror`로 컴파일하고 JAR 생성 성공: 오류 0, warning 0. 일반 deprecated API 안내 Note는 남아 있습니다.

[Ant build.xml 수정본 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Netty42_Ant_Build_Fix_20261102.zip) — Netty/JAXB 빌드 클래스패스 수정. 실제 서버 Java 970개 전체 컴파일 및 JAR 생성 성공(오류 0, 경고 45). GUI/DB 기동은 미검증.

[Netty JAR 5개·Windows 실행 예제 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/Netty_4.2.19_Final_libs.zip)

**[전체 수정 소스 ZIP 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_Netty_4.2.19_Final_Full_Source_20261102.zip)** · [검증 보고서](RKern_NETTY42_20261102_VALIDATION.md) · [SHA-256](downloads/RKern_Netty_4.2.19_Final_Full_Source_20261102.zip.sha256)

마지막에 업로드한 src.zip 기준으로 자체 NIO를 Netty 4.2.19.Final로 교체했습니다. JDK 21이 필요합니다. 전체 수정 소스, Netty JAR 5개, 설정, 재현 테스트와 로그를 포함합니다. 실제 TCP 동시 연결 1,000개·암호화 요청/응답 20,000회와 18개 네트워크 회귀 검사를 통과했습니다. 원본 게임/DB 의존성이 없어 전체 게임 서버 빌드·기동 및 실제 게임 동접은 미검증입니다.

---

# RKern FIFO/group·batch/READ 패치 재시험 — 2026-10-28

[최신 재시험 보고서](RKern_P0_P2_RETEST_REPORT_20261028.md) · [코드·원시 결과·회귀 시험 ZIP 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_P0_P2_retest_evidence_20261028.zip)

직전 업로드 대비 기본 작은 묶음 +50.5%, 1,024연결 +30.1%, 4KiB 송신 +14.2%. FIFO 오류 재현 조건은 통과했습니다. 별도 batch 호출은 Netty epoll 처리량과 동률권이지만 실제 게임 호출 연결은 아직 없고, 4KiB p99 및 임시 SEND 버퍼 회귀가 남았습니다. 프로파일 없는 성능 실행 72회, 정합성 실행, 실패 단언과 정제 프로파일 근거를 포함합니다. 업로드 소스를 추가 수정하지 않은 검증 자료이며 전체 서버 배포 ZIP은 아닙니다.

# RKern 네트워크 재비교 — 2026-10-28

[Netty epoll을 넘기 위한 구현 지침](RKern_NETTY_EPOLL_IMPLEMENTATION_PLAN_20261028.md) · [추가 진단 근거 ZIP](downloads/RKern_netty_epoll_plan_evidence_20261028.zip)

후속 진단에서 owner SEND의 순서 우회, READ 완료의 간접 executor 경로, batch self-pending fallback을 확인했습니다. 수정 위치·순서·유지 조건·합격 시험과 승리 목표를 정리했습니다. 진단 ZIP은 배포용 패치가 아닙니다.

[업로드한 개선 코어와 Netty epoll 비교 보고서](RKern_NETTY_EPOLL_REBENCH_20261028.md) · [재현 코드·원시 결과 ZIP](downloads/RKern_netty_epoll_rebench_20261028.zip)

이전 v5·새 업로드·Netty epoll을 같은 환경에서 다시 측정했습니다. 유효 성능 실행 54회와 별도 고부하 정합성 오류 기록을 포함합니다. 업로드 소스는 수정하지 않았으며 기본 설정과 보완 설정의 결과를 구분했습니다.

# RKern 최신 수정본 v5 — NPC AI 최신 소스 + 네트워크 개선

**[전체 소스 v5 ZIP 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_full_src_NPC_AI_network_v5_20261027.zip)**

[수정·검증 보고서](RKern_NPC_AI_NETWORK_v5_REPORT.md)

[L2NPC.exe와 현재 NPC AI 비교 분석](L2NPC_AI_COMPARISON.md) — Desire 핵심 대응, 실제 호출 경로, 원본과 다른 스킬·배회·파라미터 처리 및 검증 범위.

[RKern v5 vs Netty 실제 TCP 비교](RKern_NETTY_BENCHMARK_REPORT.md) — 동일 CPU·패킷·암호화로 NIO/epoll 비교, 54회 성능 측정 및 재현 자료. NPC·게임 소스 변경 없음.

[네트워크 프로파일링·수정 작업 지침](RKern_NETWORK_PROFILE_GUIDE.md) — 소형 패킷의 pool/등록 비용, 4KiB 암호화 비용, p99 추가 계측과 단계별 수정·검증 순서.

새로 업로드한 NPC AI 소스 기준입니다. 짧은 액션의 임시 SEND 버퍼 대여·반환을 줄이고 v4 수신 정리를 병합했습니다. NPC AI 본체를 보존했으며, 원본의 구·신 desire 타입 충돌 11개는 호환 위임으로 해결했습니다. 전체 Java 1,670개 컴파일과 네트워크 회귀·실제 로컬 TCP 검사를 통과했습니다. DB 추가 변경은 없습니다.

이하 이전 v4/v3 기록입니다.

# RKern v4 기록 — 네트워크 경로 정리

**[전체 소스 v4 ZIP 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_full_source_v4_network.zip)**

[네트워크 수정·검증 보고서](RKern_v4_NETWORK_REPORT.md)

v3 기준 네트워크 수신 파일 1개를 49줄 줄였습니다. 전체 컴파일, 제어 I/O 회귀 238개, 실제 로컬 TCP 검증을 통과했습니다. 합성 측정에서 처리량 향상은 확인되지 않았습니다. v3의 경제/로그인 수정과 기존 SQL 3개를 포함하며 추가 DB 변경은 없습니다.

이하 v3의 DB 적용 안내와 기존 검증 기록입니다.

# RKern v3 기록

**[전체 소스 v3 ZIP 다운로드](https://github.com/choeyeonggeol16-sketch/1234/raw/refs/heads/main/downloads/RKern_full_source_v3.zip)**

[수정·검증 보고서](RKern_v3_REPORT.md)

## DB 보완 SQL 3개

원본 net.sql을 가져온 DB를 백업하고 서버를 내린 뒤 다음 순서로 실행합니다. 계정·캐릭터·아이템 데이터를 초기화하지 않습니다.

1. [거래 영수증 테이블](database/CacheD_transaction_receipts_migration.sql)
2. [경매 미지급 정산금 테이블](database/CacheD_economy_credit_migration.sql)
3. [관련 테이블 InnoDB 변환](database/CacheD_economy_tables_migration.sql)

SQL 3개는 v3 소스 ZIP의 database/에도 들어 있습니다. 새 DB라면 먼저 net DB를 만들고 사용자가 가진 원본 net.sql을 임포트해야 합니다. 이 SQL 3개는 기존 게임 DB 보완용입니다.

## 이번 수정

- COMMIT 후 인덱스·콜백 실패를 확정 롤백과 구분해 양쪽 FIFO/아이템 게이트를 격리합니다.
- 로그인·캐릭터 생성·경매의 게임 상태 반영을 기존 CIO 소유 실행 경계로 전달합니다.
- 전체 Java1,657개 컴파일, 분리 회귀14종, 실제 MySQL5.7.18의149개 단언을 통과했습니다.
- 받은 DB의 대상19개 테이블11,903행을 보존하며 SQL3개 적용을 확인했고, 실제 DB 강제 종료/재시작 복구도 통과했습니다.
- 실제 게임 서버·클라이언트 전체 통합은 미실행이며 이전의 미해결 경로/기능 제한은 보고서를 확인하세요.

원본 net 덤프와 플레이어 데이터는 GitHub에 게시하지 않았습니다. 이전 v2 ZIP/보고서는 역사적 기록이며, 위 v3가 이번 피드백 수정본입니다.
