# RKern 브로드캐스트 88차: 전체 SRC 호출 경로 재검수 / 확인된 잔존 방송 수정

기준: `RKern_NC_NativeBroadcast_Phase87_StaticMock_Regression_FULL_SRC_20261116.zip` (2026-11-16). Windows 실서버 시험이 아닌 Java 21/Linux 정적·격리 모의 검증이다.

## 직접 확인한 사실

- **실행 Java 1,710개 전체를 다시 AST 파싱**했다. 구문 오류 0개.
- 신규 AST 반복문 탐지기(`LoopInventory88`)가 `for`, enhanced `for`, `while`, `do` 안의 이름에 `send`/`broadcast` 등이 포함된 호출을 수집한 결과 **567개 반복문 후보(204개 파일)**를 확인했다. 이 수치는 86차의 491개와 **탐색 규칙이 다르므로 증감 비교 불가**다. 탐지 결과 전체를 `CURRENT_AST_SEND_LOOPS.tsv`로 남겼다.
- 567건 중 **211건에는 `CSharedBroadcast` 등 명시적 공유 송신 이름이 포함**되고, **356건은 그러한 이름이 없어 추가 구분이 필요한 후보**다. 356건은 구형 방송이 356건 남았다는 뜻이 아니며, 단일 사용자 반복 알림·수신자별 서로 다른 패킷·실제 Native 방송 래퍼 호출도 섞였다.
- 이전의 `Broadcaster`, `broadcastPacket`, `sharedWire`, `sendPackets(new S_)` 패턴 0건은 **그 네 종류의 표면적인 구형 호출만** 없다는 뜻이며, 직접 송신을 모든 방송과 통합했다는 증거가 아니다.

## 확인된 불필요한 수신자별 직렬화 수정 (6개 송신 경로, production Java 7개 변경)

1. `SkillGfxHelper.java`: 아머 브레이크의 **파티 동일 효과음**을 파티 배열 공유 프레임으로 전송. 대상 ID는 원래부터 공통의 공격 대상 ID이다.
2. `L1Trap.java`: 트랩을 인지한 수신자 전체에 동일한 위치 효과 프레임 공유.
3. `CS_ExtraCommand.java`: 오림 공격 트랩 파티원 공통 위치 이펙트 공유. 각 인원에 별도 음영·피해 판정은 없음.
4. `MJRaidValakas.java`: 메테오 등장 이펙트 공유. **효과 송신 → 개별 사망/피해 판정** 선후 관계 보존.
5. `OnEffectThread.java`: NPC 광역 효과의 동일 좌표 이펙트 공유. **각 플레이어 효과 송신 → 피격 액션 → 피해 적용**을 한 반복 안에 유지.
6. `AuraController.java` + `SC_Pledge.java`: 혈맹 버프 만료 시 혈맹 공통 상태를 **혈맹 락 아래에서 1회 snapshot** 후 공유. 멤버별 `removeSkillEffect` 순서 유지. `SC_Pledge.sendBless`의 일반 단독 송신 코드는 보존해 비동기 혈맹 버프 갱신 경로는 변경하지 않음.

각 공유 경로는 수신자의 원래 순서/중복/필터를 유지하며 새 워커·큐·스레드·기존 송신 폴백을 추가하지 않는다. `CTransport.dll`, Native C++, CIO 세션·암호화 코드는 일절 변경하지 않았다.

## 오인하면 안 되는 잔여 직접 송신 사례

- `BossController.broadcastBossEventSnapshot` → `SC_ActionUI.sendEvents`는 상점 알림 안에 **각 `pc.getId()`**가 기록되므로 모든 대상에게 동일 원본을 보내면 오작동한다.
- `CS_Craft.handleClanBuff`의 `SC_Pledge.sendBless`는 회원별 CIO 콜백 시점이 다르며 버프·월드 상태 갱신이 섞인다. 만료 tick과 달리 무조건 하나로 공유하지 않았다.
- `Hadin`, `ShipTimeController`, `MJRaidValakas`의 일부 효과음은 **플레이어 본인 오브젝트 ID**를 넣고 있어서 수신자마다 본문이 달라 직접 송신을 유지한다.
- `OrimController` 포격 좌표는 **수신자별 난수 좌표**를 생성한다. 공유 불가.
- `Queen1`의 혈맹 구호는 **수신자 혈맹명**을 조합한다. 공유 불가.
- `L1ItemDelete`의 루프 속 `SC_SystemMessage.send(pc,...)`는 **모든 플레이어 방송이 아니라 GM 본인에게 반복 보고**하는 코드다. 임의 공유 금지.
- `SC_* .broadcast()`라는 이름이 남아 있어도 그 메서드 내부가 이미 `CSharedBroadcast.sendFormatted`를 이용하는 경우는 **구형 방송이 아니다**.

따라서 **방송 기능 완전 제거/0건이나 모든 게임 동작 100% 동일함은 선언하지 않는다.** 이 보고서는 구체적으로 확인한 경로와 AST에서 발견한 광범위 후보의 구분을 기록한 것이다.

## 검증 결과

- 이전 87차 테스트를 수정된 실제 `CAsyncSendConstBuffer`, `CSharedBroadcast`, `CTLMFilter` 소스로 재실행: 5,000 모의 수신자·Native refcount·중복·AOI·FIFO·고정 seed 무작위 검사 **통과**.
- 변경 후 Java 1,710개 구문 검사: 0 errors.
- 실제 production `SC_SkillSound`, `SC_EffectLocation`, `SC_Pledge` 메서드를 네트워크 캡처 스텁과 컴파일한 뒤, *기존 직접 송신 바이트*와 *새 nativeWriter 바이트* **40,000회 비교 통과** (`tests/run_wire_test.sh`). 30,000개 효과음/좌표, 10,000개 혈맹 버프 무작위 및 극값으로 비교. 스텁은 기본 `WriteBuffer` 바이트 메서드 동작을 모사하므로 실제 IOCP 수행을 입증하지는 않는다.
- 전체 외부 JAR 타입 컴파일·실제 Windows IOCP·MySQL·5,000명 실동접은 **실행하지 않았다**.

## 재실행

```bash
bash docs/phase87_audit/run_tests.sh
bash docs/phase88_audit/tests/run_wire_test.sh
javac -d /tmp/rkern-audit88 docs/phase88_audit/tests/LoopInventory88.java
java -Xmx1500m -cp /tmp/rkern-audit88 LoopInventory88 src /tmp/current_ast_send_loops.tsv
```

확인된 수정과 감사 증거만 추가. `docs/phase88_audit/CHANGED_JAVA_FILES.tsv`에 87차 대비 변경 항목별 SHA256을 기록했다.
