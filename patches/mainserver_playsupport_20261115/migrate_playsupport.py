#!/usr/bin/env python3
"""Move the *existing* PSS implementation into the MainServer.PlaySupport tree.

Starting point: the already compiled, 121-class MainServer package ZIP.
No gameplay, DB, packet-body, network, quota or configuration algorithms change.
"""
from pathlib import Path
import hashlib
import json
import re
import zipfile
import argparse

OLD_ROOT = "l1j.server.server.model.character.pss"
OLD_UI = OLD_ROOT + ".window"
OLD_NATIVE = "l1j.server.server.model.network.core.sc.packet"
NEW_ROOT = "l1j.server.server.packets.Protocol.MainServer.PlaySupport"
NEW_UI = NEW_ROOT + ".window"
NEW_NATIVE = NEW_ROOT + ".Proto"
FINAL = "CTransport_MainServer_PlaySupport_FULL_SRC_20261115.zip"
PREVIOUS = "CTransport_MainServer_Packages_FULL_SRC_20261115.zip"
PREVIOUS_SHA256 = "e7e191e9144e31dcc0f1c08b8302a197117c04171b4fd293cfeca9488208782f"
PSS_CORE = ("ClientAutomationSession", "PlaySupport2019", "PssConfig", "PssDailyQuota")
PSS_WINDOW = ("PlaySupportWindow", "PssWindowTransport")
NATIVE = "SC_PssNativeWindow"
sha = lambda b: hashlib.sha256(b).hexdigest()


def relocation():
    moved = {}
    for cls in PSS_CORE:
        moved[OLD_ROOT + "." + cls] = NEW_ROOT + "." + cls
    for cls in PSS_WINDOW:
        moved[OLD_UI + "." + cls] = NEW_UI + "." + cls
    moved[OLD_NATIVE + "." + NATIVE] = NEW_NATIVE + "." + NATIVE
    return moved


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path("downloads") / PREVIOUS)
    parser.add_argument("--output", type=Path, default=Path("downloads") / FINAL)
    parser.add_argument("--work", type=Path, default=Path("/tmp/playsupport-20261115"))
    args = parser.parse_args()
    if not args.input.is_file():
        raise FileNotFoundError(args.input)
    raw = args.input.read_bytes()
    if sha(raw) != PREVIOUS_SHA256:
        raise ValueError("Input ZIP is not verified previous MainServer package version")
    with zipfile.ZipFile(args.input) as z:
        assert z.testzip() is None
        files = {i.filename: z.read(i) for i in z.infolist() if not i.is_dir()}
    java_count = sum(x.endswith(".java") for x in files)
    assert java_count == 1711, java_count
    mapping = relocation()
    old_paths = {"src/" + k.replace(".", "/") + ".java": "src/" + v.replace(".", "/") + ".java"
                 for k, v in mapping.items()}
    if len(old_paths) != 7 or any(p not in files for p in old_paths):
        raise ValueError("PSS source set not found: " + repr([p for p in old_paths if p not in files]))
    for new in old_paths.values():
        if new in files:
            raise ValueError("Target path exists: " + new)
    names = sorted(mapping, key=len, reverse=True)
    full = re.compile(r"(?<![A-Za-z0-9_])(?:" + "|".join(re.escape(s) for s in names) + r")(?![A-Za-z0-9_])")
    wildcards = {OLD_ROOT + ".*": NEW_ROOT + ".*",
                 OLD_UI + ".*": NEW_UI + ".*"}
    output = {}
    edited = set()
    references = 0
    for path, data in sorted(files.items()):
        if not path.endswith(".java"):
            output[path] = data
            continue
        text = data.decode("utf-8")
        target = old_paths.get(path, path)
        if target != path:
            new_pkg = ".".join(target.split("/")[1:-1])
            old_pkg = ".".join(path.split("/")[1:-1])
            declaration = re.compile(r"(?m)^(\s*package\s+)" + re.escape(old_pkg) + r"(\s*;)")
            text, count = declaration.subn(lambda m: m.group(1) + new_pkg + m.group(2), text, count=1)
            if count != 1:
                raise ValueError("Missing package declaration: " + path)
        # Rewrites only complete qualified names; no methods/expressions altered.
        text, count = full.subn(lambda m: mapping[m.group()], text)
        references += count
        for old_wild, new_wild in wildcards.items():
            text = re.sub(r"(?m)^(\s*import\s+(?:static\s+)?)" +
                          re.escape(old_wild) + r"(\s*;)",
                          lambda m: m.group(1) + new_wild + m.group(2), text)
        result = text.encode("utf-8")
        if target in output:
            raise ValueError("Duplicate relocated file " + target)
        output[target] = result
        if target != path or result != data:
            edited.add(target)
    for old in old_paths:
        assert old not in output
    assert len(output) == len(files)
    assert sum(x.endswith(".java") for x in output) == java_count
    for path, data in output.items():
        if not path.endswith(".java"):
            assert data == files[path], "Non-Java source changed: " + path
        else:
            content = data.decode("utf-8")
            if re.search(r"\bl1j\.server\.server\.model\.character\.pss(?:\.|\b)", content):
                raise ValueError("Old PSS package still referenced in " + path)
            if re.search(r"\bl1j\.server\.server\.model\.network\.core\.sc\.packet\.SC_PssNativeWindow\b", content):
                raise ValueError("Old native packet reference still in " + path)
    assert output["CTransport.dll"] == files["CTransport.dll"]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for name, data in sorted(output.items()):
            z.writestr(name, data)
    with zipfile.ZipFile(args.output) as z:
        assert z.testzip() is None
        assert len(z.namelist()) == len(files)
    root = args.work
    root.mkdir(parents=True, exist_ok=True)
    for name, data in output.items():
        if name.startswith("src/"):
            dst = root / name
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_bytes(data)
    report = {
        "input_verified_sha256": PREVIOUS_SHA256,
        "output_sha256": sha(args.output.read_bytes()),
        "java_count": java_count,
        "moved_classes": len(mapping),
        "java_files_touched": len(edited),
        "qualified_refs_changed": references,
        "classes": mapping,
        "dll_sha256_unchanged": sha(output["CTransport.dll"]),
        "config_path_unchanged": b'./config/PSS.properties' in output[
            "src/" + (NEW_ROOT + ".PssConfig").replace(".", "/") + ".java"],
        "type_only_migration": True,
        "windows_runtime_verified": False,
    }
    assert report["config_path_unchanged"]
    (root / "report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False))
    print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
