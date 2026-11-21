#!/usr/bin/env python3
"""Compile the exact production lift-gate cleanup method with fault-injection stubs."""
from pathlib import Path
import subprocess, tempfile, textwrap
root = Path(__file__).resolve().parents[3]
source = (root / 'src/l1j/server/server/model/world/instance/shared/L1LiftGateInstance.java').read_text(encoding='utf-8')
needle='private static void closeSharedAttrs('
start=source.index(needle)
open_brace=source.index('{',start)
depth=1
end=open_brace+1
while depth:
    if source[end]=='{': depth+=1
    elif source[end]=='}': depth-=1
    end+=1
method=source[start:end]
class_code='''
package l1j.server.server.model.world.instance.shared;
public class LiftGateRelease94 {
    /* METHOD */
    private static void check(boolean ok, String why) {
        if(!ok) throw new AssertionError(why);
    }
    public static void main(String[] args) {
        var a=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(true);
        var b=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(false);
        var c=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(true);
        var frames=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer[]{a,b,c};
        RuntimeException failure=null;
        try { closeSharedAttrs(frames,null); } catch(RuntimeException ex) { failure=ex; }
        check(failure!=null,"first release exception masked");
        check(a.called==1&&b.called==1&&c.called==1,"not all native sources closed");
        check(failure.getSuppressed().length==1,"second release failure not suppressed");
        var pending=new IllegalArgumentException("original SEND failure");
        var d=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(true);
        var e=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(false);
        closeSharedAttrs(new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer[]{d,e},pending);
        check(d.called==1&&e.called==1,"not all frames closed with primary failure");
        check(pending.getSuppressed().length==1,"original primary failure not preserved");
        System.out.println("94 actual lift-gate close helper: all frames + primary/suppressed errors PASS");
    }
}
'''.replace('/* METHOD */',method)
stub='''
package l1j.server.server.model.network.core.broadcast;
public class CAsyncSendConstBuffer {
    public int called;
    public final boolean fail;
    public CAsyncSendConstBuffer(boolean fail){this.fail=fail;}
    public void close(){called++;if(fail)throw new IllegalStateException("Native release failure");}
}
'''
with tempfile.TemporaryDirectory() as td:
    base=Path(td)
    for path,contents in {
      'l1j/server/server/model/world/instance/shared/LiftGateRelease94.java':class_code,
      'l1j/server/server/model/network/core/broadcast/CAsyncSendConstBuffer.java':stub
    }.items():
        p=base/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(contents,encoding='utf8')
    subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',td,*map(str,base.rglob('*.java'))],check=True)
    subprocess.run(['java','-cp',td,'l1j.server.server.model.world.instance.shared.LiftGateRelease94'],check=True)
