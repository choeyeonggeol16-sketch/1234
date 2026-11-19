#!/usr/bin/env python3
"""Compile production CIOBufferEx/CIOFrame with a typed, fault-injectable pool."""
from pathlib import Path
import subprocess,tempfile,sys,os
ROOT=Path(__file__).resolve().parents[3]
with tempfile.TemporaryDirectory() as tmp:
    td=Path(tmp); root=td/'l1j/server/server/model/network/core/connection';root.mkdir(parents=True)
    b=td/'l1j/server/server/model/network/core/broadcast';b.mkdir(parents=True)
    w=td/'l1j/server/server/model/network/buffer';w.mkdir(parents=True)
    (root/'CIOBufferPool.java').write_text('''package l1j.server.server.model.network.core.connection;
import java.util.concurrent.atomic.*;
class CIOBufferPool { static final int HEADER_BYTES=2;
 static class Slots {int released;int generation(int slot){return 1;}void releaseOwnedLease(int slot,int gen){released++;}}
 static class Bucket {final Slots _slots=new Slots();final LongAdder _returns=new LongAdder(); final AtomicLong _allocated=new AtomicLong();final int _bytes=256;}
}
''')
    (root/'CUserSocket.java').write_text('package l1j.server.server.model.network.core.connection; public class CUserSocket {}')
    (w/'WriteBuffer.java').write_text('''package l1j.server.server.model.network.buffer;
import java.nio.ByteBuffer;
public class WriteBuffer {
 public static boolean failRelease, failBegin;
 public void releaseResources(){if(failRelease){failRelease=false;throw new IllegalStateException("writer cleanup failed");}}
 public void armExternal(ByteBuffer b,int off,int cap){}
 public void beginDirect(){if(failBegin){failBegin=false;throw new IllegalStateException("start prepare failed");}}
 public void position(int n){}
 public void preparePacket(Class<?> cls){}
}
''')
    (b/'CAsyncSendConstBuffer.java').write_text('''package l1j.server.server.model.network.core.broadcast;
import java.nio.ByteBuffer;
public class CAsyncSendConstBuffer {
 public static class Source {
  public final ByteBuffer wire=ByteBuffer.allocate(16);
  public boolean failRelease; public int retained, released;
  public Source retain(){retained++;return this;}
  public void release(){released++;if(failRelease)throw new IllegalStateException("Native source release failed");}
 }
}
''')
    (root/'BufferLease99.java').write_text('''package l1j.server.server.model.network.core.connection;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
public class BufferLease99 {
 static void ck(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void failed(Runnable task,String contains,int suppressed){
  try{task.run();throw new AssertionError("expected "+contains);}catch(IllegalStateException x){
   ck(x.getMessage().contains(contains),"wrong failure "+x);
   ck(x.getSuppressed().length==suppressed,"suppressed count "+x.getSuppressed().length);
  }
 }
 public static void main(String[] args){
  CIOBufferPool.Bucket bucket=new CIOBufferPool.Bucket();CIOBufferEx lease=new CIOBufferEx(bucket,0);
  lease.start(true,64);WriteBuffer.failRelease=true;
  failed(lease::release,"writer cleanup",0);
  ck(bucket._slots.released==1&&bucket._returns.sum()==1,"writer failure stranded slot");
  lease.start(true,64);lease.release();
  ck(bucket._slots.released==2,"lease not reusable after writer exception");
  CAsyncSendConstBuffer.Source source=new CAsyncSendConstBuffer.Source();source.failRelease=true;
  lease.startShared(source);
  failed(lease::release,"Native source",0);
  ck(source.released==1&&bucket._slots.released==3,"native release stranded slot");
  lease.start(true,64);WriteBuffer.failRelease=true;
  CAsyncSendConstBuffer.Source second=new CAsyncSendConstBuffer.Source();second.failRelease=true;
  lease._sharedSource=second;
  failed(lease::release,"writer cleanup",1);
  ck(second.released==1&&bucket._slots.released==4&&bucket._returns.sum()==4,"two failures stranded slot");
  WriteBuffer.failBegin=true;WriteBuffer.failRelease=true;
  failed(()->lease.start(true,64),"start prepare failed",1);
  // A broken cleanup during start() must not permanently leave _active=1.
  lease.start(true,64);lease.release();
  ck(bucket._slots.released==5&&bucket._returns.sum()==5,"start-failure lease not reusable");
  System.out.println("OK actual production CIOBufferEx: writer/native/combined/start failures, 5 leases returned, suppressed preserved");
 }
}
''')
    sources=[str(x) for x in td.rglob('*.java')]+[os.environ.get('RKERN_TEST_CIOBUFFEREX',str(ROOT/'src/l1j/server/server/model/network/core/connection/CIOBufferEx.java')),str(ROOT/'src/l1j/server/server/model/network/core/connection/CIOFrame.java')]
    p=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(td/'classes'),*sources],capture_output=True,text=True)
    if p.returncode:print(p.stderr);sys.exit(p.returncode)
    p=subprocess.run(['java','-cp',str(td/'classes'),'l1j.server.server.model.network.core.connection.BufferLease99'],capture_output=True,text=True)
    if p.returncode:print(p.stderr);sys.exit(p.returncode)
    print(p.stdout.strip())
