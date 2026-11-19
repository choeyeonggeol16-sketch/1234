#!/usr/bin/env python3
"""Compile real production completion methods in a typed fault-injection harness."""
from pathlib import Path
import subprocess,tempfile,sys,os
ROOT=Path(__file__).resolve().parents[3]
src=Path(os.environ.get('RKERN_TEST_CUSERSOCKET', ROOT/'src/l1j/server/server/model/network/core/connection/CUserSocket.java')).read_text()
def method(marker):
    a=src.index(marker);b=src.index('{',a); n=0
    for i in range(b,len(src)):
        if src[i]=='{':n+=1
        if src[i]=='}':
            n-=1
            if n==0:return src[a:i+1]
    raise AssertionError(marker)
base='''import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.*;
import java.util.concurrent.locks.ReentrantLock;
class CIOBufferEx {
 final int id;int _frameCount=1;CIOBufferEx(int id){this.id=id;}
 int wireLength(){return 100;}
}
class Pool {
 final List<Integer> released=new ArrayList<>();final Set<Integer> failed=new HashSet<>();
 void release(CIOBufferEx l){released.add(l.id);if(failed.contains(l.id))throw new IllegalStateException("source release "+l.id);}
}
public class Completion99 {
 final ReentrantLock _stateLock=new ReentrantLock();
 final Pool _pool=new Pool();
 int _gatherCount,_queued,_head,_zeroWriteCompletions,_closed,closeCount,writes,reads,notifies,notifyClosed;
 int accountedBytes,accountedFrames;
 CIOBufferEx _writing;
 CIOBufferEx[] _gatherFrames=new CIOBufferEx[4],_pending=new CIOBufferEx[4];
 ByteBuffer[] _gatherBuffers=new ByteBuffer[4];
 final String _remoteAddress="test";
 static final Logger LOG=Logger.getLogger("completion-test");
 boolean bytesReleased(int b,int n){accountedBytes+=b;accountedFrames+=n;return true;}
 void close(){closeCount++;_closed=1;}
 void kickWrite(){writes++;}
 void kickRead(){reads++;}
 void notifySendSlotAvailable(){notifies++;}
 void notifyClosedWhenReleased(){notifyClosed++;}
'''
tail='''
 static void ck(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 static Completion99 single(){Completion99 s=new Completion99();s._writing=new CIOBufferEx(1);return s;}
 static Completion99 gather(){Completion99 s=new Completion99();s._gatherCount=3;for(int i=0;i<3;i++)s._gatherFrames[i]=new CIOBufferEx(i+1);s._writing=s._gatherFrames[0];return s;}
 public static void main(String[] args){
  Completion99 s=single();s._pool.failed.add(1);s.completeWrite(s._writing,true);
  ck(s._writing==null&&s.closeCount==1,"single completion after source exception stuck");
  ck(s.accountedBytes==100&&s.accountedFrames==1&&s._pool.released.equals(List.of(1)),"single accounting");
  s=single();s._queued=1;s._pending[0]=new CIOBufferEx(2);s.completeWrite(s._writing,true);
  ck(s._writing.id==2&&s.writes==1&&s.closeCount==0&&s._queued==0,"single normal FIFO progression");
  s=gather();s._pool.failed.add(1);s._pool.failed.add(3);s.completeGather(true);
  ck(s._writing==null&&s._gatherCount==0&&s.closeCount==1,"gather completion stuck");
  ck(s._pool.released.equals(List.of(1,2,3)),"gather skipped later buffers");
  ck(s.accountedBytes==300&&s.accountedFrames==3,"gather double/missing accounting");
  for(CIOBufferEx x:s._gatherFrames)ck(x==null,"retired gather frame leaked");
  s=gather();s._queued=1;s._pending[0]=new CIOBufferEx(7);s.completeGather(true);
  ck(s._writing.id==7&&s.writes==1&&s.closeCount==0&&s._queued==0,"gather normal FIFO progression");
  System.out.println("OK production completeWrite/completeGather: normal FIFO, 3-way cleanup after 2 failures, single-write accounting");
 }
}
'''
with tempfile.TemporaryDirectory() as tmp:
    td=Path(tmp);(td/'Completion99.java').write_text(base+'\n'+method('    private void completeGather(boolean success) {')+'\n'+method('    private void completeWrite(CIOBufferEx lease, boolean success) {')+'\n'+tail)
    p=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(td),str(td/'Completion99.java')],capture_output=True,text=True)
    if p.returncode:print(p.stderr);sys.exit(p.returncode)
    p=subprocess.run(['java','-cp',str(td),'Completion99'],capture_output=True,text=True)
    if p.returncode:print(p.stderr);sys.exit(p.returncode)
    print(p.stdout.strip())
