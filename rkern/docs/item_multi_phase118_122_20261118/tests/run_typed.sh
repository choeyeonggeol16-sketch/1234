#!/usr/bin/env bash
set -euo pipefail
DOC="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cd "$DOC/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cp -a "$DOC/tests/typed/." "$TMP/"
for name in MovementPotionConsumables ManaProtectionPotionConsumables MovementPotionEffects ManaPotionEffects ProtectivePotionEffects TransformationScrollItems ItemActionCompletion; do
  cp "$ROOT/src/l1j/server/server/model/item/function/$name.java" "$TMP/l1j/server/server/model/item/function/$name.java"
done
(
  cd "$TMP"
  find l1j -name '*.java' > allfiles.txt
  javac --release 21 -encoding UTF-8 -d classes @allfiles.txt
  java -cp classes l1j.server.server.model.item.function.TestDurablePotions
  java -cp classes l1j.server.server.model.item.function.TestPolyReceipts
)
