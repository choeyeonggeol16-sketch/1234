#!/usr/bin/env python3
"""Phase37: the already immutable KDA snapshot is encoded into ONE wire per
refresh, then fanned out via native const buffer. The login direct send remains
unchanged. Preserve updater selection, ordering, chart snapshot and lifecycle.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase36_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase37_FULL_SRC_20261115.zip"
PIN="67e62f17ccf68ffa310c5846d0d178f5ad9edef4"
BASE="src/l1j/server/"
CHART=BASE+"server/model/network/core/sc/packet/SC_ChartRank.java"
UPDATER=BASE+"MJKDASystem/Chart/MJKDAChartUpdator.java"
CHANGED=sorted([CHART,UPDATER])
FQ="l1j.server.server.model.network.core.broadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def bounds(s,sig):
 assert s.count(sig)==1,(sig,s.count(sig))
 a=s.index(sig);br=s.index("{",a);lvl=0
 for i in range(br,len(s)):
  if s[i]=="{":lvl+=1
  elif s[i]=="}":
   lvl-=1
   if lvl==0:return a,i+1,s[a:i+1]
 raise AssertionError("bad braces "+sig)
WIRE=StringPlaceholder=None
WIRE=r'''
    /** Freeze the exact existing direct SC_BASECAMP_CHART_NOTI_PACKET
     * payload once for a chart update rather than rewriting the same
     * Snapshot fields for every eligible viewer.
     */
    public static byte[] sharedWire(Snapshot snapshot) {
        if(snapshot==null || snapshot.wireSize>0xffff)return null;
        final java.nio.ByteBuffer out=java.nio.ByteBuffer.allocate(snapshot.wireSize)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        out.position(2);
        out.put((byte)Opcodes.S_EXTENDED_PROTOBUF);
        out.putShort((short)SC_BASECAMP_CHART_NOTI_PACKET);
        if(snapshot.zero){
            out.put((byte)0x10);
            out.put((byte)0);
            out.putShort((short)0);
        } else {
            for(byte[] payload:snapshot.serialized){
                out.put((byte)0x0a);
                putBit(out,payload.length);
                out.put(payload);
            }
            out.put((byte)0x10);
            putBit(out,snapshot.serverPoint);
            out.putShort((short)0);
        }
        if(out.position()!=snapshot.wireSize)
            throw new IllegalStateException("chart packet size mismatch");
        out.putShort(0,(short)snapshot.wireSize);
        return out.array();
    }

    /** WriteBuffer.writeBit uses 32-bit unsigned semantics for negatives. */
    private static void putBit(java.nio.ByteBuffer out,long value){
        if(value<0L)value &= 0xffffffffL;
        while((value & ~0x7fL)!=0L){
            out.put((byte)((value & 0x7fL)|0x80L));
            value >>>= 7;
        }
        out.put((byte)value);
    }

'''
def patch(files):
 s=files[CHART].decode("utf8")
 anchor="    public static boolean send(L1PcInstance pc, Snapshot snapshot) {"
 assert s.count(anchor)==1
 for token in ("out.writeC(Opcodes.S_EXTENDED_PROTOBUF);",
               "out.writeH(SC_BASECAMP_CHART_NOTI_PACKET);",
               "out.writeBit(bytes.length);",
               "out.writeByte(bytes);",
               "out.writeBit(snapshot.serverPoint);",
               "out.writeH(0);"):
  assert token in s,(CHART,token)
 assert "public static byte[] sharedWire(Snapshot snapshot)" not in s
 s=s.replace(anchor,WIRE+anchor,1)
 files[CHART]=s.encode("utf8")
 s=files[UPDATER].decode("utf8")
 old='''if(_pck != null){
\t\t\t\t\tCollection<L1PcInstance> cols = L1World.getInstance().getAllPlayers();
\t\t\t\t\tfor (L1PcInstance pc : cols) {
\t\t\t\t\t\tif (pc == null || pc.getKDA() == null || !pc.getKDA().isChartView)
\t\t\t\t\t\t\tcontinue;
\t\t\t\t\t\t
\t\t\t\t\t\tSC_ChartRank.send(pc, _pck);
\t\t\t\t\t}
\t\t\t\t}'''
 assert s.count(old)==1,(UPDATER,"broadcast loop changed")
 new='''if(_pck != null){
                    Collection<L1PcInstance> cols=L1World.getInstance().getAllPlayers();
                    final byte[] wire=SC_ChartRank.sharedWire(_pck);
                    if(wire!=null){
                        try(var shared=new '''+FQ+'''.CAsyncSendConstBuffer(
                                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)){
                            for(L1PcInstance pc:cols){
                                if(pc==null || pc.getKDA()==null || !pc.getKDA().isChartView)
                                    continue;
                                '''+FQ+'''.CSharedBroadcast.sendRecipient(shared,pc);
                            }
                        }
                    }
                }'''
 s=s.replace(old,new,1)
 assert "public void onLoginUser(L1PcInstance pc)" in s
 assert "SC_ChartRank.send(pc, _pck);" in s
 assert "if (!_disposed) updateCal();" in s
 assert "synchronized (MJKDAChartUpdator.this)" in s
 files[UPDATER]=s.encode("utf8")
 return {"kda_shared_chart_broadcast":1,
         "kda_direct_login_send_unchanged":True,
         "kda_snapshot_bytes_once_per_refresh":True,
         "kda_chart_view_filter_retained":True,
         "kda_viewer_iteration_order_retained":True,
         "kda_updater_lifecycle_unmodified":True,
         "kda_varint_wire_preflight_in_place":True}
TEST=r'''import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_ChartRank;
public final class Phase37KdaChartParity {
 static int checks;
 static final Constructor<SC_ChartRank.Snapshot> CTOR;
 static {
  try {
   CTOR=SC_ChartRank.Snapshot.class.getDeclaredConstructor(byte[][].class,int.class,boolean.class);
   CTOR.setAccessible(true);
  } catch (Exception err){throw new ExceptionInInitializerError(err);}
 }
 static SC_ChartRank.Snapshot snapshot(byte[][] fields,int points,boolean zero) {
  try {return CTOR.newInstance(fields,points,zero);}
  catch(Exception err){throw new RuntimeException(err);}
 }
 static void putVar(ByteArrayOutputStream b,long value){
  if(value<0L)value &= 0xffffffffL;
  while((value & ~0x7fL)!=0L){
   b.write((int)((value&0x7fL)|0x80L));value>>>=7;
  }
  b.write((int)value);
 }
 static byte[] expected(byte[][] rows,int points,boolean zero){
  ByteArrayOutputStream b=new ByteArrayOutputStream(512);
  b.write(0);b.write(0);b.write(Opcodes.S_EXTENDED_PROTOBUF);
  b.write(0x7f);b.write(0);
  if(zero){
   b.write(0x10);b.write(0);b.write(0);b.write(0);
  } else {
   for(byte[] row:rows){
    b.write(0x0a);putVar(b,row.length);b.write(row,0,row.length);
   }
   b.write(0x10);putVar(b,points);b.write(0);b.write(0);
  }
  byte[] result=b.toByteArray();
  if(result.length>0xffff)return null;
  result[0]=(byte)result.length;result[1]=(byte)(result.length>>>8);
  return result;
 }
 static void check(byte[][] rows,int points,boolean zero){
  SC_ChartRank.Snapshot sn=snapshot(rows,points,zero);
  byte[] a=SC_ChartRank.sharedWire(sn);
  byte[] b=expected(rows,points,zero);
  if(!Arrays.equals(a,b))throw new AssertionError("chart snapshot byte parity "+checks);
  checks++;
 }
 public static void main(String[] args) {
  if(SC_ChartRank.sharedWire(null)!=null)throw new AssertionError("null");
  if(!Arrays.equals(SC_ChartRank.sharedWire(SC_ChartRank.getZero()),
      expected(new byte[0][],0,true)))throw new AssertionError("ZERO singleton");
  check(new byte[0][],0,true);check(new byte[0][],0,false);
  for(int n:new int[]{0,1,-1,127,128,255,256,16383,16384,65535,Integer.MIN_VALUE,Integer.MAX_VALUE}){
   check(new byte[][]{new byte[0]},n,false);
   check(new byte[][]{new byte[127]},n,false);
   check(new byte[][]{new byte[128]},n,false);
   check(new byte[][]{new byte[255]},n,false);
  }
  int seed=0x18273546;
  for(int k=0;k<8192;k++){
   seed ^= seed<<13;seed ^= seed>>>17;seed ^= seed<<5;
   int n=(seed>>>3)&15;
   byte[][] records=new byte[n][];
   for(int i=0;i<n;i++){
    seed ^= seed<<13;seed ^= seed>>>17;seed ^= seed<<5;
    int sz=(seed>>>1)&255;
    records[i]=new byte[sz];
    for(int j=0;j<sz;j++)records[i][j]=(byte)(seed+i*17+j);
   }
   check(records,seed,false);
  }
  check(new byte[][]{new byte[65530]},0,false); // oversize wire rejected
  System.out.println("PHASE37_KDA_WIRE_PARITY_OK "+checks);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase37"));args=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase36 source changed"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert sum(p.endswith(".java") for p in files)==1710 and "CTransport.dll" in files
 changes=patch(files)
 modified=sorted(p for p in files if files[p]!=before[p])
 assert modified==CHANGED,modified
 assert files["CTransport.dll"]==before["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for p,data in files.items():
  if p.startswith("src/") and p.endswith(".java"):
   f=args.work/p;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 t=args.work/"testsrc/Phase37KdaChartParity.java";t.parent.mkdir(parents=True,exist_ok=True);t.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for p,data in sorted(files.items()):z.writestr(p,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":37,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
       "java_sources":1710,"modified_java":modified,**changes,
       "native_unchanged":True,"no_legacy_fallback":True,"new_workers_queues_classes":0,
       "windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
 print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=="__main__":main()
