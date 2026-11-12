# 중복 실행 정리 통합 src

[**최신 전체 src 다운로드**](../../downloads/RKern_Aden_ALL_FIXES_DEDUP_FULL_SRC_20261112.zip) · [수정·검증 보고서](../../reports/RKERN_DUPLICATE_LOGIC_FIXED_20261112.md) · [점검 명세](../../reports/RKern_JP_Ainhasad_Blessing_TW_ArmorBreak_Formula_Dedup_Audit_20261112.md) · [diff](dedup.patch) · [로그](logs)

기준 `7bf315d`. 사냥 아인 중복 차감/PC방 보너스, 비사냥 경험치 중복 갱신, 회복 시간 겹침과 계정 갱신 복제를 정리하고 아머 브레이크 계산 순서를 보완했습니다. 기존 Java8개 변경, Java1,696개/전체 소스1,697개. ZIP에는 **src/만**, 이전 수정을 모두 포함합니다.

**추가 SQL 없음. 기존 HP/MP SQL은 필수이며 이미 적용했다면 다시 실행하지 않습니다.** [필수 SQL](../../database/RKern_HPMP_GROWTH_20261112.sql). 기존 라이브러리와 UTF-8/Java21/FFM 옵션으로 재빌드합니다.

전체 컴파일·새 실패 재현/수정 후 검사·기존 회귀 통과. 2015일본6,059상수는 운영 기본값으로 바꾸지 않았습니다. Windows/운영 DB/CacheD 데몬 및 경험치·계정 간 강제 종료 원자성은 검증하지 않았습니다. 세부 정책과 한계는 보고서를 참고하세요.

재현(JDK21/Python3):

```sh
python3 tests/test_hpmp.py
python3 tests/test_hpmp.py --src /path/to/src --libs /path/to/lib
```

기본 실행은 저장소 전체 ZIP 및 공개 Maven 의존성을 사용합니다. 운영 DB에 접속하지 않습니다. `dedup-before.log`는 이전 버전 실패 증거입니다.
