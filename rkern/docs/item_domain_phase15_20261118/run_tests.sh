#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$ROOT/docs/item_domain_phase15_20261118/test_phase15.py"
python3 "$ROOT/docs/item_domain_phase15_20261118/test_phase15_typed.py"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac --release 21 -d "$TMP" "$ROOT/docs/item_domain_phase15_20261118/JavaSyntaxScan15.java"
java -Xmx1200m -cp "$TMP" JavaSyntaxScan15 "$ROOT/src"
# Run independent historical domain suites. The old 9th phase's static hash
# assertion expects the pre-15th dispatcher and therefore is not chained here.
for version in 14 13 12 10; do
  python3 "$ROOT/docs/item_domain_phase${version}_20261118/test_phase${version}.py"
done

python3 "$ROOT/docs/item_domain_phase11_20261118/test_weapon_compile.py"
bash "$ROOT/docs/phase114_broadcast_only/run_tests.sh"
