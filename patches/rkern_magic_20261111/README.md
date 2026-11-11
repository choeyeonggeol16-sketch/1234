# Aden 마법중첩·2016 마법데미지 구현 — 2026-11-11

[**네트워크·스레드풀 수정 포함 전체 src**](../../downloads/RKern_Aden_IOCP_THREADPOOL_MAGIC_FULL_SRC_20261111.zip) · [SHA-256](../../downloads/RKern_Aden_IOCP_THREADPOOL_MAGIC_FULL_SRC_20261111.zip.sha256) · [교체 파일](changed-files.txt) · [diff](magic.patch) · [검증 로그](logs)

기준 소스는 `1b8cb09`의 최신 Aden 통합본이며, GitHub main `ae8a7d6`의 아래 보고서 세 개를 참고했습니다.

- [마법중첩 명세](../../reports/LINEAGE_MAGIC_STACKING_PC_RECEIVE_DAMAGE_20261111.md)
- [2016 공식·속성저항 구현 명세](../../reports/LINEAGE_JP_MAGIC_DAMAGE_2016_FORMULA_IMPLEMENTATION_20261111.md)
- [원 연구 실측표 검증 보고서](../../reports/LINEAGE_JP_MAGIC_DAMAGE_VALIDATION_20261111.md)

전체 ZIP에는 **src/만** 있습니다. 소스 1,696개(Java 1,695개), 기존 Java 5개 변경·계산 보조 클래스 1개 추가입니다. 이전 네트워크·스레드풀 수정은 모두 유지합니다.

## 마법중첩 적용

- **PC→PC / NPC→PC**, 피격자 한 명이 공유하는 고정 2초 윈도우입니다. 첫 타격 100%, 이후 `(2/3)^(타격수-1)`을 적용합니다. 300 피해라면 정수 HP 적용은 300→200→133→88→59…입니다.
- 첫 타격 기준 2,000ms 미만은 같은 윈도우, 정확히 2,000ms 이상은 100% 새 타격입니다. 후속 타격마다 시간을 미루지 않습니다. 단조 시계 `System.nanoTime()`을 사용합니다.
- `L1PcInstance.receiveDamage(attacker, damage, boolean applyMagicStack)`가 적용 여부를 명시합니다. 기존 2인자와 속성(int) 오버로드는 기본 false입니다. 컨트롤러의 기존 세 번째 boolean은 **반격 여부**이므로 의미를 바꾸지 않고 별도 인자·실행 종류를 추가했습니다.
- 중첩 상태는 실제 피격 PC의 기존 `CCreatureController`에서 HP 처리와 함께 순서대로 갱신합니다. 기존 FIFO/포화 시 overflow 경로 모두 새 구분을 보존합니다. 새 큐·워커·스레드·락은 추가하지 않았습니다.
- **무기 발동 마법·키링크·물리·반사·반격은 중첩 횟수를 올리지 않고 감쇄하지 않습니다.** 기존 무기 마법 계산도 유지합니다. 일반 스킬 처리기만 보통 마법 출처를 지정하며 무기의 `TYPE_GMBUFF` 호출은 제외합니다. `MIND_BREAK`도 명세대로 제외합니다. 피해로 변환된 힐은 일반 마법 계산을 타지 않으면 제외합니다.
- 일반 PC/NPC의 공격 마법, 범위 마법의 개별 피격, FIRE_WALL의 PC 피해는 적용합니다. FIRE_WALL의 PC 분기가 NPC용 계산기를 호출하던 오류도 PC용 계산기로 수정했습니다. 독/출혈 등 일반 지속 피해는 제외합니다.
- false/0/음수/무적·순간이동으로 막힌 적격 피해는 중첩 상태를 변경하지 않습니다. 죽은 PC의 무효 피격도 카운터를 올리지 않습니다. 접속 세대·사망 세대가 바뀐 다음 적격 피격은 새 윈도우로 처리합니다.
- 기존 디스인티그레이트의 `NO_DIS` 감쇄 및 `ANTI_DISINTEGRATE` 3초 차단을 피해 계산에서 제거했습니다. 새 일반 중첩과 중복되지 않습니다.
- 감쇄는 기존 피해 감소 처리 후, 실제 HP 적용 전의 양수 피해에 **한 번** 적용합니다. 감쇄 후에 HP/SOUL_BARRIER 상한을 적용하여 과잉 피해를 먼저 잘라 줄이는 오류를 피합니다.

**적용 단계의 선택:** 이번 구현은 receiveDamage 계약에 맞춰 **최종 피해 감쇄**를 선택했습니다. Kiyoshi 원본의 크리티컬·추가피해 전 중간단계 감쇄와 정수 결과가 항상 같다는 뜻은 아닙니다. 따라서 크리티컬·MD·DR 처리까지 Kiyoshi와 완전히 동일하다고 주장하지 않습니다.

## 마법 데미지 공식 연결

일반 공격 마법이 **PC를 대상으로 하고, 방어자 8직업과 WIS 계수가 확인되며 `MR-MH >= 10`**일 때 보고서의 후보식을 사용합니다.

```text
A = floor_0.01(1 + 3/32*(INT+SP-MB-ML-12) - ER/100)
C = defenderClass - 0.02*floor(defenderWIS/2)
B = floor_0.01(C*(MR-MH)^(-0.3))
base = floor(D*A)
critical이면 base = floor(base*1.5)
result = floor(base*B) + MD
이후 기존 PvP/스킬/장비 DR 및 후처리 → 피격자 중첩 → HP
```

- INT/SP는 공격자의 `Ability`를 사용합니다. 실제 `getSp()`가 MB+ML을 포함하므로 `getTrueSp()`를 **한 번만** 뺍니다. 클래스·WIS·MR·원소 저항은 방어자 PC에서 읽습니다.
- PC MH는 클래스/장비/기본 적중 합계인 `getTotalMagicHitup()`에 별도 스킬 적중 `getMagicHit()`을 더합니다. NPC는 자신의 `getMagicHit()`을 사용합니다. 분기 시 클래스/WIS/유효 MR은 한 번 저장하여 중간 버프 변경으로 검증 범위를 벗어나는 것을 막습니다.
- D는 기존 서버의 타격별 주사위를 한 번 굴린 값입니다. 기존 SP에 따른 주사위 면수·고정 가산 보정도 유지하며 평균값으로 바꾸지 않습니다. 연구 시트의 스킬별 숨은 다이스와 현재 스킬 DB가 동일하다는 의미는 아닙니다.
- ER은 해당 속성 하나만 읽어 -100~100으로 제한합니다. 무속성은 0이며 A에서 차감합니다. 새 분기는 기존 `PowerMr`/속성 감소와 `calcMrDefense`를 다시 적용하지 않습니다.
- MD는 기존 클래스/레벨 포함 `getBaseMagicDmg()`와 무기 `MagicDmgModifier`이며 MR 뒤에 한 번 더합니다. DR은 기존 `calcPcMagicDamage` 및 피격 측 감소 경로가 처리하므로 계산 보조 함수 호출에 중복 입력하지 않습니다. 기존 DIS 성향 보정은 치명타 뒤·MR 전 별도 스킬 가산으로 유지합니다.
- 기존 PC 치명타 확률·대상 분기는 유지합니다. 새 공식의 크리티컬 승수는 1.5입니다. NPC의 기존 15% 추첨도 유지하되 새 공식에서는 1.5를 적용하고, 기존 분기에는 원래 1.4를 유지합니다.
- `MR-MH < 10`, 미확인 직업/비정상 WIS 계수, NPC 대상, 무기 발동 효과, CONFUSION/MIND_BREAK/MAGMA_BREATH는 기존 계산을 유지합니다. 저MR을 10으로 강제 올리지 않습니다. FIRE_WALL의 고정 피해도 기존 계산입니다.
- A와 중간 피해는 100배 정수로 계산합니다. B는 부동소수 오차로 0.01 경계가 틀어지지 않도록 `k^10 * MR^3 <= C100^10`을 primitive 128비트 비교로 확인합니다. 정적 primitive 거듭제곱 표만 한 번 만들며 타격마다 BigDecimal/BigInteger를 만들지 않습니다. 음수 계수/최종 피해는 0, 최종 int 초과는 포화 처리합니다.

2015 속성식과 2016 MR식을 결합한 **연구 후보식의 구현**입니다. 2016 보고서의 98.75% 수치는 원 연구 데이터 재현율이며, 이번 서버나 현재 본서버와의 일치율이 아닙니다. 기존 `ALT_ATKMSG` 공격 측 메시지는 HP 적용 전 계산값이므로 중첩 후 실제 HP 감소 검증에는 피격 PC의 HP를 기준으로 해야 합니다.

## 적용·검증

기존 프로젝트를 백업하고 ZIP의 `src/` 전체를 반영한 뒤 기존 라이브러리·설정으로 재빌드하세요. **UTF-8 / Java 21 및 기존 FFM preview/native 실행 옵션**을 유지합니다. DLL·SQL·게임 데이터·운영 설정 변경은 없습니다. 이전 스레드풀 통합본과 정확히 일치한다면 이 폴더의 src 6개만 반영할 수도 있습니다.

- Java **1,695개 전체 타입 컴파일 성공**, 오류 0 / 기존 제거 예정 API 경고 5개. 사용한 외부 라이브러리 버전과 SHA는 `logs/build-dependencies.json`에 있습니다.
- 보고서 일반·크리티컬/속성저항 예시와 MD/DR 0·7·8 예시 통과. A는 BigDecimal 기준 123,904개, B는 BigInteger 기준 24,000개 경계/직업/WIS 및 10,000개 임의 입력을 대조했습니다.
- 실제 PC/컨트롤러/HP 코드에서 무기·false 제외, PC/NPC 공유, 0/무효 피해, 1999/2000ms, 비슬라이딩 윈도우, 접속/사망 세대, 과잉 피해 후 상한과 단일 사망 전이를 확인했습니다.
- 실제 컨트롤러 포화·overflow의 마법/비마법 혼합 161개, 8개 동시 발신 스레드의 마법 400개에서 피해·카운터 누락/중복 없이 처리했습니다. 오래된 접속 세대의 대기 피해 차단도 통과했습니다.
- 테스트 PC의 월드 가시성·안전지대·버프 상태·사망 부수효과는 대역이며 NPC 공격자는 테스트용 객체입니다. **Windows IOCP 실기동·실DB·클라이언트 전투 및 운영 성능은 미검증**입니다.
- ZIP CRC와 모든 바이트, 이전 통합본에 diff 적용 후 전 파일 일치를 확인했습니다.

재현(Java 21, Python 3):

```sh
python3 tests/test_magic.py
# 추출한 소스와 보유 라이브러리 사용:
python3 tests/test_magic.py --src /path/to/src --libs /path/to/lib
```

기본 실행은 저장소의 새 전체 ZIP과 Maven Central의 명시된 공개 빌드 라이브러리를 사용합니다. 테스트는 게임/운영 DB에 접속하지 않습니다.
