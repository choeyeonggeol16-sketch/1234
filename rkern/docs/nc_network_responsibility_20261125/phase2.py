#!/usr/bin/env python3
"""Mechanical ownership consolidation; run from the repository root."""
from pathlib import Path
import re
import json

root = Path("rkern/src")
net = root / "l1j/server/server/model/network"
cfg = net / "core/config/CIOConfig.java"
oldcfg = net / "utill/CConnectionConfig.java"
buf = net / "core/connection/CIOBufferEx.java"
frame = net / "core/connection/CIOFrame.java"
cfg_name = "l1j.server.server.model.network.core.config.CIOConfig"
old_cfg_name = "l1j.server.server.model.network.utill.CConnectionConfig"
old_frame_name = "l1j.server.server.model.network.core.connection.CIOFrame"
frame_name = "l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame"

def read(p):
    return p.read_bytes().decode("utf-8")

def write(p, s):
    p.write_bytes(s.encode("utf-8"))

def nest(s, body):
    at = s.rfind("}")
    assert at >= 0 and not s[at+1:].strip()
    return s[:at].rstrip() + "\n\n" + "\n".join("    " + v if v else "" for v in body.strip("\n").split("\n")) + "\n}\n"

# Separate initialization is intentional. Network.ini retains its original
# constructor timing; transport properties are initialized on first use only.
transport = read(cfg).replace("\r\n", "\n")
connection = read(oldcfg)
assert transport.count("private CIOConfig() {}") == 1
transport_body = transport[transport.index("{", transport.index("public final class CIOConfig"))+1:transport.rfind("}")]
transport_body = transport_body.replace("    private CIOConfig() {}\n", "")
new_cfg = connection.replace("package l1j.server.server.model.network.utill;", "package " + cfg_name.rsplit(".", 1)[0] + ";", 1)
new_cfg = new_cfg.replace("import java.net.SocketAddress;", "import java.net.SocketAddress;\nimport java.nio.file.Path;", 1)
new_cfg = new_cfg.replace("public class CConnectionConfig {", "public final class CIOConfig {", 1)
new_cfg = new_cfg.replace("public CConnectionConfig(SocketAddress", "public CIOConfig(SocketAddress", 1)
new_cfg = new_cfg.replace("Transport는 NIO.2로 고정되어 있으며 PacketExecutor / Client 공용 설정만 유지한다.", "Java Selector와 기존 CIO의 공용 설정. 두 설정 파일의 로드 시점을 유지한다.")
new_cfg = nest(new_cfg, """public static int loginTimeoutMs() { return TransportSettings.LOGIN_TIMEOUT_MS; }
public static int macLimit() { return TransportSettings.MAC_LIMIT; }

private static final class TransportSettings {
""" + transport_body.strip("\n") + "\n}")
assert "private static final class TransportSettings" in new_cfg
assert "public CIOConfig(SocketAddress" in new_cfg
assert new_cfg.count('new ConfigReader("config/Network.ini")') == 1
assert new_cfg.count('Path.of(System.getProperty("ctransport.config"') == 1
write(cfg, new_cfg)

original_frame = read(frame)
frame_body = original_frame[original_frame.index("/**"):]
assert frame_body.count("public final class CIOFrame") == 1
write(buf, nest(read(buf), frame_body.replace("public final class CIOFrame", "public static final class CIOFrame", 1)))

# Match code and imports, not comments or string literals. Keep historical
# archived fixture trees intact; only live network test sources are migrated.
lex = re.compile(r'//[^\r\n]*|/\*[\s\S]*?\*/|"""[\s\S]*?"""|"(?:\\[\s\S]|[^"\\])*"|\'(?:\\[\s\S]|[^\'\\])*\'')
def mask(s):
    return lex.sub(lambda m: re.sub(r"[^\r\n]", " ", m.group()), s)
def sub(s, pattern, replacement):
    for m in reversed(list(re.finditer(pattern, mask(s), re.M))):
        repl = replacement(m) if callable(replacement) else replacement
        s = s[:m.start()] + repl + s[m.end():]
    return s
def ensure_import(s, name):
    if re.search(r"(?m)^\s*import\s+" + re.escape(name) + r"\s*;", mask(s)):
        return s
    m = re.search(r"(?m)^package\s+[\w.]+\s*;", mask(s))
    if not m:
        return "import " + name + ";\n" + s
    return s[:m.end()] + "\n\nimport " + name + ";" + s[m.end():]
def dedup(s, name):
    found = list(re.finditer(r"(?m)^[ \t]*import[ \t]+"+re.escape(name)+r"[ \t]*;[ \t]*(?:\r?\n|$)", mask(s)))
    for m in reversed(found[1:]):
        s = s[:m.start()] + s[m.end():]
    return s

source = sorted(root.rglob("*.java"))
tests = sorted(p for d in Path("rkern/docs").glob("jproring_*") for p in d.rglob("*.java"))
changed = []
for p in source + tests:
    if p in (oldcfg, frame, cfg, buf):
        continue
    old = read(p)
    s = old
    # All old config type references, both qualified and simple.
    had_cfg = bool(re.search(r"\bCConnectionConfig\b", mask(s)))
    had_frame = bool(re.search(r"\bCIOFrame\b", mask(s)))
    if had_cfg:
        s = sub(s, re.escape(old_cfg_name) + r"\b", cfg_name)
        s = sub(s, r"\bCConnectionConfig\b", "CIOConfig")
        if not re.search(r"(?m)^\s*package\s+"+re.escape(cfg_name.rsplit(".",1)[0])+r"\s*;", mask(s)):
            s = ensure_import(s, cfg_name)
        s = dedup(s, cfg_name)
    if had_frame:
        s = sub(s, re.escape(old_frame_name) + r"\b", frame_name)
        s = ensure_import(s, frame_name)
        s = dedup(s, frame_name)
    # Preserve first-use timing of the original transport settings class.
    assert not re.search(r"(?m)^\s*import\s+static\s+"+re.escape(cfg_name)+r"\.(?:LOGIN_TIMEOUT_MS|MAC_LIMIT)\b", mask(s)), str(p)
    s = sub(s, r"\bCIOConfig\s*\.\s*LOGIN_TIMEOUT_MS\b", "CIOConfig.loginTimeoutMs()")
    s = sub(s, r"\bCIOConfig\s*\.\s*MAC_LIMIT\b", "CIOConfig.macLimit()")
    # Do not rewrite broadcast implementations or runtime protocol literals.
    if s != old:
        assert "/core/broadcast/" not in p.as_posix(), ("broadcast scope", str(p))
        write(p, s)
        changed.append(str(p))
    for lit in lex.finditer(s):
        if lit.group().startswith('"'):
            assert old_cfg_name not in lit.group() and old_frame_name not in lit.group(), ("reflection", str(p))

oldcfg.unlink()
frame.unlink()
for p in sorted(root.rglob("*.java")) + tests:
    s = mask(read(p))
    assert not re.search(r"\bCConnectionConfig\b", s), str(p)
    assert not re.search(re.escape(old_frame_name) + r"\b", s), str(p)
    assert not re.search(r"\bCIOConfig\s*\.\s*(?:LOGIN_TIMEOUT_MS|MAC_LIMIT)\b", s), str(p)
# The previous phase's deleted classes must have no live references.
for p in sorted(root.rglob("*.java")):
    s = mask(read(p))
    assert not re.search(r"\bCPacketCryptor\b|\bCUserSocketMap\b|l1j\.server\.server\.model\.network\.core\.server\.Server\b", s), str(p)
    if p.name != "CUserSocket.java":
        assert not re.search(r"\bCUserSocketReleaseTask\b", s), str(p)
    if p.name != "CThreadLocalTimer.java":
        assert not re.search(r"l1j\.server\.server\.model\.network\.thread\.CSlot\b", s), str(p)

print(json.dumps({"java_before":len(source), "java_after":len(list(root.rglob("*.java"))),
    "removed":[str(oldcfg),str(frame)], "modified":[str(cfg),str(buf),*changed],
    "broadcast_unchanged":True, "frame_object_lifetime_unchanged":True}, indent=2))
