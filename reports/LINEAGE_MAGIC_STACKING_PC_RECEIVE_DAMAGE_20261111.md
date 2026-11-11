# PC 리니지1 마법 중첩 데미지 — Kiyoshi 기준 채택 및 RKern 구현 설계

- 작성일: 2026-11-11
- **채택할 구현 기준: Kiyoshi L1J의 2초 고정 윈도우, 두 번째 마법부터 2/3 연속 감쇄**
- **사용자 필수 요구: 마법 중첩 적용 여부는 반드시 피격자의 `L1PcInstance.receiveDamage(..., boolean applyMagicStack)`에서 판정한다.**
- 이 문서는 **구현 명세·공개소스 조사·테스트 계획**이다. RKern 서버 소스 자체를 수정한 것은 아니다.
- 일본 공개 소스는 NC 서버 원본이 아니며, 실제 게임 시기·버전별 상세 규칙은 독립 실측 대상이다.
- 별도 문서: [2016년 일본 단일 마법 데미지 공식 및 클래스·속성저항 설계](./LINEAGE_JP_MAGIC_DAMAGE_2016_FORMULA_IMPLEMENTATION_20261111.md) / [실측표 2,719개 데미지 재현 검증](./LINEAGE_JP_MAGIC_DAMAGE_VALIDATION_20261111.md).

## 1. 결정사항

**우선 채택: Kiyoshi의 횟수 기반 2초 고정 윈도우.**

1. **첫 유효 마법 피격은 100%**.
2. 첫 유효 피격을 기점으로 **2,000ms 미만**에 들어오는 후속 적격 마법은 두 번째부터 차례로 2/3, 4/9, 8/27, 16/81…을 적용한다.
3. 최초 피격 기점으로 **2,000ms 이상** 경과했다면 해당 피격은 새 윈도우의 첫 타격이므로 **100%**.
4. 타이머는 **첫 타격 시각 기준**으로, 두 번째·세 번째 마법마다 2초를 다시 시작하지 않는다.
5. 중첩 카운터·기준 시각은 **피격자 PC에 귀속**시킨다. 공격자별 별도 중첩은 사용하지 않는다.
6. **중첩 적용 가능 여부는 L1PcInstance.receiveDamage(...)의 boolean 인자**로 판정한다. false인 피해는 감쇄하지 않고 중첩 시각·카운터도 변경하지 않는다.
7. **중복 적용 금지**: 스킬 계산과 receiveDamage 양쪽에서 2/3을 중복 곱하지 않는다.
8. 기존 CIO 게임 상태 소유 실행·공격 처리 순서와 기존 HP/죽음/반격 로직을 유지한다. 신규 워커·큐·스레드는 금지한다.

Kiyoshi 방식이 **이번 RKern 작업의 선택된 복원 후보**라는 뜻이다. NC 본서버의 확정식이라고 단정하지 않는다.

## 2. Kiyoshi 공식

적격 마법 타격 횟수를 현재 고정 2초 윈도우의 1부터 시작하는 정수 `k`라고 정의한다.

~~~text
WINDOW_MS = 2,000
BASE = 2 / 3

k = 1: multiplier = 1
k >= 2: multiplier = (2 / 3) ^ (k - 1)

appliedDamage = damageComponent * multiplier
~~~

| 적격 마법 타격 | k | 승수 | 300 데미지에 곱한 값 (최종 정수화 전) |
|---:|---:|---:|---:|
| 첫 번째 | 1 | 1.000000 | 300.000 |
| 두 번째 | 2 | 0.666667 | 200.000 |
| 세 번째 | 3 | 0.444444 | 133.333 |
| 네 번째 | 4 | 0.296296 | 88.889 |
| 다섯 번째 | 5 | 0.197531 | 59.259 |
| 여섯 번째 | 6 | 0.131687 | 39.506 |

**경계**: `elapsed < 2000`이면 같은 윈도우, `elapsed >= 2000`이면 신규 윈도우다.

**Kiyoshi 실코드의 상태 변화**:
- oldTime=0일 때 최초 피격 시 oldTime=now, count=0, 감쇄 100%.
- 그다음 `now-oldTime < 2000`이면 count를 먼저 증가시켜 `pow(2/3, count)`를 곱한다.
- `now-oldTime >= 2000`이면 oldTime=now, count=0, 감쇄 100%.
- Kiyoshi 해당 코드에는 일본 원본의 **4초 부분 회복/지수 8 상한을 임의로 가져오지 않는다**.

## 3. 공개 소스 대조: 사실 확인

| 코드 계열 | 감쇄 조건 | 두 번째 타격 | 회복 방식 | 코드상 특이점 |
|---|---|---:|---|---|
| [Kiyoshi](https://github.com/L1j-Kiyoshi/kiyoshicommit/blob/master/src/jp/l1j/server/model/L1Magic.java) **[채택]** | 피격자 count, 2초 | 66.67% | 2초 경과 시 count=0 | MIND_BREAK / isKiringku 제외; 4초 회복 분기 없음 |
| [일본 l1j/jp](https://github.com/l1j/jp/blob/master/src/jp/l1j/server/model/L1Magic.java) | 피격자 count, 2초 | 100% | 2~4초 부분 회복 | 세 번째부터 2/3, 지수 최대 8 |
| [일본 l1j/jp 키링크](https://github.com/l1j/jp/blob/master/src/jp/l1j/server/model/L1WeaponSkill.java) | 피격자 count, 2초 | 100% | 2~4초 | 키링크 피해에도 유사한 중첩 처리 |
| [대만 l1j-tw](https://github.com/jiansoft/l1j-tw/blob/master/src/l1j/server/server/model/Instance/L1PcInstance.java) | 직전 피격 간격 | 시간에 따라 가변 | 매 적격 피격 시 시각 갱신 | receiveDamage의 boolean isMagicDamage 사용 |
| [중국 l1jcn-zwb](https://github.com/a2323987/l1jcn-zwb/blob/master/L1J/src/com/lineage/server/model/Instance/L1PcInstance.java) | 직전 피격 간격 | 시간에 따라 가변 | 매 적격 피격 시 시각 갱신 | 대만 계열과 유사한 계산 |
| [Lineage 880 Custom](https://github.com/sunsune331-ai/lineage-880-custom/blob/main/src/com/lineage/server/model/Instance/L1PcInstance.java) | 밀리초 단위 시간표 | 시간에 따라 가변 | 매 적격 피격 시 시각 갱신 | 1~2000ms에 대한 `Map<Long,Double>` 조회 |

**중요**: 저장소 간 코드 복사·포크가 있으므로 "저장소 수 = 독립적인 증거 수"가 아니다. 위 사례는 **구현 형태**를 보여주고, 특정 연도의 NC 서버 내부 구현을 입증하지 않는다.

참고 실측 토론: [대만 PC 리니지 연속 마법 감소 실험](https://forum.gamer.com.tw/C.php?bsn=842&snA=642700). 2/3 감쇄 자체에 대한 참고는 되지만, 그 자료만으로 모든 버전의 첫·둘째 타격 및 구현 위치를 확정해서는 안 된다.

## 4. 비교 계열의 공식 (채택하지 않음)

대만/중국 일부 L1J에는 아래와 같은 시간 간격형 식이 존재한다.

~~~java
double interval = (20D - (nowMs - oldMs) / 100D) % 20D;
if (interval > 0) {
    damage *= (1D - interval / 30D);
}
~~~

`0 < Δt < 2000ms`에서 대략 다음과 같이 단순화된다.

~~~text
R_time(Δt) = 1/3 + Δt/3000
100ms -> 36.67%
500ms -> 50.00%
1000ms -> 66.67%
1500ms -> 83.33%
2000ms -> 100.00%
~~~

이 구현은 **중첩 횟수 기반이 아니며**, 일부 정확한 경계(Δt=0ms의 modulo 및 2000ms 조건)는 직관적 모델과 다른 결과를 낼 수 있다. RKern 채택안에는 포함하지 않는다.

880 Custom은 별도의 2000개 시간별 감쇄비 테이블을 만든다. 이 또한 횟수형 Kiyoshi와 다르므로 **도입하지 않는다**.

## 5. [사용자 필수] L1PcInstance.receiveDamage(..., boolean) API 계약

**핵심 계약**: 피격 PC의 receiveDamage로 들어오는 boolean은 **"이번 피해에 마법 중첩을 적용할 것인가"**다. 마법 데미지 여부, 공격자의 직업, 스킬 이름만으로 receiveDamage가 자체 추정하지 않는다. 분기는 **호출자가 명시적으로 지정**한다.

예시 호출:
~~~java
// 적격 마법 공격: 중첩 적용
targetPc.receiveDamage(caster, damage, true);

// 물리 공격, 반사, 예외 마법 등: 중첩 미적용
targetPc.receiveDamage(attacker, damage, false);
~~~

기본 개념:
~~~java
// 기존 L1PcInstance.receiveDamage의 오버로드/타입과 맞춰야 하는 설명용 구조.
// 실서버의 HP 변경/상태이상/반격/사망/CacheD 연동 본문을 교체하라는 뜻 아님.
public void receiveDamage(L1Character attacker,
                          double damage,
                          boolean applyMagicStack) {
    if (damage <= 0 || getCurrentHp() <= 0 || isDead()) {
        return; // 실제 진입 조건/처리 순서는 기존 운영 코드 유지
    }

    if (applyMagicStack) {
        damage = applyMagicStackToDamage(damage);
    }

    // 기존 HP 감산 및 부수효과 경로로 계속 진행
}
~~~

**정책표 (구현에서 반드시 준수)**:

| 입력/사건 | 피해 감쇄 | 중첩 상태 변경 |
|---|---|---|
| `applyMagicStack=false` | 없음 | **절대 변경하지 않음** |
| `applyMagicStack=true`, 첫 유효 타격 | 없음(100%) | 새로운 2초 윈도우 시작 |
| `true`, 같은 윈도우의 2타 이상 | `(2/3)^(k-1)` | 같은 윈도우 카운트 증가 |
| `true`, 정확히 2,000ms 이후 | 없음(100%) | 새로운 2초 윈도우 시작 |
| `true`, 0/무효 피해 | 없음 | **변경하지 않음** (RKern 안전 구현 정책; 본섭 확정 실측 아님) |
| `false` 피격을 적격 피격 사이에 삽입 | 없음 | 해당 이벤트는 중첩 시계와 횟수에 영향 없음 |

- 기존 receiveDamage의 세 번째 boolean이 이미 다른 뜻으로 쓰이는지 **최신 RKern 소스에서 확인한 뒤에만** 사용해야 한다. 기존 플래그 의미를 조용히 바꾸거나 기존 호출부의 `false`를 전체 변경하면 안 된다.
- `receiveDamage(attacker, damage)`처럼 boolean 없는 오버로드도 기존 정책을 유지한다. 인자 기본값을 임의로 true로 바꾸지 않는다.
- 중첩 제외 후보: Kiyoshi 소스는 `MIND_BREAK` 및 `isKiringku`를 제외한다. **호출부가 false를 전달**하도록 하고, 마법 자체가 아니어도 주어진 boolean 계약을 우선한다.
- 땡땡이/도트/범위형 스킬/마법봉/스킬 발동 무기/키링크/반사/흡혈/MP감소/NPC마법/힐을 데미지로 변환하는 경우 각각 분류표를 작성하여 명시적으로 true/false를 전달한다. **초기 일괄 true 강제 금지**.
- 피격 PC 한 명에 복수의 공격자가 들어와도 한 상태를 공유한다. 동일 캐릭터에 대해 중첩 상태 변경과 HP 처리가 **순서대로 실행**되어야 한다. CIO 소유 샤드가 순서를 보장하는지 호출 경로를 확인하고 기존 소유 구조에서 처리한다.
- 새 객체/Map/Timer/Thread/Queue는 이 로직을 위해 도입할 필요 없다. `long` 시각과 `int` 카운터면 충분하다.

## 6. Java 21용 Kiyoshi 상태 로직 참고 구현 (미적용)

이 예시는 **원본 Kiyoshi의 2초/2⁄3 지수 거동을 재현하는 최소 로직**만 담는다. 별도 스레드나 할당은 필요 없다.

~~~java
// L1PcInstance 필드
private static final long MAGIC_STACK_WINDOW_NS = 2_000_000_000L;
private long magicStackStartNs;
private int magicStackCount; // 첫 유효 피격 후 0, 다음 피격부터 1,2,3...

// 호출 규칙: receiveDamage(..., true)이고 유효한 양수 피해인 경우만 호출.
// 동일 PC 상태의 순차 실행 정책 확보 필수.
private double applyMagicStackToDamage(final double damage) {
    final long now = System.nanoTime();
    final long start = magicStackStartNs;

    if (start == 0L || now - start < 0L
            || now - start >= MAGIC_STACK_WINDOW_NS) {
        magicStackStartNs = now;
        magicStackCount = 0;
        return damage;
    }

    if (magicStackCount < Integer.MAX_VALUE) {
        ++magicStackCount;
    }
    return damage * Math.pow(2.0D / 3.0D, magicStackCount);
}

private void resetMagicStackState() {
    magicStackStartNs = 0L;
    magicStackCount = 0;
}
~~~

**주의 1 — 기존 Kiyoshi와 동일한 부분:** 최초 이벤트가 100%, 2초 고정 윈도우, 다음부터 2/3 지수, 중첩 이벤트 간 기준 시각 유지. `System.nanoTime()`은 경과 측정에 적합한 단조 시간이며 원본의 `currentTimeMillis()`와 실제 시스템 시계 변경 상황에서만 동작이 다를 수 있다.

**주의 2 — 원본과 정확한 정수 데미지를 맞추려면 적용 위치가 중요하다.** 공개 Kiyoshi `L1Magic`은 **INT·속성 계수 이후, 크리티컬 1.5배와 OriginalMagicDamage 추가 이전**에 중첩을 곱한다. 반면 위 `receiveDamage(attacker, damage, true)` 스케치는 **최종 피해**를 감쇄한다. 둘은 특히 크리티컬·고정 추가 피해·중간 내림에서 같은 결과를 보장하지 않는다.

따라서 **최종 RKern 연결에서 강제할 계약**은 다음과 같다.
1. **중첩 적용 여부와 상태 갱신은 receiveDamage의 boolean이 결정**한다 (반드시 지킴).
2. **수치의 적용 단계**는 현재 복원된 마법 공식 및 Kiyoshi의 단계 순서를 함께 대조한 후 확정한다. Kiyoshi와 동등한 산술을 구현하려면 receiveDamage에서 중첩 대상 기초 피해만 감쇄한 다음 크리티컬/추가 데미지 항목을 처리할 수 있도록 **기존 로직의 원시 분해값 또는 추가 primitive 인자**를 전달할 수 있다. 이때 단순 `damage * R`로 최종피해를 일괄 곱해 Kiyoshi와 정확히 같다고 주장하지 않는다.
3. 플래그는 연산 여부만 결정하며, false일 경우 **그 어떤 중첩 상태도 갱신하지 않는다**. 전체 데미지/HP 감소는 정상 진행한다.
4. 실제 receiveDamage API 오버로드와 호출부는 **최신 전체 SRC 확인 후** 최소 변경으로 통합하고, 기존 HP 처리 경로를 지우거나 새 큐/워커를 추가하지 않는다.

## 7. 반드시 검사할 테스트 케이스

| 테스트 | 입력 시퀀스 | 기대 결과 |
|---|---|---|
| 연속 1~5타 | t=0/100/200/300/400ms, 모두 true | 1,2/3,4/9,8/27,16/81 |
| 2초 안팎 | t=0 → 1999 → 2000ms | 1999는 감쇄, 2000은 신규 100% |
| 슬라이딩 방지 | t=0 → 1800 → 2100ms | 2100ms는 1800ms 이후 300ms여도 100% |
| 다른 공격자 | A의 true → B의 true | B도 같은 피격자 누적 상태 공유 |
| false 끼어들기 | t=0 true → 1000 false → 1500 true | false는 감쇄도 기준시각도 변경하지 않음, 마지막은 2타 |
| false 최초 | t=0 false → 100 true → 200 true | true 100% → 2/3 |
| 제외 대상 | MIND_BREAK/키링크 후보 false | 피해는 들어가지만 중첩 상태 불변 |
| 0 피해 | 0 또는 막힌 피해 true | 중첩 상태 불변 (채택 설계 정책) |
| 동시 수신 | 동일 PC로 여러 공격자 병행 | 누락·중복 카운트/HP 경합 없음, 소유 실행 순서 유지 |
| 수명 | 접속 종료/재접속/사망/부활/캐릭터 재사용 | 기존 객체 수명 정책과 맞춰 stale 중첩 없음 |
| 순서 보존 | 마법 → 물리 → 마법 / 스킬 → 키링크 | 지정한 boolean 이외 경로에 중첩 없음 |
| 치명타·MD | 같은 타격을 비감쇄/감쇄 비교 | 최종단계 곱셈과 Kiyoshi의 중간단계 곱셈 차이 측정 |
| 장기 연속 피격 | 2초 안에 매우 많은 유효 타격 | 정수 카운터 오버플로 방지, 0/음수 피해 처리 안전 |
| MR·ER | 방어자 클래스/WIS/MR/속성저항 변화 | 기존 계산식 불변, 선택된 위치에서 중첩 단 한 번만 적용 |

## 8. 현재 상태 / 차후 구현 조건

**완료:** 공개 소스 조사, Kiyoshi 방식 우선 채택, 수학식 및 2초 타임라인, receiveDamage(boolean) 계약, 다른 계열 비교, 상태/성능/테스트 명세.

**미완료:** 실제 RKern Java 코드 반영, 전체 호출부의 boolean 매핑, Kiyoshi 중첩 적용 위치와 복원된 2016 마법 공식의 정수 데미지 일치 검증, 실제 본서버 2초/타격별 실측, JVM 통합/인게임 테스트.

**운영 채택 판단**: 첫 두 타격의 실제 감쇄 여부와 2초 경계는 독립 실측으로 확인하고, `receiveDamage` 내부에서만 중첩 상태를 변경하도록 보장할 것.
