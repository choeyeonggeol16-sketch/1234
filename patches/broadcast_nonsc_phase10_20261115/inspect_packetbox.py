#!/usr/bin/env python3
import pathlib,zipfile,hashlib
root=pathlib.Path(__file__).resolve().parents[2]
file=root/"downloads/RKern_Broadcast_NonSC_Phase9_FULL_SRC_20261115.zip"
raw=file.read_bytes()
sha=hashlib.sha1(b"blob "+str(len(raw)).encode()+b"\0"+raw).hexdigest()
assert sha=="41714f4421c439aecb00f941abd7bc1e6b94ca0b"
name="src/l1j/server/server/model/network/core/sc/packet/SC_PacketBox.java"
with zipfile.ZipFile(file) as z:
 assert z.testzip() is None
 lines=z.read(name).decode("utf8").splitlines()
 out=[f"FILE {name}, lines={len(lines)}\n"]
 for lo,hi in [(1,110),(140,330),(330,500),(500,700),(700,860),(860,1039)]:
  out.append(f"======== {lo}-{hi} ========\n")
  out.extend(f"{i}: {lines[i-1]}\n" for i in range(lo,min(len(lines),hi)+1))
dest=root/"reports/broadcast_nonsc_phase10_20261115/PACKETBOX.txt"
dest.parent.mkdir(parents=True,exist_ok=True)
dest.write_text("".join(out),encoding="utf8")
print("PHASE10_PACKETBOX_OK",dest.stat().st_size)