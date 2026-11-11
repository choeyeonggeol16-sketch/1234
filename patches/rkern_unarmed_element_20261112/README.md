# 맨손·속성 저항 수정 통합본

[**최신 전체 src 다운로드**](../../downloads/RKern_Aden_ALL_FIXES_UNARMED_ELEMENT_FULL_SRC_20261112.zip) · [구현·중복 감사 보고서](../../reports/RKERN_UNARMED_ELEMENT_IMPLEMENTED_20261112.md) · [요청 보고서](../../reports/RKern_Lineage1_JP_Unarmed_ElementalResistance_Verification_20261112.md) · [diff](unarmed-element.patch) · [검증 로그](logs)

기준 `67d82d0`. 맨손 1/2 및 최종 피해 태그, 활 전용 변신 제한, NPC 폴다운 저항 중복 차감, 속성 상한/음수/구식 산술 오류를 보완했습니다. 이전 IOCP·스레드풀·마법·물리·HP/MP·재점검 수정 모두 포함합니다. ZIP은 **src/만**, Java1,696개(총 파일1,697개), 기존 Java10개 변경입니다.

**추가 SQL은 없습니다. 기존 HP/MP SQL 컬럼은 필수이며 이미 적용했다면 다시 실행하지 않습니다.** [필수 SQL](../../database/RKern_HPMP_GROWTH_20261112.sql) / [기존 적용 안내](../rkern_hpmp_20261112/README.md).

전체 컴파일·기존 회귀·새 맨손/속성/중복 재현 검사를 통과했습니다. 특별한 1피해 NPC ID(U07)는 운영 DB 매핑을 확인할 수 없어 임의로 추가하지 않았습니다. Windows 실게임·운영 DB와 실제 맵/변신 데이터는 미검증입니다. 정확한 적용 범위와 대역은 구현 보고서를 참고하세요.

검증 재현(JDK21/Python3):

```sh
python3 tests/test_hpmp.py
python3 tests/test_hpmp.py --src /path/to/src --libs /path/to/lib
```

기본 실행은 저장소의 최신 ZIP 및 공개 Maven 의존성을 사용합니다. 운영 DB에는 접속하지 않습니다. `logs/element-before.log`는 이전 버전의 실패 재현 기록입니다.
