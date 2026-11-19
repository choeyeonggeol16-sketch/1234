from pathlib import Path
import subprocess,tempfile
old=(Path(__file__).resolve().parent/'baseline_184_L1InventoryTransaction.java').read_text('utf-8')
new=(Path(__file__).resolve().parents[2]/'src/l1j/server/server/model/L1InventoryTransaction.java').read_text('utf-8')
def block(s,prefix):
 a=s.index(prefix);b=s.index('{',a);depth=1;i=b+1
 while depth:
  if s[i]=='{':depth+=1
  elif s[i]=='}':depth-=1
  i+=1
 return s[a:i]
old_failure=block(old,'    private void sqlFailed(Throwable failure)')
new_failure=block(new,'    private void sqlFailed(Throwable failure)')
accept=block(new,'    @Override public void accept(Throwable failure)')
assert old_failure==new_failure
old_failure=old_failure.replace('private void sqlFailed', 'protected void sqlFailed', 1)
java=r'''import java.util.*;
import java.util.function.*;
public class CallbackCheck {
 static int checks;
 static class Move {
   Consumer<Throwable> errorAction;
   void onError(Consumer<Throwable> a){errorAction=Objects.requireNonNull(a);}
   void fail(Throwable failure){errorAction.accept(failure);}
 }
 static abstract class Base {
   final ArrayList<String> log=new ArrayList<>();
   final Move move=new Move();
   Runnable failureAction;
   boolean postThrows;
   void post(Runnable action,Consumer<Throwable> done){
     Throwable error=null;
     try{action.run();}catch(Throwable t){error=t;}
     done.accept(error);
   }
   void quarantine(Throwable t){log.add("quarantine");}
   void releaseGates(){log.add("release");}
   static boolean unknown(Throwable t){return t instanceof Uncertain;}
   class Uncertain extends RuntimeException {}
   %OLD_ERROR%
 }
 static class Old extends Base {
   Old(){move.onError(this::sqlFailed);}
 }
 static class Updated extends Base implements Consumer<Throwable> {
   Updated(){move.onError(this);}
   %ACCEPT%
 }
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 static void runCase(int kind,int callback){
   Old original=new Old();Updated modified=new Updated();
   check(modified.move.errorAction==modified,"direct receiver identity");
   if(callback!=0){
     original.failureAction=()->{original.log.add("userFailure");if(callback==2)throw new RuntimeException("onFailure fail");};
     modified.failureAction=()->{modified.log.add("userFailure");if(callback==2)throw new RuntimeException("onFailure fail");};
   }
   Throwable a=kind==0?new RuntimeException("rollback"):original.new Uncertain();
   Throwable b=kind==0?new RuntimeException("rollback"):modified.new Uncertain();
   original.move.fail(a);modified.move.fail(b);
   check(original.log.equals(modified.log),"error callback events");
   if(kind==0)check(original.log.contains("release")&&!original.log.contains("quarantine"),"rollback releases");
   else check(original.log.contains("quarantine"),"unknown quarantines");
 }
 public static void main(String[] args){
   for(int kind=0;kind<2;kind++)for(int cb=0;cb<3;cb++)for(int i=0;i<1000;i++)runCase(kind,cb);
   System.out.println("PASS "+checks+" production sqlFailed/accept callback conditions");
 }
}
'''.replace('%OLD_ERROR%',old_failure).replace('%ACCEPT%',accept)
with tempfile.TemporaryDirectory() as d:
 p=Path(d,'CallbackCheck.java');p.write_text(java)
 x=subprocess.run(['javac','--release','21',str(p)],capture_output=True,text=True)
 if x.returncode:print(x.stdout,x.stderr);raise SystemExit(x.returncode)
 x=subprocess.run(['java','-cp',d,'CallbackCheck'],capture_output=True,text=True)
 print(x.stdout,x.stderr);raise SystemExit(x.returncode)
