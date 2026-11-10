#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
cadence_build=$(mktemp -d)
trap 'rm -rf "$cadence_build"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -source 8 -target 8 -Xlint:-options -encoding MS949 -d "$cadence_build" \
    src/l1j/server/AutoHuntSystem/NativeStepSession.java tests/NativeStepCadenceTest.java
java -cp "$cadence_build" l1j.server.AutoHuntSystem.NativeStepCadenceTest
java tests/ParseJava.java src/l1j/server/AutoHuntSystem/NativeStepSession.java \
    src/l1j/server/AutoHuntSystem/AutoHuntController.java \
    src/l1j/server/CharacterAction/Walk/WalkActionHandler.java
