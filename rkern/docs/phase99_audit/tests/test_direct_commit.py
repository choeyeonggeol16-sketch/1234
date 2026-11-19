#!/usr/bin/env python3
"""Compile unmodified production commitDirectFrame body in a typed lifecycle harness."""
from pathlib import Path
import re, subprocess, tempfile, sys, os
ROOT=Path(__file__).resolve().parents[3]
SOURCE=Path(os.environ.get('RKERN_TEST_CUSERSOCKET', ROOT/'src/l1j/server/server/model/network/core/connection/CUserSocket.java')).read_text()

def extract_method(src, marker):
    start=src.index(marker)
    brace=src.index('{',start)
    depth=0
    for i in range(brace,len(src)):
        if src[i]=='{': depth+=1
        if src[i]=='}':
            depth-=1
            if not depth:return src[start:i+1]
    raise AssertionError('unmatched method')

method=extract_method(SOURCE,'    public boolean commitDirectFrame(CIOFrame frame) {')
header='''package l1j.server.server.model.network.core.connection;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.*;
import l1j.server.server.model.network.buffer.WriteBuffer;
class CIOBufferPool { static final int HEADER_BYTES=2, SMALL_SEND_BYTES=1024; int releases; boolean failRelease; boolean reuse; CUserSocket socket; CIOFrame frame;
  void release(CIOBufferEx lease) { releases++; lease.active=false; if(reuse) {
    CIOBufferEx second=new CIOBufferEx(); second.frame=frame;
    frame.beginIO(socket,second); frame.setReservation(42,7,7); }
    if(failRelease) throw new IllegalStateException("injected source-release exception"); }
}
class CIOBufferEx { boolean active=true; CIOFrame frame; WriteBuffer writer=new WriteBuffer();
  WriteBuffer writer(){return writer;} boolean reserved(){return active;}
  java.nio.ByteBuffer buffer(){return java.nio.ByteBuffer.allocate(1024);}
  CIOFrame beginDirectFrame(CUserSocket socket){frame=new CIOFrame();frame.beginIO(socket,this);frame.setReservation(64,1,1);return frame;}
}
public class CUserSocket {
  final CIOBufferPool _pool = new CIOBufferPool();
  final ReentrantLock _stateLock = new ReentrantLock();
  int _reservedDirect=1, _reservedWireBytes=64, _stagedSends, _maxQueuedFrames=128, _closed, closeCount, kickCount, finishCount, drainCount;
  boolean injectStageReject, injectFinishFailure, injectDrainFailure, injectKickFailure, injectUpdateFailure, injectPacked;
  CIOBufferEx _stagedPacketPack; long _stagedPacketFirstTicket,_stagedPacketLastTicket;
  final long[] _socketCallbacks = new long[4]; final SlotVar SOCKET_CALLBACK_SLOT = new SlotVar();
  static final long SOCKET_CANCELLED=8;
  final String _remoteAddress="test";
  static final Logger LOG=Logger.getLogger("direct-commit");
  static class SlotVar { long getAcquire(long[] a,int index){return a[index];} }
  enum CloseReason { SEND_REJECTED, ENCODING_ERROR }
  boolean packAdjacentStagedPacketLocked(CIOBufferEx lease,long ticket,int slot,boolean direct){return injectPacked;}
  boolean stageOutboundFrameLocked(CIOBufferEx lease,boolean direct,int slot){if(injectStageReject)return false;_stagedSends++;return true;}
  void promoteDirectPackCapacityLocked(CIOBufferEx lease){}
  void compactReadyStagedPacketsLocked(){}
  boolean updateSendReadPauseLocked(){if(injectUpdateFailure)throw new IllegalStateException("update failed");return false;}
  void drainPublishedSocketCallbacks(){drainCount++;if(injectDrainFailure)throw new IllegalStateException("drain failed");}
  void finishStagedSend(CIOBufferEx lease,boolean direct){finishCount++;if(injectFinishFailure)throw new IllegalStateException("finish failed");}
  void kickRead(){kickCount++;if(injectKickFailure)throw new IllegalStateException("kick failed");}
  void close(CloseReason reason){_closed=1;closeCount++;}
'''
footer='''
  static void check(boolean b,String desc){if(!b)throw new AssertionError(desc);}
  static CUserSocket fresh(){CUserSocket s=new CUserSocket();s._pool.socket=s;return s;}
  static CIOFrame start(CUserSocket s){ CIOBufferEx l=new CIOBufferEx(); CIOFrame f=l.beginDirectFrame(s);s._pool.frame=f;return f; }
  static void acceptedFailure(String where) {
    CUserSocket s=fresh(); CIOFrame f=start(s);
    switch(where){
      case "finish" -> s.injectFinishFailure=true;
      case "drain" -> { s.injectPacked=true; s.injectDrainFailure=true; }
      case "release" -> { s.injectPacked=true; s._pool.failRelease=true; }
      case "reuse" -> { s.injectPacked=true; s._pool.failRelease=true;s._pool.reuse=true; }
      case "update" -> s.injectUpdateFailure=true;
      default -> throw new AssertionError();
    }
    boolean committed=false;Throwable thrown=null;
    try { committed=s.commitDirectFrame(f); }catch(Throwable e){thrown=e;}
    finally {if(!committed) { try{ s.abortDirectFrame(f); }catch(Throwable ignored){} } }
    check(thrown==null,"post-accept error leaked to caller: "+where+" "+thrown);
    check(committed,"accepted transfer must return true: "+where);
    check(s.closeCount==1,"post accept failure must close socket exactly once: "+where);
    check(s._reservedDirect==0 && s._reservedWireBytes==0,"reservation leaked "+where);
    if(where.equals("reuse"))check(f.ioLease(s)!=null,"next generation was incorrectly cancelled");
    else check(f.buffer()==null,"old direct frame still active: "+where);
  }
  void abortDirectFrame(CIOFrame f){CIOBufferEx l=f.ioLease(this);_reservedDirect--;_reservedWireBytes-=f.reservedBytes();f.finishIO();_pool.release(l);}
  public static void main(String[] args) {
    CUserSocket s=fresh();CIOFrame f=start(s);
    check(s.commitDirectFrame(f),"normal accept rejected");
    check(s.closeCount==0&&s._reservedDirect==0&&s._reservedWireBytes==0,"normal counters");
    acceptedFailure("finish");acceptedFailure("drain");acceptedFailure("release");acceptedFailure("reuse");acceptedFailure("update");
    CUserSocket rejected=fresh();rejected.injectStageReject=true;CIOFrame bad=start(rejected);
    check(!rejected.commitDirectFrame(bad),"expected pre-accept reject");
    check(rejected._reservedDirect==1&&rejected._reservedWireBytes==64,"reject lost reservation");
    rejected.abortDirectFrame(bad);
    check(rejected._reservedDirect==0&&rejected._reservedWireBytes==0,"reject abort failed");
    System.out.println("OK actual production commitDirectFrame: normal, reject+abort, 5 post-accept injected failures, recycled-frame safety");
  }
}
'''
# An injected error from updateSendReadPauseLocked occurs after frame.finishIO, so it must be caught.
with tempfile.TemporaryDirectory() as td:
    d=Path(td); pkg=d/'l1j/server/server/model/network/core/connection';pkg.mkdir(parents=True)
    wb=d/'l1j/server/server/model/network/buffer';wb.mkdir(parents=True)
    (wb/'WriteBuffer.java').write_text('package l1j.server.server.model.network.buffer; public class WriteBuffer {public int position(){return 12;} }')
    (pkg/'CUserSocket.java').write_text(header+method+footer)
    # use the actual production CIOFrame, which clears ownership on finishIO.
    cmd=['javac','--release','21','-encoding','UTF-8','-d',str(d/'classes'),str(pkg/'CUserSocket.java'),str(ROOT/'src/l1j/server/server/model/network/core/connection/CIOFrame.java'),str(wb/'WriteBuffer.java')]
    p=subprocess.run(cmd,capture_output=True,text=True)
    if p.returncode: print(p.stderr);sys.exit(p.returncode)
    p=subprocess.run(['java','-cp',str(d/'classes'),'l1j.server.server.model.network.core.connection.CUserSocket'],capture_output=True,text=True)
    if p.returncode: print(p.stderr);sys.exit(p.returncode)
    print(p.stdout.strip())
