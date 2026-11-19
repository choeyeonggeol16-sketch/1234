#!/usr/bin/env python3
"""Java21 typed regression and per-call allocation benchmark for actual phase69/70 production UI classes."""
from pathlib import Path
import subprocess, tempfile, re, hashlib, sys, shutil, os
DOC=Path(__file__).resolve().parents[1]
ROOT=DOC.parents[1]
PACK=ROOT/'src/l1j/server/server/model/item/Card'
BASE=DOC/'baseline'
PRODUCTION=['L1CardA.java','L1CardB.java','L1CardC.java']
ACCOUNT_GETTERS=sorted({m for name in PRODUCTION for m in re.findall(r'\bacc\.(get\w+)\(', (BASE/name).read_text())})
assert len(ACCOUNT_GETTERS)>60

def run(cmd):
    r=subprocess.run(cmd,text=True,capture_output=True,timeout=75)
    if r.returncode:raise RuntimeError('ERROR '+str(cmd)+'\n'+r.stdout+'\n'+r.stderr)
    return r.stdout.strip()

def make_stub(w):
    def write(path,text):
        p=w/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
    getters='\n'.join(f'  public int {name}() {{return read({i});}}' for i,name in enumerate(ACCOUNT_GETTERS))
    selects=[ACCOUNT_GETTERS.index('getInfo_card_'+c) for c in 'abcd']
    write('l1j/server/server/Account.java',f'''package l1j.server.server;
public class Account {{
    public static boolean TRACE = true;
    private static final String[] NAMES = new String[] {{{','.join('"'+g+'"' for g in ACCOUNT_GETTERS)}}};
    private static final int[] SEL = new int[] {{{','.join(map(str,selects))}}};
    private final long seed;
    public final StringBuilder getterTrace = new StringBuilder();
    public Account(long seed) {{this.seed=seed;}}
    private int read(int i) {{
        if (TRACE) getterTrace.append(NAMES[i]).append('|');
        for (int x:SEL) if(x==i) {{
            int[] ids = {{-1,0,1,36,37,100,101,123,200,201,211,212}};
            return ids[(int)Math.floorMod(seed + i, ids.length)];
        }}
        int[] values = {{-2,-1,0,1,2,3,7,10,32767}};
        return values[(int)Math.floorMod(seed*13L + i*17L + (seed>>>4),values.length)];
    }}
    {getters}
}}
''')
    write('l1j/server/Config.java','''package l1j.server;
public class Config {public static int hero_cost=100_000,legend_cost=1_000_000,myth_cost=10_000_000;}
''')
    write('l1j/server/server/model/Instance/L1PcInstance.java','''package l1j.server.server.model.Instance;
import l1j.server.server.Account;
public class L1PcInstance {
 private final Account acc;private final int sex;private final boolean dead;private final int id;
 public L1PcInstance(Account a,int sex,boolean dead,int id){this.acc=a;this.sex=sex;this.dead=dead;this.id=id;}
 public Account getAccount(){return acc;} public boolean isDead(){return dead;}
 public int get_sex(){return sex;}public int getId(){return id;}
}
''')
    write('l1j/server/server/model/network/core/sc/packet/SC_NPCTalkReturn.java','''package l1j.server.server.model.network.core.sc.packet;
import java.util.List;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_NPCTalkReturn {
 public static boolean CAPTURE = true;
 public static String emitted;
 public static void sendList(L1PcInstance pc, int id, String html, List<String> args) {
  if (CAPTURE) emitted=id+"/"+html+"/"+args.toString();
 }
}
''')
    write('CardCompare.java','''import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.item.Card.*;
public class CardCompare {
 public static void main(String[] args) throws Exception {
  MessageDigest sha = MessageDigest.getInstance("SHA-256");
  for (int seed=0;seed<10000;seed++){
   Account a = new Account(seed); L1PcInstance pc = seed%101==0 ? null : new L1PcInstance(seed%31==0 ? null : a,seed%3,seed%23==0, (seed*37)&0x3fffffff);
   SC_NPCTalkReturn.emitted=null;
   L1CardA.cardlist(pc); digest(sha, SC_NPCTalkReturn.emitted, a.getterTrace);a.getterTrace.setLength(0);
   SC_NPCTalkReturn.emitted=null;
   L1CardB.cardlist(pc); digest(sha, SC_NPCTalkReturn.emitted, a.getterTrace);a.getterTrace.setLength(0);
   SC_NPCTalkReturn.emitted=null;
   L1CardC.cardlist(pc); digest(sha, SC_NPCTalkReturn.emitted, a.getterTrace);a.getterTrace.setLength(0);
  }
  System.out.println("compare=30000 sha256="+HexFormat.of().formatHex(sha.digest()));
  Account.TRACE = false;
  SC_NPCTalkReturn.CAPTURE=false;
  Account a = new Account(13);
  L1PcInstance pc = new L1PcInstance(a,1,false,222);
  for(int i=0;i<40000;i++) {L1CardA.cardlist(pc);L1CardB.cardlist(pc);L1CardC.cardlist(pc);}
  ThreadMXBean b=(ThreadMXBean)ManagementFactory.getThreadMXBean();
  b.setThreadAllocatedMemoryEnabled(true);long tid=Thread.currentThread().threadId();
  for(int type=0;type<3;type++) {
   long start=b.getThreadAllocatedBytes(tid);
   for(int i=0;i<40000;i++){
    switch(type){case 0->L1CardA.cardlist(pc);case 1->L1CardB.cardlist(pc);case 2->L1CardC.cardlist(pc);}
   }
   long alloc=b.getThreadAllocatedBytes(tid)-start;
   System.out.println("allocation."+"ABC".charAt(type)+"="+(alloc/40000.0));
  }
 }
 private static void digest(MessageDigest sha, String s, StringBuilder getters) {
  sha.update((s==null?"<null>":s).getBytes(StandardCharsets.UTF_8));sha.update((byte)0);
  sha.update(getters.toString().getBytes(StandardCharsets.UTF_8));sha.update((byte)0);
 }
}
''')

def compile_and_run(working,source):
    stub=working/'stubs';stub.mkdir(parents=True)
    make_stub(stub)
    out=working/'classes';out.mkdir()
    files=[str(x) for x in stub.rglob('*.java')]+[str(source/name) for name in PRODUCTION]
    run(['javac','-encoding','UTF-8','-d',str(out)]+files)
    return run(['java','-cp',str(out),'CardCompare'])

with tempfile.TemporaryDirectory(prefix='item_card_ui70_') as temp:
    path=Path(temp)
    orig=compile_and_run(path/'orig',BASE)
    new=compile_and_run(path/'new',PACK)
    a=orig.splitlines();b=new.splitlines()
    print('baseline:\n'+orig+'\noptimized:\n'+new)
    assert a[0]==b[0], 'REGRESSION: card UI event and account getter order differ!'
    print('PASS: 30,000 Java21 typed screen-call comparisons including getter order')
    for old,new in zip(a[1:],b[1:]):
        x=float(old.partition('=')[-1]);y=float(new.partition('=')[-1]);
        print(old.split('=')[0], 'reduction:',round((x-y)/x*100,2),'percent')
        assert y < x, 'No measured per-call allocation reduction!'
