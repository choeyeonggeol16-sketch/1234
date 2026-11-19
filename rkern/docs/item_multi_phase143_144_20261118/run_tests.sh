#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
python3 "$ROOT/docs/item_multi_phase143_144_20261118/generate_typed.py" "$ROOT" "$WORK/typed"
mkdir -p "$WORK/classes"
find "$WORK/typed" -name '*.java' -print0 | xargs -0 javac --release 21 -encoding UTF-8 -d "$WORK/classes"
# Expected failure-injection logging (packet/dirty save/class lookup) is captured.
java -cp "$WORK/classes" l1j.server.server.model.item.function.Regression \
    2> "$WORK/expected_failure_injection.log"
python3 - "$ROOT" <<'PY'
import sys
from pathlib import Path
src=Path(sys.argv[1])/'src/l1j/server/server/model/item/function'
s=(src/'ItemActionDispatcher.java').read_text('utf-8')
for item_id,target in [(410014,60001),(410015,70030)]:
    assert f'case {item_id} -> {{ useNpcSpownWand(pc, {target}, item); yield ItemActionResult.STOP; }}' in s
assert 'return ItemActionResult.STOP;' in (src/'PetAndSummonItems.java').read_text('utf-8')
print('DISPATCH_STOP_GATES=3 PASS')
PY
