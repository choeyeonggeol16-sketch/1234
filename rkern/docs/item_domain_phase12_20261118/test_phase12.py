#!/usr/bin/env python3
"""12th phase: 5 exact body moves, 3 supplemental actual-production differential."""
from pathlib import Path
import hashlib,json,re,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[2]; FUN=ROOT/'src/l1j/server/server/model/item/function';D=ROOT/'docs/item_domain_phase12_20261118'
M=json.loads((D/'MANIFEST.json').read_text('utf-8'))
sha=lambda b:hashlib.sha256(b).hexdigest()
# The original method snapshots are retained in this archive; Phase 11 ZIP is not required.
assert M['baseline_sha256'] and len(M['baseline_sha256']) == 64
# Extract old/new method bodies directly, and verify delegating entrypoint calls exactly once.
def method_body(t,name):
    pattern=re.compile(r'(?m)^\s*(?:public |private )?static void '+re.escape(name)+r'\([^\n]*\)\s*\{')
    match=pattern.search(t);assert match,name
    p=t.index('{',match.start())+1;i=p;level=1;st='normal'
    while i<len(t):
        c=t[i]; nxt=t[i+1] if i+1<len(t) else ''
        if st=='normal':
            if c=='"': st='str'
            elif c=="'":st='chr'
            elif c=='/' and nxt=='/':st='line';i+=1
            elif c=='/' and nxt=='*':st='block';i+=1
            elif c=='{':level+=1
            elif c=='}':
                level-=1
                if level==0:return t[p:i]
        elif st=='str':
            if c=='\\':i+=1
            elif c=='"':st='normal'
        elif st=='chr':
            if c=='\\':i+=1
            elif c=="'":st='normal'
        elif st=='line':
            if c=='\n':st='normal'
        elif st=='block':
            if c=='*' and nxt=='/':st='normal';i+=1
        i+=1
    raise RuntimeError('Unclosed '+name)
for entry in M['methods']:
    old=(D/(entry['source']+'.baseline')).read_text('utf-8')
    extracted=(FUN/(entry['owner']+'.java')).read_text('utf-8')
    gateway=(FUN/entry['source']).read_text('utf-8')
    actual=method_body(extracted,entry['method'])
    prev=method_body(old,entry['method'])
    assert actual==prev,(entry['owner'],'body differs')
    assert sha(actual.encode())==entry['body_sha256'] and len(actual)==entry['body_length'],entry
    delegate=method_body(gateway,entry['method']).strip()
    assert delegate.startswith(entry['owner']+'.'+entry['method']+'(') and delegate.endswith(');'),delegate
print('PASS 5 original method bodies byte-identical; 5 entrypoint ABI delegates exact once')

orig=(D/'SupplementalItemActions.java.baseline').read_text('utf-8')
t1=orig.index('\t\tint l = 0;');t2=orig.index('\n\n\t\tif (itemId >= 40033 && itemId <= 40038)',t1)
s2=orig.index('\n\n\t\tL1ItemInstance l1iteminstance1 =',t2);s3=orig.index('\n\n\t\tswitch (itemId)',s2)
target=orig[t1:t2];elixir=orig[t2:s2];earring=orig[s2:s3]
for expected,row in zip([target,elixir,earring],M['supplemental']):
    assert sha(expected.encode())==row['source_sha256'] and len(expected)==row['length']
    assert (FUN/(row['class']+'.java')).is_file()
cur=(FUN/'SupplementalItemActions.java').read_text('utf-8')
# Rebuild the prior method exactly, reversing the three helper calls.
replacement='''        final int l = SupplementalTargetId.select(itemId, I);
        if (!SupplementalElixirEligibility.allow(pc, itemId)) return;
        L1ItemInstance l1iteminstance1 = l == 0 ? null : pc.getInventory().getItem(l);
        if (!SupplementalEarringTargetRules.allow(pc, l1iteminstance, l1iteminstance1, itemId)) return;'''
assert cur.count(replacement)==1
assert cur.replace(replacement,target+elixir+earring)==orig
print('PASS supplemental original routing/validation reconstructed exactly; no control-flow reordering')

# Differential test is built from original fragments vs production helper classes.
files={}
package='l1j/server/server/model/item/function/'
for cls in ('SupplementalTargetId','SupplementalElixirEligibility','SupplementalEarringTargetRules'):
    files[package+cls+'.java']=(FUN/(cls+'.java')).read_text('utf-8')
files[package+'BaselineSupplemental.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
final class BaselineSupplemental {
    static int target(int itemId,int I){
'''+target+'''\nreturn l;}
    static boolean elixir(L1PcInstance pc,int itemId){
'''+elixir.replace('return;', 'return false;')+'''\nreturn true;}
    static boolean earring(L1PcInstance pc,L1ItemInstance l1iteminstance,L1ItemInstance l1iteminstance1,int itemId){
'''+earring[earring.index('\n\n\t\t// Targeted'):].replace('return;', 'return false;')+'''\nreturn true;}
}'''
files['l1j/server/server/model/Instance/L1ItemInstance.java']='''package l1j.server.server.model.Instance;
public final class L1ItemInstance {public int id;public boolean missing;public L1ItemInstance(int id){this.id=id;} public int getId(){return id;} public L1ItemInstance getItem(){return missing?null:this;}}'''
files['l1j/server/server/model/Instance/L1PcInstance.java']='''package l1j.server.server.model.Instance;
import java.util.HashMap;
public final class L1PcInstance {public int lvl,count;public StringBuilder log=new StringBuilder();public Inv inv=new Inv(); public int getLevel(){return lvl;}public int getElixirStats(){return count;}public Inv getInventory(){return inv;} public static final class Inv {public HashMap<Integer,L1ItemInstance> items=new HashMap<>();public L1ItemInstance getItem(int id){return items.get(id);}}}'''
files['l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_ServerMessage {public static void send(L1PcInstance pc,int id){pc.log.append("S:").append(id);}}'''
files['l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_SystemMessage {public static void send(L1PcInstance pc,String msg){pc.log.append("M:").append(msg);}}'''
files[package+'Phase12Differential.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public final class Phase12Differential {
 public static void main(String[]args){
  int n=0;
  int[] levels={1,54,55,59,60,64,65,69,70,74,75,79,80,81,100};
  int[] ids={40032,40033,40034,40035,40036,40037,40038,40039,40924,40925,40926,40927,40928,40929,40930,300001,40964,410094,210082,707,698,776,400001,500001,600001,700001,-1,0};
  for(int id:ids){for(int input:new int[]{-1,0,11,12000}){
    int a=BaselineSupplemental.target(id,input),b=SupplementalTargetId.select(id,input);
    if(a!=b)throw new AssertionError("target "+id+" "+input); n++;
  }}
  for(int id:ids){for(int lv:levels){for(int count=0;count<=9;count++){
    L1PcInstance a=new L1PcInstance(),b=new L1PcInstance();a.lvl=b.lvl=lv;a.count=b.count=count;
    boolean ar=BaselineSupplemental.elixir(a,id),br=SupplementalElixirEligibility.allow(b,id);
    if(ar!=br||!a.log.toString().equals(b.log.toString()))throw new AssertionError("elixir "+id+" "+lv+" "+count);n++;
  }}}
  for(int id:ids){for(int mode=0;mode<8;mode++){
   L1PcInstance a=new L1PcInstance(),b=new L1PcInstance();
   L1ItemInstance sa=new L1ItemInstance(11),sb=new L1ItemInstance(11),ta=null,tb=null;
   if(mode!=0){ta=new L1ItemInstance(12);tb=new L1ItemInstance(12);}
   if(mode==1){ta.missing=true;tb.missing=true;}
   if(mode==2||mode==5||mode==7){a.inv.items.put(12,ta);b.inv.items.put(12,tb);}
   if(mode==3||mode==6){a.inv.items.put(12,new L1ItemInstance(12));b.inv.items.put(12,new L1ItemInstance(12));}
   if(mode==4){ta=sa;tb=sb;}
   boolean ar=BaselineSupplemental.earring(a,sa,ta,id),br=SupplementalEarringTargetRules.allow(b,sb,tb,id);
   if(ar!=br||!a.log.toString().equals(b.log.toString())) throw new AssertionError("earring "+id+" mode="+mode);n++;
  }}
  System.out.println("PASS actual production supplemental 3 classes Java 21 typed compile and "+n+" original-vs-new runtime scenarios");
 }
}'''
with tempfile.TemporaryDirectory(prefix='rkern_phase12_') as temp:
    tmp=Path(temp)
    for name,v in files.items():
        p=tmp/name;p.parent.mkdir(exist_ok=True,parents=True);p.write_text(v,'utf-8')
    cmd=['javac','--release','21','-encoding','UTF-8','-d',str(tmp),*[str(p) for p in tmp.rglob('*.java')]]
    proc=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    assert proc.returncode==0,proc.stdout
    proc=subprocess.run(['java','-cp',str(tmp),'l1j.server.server.model.item.function.Phase12Differential'],capture_output=True,text=True)
    assert proc.returncode==0,proc.stdout+proc.stderr
    print(proc.stdout.strip())
