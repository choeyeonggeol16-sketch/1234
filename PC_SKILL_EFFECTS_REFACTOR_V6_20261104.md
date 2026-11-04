# PC 스킬 효과 분리 V6

기준: GitHub main `95d0548`의 V5 전체 소스. 사용자가 붙여준 PC 스킬 코드와 같은 구간의 효과 64개를 정리했다.

## 구조

`L1PcInstance`의 기존 공개 메서드는 한 줄 위임으로 남겼다. 기존 호출자는 변경 없이 전용 효과 모듈을 사용한다.

| 모듈 | 담당 |
| --- | --- |
| `PcPassiveEffects` | 패시브 23종의 능력치·파티 갱신·아이콘·안내 |
| `PcPotentialEffects` | 잠재력 31종의 능력치 변경과 공통 버프 패킷 |
| `PcClanBuffEffects` | 혈맹 버프 8종 |
| `PcWantedEffects` | 수배 버프 2종과 기존 on/off 배율 |

모듈 경로: `src/l1j/server/server/model/character/effect/`.

예를 들어 PC 클래스의 `hp300(boolean isOn)`에는 다음 연결만 남는다.

```java
public void hp300(boolean isOn) {
    PcPotentialEffects.apply(this, PcPotentialEffects.Kind.hp300);
}
```

잠재력마다 반복되던 패킷 지역 변수·존재 비트·전송 호출을 공통화했다. 프로필에는 스킬 번호와 툴팁 번호를 보관하고, 효과 적용 → 버프 패킷 → 스킬 상태 등록의 기존 순서를 유지한다.
툴팁 번호의 5045→5051 간격, 4710 아이콘, RESTAT 종류, 존재 비트 및 생략 필드도 원본 그대로다.

`기간틱`·`프라이드`의 정수 나눗셈 후 HP 계산, `어드밴스`의 HP/MP와 파티 갱신, `세인트`·`레이징포스`의 레벨별 안내 순서를 바꾸지 않았다.
원래 `isOn`을 무시하던 메서드에 임의의 해제 동작이나 중복 적용 방지 정책을 추가하지 않았다. 실제로 플래그를 사용하던 수배 두 메서드는 그대로 전달한다.

PC 클래스는 **9,922줄 → 8,825줄**, 새 모듈 4개는 합계 456줄이다. PC 밖으로만 이동한 부분과 공통화로 줄어든 부분을 구분한 수치다.

## 객체와 메모리

전용 모듈은 정적 함수와 고정 프로필로 구성했다. 캐릭터마다 매니저나 컨텍스트를 추가하지 않는다. 프로필에는 정수만 보관하며 플레이어·아이템 참조는 없다.
PC 필드 구조는 그대로다. 메서드 코드는 캐릭터마다 복사되는 힙 메모리가 아니므로, 이번 줄 수 감소를 캐릭터당 힙 절감량으로 해석하면 안 된다. 운영 힙·CPU·전체 서버 메모리 절감률은 측정하지 않았다.

## 검증

- 전체 **Java 1,707개 컴파일 통과**. 기존 removal 경고 5개.
- 원래 PC 메서드와 분리된 구현 **31,744개 상황 비교 통과**. 64종 모두 포함하고, 레벨 0~120 및 추가 경계, 홀수 레벨의 정수 나눗셈, 파티 여부, true/false, 반복 호출, 연결 종료 상태를 비교했다.
- 능력치 변화, HP/MP, 스킬 상태, 파티 갱신, 안내/아이콘 순서를 비교했다. 잠재력 **15,004개 상황에서는 실제 운영용 protobuf 송신·직렬화 코드로 패킷 바이트까지 비교**했다. 테스트 전송기는 메모리 프레임으로 기록한다.
- 독립 Java 구문 트리 검사로 공개 메서드 64개의 서명과 정확한 한 줄 연결을 확인했다. PC 필드 532개(정적 포함)와 나머지 멤버 1,049개는 동일하다.
- V5의 다른 Java **1,702개는 바이트 단위 동일**. 수정은 `L1PcInstance.java` 하나, 신규 효과 모듈 4개, 삭제 없음. 해시는 `validation/source_manifest.json`에 있다.

게임 상태·파티·일부 패킷 경계는 테스트 대체 객체다. 운영 DB, Windows IOCP, 실클라이언트의 전체 스킬 사용 검증은 별도다.

## 적용

- FULL_SRC는 V5까지 포함한 전체 소스다.
- PATCH는 **V5 `95d0548` 기준 Java 5개를 모두** 적용한다. PC 파일만 교체하면 신규 모듈을 찾을 수 없다.
- 기존 lib·data·config·DB를 보존하고 전체 Clean/Rebuild 후 재시작한다. 추가 DB·설정·운영 라이브러리는 없다.
- 기존 JDK 21 / UTF-8 / `--enable-preview` 조건을 유지한다.

재검증 시 `ITEM_DEPS_DIR`은 jar 디렉터리, `ITEM_BUILD_CLASSES`는 빌드 classes 디렉터리다.

```sh
python3 tools/build_item_refactor.py --dependencies <lib> --output build/item-processing
python3 tests/pc_skill_effects/test_pc_effects.py
java tests/pc_skill_effects/PcEffectSourceAudit.java <V5 전체 소스> .
```
