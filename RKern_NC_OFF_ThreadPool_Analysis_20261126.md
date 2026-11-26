# NC OFF 스레드풀 및 반복 작업 실행 구조 분석 — 2026-11-26

## 분석 범위와 출처
직전 대화의 분석 취지를 기존 스케줄러 보고서 및 과거 소스/바이너리 분석 기록으로 복원한 보충 보고서다. 직전 답변 전문은 확보하지 못했으며, 이번 업로드에서 OFF 원본을 새로 전수 분석한 것은 아니다.

- [기존 NC OFF 스케줄러 분석](RKern_NC_OFF_Scheduler_Analysis_20261126.md)
- [Open_LU4_L2OFF](https://github.com/luannbr/Open_LU4_L2OFF) — 공개 C++20 이식판; NC 내부 원본과 동일한 버전으로 간주하지 않는다.
- 이전 분석 기록: `L2Server_네트워크소스_확인보고서_20261023.txt`, `NC_L2Server10_네트워크_최종3항목_바이너리역복원_20261023.txt`, `NC_CacheD_LogD_Auth_네트워크_코드대조_20261023.txt`.

## 1. 핵심 결론
L2Server의 일반 반복 게임 작업은 기능마다 독립적인 스레드풀을 만드는 방식보다 기존 IO 스레드와 thread-local timer를 재사용하는 구조다. 반복 작업 등록과 실행 스레드 생성은 서로 다른 개념이다. 모든 NC 프로세스가 하나의 풀을 공유한다거나 별도 업무 스레드가 전혀 없다는 의미는 아니다.

이전 소스 대조에서 L2Server.cpp의 주석은 "Reader threads do work. No worker threads."로 기록되었다. 이는 해당 실행 경로에 대한 설명이지 전체 프로세스의 모든 스레드 부재를 증명하지 않는다. L2Server(10).exe 별도 바이너리 분석에서는 IOCP 완료 워커 수가 min(CPU 수, 8)로 확인되었다. 이 수치를 다른 버전의 OFF 소스에 그대로 적용하지 않는다.

## 2. 일반 반복 작업
기존 보고서에 기록된 호출 흐름:

```text
IOThreadCallback::IOThread_common()
    -> IOThreadNextSyncTime()
    -> CIOObject::TimerDispatch()
    -> CThreadLocalTimer::Dispatch()
    -> 만료된 타이머 callback
```

- CAnnounceTimer, CReservationAnnounceTimer, CReservationNPCSpawnTimer, CRespawnTimer, CIODeadlockChecker는 공용 AddTimer 등록 경로를 사용한다.
- 이전 대화 분석에는 CThreadLocalTimer의 50ms 해상도, 200슬롯(10초 범위), 장기 예약 priority queue, CCreature::TimerExpired()의 Heartbeat 및 AddTimer 재등록이 기록되어 있다. 666ms Heartbeat 및 초기 랜덤 오프셋 역시 이전 분석 기록이며 이번에 원본 줄 단위로 재검증하지 않았다.
- 50ms 동기화 기준은 모든 기능의 실제 반복 주기가 50ms라는 의미가 아니다.
- 예약 만료 때 callback 실행, 필요 시 재예약하는 방식은 기능별 무한 루프 스레드나 독립 ScheduledExecutorService를 요구하지 않는다.

## 3. 프로세스별 구분
| 프로세스 | 이전 분석에서 기록된 구조 |
|---|---|
| L2Server | IO 완료 및 일반 게임 타이머 처리; thread-local timer |
| L2NPC | 기존 IO 스레드의 TimerDispatch |
| CacheD | CThreadManager IO 풀, 스레드별 타이머 큐; 과거 소스 대조의 상한 min(CPU*2,16) |
| LogD | CThreadManager IO 풀; 과거 소스 대조의 상한 min(CPU*2,8) |
| L2Auth / AuthD | 별도 TimerThread/Timer Queue 및 IOThreadServer/IOThreadInt; 별도 설정 기반 |

위 수치와 명칭은 과거 분석 대상별 기록이다. 각 프로세스의 정확한 모든 전용 스레드 및 동기 DB 처리 경로를 이번 보고서에서 전수 확정하지 않는다.

## 4. RKern 적용 원칙
현재 브랜치의 CIO 소유 실행과 CThreadLocalTimer/Timing Wheel을 재사용한다. 일반 반복 게임 작업은 소유 샤드에서 예약 및 실행하고, 캐릭터별 공통 Heartbeat에 적합한 경량 처리는 통합 후보로 삼는다. 별도 실행이 필요한 동기 DB/파일 IO와 장시간 작업을 IO/CIO 워커에 강제로 합치지 않는다. 기존 CacheD 및 NPC 실행 소유권을 보존한다.

검토 후보: RealTimeClock, L1GameTimeClock, AuraController, ShipTimeController, DominanceTimeController, BossController, GameEffectActionController, GameSoundController 및 이벤트 전용 반복 스레드. 이는 이전 조사 후보 목록이며 현재 최신 브랜치에 모두 남아 있다는 의미는 아니다. 제거/통합 전에 실제 호출부와 작업량, 실행 소유권, 종료 조건을 확인해야 한다.

안전 조건: 동일 소유자 직렬 실행, 타이머 중복 등록 방지, 취소/재등록 및 재접속 generation 검증, 블로킹 작업 격리, 예외 후 반복 생존, 타이밍 지연 및 부하 실측. 타이머를 통합한다는 이유로 새로운 전용 워커를 추가하거나 기존 게임 동작을 바꾸지 않는다.

## 5. 변경 범위 및 검증 상태
문서만 신규 추가한다. 기존 Java 소스 및 기존 스케줄러 보고서는 변경하지 않는다. 이번 작업은 과거 분석을 보존하는 것이며 새로운 소스 전수조사, 성능 측정, 전체 컴파일 및 인게임 검증은 수행하지 않았다.
