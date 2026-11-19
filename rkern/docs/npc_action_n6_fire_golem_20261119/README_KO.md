# N6 후속 — 불골렘 강화 무기 및 유료 이동

기준: RKern_NPC_Action_N6_Additional_Exchange_Receipt_FULL_SRC_20261119.zip.

- NPC 5066의 숫자 1~13 및 소문자 a~f, 19개 제작 명령을 기존 NpcExchangeCommands에 이전했다. 기존 재료 우선순위, 장착 장비 제외, 강화 단계, 소모 수량, 보상 ID, 메시지(11번은 별도 403 없음)를 유지한다. 실제 차감 실패는 성공 처리하지 않는다. 지팡이 제작 시 결정체 100,000개를 실제 확보해야 한다(기존은 1개 소지만 확인).
- 기존 L1InventoryTransaction에서 재료와 보상을 같은 receipt에 준비한다. 보상은 detached nonstackable grant image에 강화 수치를 지정하며, 커밋 이후에만 성공 안내를 보낸다. 운영 템플릿이 누락되거나 stackable로 잘못 설정되면 거부한다. 보상 capacity와 지연 HTML 송신은 기존 transaction 정책을 따른다. 기존 helper에 있던 월드 등록 선행 객체 생성 경로를 제거했다.
- 대문자 A~P 방어구 16개는 바닥 지급 fallback이 존재하므로 그대로 보존했다. 추후 원자화 시 기존 ground inventory 계약 검증 필요.
- NPC 50015의 1,500 아데나 이동은 consumeItem 성공 후 실행. 좌표/정상 메시지 보존.
- 신규 생산 클래스/스레드/큐 없음. 기존 아이템/네트워크/방송/전투/CacheD 코어 보존.
- 신규 모의 검사 13,833 + 30, 이전 통합 교환 회귀 36,237 및 이전 개별 회귀 28,662 재실행 통과(이전 통합의 부분집합). 두 기존 테스트의 스텁 API만 확장. 전체 Java21 구문 1,925개 정상. 전체 javac는 기존 531 errors/5 warnings, 이전 진단 로그와 byte-identical. 전체 타입 컴파일 성공이나 실DB/인게임/Heap 검증을 주장하지 않는다.
