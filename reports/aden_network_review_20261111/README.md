# aden.zip 네트워크 코어 검토

[상세 보고서](REPORT_KO.md) · [개선 목록 CSV](findings.csv) · [검사 범위/결과](validation.json) · [재현 검사 실행 방법](PROBES_KO.md)

Windows IOCP + Java 21 FFM + native codec + 샤드 실행 + 버퍼 풀을 갖춘 자체 코어입니다. 개선 우선순위 7개를 코드 위치·영향·수정 방향과 함께 정리했습니다.

먼저 검토할 부분은 실행 중 패킷과 로그아웃 정리의 직렬화, 종료 전체 시간 제한입니다. 수신 stash 데이터 손상 조건·await 즉시 반환·registry tombstone 누적은 선택한 실제 Java 클래스로 로컬 재현했습니다. DLL/게임은 가짜 구현으로 대체했으며, 실게임 장애나 성능 저하율을 확정한 검사는 아닙니다.

입력 소스는 수정하지 않았습니다. 원본 Java와 DLL은 이 보고서 묶음에 포함하지 않았습니다. 전체 서버 빌드·실제 Windows 게임/DB·부하 검증은 남아 있습니다.
