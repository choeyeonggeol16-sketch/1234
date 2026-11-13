# MJBin (2019) 구버전 클라이언트 기능 이식 후보 — 추가 역분석

대상은 사용자 제공 **MJ 프리서버의** `mjlin.bin` 및 복호화 메모리 이미지(`mjlin_loaded_code_00400000_0180D000.bin`)다. 정식 NC 본서버 클라이언트라고 단정하지 않는다. 주소는 원본 이미지 베이스 0x00400000에 따른 x86 VA이다. **구버전 DLL 실제 실행 테스트는 아직 하지 않았다.**

## 1. 한 번 클릭 공격 유지 — 실행 코드 확인

- `ClientConfig.attackContinueByOneClick = 55` (bool, 기본 false): 실제 복원한 `ClientConfig.proto`의 필드.
- UI 액션 이름 `Action_AttackContinueByOneClick`, 문자열 VA `0x01258E54`.
- 액션 등록: `0x00C02517`에서 콜백 `0x00C02C20`와 액션 문자열을 등록.
- 콜백 `0x00C02C20`에서 상태 `0x015B395A`를 등록/반전한다.

```asm
0x00C02C23 mov eax, [ebp+8]
0x00C02C26 cmp dword ptr [eax+0x50],0
0x00C02C2C mov dword ptr [eax+0x50],0x015B395A
0x00C02C35 cmp byte ptr [0x015B395A],0
0x00C02C3C sete byte ptr [0x015B395A]
```

설정 플래그는 다음 전투/입력 관련 조건에서도 읽힌다.

```asm
0x00ECE330 cmp byte ptr [0x015B395A],1
0x00ECE337 jne 0x00ECE33C
0x00ECE339 xor al,al
0x00ECE33B ret

0x00ECE670 cmp byte ptr [0x015B395A],0
0x00ECE677 jne 0x00ECE67C
0x00ECE679 xor al,al
0x00ECE67B ret
```

두 함수는 플래그에 따라 **상반된 조건 경로**를 선택하며, 이후 각각 다른 게임 상태·대상 조건을 확인한다. 설정 로드 `0x0085BDE6`, 저장 `0x0085DBCC`, 상태 직렬화/복원 `0x00B24DBE`, `0x00B27279`에서도 같은 주소가 사용된다. 앞서 찾은 **Ctrl 두 번 자동공격 상태 `0x015B38BD/BE`와 다른 변수**다.

**확정:** 설정·액션 버튼·상태 토글·다른 실행 조건의 조회. **미확정:** 클릭에서 실제 공격 패킷의 연속 재발행과 중지까지 이어지는 전체 호출 흐름.

## 2. 자동 인형 소환 — 토글 코드 확인

- `ClientConfig.autoSummonMagicDoll = 54` (bool, 기본 false).
- RTTI `AutoSummonMagicDoll@Lineage`.
- `Action_AutoSummonMagicDoll` 등록 콜백 **`0x00C02CA0`**. 내부에서 `0x00847790` 상태를 읽고 반전해 `0x00847650`으로 기록하는 분기가 있다.
- 세부 재소환 트리거·재료 검사·지연은 추가 분석 필요.

## 3. 자동 포우 슬레이어 설정 — 0~4 순환 확인

- `ClientConfig.autoFoeSlayerLevel = 56` (int32, 기본 0).
- `Action_AutoFoeSlayer` 등록 콜백 **`0x00C02C50`**.
- 전역 `0x015B395C`를 증가시키다가 4 초과 시 0으로 순환한다. 별도 상태 `0x015B395B`에 활성 여부 저장.
- 각 단계의 실제 자동 스킬 시전 조건은 아직 미확인.

## 4. 추가로 가져올 만한 시스템

| 기능 | 실제 확보한 근거 | 구버전 포팅 가치 | 현재 경계 |
|---|---|---|---|
| 스마트 스킬 | `SmartCastSpell`, `SmartCastSpellEventHandler`, `SetSmartCastTargetEvent` RTTI | 대상 지정·시전 UX | 클래스 확인, 소비자 미추적 |
| 펫/컴패니언 자동 물약 | `CompanionPotionAutoUseSystem/Event` RTTI, `ClientConfig.petAutoPotionNameId = 69` | 자동 회복 | 조건·전송 경로 미추적 |
| 장비 세트 교체 | `Action_SwapEquipSet` 콜백 `0x00C03D30` → `0x007B8B60` | 장비 프리셋 | 함수 진입 확인 |
| 물약 헬퍼 | `Action_PotionHelper`, `ClientConfig.LinHelper.hpPotionNameId/mpPotionNameId` | 물약 UI/선택 | 자동사용 정책 미확인 |
| 몬스터 외곽선 | `Action_MonsterOutline` 콜백 `0x00C035F0`; `showTargetMonsterOutline = 61` | 타겟 강조 | 액션 등록 확인 |
| 상태이상 시간 | `StatusEffectDuration*`, `StatusEffectTooltip*` RTTI | 버프·디버프 UI | 클래스 확인 |
| 조건별 퀵모션 UI | `QuickMotionSystem/Runner`, `QuickMotionInventoryCondition`, `QuickMotionZoneCondition` RTTI | 조건 반응 UI | 클래스 확인 |
| 경로 기록 | `PathRecorder`, `CommandBotPathRecord` RTTI, `PSS.PathRecord.pathlist` | 자동사냥 길 기록 | 타입·스키마 확인 |
| 기타 설정 | `fishingAutoQuit = 64`, `spamAutoBlock = 59`, `autoUseDragonDiamond = 57` | 편의 옵션 | 설정 필드 확인 |

## 5. 이식 권장 순서

**① 한 번 클릭 공격 유지 → ② 인형 자동소환/펫 물약 → ③ 스마트 스킬 시전 → ④ 장비 교체 → ⑤ 타겟·버프 UI.**

구버전은 2019년 바이너리와 객체 구조·주소·패킷 계약이 다를 수 있다. 함수를 기계어로 단순 복사하지 말고, 구버전의 입력/타겟/공격·스킬/아이템 함수 대응을 확보한 뒤 32비트 DLL과 연결한다. 이 목록은 **분석·설계 후보이지 구현·실행 검증 완료 목록이 아니다.**

관련 자료: [Ctrl 2회 자동공격 보고서](CTRL_DOUBLE_AUTOATTACK_20261113.md), [구버전 DLL 통합 보고서](LEGACY_CLIENT_CTRL_DOUBLE_AUTOATTACK_DLL_FULL_REPORT_20261113.md), [PSS 전체 분석](REVERSE_ENGINEERING_20261113.md).
