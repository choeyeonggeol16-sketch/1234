# Windows 네트워크 부하 검사 증거

최신 수치와 적용 안내는 ../../REPORT_STABILITY.md를 확인합니다. 각 run 디렉터리는 실제 GitHub Actions Windows 실행에서 가져온 원시 JSON, 로그 및 JFR입니다.

- 43563240217: 최초 부하 검사 통과. 4KB 송신 검증기의 불필요한 오류 문자열 생성이 할당량을 왜곡하므로 성능 기준으로 사용하지 않습니다.
- 43565159923: 검증기 수정 및 지속 수신 부하. 수신 21,908,800 프레임 통과, 송신 확대 시 타임아웃.
- 43566252936: 수정 전 송신 정지의 상세 상태. 한 소켓의 ready WRITE_COMPLETE, pending=1, draining=0, native send_pending=0. 다른 세 소켓은 262,144 프레임씩 완료.
- 43567498897: 최종 수신·3회 송신·4KB·재접속32개 GC 회수·backpressure·두 종료 경합·기존 회귀 통합 성공. 최종 수치 기준 실행입니다.
- 43567167226: StoreLoad 수정 후 송신 1,048,576 프레임씩 세 번 및 4KB 131,072 프레임 통과. 마지막 종료 검사에서 native close-start를 Java CLOSED와 혼동한 테스트 가정을 바로잡았습니다. 전체 검사 성공으로 표시하지 않습니다.

BASELINE.json은 직전 배포 481ba8c의 전체 src 1,712개 해시와 native 파일 해시입니다. verify_stability_scope.py는 CUserSocket 이외의 소스 및 native 코드/DLL/PSS 보존을 확인합니다.

재실행: Windows x64 / Temurin21에서 tests/run_windows.ps1 후 tests/run_stability.ps1. build/stability-{rx,java}.json, build/stability-{rx,java}.jfr, logs/stability-{rx,java}.log가 생성됩니다. JFR의 환경 변수·시스템 속성 초기 덤프는 비활성화하며 저장 후에도 해당 이벤트 수를 검사합니다.

수신 성능 경로는 실제 CIOServer/CSocket/FFM/네이티브 L1 프레이밍·디코더와 테스트 게임 수신 소유자를 사용합니다. 별도 무결성 모드는 해제 전 평문의 순서 의존 체크섬을 비교합니다. 송신은 실제 CUserSocket/자바 풀/FIFO/암호화/FFM/DLL을 통과한 모든 프레임의 바이트를 비교합니다. 참조 코덱은 제공된 같은 DLL의 별도 키 상태이며 독립적인 코덱 재구현이 아닙니다. 게임·계정·스케줄러는 fixture이며 DB·실제 게임 콘텐츠는 이 시험의 대상이 아닙니다.
