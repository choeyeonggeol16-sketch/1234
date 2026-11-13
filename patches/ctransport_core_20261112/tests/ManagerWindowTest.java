package l1j.server.swing;
import java.awt.*;import java.lang.reflect.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import javax.swing.*;
public final class ManagerWindowTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static long heap()throws Exception{System.gc();Thread.sleep(100);return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();}
 public static void main(String[] args)throws Throwable{
  check(!GraphicsEnvironment.isHeadless(),"real display required");
  var prefs=java.util.prefs.Preferences.userRoot().node("Software/PROJECT_L1/LINEAGE_SERVER");
  prefs.putInt("sizex",20000);prefs.putInt("sizey",20000);
  chocco[] holder=new chocco[1];
  EventQueue.invokeAndWait(()->{holder[0]=new chocco(false);holder[0].setVisible(true);});
  chocco manager=holder[0];Robot robot=new Robot();robot.waitForIdle();
  try{
   Rectangle screen=manager.getGraphicsConfiguration().getBounds();
   check(manager.getWidth()<=Math.max(640,screen.width)&&manager.getHeight()<=Math.max(480,screen.height),"persisted huge frame restored without screen cap");
   CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
   EventQueue.invokeLater(()->{entered.countDown();try{release.await();}catch(InterruptedException ex){Thread.currentThread().interrupt();}});
   check(entered.await(5,TimeUnit.SECONDS),"EDT gate");
   AtomicInteger applied=new AtomicInteger(),last=new AtomicInteger();Method publish=chocco.class.getDeclaredMethod("publishStatus",Runnable.class);publish.setAccessible(true);
   try{for(int i=0;i<12000;i++){final int n=i;chocco.appendLog(chocco.txtGlobalChat,"x".repeat(4096)+" #"+i);publish.invoke(manager,(Runnable)()->{applied.incrementAndGet();last.set(n);});}
    long[] q=ManagerLogBuffer.diagnostics();check(q[0]<=1024&&q[1]<=256*1024&&q[3]==1,"real manager EDT backlog unbounded");
   }finally{release.countDown();}
   robot.waitForIdle();ManagerLogBufferTest.settle();check(applied.get()==1&&last.get()==11999,"status update backlog/coalescing");
   check(chocco.txtGlobalChat.getDocument().getLength()<=64*1024,"real manager log document unbounded");
   long[] retained=new long[2];long[][] process=new long[2][];
   for(int phase=0;phase<2;phase++){
    for(int i=0;i<200;i++){final int n=i;EventQueue.invokeAndWait(()->manager.setBounds(10+n%60,10+n%50,640+n%500,480+n%300));if(i%10==0)robot.waitForIdle();}
    robot.waitForIdle();retained[phase]=heap();process[phase]=WindowsUiMemory.read();System.out.println("real manager resize/move phase="+phase+" postGcHeap="+retained[phase]+" committedHeap="+Runtime.getRuntime().totalMemory()+" [workingSet,privateBytes,GDI,USER]="+java.util.Arrays.toString(process[phase]));
   }
   check(retained[1]-retained[0]<16L*1024*1024,"retained heap grew across equal resize phases");
   if(process[0].length!=0){
    check(process[0][3]>0,"Windows GUI counters unavailable");
    check(process[1][1]-process[0][1]<64L*1024*1024,"Windows private memory grew across equal resize phases");
    check(process[1][2]-process[0][2]<32&&process[1][3]-process[0][3]<8,"Windows GDI/USER resource growth");
   }
   Method pools=chocco.class.getDeclaredMethod("showPoolReport");pools.setAccessible(true);
   for(int i=0;i<8;i++){
    EventQueue.invokeAndWait(()->{try{pools.invoke(manager);}catch(Exception e){throw new AssertionError(e);}});robot.waitForIdle();
    EventQueue.invokeAndWait(()->{for(Window w:manager.getOwnedWindows())if(w instanceof JDialog)w.dispose();});
   }
  }finally{
   EventQueue.invokeAndWait(manager::dispose);prefs.remove("sizex");prefs.remove("sizey");
  }
  EventQueue.invokeAndWait(()->{});check(!manager.isDisplayable()&&!chocco.serverstart,"GUI lifecycle or unintended boot");
  System.out.println("PASS actual Swing manager:oversized saved bounds capped,12000 blocked-EDT updates coalesced/bounded,400 real moves/resizes,retained heap bound,8 pool-dialog lifecycles,no game/DB startup");
  System.exit(0);
 }
}
