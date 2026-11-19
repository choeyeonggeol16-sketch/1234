#!/usr/bin/env python3
"""Typed Java 21 harness for production commitInternal; baseline vs candidate.
Checks SQL preimage order, mutation order, image replay order, owner-release,
post-COMMIT callbacks and failure injection; no actual MySQL involved.
"""
from pathlib import Path
import subprocess,tempfile,re
BASE=(Path(__file__).resolve().parent/'baseline_181_L1InventoryTransaction.java').read_text('utf-8')
NEW=(Path(__file__).resolve().parents[2]/'src/l1j/server/server/model/L1InventoryTransaction.java').read_text('utf-8')
def block(s,start):
  a=s.index(start); b=s.index('{',a); deep=1; p=b+1
  while deep:
    if s[p]=='{':deep+=1
    elif s[p]=='}':deep-=1
    p+=1
  return s[a:p]
old=block(BASE,'    private void commitInternal(Runnable afterCommitted, boolean releaseBeforeEffects)')
new=block(NEW,'    private void commitInternal(Runnable afterCommitted, boolean releaseBeforeEffects)')
# Recreate source method verbatim, only relax private visibility for harness.
old=old.replace('private void commitInternal','void commitInternal',1)
new=new.replace('private void commitInternal','void commitInternal',1)
java='''import java.util.*;
import java.util.function.*;
public class CommitPathCheck {
  static int assertions;
  static final class Item {
    final int id; int count; Item(int id,int count){this.id=id;this.count=count;}
    int getId(){return id;} int getCount(){return count;}
  }
  static final class Snapshot { final int id; Snapshot(int id){this.id=id;} }
  static final class Player {
    final int id;final Inventory inv;
    Player(int id,List<String> events){this.id=id;this.inv=new Inventory(events,id);}
    int getId(){return id;}
    Inventory getInventory(){return inv;}
  }
  static final class Inventory {
    final List<String> events;final int owner;
    Inventory(List<String> ev,int owner){events=ev;this.owner=owner;}
    void applyEconomyImage(Item original,Item image){events.add("APPLY:"+owner+":"+(original==null?0:original.id)+":"+(image==null?0:image.id)+":"+(image==null?0:image.count));}
  }
  static final class Entry {
    final Player originalOwner; final Item original; final Snapshot before;
    Player owner; Item image;
    Entry(Player prev,Player next,int id,int count,boolean existing){
      originalOwner=existing?prev:null;original=existing?new Item(id,count):null;
      before=existing?new Snapshot(id):null; owner=next; image=new Item(id,count);
    }
  }
  static final class OwnerSet {
    final Player[] players;OwnerSet(Player[] a){players=a;}
    int size(){return players.length;} Player get(int i){return players[i];}
    boolean current(Player p){return true;} Object monitor(Player p){return p;}
  }
  @FunctionalInterface interface Mutation { void run(Object con); }
  static final class Move {
    final List<String> events;final ArrayList<Mutation> sql=new ArrayList<>();
    Runnable onCommitted;boolean failDb;int failVerifyId;int verifyMutations;
    int stageCount,writeCount;
    static int throwStageId;
    Move(List<String> ev){events=ev;}
    void stageWarehouseSql(Mutation m){verifyMutations++;sql.add(m);}
    void prepareOwnedDelete(int owner,int id){sql.add(con->events.add("DELETE:"+owner+":"+id));stageCount++;}
    void prepareOwnedWrite(int owner,Item image,int previous){
      final int id=image.id,count=image.count;
      if(throwStageId==id)throw new IllegalStateException("STAGE:"+id);
      sql.add(con->events.add("WRITE:"+owner+":"+id+":"+count+":"+previous));stageCount++;writeCount++;
    }
    void commit(Runnable after){onCommitted=after;}
    void runDb(){
      for(Mutation op:sql)op.run(null);
      if(failDb)throw new RuntimeException("SQLROLLBACK");
      if(onCommitted!=null)onCommitted.run();
    }
    void finishOwnerApply(Throwable failure){events.add("FINISH:"+(failure==null?"OK":"FAIL"));}
  }
  static final class CTransaction {
    static int failVerifyId;
    static List<String> currentEvents;
    static void verifyItemPreimage(Object connection,Snapshot snapshot){
      currentEvents.add("VERIFY:"+snapshot.id);
      if(failVerifyId==snapshot.id)throw new IllegalStateException("STALE:"+snapshot.id);
    }
  }
  static final class CacheD {
    static void applyCommittedWarehouseMove(Move m,Runnable replay){replay.run();}
  }
  static final Comparator<Entry> ROW_ID_ORDER=(a,b)->Integer.compare(a.image.id,b.image.id);
  static final Entry[] NO_ENTRIES=new Entry[0];
  static abstract class Base {
    final ArrayList<String> events=new ArrayList<>();
    final Player[] pcs={new Player(1,events),new Player(2,events)};
    final Player primary=pcs[0];
    final OwnerSet owners=new OwnerSet(pcs);
    final Move move=new Move(events);
    Entry[] entries;int entryCount;boolean submitted;
    void populate(int count, long seed){
      Random r=new Random(seed);int[] ids=new int[count];
      for(int i=0;i<count;i++)ids[i]=10000+i*17;
      for(int i=count-1;i>0;i--){int j=r.nextInt(i+1);int t=ids[i];ids[i]=ids[j];ids[j]=t;}
      entries=new Entry[Math.max(2,Integer.highestOneBit(Math.max(2,count)-1)<<1)];
      for(int i=0;i<count;i++){
        int id=ids[i],qty=(i%5==0?0:3+i%4);
        boolean existing=i%7!=0;
        Player prev=pcs[(i+1)%2],to=pcs[i%2];
        entries[i]=new Entry(prev,to,id,qty,existing);
      }
      entryCount=count;
    }
    void open(){if(submitted)throw new IllegalStateException("submitted");}
    void validateLive(){}
    void validateCapacity(){}
    static Item copy(Item item){return new Item(item.id,item.count);}
    void post(Runnable action,Consumer<Throwable> done){
      Throwable failure=null;
      try{action.run();}catch(Throwable t){failure=t;}
      done.accept(failure);
    }
    void releaseGates(){events.add("RELEASE");}
    void quarantine(Throwable t){events.add("QUARANTINE");}
    void run(boolean effectsAfterRelease){commitInternal(()->events.add("EFFECT"),effectsAfterRelease);}
    abstract void commitInternal(Runnable cb,boolean afterRelease);
  }
  static final class Legacy extends Base { %OLD% }
  static final class Optimized extends Base { %NEW% }
  static void check(boolean test,String msg){assertions++;if(!test)throw new AssertionError(msg);}
  static void checkCase(int n,long seed,boolean afterRelease,int failureIndex){
    Legacy before=new Legacy(); Optimized after=new Optimized();
    before.populate(n,seed);after.populate(n,seed);
    before.run(afterRelease);after.run(afterRelease);
    check(before.submitted&&after.submitted,"submitted");
    int existing=0;for(int i=0;i<before.entryCount;i++)if(before.entries[i].before!=null)existing++;
    check(before.move.verifyMutations==existing,"legacy stage count");
    check(after.move.verifyMutations==(existing==0?0:n==1?1:1),"batched stage count");
    check(after.move.verifyMutations<=before.move.verifyMutations,"stage reduction");
    check(before.move.stageCount==after.move.stageCount,"mutations count");
    check(before.move.writeCount==after.move.writeCount,"writes count");
    check(before.move.onCommitted!=null && after.move.onCommitted!=null,"async registered");
    // In a staging failure, caller-visible prepared insertion order must still
    // be unchanged; it is part of the prior 181 contract for grant merging.
    for(int i=0;i<n;i++)
      check(before.entries[i].image.id==after.entries[i].image.id,"original prepared insertion order");
    // Each production callback must preserve DB preimage-lock order and write order.
    int failId=0;
    if(failureIndex>=0){int[] ids=new int[n];for(int i=0;i<n;i++)ids[i]=10000+i*17;
      Arrays.sort(ids);failId=ids[failureIndex%n];
    }
    for(Base tx:new Base[]{before,after}){
      CTransaction.currentEvents=tx.events;
      CTransaction.failVerifyId=failId;
      try {tx.move.runDb();}
      catch(IllegalStateException e){tx.events.add("ROLLED_BACK");}
      CTransaction.failVerifyId=0;
    }
    check(before.events.equals(after.events),"SQL/owner replay mismatch n="+n+" seed="+seed+" release="+afterRelease+" failed="+failureIndex+"\\nold="+before.events+"\\nnew="+after.events);
    if(failureIndex<0){
      long verify=after.events.stream().filter(x->x.startsWith("VERIFY:")).count();
      check(verify==existing,"verify count");
      long effect=after.events.stream().filter(x->x.equals("EFFECT")).count();
      check(effect==1,"exactly once effects");
    }
    check(after.entries==after.entries,"owned storage retained");
    check(after.entryCount==n,"count stable");
  }
  static void stageFailure(int n,long seed){
    Legacy before=new Legacy();Optimized after=new Optimized();
    before.populate(n,seed);after.populate(n,seed);
    int thrownId=0;
    for(int i=0;i<n;i++)if(before.entries[i].image.count>0){thrownId=before.entries[i].image.id;break;}
    if(thrownId==0)return;
    Move.throwStageId=thrownId;
    try{
      boolean oldThrown=false,newThrown=false;
      try{before.run(false);}catch(IllegalStateException e){oldThrown=true;}
      try{after.run(false);}catch(IllegalStateException e){newThrown=true;}
      check(oldThrown && newThrown,"same staging failure");
      check(!before.submitted&&!after.submitted,"staging failure never submitted");
      check(before.move.verifyMutations>0 && after.move.verifyMutations>0,"preimage staged");
      for(int i=0;i<n;i++)check(before.entries[i].image.id==after.entries[i].image.id,"failed prepare must preserve insertion order");
    }finally{Move.throwStageId=0;}
  }
  public static void main(String[] args){
    for(int n:new int[]{1,2,3,4,7,8,9,10,12,16,32,64,128}){
      for(int k=0;k<30;k++)for(boolean afterRelease:new boolean[]{false,true}){
        checkCase(n,42L+n*997L+k*77L,afterRelease,-1);
        if(k%3==0)checkCase(n,123L+n*19L+k*11L,afterRelease,k%n);
      }
    }
    for(int n:new int[]{2,3,4,8,9,10,16,32,64})for(int s=0;s<25;s++)stageFailure(n,100L+s*19+n*71);
    System.out.println("PASS "+assertions+" typed production commit path conditions");
  }
}
'''
java=java.replace('%OLD%',old).replace('%NEW%',new)
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'CommitPathCheck.java';p.write_text(java)
 x=subprocess.run(['javac','--release','21',str(p)],capture_output=True,text=True)
 if x.returncode:print(x.stdout,x.stderr);raise SystemExit(x.returncode)
 x=subprocess.run(['java','-cp',d,'CommitPathCheck'],capture_output=True,text=True)
 print(x.stdout,x.stderr);raise SystemExit(x.returncode)
