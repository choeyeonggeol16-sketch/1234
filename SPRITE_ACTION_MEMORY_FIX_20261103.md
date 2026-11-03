# 스프라이트 액션 배열 메모리 수정 — 2026-11-03

기준: 공개 main f2fea0e. 기존 NPC 타깃 전환/추적 간격 수정도 포함합니다.
Java 변경은 src/l1j/server/MJ3SEx/SpriteInformation.java 한 파일입니다.

스크린샷은 메모리 히스토그램이며 ActionInformation[]이 약 30MB로 보입니다.
소스에서 SpriteInformation은 각 spr_info 행마다 256칸 참조 배열을 항상 만들었습니다.
스프라이트 정의는 캐릭터들이 공유하고, 캐릭터는 현재 SpriteInformation을 참조합니다.
빈 액션 슬롯의 낭비는 확인되지만, 이 스크린샷만으로 메모리 누수나 CPU 병목을 확정할 수는 없습니다.

수정:
- 비어 있는 스프라이트는 길이 0인 공통 배열을 사용합니다.
- 실제 등록된 가장 큰 액션 번호 + 1까지만 배열을 확장합니다. 최대 256칸/ID 범위는 유지합니다.
- 조회는 기존처럼 ID로 직접 인덱싱하므로 O(1)이며, 조회 중 새 객체를 만들지 않습니다.
- 등록 시 완성된 배열을 volatile로 게시하고 쓰기만 동기화합니다. 읽기는 잠그지 않습니다.
- 없는 기본 액션은 스프라이트별로 한 번 생성합니다. 동시 생성에 의한 덮어쓰기를 막습니다.
- 기존 액션 객체, 프레임 수/속도, 변신 4종의 액션 매핑, 레벨별 사용자 속도, decorator 호출,
  유효 ID/미등록 ID 처리 및 기본 액션 캐시 동작을 유지합니다.
- 액션/프레임 계산 클래스나 DB 스키마를 바꾸지 않았습니다.

실제 Java Instrumentation.getObjectSize로 측정한 배열 점유 (스프라이트 10,000개):

최고 등록 액션 ID | 기존 | 수정
없음 | 10,400,000바이트 | 공유 배열 총 16바이트
8 (0/1/8) | 10,400,000바이트 | 560,000바이트 (약 94.6% 감소)
99 (0/1/99) | 10,400,000바이트 | 4,160,000바이트 (60% 감소)
255 | 10,400,000바이트 | 10,400,000바이트

이는 배열만의 측정입니다. 실제 DB 액션 번호 분포가 없으므로 서버 전체 힙이나 스크린샷의
30MB가 얼마나 감소할지는 적용 후 확인해야 합니다. 기존 ActionInformation/프레임 객체는 유지합니다.
등록 과정의 배열 복사는 임시 할당이며, 초기 로딩 속도 향상을 주장하지 않습니다.

검증:
- 실제 SpriteInformation, ActionInformation, MJFrameElement, EActionCodes/EActionConstruct 사용.
  캐릭터, decorator, 레벨 색인, ResultSet 경계는 대체 객체입니다.
- 회귀 8개 그룹: 메타데이터/범위, 256개 ID 역순 등록/교체/빈칸, 모든 실제 enum 기본값,
  변신 4종과 레벨별 속도, 스프라이트별 사용자 값 격리/커스텀 ID 255,
  동시 게시, 기존 dispose 계약, 배열 메모리 확인.
- 원본은 기능 7개 통과, 메모리 예산 검사 1개 실패. 수정본은 8개 모두 통과합니다.
- 조회 1,000,000회에서 ThreadMXBean 추가 할당 0바이트 확인.
- 전체 Java 1,701개 타입 컴파일. 원시 로그는 validation/sprite_*에 있습니다.
- 실제 데이터 로드/로그인/변신/클라이언트/운영 힙과 CPU 부하 검증은 미실행입니다.
  기존 NPC 검사 결과를 이번 메모리 패치의 새 실행 결과로 재사용하지 않습니다.

NC 참고 범위:
사용자 L2Server.exe에서 User::Polymorph(int), User::OnPolymorph(CNPC*)와 NPCPlayAnimation의
함수명 흔적을 확인했습니다. 해당 바이너리의 액션 테이블 용량/배치와 Java 구조의 동등성을
복원한 것은 아닙니다. 공유 원형 데이터와 개체의 현재 변신 참조 분리는 설계상 참고할 방향이며,
이번 수정의 근거는 현재 Java 소스와 실측입니다. 실행 파일은 배포물에 포함하지 않았습니다.

적용:
기존 소스/JAR를 백업하고 PATCH의 SpriteInformation.java를 교체해 서버를 재빌드/재시작하세요.
JDK 21 / UTF-8 / --enable-preview를 사용합니다. lib/config/게임 데이터/DB는 보존합니다.
라이브 reload 전체의 수명 문제를 고친 패치는 아니므로 재시작 기준으로 검증하세요.
아이템 리팩토링 V2에도 한 파일 패치를 적용할 수 있습니다. FULL_SRC는 공개 main 기준입니다.

재현: python3 tests/sprite_memory/test_sprite_tables.py
원본 비교: --baseline-root 에 수정 전 프로젝트 루트를 지정합니다.
전체 컴파일: python3 tools/fetch_dependencies.py 후 python3 tools/compile_sources.py
적용 전후 같은 몬스터 수/변신 조건에서 GC 후 ActionInformation[] 개수·바이트와 사용 속도를 비교하세요.
