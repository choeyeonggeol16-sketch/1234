#!/usr/bin/env python3
"""Compile production abortDirectFrame+rollbackDirectBegin, exercise cleanup failures."""
from pathlib import Path
import subprocess,tempfile,sys,os
ROOT=Path(__file__).resolve().parents[3]
src=Path(os.environ.get('RKERN_TEST_CUSERSOCKET', ROOT/'src/l1j/server/server/model/network/core/connection/CUserSocket.java')).read_text()
def meth(marker):
    a=src.index(marker);b=src.index('{',a);n=0
    for i in range(b,len(src)):
        if src[i]=='{':n+=1
        if src[i]=='}':
            n-=1
            if not n:return src[a:i+1]
    raise AssertionError(marker)
header='''package l1j.server.server.model.network.core.connection;
import java.util.logging.*;
import java.util.concurrent.locks.ReentrantLock;
import l1j.server.server.model.network.buffer.WriteBuffer;
class CIOBufferEx {
  boolean active=true;
  WriteBuffer writer=new WriteBuffer();
  WriteBuffer writer(){return writer;}
  boolean reserved(){return active;}
}
public class CUserSocket {
  final ReentrantLock _stateLock=new ReentrantLock();
  int _reservedDirect=1,_reservedWireBytes=64, _closed,closeCount,cancelled,drained,read,notified,releaseCount;
  boolean failRelease,failDrain,failRead;
  final String _remoteAddress="abort-test";
  static final Logger LOG=Logger.getLogger("direct-abort-test");
  enum CloseReason { ENCODING_ERROR }
  void cancelReservedTicketLocked(int slot){cancelled++;}
  void abortOutboundFrame(CIOBufferEx lease){lease.active=false;releaseCount++;if(failRelease)throw new IllegalStateException("injected native release");}
  boolean updateSendReadPauseLocked(){return true;}
  void drainPublishedSocketCallbacks(){drained++;if(failDrain)throw new IllegalStateException("drain");}
  void kickRead(){read++;if(failRead)throw new IllegalStateException("read");}
  void notifyClosedWhenReleased(){notified++;}
  void close(CloseReason reason){_closed=1;closeCount++;}
'''
footer='''
  static void ck(boolean b,String m){if(!b)throw new AssertionError(m);}
  static CIOFrame frame(CUserSocket s,boolean optional){CIOFrame f=new CIOFrame();f.beginIO(s,new CIOBufferEx());f.setReservation(64,1,1);f.setOptional(optional);return f;}
  static void run(boolean optional,boolean releaseError,boolean drainError){
   CUserSocket s=new CUserSocket();s.failRelease=releaseError;s.failDrain=drainError;
   CIOFrame frame=frame(s,optional);
   s.abortDirectFrame(frame);
   ck(frame.buffer()==null,"recycled frame was not retired");
   ck(s._reservedDirect==0&&s._reservedWireBytes==0,"lost reservation accounting");
   ck(s.releaseCount==1&&s.cancelled==1&&s.drained==1&&s.notified==1,"failed to retire ticket/source");
   ck(s.closeCount==((!optional||releaseError||drainError)?1:0),"incorrect fatal/non-fatal optional policy");
  }
  public static void main(String[] args){
    run(false,false,false);run(true,false,false);run(false,true,false);run(true,true,false);run(true,false,true);
    CUserSocket s=new CUserSocket();s.failRelease=true;CIOFrame f=frame(s,false);
    try {try{throw new IllegalStateException("original encoder fault");}finally{s.abortDirectFrame(f);}}
    catch(IllegalStateException expected){ck("original encoder fault".equals(expected.getMessage()),"cleanup masked encoder fault");}
    ck(s.closeCount==1&&s.releaseCount==1,"encode-fault abort incomplete");
    System.out.println("OK production abortDirectFrame: 6 scenarios, optional policy, FIFO retirement, original exception preserved");
  }
}
'''
with tempfile.TemporaryDirectory() as tmp:
    td=Path(tmp);pkg=td/'l1j/server/server/model/network/core/connection';pkg.mkdir(parents=True)
    wb=td/'l1j/server/server/model/network/buffer';wb.mkdir(parents=True)
    (wb/'WriteBuffer.java').write_text('package l1j.server.server.model.network.buffer; public class WriteBuffer {}')
    (pkg/'CUserSocket.java').write_text(header+meth('    private void rollbackDirectBegin(int bytes, int slot,')+'\n'+meth('    public void abortDirectFrame(CIOFrame frame) {')+'\n'+footer)
    p=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(td/'classes'),str(pkg/'CUserSocket.java'),str(ROOT/'src/l1j/server/server/model/network/core/connection/CIOFrame.java'),str(wb/'WriteBuffer.java')],capture_output=True,text=True)
    if p.returncode:print(p.stderr);sys.exit(p.returncode)
    p=subprocess.run(['java','-cp',str(td/'classes'),'l1j.server.server.model.network.core.connection.CUserSocket'],capture_output=True,text=True)
    if p.returncode:print(p.stderr);sys.exit(p.returncode)
    print(p.stdout.strip())
