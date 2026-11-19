#!/usr/bin/env python3
"""Typed production methods. Forced debit failures/invalid selections, and reward mapping parity."""
from pathlib import Path
import tempfile, subprocess, re, os
DOC=Path(__file__).resolve().parent
ROOT=DOC.parents[1]
NAMES=['L1CardAlchemy1.java','L1CardAlchemy2.java','L1CardAlchemy3.java']
namespace={'__file__':str(DOC/'run_normal_ab.py')}
base_script=(DOC/'run_normal_ab.py').read_text()
exec(base_script.split('with tempfile.TemporaryDirectory(',1)[0],namespace)
STUBS=namespace['STUBS'];MAIN=namespace['MAIN'];write=namespace['write'];run=namespace['run']
STUBS['l1j/server/server/model/Instance/L1PcInstance.java']=STUBS['l1j/server/server/model/Instance/L1PcInstance.java'].replace('account.events.append("consume:").append(id).append(\':\').append(count).append(\';\');return true;', 'account.events.append("debitAttempt:").append(id).append(\':\').append(count).append(\';\');return seed%23 != 7;')
# Results are compared via reflection; immutable output fields can be private.
EDGE=r'''import java.lang.reflect.*;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Card.*;
import l1j.server.server.model.network.core.sc.packet.Trace;
public class Edge {
 static final Class<?>[] TYPES={L1CardAlchemy1.class,L1CardAlchemy2.class,L1CardAlchemy3.class};
 static final String[] CALL={"LegendAlchemy","HeroAlchemy1","MythAlchemy"};
 public static void main(String[] a)throws Exception {
  int checked=0,failures=0,invalids=0;
  for (int type=0;type<3;type++){
   Method method=TYPES[type].getDeclaredMethod(CALL[type],L1PcInstance.class);
   for(int i=0;i<1700;i++){
    int seed=i*23+7;
    Account account=new Account(seed,type);L1PcInstance pc=new L1PcInstance(account,type,seed);
    Trace.EVENTS.setLength(0);Trace.ON=true;
    boolean result=(boolean)method.invoke(null,pc);
    String e=account.events.toString();
    // i=3 fail selection due to seeded account, i=4 fail preflight.
    if(seed%11==4||seed%11==3||seed%11==5) continue;
    if(result || !e.contains("debitAttempt:") || e.contains("store:") || e.contains("set") || e.contains("update"))
      throw new AssertionError("debit refusal not fail-closed "+type+" "+seed+" "+e+" "+result);
    failures++;checked++;
   }
  }
  for(int type:new int[]{0,2}) {
   Method method=TYPES[type].getDeclaredMethod(CALL[type],L1PcInstance.class);
   for(int bad:new int[]{-999,-1,35,99,135,200,212,7777,Integer.MAX_VALUE}) {
    Account account=new Account(100,type); account.setInfo_card_a(bad);
    // initialize test events *after* the test's own selection mutation
    account.events.setLength(0);
    L1PcInstance pc=new L1PcInstance(account,type,100);
    boolean result=(boolean)method.invoke(null,pc);
    if(result || account.events.length()!=0)throw new AssertionError("invalid card charged/mutated type="+type+" bad="+bad+" events="+account.events);
    invalids++;
   }
  }
  System.out.println("debit_failures="+failures+" invalid_selections="+invalids+" PASS");
 }
}'''
# For reward mapping parity, evaluate deterministic private selector in each copy independently.
REWARD=r'''import java.lang.reflect.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import l1j.server.server.model.item.Card.L1CardAlchemy3;
public class Reward {
 public static void main(String[] args)throws Exception {
  Method method=L1CardAlchemy3.class.getDeclaredMethod("getResultCard",int.class,boolean.class,int.class);method.setAccessible(true);
  MessageDigest md=MessageDigest.getInstance("SHA-256");int cases=0;
  for(int sex=-2;sex<=3;sex++)for(int rx=-10;rx<=25;rx++)for(boolean ok:new boolean[]{false,true}){
   Object card=method.invoke(null,sex,ok,rx);StringBuilder s=new StringBuilder();
   for(String field:new String[]{"cardId","name","itemId"}){Field f=card.getClass().getDeclaredField(field);f.setAccessible(true);s.append(f.get(card)).append('|');}
   md.update(s.toString().getBytes(StandardCharsets.UTF_8));md.update((byte)0);cases++;
  }
  System.out.println("reward_cases="+cases+" checksum="+HexFormat.of().formatHex(md.digest()));
 }
}'''

def build(path, production, test_main, name):
    root=path/'src';c=path/'classes';c.mkdir(parents=True)
    for fn,v in STUBS.items():write(root,fn,v)
    write(root,name+'.java',test_main)
    sources=[str(v) for v in root.rglob('*.java')]+[str(production/f) for f in NAMES]
    run(['javac','-encoding','UTF-8','-d',str(c),'--release','21']+sources)
    return run(['java','-cp',str(c),name])
with tempfile.TemporaryDirectory(prefix='rkern_edge_') as tmp:
    w=Path(tmp)
    print(build(w/'edge',ROOT/'src/l1j/server/server/model/item/Card',EDGE,'Edge'))
    old=build(w/'old',DOC/'baseline',REWARD,'Reward')
    new=build(w/'new',ROOT/'src/l1j/server/server/model/item/Card',REWARD,'Reward')
    print('BASE',old,'NEW',new)
    assert old==new,'Reward descriptor mismatch'
    print('PASS: invalid IDs, debit rejection, and all reward mapping boundaries')
