# RKern 아이템 사용 204~210차 — 잔여 기능 분리 및 라우팅 검수

기준: RKern_ItemUse_Refactor_MultiPhase198_203_FULL_SRC_20261119.zip (SHA256 0141e7dd9adac19ca58f59a1013f60f24714414863f59a66806666e1cd5e4dff).

## 범위와 변경

- 204: 화살·스팅 선택을 AmmunitionSelectionItems로 분리. 최초 분기 우선순위, 패킷 순서, 기존 계속 디스패치 정책 보존.
- 205: 진 데스나이트 목록과 Neige 변신 선택 UI를 TransformationListItems로 이관. 사전 admission 위치, 투명 조건, 패킷 순서 및 반환값 유지. target modeOf 변경 없음.
- 206: 번호별 버그베어 경주 조작을 기존 BugRaceControlWandItems에 이관. GM 검사, 5개 번호, 상태 변경/안내 순서 유지.
- 207: 좌표 지정 텔레포트 42501을 WorldDestinationItems에 이관. 원래 좌표 해석, 효과 번호, 반환값 및 권한 정책 보존.
- 208: 경험치 복구 증서 안내를 ExperienceRecoveryConsumables에 이관.
- 209: 공성 소모품 안내를 SiegeSupplyItems에 분리.
- 210: 드래곤볼 확인 UI를 DragonBallConfirmationItems에 분리. 응답 처리나 소모 정책 변경 없음.

CS_ItemUse → User → CItem/ExtendedItemActions → ItemActionService → ItemActionDispatcher/UnlistedItemRouter/SupplementalItemActions 및 하위 라우터를 구조 검토했다. 메인 스위치의 133개 분기/860개 ID 토큰은 그대로 유지된다. 40117의 빈 동작, 기존 기능 클래스 호출, 공용 소유권·admission·completion은 의도적으로 유지했다. 별도 NPC/네트워크/방송/DB 쓰기 구현은 수정하지 않았다. 아이템 클릭에 이어지는 별도 UI 응답 패킷의 전 기능 실행 검증을 의미하지 않는다.

## 발견 사항 (기존 동작 유지)

보조 ID 레지스트리 331개 중 54개가 보조 디스패처에 없으며, 반대로 보조 디스패처의 32011, 3000147은 레지스트리에 없다. 레지스트리 집합은 기존 2026-11-17 분리 작업 때 보존한 원본과 동일하다. 당시 존재하는 70888은 메인 엘븐스트라이크 분기보다 앞선 보조 경로에 가로막힌다(확장 처리에서 먼저 처리되지 않는 경우). 32011(소생 조합), 3000147(테베대항전)은 보조 분기 진입 조건에서 제외되어 있다. 보조 ID에 존재하지만 case가 없는 ID에 대한 실제 DB 아이템 등록 여부와 기대 동작은 확인되지 않았다. 임의로 레지스트리를 바꾸면 기존 동작, 소모 또는 경로가 달라질 수 있어 이번 분리 작업에서는 활성화하지 않았다. ID별 전체 목록은 test.log 참조. 42501의 GM 전용 여부는 운영 DB/기존 권한 정책 확인 필요(기존 GM 지급 패키지에서 제공되지만 이 분기 자체에는 GM 검사 없음). 따라서 권한 조건도 임의 변경하지 않았다.

## 검증

- JDK21 typed stub에서 실제 원본 및 변경 메서드 비교: 준비부/탄약 3780, 직접 분기 및 경주 조작 220, 사전 선택 56 조건 통과. 예외 주입 포함.
- 메인 디스패처 원본과 의도한 5개 개별 분기 외 완전 동일, target modeOf 동일, 확장 처리부는 번호별 경주 위임 외 동일.
- 보조 디스패처 5개 하위 라우터의 ID 집합 전부 정확히 일치(28+97+52+59+43=279).
- 198~203차 기존 typed regression 재실행 및 전체 1913 생산 Java Java21 구문 검사 통과.
- 전체 의존성 타입 컴파일, 실제 인게임 및 MySQL/CacheD 장애·부하 검증은 수행하지 않았다. 미등록 ID의 실제 DB 데이터가 포함되어 있지 않다.

재현: python docs/item_use_phase204_210_20261119/test_phase204_210.py
