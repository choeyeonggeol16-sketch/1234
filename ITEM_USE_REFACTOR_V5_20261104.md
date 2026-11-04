# 아이템 사용 리팩토링 V5

기준은 GitHub main `532c1ee`의 V4 전체 소스다. 이번에는 효과 코드 내부의 반복 검사를 공통화했다.

## 변경

- **학습 아이템 43종(패시브 27종·스킬 아이템 16종)을 규칙 기반으로 통합했다.** 각 아이템에는 직업/레벨, 선행 조건, 이미 습득한 경우와 사용 불가 안내, 교체 스킬만 선언한다. 공통 검사·안내·기존 소모 함수 연결은 `ItemLearningRule`이 담당한다.
- `ItemUsePassiveLearning`과 `ItemUseSkillLearning`에는 규칙과 아이템별 저장 완료 효과만 남겼다. 두 파일은 합계 832줄에서 454줄로 줄었고, 공통 규칙 구현은 별도 125줄이다.
- 패시브 활성화와 학습 효과음의 동일한 연속 호출을 공통 함수로 합쳤다. 호출 위치와 순서는 유지했다.
- 기존에는 일부 아이템이 직업/레벨부터 검사하고 다른 아이템은 습득 여부부터 검사했다. 그 차이를 규칙에 명시했다. 선행 마법 조회·상위 패시브 차단·기본 패시브 필요 조건·행동 불가 검사도 원래 순서로 실행한다.
- 컨피그의 콜 클랜 어드밴스/이뮨 투 함 블레이드 레벨은 사용 시 읽는다. 서버 시작 때 값이 고정되지 않는다. 게일 샷의 기존 선행 마법+레벨 조건도 그대로 유지했다.
- **아이템 구현 57개 파일에서 불필요한 import 875개를 제거했다.** 이 중 55개 파일은 import 외의 실행 소스가 바이트 단위로 동일하다. 사용 확률·강화·보상·이동·인벤토리/CacheD 정책은 바꾸지 않았다.

규칙은 ID·문구·조건만 보관하는 고정 객체다. 사용 때 규칙/맵/리스트를 새로 만들지 않는다.
저장 후 효과는 기존처럼 필요한 시점에 Runnable 하나를 생성하고, 캐릭터 하나만 캡처한다.
신규 규칙 객체에는 플레이어·아이템·효과 콜백 참조가 없다. 운영 서버의 메모리 절감률이나 누수 여부를 힙 덤프로 실측한 결과는 아니다.

## 검증

- 전체 **Java 1,703개 컴파일 통과**, 기존 removal 경고 5개.
- 실제 V4 핸들러와 새 구현을 **44,032개 상황에서 실행 비교**했다. 43종 모두 승인 성공 사례가 포함된다. 직업·레벨 경계·중복 습득·선행 스킬·컨피그 변경·행동 불가, 검사에 사용한 getter 순서와 안내 문구, 소모 함수 인수, 지연 저장 완료/실패 시 효과를 비교했다.
- 독립 Java 구문 트리 검사로 **43개 저장 후 효과와 아이템 경로**가 공통 함수 확장 후 정확히 같은지 확인했다.
- 전투 메서드 14개와 기존 고급 아이템 경로 52개에 대한 연속성 검사 통과. 이전 V4 대비 저장 후 Runnable에 캐릭터 외의 참조가 추가되지 않았음을 확인했다.
- 기존 사용 단계 **12개**, 트랜잭션 **27개** 테스트 통과.
- V4 Java 1,702개 중 **1,645개는 바이트 단위 동일, 55개는 import만 변경**, 학습 구현 2개 수정, 규칙 클래스 1개 신규, 삭제 없음. `validation/source_manifest.json`에 파일 목록과 SHA-256이 있다.

게임 객체·저장·소켓 경계 일부는 테스트 대체 객체다. 운영 DB와 실클라이언트의 모든 아이템 사용을 실행한 검증은 아니다.

## 적용

- **FULL_SRC**: V4 변경을 포함한 전체 소스. 기존 lib·data·config·DB를 보존하고 전체 Clean/Rebuild·재시작한다.
- **PATCH**: V4 `532c1ee` 기준 Java **58개**. 55개는 import 정리이며, 논리 변경 2개+신규 공통 클래스 1개도 모두 적용한다. V4 미적용 상태라면 FULL_SRC를 기준으로 병합한다.
- 추가 DB나 설정·운영 라이브러리는 없다. 기존 JDK 21 / UTF-8 / `--enable-preview` 조건을 유지한다.

재검증 시 `ITEM_DEPS_DIR`은 jar 디렉터리, `ITEM_BUILD_CLASSES`는 컴파일 결과 classes 디렉터리를 가리킨다.

```sh
python3 tools/build_item_refactor.py --dependencies <lib> --output build/item-processing
python3 tests/item_processing/test_learning_rules.py
java tests/item_processing/EffectSourceAudit.java .
python3 tests/item_processing/test_use_pipeline.py
python3 tests/item_processing/test_transactions.py
python3 tools/audit_item_imports.py --baseline <V4 전체 소스 디렉터리>
```
