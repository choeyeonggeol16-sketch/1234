"""Compare Java shared frames with the existing C++ argument layouts."""
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
NATIVE = (ROOT / "native/CTransport.cpp").read_text(encoding="utf-8")
FORMATS = NATIVE[NATIVE.index("CIO_API void* L1Interop_CreateFormattedConstBuffer("):
                 NATIVE.index("CIO_API void* L1Interop_CreateWritableConstBuffer(")]
LAYOUTS = {}
for condition, values, widths in re.findall(
    r'if\((kind==[\d |kind=]+)\)\{[^\n]*\n\s*const std::uint64_t args\[\d+\]=\{([^}]+)\};'
    r'\s*return AssemblePooled\(server,"([chd]+)",args,\d+\);', FORMATS
):
    for kind in re.findall(r"kind==(\d+)", condition):
        LAYOUTS[int(kind)] = [part.strip() for part in values.split(",")], widths
assert set(LAYOUTS) == set(range(1, 43))


def value(expr, args):
    cast = re.fullmatch(r"static_cast<unsigned>\((\w+)\)", expr)
    if cast:
        return args[cast[1]] & 0xFFFFFFFF
    condition = re.fullmatch(r"\(?\s*(\w+)\s*(?:==\s*(\d+))?\s*\?\s*([^:]+):\s*([^)]*)\)?", expr)
    if condition:
        name, match, yes, no = condition.groups()
        enabled = args[name] == int(match) if match is not None else args[name] != 0
        return value(yes.strip() if enabled else no.strip(), args)
    return int(expr.removesuffix("u"), 0)


JAVA = '''
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;
import l1j.server.server.model.network.core.broadcast.CSharedPacketFormat;
class SharedFormatVectors {
    static int parameter(int sample, int position) {
        if (sample < 3) return sample;
        return (sample * 0x7f4a7c15) ^ (position * 0x61c88647);
    }
    public static void main(String[] args) {
        ByteBuffer out = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        for (int sample = 0; sample < 24; sample++) {
            int[] p = new int[12];
            for (int i = 0; i < p.length; i++) p[i] = parameter(sample, i);
            for (int kind = 1; kind <= 42; kind++) {
                out.clear();
                out.position(2);
                if (kind <= 30) CSharedPacketFormat.writeFormatted(out, kind, p[0], p[1], p[2], p[3], p[4]);
                else CSharedPacketFormat.writeExtended(out, kind, p[0], p[1], p[2], p[3], p[4], p[5],
                        p[6], p[7], p[8], p[9], p[10], p[11]);
                out.putShort(0, (short) out.position());
                System.out.println(HexFormat.of().formatHex(out.array(), 0, out.position()));
            }
        }
    }
}
'''

with tempfile.TemporaryDirectory() as tmp:
    source = Path(tmp, "SharedFormatVectors.java")
    source.write_text(JAVA, encoding="utf-8")
    packet_format = ROOT / "src/l1j/server/server/model/network/core/broadcast/CSharedPacketFormat.java"
    subprocess.run([os.environ.get("JAVAC", "javac"), "-d", tmp, str(source), str(packet_format)], check=True)
    actual = subprocess.check_output([os.environ.get("JAVA", "java"), "-cp", tmp, "SharedFormatVectors"], text=True).splitlines()

expected = []
for sample in range(24):
    p = [sample if sample < 3 else ((sample * 0x7F4A7C15) ^ (position * 0x61C88647)) & 0xFFFFFFFF
         for position in range(12)]
    args = dict(zip(("opcode", "objectId", "a", "b", "c"), p))
    args.update({f"p{i}": p[i + 1] for i in range(11)})
    for kind in range(1, 43):
        args["kind"] = kind
        values, format_chars = LAYOUTS[kind]
        assert len(values) == len(format_chars)
        data = bytearray()
        for expr, shape in zip(values, format_chars):
            width = {"c": 1, "h": 2, "d": 4}[shape]
            data += (value(expr, args) & ((1 << (8 * width)) - 1)).to_bytes(width, "little")
        expected.append(((len(data) + 2).to_bytes(2, "little") + data).hex())

assert len(actual) == len(expected)
for index, (received, want) in enumerate(zip(actual, expected)):
    assert received == want, (index // 42, index % 42 + 1, received, want)
print(f"PASS wire layouts={len(LAYOUTS)} vectors={len(expected)}")
