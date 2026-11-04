# 아이템 사용 코드 정리 V4

최근 첨부한 `RKern_InterServer_Domination_PacketTrace_FULL_SRC_20261104.zip`을 기준으로 수정했다.
기존 사용 진입부·인벤토리·CacheD 트랜잭션을 유지하면서 중복 효과 구현과 반복 할당을 정리했다.

## 실제 변경

- **마법서 학습 6개 중복 구현을 하나로 통합.** 스킬 검색 범위·책 이름·속성 제한·29단계 전송 위치·효과음 차이를 `ItemUseSkillBookLearning.Kind`에 모았다. 기존 공개 메서드는 호환 진입점으로 유지한다. `ItemUseSpellBooks`는 1,396줄에서 403줄로 줄었고, 공통 학습 구현은 별도 79줄이다.
- 스킬별 이름을 매번 문자열로 조립하지 않고 원래 `equalsIgnoreCase` 조건과 같은 방식으로 비교한다. 검색 중복 이름의 누적/최종 선택, 미일치, 습득 여부, 패킷→효과음→학습 저장→아이템 소모 순서를 유지한다.
- **전투 효과의 스턴 지속시간을 전역 변수에서 지역 변수로 변경.** 두 캐릭터가 동시에 사용하며 서로의 시간을 덮어쓰던 경합을 없앴다. 확률식과 지속시간 후보는 유지한다.
- 지속시간 CSV는 사용마다 `split`하지 않고 세 설정의 현재 값만 보관한다. 설정 변경을 반영하며, 잘못된 토큰의 선택 시 예외와 난수 호출 순서도 보존한다. 플레이어·아이템을 캐시에 저장하지 않는다.
- 사용 때마다 생성하던 `Random` 네 곳을 `ThreadLocalRandom.current()`로 바꿨다. 범위와 분포는 유지하며 난수열 자체는 동일하지 않다.
- 큰 전투/학습 분기를 책임별 모듈로 나누고 과거 주석 처리 코드와 불규칙한 들여쓰기를 정리했다. 전투 공개 API 14개, 고급 아이템 분기 52개의 호출·조건·효과·소모 콜백을 유지한다.
- **물약 데이터는 읽기 전용 `Effect[]`와 정수 키 맵으로 변경.** 항목마다 불필요한 CopyOnWriteArrayList 래퍼가 없어지고 조회 시 정수 키를 boxing하지 않는다. XML 효과 순서를 유지한다.
- 물약 XML을 완전히 읽은 뒤 새 데이터만 공개한다. 재로딩 실패 시 기존 정상 데이터를 보존한다. 설정이 없는 회복 아이템은 예외 대신 기존 79번 안내를 보내며 소모하지 않는다.
- 첨부 원본에 있던 `DominationHybrid.getMap(int)` 컴파일 오류에 한해 `short` 캐스팅 한 줄을 포함했다. 인터서버 절차는 바꾸지 않았다.

## 검증

- 전체 **1,702개 Java 소스 컴파일 통과**. 기존 deprecated/removal 경고 5개가 있다.
- 원본 Java **1,686개는 바이트 단위 동일**. 기존 6개 파일 수정, 신규 10개, 삭제 없음. 목록과 해시는 `validation/source_manifest.json`에 있다.
- 원래 마법서 메서드와 새 구현 **3,000개 입력 비교 통과**. 실제 `SC_AddSkill` 직렬화 바이트, 메시지, 효과음, 학습 저장, 소모 순서를 비교했다. 다중 이름 매치·속성·GM·이미 습득·단계·미일치를 포함한다.
- 지속시간 **5,000개 비교 및 동시 접근 12,000회 통과**. 잘못된/선택되지 않은 토큰, 설정 변경, 난수 상태를 포함한다.
- 독립 Java 구문 트리 검사로 전투 메서드 14개와 API 전달, 고급 아이템 분기 52개의 본문·라벨·종료·콜백·경로를 비교했다. 위에 설명한 난수/지속시간 수정만 예외로 허용한다.
- 실제 JAXB로 배열 바인딩, 효과 순서, 정상 교체, 실패 복구, 동시 읽기를 검증했다. 로드 실패 로그 한 건은 실패 복구 테스트가 의도적으로 만든 것이다.
- 기존 사용 단계 **12개**, 트랜잭션 **27개** 테스트 통과. 이전 사용 단계 테스트의 버프 구슬 기대값은 최근 첨부본에서 해당 DB 테이블 경로가 삭제된 상태에 맞췄다. 생산 코드에 기능을 다시 추가하지 않았다.

게임 객체·DB·소켓 경계 일부는 테스트 대체 객체다. 실클라이언트에서 모든 아이템을 사용한 결과는 아니다.
현재 확인한 범위에서 지속적으로 증가하는 메모리 누수는 입증하지 못했다. 이번 변경은 반복 할당·정적 공유 상태·재로딩 안전성 개선이며, 운영 힙 덤프나 서버 전체 메모리 절감률은 측정하지 않았다.

## 적용

FULL_SRC는 최신 첨부본에 이번 변경을 합친 전체 소스다. PATCH에는 수정/신규 Java **16개를 모두** 넣었다.
기존 lib·data·config·DB를 보존하고 전체 Clean/Rebuild 후 재시작한다. 신규 DB 테이블이나 설정은 없다.
빌드/실행은 기존대로 JDK 21과 `--enable-preview`가 필요하다. 새 운영 라이브러리는 필요하지 않다.

검증 재실행 시 `ITEM_DEPS_DIR`은 기존 의존성 jar 디렉터리, `ITEM_BUILD_CLASSES`는 컴파일 결과 classes 디렉터리를 가리킨다.
XML 테스트에만 선택적 JAXB 런타임이 필요하며 `tools/fetch_item_use_test_dependencies.py`로 받을 수 있다.

```sh
python3 tools/build_item_refactor.py --dependencies <lib> --output build/item-processing
java tests/item_processing/EffectSourceAudit.java .
python3 tests/item_processing/test_effect_refactor.py
python3 tests/item_processing/test_use_pipeline.py
python3 tests/item_processing/test_transactions.py
python3 tools/fetch_item_use_test_dependencies.py
python3 tests/item_processing/test_healing_catalog.py
```

운영 적용 전 물약·속성별 마법서·스턴/데몰리션·패시브 학습·강화·상자·충전 아이템을 기존 서버와 비교한다.
