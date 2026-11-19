# RKern 아이템 전용 72차 (2026-11-18)

기준: RKern_ItemOnly_CardAlchemy_Memory_Phase71_FULL_SRC_20261118.zip

## 실제 변경 파일 (생산 Java 정확히 3개)
- src/l1j/server/server/model/item/Card/L1CardAlchemy1.java
- src/l1j/server/server/model/item/Card/L1CardAlchemy2.java
- src/l1j/server/server/model/item/Card/L1CardAlchemy3.java

NPC·네트워크·방송·CacheD 공통 코드는 **변경하지 않음**. 신규 생산 클래스·스레드·큐 없음.

## 최적화
- 카드 합성 결과 UI의 ArrayList 초기 용량을 기존 실제 항목 수와 일치하게 지정: 전설 62, 영웅 78, 신화 28.
- 결과 UI의 카드 수량 표시를 위해 생성하던 int[34], int[36], int[10] 제거. 0장 포함 원래 포맷·계정 getter 조회 순서 보존.
- 영웅·신화 합성 시 선택 카드 4개 보관에 사용하던 int[4] 임시 배열 제거. 4개의 primitive 로컬로 치환. 원래 검증·차감 순서 보존.
- 기존 결과 카드 정적 보상표(71차)와 합성 확률, 난수 호출, 지급 ID, 카드 차감/DB 호출 순서는 불변.

## 검증
- docs/item_only_phase72_20261118/run_phase72.py: **42,000회** Java 21 생산 클래스 + typed stub 원본 A/B 결과 hash 완전 일치 (`9a2eb6b3fa5173a1f0b38c9b04344da30984274f80cd85e11df4e328be567692`).
- 같은 테스트에서 GUI 결과창 호출당 할당량(실제 생산 메서드, mock 패킷 전송, reflection 포함):
  - 전설 결과 UI: 3,456B -> 2,648B (-23.4%)
  - 영웅 결과 UI: 4,024B -> 2,816B (-30.0%)
  - 신화 결과 UI: 약 1,602.9B -> 1,288B (-19.6%)
- docs/item_only_phase72_20261118/check_tables.py: 신화/전설/영웅 보상표 117행의 순서, 아이템 ID, 중복 가중치 및 신화 합성 결과 선택 본문 보존.
- 이전 66~70차 카드 UI/물약/아이템 회귀검사 통과, 31차 스타터 521건, 32차 선물상자 1,381건, 30차 레지스트리 259,200건 통과.
- 전체 생산 Java 1,922개 JDK 21 구문 분석 오류 0건.
- 71차 구형 check_reward_tables.py는 `L1CardAlchemy3.java`가 byte-identical이라는 **당시 차수 전용 가정** 때문에 실패하므로, 이번에는 동일 보상/선택 여부를 72차 검사로 대체함. 생산 버그는 아님.

## 검증 한계와 남은 리스크
- 게임 전체 의존성 타입 컴파일, 실제 MySQL·CacheD 장애 주입, 클라이언트 실전, 장시간 동시성 시험 미수행.
- 카드 합성 계정 상태 변경과 아데나 차감·보상 저장이 별도 호출인 기존 코드에는 여전히 원자성 검토가 필요함. 특히 일부 진입점은 선택 카드 값의 유효성·실제 소비 성공 여부를 충분히 검증하지 않음. 이번 72차는 확률/거래 규칙을 임의 변경하지 않아 이 영역은 그대로 유지함.
- 마이크로벤치 수치는 UI 결과창 처리 fixture만의 수치. 전체 힙/GC 절감율로 사용할 수 없음.
