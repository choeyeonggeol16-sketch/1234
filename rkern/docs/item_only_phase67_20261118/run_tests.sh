#!/bin/sh
set -eu
export LANG=C.UTF-8 LC_ALL=C.UTF-8
cd "$(dirname "$0")/../.."
python3 docs/item_only_phase67_20261118/test_phase67.py "$@"
