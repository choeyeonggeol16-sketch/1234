from pathlib import Path
import subprocess,tempfile,hashlib,json
ROOT=Path(__file__).resolve().parents[1];SRC=ROOT/'input/src';PKG='l1j/server/server/model/network/core';LOG=ROOT/'logs';LOG.mkdir(exist_ok=True)
USER='''package l1j.server.server.model.network.core;
final class CIOUserSocket {
 int readBytes;
 void transportReadCompleted(int n){readBytes=n;}
 void transportWriteCompleted(int n){}
 void transportGatherCompleted(long n){}
}
'''
STUB='''package l1j.server.server.model.network.core;
import java.nio.ByteBuffer;
final class CIOWinServer {
 boolean pause(long id,boolean value){return true;}
 boolean disconnect(long id){return true;}
 boolean send(long id,ByteBuffer[] b,int off,int count){return true;}
}
'''
SOCKET='''package l1j.server.server.model.network.core;
import java.nio.ByteBuffer;
import java.lang.foreign.*;
import java.net.InetSocketAddress;
import java.util.Arrays;
public final class SocketStashProbe {
 static void feed(CIOWinSocket socket,byte[] bytes){try(Arena a=Arena.ofConfined()){
  MemorySegment b=a.allocate(bytes.length);b.copyFrom(MemorySegment.ofArray(bytes));socket.nativeReceive(b,bytes.length);
 }}
 public static void main(String[] args){
  CIOWinSocket s=new CIOWinSocket(new CIOWinServer(),1,new InetSocketAddress("127.0.0.1",12345),null);
  CIOUserSocket owner=new CIOUserSocket();
  byte[] all=new byte[7000];for(int i=0;i<all.length;i++)all[i]=(byte)((i/37)^(i*13));
  feed(s,Arrays.copyOfRange(all,0,3000));feed(s,Arrays.copyOfRange(all,3000,6000));
  s.read(ByteBuffer.allocateDirect(4096),owner);if(owner.readBytes!=4096)throw new AssertionError();
  // Simulate an already-in-flight completion after a partial stash drain.
  feed(s,Arrays.copyOfRange(all,6000,7000));
  ByteBuffer out=ByteBuffer.allocateDirect(2904);s.read(out,owner);out.flip();byte[] got=new byte[out.remaining()];out.get(got);
  byte[] want=Arrays.copyOfRange(all,4096,7000);
  if(Arrays.equals(got,want))throw new AssertionError("Expected original-source bug was not reproduced");
  System.out.println("REPRODUCED original CIOWinSocket: 6000 stashed -> read 4096 -> append 1000; remaining 2904 bytes corrupted.");
  System.out.println("expected first 8="+Arrays.toString(Arrays.copyOf(want,8))+" actual="+Arrays.toString(Arrays.copyOf(got,8)));
  System.out.println("Scope: original Java class, fake native pause/send and fake CIOUserSocket; in-flight callback interleaving, not a live DLL trace.");
 }
}
'''
REGISTRY='''package l1j.server.server.model.network.core;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReferenceArray;
public final class RegistryProbe {
 static void set(Object obj,String name,Object v)throws Exception{Field f=CIOWinServer.class.getDeclaredField(name);f.setAccessible(true);f.set(obj,v);}
 public static void main(String[] args)throws Exception{
  Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field f=unsafe.getDeclaredField("theUnsafe");f.setAccessible(true);
  Object u=f.get(null);CIOWinServer server=(CIOWinServer)unsafe.getMethod("allocateInstance",Class.class).invoke(u,CIOWinServer.class);
  int size=1024;AtomicReferenceArray<CIOWinSocket> table=new AtomicReferenceArray<>(size);
  CIOWinSocket tomb=new CIOWinSocket(server,-1,new InetSocketAddress("127.0.0.1",1),null);
  set(server,"connections",table);set(server,"mask",size-1);set(server,"tombstone",tomb);
  Method reg=CIOWinServer.class.getDeclaredMethod("register",CIOWinSocket.class);reg.setAccessible(true);
  Method forget=CIOWinServer.class.getDeclaredMethod("forget",CIOWinSocket.class);forget.setAccessible(true);
  Method find=CIOWinServer.class.getDeclaredMethod("find",long.class);find.setAccessible(true);
  int nulls=size;int churn=0;
  for(long id=1;id<=100000 && nulls!=0;id++){
   CIOWinSocket s=new CIOWinSocket(server,id,new InetSocketAddress("127.0.0.1",1),null);
   if(!(Boolean)reg.invoke(server,s))throw new AssertionError();forget.invoke(server,s);churn++;
   nulls=0;for(int i=0;i<size;i++)if(table.get(i)==null)nulls++;
  }
  if(nulls!=0)throw new AssertionError("coverage incomplete");
  if(find.invoke(server,999999999L)!=null)throw new AssertionError();
  System.out.println("REPRODUCED original CIOWinServer registry: slots="+size+", sequential one-at-a-time connect/forget="+churn+", live=0, null slots=0.");
  System.out.println("A missing-id find now traverses all 1024 slots by the original find loop; this is a probe-count result, not an end-to-end throughput benchmark.");
  System.out.println("Scope: original register/find/forget via reflection, constructor bypassed; no native DLL loaded and no network connection.");
 }
}
'''
results=[]
for kind,test in [('stash',SOCKET),('registry',REGISTRY)]:
 with tempfile.TemporaryDirectory(prefix='aden-probe-') as tmp:
  root=Path(tmp);p=root/PKG;p.mkdir(parents=True);build=root/'build';build.mkdir()
  srcfiles=[]
  for name in ('CIOWinSocket.java',)+(() if kind=='stash' else ('CIOWinServer.java',)):
   q=p/name;q.write_bytes((SRC/PKG/name).read_bytes());srcfiles.append(q)
  for name,text in [('CIOUserSocket.java',USER),*(( [('CIOWinServer.java',STUB)] ) if kind=='stash' else []),('SocketStashProbe.java' if kind=='stash' else 'RegistryProbe.java',test)]:
   q=p/name;q.write_text(text);srcfiles.append(q)
  cmd=['java','-m','jdk.compiler/com.sun.tools.javac.Main','--enable-preview','-source','21','-target','21','-d',str(build),*[str(x) for x in srcfiles]]
  with (LOG/(kind+'-source-probe.log')).open('w') as out:
   subprocess.run(cmd,check=True,stdout=out,stderr=subprocess.STDOUT)
   main='SocketStashProbe' if kind=='stash' else 'RegistryProbe'
   subprocess.run(['java','--enable-preview','--enable-native-access=ALL-UNNAMED','-cp',str(build),PKG.replace('/','.')+'.'+main],check=True,stdout=out,stderr=subprocess.STDOUT)
  if kind=='stash':
   sock=p/'CIOWinSocket.java';code=sock.read_text();needle='else if (stashStart > 0 && stash.length - stashEnd < length - at)'
   assert needle in code;sock.write_text(code.replace(needle,'else if (stashStart > 0)'))
   probe=p/'SocketStashProbe.java';code=probe.read_text().replace('if(Arrays.equals(got,want))','if(!Arrays.equals(got,want))').replace('Expected original-source bug was not reproduced','Suggested compaction fix did not preserve bytes').replace('REPRODUCED original CIOWinSocket: 6000 stashed -> read 4096 -> append 1000; remaining 2904 bytes corrupted.','PASS suggested fix on temporary copy: same 4KiB-read case preserves all remaining 2904 bytes.').replace('original Java class, fake native','temporary copy with one-condition compaction fix, fake native')
   probe.write_text(code)
   with (LOG/'stash-suggested-fix-probe.log').open('w') as out:
    subprocess.run(cmd,check=True,stdout=out,stderr=subprocess.STDOUT)
    subprocess.run(['java','--enable-preview','--enable-native-access=ALL-UNNAMED','-cp',str(build),PKG.replace('/','.')+'.SocketStashProbe'],check=True,stdout=out,stderr=subprocess.STDOUT)
  results.append({'probe':kind,'result':'REPRODUCED','original_source_unchanged':True})
(ROOT/'logs/source-probes.json').write_text(json.dumps(results,indent=2)+'\n')
print(json.dumps(results))
