#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
AUDIT="$ROOT/docs/item_domain_split_20261117"
F="$ROOT/src/l1j/server/server/model/item/function"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/classes"
mapfile -t STUBS < <(find "$AUDIT/tests/stubs" -name '*.java' -print)
ACTUAL=("$F/ClassRewardPackages.java" "$F/LevelAndEquipmentPackages.java"
    "$F/StarterSupportPackages.java" "$F/RuneRewardPackages.java"
    "$F/SealedRuneLevel90Rewards.java" "$F/SealedRuneLevel85Rewards.java"
    "$F/SealedRuneLevel80Rewards.java" "$F/SealedRuneLevel70Rewards.java"
    "$F/SealedRuneLevel55Rewards.java"
    "$F/EquipmentStarterPackages.java" "$F/LevelMilestonePackages.java"
    "$F/PackageRewardStaging.java" "$F/QuestDialogueItems.java" \
    "$F/SpecialEnchantmentItems.java" "$F/DurableBuffScrollItems.java")
javac -encoding UTF-8 -d "$TMP/classes" "${STUBS[@]}" "${ACTUAL[@]}"   "$AUDIT/tests/TestPackage.java" "$AUDIT/tests/TestDialogue.java" "$AUDIT/tests/TestScrolls.java"
java -cp "$TMP/classes" TestPackage > "$TMP/package.out" 2> "$TMP/package.err"
ACTUAL_HASH="$(sha256sum "$TMP/package.out" | cut -d' ' -f1)"
EXPECTED_HASH="$(cat "$AUDIT/ORIGINAL_PACKAGE_OUTPUT_SHA256.txt")"
if [[ "$ACTUAL_HASH" != "$EXPECTED_HASH" ]]; then
  echo 'FAIL package rewards changed from original'; exit 1;
fi
echo 'PASS package original/new exact event stream for 1755 inputs'
java -cp "$TMP/classes" l1j.server.server.model.item.function.TestDialogue "$AUDIT/QUEST_DIALOGUE_ORIGINAL.tsv"
java -cp "$TMP/classes" l1j.server.server.model.item.function.TestScrolls
python3 "$AUDIT/tests/test_structure.py"
javac -encoding UTF-8 -d "$TMP/classes" "$AUDIT/tests/stubs/l1j/server/MJINNSystem/MJINNHelper.java"     "$ROOT/src/l1j/server/server/utils/SupplementalItemIds.java"
javac -encoding UTF-8 -d "$TMP/classes" "$ROOT/docs/item_function_refactor_20261117/tests/ParseAllJava.java"
java -Xmx3g -cp "$TMP/classes" ParseAllJava "$ROOT/src"
echo 'PASS item domain split regression'
