# RKern 물리 데미지 vs 일본 Phil 2015년 연구 — 코드 검수 보고서 (선택 5항목)

**작성일:** 2026-11-11  
**검수 소스:** `RKern_Aden_N01_N07_CompletedJava_NativeGuard_FULL_SRC_20261111.zip`  
**전체 소스 SHA-256:** `26fc7b49baa5ef992372824779becf6a29846bbbc674f9d341a973ce48b01a64`  
**검수 방식:** ZIP 원본 Java 소스 직접 확인 + 일본 원문 수식과 정적 비교. ZIP CRC 통과. **서버 소스는 수정하지 않았음.**  
**일본 출처:** [Phil, 「攻撃ダメージ計算式でのDRの影響」 (2015-09-26; 9/29 결론 추가)](https://recordgame.blogspot.com/2015/09/) · [Phil, 「分間ダメージの計算式に必要な要素」 (2015-09-02, 이후 수정)](https://recordgame.blogspot.com/2015/09/blog-post_2.html) · [Phil, 「損傷の仕様」](https://recordgame.blogspot.com/2015/09/blog-post.html).

> **사용자 지정 제외 범위:** 더블브레이크 발동 확률과 **크래시·퓨리 발동 확률**의 비교·개선 제안은 이 보고서에서 제외한다. 더블히트/더블브레이크 자체의 동작도 별도 검수 대상으로 다루지 않는다. 단, **퓨리의 데미지 적용 *방식***은 사용자가 지정한 제외 대상(확률)이 아니므로 다룬다. 크로우의 **크리티컬 확률 결합식**은 더블브레이크 확률과 다른 항목이므로 포함한다.

## 검수 결과 요약

| 번호 | 항목 | RKern 확인 위치 | 일본 2015 연구식과 대조 | 판정 |
|---|---|---|---|---|
| 1 | DR과 버닝/최종 배율의 적용 순서 | `L1DamageCalcHelper.java:70-76` → `CAttackAction.java:1469-1475, 1482-1491` | RKern은 버닝 1.5배 계산 후 DR, 일본은 DR 후 버닝 1.5배 | **불일치 확인** |
| 2 | 크로우 크리티컬: 캐릭터 확률 + 무기 확률 | `L1SkillProcHelper.java:220-249` | RKern은 합산, 일본 연구는 독립 사건의 합집합 확률 | **불일치 확인** |
| 3 | 소울 오브 플레임의 최대 다이스 조건 | `L1SkillProcHelper.java:281-285`; `CAttackAction.java:1752,1824-1828` | RKern `ranged=true`에서만 최대 다이스, 일본 연구는 근접 무기 최대 다이스 | **코드 조건 역전 의심 강함** |
| 4 | 퓨리 발동 시 데미지 *적용 방식* | `CAttackAction.java:1837-1848` | RKern은 크래시 보너스 기반 추가 가산, 일본 연구는 전체 데미지 2배 | **불일치 확인** |
| 5 | 손상 무기 부분의 최소 데미지 | `L1DamageCalcHelper.java:53-67` | RKern은 최솟값 0, 일본 연구는 최솟값 1 | **불일치 확인** |

**주의:** 위에서 확인한 것은 **2015년 일본 연구모델과 현행 검수 소스가 다르다**는 사실이다. **2019년 2월 PC 리니지 NC 서버와 다른지는 아직 직접 증명하지 않았다.** 본서버 연도·스킬 리뉴얼·PvP/사냥·버프/패시브 정책을 분리해야 한다.

## 1. DR 차감 위치 vs 버닝스피릿 배율

### 일본 2015년 연구

Phil은 확장된 근접 데미지를 간략히 다음처럼 설명한다.

```text
일본 2015년의 단순화된 단계:
기본 무기·캐릭터·스킬 피해를 합산
→ 방어자 DR 차감
→ 버닝스피릿/브레이브멘탈/엘리멘탈파이어/퓨리 등 최종 배율
→ 배율 곱셈으로 생기는 소수점은 내림
```

즉, **DR이 최종 배율 앞**에 있다. 원문은 다른 추가타·스킬의 예외가 있음을 별도로 기록한다.

### 실제 RKern

**`src/l1j/server/server/model/character/Attack/Helper/L1DamageCalcHelper.java:70-76`**

```java
int dmg = weaponPart + charMagic + statBonus + classBonus;
if (dmg <= 0) return 0;
if (procBurning) dmg += (dmg >> 1);
return dmg;
```

**`src/l1j/server/server/model/character/action/CAttackAction.java:1451-1475`**

```java
int dmg = calcPcDamageByTable(_targetPc, true);
// ... PvP 공격 보정 ...
dmg = DamageReductionHelper.applyAllDamageReductions(
    _targetPc, _pc, false, false, dmg);
```

대상 NPC 경로에서도 `calcPcDamageByTable` 뒤에 `calcNpcDamageReduction()`을 차감한다(`CAttackAction.java:1482-1491`).

### 수치 사례

원시 데미지 `D=100`, `DR=10`, 버닝 1.5배가 발동한 경우:

- 일본 모델: `floor((100 - 10) * 1.5) = 135`
- RKern 순서: `floor(100 * 1.5) - 10 = 140`
- **5 데미지 차이**.

**판단:** 현재 단계 순서가 연구식과 다르다. 다만 RKern의 `DamageReductionHelper.applyAllDamageReductions`는 여러 종류의 DR을 취급하므로 **모든 DR을 일괄 앞당기는 패치는 금물**. 원문이 말하는 기본 DR과 RKern의 확률·퍼센트·PvP DR이 동일 계산 단계인지 항목별 분리가 필요하다.

**추가로 주의할 구조:** 버닝이 적용된 뒤 크래시·퓨리/버닝슬래시 등 추가 타격이 `CAttackAction.java`에서 붙고, 이후 PC/NPC DR이 차감된다. 단일 산식 전체의 순서를 실제 호출 그래프로 확인해야 한다.

## 2. 크로우 크리티컬 확률 결합 방식

### 일본 2015년 연구

캐릭터의 근접 크리티컬 확률 `p_c`와 크로우의 무기 크리티컬 확률 `p_w`가 **독립적으로 판정된다면**, 어느 하나가 발생할 확률은:

```text
P(critical) = 1 - (1 - p_c)(1 - p_w)
            = p_c + p_w - p_c p_w
```

### 실제 RKern

**`src/l1j/server/server/model/character/Attack/Helper/L1SkillProcHelper.java:235-249`**

```java
chance = baseCri + statCri + miscCri;
if (weaponType == 58) {
    chance += weaponDoubleDmgChance;
}
if (chance <= 0) return false;
if (chance > 100) chance = 100;
return rnd100() <= chance;
```

즉, **캐릭터 크리티컬 확률과 크로우 확률을 단순 합산한 후 단 한 번 추첨**한다. 일본 연구의 독립 판정 모델보다 겹침 확률만큼 높다.

| 가정 | 독립합집합 방식 | RKern 단순합산 | 차이 |
|---|---:|---:|---:|
| `p_c=5%`, `p_w=10%` | 14.5% | 15% | +0.5%p |
| `p_c=10%`, `p_w=25%` | 32.5% | 35% | +2.5%p |
| `p_c=20%`, `p_w=30%` | 44.0% | 50% | +6.0%p |

**판단:** **2015년 일본 모델과 수학적으로 불일치 확정**. 단, 운영 버전에서 크로우 추가 확률을 독립 판정하는 게 맞는지는 실측으로 결정한다. **임의로 2번 굴리게 바꾸지 말 것.**

## 3. 소울 오브 플레임(SOF) 최대 다이스 적용 대상

### 일본 연구

소울 오브 플레임은 일반적인 근접 무기 최대 다이스 판정으로 분류된다. 무기의 랜덤 데미지를 해당 대형/소형 목표 다이스 **최대값**으로 만든다.

### 실제 RKern

**`src/l1j/server/server/model/character/Attack/Helper/L1SkillProcHelper.java:281-285`**

```java
public static boolean isSoulOfFlameMax(
        final L1PcInstance pc, final boolean ranged) {
    if (pc == null) return false;
    if (!ranged) return false;
    return pc.hasSkillEffect(L1SkillId.SOUL_OF_FLAME);
}
```

**`CAttackAction.java:1752,1824-1828`**에서는:

```java
final boolean ranged = (type1 == 20 || type1 == 62);
final boolean soulFlameMax = L1SkillProcHelper.isSoulOfFlameMax(_pc, ranged);
final boolean forceMaxRandom = critProc || soulFlameMax;
```

따라서 **현재 소스 기준으로 근접 공격(`ranged=false`)은 SOF 최대 다이스를 얻을 수 없고**, 오히려 원거리 공격일 때 `SOUL_OF_FLAME` 버프가 있으면 강제 최대값 후보가 된다.

**판단:** 조건이 일본의 SOF 동작과 반대다. 일반적인 SOF의 근접 전용 규칙 기준에서 **코드상 결함 가능성이 매우 높음**. 운영용 수정 전에 다른 스킬/패시브가 SOF를 별도 경로로 구현했는지, 활에 스킬 상태가 존재 가능한지 확인해야 한다.

## 4. 퓨리: 확률을 제외한 데미지 적용 방식만 비교

**이 절에서는 크래시·퓨리의 발동률, 기본 확률, 외부 설정값을 일절 검토하지 않는다.**

### 일본 2015년 연구

Phil은 퓨리 발생 시 **크래시 추가피해만 두 배가 아니라, DR 이후의 해당 공격 전체 데미지가 2배**라고 정리했다.

### 실제 RKern

**`src/l1j/server/server/model/character/action/CAttackAction.java:1837-1848`**

```java
if (L1SkillProcHelper.hasCrash(cfFlags)) {
    final int crashDmg = L1SkillProcHelper.crashDamageByLevel(_pc.getLevel());
    dmg += crashDmg;
    _crash = true;
    if (L1SkillProcHelper.hasFury(cfFlags)) {
        final int furyDmg = crashDmg * 2;
        dmg += furyDmg;
        _fury = true;
    }
}
```

이는 `base + crashDmg + 2*crashDmg`이므로, **원시 공격 데미지 전체를 2배 하는 구조가 아니다.** 일본 방식과 달리 위의 DR 이전에 해당 추가 데미지가 들어간다.

**예시:**

- 기존 공격 데미지 `100`, 크래시 추가 `40`, DR=`0`.
- 일본 연구 모델의 퓨리 전체 배율: `(100+40)*2 = 280`.
- RKern 가산 방식: `100+40+40*2 = 220`.
- **60 데미지 차이**.

**판단:** 발동 확률과 무관하게 **데미지 연산 구조가 불일치**한다. 단, 2019년 이후 퓨리의 효과나 스킬 레벨 차이가 있을 수 있으므로 동일 버전 실측 전에는 무조건 전면 수정하지 않는다.

## 5. 손상 무기 데미지 하한값

### 일본 2015년 실측

일본 연구의 손상 무기 계산은 다음과 같다.

```text
weaponCore = max(
    weaponDice + weaponAddDamage + weaponEnchant - weaponDamagePenalty,
    1
)
final = weaponCore + elementalBonus + stat/characterBonus + ...
```

하한 **1은 무기 부분만** 대상으로 한다. 속성 데미지·캐릭터 추가타격까지 무조건 1로 보정하는 뜻은 아니다. 일본 연구는 강화/손상값의 개별 조건도 별도로 기록했다.

### 실제 RKern

**`src/l1j/server/server/model/character/Attack/Helper/L1DamageCalcHelper.java:53-67`**

```java
int weaponCore = aRandom + weaponBonus + enchant + undeadBonus - weaponLoss;
if (weaponCore < 0) weaponCore = 0;
```

일본식의 `max(...,1)`이 아니라 `max(...,0)`이다. 예를 들어 `1+0+0+0-5=-4`이면 일본 연구의 무기 부분은 1, RKern은 0이다.

**판단:** 무기 데미지가 손상으로 0 이하가 되는 **경계에서 1 차이**가 생긴다. `weaponCore` 하한 변경은 실제로 무기가 있는 일반 공격과 맨손/특수무기 공격을 구분해 결정해야 한다.

## 6. 재현·검증 체크리스트 (아직 미수행)

이 보고서는 **정적 소스 비교**로 아래 실측 테스트를 수행했다고 주장하지 않는다.

1. **DR 순서**: 동일 무기·스탯·버닝 상태에서 DR 0/5/10의 전후 데미지 분포를 측정; 1.5배가 DR 앞/뒤인지 판별한다.
2. **크로우 크리티컬**: 캐릭터 크리티컬만 / 크로우만 / 둘 다의 3개 조건에서 수천회 이상 비교하여 독립합집합과 합산의 로그우도·잔차를 비교한다.
3. **SOF**: 근접 검·도끼 등에서 SOF on/off시 1~최대 다이스가 실제로 최대값만 발생하는지 비교하고 활도 별도로 확인한다.
4. **퓨리 데미지**: 발동한 타격만 조건부로 추출하여 전체 공격값의 2배인지, 크래시 추가타 부분만 증폭하는지 비교한다. **발동 확률 자체는 검수 범위 밖**이다.
5. **무기 손상**: 무기 강화·손상 수치와 드롭 피격 데미지를 고정하고 무기 부분 최소 0/1을 판별한다.

## 7. 결론 및 수정 우선순위

- **우선 1 — SOF**: `isSoulOfFlameMax`의 근접/원거리 조건이 연구 내용과 반대. 명확한 코드 조건 오류 후보.
- **우선 2 — DR 순서**: 버닝 배율이 DR 앞에 있어 결과 차이가 난다. **DR의 타입별 적용 단계를 검증한 후** 최소 변경.
- **우선 3 — 퓨리 데미지 구조**: 전체 2배 vs 크래시 추가타 가산 방식 차이. **확률은 변경하지 않음**.
- **우선 4 — 크로우 크리티컬**: 독립합집합 vs 합산의 통계적 차이. 버전 맞춤 실측 뒤 선택.
- **우선 5 — 손상 무기 최소 1**: 손상 경계에서 최소 0 vs 1 차이. 무기 사용 조건을 구분한 뒤 수정.

**이 문서의 제출은 어떠한 Java 운영 소스 파일 변경도 의미하지 않는다.** 코드 수정이 필요하면 별도 작업 범위 승인 후 최신 전체 SRC를 기준으로 수행한다.

## 참고 자료

- [Phil (2015), DR 차감 및 공격 배율 단계](https://recordgame.blogspot.com/2015/09/)
- [Phil (2015), 무기 크리티컬·SOF·퓨리 연구](https://recordgame.blogspot.com/2015/09/blog-post_2.html)
- [Phil (2015), 손상 무기 최소 데미지 검증](https://recordgame.blogspot.com/2015/09/blog-post.html)

