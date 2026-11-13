# 현재 네트워크 원본 대조 — 배포03b1453

접속·송수신·직렬화·버퍼·타이머·등록·정상 종료/회수의 현재 실행 경로를 확인했다. 원본과 동일한 전체 클래스 ABI/오류/게임 프로필 복원 완료를 뜻하지 않는다.

명확히 남은 진단은 CUserSocketReleaseVerifier의 종료 후 양수 잔여 참조 확인과 CUserSocket 참조 종류별 횟수·호출 위치 추적이다. 기존 I/O 회수와 Java cleanup은 이미 있다. MAC 클라이언트 연동·원본 전체 운영 출력·상위 서비스 분기 경계·실운영 검증은 별도로 표시했다.

[전체 대조 보고서](ctransport_current_network_audit_20261113/README_KO.md) · [156개 선택 함수 목록](ctransport_current_network_audit_20261113/FUNCTION_INVENTORY.csv) · [원본/현재 근거 JSON](ctransport_current_network_audit_20261113/CURRENT_COMPARISON.json)

현재 배포 커밋03b14538d2a813007f4b964ff207ae6aeb8f54ac. 이번에는 분석 자료만 추가하고 DLL/src/배포 ZIP은 바꾸지 않는다. 수록156개 진입점/38개 대조 행은 복원율의 분모가 아니다.
