# RKern 스탯 JVM 검증과 A/B 측정

## 측정 조건

기준은 재검증 보고서가 올라간 `59e88fd3`의 Java 빌드다. 각 쌍에서 기준과 최종 운영 소스 1,696개를 다시 컴파일한 수정본을 별도 Java 21 프로세스로 실행했다. Linux CPU 0에 고정했고 JVM에는 `-Xms256m -Xmx256m -XX:ActiveProcessorCount=2`를 적용했다. 5쌍의 실행 순서를 AB, BA, AB, BA, AB로 교대했다. 8개 클래스와 6개 레벨로 생성한 48개 실제 캐릭터에 동일한 입력을 순환했다.

- 전투는 실제 공격·명중·방어·회피·마법·회복·무게 getter 조합이다. JVM당 600만 회 워밍업과 200만 회 측정.
- 미리보기는 `StatContext` 생성·스탯 입력·실제 캐릭터 기준 계산·결과 배열 읽기다. JVM당 200만 회 워밍업과 60만 회 측정.
- 버프는 실제 `SkillEffectContainer`에 STR·WIS 효과를 붙이고 제거하는 한 사이클이다. JVM당 22만 회 워밍업과 9.6만 회 측정.

32번째 호출마다 벽시계 지연을 샘플링했다. CPU와 할당은 호출을 실행한 Java 스레드의 누적치 차분이다. GC 횟수와 시간은 전체 JVM 누적치 차분이다. 힙 사용량은 측정 시작·종료 시점의 관측치다.

## 5쌍 중앙값

| 부하 | 처리량 기존 → 수정, 회/초 | CPU 기존 → 수정, ns/op | 할당 기존 → 수정, B/op | p50 기존 → 수정, ns | p99 기존 → 수정, ns | p99.9 기존 → 수정, ns | GC 기존 → 수정, 회 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 전투 | 19,093,901 → 25,382,650 | 47.62 → 32.87 | 0 → 0 | 78 → 59 | 110 → 85 | 191 → 202 | 0 → 0 |
| 미리보기 | 5,226,781 → 7,995,369 | 100.91 → 100.63 | 80 → 80 | 82 → 87 | 1,533 → 193 | 19,902 → 17,091 | 0 → 1 |
| 버프 | 1,753,961 → 1,779,200 | 263.12 → 436.00 | 240 → 248 | 216 → 372 | 1,799 → 2,225 | 11,738 → 9,500 | 1 → 1 |

세 부하의 일반 입력 체크섬은 모든 쌍에서 일치했다. 전투 처리량의 중앙값은 증가했지만 쌍별로는 5회 중 2회만 증가했다. CPU/op은 5회 중 4회 높았다. 미리보기 p99는 5회 모두 낮았고 할당은 80 B/op로 같았다.

버프 적용·해제는 **5쌍 모두 CPU/op과 p50이 높아졌고 8 B/op 더 할당됐다**. 중앙 p99는 1,799에서 2,225 ns로 늘었으며 쌍별 개선 방향은 3회 증가, 2회 감소였다. 버프 누적값을 정확하게 보관하는 변경을 포함한 최종 빌드에서 관측한 비용이다. 3개 워크로드의 수치는 소켓·DB·동접을 포함하지 않는다. 컨테이너 CPU 할당량은 2개이고 다른 CPU의 검증 작업도 같은 할당량을 사용하므로 절대 처리량과 p99.9의 쌍별 변동이 크다. 시작·종료 시점의 힙 사용량 차이에는 GC 시점이 포함된다.

별도 S10 시험에서는 미리보기의 임시 배열 하나를 덜 만드는 변경을 당시 기능 수정본에 더해 짧은 부하와 긴 부하를 각각 5쌍 비교했다. 긴 부하는 400만 회를 워밍업하고 200만 회를 측정했다. 두 부하 모두 할당은 약 80에서 40 B/op로 감소했다. 짧은 부하 p99는 110에서 251 ns로 늘었다. 긴 부하 중앙 처리량은 초당 2,139만 회에서 2,046만 회로 낮아졌고 p99.9는 146에서 1,524 ns로 늘었다. 미리보기 기능 수정본을 유지했다.

최종 5쌍은 `full-final`로 기록했다. 별도 배열 변경 시험에서는 A가 당시 기능 수정본, B가 임시 배열을 제거한 후보였다. [쌍별 원시 TSV](CREATURE_STAT_AB_20261127.tsv)

## 기능 회귀

- 경계값 조합 1,440개와 8개 클래스의 실제 인벤토리 등록·장비 바인딩·버프 중첩·해제 128회를 두 빌드에서 실행했다. 결과 체크섬은 모두 `695560bb8af4e771`이었다. 한 소유 스레드의 버프 재적용·제거와 동시 조회에서도 인접 스탯 변동이나 제거 후 누적이 없었다. 빈 지도·세트 카탈로그를 사용했다.
- 기존 실제 객체 생명주기 시험의 112,832개 단언을 두 빌드에서 통과했다. 클래스/레벨 변경·성장 이력·최대 HP/MP 수치, NPC 원형 교체·인스턴스 격리를 포함한다.
- 입장 준비 상태를 게시한 loopback 클라이언트에서 평상시 스탯 투자 6종의 암호화 응답을 복호화했다. STR의 상태/무게, DEX의 상태/방어, CON의 무게 응답이 2회에서 1회가 됐다. 결과 스탯과 UI 상세 값 종류는 같았다. 각 투자에서 저장 요청이 1회 발생했다.
- 지식빙수의 적용/제거 SP 패킷이 0/7에서 7/0으로 정정됐다. COMA_B 적용/해제 상태 프레임은 각 7개에서 6개로 줄었고 마지막 프레임의 AC/ER는 캐릭터 값과 일치했다. 동일 재적용과 무알림 해제에서는 양쪽 모두 프레임이 없었다. 혈맹 전체 스탯 버프의 무게와 마법 상태 알림은 각각 2회에서 1회가 됐다.

암호화 시험은 실제 socket owner에서 동작하고 캐릭터 저장 호출을 기록한다. DB 재접속·전체 로그인·운영 전투 부하는 별도 검증 범위다.

## 실행

`BASE_CLASSES`, `CANDIDATE_CLASSES`에 전체 Java 21 빌드 디렉터리를 지정한다. `DEPENDENCIES`는 서버 빌드에 사용한 의존 JAR의 클래스패스다. 아래 명령을 저장소 루트에서 실행한다. `CPU`는 고정할 Linux CPU 번호다.

```sh
docs=rkern/docs/nc_creature_stat_audit_20261125
tests=$(mktemp -d)
git show "59e88fd3:$docs/TestCreatureLifecycle.java" > "$tests/TestCreatureLifecycle.java"
javac --enable-preview --release 21 -encoding UTF-8 \
  -cp "$BASE_CLASSES:$DEPENDENCIES" -d "$tests" \
  "$docs/CreatureStatBenchmark.java" "$docs/CreatureStatCrosscuttingRegression.java" \
  "$tests/TestCreatureLifecycle.java" \
  rkern/docs/jproring_selector_transport_20261120/SelectorTransportTest.java \
  "$docs/CreatureStatWireRegression.java"

run() {
  for mode in combat preview skill; do
    taskset -c "$CPU" java --enable-preview -ea -Xms256m -Xmx256m \
      -XX:ActiveProcessorCount=2 -cp "$tests:$1:rkern/src:$DEPENDENCIES" \
      CreatureStatBenchmark "$mode"
  done
}

for pair in 1 2 3 4 5; do
  if [ $((pair % 2)) = 1 ]; then
    printf '%s\n' "base-$pair"; run "$BASE_CLASSES"
    printf '%s\n' "candidate-$pair"; run "$CANDIDATE_CLASSES"
  else
    printf '%s\n' "candidate-$pair"; run "$CANDIDATE_CLASSES"
    printf '%s\n' "base-$pair"; run "$BASE_CLASSES"
  fi
done
```

`l1j.server.server.model.CreatureStatCrosscuttingRegression`과 `l1j.server.server.model.TestCreatureLifecycle`은 같은 클래스패스로 실행한다. `CreatureStatWireRegression`은 `baseline` 또는 `candidate` 인수를 받아 각각 새 빈 작업 디렉터리에서 실행한다. 이때 클래스패스의 서버 클래스와 리소스는 절대 경로로 지정한다.


수정본 전용 장비·동시 갱신 검사는 최신 소스에 들어 있는 확장 생명주기 시험을 사용한다. 아래 명령은 위와 같은 셸에서 실행한다.

```sh
mkdir -p "$tests/final"
javac --enable-preview --release 21 -encoding UTF-8 -cp "$CANDIDATE_CLASSES:$DEPENDENCIES" \
  -d "$tests/final" "$docs/TestCreatureLifecycle.java"
java --enable-preview -ea -cp "$tests/final:$CANDIDATE_CLASSES:rkern/src:$DEPENDENCIES" \
  l1j.server.server.model.TestCreatureLifecycle
```
