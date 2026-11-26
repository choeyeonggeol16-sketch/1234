# 아이템 옵션 편집

서버 실행 폴더에 `script` 폴더를 만들고 수정할 정의 파일을 넣은 뒤 서버를 재시작하면 적용됩니다. 기본 파일은 소스의 `rkern/src/script`에 있습니다. 각 파일은 UTF-8로 저장합니다. 탭으로 구분된 파일에서는 기존 행의 탭을 유지합니다.

| 파일 | 수정할 값 |
| --- | --- |
| `equipment-base.txt` | 무기·방어구 기본 능력치, 타격치, 무게 감소, 마법명 |
| `equipment-options.txt` | 아이템 ID별 추가 능력치, 강화 구간, 장신구 보너스, 특수 속성 |
| `equipment-registered.txt` | 귀걸이·견갑·문장·티셔츠 등의 직업·강화별 등록 효과 |
| `equipment-blessing.txt` | 방어구 축복 단계별 효과 |
| `doll-options.txt` | 인형 기본 효과, 자동 회복량, 명중 시 발동 종류 |
| `doll-potential.txt` | 인형 잠재력 효과와 툴팁 |
| `cooking-options.txt` | 요리 효과, 지속 시간(초), 아이콘, 중복 적용 그룹 |

`equipment-base.txt`에 등록된 ID의 기본 옵션은 이 파일의 값이 우선합니다. 등록된 행에서 생략한 숫자 필드는 0입니다. 파일에 없는 ID의 기본값과 모든 아이템의 이름·외형·장착 조건·아이템 분류는 기존 DB에서 읽습니다.

`equipment-options.txt`에서 `MR:4:2`는 마법 방어력 4에 인챈트 수치당 2를 더한다는 뜻입니다. 인형과 요리의 수치는 각 파일의 해당 아이템 또는 프로필 아래에서 바꿀 수 있습니다. `doll-potential.txt`의 `{VALUE}`는 실제 적용되는 효과값으로 표시됩니다. 인형의 명중 시 발동 확률과 피해 범위는 기존 `config/MagicDollProc.properties`를 사용합니다.
