# RKern 브로드캐스트 1~55차 검증 — 최종 보고서

## 판정

이번 자동 테스트 범위에서 **추가로 재현된 production 서버 버그는 0건**이다. 단, 실전 게임 전체 및 5,000명 장시간 부하를 검증한 의미는 아니다.

## 검증 결과

- 1~55차 모든 원본 ZIP의 CRC 검증 및 직전 단계의 내부 파일 바이트 비교: **55차 전부 통과**.
- 각 단계의 전체 Java 1,710~1,711개 파일을 Java 21로 **55차 각각 독립 전체 컴파일: 55/55 통과**.
- 각 차수의 당시 내장 Java 패킷 바이트·경계 회귀 테스트: **49/49 통과**.
- 내장 패킷 테스트가 없던 **4·5·15·16·17·18·41차**, 당시 변경 스크립트를 실제 직전 SRC에서 다시 실행, 모든 생산 파일 바이트 비교: **7/7 통과**.
- 55차 누적 원본 기준 **Windows 실제 CTransport.dll / IOCP / TCP 방송 테스트: 10회 연속 통과**.
- 네이티브 DLL은 0~55차 동안 변경되지 않음. SHA256: 0801f22fedac95ee29d33c747c5e4019c3b36d79d31615c7d84cc79405258606
- 18차에서 기존 Java 클래스 1개 제거를 확인. 1~17차 Java 1,711개, 18~55차 1,710개.
- 수정 이력이 있는 총 경로: 153개(보고서 문서 3개 포함).
- 최신 64차 ZIP SHA256: 1fd040e4bb4fe89c9c8fe1d8e5f6c2877fbfb6e990f5e551016fe8dc1557a23d, 검증 중 production 소스 수정 없음.

### 테스트를 수행하며 발생한 검증기 문제

3차 패킷 시험의 Proto 패키지 import를 과거 빌드 방식대로 생성하지 않아 최초 시험 컴파일이 실패했다. 이를 정정한 **동일 3차 패킷 비교 시험은 통과**했고, 3차 전체 게임 Java 컴파일도 처음부터 통과했다. 또한 병렬 테스트 결과의 이름 충돌로 이전 실패 기록이 덮이는 집계 오류가 있어서, 각 matrix job의 원본 아티팩트만 사용해 다시 집계했다. 둘 다 서버 코드 오류가 아니라 검증 환경 오류로 확인했다.

### 검증 범위의 한계

49건은 차수별 패킷 회귀 시험 건수이며 1~55차의 실제 게임 기능 전체를 뜻하지 않는다. 7개 차수의 재실행 검증은 **코드 재현과 원본 보호 조건 검증**이며 별도 실전 패킷 송수신 테스트와 동일하지 않다. Windows IOCP 10회는 **55차 누적 코드**를 4개 연결의 실제 TCP·서로 다른 L1 암호화 키·16KiB 공유 프레임으로 시험했다. 각 중간 차수마다 Windows 네이티브 10회를 따로 실행한 것은 아니다. 대규모 동접, 혈맹·공성·던전 복합 상황과 장시간 실서버 검증은 별도 확인이 필요하다.

## 검증 실행 원본

- [Historical CRC and file-diff audit](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43762442247)
- [Corrected 55-stage Java21 and parity reassembly](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43764000440)
- [Seven historical migration replay checks](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43764222527)
- [Windows native IOCP phase55 10 rounds](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43763518551)
- [Final phase64 compile and parity](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43760972835)
- [Final phase64 Windows IOCP 10 rounds](https://github.com/choeyeonggeol16-sketch/1234/actions/runs/43761296603)

## 적용 원칙

**현재 production 기준은 기존 64차 전체 SRC 그대로 유지한다.** 옛 차수의 파일을 되살리거나 병합하지 않는다. 이번 감사에서 production 소스 변경·신규 fallback·새 worker/queue 추가는 없다.
