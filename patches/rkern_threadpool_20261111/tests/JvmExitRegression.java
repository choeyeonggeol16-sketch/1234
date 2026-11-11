import java.util.concurrent.TimeUnit;
import l1j.server.server.ThreadPool;
public class JvmExitRegression {
 public static void main(String[] args) {
  ThreadPool pool=ThreadPool.getInstance();
  Runtime.getRuntime().addShutdownHook(new Thread(()->{
   if(!pool.shutdown(3,TimeUnit.SECONDS)) {
    System.err.println("FAIL JVM shutdown hook waited on the System.exit countdown");
    Runtime.getRuntime().halt(3);
   }
   System.out.println("PASS actual System.exit countdown and JVM shutdown hook complete without circular wait");
  },"test-jvm-shutdown-hook"));
  pool.startDedicatedThread(new Thread(()->{
   pool.detachCurrentDedicatedThreadForJvmExit();
   System.exit(0);
  },"test-countdown"));
 }
}
