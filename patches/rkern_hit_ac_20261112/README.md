# 물리 명중·AC 통합 수정

기준 `3ca9000`, 보고서 main `f982388`. Java3개 수정. 기존 전체 수정 유지, 새 SQL 없음.

- [전체 src ZIP](../../downloads/RKern_Aden_ALL_FIXES_HIT_AC_FULL_SRC_20261112.zip)
- [상세 수정·검증·남은 한계](../../reports/RKERN_HIT_AC_FIXED_20261112.md)
- [기준 보고서](../../reports/RKern_JP2016_PhysicalHit_AC_33000_Verification_20261112.md)

## 적용

1. 기존 프로젝트 src와 DB를 백업합니다.
2. ZIP의 src/ 전체를 반영하고 기존 프로젝트 라이브러리·DLL·운영 설정으로 다시 빌드합니다. Java21 preview, 소스 UTF-8.
3. 기존 HPMP SQL을 적용하지 않았다면 `../../database/RKern_HPMP_GROWTH_20261112.sql`의 hp_mp_growth 컬럼을1회 추가합니다. 이미 있다면 추가 SQL 없음.
4. 운영 투입 전 테스트 서버에서 근/원거리 PC/NPC 명중, 저AC, 키링크·펫/투견·회피 스킬을 확인합니다.

기본 명중 범위0~100%, AC 역전 제거, 저AC 무제한 외삽 차단, signed AC 및 PC H 오버플로를 고쳤습니다. 저AC0~63은 AC64 경계값으로 제한되어 평평하며, 본섭 공식 전체 복원을 의미하지 않습니다. 일본 표시H147의 의미를 확인하지 못해 H-13 보정은 넣지 않았습니다. 원 보고서 두 캐릭터의0/100% 실험은 인게임 재현하지 않았습니다.

## 로컬 검증 재실행

이 폴더에서 압축을 푼 src와 라이브러리를 지정합니다.

```bash
python tests/test_hpmp.py --src /path/to/src --libs /path/to/test-libs --build-dir /tmp/rkern-hit-classes
```

Java21의 jdk.compiler 모듈 필요. libs 생략 시 Maven Central에서 기존 검사와 같은 의존성을 가져옵니다. 전체1696Java 컴파일 후 기존 회귀와 HitEvasionTest/HitPathTest를 실행합니다. 테스트용 카운터 HitEvasionModel은 임시 fixture 클래스 경로에서만 사용하며 생산 src에 포함되지 않습니다. 운영 서버·DB·DLL을 기동하지 않습니다.

`hit-ac.patch`는 직전 DEDUP 전체 src 기준입니다. 전체 ZIP 반영과 패치 적용 중 한 방법만 사용합니다. 모든 파일의 해시는 SHA256SUMS에 있습니다.
