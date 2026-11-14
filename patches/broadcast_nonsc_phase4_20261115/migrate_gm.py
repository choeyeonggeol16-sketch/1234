#!/usr/bin/env python3
"""Phase4: share the GM-only death-drop announcement wire.

Input is pinned to the verified phase3 full source ZIP via Git blob SHA-1.
Only one proven recipient-independent GM notice is changed.
No generic packet serialization, retry/fallback, queue or worker additions.
"""
from __future__ import annotations
import argparse, hashlib, json, pathlib, re, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
INPUT = ROOT / "downloads/RKern_Broadcast_NonSC_Phase3_FULL_SRC_20261115.zip"
OUTPUT = ROOT / "downloads/RKern_Broadcast_NonSC_Phase4_FULL_SRC_20261115.zip"
EXPECTED_GIT_BLOB_SHA = "7df6095fa8203b5f34153f8ee8091e35db576272"
SHARED = "src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java"
PLAYER = "src/l1j/server/server/model/Instance/L1PcInstance.java"
SYSTEM = "src/l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java"

def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def blob_sha(data: bytes) -> str:
    header = b"blob " + str(len(data)).encode("ascii") + b"\0"
    return hashlib.sha1(header + data).hexdigest()

SEND_GMS = '''
    /**
     * One identical frame for online GM recipients. The original world
     * traversal order and per-connection FIFO are preserved, and non-GMs
     * receive no packet. No temporary recipient collection is allocated.
     */
    public static void sendAllGms(byte[] wire) {
        if (wire == null) return;
        try (var shared = new CAsyncSendConstBuffer(
                wire, Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
                if (pc == null || !pc.isGm()) continue;
                final CIOClient client = pc.getNetConnection();
                if (client != null && client.isConnected()) {
                    shared.send(client.getConnection(), pc.getId());
                }
            }
        }
    }

'''

TARGET_SEND = r'SC_SystemMessage\.send\(\s*pc\s*,\s*"\\\\fY\[GM\] "\s*\+\s*getName\(\)\s*\+\s*"님의 \["\s*\+\s*item\.getLogName\(\)\s*\+\s*"\] 드랍"\s*\)\s*;'
LOOP = re.compile(
    r'for\s*\(\s*L1PcInstance\s+pc\s*:\s*L1World\.getInstance\(\)\.getAllPlayers\(\)\s*\)'
    r'\s*\{\s*if\s*\(\s*pc\.isGm\(\)\s*\)\s*\{\s*'
    + TARGET_SEND
    + r'\s*;?\s*\}\s*\}',
    re.MULTILINE
)
REPLACEMENT = (
    'l1j.server.server.model.network.core.broadcast.CSharedBroadcast.'
    'sendAllGms(SC_SystemMessage.sharedWire('
    '"\\\\fY[GM] " + getName() + "님의 [" + item.getLogName() + "] 드랍"));'
)

def patch_shared(source: str) -> str:
    marker = '    /** Global broadcast includes the sender and uses the normal socket FIFO. */'
    if source.count(marker) != 1:
        raise AssertionError("Unexpected CSharedBroadcast insertion point")
    if 'public static void sendAllGms(' in source:
        raise AssertionError("GM shared broadcast already present")
    if 'public static void sendRawSelfAndVisible(' not in source:
        raise AssertionError("Not latest phase3 social-action source")
    return source.replace(marker, SEND_GMS + marker, 1)

def patch_player(source: str) -> str:
    matches = list(LOOP.finditer(source))
    if len(matches) != 1:
        raise AssertionError(f"Expected one exact active GM drop announcement, got {len(matches)}")
    return source[:matches[0].start()] + REPLACEMENT + source[matches[0].end():]

def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--input", type=pathlib.Path, default=INPUT)
    p.add_argument("--output", type=pathlib.Path, default=OUTPUT)
    p.add_argument("--work", type=pathlib.Path, default=pathlib.Path("/tmp/broadcast-nonsc-phase4"))
    args = p.parse_args()
    original_zip = args.input.read_bytes()
    if blob_sha(original_zip) != EXPECTED_GIT_BLOB_SHA:
        raise ValueError("Input is not the verified GitHub phase3 full SRC ZIP")
    with zipfile.ZipFile(args.input) as z:
        assert z.testzip() is None
        contents = {f.filename: z.read(f) for f in z.infolist() if not f.is_dir()}
    before = dict(contents)
    assert "CTransport.dll" in contents
    assert sum(k.endswith(".java") for k in contents) == 1711
    assert 'public static byte[] sharedWire(String message)' in contents[SYSTEM].decode("utf-8")
    for name, fn in [(SHARED, patch_shared), (PLAYER, patch_player)]:
        old = contents[name].decode("utf-8")
        new = fn(old)
        assert new != old
        contents[name] = new.encode("utf-8")
    changed = sorted(k for k in contents if contents[k] != before[k])
    assert changed == sorted([PLAYER, SHARED]), changed
    assert contents["CTransport.dll"] == before["CTransport.dll"]
    args.work.mkdir(parents=True, exist_ok=True)
    for name, data in contents.items():
        if name.startswith("src/") and name.endswith(".java"):
            dest = args.work / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(data)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for name, data in sorted(contents.items()):
            z.writestr(name, data)
    with zipfile.ZipFile(args.output) as z:
        assert z.testzip() is None
        assert set(z.namelist()) == set(contents)
    report = {
        "phase": 4, "input_git_blob_sha": EXPECTED_GIT_BLOB_SHA,
        "input_sha256": sha256(original_zip),
        "output_sha256": sha256(args.output.read_bytes()),
        "modified_java_files": changed,
        "full_java_sources": 1711,
        "gm_filter_preserved": True,
        "world_traversal_order_preserved": True,
        "one_shared_system_message_wire": True,
        "no_fallback": True,
        "native_dll_unchanged": True,
        "windows_runtime_tested": False
    }
    (args.work / "audit.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf8"
    )
    print(json.dumps(report, indent=2, ensure_ascii=False))

if __name__ == "__main__":
    main()
