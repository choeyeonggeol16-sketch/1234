# RKern 아이템 전용 69차 — 카드 도감 primitive 조회 및 인자 배열 선할당

**기준:** `RKern_ItemOnly_Memory_Optimization_Phase68_FULL_SRC_20261118.zip` (68차 전체 SRC).
**범위:** 카드 도감·합성 준비 화면 관련 생산 Java 5개만 수정. NPC 퀘스트·AI·네트워크·방송·CacheD 공통 코드는 바이트 단위 유지. 신규 생산 클래스 0개.

## 수정 대상

- `L1CardStep1.java` 전설 합성 준비, `L1CardStep2.java` 영웅 합성 준비, `L1CardStep3.java` 신화 합성 준비: `Function<Account,Integer>` → `ToIntFunction<Account>`, `apply` → `applyAsInt`. `Account`의 원래 `int` getter를 호출할 때의 불필요한 `Integer` 박싱 가능성을 줄임.
- `L1CardLegendList.java`: 동일한 primitive getter 전환 및 전체 도감 46개 화면 인자의 `ArrayList` 최초 용량 선할당.
- `L1CardHeroList.java`: 전체 도감 72개 화면 인자의 `ArrayList` 최초 용량 선할당. 기존 카드 수량 `int[]`, 표시 텍스트, 카테고리 필터, 성별 분기, 카드 선택 난수 호출은 그대로 유지.

## 수행 검증

- 본문 내 `Account::getter` 70개를 실제 생산 클래스 5개의 복사본과 타입 스텁으로 연결하여 Java 21 컴파일 통과.
- 전설·영웅·신화 합성 준비, 전설·영웅 전체/카테고리 목록, NPC 보기 경계 및 null 입력 총 **11,532개 입력 비교에서 완전 동일한 화면 인자·순서·패킷 템플릿**을 확인.
- 동일한 자바 테스트 모델(계정 70개 getter; 각 getter의 값은 0,1,2,3,4,127,128,129,255,1024,65535,-1 교차)을 사용. 박싱이 실제 발생할 수 있는 범위도 포함.
- `ThreadMXBean` 테스트 모형의 준비 화면 5종 균등 반복 측정(75,000회/실행, Java 21): 기존 약 2,750~2,762B/호출, 69차 약 2,388~2,418B/호출. **이 수치는 테스트 스텁의 해당 화면 렌더링 경로만 해당하며 운영 서버 전체 GC 절감량이 아님.**
- 생산 Java 1,922개 JDK 21 JavacTask 구문 파싱 오류 0건.
- 이전 차수 관련 검증: 68차 레이드 설정 150,015건·효과 집계 216,000건·위탁판매 1,200건, 67차 변신 카드 120,000건, 66차 물약/난수, 31차 스타터 521건, 32차 선물상자 1,381건 통과. 31차 `run_tests.sh`에 포함된 오래된 30차 `ItemActionDispatcher.java` 바이트-스냅샷 검사는 이후 리팩토링으로 변경되어 실패하므로, 31차 스타터 검사는 `test_recruit_atomic.py`로 직접 실행해 확인함.

## 재실행

```bash
python3 docs/item_only_phase69_20261118/run_phase69.py
```

Java 21과 Python 3 필요. 검증기는 현재 생산 소스 5개와 ZIP 안에 보존된 68차 베이스라인 5개를 별도로 타입 컴파일하여 11,532개 결과를 대조하고 호출당 할당량을 표시함. 기존 베이스라인을 변경하지 않음.

## 미검증

프로젝트 전체 외부 의존성 타입 컴파일, 실제 인게임 UI, 실 MySQL/CacheD 장애 주입 및 5,000명 실부하는 이번 단계에서 수행하지 않음. 계정의 실제 동시 갱신이 있는 경우 게임 이벤트 순서 검증은 별도로 필요. 다만 이번 변경은 getter 호출 회수와 순서, 인자 내용, RNG 소비를 변경하지 않음.
