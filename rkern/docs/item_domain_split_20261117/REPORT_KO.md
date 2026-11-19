# RKern — 아이템 사용 도메인 클래스 분리 및 호출부 이관

**기준:** `RKern_ItemFunction_Followup_Ammo_Hotpath_Full_SRC_20261117.zip` (직전 아이템 추가 수정본)  
**범위:** 아이템 사용 함수 및 관련 회귀검사만 변경. 메모리·CPU 튜닝, 네트워크·방송·CIO·CacheD 구현은 변경하지 않음.

## 실제 분리·이관

- `additem.java` → `ClassRewardPackages.java`: 아이템 28종의 기존 CacheD 영수증 소모·커밋 소유. 내용은 지원상자 3종 `StarterSupportPackages.java`, 룬·봉인 보상 25종 `RuneRewardPackages.java`로 이동.
- `additem2.java` → `LevelAndEquipmentPackages.java`: 아이템 10종의 소모·커밋 소유. 장비·악세 패키지 4종 `EquipmentStarterPackages.java`, 레벨 달성 6종 `LevelMilestonePackages.java`로 분리.
- 기존 `additem.stageReward` 및 봉인 보상 생성 로직은 `PackageRewardStaging.java`로 추출. **대상 객체 생성, `tx.grant`, `tx.commit` 순서 및 보상 알림 시점 변경 없음.**
- `ItemActionDispatcher`의 **퀘스트 HTML 대화 아이템 63종**을 `QuestDialogueItems.java`로 분리. 원래 아이템 ID→HTML 키 문자열을 모두 보존; 한 분기에서 위임함.
- **특수 주문서 9종**을 `SpecialEnchantmentItems.java`, **지속형 버프 아이템 3종**을 `DurableBuffScrollItems.java`로 분리. 기존 강화 함수 호출과 `UseDurableConsume` 내부 동작은 그대로 둠.
- 사용되지 않는 이전 `additemnew`를 `RecruitStarterKit.java`로 이름만 변경(내부 로직 **미변경**). 현재 Java 생산 호출부는 발견되지 않음.
- `ItemUseUtil` → `SupplementalItemIds`: 기존 특수 항목 ID 배열·정렬·이진 검색 동일; `ItemActionService` 호출부 연결 변경.
- `CS_ItemUse`는 **클라이언트 패킷 진입 식별자**이므로 네트워크 패키지 클래스 이름을 유지함. 실제 게임 아이템 로직은 `ItemActionService`/도메인 클래스로 분리되어 있음.

## 실제 검증

1. **Java 21 컴파일 + 생산 메서드 실행:** 원본 2개 보상 상자 클래스와 분리된 생산 보상 클래스들을 같은 typed-stub 환경에서 실제 `javac`로 컴파일하고 실행. 38종 ID × 9개 직업/무직 조건 × 5개 실패·정상 조건 + 미등록 ID를 포함해 **1,755개 입력 이벤트 로그가 원본과 완전 동일**. 기본 지급, 없는 템플릿, 두 번째 지급 거절, debit 거절, 인벤토리 유효성 거절 포함.
2. **퀘스트 대화:** 원본 63개 ID→HTML 키 전수 비교 + 없는 ID 거절.
3. **특수 인챈트·버프:** 생산 클래스 타입 컴파일; 강화 9종, 버프 3종의 입력 ID/대상 전달/상태·버프 적용 순서 확인.
4. **디스패처:** 그룹 분리 전후 기존 **860개 case-label 원소를 정렬 비교하여 동일** 확인. 기존 분기 대상 ID 제거·추가 없음.
5. **보충 특수 ID:** `SupplementalItemIds` ID 배열 **331개 원본 토큰 순서·내용 동일** 확인.
6. **전체 생산 Java 구문 검사:** **1,721개 / 오류 0건**.
7. **이전 아이템 리팩토링·hotpath·라우터·확률표·레이드 설정 캐시 및 114차 방송 모의 회귀:** 통과.
8. **코드 변경 범위 및 최종 ZIP CRC·재추출 시험:** 확인.

## 검증되지 않은 사항

외부 라이브러리를 포함한 **전체 의존성 타입 컴파일**, 실제 MySQL·CacheD 원자성·장애 복구 및 Windows 실서버 사용 시험은 실행하지 않았음. `RecruitStarterKit`의 기존 `consumeItem`/즉시 지급 경로는 활성 호출이 없는 상태여서 **동작을 변경하지 않았으며 트랜잭션 안전성을 별도 검증한 것은 아님**. 외부 플러그인이 이전 공개 클래스 이름을 직접 참조한다면 함께 변경해야 함.

재현: `bash docs/item_domain_split_20261117/run_tests.sh`; 기존 회귀: `bash docs/item_followup_20261117/run_tests.sh`.
