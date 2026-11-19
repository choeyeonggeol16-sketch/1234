from pathlib import Path
import subprocess,tempfile
ROOT=Path(__file__).resolve().parents[2]
s=(ROOT/'src/l1j/server/server/model/item/Exp/CalcExp.java').read_text()
def block(marker):
    start=s.index(marker); i=s.index('{',start); depth=1;j=i+1
    while depth:
        depth+=(s[j]=='{')-(s[j]=='}');j+=1
    return s[start:j]
parts=[block('private static final class Work'),block('private static final class PartyScreenInfo'),block('public static void calcExp(')]
java='''import java.util.*;
public class TestCalcExpLifetime {
    static class Config {static int MAX_PT=2,RATE_LAWFUL=1,RATE_XP_CLAUDIA=1;}
    static class ExpTable {static double getPenaltyRate(int l){return 1;}}
    static class L1Object {}
    static class L1Character extends L1Object {boolean isDead(){return false;}}
    static class L1NpcInstance extends L1Character {int getLevel(){return 20;} int getLawful(){return 1;}}
    static class L1PetInstance extends L1NpcInstance {}
    static class L1SummonInstance extends L1NpcInstance {}
    static class L1CompanionInstance extends L1Character {L1PcInstance get_master(){return null;}}
    static class Party {L1PcInstance[] members; Party(L1PcInstance[] m){members=m;} L1PcInstance[] getMembers(){return members;} L1PcInstance getLeader(){return members[0];}}
    static class L1PcInstance extends L1Character {
        Party p; boolean visible=true; boolean fail=false; int lv=20;
        int getLevel(){if(fail)throw new IllegalStateException("level");return lv;}
        boolean knownsObject(L1PcInstance x){return x.visible;}
        Party getParty(){return p;} boolean isInParty(){return p!=null;} boolean isCrown(){return true;}
        int getHomeTownId(){return 0;} void addContribution(int n){} void addLawful(int n){} void addExp(int n){}
    }
    static class L1World {static final L1World W=new L1World();static L1World getInstance(){return W;} L1Object findObject(int id){return new L1NpcInstance();} boolean isProcessingContributionTotal(){return false;}}
    static final ThreadLocal<Work> TL=ThreadLocal.withInitial(Work::new);
    static int calls,solos,nested; static boolean throwDistribution,reenter;
    static void calcExpSafePath(L1PcInstance pc,L1NpcInstance n,ArrayList<?> a,ArrayList<?> h,int e){nested++; Work w=TL.get(); check(w.busy==1 && w.psi.partyMembersRef==pc.p.members,"outer state retained during nested path");}
    static void distributePartyOptimized(Work w,PartyScreenInfo psi,L1Character[] a,int[] h,int[] o,int m,int e,int l,long t,long tl){
        calls++; check(psi.partyMembersRef!=null && psi.inScreenCount>1,"party visible during distribution");
        for(L1PcInstance pc:psi.partyMembersRef) if(pc!=null)check(psi.isPartyMember(pc),"member lookup");
        if(reenter) {L1PcInstance pc=psi.partyMembersRef[0];calcExp(pc,1,new ArrayList<>(List.of(pc)),new ArrayList<>(List.of(1)),1);check(psi.partyMembersRef==pc.p.members,"nested didn't clear outer party");}
        if(throwDistribution)throw new IllegalStateException("distribution");
    }
    static void distributeSolo(L1Character[] a,int[] h,int m,int e,int l,long t,long tl){solos++;}
    static int checks;static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void clean(Work w){
        check(w.busy==0,"busy");check(w.psi.partyMembersRef==null,"party snapshot leaked");
        check(w.psi.inScreenCount==0 && w.psi.partyLevelSqSum==0 && w.psi.ptBonus==0 && w.psi.priBonus==0,"metadata reset");
        check(w.psi.inScreenMembers==w.inScreen && w.psi.inScreenLv==w.inScreenLv && w.psi.inScreenLvSq==w.inScreenLvSq,"arrays reused");
        for(var p:w.acq)check(p==null,"acq retained");for(var p:w.inScreen)check(p==null,"screen retained");
    }
    public static void main(String[] args){
        Work w=TL.get();
        for(int n:new int[]{2,3,8,17,33,2})for(int scenario=0;scenario<5;scenario++){
            L1PcInstance[] members=new L1PcInstance[n];for(int i=0;i<n;i++)members[i]=new L1PcInstance();
            L1PcInstance pc=members[0];pc.p=new Party(members);
            if(scenario==1)for(int i=1;i<n;i++)members[i].visible=false;
            if(scenario==2)members[n-1].fail=true;
            throwDistribution=scenario==3;reenter=scenario==4;
            var a=new ArrayList<L1PcInstance>();var h=new ArrayList<Integer>();for(var m:members){a.add(m);h.add(1);}
            boolean failed=false;try{calcExp(pc,1,a,h,10);}catch(IllegalStateException ex){failed=true;}
            check(failed==(scenario==2||scenario==3),"expected exception");clean(w);
            check(members[0]==pc && members[n-1]!=null,"party snapshot itself not mutated");
            pc.p=null;throwDistribution=false;reenter=false;calcExp(pc,1,a,h,10);clean(w);
        }
        check(calls==18 && nested==6 && solos==36,"all path counts");
        System.out.println("CalcExp lifetime checks="+checks+" party="+calls+" solo="+solos+" nested="+nested);
    }
'''+ '\n'.join(parts)+'\n}'
with tempfile.TemporaryDirectory() as t:
    p=Path(t)/'TestCalcExpLifetime.java';p.write_text(java)
    subprocess.run(['javac','--release','21',str(p)],check=True)
    subprocess.run(['java','-cp',t,'TestCalcExpLifetime'],check=True)
