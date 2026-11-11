# HP/MP 재점검 보완본

[**최신 전체 src 다운로드**](../../downloads/RKern_Aden_ALL_FIXES_HPMP_RECHECK_FULL_SRC_20261112.zip) · [재점검 보고서](../../reports/RKERN_HPMP_RECHECK_20261112.md) · [필수 SQL](../../database/RKern_HPMP_GROWTH_20261112.sql) · [보완 diff](recheck.patch) · [검증 로그](logs)

이전 `aab3d06`을 재점검하여 저장 교착, 초기화 중간값 저장, 미완료 초기화 확정, 최종 HP/MP 회복 순서를 보완했습니다. 기존 네트워크·스레드풀·마법·물리·HP/MP 수정 모두 포함, ZIP에는 **src/만** 들어 있습니다. 소스 1,697개/Java 1,696개이며 이전 전체 src 대비 4개 Java만 변경했습니다.

**기존 HP/MP SQL 컬럼이 필수입니다. 이미 적용했다면 추가 SQL은 없습니다.** 미적용 DB는 서버 종료·백업 후 위 SQL을 한 번 적용합니다. 자세한 적용 순서와 기존 데이터 정책은 [이전 안내](../rkern_hpmp_20261112/README.md)를 따릅니다.

네 가지 이전 결함의 실패 재현과 수정 후 통과, 실제 공개 초기화 핸들러의 성장 메시지, 실제 저장 진입점의 잠금 경합 검사를 추가했습니다. 전체 컴파일과 기존 HP/MP·H2 저장·마법·물리 회귀도 통과했습니다. **Windows 실게임·운영 MySQL/CacheD·전체 초기화 UI 통합은 미검증**입니다. 상세 범위와 대역은 재점검 보고서를 참고하세요.

재현(JDK21/Python3):

```sh
python3 tests/test_hpmp.py
python3 tests/test_hpmp.py --src /path/to/src --libs /path/to/lib
```

기본 실행은 저장소의 보완 ZIP과 공개 Maven 의존성을 사용하며 운영 DB에 접속하지 않습니다. `logs/recheck-before.log`는 수정 전 실패 증거이며 현재 버전 실패가 아닙니다.
