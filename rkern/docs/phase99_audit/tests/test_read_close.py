#!/usr/bin/env python3
"""Compile actual read-lease cleanup and verify close-complete progress."""
from pathlib import Path
import subprocess,tempfile,sys,os
ROOT=Path(__file__).resolve().parents[3]
src=Path(os.environ.get('RKERN_TEST_CUSERSOCKET', ROOT/'src/l1j/server/server/model/network/core/connection/CUserSocket.java')).read_text()
a=src.index('    private void releaseReadWhenIdle() {');b=src.index('{',a);n=0
for i in range(b,len(src)):
    if src[i]=='{':n+=1
    if src[i]=='}':
        n-=1
        if n==0:
            method=src[a:i+1];break
else:raise AssertionError('unterminated releaseReadWhenIdle')
head='''import java.util.logging.*;
import java.util.concurrent.locks.ReentrantLock;
class CIOBufferEx {}
class Pool {int released;boolean fail;
 void release(CIOBufferEx lease){released++;if(fail)throw new IllegalStateException("Native READ release failed");}
}
public class Read99 {
 final ReentrantLock _stateLock=new ReentrantLock();final Pool _pool=new Pool();
 boolean _readReleased;int _readActive,_closed,closes;
 CIOBufferEx _reading=new CIOBufferEx();
 final String _remoteAddress="test";static final Logger LOG=Logger.getLogger("read-test");
 enum CloseReason { ENCODING_ERROR }
 void close(CloseReason r){_closed=1;closes++;}
'''
tail='''
 static void ck(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  Read99 s=new Read99();s.releaseReadWhenIdle();
  ck(s._readReleased&&s._reading==null&&s._pool.released==1&&s.closes==0,"normal read release");
  s.releaseReadWhenIdle();ck(s._pool.released==1,"duplicate READ lease release");
  s=new Read99();s._pool.fail=true;s.releaseReadWhenIdle();
  ck(s._readReleased&&s._reading==null&&s.closes==1,"failed READ release blocks close");
  s=new Read99();s._closed=1;s._pool.fail=true;s.releaseReadWhenIdle();
  ck(s._readReleased&&s.closes==0,"already closed socket closed twice");
  s=new Read99();s._readActive=1;s.releaseReadWhenIdle();
  ck(!s._readReleased&&s._pool.released==0,"active READ prematurely released");
  System.out.println("OK production releaseReadWhenIdle: 5 cases, Native failure cannot block close notification");
 }
}
'''
with tempfile.TemporaryDirectory() as tmp:
 td=Path(tmp);(td/'Read99.java').write_text(head+method+tail)
 p=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(td),str(td/'Read99.java')],capture_output=True,text=True)
 if p.returncode:print(p.stderr);sys.exit(p.returncode)
 p=subprocess.run(['java','-cp',str(td),'Read99'],capture_output=True,text=True)
 if p.returncode:print(p.stderr);sys.exit(p.returncode)
 print(p.stdout.strip())
