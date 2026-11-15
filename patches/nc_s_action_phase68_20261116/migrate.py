#!/usr/bin/env python3
"""Phase68: NC-inspired 8-byte S_ACTION shared broadcast on phase67 source.

Actual NC evidence: 0x70429C Assemble, 0x41F694 pooled one-time format,
0x9241F8 ExceptSelf shared ref, 0x45C7AC serialized SEND, 0x90E6C4
per-socket encrypt/copy. This changes ONLY L1 S_ACTION packet creation and
fanout. The L1 opcode/wire fields/FIFO/native ownership remain unchanged.
"""
from pathlib import Path
import hashlib,json,sys,zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_MoveChar_LazyOwned_FULL_SRC_20261116.zip"
OUT=ROOT/"downloads/RKern_NC_SAction_Shared_FULL_SRC_20261116.zip"
PIN="81185c238e8342ca22b83045d4c3ad2ea70551c1dfba9dd5d92b574e81c9814d"
PREFIX="src/l1j/server/server/model/network/core/"
ACTION=PREFIX+"sc/packet/SC_DoActionGFX.java"
FUNCTOR=PREFIX+"broadcast/CAsyncSendConstBufferExceptSelf.java"
BROADCAST=PREFIX+"broadcast/CSharedBroadcast.java"
CHANGED={ACTION,FUNCTOR,BROADCAST}

def replace_once(d,key,old,new):
    text=d[key].decode("utf-8")
    n=text.count(old)
    assert n==1,(key,n,old[:110])
    d[key]=text.replace(old,new,1).encode("utf-8")

def main():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        infos=z.infolist()
        files={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    assert len(files)==1729
    assert sum(p.endswith(".java") for p in files)==1710
    original=files.copy()
    anchor="""    @Override protected boolean exclude(int recipientId) { return recipientId == senderId(); }"""
    owned_action="""    /**
     * NC-inspired once-assembled 8-byte L1 S_ACTION plaintext. The mutable
     * array is created here and never exposed to the caller. Only the existing
     * internal owned-frame constructor receives it, so there is no clone.
     * Native/Java SEND consumers retain their pre-existing FIFO/lease contract.
     */
    static CAsyncSendConstBufferExceptSelf forAction(
            int senderId, int opcode, int objectId, int actionId) {
        final byte[] wire = new byte[8];
        wire[0] = 8;
        wire[2] = (byte) opcode;
        wire[3] = (byte) objectId;
        wire[4] = (byte) (objectId >>> 8);
        wire[5] = (byte) (objectId >>> 16);
        wire[6] = (byte) (objectId >>> 24);
        wire[7] = (byte) actionId;
        // wire[1] is the zero high byte of the 2-byte framing header.
        return new CAsyncSendConstBufferExceptSelf(wire, senderId, true);
    }

"""
    replace_once(files,FUNCTOR,anchor,owned_action+anchor)
    start="""    public static void sendMove(L1Object anchor, int objectId, int x, int y, int heading) {"""
    action_loop="""    /**
     * NC-style action S-packet: one immutable 8-byte plaintext per event,
     * shared by all eligible sockets, each socket encrypts in its own FIFO.
     * A 0-recipient event creates no packet array/clone/shared wrapper.
     */
    public static void sendAction(L1Object anchor, int objectId, int actionId) {
        if (anchor == null) return;
        final L1World world = L1World.getInstance();
        final List<L1PcInstance> recipients = world.borrowVisiblePlayerFast(anchor);
        CAsyncSendConstBufferExceptSelf shared = null;
        try {
            for (L1PcInstance pc : recipients) {
                if (!CTLMFilter.sameWorldOther(anchor, pc)) continue;
                final CIOClient client = pc.getNetConnection();
                if (client == null || !client.isConnected()) continue;
                final var socket = client.getConnection();
                if (shared == null) {
                    if (socket == null || !socket.isClientConnected()) continue;
                    shared = CAsyncSendConstBufferExceptSelf.forAction(
                            objectId, l1j.server.server.Opcodes.S_ACTION,
                            objectId, actionId);
                }
                shared.send(socket, pc.getId());
            }
        } finally {
            try {
                if (shared != null) shared.close();
            } finally {
                world.releaseVisiblePlayerFast(recipients);
            }
        }
    }

"""
    replace_once(files,BROADCAST,start,action_loop+start)
    old="""        final int objectId = cha.getId();
        final byte[] wire = java.nio.ByteBuffer.allocate(8)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).putShort((short) 8)
                .put((byte) l1j.server.server.Opcodes.S_ACTION)
                .putInt(objectId).put((byte) actionId).array();
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(cha, wire, true, -1, null);"""
    new="""        // NC-style one owned plaintext frame, fanout with existing L1 AOI/FIFO.
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAction(
                cha, cha.getId(), actionId);"""
    replace_once(files,ACTION,old,new)
    touched={n for n in files if files[n]!=original[n]}
    assert touched==CHANGED,(touched,CHANGED)
    assert all(files[n]==original[n] for n in files if n not in CHANGED)
    assert files["CTransport.dll"]==original["CTransport.dll"]
    assert b"sendMove(" in files[BROADCAST] and b"sendAction(" in files[BROADCAST]
    assert b"frame.clone()" in files[PREFIX+"broadcast/CAsyncSendConstBuffer.java"]
    assert b"forMove(" in files[FUNCTOR] and b"forAction(" in files[FUNCTOR]
    OUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUT,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for i in infos:
            z.writestr(i, b"" if i.is_dir() else files[i.filename])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None
        assert z.namelist()==[i.filename for i in infos]
    work=Path(sys.argv[1]) if len(sys.argv)>1 else Path("/tmp/rkern-nc-s-action68")
    work.mkdir(parents=True,exist_ok=True)
    for name,blob in files.items():
        if name.startswith("src/") and name.endswith(".java"):
            dest=work/name
            dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(blob)
    audit={
        "phase":68,
        "nc_reference":"L2Server(10).exe 0x41F694/0x70429C/0x9241F8/0x45C7AC/0x90E6C4",
        "previous_sha256":PIN,
        "output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
        "entries":len(files),
        "java_count":1710,
        "changed":sorted(touched),
        "unchanged_identical":True,
        "native_dll_identical":True,
        "existing_movechar_67_kept":True,
        "legacy_fallback":False,
        "new_worker_queue_pool":False,
        "nc_native_slab_emulated":False
    }
    (work/"audit.json").write_text(json.dumps(audit,ensure_ascii=False,indent=2)+"\n")
    print("PHASE68_NC_SACTION",json.dumps(audit,ensure_ascii=False))

if __name__=="__main__":main()
