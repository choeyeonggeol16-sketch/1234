from pathlib import Path
import subprocess
from test_102 import ROOT, FILES, STUBS, STUB_TEST, write
MAIN='''import java.lang.reflect.*;import java.security.*;import java.nio.charset.StandardCharsets;import java.util.*;
import l1j.server.server.Account;import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Card.*;
import l1j.server.server.model.network.core.sc.packet.Trace;
public class AB101 {
 static Class<?>[] K={L1CardAlchemy1.class,L1CardAlchemy2.class,L1CardAlchemy3.class};
 static String[] M={"LegendAlchemy","HeroAlchemy1","MythAlchemy"};
 public static void main(String[] args)throws Exception {
  MessageDigest digest=MessageDigest.getInstance("SHA-256");int success=0;
  for(int k=0;k<3;k++){
   Method method=K[k].getMethod(M[k],L1PcInstance.class);
   Field rng=K[k].getDeclaredField("_random");rng.setAccessible(true);
   for(int i=0;i<12000;i++){
    int seed=1000+i;
    Account acc=new Account(seed,k);L1PcInstance pc=new L1PcInstance(acc,k,seed);
    Trace.ON=true;Trace.EVENTS.setLength(0);
    ((Random)rng.get(null)).setSeed(((long)seed*8191L)+k*23917L);
    boolean accepted=(Boolean)method.invoke(null,pc);
    if(accepted)success++;
    String events=acc.events.toString();int t=events.lastIndexOf("store:");
    String item=t<0?"-":events.substring(t).split(";")[0];
    String state=k+":"+seed+":"+accepted+":"+acc.getInfo_card_a()+":"+acc.getInfo_card_b()+":"+
      acc.getInfo_card_c()+":"+acc.getInfo_card_d()+":"+acc.getInfo_ds_poly()+":"+acc.getInfo_heaven_poly()+":"+
      acc.getInfo_class_poly()+":"+pc.전설합성확률증가+":"+pc.영웅합성확률증가+":"+pc.신화합성확률증가+":"+item+":"+Trace.EVENTS;
    digest.update(state.getBytes(StandardCharsets.UTF_8));digest.update((byte)0);
   }
  }
  System.out.println("TOTAL=36000 SUCCESS="+success+" SHA256="+HexFormat.of().formatHex(digest.digest()));
 }
}'''
for kind in ('baseline','new'):
 dst=ROOT/(kind+'_ab101');src_root=dst/'src'
 for name,code in STUBS.items():write(src_root,name).write_text(code,encoding='utf8')
 write(src_root,'AB101.java').write_text(MAIN,encoding='utf8')
 base=ROOT/('updated/src' if kind=='new' else 'baseline/src')/'l1j/server/server/model/item/Card'
 java=[str(p) for p in src_root.rglob('*.java')]+[str(base/n) for n in FILES]
 java.append(str(base/'CardAlchemyReceipt.java'))
 classes=dst/'classes';classes.mkdir(parents=True,exist_ok=True)
 r=subprocess.run(['javac','-encoding','UTF-8','--release','21','-d',str(classes),*java],capture_output=True,text=True,timeout=120)
 if r.returncode:raise RuntimeError(r.stderr[:5000])
 r=subprocess.run(['java','-Xmx512m','-cp',str(classes),'AB101'],capture_output=True,text=True,timeout=120)
 if r.returncode:raise RuntimeError(r.stderr[:5000])
 print(kind,r.stdout.strip())
 (dst/'digest.txt').write_text(r.stdout)
old=(ROOT/'baseline_ab101/digest.txt').read_text();new=(ROOT/'new_ab101/digest.txt').read_text()
assert old==new,(old,new)
print('PASS 36,000 original vs new normal flow output/reward/card-state hashes equal')
