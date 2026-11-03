# NPC class_id DB 직접 로딩 SRC — 2026-11-04

이번에 첨부한 src.zip 기준 운영 Java 4개를 수정했습니다. 전체 운영 Java는 1,691개입니다. **npc.class_id를 직접 읽으며 별도 설정 파일을 사용하지 않습니다.** 이전 NpcClassIdConfig 및 properties 로딩을 제거했습니다. 기존 설정 파일에 10030이 남아 있어도 DB 값을 덮어쓰지 않습니다.

NpcTable이 부팅 시 기존 NPC 조회 결과에서 class_id를 읽어 템플릿에 저장하고, 일반 몬스터 등장 시 기존 직접 송신기와 프로토콜 팩토리 두 경로에서 39번 필드로 보냅니다. 등장/IO 처리마다 DB나 파일을 다시 읽지 않습니다. 기존 펫 전용 분기와 일반 NPC 처리, 0 값의 필드 생략은 유지합니다.

## 적용

1. 서버를 내리고 기존 소스/실행 파일을 백업합니다. FULL_SRC의 src를 적용하거나 PATCH의 **Java 4개를 전부** 반영합니다. 이전 설정 시험판의 NpcClassIdConfig.java는 DELETE_OLD_HELPER.txt에 적힌 경로에서 제거할 수 있습니다.
2. JDK 21 / UTF-8 / --enable-preview로 전체 Clean/Rebuild하고 실행 파일을 교체합니다.
3. DB에 컬럼/값이 아직 없다면 내 게임 DB를 선택한 상태로 **database/APPLY_Monsterbook_ClassIds.sql**을 실행합니다. 이미 반영했다면 재적용할 필요 없습니다. 적용·백업/복구 SQL과 대조표를 ZIP에 포함했습니다.
4. 서버를 재시작합니다. **[NPC class_id] loaded N templates from npc.class_id** 로그를 확인합니다. 컬럼이 없으면 적용 SQL 안내 경고가 나오며 기존 방식으로 기동합니다. 소스가 DB 컬럼을 자동 생성하지는 않습니다.
5. 진행 중인 주간퀘스트 대상으로 확인합니다. 어둠의 흑기사 이미지 16853의 class_id는 14606, 이전 첨부 DB의 이미지 15595는 12821입니다. SQL은 이미지·능력치를 변경하지 않습니다.

확인 쿼리: `SELECT npcid,name,gfxid,class_id FROM npc WHERE npcid=7310054;`

동봉 SQL은 첨부 DB의 도감 고유 NPC 612종 중 현재 이미지 기준 385종을 매칭한 버전입니다. 중복/누락 227종은 database/NEEDS_REVIEW.csv에 분리했습니다. 실제 실행 DB의 다른 이미지도 검증된 조합에 있으면 선택하며 실제 변경 수는 SQL 실행 결과에 나옵니다. 게임 DB 적용은 이 환경에서 아직 수행하지 않았습니다.

## 검증

전체 운영 Java **1,691개** 컴파일 통과(기존 경고 5개). 실제 NpcTable 로더에 JDBC 대역을 연결해 DB 값 14606, 변경 값 12821, 컬럼 없음, 음수 및 예전 설정 파일 무시를 확인했습니다. 실제 송신기/팩토리 3경로의 **15개 ID 조합**에서 39번 필드·varint 경계·0 생략·다른 바이트 유지·일반 NPC 제외·프레임 반환을 확인했습니다.

첨부본의 BQSCharacterData를 포함해 다른 운영 Java 1,687개는 그대로 유지했습니다. 원시 diff 적용·전체 소스 해시·ZIP CRC/SHA-256 검증을 제공합니다. 테스트의 JDBC/세션/몬스터 상태는 대역이며 실제 MySQL 부팅·Windows IOCP·이 버전의 게임 화면은 미실행입니다. 14606/16853의 화면 표시는 사용자가 앞서 확인한 결과입니다. 테스트 stubs는 운영 classpath에 넣지 않습니다.

재현: `python3 tools/build_item_refactor.py --output /tmp/npc-db-build` 후 `python3 tests/npc_class_id/test_class_id.py --classes /tmp/npc-db-build/classes`.

PATCH의 validation/source.patch는 이번 첨부 원본 src.zip 기준입니다. 이전 설정 시험판에는 PATCH의 완성 Java 4개를 덮어쓰는 방식으로 반영하세요. 날짜는 한국 시간 기준입니다.
