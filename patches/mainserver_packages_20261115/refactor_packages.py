#!/usr/bin/env python3
"""Move existing MainServer Java packets into schema-grouped packages.

Input is the verified full-source 2026-11-14 ZIP. No packet body, wire ID,
CIO/IOCP/CTransport implementation, or unrelated game code is rewritten.
"""
from __future__ import annotations
from collections import Counter
from pathlib import Path, PurePosixPath
import argparse
import csv
import hashlib
import json
import re
import sys
import zipfile

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
BASE = "l1j.server.server.packets.Protocol"
OLD = BASE + ".Packet"
NEW = BASE + ".MainServer"
OLD_PATH = "src/" + OLD.replace(".", "/") + "/"
FINAL = "CTransport_MainServer_Packages_FULL_SRC_20261115.zip"
FEATURE_BY_SCHEMA = {
    "MainServer_Client_Indun.proto": "Indun",
    "MainServer_Client_Inventory.proto": "Inventory",
    "MainServer_Client_Companion.proto": "Companion",
    "MainServer_Client_PlaySupport.proto": "PlaySupport",
    "MainServer_Client_ArenaCo.proto": "ArenaCo",
    "MainServer_Client_Pledge.proto": "Pledge",
    "MainServer_Client_WeaponMastery.proto": "WeaponMastery",
    "MainServer_Client_InterRacing.proto": "InterRacing",
    "MainServer_Client_GameGate.proto": "GameGate",
    "MainServer_Client_BuilderCommand.proto": "BuilderCommand",
}
HELPER_FEATURE = {"Inventory": "Inventory", "Companion": "Companion"}
SUBPACKAGES = {"Attend", "Craft", "MonsterBook", "Ranker"}
sha = lambda b: hashlib.sha256(b).hexdigest()


def read_source(zip_path: Path):
    with zipfile.ZipFile(zip_path) as z:
        error = z.testzip()
        if error:
            raise ValueError("Invalid ZIP CRC: " + error)
        files = {i.filename: z.read(i) for i in z.infolist() if not i.is_dir()}
    manifest = json.loads((REPO / "patches/mainserver_protocol_20261114/MANIFEST.json").read_text())
    actual = {p: sha(b) for p, b in files.items() if p.startswith("src/")}
    if actual != manifest["output_files"]:
        differences = sorted(set(actual) ^ set(manifest["output_files"]) |
                             {p for p in actual.keys() & manifest["output_files"].keys()
                              if actual[p] != manifest["output_files"][p]})
        raise ValueError("Input is not exact 2026-11-14 latest SRC: " +
                         ", ".join(differences[:12]) + " (total " + str(len(differences)) + ")")
    return files, manifest


def build_moves(files):
    with (REPO / "patches/mainserver_protocol_20261114/class-names.csv").open(
            newline="", encoding="utf-8-sig") as f:
        class_schema = {r["new"]: r["schema"] for r in csv.DictReader(f)}
    names = {}
    moves = {}
    counts = Counter()
    seen_types = {}
    for path in sorted(files):
        if not path.endswith(".java") or not path.startswith(OLD_PATH):
            continue
        rel = path[len(OLD_PATH):]
        dirs = rel.split("/")
        name = PurePosixPath(path).stem
        old_pkg = OLD + ("." + ".".join(dirs[:-1]) if len(dirs) > 1 else "")
        if len(dirs) == 1:
            if name == "GludioDungeonPackets":
                feature = "Indun"
            else:
                feature = FEATURE_BY_SCHEMA.get(class_schema.get(name, ""), "")
            new_pkg = NEW + ("." + feature if feature else "") + ".Proto"
        elif dirs[0] in HELPER_FEATURE:
            new_pkg = NEW + "." + HELPER_FEATURE[dirs[0]] + ".Proto"
            if len(dirs) > 2:
                new_pkg += "." + ".".join(dirs[1:-1])
        elif dirs[0] in SUBPACKAGES:
            new_pkg = NEW + ".Proto." + ".".join(dirs[:-1])
        else:
            raise ValueError("Unclassified helper package: " + path)
        new_path = "src/" + new_pkg.replace(".", "/") + "/" + name + ".java"
        old_type = old_pkg + "." + name
        new_type = new_pkg + "." + name
        if new_type in seen_types:
            raise ValueError("Class collision: " + new_type + " and " + seen_types[new_type])
        if new_path in files or new_path in moves.values():
            raise ValueError("Target path already exists: " + new_path)
        seen_types[new_type] = path
        names[old_type] = new_type
        moves[path] = new_path
        counts[new_pkg] += 1
    if len(moves) < 90:
        raise ValueError("Unexpectedly small MainServer migration: " + str(len(moves)))
    return names, moves, counts


def transcode(files, names, moves):
    # Exact FQCN replacements, including explicit/static imports and reflective strings.
    # Longest first avoids an FQCN matching the prefix of another class.
    full = re.compile(r"(?<![\w.])(?:" +
                      "|".join(re.escape(s) for s in sorted(names, key=len, reverse=True)) +
                      r")(?![\w])")
    new_files = {}
    edited = []
    auto_imported = 0
    changed_types = {old.split(".")[-1]: (old, new) for old, new in names.items()}
    if len(changed_types) != len(names):
        raise ValueError("Nonunique simple class names; review import resolution")
    for path, data in sorted(files.items()):
        if not path.endswith(".java"):
            new_files[path] = data
            continue
        text = data.decode("utf-8")
        package_match = re.search(r"(?m)^(\s*package\s+)([\w.]+)(\s*;)", text)
        if not package_match:
            new_files[path] = data
            continue
        old_pkg = package_match.group(2)
        new_path = moves.get(path, path)
        new_pkg = old_pkg
        if path in moves:
            new_pkg = ".".join(PurePosixPath(new_path).parts[1:-1])
        output = full.sub(lambda m: names[m.group()], text)
        if path in moves:
            output = re.sub(r"(?m)^(\s*package\s+)[\w.]+(\s*;)",
                            lambda m: m.group(1) + new_pkg + m.group(2), output, count=1)
        # A packet that used to share Packet's package may refer to another
        # packet without an import. Rebuild only those imports made necessary
        # by the migration, never rely on wildcard imports.
        imports = []
        if old_pkg == OLD:
            without_imports = re.sub(r"(?m)^\s*import\s+(?:static\s+)?[\w.*]+\s*;[^\n]*", "", output)
            for simple, (old_type, new_type) in changed_types.items():
                if old_type.rsplit(".", 1)[0] != OLD or new_type.rsplit(".", 1)[0] == new_pkg:
                    continue
                if path.endswith("/" + simple + ".java"):
                    continue
                if re.search(r"(?<![\w.])" + re.escape(simple) + r"\b", without_imports):
                    directive = "import " + new_type + ";"
                    if directive not in output:
                        imports.append(directive)
        if imports:
            pos = re.search(r"(?m)^\s*package\s+[\w.]+\s*;", output)
            if not pos:
                raise ValueError("Cannot insert imports: " + path)
            output = output[:pos.end()] + "\n\n" + "\n".join(sorted(set(imports))) + output[pos.end():]
            auto_imported += len(set(imports))
        result = output.encode("utf-8")
        if result != data or new_path != path:
            edited.append(new_path)
        if new_path in new_files:
            raise ValueError("Overwrite: " + new_path)
        new_files[new_path] = result
    if len(new_files) != len(files):
        raise ValueError("Source count changed")
    for old in moves:
        if old in new_files:
            raise ValueError("Old Java file retained: " + old)
    return new_files, edited, auto_imported


def archive(new_files, dest):
    dest.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(dest, "w", compression=zipfile.ZIP_DEFLATED,
                         compresslevel=6, allowZip64=True) as z:
        for path, data in sorted(new_files.items()):
            z.writestr(path, data)
    with zipfile.ZipFile(dest) as z:
        assert z.testzip() is None
        assert len([x for x in z.namelist() if x.endswith(".java")]) == 1711
    return sha(dest.read_bytes())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", type=Path, default=REPO /
                    "downloads/CTransport_MainServer_Protocol_FULL_SRC_20261114.zip")
    ap.add_argument("--output", type=Path, default=REPO / "downloads" / FINAL)
    ap.add_argument("--work", type=Path, default=Path("/tmp/mainserver-package-work"))
    args = ap.parse_args()
    files, manifest = read_source(args.input)
    names, moves, counts = build_moves(files)
    new_files, edited, imports = transcode(files, names, moves)
    # The source outside changed Java files is carried over byte-identically.
    for path, data in files.items():
        if not path.endswith(".java"):
            assert new_files[path] == data, path
    root = args.work
    root.mkdir(parents=True, exist_ok=True)
    src_root = root / "src"
    for path, data in sorted(new_files.items()):
        if not path.startswith("src/"):
            continue
        out = root / path
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(data)
    checksum = archive(new_files, args.output)
    report = {
        "base_commit": "8c0a6d636e0d4856c25dfe1f79c6155e7eaee1d5",
        "baseline_verified_files": len(manifest["output_files"]),
        "source_java": 1711,
        "moved_java": len(moves),
        "edited_java": len(edited),
        "additional_cross_package_imports": imports,
        "target_packages": dict(sorted(counts.items())),
        "dll_original_sha256": sha(files["CTransport.dll"]) if "CTransport.dll" in files else None,
        "output_sha256": checksum,
        "archive": str(args.output),
    }
    (root / "packages-validation.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
