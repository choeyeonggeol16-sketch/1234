"""Run: python test_npc_entry.py <243 baseline ZIP> <current extracted root>. JDK21 required."""
import sys, zipfile, pathlib, re, tempfile, subprocess, itertools
base,root=sys.argv[1:3]; root=pathlib.Path(root)
p='src/l1j/server/server/model/network/core/cs/packet/'
with zipfile.ZipFile(base) as z:
 def old(name): return z.read(p+name+'.java').decode('utf-8').replace('\r\n','\n')
 a,t,u=map(old,('CS_NpcAction','CS_NpcTalk','User'))
def current(name): return (root/p/(name+'.java')).read_text()
h=current('HtmlCmdHandler'); n=current('NpcTalkHandler')
# Reverse exactly the extraction to prove all untouched execution blocks and helpers preserved.
start=a.index('\t\t\tif (s.equalsIgnoreCase("deadTrans")'); end=a.index('\n\t\t\tint[] materials = null;',start); block=a[start:end]
lead='\n\t/** Target-independent commands keep their original precedence over NPC lookup. */\n\tprivate static boolean dispatchLocalCommand(L1PcInstance pc, String s) {\n'
lo=h.index(lead); hi=h.index('\n\tprivate static String 지브릴(',lo)
expected='\n'.join(x[1:] if x.startswith('\t') else x for x in block.replace('return;', 'return true;').split('\n'))
assert h[lo:hi]==lead+expected+'\n\t\treturn false;\n\t}\n'
rest=(h[:lo]+h[hi:]).replace('\t\t\tif (dispatchLocalCommand(pc, s)) return;\n',block,1)
assert rest.replace('final class HtmlCmdHandler {','public final class CS_NpcAction {',1).replace('private HtmlCmdHandler()', 'private CS_NpcAction()',1)==a
assert n.replace('final class NpcTalkHandler {','public final class CS_NpcTalk {',1).replace('private NpcTalkHandler()', 'private CS_NpcTalk()',1)==t
new_user=current('User'); start_u=new_user.index('\n    // Synchronous on the already admitted execution context;'); end_u=new_user.index('\n    static void OnUseItemPacket(',start_u)
assert new_user[:start_u]+new_user[end_u:]==u
assert 'User.OnNpcActionPacket(client, objid, s, s2, actionData);' in current('CS_NpcAction')
assert 'User.OnNpcTalkPacket(client, objId);' in current('CS_NpcTalk')
print('Exact reversible source comparison: PASS (full action/talk bodies and original User preserved)')
# Exercise the actual local command source extracted from both versions, not a reimplemented condition list.
# Typed simplified dependencies log call sequence; this is a local mock, not a game integration test.
new_method=h[lo+len(lead):hi]; new_method=new_method.rsplit('\n\t}\n',1)[0]
java=r'''
import java.util.*;
public class NpcLocalTest {
 static final class L1PcInstance {
  boolean inv, invis, gm, control; int level; StringBuilder trace=new StringBuilder();
  L1PcInstance(boolean a,boolean b,boolean c,boolean d,int e){inv=a;invis=b;gm=c;control=d;level=e;}
  int getId(){return 7;} L1PcInstance getInventory(){return this;}
  boolean checkItem(int i,int n){trace.append("check;");return inv;} boolean isInvisble(){return invis;}
  int getLevel(){return level;} boolean isGm(){return gm;}
  void consumeItem(int i,int n){trace.append("consume;");} void sendPackets(String s){trace.append("packet:").append(s).append(';');}
 }
 static final class SC_SkillSound {static void send(L1PcInstance p,int i,int s){p.trace.append("sound:").append(s).append(';');}}
 static final class SC_ChatPacket {static void sendDirect(L1PcInstance p,L1PcInstance q,String s){p.trace.append("chat:").append(s).append(';');}}
 static final class NeigeNormal {static void Normal(L1PcInstance p,String s){p.trace.append("normal;");}}
 static final class NeigeLow {static void Low(L1PcInstance p,String s){p.trace.append("low;");}}
 static final class NeigeCard {static void Neige(L1PcInstance p,String s){p.trace.append("card;");}}
 static final class NeigeCard2 {static void NeigeLegend(L1PcInstance p,String s){p.trace.append("legend;");}}
 static final class NeigeCard3 {static void NeigeMyth(L1PcInstance p,String s){p.trace.append("myth;");}}
 static final class NeigeGM {static NeigeGM getInstance(){return new NeigeGM();} void gm(L1PcInstance p,String s){p.trace.append("gm;");}}
 static final class ActionControlHandler {static ActionControlHandler getInstance(){return new ActionControlHandler();} boolean handleAction(L1PcInstance p,String s){p.trace.append("control;");return p.control;}}
 static void legacy(L1PcInstance pc,String s) {
LEGACY
 pc.trace.append("fallthrough;");
 }
 static boolean current(L1PcInstance pc,String s) {
CURRENT
 }
 public static void main(String[] args){
  String[] commands={"deadTrans","DEADTRANS","pvpSet","ShowHPMPRecovery","showDisableEffectIcon","showDungeonTimeLimit","nenome","nenormal","nenormalnelowp","nelome","nelowp","rnlpoly","rnalce","rnll","rnmy","rsnmy","neigma","ps_potion_","ps_autobuff_","ps_0","ps_00042","ps_","ps_9x","PS_99","buy","sell","a","","nenormalneigma","rnllrnmy","prefixnenormalSuffix","ps_123nenome","neigmaps_1"};
  int count=0; for(String cmd:commands)for(int flags=0;flags<16;flags++)for(int level:new int[]{1,47,48,99}){
   L1PcInstance a=new L1PcInstance((flags&1)!=0,(flags&2)!=0,(flags&4)!=0,(flags&8)!=0,level);
   L1PcInstance b=new L1PcInstance(a.inv,a.invis,a.gm,a.control,level);
   legacy(a,cmd); if(!current(b,cmd)) b.trace.append("fallthrough;");
   if(!a.trace.toString().equals(b.trace.toString()))throw new AssertionError(cmd+" "+flags+" "+level+" "+a.trace+" != "+b.trace);
   count++;
  } System.out.println("Local command executed differential cases="+count+" PASS");
 }
}
'''.replace('LEGACY',block).replace('CURRENT',new_method)
with tempfile.TemporaryDirectory() as tmp:
 q=pathlib.Path(tmp)/'NpcLocalTest.java'; q.write_text(java)
 subprocess.run(['javac','--release','21',str(q)],check=True)
 subprocess.run(['java','-cp',tmp,'NpcLocalTest'],check=True)
