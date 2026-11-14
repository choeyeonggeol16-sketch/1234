from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
old=(r/'research/pool-manager/manager-append-baseline.java.txt').read_text()
baseline='''import javax.swing.*;import java.util.concurrent.*;import java.lang.ref.*;
public final class ManagerBacklogBaseline {
static final int MAX_LOG_LINES=300;
'''+old+'''
public static void main(String[] args)throws Exception {
JTextArea area=new JTextArea();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
SwingUtilities.invokeLater(()->{entered.countDown();try{release.await();}catch(Exception e){throw new AssertionError(e);}});
if(!entered.await(5,TimeUnit.SECONDS))throw new AssertionError("EDT barrier");
WeakReference<?>[] refs=new WeakReference<?>[12000];
for(int i=0;i<refs.length;i++){String text="x".repeat(4096)+" #"+i;refs[i]=new WeakReference<>(text);appendLog(area,text);}
System.gc();Thread.sleep(100);int retained=0;for(var w:refs)if(w.get()!=null)retained++;
System.out.println("BASELINE original appendLog,EDT blocked:retainedMessages="+retained+" heapBytes="+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()));
if(retained<11999)throw new AssertionError("baseline no longer reproduces pending captures");
System.out.println("REPRODUCED original unbounded pending UI callbacks;this is a controlled EDT stall,not proof of every Windows resize cause");
release.countDown();System.exit(0);
}}
'''
with tempfile.TemporaryDirectory(prefix='manager-memory-') as tmp:
 out=Path(tmp);src=out/'ManagerBacklogBaseline.java';src.write_text(baseline)
 subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',tmp,str(src),str(r/'src/l1j/server/swing/ManagerLogBuffer.java'),str(r/'tests/ManagerLogBufferTest.java'),str(r/'src/l1j/server/server/model/network/core/diagnostics/CPoolDiagnostics.java'),str(r/'tests/PoolDiagnosticsTest.java')],check=True)
 for cls in ['ManagerBacklogBaseline','l1j.server.swing.ManagerLogBufferTest','l1j.server.server.model.network.core.diagnostics.PoolDiagnosticsTest']:
  subprocess.run(['java','-Xmx192m','-Djava.awt.headless=true','-cp',tmp,cls],check=True,timeout=60)
