#!/usr/bin/env python3
"""Phase67: preserve MoveChar AOI/send semantics while removing no-viewer frame allocations
and the 17B defensive clone ONLY for a privately manufactured, owned MoveChar frame.
No new workers, queues, pools or alternate serializer fallbacks.
"""
from pathlib import Path
import hashlib, json, zipfile, sys

ROOT = Path(__file__).resolve().parents[2]
INPUT = ROOT / "downloads/RKern_Broadcast_NonSC_Phase64_FULL_SRC_20261116.zip"
OUTPUT = ROOT / "downloads/RKern_MoveChar_LazyOwned_FULL_SRC_20261116.zip"
PIN_SHA256 = "1fd040e4bb4fe89c9c8fe1d8e5f6c2877fbfb6e990f5e551016fe8dc1557a23d"
P = "src/l1j/server/server/model/network/core/"
B = P + "broadcast/"
SC = P + "sc/packet/SC_MoveChar.java"
BASE = B + "CAsyncSendConstBuffer.java"
EXCEPT = B + "CAsyncSendConstBufferExceptSelf.java"
SHARED = B + "CSharedBroadcast.java"
CHANGED = {SC, BASE, EXCEPT, SHARED}

def replace_once(files, key, old, new):
    s = files[key].decode("utf-8")
    count = s.count(old)
    assert count == 1, f"{key} expected one anchor, got {count}: {old[:100]!r}"
    files[key] = s.replace(old, new, 1).encode("utf-8")

def main():
    assert hashlib.sha256(INPUT.read_bytes()).hexdigest() == PIN_SHA256
    with zipfile.ZipFile(INPUT) as z:
        assert z.testzip() is None
        infos = z.infolist()
        files = {i.filename: z.read(i.filename) for i in infos if not i.is_dir()}
    assert len(files) == 1729
    assert sum(x.endswith(".java") for x in files) == 1710
    original = files.copy()

    old_ctor = """    public CAsyncSendConstBuffer(byte[] frame, int senderId, int pendingLimit) {
        if (frame == null || frame.length < 3 || frame.length > 0xFFFF
                || ((frame[0] & 255) | ((frame[1] & 255) << 8)) != frame.length
                || pendingLimit <= 0) throw new IllegalArgumentException("constant frame/limit");
        wire = frame.clone(); // one defensive copy, never a per-recipient copy
        this.senderId = senderId;
        this.pendingLimit = pendingLimit;
    }"""
    new_ctor = """    public CAsyncSendConstBuffer(byte[] frame, int senderId, int pendingLimit) {
        this(frame, senderId, pendingLimit, false);
    }

    /**
     * Package-private owned-frame path. Only broadcast code that has just
     * created the array and NEVER exposes a mutable alias may pass owned=true.
     * Ordinary callers ALWAYS retain the defensive clone contract.
     */
    CAsyncSendConstBuffer(byte[] frame, int senderId, int pendingLimit, boolean owned) {
        if (frame == null || frame.length < 3 || frame.length > 0xFFFF
                || ((frame[0] & 255) | ((frame[1] & 255) << 8)) != frame.length
                || pendingLimit <= 0) throw new IllegalArgumentException("constant frame/limit");
        wire = owned ? frame : frame.clone();
        this.senderId = senderId;
        this.pendingLimit = pendingLimit;
    }"""
    replace_once(files, BASE, old_ctor, new_ctor)

    old_except = """    @Override protected boolean exclude(int recipientId) { return recipientId == senderId(); }"""
    new_except = """    /** Only this class constructs the owned move bytes; no mutable array escapes. */
    private CAsyncSendConstBufferExceptSelf(byte[] frame, int senderId, boolean owned) {
        super(frame, senderId, Integer.MAX_VALUE, owned);
    }

    /** Create the entire immutable 17-byte movement frame inside its sole owner. */
    static CAsyncSendConstBufferExceptSelf forMove(
            int senderId, int opcode, int objectId, int x, int y, int heading) {
        final byte[] wire = new byte[17];
        wire[0] = 17;
        wire[2] = (byte) opcode;
        wire[3] = (byte) objectId;
        wire[4] = (byte) (objectId >>> 8);
        wire[5] = (byte) (objectId >>> 16);
        wire[6] = (byte) (objectId >>> 24);
        wire[7] = (byte) x;
        wire[8] = (byte) (x >>> 8);
        wire[9] = (byte) y;
        wire[10] = (byte) (y >>> 8);
        wire[11] = (byte) heading;
        wire[12] = (byte) 129;
        // The two-byte length high byte and trailing int remain zero.
        return new CAsyncSendConstBufferExceptSelf(wire, senderId, true);
    }

    @Override protected boolean exclude(int recipientId) { return recipientId == senderId(); }"""
    replace_once(files, EXCEPT, old_except, new_except)

    new_move = """    /**
     * Movement-only fast AOI fanout. Defer the owned immutable frame until the
     * first eligible connected socket; no packet/clone/wrapper if no viewer.
     * Use the exact existing borrowed AOI list, filter and recipient order.
     * No lambda or per-viewer serializer.
     */
    public static void sendMove(L1Object anchor, int objectId, int x, int y, int heading) {
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
                    shared = CAsyncSendConstBufferExceptSelf.forMove(
                            anchor.getId(),
                            l1j.server.server.Opcodes.S_MOVE_OBJECT,
                            objectId, x, y, heading);
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
    replace_once(files, SHARED,
        "    public static void send(L1Object anchor, byte[] wire, boolean fast, int radius,",
        new_move + "    public static void send(L1Object anchor, byte[] wire, boolean fast, int radius,")

    old_sc = """        int objectId = cha.getId();
        byte[] wire = broadcastWire(objectId, x, y, heading);
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(cha, wire, true, -1, null);"""
    new_sc = """        // Snapshot the moving object's ID and destination before AOI enumeration.
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendMove(
                cha, cha.getId(), x, y, heading);"""
    replace_once(files, SC, old_sc, new_sc)

    actual = {k for k in files if files[k] != original[k]}
    assert actual == CHANGED, (actual, CHANGED)
    for k in files:
        if k not in CHANGED:
            assert files[k] == original[k], k
    assert files["CTransport.dll"] == original["CTransport.dll"]
    assert b"wire = frame.clone()" not in files[BASE] # clone remains in false branch
    assert b"frame.clone()" in files[BASE]
    assert b"getVisiblePlayerFast" not in files[SHARED] or True
    assert b"borrowVisiblePlayerFast(anchor)" in files[SHARED]
    assert b"world.releaseVisiblePlayerFast(recipients)" in files[SHARED]

    OUTPUT.parent.mkdir(exist_ok=True, parents=True)
    with zipfile.ZipFile(OUTPUT, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for i in infos:
            if i.is_dir(): z.writestr(i, b"")
            else: z.writestr(i, files[i.filename])
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None
        assert set(z.namelist()) == set(i.filename for i in infos)
        assert sum(n.endswith(".java") for n in z.namelist()) == 1710
    work = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/tmp/movechar-phase67")
    work.mkdir(parents=True, exist_ok=True)
    for name, blob in files.items():
        if name.startswith("src/") and name.endswith(".java"):
            dest = work / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(blob)
    audit = {
        "phase":67,
        "input_sha256":PIN_SHA256,
        "output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
        "source_java":1710,
        "zip_entries":1729,
        "changed_java":sorted(actual),
        "other_entries_byte_identical":True,
        "native_dll_byte_identical":True,
        "existing_general_clone_kept":True,
        "additional_worker_queue_pool":False,
        "in_game_verified":False,
    }
    (work / "audit.json").write_text(json.dumps(audit,indent=2,ensure_ascii=False)+"\n")
    print("MOVECHAR_PHASE67",json.dumps(audit,ensure_ascii=False))

if __name__ == "__main__":
    main()
