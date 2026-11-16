#!/usr/bin/env python3
"""74: six more fixed L1 AOI S-packet layouts use the existing Native pooled
Assemble and socket-local encrypted SEND; based exclusively on pinned 73 ZIP.
No new worker, queue, packet class, alternate broadcast path or fallback.
"""
from pathlib import Path
import hashlib, json, shutil, sys, zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "downloads/RKern_NC_SPacket_Assemble_Extended_FULL_SRC_20261116.zip"
BASE_SHA = "c330159d27d4f1f85634b2fd1606e259989a6b49e71db175c355c45d6ff31c19"
OUT = ROOT / "downloads/RKern_NC_SPacket_Assemble_Phase74_FULL_SRC_20261116.zip"
WORK = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else None
P = "src/l1j/server/server/model/network/core/"
BCAST = P + "broadcast/CSharedBroadcast.java"
SERVER = P + "transport/CIOServer.java"
CONFIG = P + "config/CIOConfig.java"
CPP = "native/CTransport.cpp"
ABI = "native/interop_abi.hpp"
PACKETS = {name: P + "sc/packet/SC_" + name + ".java" for name in
           ("Light", "Poison", "HPMeter", "CharSpeedUpdate", "Invis", "Fishing")}
CHANGED = set(PACKETS.values()) | {BCAST, SERVER, CONFIG, CPP, ABI, "CTransport.dll"}

def mutate(files, key, old, new):
    original = files[key].decode("utf-8")
    crlf = "\r\n" in original
    data = original.replace("\r\n", "\n")
    assert data.count(old) == 1, (key, "match count", data.count(old), old[:140])
    data = data.replace(old, new, 1)
    files[key] = data.replace("\n", "\r\n").encode("utf-8") if crlf else data.encode("utf-8")

def remove_unused_private_wire(files, key):
    original = files[key].decode("utf-8")
    crlf = "\r\n" in original
    data = original.replace("\r\n", "\n")
    assert data.count("sharedWire(") == 1, ("legacy wire still referenced", key)
    begin = data.index("private static byte[] sharedWire(")
    left = data.rfind("\n", 0, begin) + 1
    open_brace = data.index("{", begin)
    depth, end = 0, 0
    for pos in range(open_brace, len(data)):
        if data[pos] == "{": depth += 1
        elif data[pos] == "}":
            depth -= 1
            if depth == 0:
                end = pos + 1
                break
    assert end and depth == 0
    data = data[:left] + data[end:].lstrip("\n")
    files[key] = data.replace("\n", "\r\n").encode("utf-8") if crlf else data.encode("utf-8")

def prepare():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest() == BASE_SHA, "73 ZIP source pin mismatch"
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        infos = z.infolist()
        files = {i.filename: z.read(i.filename) for i in infos if not i.is_dir()}
    old = files.copy()
    assert len(files) == 1734 and sum(n.endswith(".java") for n in files) == 1710

    mutate(files, BCAST,
        "    public static final int FORMAT_EFFECT_LOCATION = 8;\n",
        """    public static final int FORMAT_EFFECT_LOCATION = 8;
    // Additional fixed L1 formats, all assembled into one refcounted Native source.
    public static final int FORMAT_LIGHT = 9;
    public static final int FORMAT_POISON = 10;
    public static final int FORMAT_HP_METER = 11;
    public static final int FORMAT_CHAR_SPEED = 12;
    public static final int FORMAT_INVIS = 13;
    public static final int FORMAT_FISHING = 14;
""")
    mutate(files, SERVER,
        "            case 8 -> 10;  // S_EFFECT_LOC: chhhc\n",
        """            case 8 -> 10;  // S_EFFECT_LOC: chhhc
            case 9, 13 -> 8;  // S_CHANGE_LIGHT / S_INVISIBLE: cdc
            case 10 -> 9;     // S_POISON: cdcc
            case 11 -> 11;    // S_HIT_RATIO: cdcch
            case 12 -> 10;    // S_CHANGE_LEVEL: cdccc
            case 14 -> 18;    // S_ACTION fishing: cdchhdh
""")

    data = files[CPP].decode("utf-8")
    start = data.index("CIO_API void* L1Interop_CreateFormattedConstBuffer(")
    stop = data.index("CIO_API const void* L1Interop_ConstBufferData", start)
    index = data.rfind("        return nullptr;", start, stop)
    assert index > start, "Native formatter fallthrough anchor missing"
    native = """        if(kind==9 || kind==13){ // light or invis: opcode,dword,byte
            const std::uint64_t args[3]={
                static_cast<unsigned>(opcode),static_cast<unsigned>(objectId),
                static_cast<unsigned>(a)};
            return AssemblePooled(server,"cdc",args,3);
        }
        if(kind==10){ // poison: exactly two mutually exclusive 0/1 flags
            const std::uint64_t args[4]={
                static_cast<unsigned>(opcode),static_cast<unsigned>(objectId),
                a==1?1u:0u,a==2?1u:0u};
            return AssemblePooled(server,"cdcc",args,4);
        }
        if(kind==11){ // HP meter: id, hp%, mp%, zero short
            const std::uint64_t args[5]={
                static_cast<unsigned>(opcode),static_cast<unsigned>(objectId),
                static_cast<unsigned>(a),static_cast<unsigned>(b),0u};
            return AssemblePooled(server,"cdcch",args,5);
        }
        if(kind==12){ // character speed: id, speed, level, zero byte
            const std::uint64_t args[5]={
                static_cast<unsigned>(opcode),static_cast<unsigned>(objectId),
                static_cast<unsigned>(a),static_cast<unsigned>(b),0u};
            return AssemblePooled(server,"cdccc",args,5);
        }
        if(kind==14){ // fishing action: id, motion, x/y, zero dword/short
            const std::uint64_t args[7]={
                static_cast<unsigned>(opcode),static_cast<unsigned>(objectId),
                static_cast<unsigned>(a),static_cast<unsigned>(b),
                static_cast<unsigned>(c),0u,0u};
            return AssemblePooled(server,"cdchhdh",args,7);
        }
"""
    assert "AssemblePooled(server,\"chhhc\"" in data[start:stop]
    files[CPP] = (data[:index] + native + data[index:]).encode("utf-8")
    mutate(files, ABI, "0x000a0005", "0x000a0006")
    mutate(files, CONFIG, "ABI = 0x000a0005", "ABI = 0x000a0006")

    k = PACKETS["Light"]
    mutate(files, k, "l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(cha, sharedWire(objectId, type), true, -1, null);",
        """l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                cha, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_LIGHT,
                Opcodes.S_CHANGE_LIGHT, objectId, type, 0, 0, true, -1, null);""")
    k = PACKETS["Poison"]
    for fast in ("false", "true"):
        mutate(files, k, f"l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, type), {fast}, -1, null);",
            f"""l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_POISON,
                Opcodes.S_POISON, objectId, type, 0, 0, {fast}, -1, null);""")
    k = PACKETS["HPMeter"]
    mutate(files, k, "l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(frozenId, frozenHp, frozenMp), false, -1, null);",
        """l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_HP_METER,
                Opcodes.S_HIT_RATIO, frozenId, frozenHp, frozenMp, 0, false, -1, null);""")
    k = PACKETS["CharSpeedUpdate"]
    mutate(files, k, "l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, speed, level), false, -1, null);",
        """l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_CHAR_SPEED,
                Opcodes.S_CHANGE_LEVEL, objectId, speed, level, 0, false, -1, null);""")
    k = PACKETS["Invis"]
    for fast in ("false", "true"):
        mutate(files, k, f"l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, type), {fast}, -1, null);",
            f"""l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_INVIS,
                Opcodes.S_INVISIBLE, objectId, type, 0, 0, {fast}, -1, null);""")
    k = PACKETS["Fishing"]
    mutate(files, k, "l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, motionNum, x, y), false, -1, null);",
        """l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_FISHING,
                Opcodes.S_ACTION, objectId, motionNum, x, y, false, -1, null);""")
    for key in PACKETS.values():
        remove_unused_private_wire(files, key)

    touched = {n for n in old if old[n] != files[n]}
    assert touched == CHANGED - {"CTransport.dll"}, ("unexpected diff", sorted(touched ^ (CHANGED - {"CTransport.dll"})))
    assert all(files[n] == old[n] for n in old if n not in touched)
    assert "sourceOverflowRecord" in files["native/cio_core.hpp"].decode("utf-8")
    assert "case 14 -> 18" in files[SERVER].decode("utf-8")
    for name, value in files.items():
        if name.endswith(".java") and name.startswith("src/") or name.startswith("native/"):
            dest = WORK / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(value)
    native_root = ROOT / "patches/ctransport_core_20261112/native"
    for src in native_root.iterdir():
        if src.is_file() and not (WORK / "native" / src.name).exists():
            shutil.copy2(src, WORK / "native" / src.name)
    shutil.copytree(ROOT / "patches/ctransport_core_20261112/tests",
                    WORK / "tests", dirs_exist_ok=True)
    for directory in ("build", "logs"):
        (WORK / directory).mkdir(exist_ok=True)
    print("PHASE74_PREPARED", len(touched), "source files; 6 Native pool formatters; 1710 Java", flush=True)

def package():
    dll = WORK / "build/CTransport.dll"
    dllsha = hashlib.sha256(dll.read_bytes()).hexdigest()
    assert dllsha in (WORK / CONFIG).read_text(encoding="utf-8"), "Native DLL checksum not pinned in CIOConfig"
    with zipfile.ZipFile(BASE) as z:
        infos = z.infolist()
        original = {i.filename: z.read(i.filename) for i in infos if not i.is_dir()}
    modified = original.copy()
    for name in CHANGED - {"CTransport.dll"}:
        modified[name] = (WORK / name).read_bytes()
    modified["CTransport.dll"] = dll.read_bytes()
    touched = {n for n in original if original[n] != modified[n]}
    assert touched == CHANGED, ("changed set mismatch", sorted(touched), sorted(CHANGED))
    assert len(modified) == 1734 and sum(x.endswith(".java") for x in modified) == 1710
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for info in infos:
            z.writestr(info, b"" if info.is_dir() else modified[info.filename])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None and len(z.namelist()) == 1734
    audit = {
        "phase": 74, "baseline_sha256": BASE_SHA,
        "full_src_sha256": hashlib.sha256(OUT.read_bytes()).hexdigest(),
        "dll_sha256": dllsha, "native_abi": "0x000a0006",
        "modified": sorted(touched), "java_count": 1710, "zip_entries": 1734,
        "native_pool_exhaustion_phase72_preserved": True,
        "phase73_6_formatters_preserved": True,
        "socket_fifo_cipher_preserved": True, "no_new_workers_queues_classes": True,
        "in_game_5000_soak_verified": False
    }
    (WORK / "audit.json").write_text(json.dumps(audit, ensure_ascii=False, indent=2) + "\n")
    print("PHASE74_FULL_SRC_VERIFIED", json.dumps(audit), flush=True)

if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[2] not in ("prepare", "package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    prepare() if sys.argv[2] == "prepare" else package()
