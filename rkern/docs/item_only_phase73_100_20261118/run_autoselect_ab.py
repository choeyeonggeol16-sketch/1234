#!/usr/bin/env python3
"""Actual original/optimized auto-select method type-compilation and deterministic shuffle parity."""
from pathlib import Path
import subprocess,tempfile,re,zipfile
DOC=Path(__file__).resolve().parent
ROOT=DOC.parents[1]
FILES=['L1CardLPast','L1CardHPast']
with tempfile.TemporaryDirectory(prefix='rkern_autoselect_') as t:
 w=Path(t);p=w/'src';p.mkdir()
 original='\n'.join((DOC/'baseline'/f'{x}.java').read_text() for x in FILES)
 getters=sorted(set(re.findall(r'acc\.(get\w+)\(',original)))
 for suffix,dir in [('Old',DOC/'baseline'),('New',ROOT/'src/l1j/server/server/model/item/Card')]:
  for name in FILES:
   s=(dir/f'{name}.java').read_text()
   s=s.replace('public class '+name+' {','public class '+suffix+name+' {')
   for var in ('eligibleIndices','eligibleCards'):
    s=s.replace('Collections.shuffle('+var+');','Collections.shuffle('+var+',new java.util.Random(Test.seed));')
   s=s.replace('L1CardB.cardlist(pc);','Test.events.append("VIEW-B;");').replace('L1CardA.cardlist(pc);','Test.events.append("VIEW-A;");')
   target=p/'l1j/server/server/model/item/Card'/f'{suffix}{name}.java'
   target.parent.mkdir(parents=True,exist_ok=True);target.write_text(s)
 def put(name,src):
  dst=p/name;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_text(src)
 account='''package l1j.server.server;
import l1j.server.server.model.item.Card.Test;
public class Account {
 final int seed;public final int[] selected=new int[4];
 public Account(int seed){this.seed=seed;}
 private int read(String s){Test.events.append(s).append(';');return Math.floorMod(seed*29+s.hashCode(),7);}
'''
 account+='\n'.join('public int '+n+'(){return read("'+n+'");}' for n in getters)+'\n'
 account+='\n'.join('public void setInfo_card_'+x+'(int v){selected['+str(i)+']=v;Test.events.append("set'+x+'="+v+";");}' for i,x in enumerate('abcd'))+'\n}'
 put('l1j/server/server/Account.java',account)
 put('l1j/server/server/model/Instance/L1PcInstance.java','''package l1j.server.server.model.Instance;import l1j.server.server.Account;
public class L1PcInstance{final Account a;public L1PcInstance(Account a){this.a=a;}public Account getAccount(){return a;}}''')
 put('l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java','''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.item.Card.Test;import l1j.server.server.model.Instance.L1PcInstance;
public class SC_SystemMessage{public static void send(L1PcInstance pc,String msg){Test.events.append("MSG:").append(msg).append(';');}}''')
 put('l1j/server/server/model/item/Card/Test.java','''package l1j.server.server.model.item.Card;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import java.util.Arrays;
public class Test {
 public static long seed;
 public static final StringBuilder events=new StringBuilder();
 private static String run(int kind,boolean updated,int s){
   seed=s*43L+kind*83L;events.setLength(0);
   Account account=new Account(s);L1PcInstance pc=new L1PcInstance(account);
   if(kind==0){if(updated)NewL1CardLPast.L1PastSelct(pc);else OldL1CardLPast.L1PastSelct(pc);}
   else{if(updated)NewL1CardHPast.L1PastSelct(pc);else OldL1CardHPast.L1PastSelct(pc);}
   return events.toString()+"|"+Arrays.toString(account.selected);
 }
 public static void main(String[] args){
  long cases=0;for(int kind=0;kind<2;kind++)for(int s=0;s<15000;s++){
    String a=run(kind,false,s),b=run(kind,true,s);
    if(!a.equals(b))throw new AssertionError("kind="+kind+" seed="+s+" old="+a+" new="+b);
    cases++;
  }System.out.println("PASS: "+cases+" deterministic auto-select cases including getters, selection, error, UI order");
 }
}''')
 java=[str(x) for x in p.rglob('*.java')]
 cp=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(w/'classes'),*java],capture_output=True,text=True)
 if cp.returncode:raise RuntimeError(cp.stderr)
 cp=subprocess.run(['java','-cp',str(w/'classes'),'l1j.server.server.model.item.Card.Test'],capture_output=True,text=True,timeout=80)
 if cp.returncode:raise RuntimeError(cp.stdout+'\n'+cp.stderr)
 print(cp.stdout.strip())
