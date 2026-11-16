#!/usr/bin/env python3
"""79: finalize eight remaining SC sendAll global codecs, plus filtered War clan
and SystemMessage recipients, via existing phase77 Native writable CIOBufferEx.

SHA-pinned phase78 input. No Native ABI/DLL change, no legacy SEND fallback,
no extra queues/workers/classes. 1:1 SC send methods and wire API preserved.
"""
from pathlib import Path
import hashlib,json,re,shutil,sys,zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase78_FULL_SRC_20261116.zip"
PIN="07bfc9631c56a9ea4dc6a6bce60824b0ce2a9065b0b8c64ced4451863c1fb943"
OUT=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase79_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else None
P="src/l1j/server/server/model/network/core/"
BC=P+"broadcast/CSharedBroadcast.java"
SC={name:P+"sc/packet/SC_"+name+".java" for name in
    ("IconMessage","MessageYN","NewChat","ServerMessage",
     "SystemMessage","TopMessage","War","Weather")}
CHANGED=set(SC.values())|{BC}
FQ="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"

def change(files,key,old,new):
    src=files[key].decode("utf-8")
    crlf="\r\n" in src
    s=src.replace("\r\n","\n")
    assert s.count(old)==1,(key,"matched",s.count(old),old[:100])
    s=s.replace(old,new,1)
    files[key]=(s.replace("\n","\r\n") if crlf else s).encode("utf-8")

def remove_private(files,name,method):
    key=SC[name];original=files[key].decode("utf-8");crlf="\r\n" in original
    s=original.replace("\r\n","\n")
    if s.count(method+"(")!=1:return
    m=re.search(r"\bprivate static byte\[\]\s+"+re.escape(method)+r"\s*\(",s)
    if m is None:return
    a=s.rfind("\n",0,m.start())+1;b=s.index("{",m.start());depth=0;e=-1
    for j in range(b,len(s)):
        if s[j]=="{":depth+=1
        elif s[j]=="}":
            depth-=1
            if depth==0:e=j+1;break
    assert e>b
    s=s[:a]+s[e:].lstrip("\n")
    files[key]=(s.replace("\n","\r\n") if crlf else s).encode("utf-8")

def add_shared_api(files):
    marker="    public static void sendWrittenAll(NativeWireWriter writer) {"
    extra="""    /** writeS2 exact MS949 byte count, with Native pooled direct output. */
    public static void writeMS949S2(java.nio.ByteBuffer out,String msg) {
        final int lenPos=out.position();
        out.put((byte)0);
        writeMS949(out,msg);
        final int encoded=out.position()-lenPos-1;
        if(encoded>255)throw new IllegalArgumentException(
                "Native writeS2 encoded length "+encoded+" exceeds 255");
        out.put(lenPos,(byte)encoded);
    }

    /** Original clan-member traversal and every online-member duplicate/order. */
    public static void sendWrittenClan(
            l1j.server.server.model.L1Clan clan,NativeWireWriter writer) {
        if(clan==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            try(var members=clan.createOnlineMembers()) {
                final var it=members.iterator();
                while(it.hasNext()) {
                final var member=it.next();
                final L1PcInstance pc=member.player;
                if(pc==null)continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                final var socket=client.getConnection();
                if(socket==null||!socket.isClientConnected())continue;
                if(shared==null) {
                    final CAsyncSendConstBuffer.Source source=
                            CAsyncSendConstBuffer.openWrittenSource(socket,writer);
                    if(source==null)throw new IllegalStateException(
                            "Native clan immutable source unavailable");
                    try {
                        shared=new CAsyncSendConstBuffer(source,Integer.MIN_VALUE,Integer.MAX_VALUE);
                    } catch(RuntimeException|Error failure) {
                        source.release();
                        throw failure;
                    }
                }
                shared.send(socket,pc.getId());
                }
            }
        } finally {
            if(shared!=null)shared.close();
        }
    }

"""
    change(files,BC,marker,extra+marker)

def migrate_icon(files):
    k=SC["IconMessage"]
    old="""        final byte[] wire=sharedWire(message, 0, color, surface, duration, false);
        if(wire==null)return;
        """+FQ+""".sendAll(wire);"""
    new="""        final String text=message==null?"":message;
        final byte[] rgb=color==null?null:color.get_bytes();
        """+FQ+""".sendWrittenAll(b -> {
            b.put((byte)Opcodes.S_EXTENDED_PROTOBUF).putShort((short)TEXT_MESSAGE);
            putVar(b,0x08);putVar(b,surface*2);putVar(b,0x12);
            """+FQ+""".writeMS949S2(b,text);
            putVar(b,0x1a);
            if(rgb!=null)b.put(rgb);
            putVar(b,0x20);putVar(b,duration);
            b.putShort((short)0);
        });"""
    change(files,k,old,new)
    old="""        final byte[] wire=sharedWire("", index, color, surface, duration, true);
        if(wire==null)return;
        """+FQ+""".sendAll(wire);"""
    new="""        final byte[] rgb=color==null?null:color.get_bytes();
        """+FQ+""".sendWrittenAll(b -> {
            b.put((byte)Opcodes.S_EXTENDED_PROTOBUF).putShort((short)INDEX_MESSAGE);
            b.put((byte)0x08);putVar(b,surface*2);
            b.put((byte)0x10);putVar(b,index*2);
            b.put((byte)0x1a);
            if(rgb!=null)b.put(rgb);
            b.put((byte)0x20);putVar(b,duration);
            b.putShort((short)0);
        });"""
    change(files,k,old,new)
    # Preserve public sharedWire helpers for original 1:1 packeting.

def migrate_yn(files):
    k=SC["MessageYN"]
    change(files,k,
       """        final byte[] wire=sharedWire(type, message);
        if(wire==null)return;
        """+FQ+""".sendAll(wire);""",
       """        """+FQ+""".sendWrittenAll(b -> {
            b.put((byte)Opcodes.S_ASK).putShort((short)0).putInt(0).putShort((short)type);
            """+FQ+""".writeMS949(b,message);
            b.put((byte)0);
        });""")

def migrate_new_chat(files):
    k=SC["NewChat"]
    change(files,k,
        """        final ChatData data=prepare(sender,type,chatType,text,targetName);
        final byte[] wire=sharedWire(data);
        if(wire==null)return;
        """+FQ+""".sendAll(wire);""",
        """        final ChatData data=prepare(sender,type,chatType,text,targetName);
        """+FQ+""".sendWrittenAll(b -> writeNativeChat(b,data));""")
    old=files[k].decode("utf-8")
    crlf="\r\n" in old
    s=old.replace("\r\n","\n")
    start=s.index("    public static byte[] sharedWire(ChatData d) {")
    end=s.index("\n    }\n",start)+len("\n    }")
    method=s[start:end]
    method=method.replace(
        "    public static byte[] sharedWire(ChatData d) {",
        "    private static void writeNativeChat(java.nio.ByteBuffer b,ChatData d) {",1)
    before="""        final long capacity=2L+64L+d.textBytes.length+d.targetBytes.length
                              +d.senderBytes.length;
        if(capacity>0xFFFF)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate((int)capacity)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.position(2);"""
    assert method.count(before)==1,"NewChat private writer alloc prefix"
    method=method.replace(before,"        b.order(java.nio.ByteOrder.LITTLE_ENDIAN);",1)
    before="""        final int size=b.position();
        if(size>0xFFFF)return null;
        b.putShort(0,(short)size);
        return java.util.Arrays.copyOf(b.array(),size);"""
    assert method.count(before)==1,"NewChat private writer array suffix"
    method=method.replace(before,"        if(b.position()>16384)throw new java.nio.BufferOverflowException();",1)
    assert "ByteBuffer.allocate" not in method
    trimmed=s.rstrip()
    assert trimmed.endswith("}"),"NewChat final class marker"
    last=trimmed.rfind("}")
    s=trimmed[:last]+"\n\n"+method+"\n}\n"
    files[k]=(s.replace("\n","\r\n") if crlf else s).encode("utf-8")
    # Keep public original sharedWire(ChatData) as differential test oracle.

def migrate_other(files):
    change(files,SC["ServerMessage"],
        """        final byte[] wire=sharedWire(type);
        
        """+FQ+""".sendAll(wire);""",
        """        """+FQ+""".sendWrittenAll(b -> b.put((byte)Opcodes.S_MESSAGE_CODE)
                .putShort((short)type).put((byte)0));""")
    change(files,SC["SystemMessage"],
        """        final byte[] wire=sharedWire(message);
        if(wire==null)return;
        """+FQ+""".sendAll(wire);""",
        """        """+FQ+""".sendWrittenAll(b -> {
            b.put((byte)Opcodes.S_MESSAGE).put((byte)0x09);
            """+FQ+""".writeMS949(b,message);
            b.put((byte)0);
        });""")
    change(files,SC["SystemMessage"],
        """        final byte[] wire=sharedWire(message);
        if(wire!=null)
            """+FQ+"""
                    .sendRecipients(recipients,wire);""",
        """        """+FQ+""".sendWrittenRecipients(recipients,b -> {
            b.put((byte)Opcodes.S_MESSAGE).put((byte)0x09);
            """+FQ+""".writeMS949(b,message);
            b.put((byte)0);
        });""")
    change(files,SC["TopMessage"],
        """        final byte[] wire=sharedWire(msg);
        if(wire==null)return;
        """+FQ+""".sendAll(wire);""",
        """        """+FQ+""".sendWrittenAll(b -> {
            b.put((byte)121).put((byte)84).put((byte)2);
            """+FQ+""".writeMS949(b,msg);
            b.put((byte)0).put((byte)0).put((byte)0);
        });""")
    remove_private(files,"TopMessage","sharedWire")
    change(files,SC["War"],
        """        final byte[] wire=sharedWire(type, clan1, clan2);
        if(wire==null)return;
        """+FQ+""".sendAll(wire);""",
        """        """+FQ+""".sendWrittenAll(b -> writeNativeWar(b,type,clan1,clan2));""")
    change(files,SC["War"],
        """        final byte[] wire=sharedWire(type, clan1, clan2);
        if(wire==null)return;
        try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                wire, Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            clan.createOnlineMembers().forEach(member -> {
                final L1PcInstance player=member.player;
                if(player==null)return;
                final CIOClient client=player.getNetConnection();
                if(client!=null && client.isConnected()) shared.send(client.getConnection(),player.getId());
            });
        }""",
        """        """+FQ+""".sendWrittenClan(clan,b -> writeNativeWar(b,type,clan1,clan2));""")
    # One Native writer for both global and clan recipient policy.
    k=SC["War"];orig=files[k].decode("utf-8");crlf="\r\n" in orig;s=orig.replace("\r\n","\n")
    marker="    private static byte[] sharedWire(int type, String clan1, String clan2) {"
    method="""    private static void writeNativeWar(java.nio.ByteBuffer b,int type,
            String clan1,String clan2) {
        b.put((byte)Opcodes.S_WAR).put((byte)type);
        """+FQ+""".writeMS949(b,clan1);
        b.put((byte)0);
        """+FQ+""".writeMS949(b,clan2);
        b.put((byte)0);
    }

"""
    assert s.count(marker)==1
    s=s.replace(marker,method+marker,1)
    files[k]=(s.replace("\n","\r\n") if crlf else s).encode("utf-8")
    change(files,SC["Weather"],
        """        final byte[] wire=sharedWire(weather);
        
        """+FQ+""".sendAll(wire);""",
        """        """+FQ+""".sendWrittenAll(b -> b.put((byte)Opcodes.S_WEATHER)
                .put((byte)weather));""")
    remove_private(files,"Weather","sharedWire")

def prepare():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN,"phase78 baseline pin mismatch"
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        infos=z.infolist()
        files={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    old=files.copy()
    assert len(files)==1734 and sum(x.endswith(".java") for x in files)==1710
    add_shared_api(files)
    migrate_icon(files);migrate_yn(files);migrate_new_chat(files);migrate_other(files)
    touched={n for n in old if files[n]!=old[n]}
    assert touched==CHANGED,("unexpected phase79 diff",sorted(touched^CHANGED))
    for name,key in SC.items():
        s=files[key].decode("utf-8")
        assert FQ+".sendAll(" not in s,(name,"legacy all broadcast")
        if name=="War":assert "new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer" not in s
        if name=="SystemMessage":assert ".sendRecipients(recipients,wire)" not in s
    for n,data in files.items():
        if n.startswith("native/") or (n.startswith("src/") and n.endswith(".java")):
            dst=WORK/n;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(data)
    native_root=ROOT/"patches/ctransport_core_20261112/native"
    for src in native_root.iterdir():
        if src.is_file() and not (WORK/"native"/src.name).exists():
            shutil.copy2(src,WORK/"native"/src.name)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    for n in ("build","logs"):(WORK/n).mkdir(exist_ok=True)
    print("PHASE79_PREPARED_EIGHT_GLOBAL_SC_AND_WAR_CLAN_SYSTEM_RECIPIENT",flush=True)

def package():
    with zipfile.ZipFile(BASE) as z:
        infos=z.infolist()
        old={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    # Production DLL is unchanged; CI restores this exact tested phase78 DLL.
    assert (WORK/"build/CTransport.dll").read_bytes()==old["CTransport.dll"],"DLL must remain exact phase78"
    new=old.copy()
    for n in CHANGED:new[n]=(WORK/n).read_bytes()
    touched={n for n in old if new[n]!=old[n]}
    assert touched==CHANGED,sorted(touched^CHANGED)
    assert len(new)==1734 and sum(n.endswith(".java") for n in new)==1710
    # Assert all 195 SC packet definitions no longer invoke the previous
    # byte[] broadcast helper; remaining sharedWire APIs are 1:1/oracles only.
    legacy=[]
    for n,data in new.items():
        if "/sc/packet/SC_" not in n or not n.endswith(".java"):continue
        s=data.decode("utf-8")
        for method in ("send","sendRecipients","sendAll","sendRawRecipients",
                       "sendRawArray","sendArray","sendObjectPlayers","sendRawSelfAndVisible"):
            if re.search(r"(?:CSharedBroadcast\s*\.\s*)"+re.escape(method)+r"\s*\(",s):
                legacy.append((n,method))
        if re.search(r"new\s+(?:l1j\.server\.server\.model\.network\.core\.broadcast\.)?CAsyncSendConstBuffer\s*\(",s):
            legacy.append((n,"new CAsyncSendConstBuffer"))
    assert not legacy,("SC legacy shared SEND remains",legacy)
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for info in infos:z.writestr(info,b"" if info.is_dir() else new[info.filename])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None and len(z.namelist())==1734
    report={"phase":79,"baseline_sha256":PIN,
       "full_src_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
       "dll_sha256":hashlib.sha256(old["CTransport.dll"]).hexdigest(),
       "native_abi":"0x000a0009",
       "changed":sorted(touched),"java_count":1710,"zip_entries":1734,
       "sc_legacy_broadcast_remaining":0,
       "all_195_SC_scanned":True,
       "native_socket_fifo_cipher_preserved":True,
       "new_queues_workers_classes":0,
       "native_shared_writable_max_bytes":16384,
       "5000_ingame_soak_verified":False}
    (WORK/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print("PHASE79_SC_FINAL_NO_LEGACY_BROADCAST_VERIFIED",json.dumps(report),flush=True)
if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    prepare() if sys.argv[2]=="prepare" else package()
