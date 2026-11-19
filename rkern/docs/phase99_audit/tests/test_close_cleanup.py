#!/usr/bin/env python3
"""Run production CUserSocket.close(boolean, reason) with queue/native failures."""
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
base='''import java.util.*;
import java.util.logging.*;
import java.util.concurrent.locks.ReentrantLock;
class CNetworkDiagnostics {static void javaClose(Object trace, CloseReason r) {}}
enum CloseReason { SERVER_CLOSE, ENCODING_ERROR }
class CIOBufferEx {
 final int id;final int _frameCount=1;CIOBufferEx(int id){this.id=id;}
 int wireLength(){return 100;}
}
class Pool {
 final List<Integer> released=new ArrayList<>();final Set<Integer> failed=new HashSet<>();
 void release(CIOBufferEx l){released.add(l.id);if(failed.contains(l.id))throw new IllegalStateException("native release "+l.id);}
 int usedSmallSendSlots(){return 0;}int smallSendSlots(){return 10;}
 int usedLargeSendSlots(){return 0;}int largeSendSlots(){return 10;}
}
class Channel {boolean failClose;int closes,graceful;
 void close(){closes++;if(failClose)throw new IllegalStateException("channel close failed");}
 void closeGracefully(){graceful++;if(failClose)throw new IllegalStateException("channel close failed");}
}
class Var {boolean compareAndSet(Close99 s,int a,int b){if(s._closed!=a)return false;s._closed=b;return true;}
 void setVolatile(Close99 s,Object v){}
 long getAcquire(long[] a,int i){return a[i];}
 int getVolatile(Close99 s){return 0;}
}
public class Close99 {
 final ReentrantLock _stateLock=new ReentrantLock();
 static final Var CLOSED=new Var(), ON_DRAINED=new Var(), SOCKET_CALLBACK_SLOT=new Var(), SOCKET_PENDING=new Var(), SOCKET_DRAINING=new Var();
 static final int SOCKET_MASK=3;
 static final Logger LOG=Logger.getLogger("close-test");
 final Channel _channel=new Channel(); final Pool _pool=new Pool();
 int _closed,_queued,_head,_readActive,_outstandingWireBytes,_reservedWireBytes,_stagedSends,_reservedDirect,_gatherCount,_outstandingFrames,_lastSendReject;
 CIOBufferEx _writing; CIOBufferEx[] _pending=new CIOBufferEx[4];
 Object networkTrace,_networkDeadline,_deferredClosePacket,_stagedPacketPack;
 final String _remoteAddress="test";
 long _socketHead;Thread _socketOwner; final long[] _socketCallbacks=new long[4];boolean _readSocketOwnerActive;
 int accountedBytes,accountedFrames,callbacks,kicks,notifications,readReleases;boolean failOnClosing,failCancel;
 int physicalSendLeasesLocked(){return _queued;}
 void onTransportClosing(){if(failOnClosing)throw new IllegalStateException("closing observer failed");}void cancelQueuedNativePacket(){if(failCancel)throw new IllegalStateException("packet cleanup failed");}
 boolean publishProducerLeaseLocked(){return false;}
 boolean bytesReleased(int b,int n){accountedBytes+=b;accountedFrames+=n;return false;}
 void drainSocketCallbacks(){callbacks++;}
 void releaseReadWhenIdle(){readReleases++;}
 void finishReadIfClosed(){}
 void kickWrite(){kicks++;}
 void notifyClosedWhenReleased(){notifications++;}
'''
tail='''
 static void ck(boolean v,String m){if(!v)throw new AssertionError(m);}
 static Close99 seeded(){Close99 s=new Close99();s._queued=3;for(int i=0;i<3;i++)s._pending[i]=new CIOBufferEx(i+1);return s;}
 public static void main(String[] args){
  Close99 s=seeded();s._pool.failed.add(1);s._pool.failed.add(3);s._channel.failClose=true;s.failOnClosing=true;s.failCancel=true;
  s.close(false,CloseReason.SERVER_CLOSE);
  ck(s._closed==1&&s._queued==0,"close did not retire all pending leases");
  ck(s._pool.released.equals(List.of(1,2,3)),"failed native release leaked later FIFO leases");
  ck(s.accountedBytes==300&&s.accountedFrames==3,"close accounting failure");
  ck(s.notifications==1&&s._channel.closes==1,"close interrupted cleanup");
  s.close(false,CloseReason.SERVER_CLOSE);
  ck(s._channel.closes==1&&s._pool.released.size()==3,"duplicate close repeated releases");
  s=seeded();s.close(true,CloseReason.SERVER_CLOSE);
  ck(s._channel.graceful==1&&s._queued==0&&s._pool.released.equals(List.of(1,2,3)),"graceful close changed");
  System.out.println("OK production close: observer/native shutdown + two queue-release failures, 3/3 leases retired, exact-once close");
 }
}
'''
with tempfile.TemporaryDirectory() as tmp:
    td=Path(tmp);(td/'Close99.java').write_text(base+'\n'+method('    private void close(boolean graceful, CloseReason reason) {')+'\n'+tail)
    p=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(td),str(td/'Close99.java')],capture_output=True,text=True)
    if p.returncode:print(p.stderr);sys.exit(p.returncode)
    p=subprocess.run(['java','-cp',str(td),'Close99'],capture_output=True,text=True)
    if p.returncode:print(p.stderr);sys.exit(p.returncode)
    print(p.stdout.strip())
