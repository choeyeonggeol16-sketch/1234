#!/usr/bin/env python3
"""Compile production rollbackDirectBegin and inject Native release failures."""
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
header='''import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
class CIOBufferEx {boolean active=true;boolean reserved(){return active;}}
public class Rollback99 {
 final ReentrantLock _stateLock=new ReentrantLock();
 int _reservedDirect=1,_reservedWireBytes=64, cancelled,drained,read,notified;
 boolean failRelease, failUpdate, failDrain, failRead, failNotify;
 void cancelReservedTicketLocked(int slot){cancelled++;}
 void abortOutboundFrame(CIOBufferEx lease){lease.active=false;if(failRelease)throw new IllegalStateException("release failure");}
 boolean updateSendReadPauseLocked(){if(failUpdate)throw new IllegalStateException("update failure");return true;}
 void drainPublishedSocketCallbacks(){drained++;if(failDrain)throw new IllegalStateException("drain failure");}
 void kickRead(){read++;if(failRead)throw new IllegalStateException("read failure");}
 void notifyClosedWhenReleased(){notified++;if(failNotify)throw new IllegalStateException("notify failure");}
'''
footer='''
 static void ck(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void verify(int injected){
  Rollback99 s=new Rollback99();CIOBufferEx lease=new CIOBufferEx();
  switch(injected){case 1->s.failRelease=true;case 2->s.failUpdate=true;
     case 3->s.failDrain=true;case 4->s.failRead=true;case 5->s.failNotify=true;
     case 6->{s.failRelease=true;s.failDrain=true;s.failNotify=true;}}
  try{s.rollbackDirectBegin(64,3,lease);ck(injected==0,"expected error after injected release");}
  catch(IllegalStateException e){ck(injected!=0,"normal rollback threw "+e);
     if(injected==6)ck(e.getSuppressed().length==2,"missing other cleanup failures");}
  ck(s._reservedDirect==0&&s._reservedWireBytes==0,"failed to restore reserved counters");
  ck(s.cancelled==1&&s.drained==1&&s.notified==1,"pending FIFO ticket or closing notification stuck");
  ck(!lease.active,"Native lease not returned");
  ck(s.read==(!s.failUpdate?1:0),"read resume policy changed");
 }
 public static void main(String[] args){for(int i=0;i<=6;i++)verify(i);
  System.out.println("OK production rollbackDirectBegin: normal + 6 injected cases, canceled ticket always drained");
 }
}
'''
with tempfile.TemporaryDirectory() as tmp:
    td=Path(tmp);(td/'Rollback99.java').write_text(header+method('    private void rollbackDirectBegin(int bytes, int slot,')+'\n'+footer)
    p=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(td),str(td/'Rollback99.java')],capture_output=True,text=True)
    if p.returncode:print(p.stderr);sys.exit(p.returncode)
    p=subprocess.run(['java','-cp',str(td),'Rollback99'],capture_output=True,text=True)
    if p.returncode:print(p.stderr);sys.exit(p.returncode)
    print(p.stdout.strip())
