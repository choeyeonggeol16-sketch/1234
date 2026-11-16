#!/usr/bin/env python3
from pathlib import Path
import zipfile,hashlib
root=Path(__file__).resolve().parents[2]
source=root/"downloads/RKern_NC_SPacket_Assemble_Phase77_FULL_SRC_20261116.zip"
assert hashlib.sha256(source.read_bytes()).hexdigest()=="c9c0eb7dd2ac7dc0833fb8b7ed87c0623c141f6420d79429fe8d993ab0637fb3"
out=root/"reports/nc_phase78_preflight";out.mkdir(exist_ok=True,parents=True)
with zipfile.ZipFile(source) as z:
    for name in z.namelist():
        if name.endswith("/WriteBuffer.java") or name.endswith("/SC_WorldPutObject.java"):
            (out/Path(name).name).write_bytes(z.read(name))
