#!/usr/bin/env python3
"""Compile & exercise real protection-scroll method bodies + real shared receipt entrypoint.
Typed stubs model asynchronous COMMIT, ROLLBACK, notification faults, and identity guards.
Not a substitute for MySQL/in-game tests.
"""
from pathlib import Path
import re, subprocess, tempfile
import sys
project=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parents[2]
root=project/'src/l1j/server/server/model/item/function'
classes=['EmblemProtectionScrolls','InsigniaProtectionScrolls','OrimProtectionScrolls','RoomtisProtectionScrolls','ShoulderProtectionScrolls','SnapperProtectionScrolls']
def extract(source, token):
    start=source.index(token); begin=source.index('{',start); depth=0
    for end in range(begin,len(source)):
        if source[end]=='{':depth+=1
        elif source[end]=='}':
            depth-=1
            if depth==0:return source[start:end+1]
    raise AssertionError('bad source nesting')
with tempfile.TemporaryDirectory() as tmp:
    rootdir=Path(tmp)
    def write(name,s):
        p=rootdir/(name+'.java');p.write_text(s,encoding='utf8')
    src=(root/'ItemReceiptOperations.java').read_text(encoding='utf8')
    helper=extract(src,'    public static void UseProtectionEnchant(')
    notify=extract(src,'    private static void protectionNotice(')
    write('ItemReceiptOperations', '''import java.util.logging.*;
class ItemReceiptOperations {
 static final Logger ITEM_USE2_LOG=Logger.getLogger("protected-enchant-test");
 static int submitted,successes,failures; static boolean commit=true;
 static Runnable pending;
 static void UseAtomicEnchantSuccess(L1PcInstance p,L1ItemInstance s,L1ItemInstance t,CIOClient c,Runnable cb){
   if(!SpecialEnchantScrolls.isOwnedScrollTarget(p,s,t))return;
   submitted++;successes++;pending=()->{if(commit){s.count--;t.enchant++;ItemActionCompletion.finishCommittedConsumable(p,s);if(cb!=null)cb.run();}};
 }
 static void UseDurableConsume(L1PcInstance p,L1ItemInstance s,Runnable cb){
   submitted++;failures++;pending=()->{if(commit){s.count--;if(cb!=null)cb.run();}};
 }
 '''+helper+'\n'+notify+'\n}')
    write('Mocks','''class CIOClient {}
class L1ItemInstance {
 int id,count=1,enchant=1,bless; Item item=new Item();
 L1ItemInstance(int x){id=x;} int getId(){return id;}int getCount(){return count;}
 int getEnchantLevel(){return enchant;}int getBless(){return bless;}Item getItem(){return item;}
 static class Item {int type=9;int getType(){return type;}}
}
class L1PcInstance {
 L1ItemInstance scroll=new L1ItemInstance(1),target=new L1ItemInstance(2);
 Inventory inv=new Inventory(); int textCount;
 Inventory getInventory(){return inv;} CIOClient getNetConnection(){return null;}
 void sendPackets(String s){textCount++;}
 class Inventory {L1ItemInstance getItem(int id){return id==1?scroll:id==2?target:null;}}
}
class SpecialEnchantScrolls {
 static boolean isOwnedScrollTarget(L1PcInstance p,L1ItemInstance s,L1ItemInstance t){
  return p!=null&&s!=null&&t!=null&&s!=t&&s.count>0&&t.count>0&&p.getInventory().getItem(s.id)==s&&p.getInventory().getItem(t.id)==t;
 }
}
class ItemActionCompletion {static int calls;static void finishCommittedConsumable(L1PcInstance p,L1ItemInstance s){
 if(s.count!=0)throw new AssertionError("complete before debit");calls++;}}
class SC_ServerMessage {static int calls;static void send(L1PcInstance pc,int id){calls++;}}
class SC_SystemMessage {static int calls;static boolean throwNext;
 static void send(L1PcInstance pc,String msg){calls++;if(throwNext){throwNext=false;throw new RuntimeException("test notice failure");}}}
class MJRnd {static boolean win;static int calls;static boolean isWinning(int total,int chance){calls++;return win;}}
class Config {
 static int SentenceLevel=10,Insignia_Level=10,RoomtisLevel=10,Should_Level=10,SnapperLevel=10,악세사리=10;
 static int[] Sentence={50,50,50,50},Insignia={50,50,50,50},Roomtis={50,50,50,50},Should={50,50,50,50},Sanpper={50,50,50,50},bless_orim={50,50,50,50};
}''')
    signatures=[]
    for c in classes:
        prod=(root/(c+'.java')).read_text(encoding='utf8')
        methods=re.findall(r'public static void (enchant_\w+)\(',prod)
        for method in methods:signatures.append((c,method))
        write(c,'final class '+c+' {\n'+ '\n'.join(extract(prod,'public static void '+m+'(') for m in methods)+'\n}')
    test='''import java.lang.reflect.*;
class TestHarness {
 static int cases;static void ok(boolean good,String name){cases++;if(!good)throw new AssertionError(cases+" "+name);}
 static void run(String type,String method,boolean guaranteed,boolean requestedWin,boolean doCommit,boolean packetFailure,boolean stale,boolean overMax,boolean cursed) throws Exception {
    L1PcInstance p=new L1PcInstance();L1ItemInstance s=p.scroll,t=p.target;
    if(stale)p.target=new L1ItemInstance(3);
    if(overMax)t.enchant=10;
    if(cursed)t.bless=128;
    MJRnd.win=requestedWin; ItemReceiptOperations.commit=doCommit;ItemReceiptOperations.pending=null;
    SC_SystemMessage.throwNext=packetFailure;
    int prior=ItemReceiptOperations.submitted, finish=ItemActionCompletion.calls;
    int notice=SC_SystemMessage.calls;int roll=MJRnd.calls;
    Method target=Class.forName(type).getDeclaredMethod(method,L1PcInstance.class,L1ItemInstance.class,L1ItemInstance.class);
    target.setAccessible(true);target.invoke(null,p,s,t);
    boolean denied=stale||overMax||cursed;
    ok(ItemReceiptOperations.submitted==prior+(denied?0:1),type+" "+method+" submit");
    ok(s.count==1&&t.enchant==(overMax?10:1),"not applied prior to commit");
    if(!denied){
      ok(ItemReceiptOperations.pending!=null,"pending callback");
      ItemReceiptOperations.pending.run();
      boolean applied=doCommit;
      ok(s.count==(applied?0:1),"debit only after commit");
      ok(t.enchant==1+(applied&&(guaranteed||requestedWin)?1:0),"enchant change only after commit and win");
      ok(ItemActionCompletion.calls==finish+(applied?1:0),"finish only after commit");
      ok(SC_SystemMessage.calls==notice+(applied&&(!guaranteed||!requestedWin)&&!guaranteed?1:0),"notice only after commit");
    } else {
      ok(s.count==1,"denied no scroll debit");
      ok(ItemActionCompletion.calls==finish,"denied no finish");
      ok(MJRnd.calls==roll,"denied before RNG");
    }
 }
 public static void main(String[] args)throws Exception {
'''
    for cls,method in signatures:
        guaranteed = method.endswith('1')
        # none of probability methods end in 1
        test+=f'  for(boolean win:new boolean[]{{false,true}})for(boolean commit:new boolean[]{{false,true}})for(boolean packet:new boolean[]{{false,true}})\n'
        test+=f'    run("{cls}","{method}",{str(guaranteed).lower()},win,commit,packet,false,false,false);\n'
        test+=f'  run("{cls}","{method}",{str(guaranteed).lower()},true,true,false,true,false,false);\n'
        test+=f'  run("{cls}","{method}",{str(guaranteed).lower()},true,true,false,false,true,false);\n'
        test+=f'  run("{cls}","{method}",{str(guaranteed).lower()},true,true,false,false,false,true);\n'
    test+='''  System.out.println("PROTECTION_TYPED_JAVA_CASES="+cases+" PASS");
 }
}'''
    write('TestHarness',test)
    cmd=['javac','--release','21','-encoding','utf8','-d',str(rootdir)]+[str(p) for p in rootdir.glob('*.java')]
    c=subprocess.run(cmd,text=True,capture_output=True)
    if c.returncode:raise SystemExit('JAVAC failed\n'+c.stdout+c.stderr)
    c=subprocess.run(['java','-cp',str(rootdir),'TestHarness'],text=True,capture_output=True)
    if c.returncode:raise SystemExit('JAVA failed\n'+c.stdout+c.stderr)
    print('PROTECTION_METHODS=',len(signatures),'\n'+c.stdout.strip())
