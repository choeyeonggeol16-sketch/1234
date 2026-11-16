#!/usr/bin/env python3
"""75: All 16 fixed-size legacy SC AOI broadcast encoders use Native pooled Assemble.

Input must be the verified Phase74 full SRC; no legacy/heap fallback, no unrelated
game-world/one-to-one SEND changes, no new queues/workers/classes.
"""
from pathlib import Path
import hashlib, json, re, shutil, sys, zipfile
ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "downloads/RKern_NC_SPacket_Assemble_Phase74_FULL_SRC_20261116.zip"
PIN = "4f5d0f8cdb1850693aa0b36ef1fba5c609292233f6c7558102f047287a24b3d8"
OUT = ROOT / "downloads/RKern_NC_SPacket_Assemble_Phase75_FULL_SRC_20261116.zip"
WORK = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else None
P = "src/l1j/server/server/model/network/core/"
SHARED = P + "broadcast/CSharedBroadcast.java"
SERVER = P + "transport/CIOServer.java"
CONFIG = P + "config/CIOConfig.java"
CPP = "native/CTransport.cpp"
ABI = "native/interop_abi.hpp"
# Each entry: kind, opcode symbol, four frozen primitive arguments, count of
# distinct legacy broadcast calls in the exact Phase74 source.
SPEC = {
    "Door": (15, "S_CHANGE_ATTR", ("x", "y", "direction", "passable"), 1),
    "Lawful": (16, "S_CHANGE_ALIGNMENT", ("objectId", "lawful", "0", "0"), 2),
    "Liquor": (17, "S_DRUNKEN", ("objectId", "type", "0", "0"), 2),
    "PinkName": (18, "S_CRIMINAL", ("objectId", "time", "0", "0"), 2),
    "SkillBrave": (19, "S_EMOTION", ("objectId", "mode", "time", "0"), 2),
    "SkillHaste": (20, "S_SPEED", ("objectId", "mode", "time", "0"), 2),
    "SkillIconAura": (21, "S_EVENT", ("0x16", "iconId", "time", "0"), 2),
    "SkillIconBlessOfEva": (22, "S_BREATH", ("objectId", "time", "0", "0"), 2),
    "SkillIconShield": (23, "S_MAGE_SHIELD", ("time", "type", "0", "0"), 2),
    "SkillIconWindShackle": (24, "S_EVENT", ("objectId", "0", "0", "0"), 2),
    "SkillIconWisdomPotion": (25, "S_EVENT", ("0x39", "0x2c", "time", "0"), 2),
    "Resurrection": (26, "S_RESURRECT", ("targetId", "type", "userId", "targetClassId"), 1),
    "Dexup": (27, "S_MAGE_DEXTERITY", ("time", "dex", "type", "0"), 2),
    "Strup": (28, "S_MAGE_STRENGTH", ("time", "totalStr", "weight100", "type"), 2),
    "CharVisualUpdate": (29, "S_WIELD", ("(int)(packed >>> 32)", "(int)packed", "0", "0"), 2),
    "TrueTargetNew": (30, "S_EVENT", ("targetId", "(active ? 1 : 0)", "0", "0"), 1),
}
PATHS = {name: P + "sc/packet/SC_" + name + ".java" for name in SPEC}
CHANGED = set(PATHS.values()) | {SHARED, SERVER, CONFIG, CPP, ABI, "CTransport.dll"}

def replace_once(files, path, before, after):
    original = files[path].decode("utf-8")
    crlf = "\r\n" in original
    s = original.replace("\r\n", "\n")
    assert s.count(before) == 1, (path, "match count", s.count(before), before[:100])
    s = s.replace(before, after, 1)
    files[path] = (s.replace("\n", "\r\n") if crlf else s).encode("utf-8")

def erase_private_wire(s, path):
    # All matching private builders are used only by broadcast; we preserve the
    # existing public SC_TrueTargetNew.sharedWire API for other code.
    assert s.count("sharedWire(") == 1, ("unexpected residual sharedWire references", path)
    start = s.index("private static byte[] sharedWire(")
    left = s.rfind("\n", 0, start) + 1
    brace = s.index("{", start)
    depth = 0
    end = -1
    for pos in range(brace, len(s)):
        if s[pos] == "{": depth += 1
        elif s[pos] == "}":
            depth -= 1
            if depth == 0:
                end = pos + 1
                break
    assert end > brace
    return s[:left] + s[end:].lstrip("\n")

def edit_sc(files, name, spec):
    kind, opcode, params, expected = spec
    path = PATHS[name]
    original = files[path].decode("utf-8")
    crlf = "\r\n" in original
    s = original.replace("\r\n", "\n")
    # Dex/Str initially store the private array in a local.
    if name in ("Dexup", "Strup"):
        pat = r"(?m)^[ \t]*final byte\[\] wire=sharedWire\([^;\n]*\);\n"
        s, n = re.subn(pat, "", s)
        assert n == 2, (name, "local arrays", n)
        assert s.count("CSharedBroadcast.send(anchor, wire,") == 2
        target = r"CSharedBroadcast\.send\((anchor), wire, (true|false), -1, null\);"
    else:
        target = r"CSharedBroadcast\.send\((\w+), sharedWire\([^;\n]*\), (true|false), -1, null\);"
    prefix = "l1j.server.server.model.network.core.broadcast.CSharedBroadcast."
    args = ", ".join(params)
    def migrate_call(m):
        anchor, fast = m.group(1), m.group(2)
        return (f"CSharedBroadcast.sendFormatted({anchor}, "
                f"l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_{name.upper()}, "
                f"Opcodes.{opcode}, {args}, {fast}, -1, null);")
    s, count = re.subn(target, migrate_call, s)
    assert count == expected, (name, "broadcast count", count, expected)
    # Existing fully-qualified class prefix precedes the matched method.
    assert s.count(prefix + "sendFormatted(") >= expected, name
    assert "CSharedBroadcast.send(" not in s and "CSharedBroadcast.sendRecipients(" not in s, name
    if name != "TrueTargetNew":
        s = erase_private_wire(s, path)
    else:
        assert "public static byte[] sharedWire" in s
    files[path] = (s.replace("\n", "\r\n") if crlf else s).encode("utf-8")

NATIVE = r"""
        if(kind==15){ // door x/y/direction/passable
            const std::uint64_t args[5]={static_cast<unsigned>(opcode),
                static_cast<unsigned>(objectId),static_cast<unsigned>(a),
                static_cast<unsigned>(b),static_cast<unsigned>(c)};
            return AssemblePooled(server,"chhcc",args,5);
        }
        if(kind==16){ // lawful: id,short alignment,zero DWORD
            const std::uint64_t args[4]={static_cast<unsigned>(opcode),
                static_cast<unsigned>(objectId),static_cast<unsigned>(a),0u};
            return AssemblePooled(server,"cdhd",args,4);
        }
        if(kind==17||kind==18){ // drunken/criminal: id,byte
            const std::uint64_t args[3]={static_cast<unsigned>(opcode),
                static_cast<unsigned>(objectId),static_cast<unsigned>(a)};
            return AssemblePooled(server,"cdc",args,3);
        }
        if(kind==19||kind==20){ // brave/haste: id,mode,time
            const std::uint64_t args[4]={static_cast<unsigned>(opcode),
                static_cast<unsigned>(objectId),static_cast<unsigned>(a),
                static_cast<unsigned>(b)};
            return AssemblePooled(server,"cdch",args,4);
        }
        if(kind==21||kind==25){ // event subtype,byte value,short time
            const std::uint64_t args[4]={static_cast<unsigned>(opcode),
                static_cast<unsigned>(objectId),static_cast<unsigned>(a),
                static_cast<unsigned>(b)};
            return AssemblePooled(server,"ccch",args,4);
        }
        if(kind==22){ // breath id,time
            const std::uint64_t args[3]={static_cast<unsigned>(opcode),
                static_cast<unsigned>(objectId),static_cast<unsigned>(a)};
            return AssemblePooled(server,"cdh",args,3);
        }
        if(kind==23){ // shield: time,type,zero DWORD
            const std::uint64_t args[4]={static_cast<unsigned>(opcode),
                static_cast<unsigned>(objectId),static_cast<unsigned>(a),0u};
            return AssemblePooled(server,"chcd",args,4);
        }
        if(kind==24){ // Wind Shackle: time is intentionally ignored (original)
            const std::uint64_t args[6]={static_cast<unsigned>(opcode),
                0x2cu,static_cast<unsigned>(objectId),0u,0x0au,0x80u};
            return AssemblePooled(server,"ccdccc",args,6);
        }
        if(kind==26){ // resurrection: id,byte,dword,dword
            const std::uint64_t args[5]={static_cast<unsigned>(opcode),
                static_cast<unsigned>(objectId),static_cast<unsigned>(a),
                static_cast<unsigned>(b),static_cast<unsigned>(c)};
            return AssemblePooled(server,"cdcdd",args,5);
        }
        if(kind==27){ // dex: time,dex,type,zero DWORD
            const std::uint64_t args[5]={static_cast<unsigned>(opcode),
                static_cast<unsigned>(objectId),static_cast<unsigned>(a),
                static_cast<unsigned>(b),0u};
            return AssemblePooled(server,"chccd",args,5);
        }
        if(kind==28){ // str: time,str,weight,type,zero DWORD
            const std::uint64_t args[6]={static_cast<unsigned>(opcode),
                static_cast<unsigned>(objectId),static_cast<unsigned>(a),
                static_cast<unsigned>(b),static_cast<unsigned>(c),0u};
            return AssemblePooled(server,"chcccd",args,6);
        }
        if(kind==29){ // wield: id,status,0xff,0xff,zero WORD
            const std::uint64_t args[6]={static_cast<unsigned>(opcode),
                static_cast<unsigned>(objectId),static_cast<unsigned>(a),
                0xffu,0xffu,0u};
            return AssemblePooled(server,"cdccch",args,6);
        }
        if(kind==30){ // true target: original event subtype + zero trailers
            const std::uint64_t args[9]={static_cast<unsigned>(opcode),
                0xc2u,static_cast<unsigned>(objectId),0x4fu,0x33u,
                0u,0u,static_cast<unsigned>(a),0u};
            return AssemblePooled(server,"ccdccccdh",args,9);
        }
"""

def prepare():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest() == PIN, "phase74 baseline mismatch"
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        infos = z.infolist()
        files = {i.filename: z.read(i.filename) for i in infos if not i.is_dir()}
    old = files.copy()
    assert len(files) == 1734 and sum(x.endswith(".java") for x in files) == 1710
    b = files[SHARED].decode("utf-8").replace("\r\n", "\n")
    anchor = "    public static final int FORMAT_FISHING = 14;"
    assert b.count(anchor) == 1
    constants = "\n" + "\n".join(
        f"    public static final int FORMAT_{name.upper()} = {spec[0]};"
        for name, spec in SPEC.items())
    replace_once(files, SHARED, anchor, anchor + constants)
    sw = files[SERVER].decode("utf-8").replace("\r\n", "\n")
    marker = "            case 14 -> 18;    // S_ACTION fishing: cdchhdh"
    assert marker in sw
    sizes = [9,13,8,8,10,10,7,9,10,11,7,16,11,12,12,18]
    assert len(sizes) == len(SPEC)
    cases = "\n" + "\n".join(f"            case {kind} -> {size};"
            for kind,size in zip(range(15,31),sizes))
    replace_once(files, SERVER, marker, marker + cases)
    cpp = files[CPP].decode("utf-8")
    start = cpp.index("CIO_API void* L1Interop_CreateFormattedConstBuffer(")
    end = cpp.index("CIO_API const void* L1Interop_ConstBufferData", start)
    pos = cpp.rfind("        return nullptr;", start, end)
    assert pos > start and "kind==14" in cpp[start:end]
    files[CPP] = (cpp[:pos] + NATIVE + cpp[pos:]).encode("utf-8")
    replace_once(files, CONFIG, "ABI = 0x000a0006", "ABI = 0x000a0007")
    replace_once(files, ABI, "0x000a0006", "0x000a0007")
    for name,spec in SPEC.items(): edit_sc(files,name,spec)
    touched = {n for n in old if old[n]!=files[n]}
    assert touched == CHANGED - {"CTransport.dll"}, ("unexpected changes",sorted(touched ^ (CHANGED - {"CTransport.dll"})))
    assert "sourceOverflowRecord" in files["native/cio_core.hpp"].decode("utf-8")
    for name,value in files.items():
        if name.startswith("native/") or (name.startswith("src/") and name.endswith(".java")):
            dest=WORK/name
            dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(value)
    native_root = ROOT/"patches/ctransport_core_20261112/native"
    for src in native_root.iterdir():
        if src.is_file() and not (WORK/"native"/src.name).exists():
            shutil.copy2(src,WORK/"native"/src.name)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    for name in ("build","logs"): (WORK/name).mkdir(exist_ok=True)
    print("PHASE75_PREPARED",len(SPEC),"fixed-SC",len(touched),"changed source files",flush=True)

def package():
    dll=WORK/"build/CTransport.dll"
    dllsha=hashlib.sha256(dll.read_bytes()).hexdigest()
    assert dllsha in (WORK/CONFIG).read_text(encoding="utf-8"),"CTransport DLL checksum pin mismatch"
    with zipfile.ZipFile(BASE) as z:
        infos=z.infolist()
        old={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    new=old.copy()
    for name in CHANGED-{"CTransport.dll"}: new[name]=(WORK/name).read_bytes()
    new["CTransport.dll"]=dll.read_bytes()
    touched={n for n in old if old[n]!=new[n]}
    assert touched==CHANGED,(sorted(touched),sorted(CHANGED))
    assert len(new)==1734 and sum(n.endswith(".java") for n in new)==1710
    OUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for info in infos:z.writestr(info,b"" if info.is_dir() else new[info.filename])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None and len(z.namelist())==1734
    audit={"phase":75,"baseline_sha256":PIN,
        "full_src_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
        "dll_sha256":dllsha,"native_abi":"0x000a0007",
        "packet_classes":sorted(PATHS),
        "production_changed":sorted(touched),
        "java_count":1710,"zip_entries":1734,
        "phase72_native_pool_exhaustion_preserved":True,
        "phase73_phase74_codecs_preserved":True,
        "existing_socket_fifo_cipher_preserved":True,
        "new_queues_threads_classes":0,
        "unmigrated_complex_SC_to_follow":19,
        "in_game_5000_soak_verified":False}
    (WORK/"audit.json").write_text(json.dumps(audit,ensure_ascii=False,indent=2)+"\n")
    print("PHASE75_FULL_SRC_VERIFIED",json.dumps(audit),flush=True)

if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    prepare() if sys.argv[2]=="prepare" else package()
