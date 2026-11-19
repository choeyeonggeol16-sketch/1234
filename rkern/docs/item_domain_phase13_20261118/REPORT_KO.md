# RKern 아이템 기능별 분리 13차 (2026-11-18)

## 기준·범위

- 입력: `RKern_ItemFunction_DomainSeparation_Phase12_FULL_SRC_20261118.zip` (12차 전체 소스)
- 생산 기존 변경: `ItemBonusHooks.java`, `IceBuffItem.java`, `TeleportUseService.java` 3개
- 생산 신규 클래스: `ItemBonusPrimitiveMaps`, `DollTickBonuses`, `DollExperienceBonuses`, `EquipmentExperienceBonuses`, `IceBuffEffects`, `IceBuffSingleUse`, `IceBuffAllUse`, `TeleportItemRules`, `TeleportRandomTravel`, `TeleportClanBookmarkTravel` 10개
- 기존 외부 진입점과 메서드 시그니처는 그대로 유지. 실제 처리 본문을 역할별로 옮김.
- 방송, 네트워크, NPC, DB 공통 코드 및 다른 production Java는 변경하지 않음.

## 구체적 보존 항목

- 인형: HP/MP 회복 테이블·최댓값 우선 선택·경험치 누적 및 이중 소환 방지·해제 시 정확한 차감
- 장비: 아르카 경험치 가산·문장 배율 슬롯별 적용 및 해제·장비 강화 시 다시 계산
- 빙수: 3개 단일 아이템과 통합 버프, 원래 아이템 소모 receipt/lambda 경계, 기존 효과 제거/부여 및 스킬/패킷 순서
- 텔레포트: GM/PSS 구분, 북마크 검사, 맵·아이템·시간 제한, 랜덤 및 클랜 주변 이동, 소비 item receipt, 실패 메시지와 tpEndFinish 순서
- 두 primitive 맵의 해시·open addressing·rehash 구현과 테이블을 그대로 이전. 추가 스레드/큐/사용당 컬렉션 생성 없음.

## 완료한 검사

- 모든 생산 Java 1,895개 Java 21 구문 분석 오류 0.
- 기존 메서드 본문 30개 byte-identical. 기존 `IceBuffItem.use`와 텔레포트 두 공개 진입점은 기존 호출을 새 소유 클래스로 치환한 것 외 본문 동일.
- 실제 신규 생산 클래스 Java 21 typed mock compile 통과. 12차와 런타임 입력 비교: 인형/장비 65,920개, 빙수 960개, 텔레포트 36,792개, 합계 103,672개.
- 12차~8차 기능/아이템 회귀와 방송 114차 회귀 검사 재통과. 방송 테스트는 고의 삽입 송신 예외를 정상적으로 격리하는지 시험함.
- 변경 파일 목록, 12차 나머지 모든 파일 SHA-256 동일성, ZIP CRC, 중복 경로 없음 검사 통과.

재검증: `bash docs/item_domain_phase13_20261118/run_tests.sh`

## 미검증 및 한계

전체 게임 의존성 빌드/전체 서버 실행, 실서버 CacheD/MySQL, 동시접속, 운영 인게임 효과는 별도 검증이 필요하다. Typed-stub 기반 비교는 전체 게임 타입 컴파일을 대체하지 않는다. 텔레포트 난수 이동 위치 비교는 통제한 시험 모델에서만 수행했다.
