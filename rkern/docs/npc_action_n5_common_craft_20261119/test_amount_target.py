from pathlib import Path
import re,tempfile,subprocess,sys
r=Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).resolve().parents[2]
p=r/'src/l1j/server/server/model/network/core/cs/packet'
s=(p/'HtmlCmdHandler.java').read_text(); amount=(p/'CS_Amount.java').read_text(); method=re.search(r'static boolean isNpcTargetInRange\(.*?\n    }',s,re.S).group()
assert 'if (!isNpcTargetInRange(pc, npc)) return false;' in s
assert (amount.index('!HtmlCmdHandler.isNpcTargetInRange(pc, npc)')
        < amount.index('onResult(npc, pc')
        < amount.index('npcAction.dispatch(actionName, pc, npc, null, amount)'))
code='''class TargetTest {static class L1PcInstance {int x,y,m;int getX(){return x;}int getY(){return y;}int getMapId(){return m;}} static class L1NpcInstance extends L1PcInstance {} METHOD static void check(boolean b){if(!b)throw new AssertionError();}public static void main(String[] a){int n=0;L1PcInstance p=new L1PcInstance();L1NpcInstance q=new L1NpcInstance();for(int x=-10;x<=10;x++)for(int y=-10;y<=10;y++)for(int m=0;m<3;m++){q.x=x;q.y=y;q.m=m;check(isNpcTargetInRange(p,q)==(m==0&&Math.abs(x)<=6&&Math.abs(y)<=6));n++;}p.x=Integer.MIN_VALUE;q.x=Integer.MAX_VALUE;q.m=0;q.y=0;check(!isNpcTargetInRange(p,q));System.out.println("Target range: "+(n+1));}}'''.replace('METHOD',method)
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'TargetTest.java';p.write_text(code);subprocess.run(['javac','--release','21',str(p)],check=True);subprocess.run(['java','-cp',d,'TargetTest'],check=True)
