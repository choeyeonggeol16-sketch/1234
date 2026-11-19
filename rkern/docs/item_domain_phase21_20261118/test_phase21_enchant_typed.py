#!/usr/bin/env python3
"""Verify production enchant success/failure classes compile and execute identically before and after merge."""
from pathlib import Path
import subprocess,tempfile,os
D=Path(__file__).resolve().parent
P=D.parents[1]/'src/l1j/server/server/model/item/function'
ENV=dict(os.environ,LANG='C.UTF-8',LC_ALL='C.UTF-8')
PKG='l1j/server/server/model/item/function/'
STUBS={
 'l1j/server/Config.java':'''package l1j.server;public class Config {public static boolean MASTERENCHANTMESS;public static int LOGGING_WEAPON_ENCHANT=1, LOGGING_ARMOR_ENCHANT=1;}''',
 'l1j/server/server/model/L1PcInventory.java':'''package l1j.server.server.model;import l1j.server.server.model.Instance.*;import l1j.server.server.model.item.function.Log;
public class L1PcInventory {public static final int COL_ENCHANTLVL=8;public void setEquipped(L1ItemInstance item,boolean b){Log.s.append("equip:").append(b).append(';');}
public void updateItem(L1ItemInstance item,int col){Log.s.append("update:").append(col).append(';');}
public void saveItem(L1ItemInstance item,int col){Log.s.append("saveitem:").append(col).append(';');}
public void removeItem(L1ItemInstance item,int c){Log.s.append("remove:").append(c).append(';');}}
''',
 'l1j/server/server/model/Instance/L1ItemInstance.java':'''package l1j.server.server.model.Instance;
public class L1ItemInstance {public int id=1000, level, safe=4, type2=1, type=5, count=3;public boolean identified=true,equipped=true;public String name="검";
public int getId(){return id;} public int getEnchantLevel(){return level;}public void setEnchantLevel(int n){level=n;}
public String getName(){return name;}public boolean isIdentified(){return identified;}public boolean isEquipped(){return equipped;} public int getCount(){return count;}
public Template getItem(){return new Template();}public class Template {public int getType2(){return type2;}public int getItemId(){return id;}public int getType(){return type;}public int get_safeenchant(){return safe;}}}
''',
 'l1j/server/server/model/Instance/L1PcInstance.java':'''package l1j.server.server.model.Instance;import l1j.server.server.model.L1PcInventory;import l1j.server.server.model.item.function.Log;
public class L1PcInstance {public final L1PcInventory inventory=new L1PcInventory();public final AC ac=new AC();public final Resist resist=new Resist();
public L1PcInventory getInventory(){return inventory;}public void saveInventory(){Log.s.append("saveinv;");}
public AC getAC(){return ac;}public Resist getResistance(){return resist;}
public class AC{public void addAc(int n){Log.s.append("ac:").append(n).append(';');}}
public class Resist{public void addMr(int n){Log.s.append("mr:").append(n).append(';');}}}
''',
 'l1j/server/server/model/network/core/session/CIOClient.java':'''package l1j.server.server.model.network.core.session;import l1j.server.server.model.Instance.L1PcInstance;public class CIOClient {public final L1PcInstance pc;public CIOClient(L1PcInstance pc){this.pc=pc;}public L1PcInstance getActiveChar(){return pc;}}''',
 'l1j/server/server/monitor/LoggerInstance.java':'''package l1j.server.server.monitor;import l1j.server.server.model.Instance.*;import l1j.server.server.model.item.function.Log;
public class LoggerInstance {public static final LoggerInstance INSTANCE=new LoggerInstance();public static LoggerInstance getInstance(){return INSTANCE;}
public void addEnchant(L1PcInstance pc,L1ItemInstance item,boolean b){Log.s.append("log:").append(b).append(';');}}
''',
 'l1j/server/server/model/network/core/sc/packet/SC_OwnCharStatus.java':'''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.L1PcInstance;import l1j.server.server.model.item.function.Log;
public class SC_OwnCharStatus{public static void send(L1PcInstance pc){Log.s.append("status;");}}''',
 'l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java':'''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.L1PcInstance;import l1j.server.server.model.item.function.Log;
public class SC_ServerMessage {public static void send(L1PcInstance p,int i){Log.s.append("msg:").append(i).append(';');}
public static void send(L1PcInstance p,int i,String a,String b){Log.s.append("msg:").append(i).append(':').append(a).append(':').append(b).append(';');}
public static void send(L1PcInstance p,int i,String a,String b,String c){Log.s.append("msg:").append(i).append(':').append(a).append(':').append(b).append(':').append(c).append(';');}}
''',
 'l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java':'''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.item.function.Log;
public class SC_SystemMessage {public static void broadcast(String s){Log.s.append("broadcast:").append(s).append(';');}}''',
 'l1j/server/server/model/network/core/sc/packet/SC_PacketBox.java':'''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.item.function.Log;
public class SC_PacketBox{public static void broadcastGreen(String s){Log.s.append("green:").append(s).append(';');}}''',
 'l1j/server/server/model/network/core/sc/packet/SC_SPMR.java':'''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.L1PcInstance;import l1j.server.server.model.item.function.Log;
public class SC_SPMR{public static void send(L1PcInstance p){Log.s.append("spmr;");}}''',
 PKG+'Log.java':'package l1j.server.server.model.item.function; public class Log {public static final StringBuilder s = new StringBuilder();}',
}
HARNESS='''package l1j.server.server.model.item.function;
import l1j.server.Config;import l1j.server.server.model.Instance.*;import l1j.server.server.model.network.core.session.CIOClient;
import java.security.MessageDigest;import java.nio.charset.StandardCharsets;
public class Harness {
 static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
 public static void main(String[] args)throws Exception {
 MessageDigest md=MessageDigest.getInstance("SHA-256");int cases=0;
 for(int type2=0;type2<=2;type2++)for(int id:new int[]{1115,20110,20079,20056,12345})for(int lev:new int[]{-2,-1,0,1,5,9,10,12})for(int plus:new int[]{-1,1,2,3,4})for(int flags=0;flags<16;flags++){
 L1PcInstance pc=new L1PcInstance();L1ItemInstance item=new L1ItemInstance();CIOClient cli=new CIOClient(pc);
 item.type2=type2;item.id=id;item.level=lev;item.identified=(flags&1)==0;item.equipped=(flags&2)!=0;
 item.type=((flags&4)==0?5:8);item.safe=((flags&8)==0?4:0);Config.MASTERENCHANTMESS=(flags&8)!=0;
 Log.s.setLength(0); TRY_CALL(pc,item,cli,plus);
 String line=type2+"/"+id+"/"+lev+"/"+plus+"/"+flags+":"+Log.s+":"+item.level+"\\n";
 md.update(line.getBytes(StandardCharsets.UTF_8));cases++;
 }
 System.out.println("enchant_typed_cases="+cases+" sha256="+hex(md.digest()));
 }}
'''

def test(side):
 with tempfile.TemporaryDirectory(prefix='rkern_phase21_enchant_') as tmp:
  root=Path(tmp); sources=dict(STUBS)
  if side=='old':
   for f in ('EnchantmentSuccessEffects.java','EnchantmentFailureEffects.java'):
    sources[PKG+f]=(D/'baseline'/f).read_text('utf8')
   hook='EnchantmentSuccessEffects.SuccessEnchant(pc,item,cli,plus); EnchantmentFailureEffects.FailureEnchant(pc,item,cli);'
  else:
   sources[PKG+'EnchantmentOutcomeEffects.java']=(P/'EnchantmentOutcomeEffects.java').read_text()
   hook='EnchantmentOutcomeEffects.SuccessEnchant(pc,item,cli,plus); EnchantmentOutcomeEffects.FailureEnchant(pc,item,cli);'
  sources[PKG+'Harness.java']=HARNESS.replace('TRY_CALL(pc,item,cli,plus);',hook)
  for rel,s in sources.items():
   f=root/'src'/rel;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(s,encoding='utf8')
  files=[str(f) for f in (root/'src').rglob('*.java')]
  c=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(root/'classes'),*files],env=ENV,text=True,capture_output=True)
  assert c.returncode==0,side+': '+c.stderr[:8000]
  r=subprocess.run(['java','-cp',str(root/'classes'),'l1j.server.server.model.item.function.Harness'],env=ENV,text=True,capture_output=True)
  assert r.returncode==0,side+': '+r.stderr[:6000]
  return r.stdout.strip()

if __name__=='__main__':
 a,b=test('old'),test('new');assert a==b,(a,b);print('PASS enchant production typed differential:',a)
