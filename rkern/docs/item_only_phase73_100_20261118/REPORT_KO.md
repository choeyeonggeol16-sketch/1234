# RKern 아이템 전용 73~100차 코드 수정·검증 (2026-11-18)

**기준:** `RKern_ItemOnly_CardAlchemy_Memory_Phase72_FULL_SRC_20261118.zip`  
**범위:** `src/l1j/server/server/model/item/**` 생산 코드만 변경. NPC, 네트워크, 방송, CacheD 공통 구현 **미변경**.  
**작업 성격:** 28개 미세 차수/변경 포인트; 모두 큰 독립 기능 개발이라는 의미는 아님. 이미 실측과 동작 검증에 유리한 변경만 남김.

## 실제 적용 작업 (73~100차)
- 73차 — 신화·유일 합성 결과의 불변 정적 보상 객체 재사용
- 74차 — 전설·영웅·신화 합성 아데나 차감 실패 시 진행 중단
- 75차 — 전설·신화 합성에 존재하지 않는 카드 ID 차단
- 76차 — 영웅 자동 카드 선택 임시 int[37] 제거
- 77차 — 전설 자동 선택 리스트 최대 23종만큼 초기 용량 지정
- 78차 — 전설 합성 소비자 HashMap → 34슬롯 배열
- 79차 — 영웅 합성 소비자 HashMap → 36슬롯 배열
- 80차 — 신화 합성 소비자 HashMap → 11슬롯 배열
- 81차 — 유일 카드 성공 공지 작업 명시적 정적 재사용
- 82차 — 신화 자동 선택 후보 리스트 11종 선할당
- 83차 — 카드 컬렉션 정보 15개 메시지 슬롯 선할당
- 84차 — 영웅 필터 목록 선할당 구조 정리(85차에서 정확한 크기로 최종 보정)
- 85차 — 영웅 필터 목록 근거리21·원거리11·마법10 정확한 용량
- 86차 — 영웅 필터 목록의 호출별 int[36] 스냅샷 제거
- 87차 — 영웅 도감 고정 이미지 문자열 정적 캐시
- 88차 — 전설 도감 고정 이미지 문자열 정적 캐시
- 89차 — 전설 필터 목록 근거리16·원거리7·마법4 정확한 용량
- 90차 — 신화 도감 화면별 리스트 크기 5·22 정확한 용량
- 91차 — 신화 도감 고정 이미지 문자열 정적 캐시
- 92차 — 전설 합성 준비 화면 아이콘 문자열 정적 캐시
- 93차 — 영웅 합성 준비 화면 아이콘 문자열 정적 캐시
- 94차 — 신화 합성 준비 화면 아이콘 문자열 정적 캐시
- 95차 — 스킬북 대응 스킬이 없을 때 아이템 소모 방지
- 96차 — 스킬 데이터가 누락돼도 NullPointerException 없이 다음 항목 검사
- 97차 — 스킬북 이름의 반복 조회 제거
- 98차 — 스킬 이름의 불필요한 문자열 래핑 제거
- 99차 — 전사 스킬북의 비정상 스킬 레벨 인덱스 방어
- 100차 — 전사 스킬북 SkillsTable 객체 조회 중복 제거

## 검증
- Java21 전체 생산 소스 구문 검사: **1,922개, 오류 0건**.
- 카드 합성 실제 생산 클래스 타입 컴파일/정상 경로 원본 비교: **42,000건**, 결과 SHA256 일치(계정 갱신, 지급, 난수, 메시지 로그).
- 신화 합성 결과 카드 맵/경계 값 원본 비교: **432건** 일치.
- 합성 실차감 거절 주입: **3,714건**에서 후속 카드 소모·지급·이펙트 방지 확인. 존재하지 않는 카드 입력 **18건** 거절.
- 전설·영웅 자동 카드 선택 원본 비교: **30,000건** (동일 테스트 시드로 shuffle 고정, getter·선택·UI 결과 일치).
- 도감·준비 UI 생산 클래스 5개 타입 컴파일: **11,532건** 원본 출력 일치.
- 기존 신화·전설 도감/자동 선택: **120,000건** 원본 출력·후보 시험 통과.
- 스킬북·전사 스킬북 생산 메서드/추출 메서드 Java21 타입 컴파일: **정상 20개 경로 원본 일치**, 잘못된 스킬 레벨·누락 템플릿 시 미소모 검증.
- 아이템 기존 검사: 66차 물약 **3,648건**, 68차 레지스트리 **216,000건**, 69차 도감 **11,532건**, 70차 카드 화면 **30,000건**, 31차 스타터 **521건**, 32차 선물상자 **1,381건**, 30차 레지스트리 **259,200건** 통과. 개별 테스트의 입력은 서로 중복될 수 있으므로 총합을 독립 건수로 합산하지 않음.

**정상 성공 경로 불변:** 아이템 ID, 카드 보상표, 난수 호출과 결과, 합성 성공·실패 이펙트, 기존 수량·출력 순서.  
**의도적인 오류 경로 변경:** 아데나 실제 차감 실패, 무효 카드 ID, 누락·비정상 스킬북 데이터는 후속 처리를 중단함.

## 성능
- 5종 카드 준비/도감 화면 마이크로벤치마크: baseline **약 2,400B/호출** → revised **약 1,300~1,350B/호출** (Java21 테스트용 모의 패킷 렌더러 평균). 실제 게임 GC/힙 절감률은 미측정.
- 신화 합성 결과 객체는 성공·실패마다 생성되던 `ResultCard` 인스턴스를 정적 불변 데이터로 재사용함.
- 카드 ID→계정 필드 감소 함수 조회의 `Integer` 박싱·HashMap 노드 경로를 축소한 고정 크기 배열로 교체함. 합성 제어와 데이터 저장 트랜잭션은 이번 단계에서 통째로 재설계하지 않았음.

## 검증 한계·남은 위험
- **전체 게임 의존성 타입 컴파일**, 인게임 시뮬레이션, 실 MySQL·CacheD 장애 주입은 **미완료**.
- 카드 합성의 **아데나 차감 / 계정 카드 차감·저장 / 결과 아이템 지급**은 아직 단일 영속 트랜잭션으로 묶여 있지 않음. 이번 차수의 아데나 차감 결과 검사는 이 원자성 문제 전체를 해결한 것이 아님.
- 스킬북 실패 경로는 모의 게임 모델로만 검증함. 실제 DB 및 캐릭터 스킬 저장 후 ACK 손실은 미검증.
- 100차는 코드 정리 차수의 누적 이정표이며 **전체 아이템 시스템의 완전한 검증·완료를 뜻하지 않음**.
- NPC·퀘스트 파일은 수정하지 않았으며 38~65차의 기존 내용을 그대로 포함함.

## 생산 코드 변경 파일
- `src/l1j/server/server/model/item/function/SkillBookLearning.java`
- `src/l1j/server/server/model/item/function/WarriorSkillBookItems.java`
- `src/l1j/server/server/model/item/Card/L1CardAlchemy1.java`
- `src/l1j/server/server/model/item/Card/L1CardMyPast.java`
- `src/l1j/server/server/model/item/Card/L1CardMythList.java`
- `src/l1j/server/server/model/item/Card/L1CardLegendList.java`
- `src/l1j/server/server/model/item/Card/L1CardAlchemy2.java`
- `src/l1j/server/server/model/item/Card/L1CardLPast.java`
- `src/l1j/server/server/model/item/Card/L1CardStep3.java`
- `src/l1j/server/server/model/item/Card/L1CardStep1.java`
- `src/l1j/server/server/model/item/Card/L1CardHeroList.java`
- `src/l1j/server/server/model/item/Card/L1CardHPast.java`
- `src/l1j/server/server/model/item/Card/L1CardStep2.java`
- `src/l1j/server/server/model/item/Card/L1CardAlchemy3.java`
- `src/l1j/server/server/model/item/Card/L1CardCollection.java`
