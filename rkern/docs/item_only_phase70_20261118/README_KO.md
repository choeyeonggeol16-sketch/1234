# RKern 아이템 전용 70차 — 카드 합성 준비 화면 메모리 개선

기준 ZIP: `RKern_ItemOnly_CardPrimitiveMemory_Phase69_FULL_SRC_20261118.zip`

## 생산 코드 변경 (3개, 신규 클래스·삭제 클래스 없음)

- `src/l1j/server/server/model/item/Card/L1CardA.java`: 영웅 화면의 호출별 카드당 `int[]`(36개)와 바깥 2차원 배열 제거. 카드 수량 배열 하나와 정적 아이콘 문자열 표를 사용. 78개 화면 인자를 한 번에 담는 리스트 크기 지정. 선택 카드 4개 임시 배열 제거.
- `src/l1j/server/server/model/item/Card/L1CardB.java`: 전설 화면의 카드당 `int[]`(23개) 및 11칸 추가 카운트 배열 생성 제거. 카드 수량 배열 하나, 정적 아이콘 문자열 표, 확정 용량 62 사용.
- `src/l1j/server/server/model/item/Card/L1CardC.java`: 신화 화면의 카드당 `int[]`(10개), 선택 카드 4개 임시 배열 제거. 남/녀 오프·온 정적 아이콘 문자열 표와 카운트 배열 하나, 확정 용량 28 사용.

아이콘 선택 경계(2장 이상), 문자열 형식, 출력 항목 순서, account getter 호출 순서와 성별 분기는 변경하지 않았다. 추가 호출 경로, NPC, 네트워크, DB/CacheD, 공용 스레드 수정 없음.

## 검증

`python3 docs/item_only_phase70_20261118/tests/test_card_ui.py`

- **실제 변경·원본 생산 Java 세 클래스**를 각각 Java 21로 타입 컴파일하고 같은 테스트용 Account/PC/패킷 모델에 연결.
- 10,000 입력 조합 × 3 화면 = **30,000 화면 호출** 결과 SHA-256 완전 일치. 결과 문자열뿐 아니라 Account getter 조회 순서 비교. null/dead/남녀/경계값/카드 수량 -2,-1,0,1,2,3,7,10,32767 등 포함.
- Java21 ThreadMXBean 사용한 동일 모델의 호출당 할당량(40,000회 측정):
  - 영웅 A: **8,640B → 4,712B (-45.46%)**
  - 전설 B: **6,752B → 4,192B (-37.91%)**
  - 신화 C: **2,872B → 1,728B (-39.83%)**
- 전체 Java21 `JavacTask.parse()` 검사: **1,922 파일, 오류 0건**.
- 기존 66~69차의 관련 테스트(물약/카드/레지스트리/보상) 재통과. 32차 회귀 묶음도 통과.
- ZIP CRC, 이름 중복 없음, 69차 원본 대비 생산 Java 3개만 변경, 나머지 생산 파일 byte-identical 검증.

주의: 할당량 수치는 stub 모델에서의 해당 메서드 호출량만 뜻하며 서버 전체 heap, throughput 또는 GC 감소율이 아니다. 아이콘 정적 문자열 테이블은 장수명 메모리를 일부 사용한다. 전체 게임 의존성 타입 컴파일, 실제 인게임 UI 및 MySQL·CacheD 장애 테스트는 아직 실행하지 않았다.
