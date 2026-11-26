# NC OFF 스케줄러 구조 분석 — 2026-11-26

## 참조 OFF 소스코드

- [Open_LU4_L2OFF — GitHub](https://github.com/luannbr/Open_LU4_L2OFF)
- [L2Server](https://github.com/luannbr/Open_LU4_L2OFF/tree/main/L2Server) · [L2NPC](https://github.com/luannbr/Open_LU4_L2OFF/tree/main/L2NPC) · [CacheD](https://github.com/luannbr/Open_LU4_L2OFF/tree/main/CacheD) · [AuthD](https://github.com/luannbr/Open_LU4_L2OFF/tree/main/AuthD)

해당 공개 저장소는 C++20 이식판이며, 수정되지 않은 NC 내부 원본과 동일하다고 단정하지 않는다.

## 분석 대상과 범위
- NC OFF 계열 C++ 원본 `Open_LU4_L2OFF`에 대한 직전 분석 결과를 정리한다.
- RKern 기준 브랜치: `rkern/nc-user-timer-integration-20261126`, 기준 커밋 `e330a63ba01210ce81a3419cf0b180b6fba6fc43`.
- 이 문서는 직전 분석의 확인된 요약을 복원한 것이다. 원본 소스의 전체 파일 경로, 줄 번호, 타이머 내부 자료구조 및 정확한 스레드 수는 이번 문서 작성 시 재검증하지 않았다.

## 1. 프로세스별 타이머 구조

| 프로세스 | 직전 분석 결과 |
|---|---|
| L2Server | CThreadLocalTimer 기반, 기존 IO 스레드의 주기적인 타이머 dispatch |
| L2NPC | 기존 IO 스레드에서 TimerDispatch 실행 |
| CacheD | 스레드별 priority queue 기반 타이머 |
| AuthD | 별도 Timer Queue |

L2Server의 일반 게임 타이머를 처리하기 위해 독립적인 범용 ScheduledExecutor/스케줄러 스레드풀을 별도로 두는 구조는 직전 분석에서 확인되지 않았다. 이 결론은 모든 프로세스에 별도 타이머가 없다는 의미가 아니다. AuthD는 별도 Timer Queue로 구분된다.

## 2. L2Server 타이머 실행 경로

직전 분석에서 정리한 호출 흐름:

```text
IOThreadCallback::IOThread_common()
    -> IOThreadNextSyncTime()  (50ms 기준)
    -> CIOObject::TimerDispatch()
    -> CThreadLocalTimer::Dispatch()
```

기존 IO 스레드 실행 루프에서 타이머 처리 시점을 확인하고 해당 타이머를 dispatch하는 방식이다. 50ms는 직전 분석의 동기화 기준이며, 모든 타이머가 반드시 50ms마다 실행된다는 뜻은 아니다. 세부 호출 조건 및 만료 시각 계산은 원본을 재확인해야 한다.

## 3. AddTimer 공용 등록 경로

직전 분석에서 공용 AddTimer 사용 대상으로 확인한 클래스:

- `CAnnounceTimer`: 공지 타이머
- `CReservationAnnounceTimer`: 예약 공지 타이머
- `CReservationNPCSpawnTimer`: 예약 NPC 스폰 타이머
- `CRespawnTimer`: 리스폰 타이머
- `CIODeadlockChecker`: IO 데드락 감시 타이머

타이머 종류별로 별도 실행 스레드풀을 만드는 대신 공용 등록 및 dispatch 경로를 재사용한다. 각 클래스의 상세 주기, callback 재등록 및 종료 정책은 이 보고서에서 확정하지 않는다.

## 4. RKern 적용 기준

- 기존 CIO 소유 실행 구조와 Timing Wheel을 우선 재사용한다.
- NC의 thread-local 실행 정책을 참고하되 C++ 내부 자료구조를 무조건 복제하지 않는다.
- 캐릭터·장비·NPC·공지 등 예약 실행의 책임과 수명 관리를 명확히 한다.
- 동일 소유자 상태의 동시 변경을 방지하고, 로그아웃·재접속·객체 재사용 시 이전 타이머가 새 상태에 영향을 주지 않게 한다.
- IO/CIO 실행부에 동기 DB 접근이나 장시간 블로킹 작업을 억지로 합치지 않는다.
- 중복 예약, 반복 할당, stale callback, 취소 및 재등록 경쟁을 검증한다.
- 별도 범용 스케줄러를 없애는 판단은 실제 호출부와 실행 소유권 검증 후 내린다. AuthD/CacheD 구조까지 동일하다고 가정하지 않는다.

## 5. 현재 RKern 기준

기준 커밋에는 `L1EquipmentTimer.java`와 `CIOThreadCallback.java`의 변경이 포함되어 있다. 직전 작업 보고는 NC식 캐릭터 타이머 4단계 및 장비 타이머 5차 통합이었다. 이 보고서 커밋은 문서 추가만 수행하며 Java 소스, 실행 정책, 설정을 수정하지 않는다.

## 6. 검증 경계

본 문서는 이전 분석 요약을 GitHub에 보존하기 위한 기록이다. 이번 업로드에서는 NC 원본 재열람, RKern 전체 컴파일, Windows IOCP 실동작 및 동시접속 부하 검증을 수행하지 않았다. 그러므로 타이머 통합 전체 완료나 무결성 검증을 이 문서만으로 주장하지 않는다.
