# 12차 아이템 기능별 분리 검증 기록 (2026-11-18)

## 기준
- 11차 전체 SRC ZIP 기반. 변경 파일 3개(`SpellBookItems`, `EnchantmentItems`, `SupplementalItemActions`) 외 생산 Java 파일은 원본 바이트 유지.
- 신규 기능별 Java 클래스 8개. 기존 public 외부 진입점 유지.

## 변경
1. 마법서 일반 사용 / 엘프 사용 / 습득 완료: `GeneralSpellbookConsumable`, `ElfSpellbookConsumable`, `GeneralSpellbookLearning`.
2. 강화 성공 및 실패 후처리: `EnchantmentSuccessEffects`, `EnchantmentFailureEffects`.
3. 보조 아이템 대상 ID 결정, 엘릭서 레벨·개수 제한, 귀걸이 대상 검증: `SupplementalTargetId`, `SupplementalElixirEligibility`, `SupplementalEarringTargetRules`.

## 확인
- 위 5개 원본 메서드 본문은 11차와 바이트 동일. 공개 엔트리포인트에서 새 메서드를 정확히 한 번 호출.
- 보조 아이템 기존 분기·확률·메시지·대상 검증 소스 완전 재구성; 원본 실행 위치 보존.
- 보조 아이템 실제 생산 클래스 3종 Java 21 typed-stub 컴파일 후 원본과 4,536개 입력에 대한 결과·메시지 순서 일치.
- 신규 마법서·강화 처리 클래스 5종 Java 21 typed-stub 컴파일 및 호출 테스트 통과.
- Java 21 전체 생산 구문 검사 1,885개 파일 오류 0개.
- 11차 아이템 회귀·10차 갑옷/변신·9차/8차/방송114차 회귀 재통과.
- 생산 변경 범위 및 기존 파일 SHA-256 불변성, ZIP CRC/중복 경로 검사 통과.

## 보존·한계
- 기존 아이템 소모·경험치·마법 습득·강화 상태 업데이트·로그/저장·CacheD 경계 변경하지 않음.
- 일반 방송/네트워크/NPC/DB 코어 변경 없음. 별도의 스레드·큐·동적 per-use 할당 추가 없음.
- 실제 게임 전체 의존성 타입 컴파일, 실제 서버 MySQL·인게임 테스트는 미검증. 테스트용 typed stub은 전체 게임 통합 컴파일이 아님.

재검증: `bash docs/item_domain_phase12_20261118/run_tests.sh`
