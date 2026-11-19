#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
HERE="$(cd "$(dirname "$0")" && pwd)"
python3 "$HERE/test_domain_preservation.py"
bash "$HERE/../item_remaining_domain_split_20261117/run_tests.sh"
echo 'PASS item domain continuation + original item/broadcast regressions'
