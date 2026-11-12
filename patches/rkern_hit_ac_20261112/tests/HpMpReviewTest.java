import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.cs.packet.CS_ReturnStatus;
import l1j.server.server.storage.CharacterStorage;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;

/** Recheck the real save facade and reset handler, not just their helpers. */
public class HpMpReviewTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static class ResetPc extends HpMpTest.Pc {
  boolean stopAfterVitals,requireResetLock;
  @Override public void start_teleport(int x,int y,int map,int heading,int effect,boolean fx,boolean skill){}
  @Override public void resetBaseAc(){if(stopAfterVitals)throw new StopBeforeAsyncSave();}
  @Override public void RenewStat(){}
  @Override public void CheckStatus(){}
  @Override public synchronized int getReturnStat(){
   if(requireResetLock)try {
    Field f=L1PcInstance.class.getDeclaredField("userStateLock");f.setAccessible(true);
    check(((l1j.server.server.model.character.CYieldLock)f.get(this)).isHeldByCurrentThread(),"reset handler reads/mutates staged state without the snapshot lock");
   }catch(ReflectiveOperationException e){throw new AssertionError(e);}
   return super.getReturnStat();
  }
 }
 static class StopBeforeAsyncSave extends RuntimeException {}
 static class Client extends CIOClient {
  L1PcInstance pc;int kicks;
  Client(){super(null,false);}
  @Override public L1PcInstance getActiveChar(){return pc;}
  @Override public void kick(){kicks++;}
  @Override public boolean isConnected(){return false;}
 }
 static ResetPc resetPc()throws Exception {
  ResetPc p=new ResetPc();p.setName("reset-review");p.setType(3);p.setLevel(80);p.setHighLevel(80);
  p.setExp(ExpTable.getExpByLevel(80));p.setReturnStat(p.getExp());
  p.addBaseMaxHp((short)1000);p.addBaseMaxMp(1000);
  p.rst=new L1ReturnStatTemp();p.rst.level=1;p.rst.baseHp=12;p.rst.baseMp=8;
  p.rst.basestr=8;p.rst.basedex=7;p.rst.basecon=12;p.rst.baseint=18;p.rst.basewis=18;p.rst.basecha=8;
  return p;
 }
 static void resetSnapshot()throws Exception {
  ResetPc p=resetPc();
  try{new CharacterSnapshot(p);throw new AssertionError("snapshot accepted staged reset stats with old growth history");}
  catch(IllegalStateException expected){}
  CharacterTable table=CharacterTable.getInstance();Field f=CharacterTable.class.getDeclaredField("_charStorage");f.setAccessible(true);Object before=f.get(table);
  AtomicInteger writes=new AtomicInteger();
  CharacterStorage fake=(CharacterStorage)Proxy.newProxyInstance(HpMpReviewTest.class.getClassLoader(),new Class[]{CharacterStorage.class},(o,m,a)->{if(m.getName().equals("storeCharacter"))writes.incrementAndGet();return null;});
  try{f.set(table,fake);table.storeCharacter(p);check(writes.get()==0,"save facade wrote an incomplete reset");}
  finally{f.set(table,before);}
  p.rst=null;new CharacterSnapshot(p); // ReturnStat START marker alone is a valid durable state.
  System.out.println("PASS direct snapshot rejects staged reset; real save facade skips it under lock; START marker remains saveable");
 }
 static void earlyFinish()throws Exception {
  for(int type:new int[]{2,3}) {
   ResetPc p=resetPc();Client c=(Client)HpMpTest.unsafe(Client.class);c.pc=p;
   String old=p.encodeHpMpGrowth();CS_ReturnStatus.handle(c,type,8,0,0);
   check(c.kicks==1&&p.getBaseMaxHp()==1000&&p.getBaseMaxMp()==1000
       &&java.util.Objects.equals(old,p.encodeHpMpGrowth()),"incomplete reset finalized through packet type "+type);
  }
  System.out.println("PASS early type2/type3 finish rejected before HP/MP/history mutation");
 }
 static void completedResetVitals()throws Exception {
  ResetPc p=resetPc();p.rst.wis=27;
  Method up=CS_ReturnStatus.class.getDeclaredMethod("statup",L1PcInstance.class);up.setAccessible(true);
  while(p.rst.level<80)up.invoke(null,p);
  p.stopAfterVitals=true;
  Method finish=CS_ReturnStatus.class.getDeclaredMethod("applyFinalAndFinish",CIOClient.class,L1PcInstance.class);finish.setAccessible(true);finish.invoke(null,null,p);
  check(p.getAbility().getWis()==45,"reset final allocation missing");
  check(p.getCurrentHp()==p.getMaxHp()&&p.getCurrentMp()==p.getMaxMp(),"reset fills vitals before permanent bonus restored");
  System.out.println("PASS real final reset path fills HP/MP after permanent WIS45 bonus (stopped before later world/async effects)");
 }
 static void packetStaging()throws Exception {
  ResetPc p=resetPc();p.rst=null;p.requireResetLock=true;
  Client c=(Client)HpMpTest.unsafe(Client.class);c.pc=p;
  int[] stats={8,18,18,7,12,8};long packed=0;
  for(int i=0;i<stats.length;i++)packed|=(long)stats[i]<<(i*8);
  CS_ReturnStatus.handle(c,1,0,0,packed);
  check(p.rst!=null&&p.rst.level==1,"valid reset START failed");
  for(int i=2;i<=80;i++)CS_ReturnStatus.handle(c,2,0,0,0);
  check(p.rst.level==80&&c.kicks==0,"valid reset level messages rejected");
  for(int i=2;i<=80;i++)check(p.rst.hpMpGrowth.known(i),"packet stage omitted earned level "+i);
  Method valid=CS_ReturnStatus.class.getDeclaredMethod("hasCompleteResetGrowth",L1PcInstance.class);valid.setAccessible(true);
  check((Boolean)valid.invoke(null,p),"valid completed stage rejected by finish guard");
  System.out.println("PASS public reset handler uses snapshot lock; 79 actual level messages build complete history accepted by finish guard");
 }
 static void facadeLockOrder()throws Exception {
  HpMpPersistenceTest.SnapshotPc p=new HpMpPersistenceTest.SnapshotPc();p.setName("lock-review");p.setLevel(1);p.pause=true;
  CharacterTable table=CharacterTable.getInstance();Field f=CharacterTable.class.getDeclaredField("_charStorage");f.setAccessible(true);Object before=f.get(table);
  AtomicReference<Throwable> failure=new AtomicReference<>();CountDownLatch facadeEntered=new CountDownLatch(1);
  CharacterStorage fake=(CharacterStorage)Proxy.newProxyInstance(HpMpReviewTest.class.getClassLoader(),new Class[]{CharacterStorage.class},(o,m,a)->{
   if(m.getName().equals("storeCharacter")){facadeEntered.countDown();new CharacterSnapshot((L1PcInstance)a[0]);}return null;
  });
  Thread snapshot=new Thread(()->{try{new CharacterSnapshot(p);}catch(Throwable t){failure.set(t);}},"review-snapshot");
  Thread save=new Thread(()->{try{table.storeCharacter(p);}catch(Throwable t){failure.set(t);}},"review-save-facade");
  snapshot.setDaemon(true);save.setDaemon(true);
  try {
   f.set(table,fake);snapshot.start();check(p.entered.await(2,TimeUnit.SECONDS),"snapshot did not acquire state lock");
   save.start();facadeEntered.await(150,TimeUnit.MILLISECONDS);p.release.countDown();
   snapshot.join(1500);save.join(1500);
   check(!snapshot.isAlive()&&!save.isAlive(),"CharacterTable.storeCharacter PC-monitor/user-state-lock deadlock");
   if(failure.get()!=null)throw new AssertionError(failure.get());
  }finally{p.release.countDown();f.set(table,before);}
  System.out.println("PASS actual CharacterTable.storeCharacter / CharacterSnapshot contention completes without inverted PC-monitor/state-lock order");
 }
 public static void main(String[] args)throws Exception {
  switch(args.length==0?"all":args[0]){
   case "snapshot":resetSnapshot();break;
   case "finish":earlyFinish();break;
   case "vitals":completedResetVitals();break;
   case "locks":facadeLockOrder();break;
   default:resetSnapshot();earlyFinish();completedResetVitals();packetStaging();facadeLockOrder();
  }
 }
}
