package l1j.server.swing;
import java.awt.*;import java.awt.image.*;import java.util.concurrent.*;import javax.swing.*;
public final class ManagerLogBufferTest {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void settle()throws Exception{long due=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(ManagerLogBuffer.diagnostics()[3]!=0&&System.nanoTime()<due)EventQueue.invokeAndWait(()->{});check(ManagerLogBuffer.diagnostics()[3]==0,"screen drain stuck");}
 public static void main(String[] args)throws Exception{
  JTextArea[] area=new JTextArea[1];EventQueue.invokeAndWait(()->{area[0]=new JTextArea();ManagerLogBuffer.install(area[0]);});
  CountDownLatch blocked=new CountDownLatch(1),release=new CountDownLatch(1);
  EventQueue.invokeLater(()->{blocked.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});check(blocked.await(5,TimeUnit.SECONDS),"EDT blocker");
  try{for(int i=0;i<12000;i++)ManagerLogBuffer.append(area[0],"x".repeat(8192)+" #"+i);
   long[] p=ManagerLogBuffer.diagnostics();check(p[0]<=ManagerLogBuffer.MAX_PENDING&&p[1]<=ManagerLogBuffer.MAX_PENDING_CHARS&&p[2]>0&&p[3]==1,"unbounded blocked EDT backlog");
   System.out.println("Bounded blocked EDT queue:"+java.util.Arrays.toString(p));
  }finally{release.countDown();}settle();
  EventQueue.invokeAndWait(()->{check(area[0].getDocument().getLength()<=ManagerLogBuffer.MAX_DOCUMENT_CHARS&&area[0].getLineCount()<=ManagerLogBuffer.MAX_LINES,"document bounds");check(area[0].getText().contains("#11999"),"newest display lost");
   area[0].setText("z".repeat(1000000));check(area[0].getDocument().getLength()<=ManagerLogBuffer.MAX_DOCUMENT_CHARS,"direct document insert bypassed cap");area[0].setText("");});
  for(int i=0;i<600;i++)ManagerLogBuffer.append(area[0],"line"+i);settle();
  BufferedImage image=new BufferedImage(1400,900,BufferedImage.TYPE_INT_RGB);
  for(int phase=0;phase<2;phase++){
   EventQueue.invokeAndWait(()->{for(int i=0;i<400;i++){area[0].setSize(640+i%700,480+i%400);Graphics2D g=image.createGraphics();try{area[0].paint(g);}finally{g.dispose();}}});
   System.gc();Thread.sleep(50);System.out.println("resize phase heap bytes="+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()));
  }
  image.flush();System.out.println("PASS production screen logger:12000 updates while EDT blocked,bounded entries/chars,latest retained,64KiB/300-line document,800 resize/paint cycles");
 }
}
