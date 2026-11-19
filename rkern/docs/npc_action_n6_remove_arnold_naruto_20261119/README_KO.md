# 아놀드·나루터 전용 NPC 보상 제거

기준: N6 FireGolem Weapon Receipt 전체 SRC

- CS_NpcAcion2: NPC ID 7(아놀드), 9(나루터) 일반/특별/빛나는 보상 6개 분기 제거. 해당 분기 전용 경험치 함수 삭제.
- L1MerchantInstance: 아놀드 전용 anold1/anold2 대화 화면 분기 제거. 다른 NPC 및 에킨스 분기 유지.
- 기존 불골렘 회귀 테스트의 삭제된 분기 경계만 갱신. 원래 검증 조건은 유지.
- 실제 DB, NPC/스폰/HTML 데이터, 기존 아이템 및 스킬 정의, 기존 GM 정리 기능은 변경하지 않음. NPC 자체를 월드에서 없애는 것은 운영 데이터에서 별도 처리 필요.
- 다른 명령과 일반 XML/HTML 기본 처리 경로는 유지.
