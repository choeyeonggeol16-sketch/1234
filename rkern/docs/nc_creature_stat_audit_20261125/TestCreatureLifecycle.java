package l1j.server.server.model;

import java.lang.reflect.Method;
import java.util.Arrays;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.model.character.skill.SkillEffectContainer;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.ItemStat;
import l1j.server.server.utils.CPCParamDB;

public final class TestCreatureLifecycle {
    private static int checks;
    private static void eq(int expected,int actual,String msg){
        checks++;
        if(expected!=actual)throw new AssertionError(msg+" expected="+expected+" actual="+actual);
    }
    private static void yes(boolean b,String msg){eq(1,b?1:0,msg);}
    private static final int[] IDS={0,61,138,734,2786,6658,6671,12490};
    private static final int[] AC_DIV={6,6,7,8,6,6,8,6};
    private static final Method LEVEL_TRANSITION;
    static {
        try {
            LEVEL_TRANSITION=L1PcInstance.class.getDeclaredMethod("updateLevelAndGrowth",int.class);
            LEVEL_TRANSITION.setAccessible(true);
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    private static void level(L1PcInstance pc,int target)throws Exception{
        int before=pc.getLevel();
        pc.setExp(ExpTable.getExpByLevel(target));
        LEVEL_TRANSITION.invoke(pc,target-before);
        eq(target,pc.getLevel(),"EXP -> level");
    }
    private static void verify(L1PcInstance p){
        int t=p.getType(),l=p.getLevel();
        int[] values={p.getBaseMeleeDmgByLevelClass(),p.getBaseBowDmgByLevelClass(),p.getBaseMagicDmgByLevelClass(),
            p.getBaseMeleeHitByLevelClass(),p.getBaseBowHitByLevelClass(),p.getBaseMagicHitByLevelClass(),
            p.getBaseMeleeCriByLevelClass(),p.getBaseBowCriByLevelClass(),p.getBaseMagicCriByLevelClass(),
            p.getBasePvpDrByLevelClass(),p.getBaseMLByLevelClass()};
        for(int i=0;i<values.length;i++)eq(CPCParamDB.GetBaseParam(i,t,l),values[i],"base param "+i);
        eq(CPCParamDB.물리방어력(p.GetTotalStatDex())-l/AC_DIV[t],p.GetBasePDefend(),"base AC");
        eq(Math.clamp(CPCParamDB.마법방어(t,p.GetTotalStatWis())+l/2,0,250),p.GetOrgMDefend(),"base MR");
        eq(p.getBaseMLByLevelClass(),p.getMagicLevel(),"magic level");
        yes(p.getCurrentHp()<=p.getMaxHp(),"HP clamp");
        yes(p.getCurrentMp()<=p.getMaxMp(),"MP clamp");
    }
    private static L1PcInstance create(int t){
        L1PcInstance p=new L1PcInstance();
        p.setType(t);p.setClassId(IDS[t]);p.setLevel(1);
        p.ResetPrimaryStats(20,20,25,20,25,10);
        p.addBaseMaxHp(CPCParamDB.initialHp(t));p.addBaseMaxMp(CPCParamDB.initialMp(t,25));
        return p;
    }
    private static int[] snapshot(L1PcInstance p){
        return new int[]{p.GetTotalStatStr(),p.GetTotalStatDex(),p.GetTotalStatCon(),p.GetTotalStatInt(),p.GetTotalStatWis(),
            p.getAc(),p.GetMDefend(),p.GetStatSp(),p.getMaxHp(),p.getMaxMp(),p.getHpr(),p.getMpr(),
            p.getTotalMagicHitup(),p.getBaseMagicCritical(),p.getMaxWeight(),p.GetStatPAttack(false),p.GetStatPAttack(true)};
    }
    private static void compare(int[] a,int[] b,String context){
        for(int i=0;i<a.length;i++)eq(a[i],b[i],context+" field "+i);
    }
    private static final class DeferredNpc extends L1NpcInstance {
        DeferredNpc() { super(null); }
        void apply(L1Npc t) { CopyFrom(t); }
    }
    private static L1Npc prototype(int id,int level,int hp,int mp,int ac,int mr) {
        L1Npc t=new L1Npc();
        t.set_npcId(id);t.set_name("prototype");t.set_nameid("prototype");
        t.set_level(level);t.set_hp(hp);t.set_mp(mp);t.set_ac(ac);t.set_mr(mr);
        t.set_str((byte)20);t.set_con((byte)18);t.set_dex((byte)16);
        t.set_int((byte)14);t.set_wis((byte)12);
        t.set_agro(true);t.set_agrocoi(true);t.set_exp(123);
        return t;
    }
    private static void verifyNpcCopy() {
        L1Npc t=prototype(900001,40,1000,200,-25,60);
        L1NpcInstance a=new L1NpcInstance(t), b=new L1NpcInstance(t);
        yes(a.getNpcTemplate()==t && b.getNpcTemplate()==t,"shared prototype");
        eq(40,a.getLevel(),"copied level");eq(-25,a.GetBasePDefend(),"copied AC");
        eq(60,a.GetOrgMDefend(),"copied MR");eq(20,a.GetStatStr(),"copied STR");
        eq(18,a.GetStatCon(),"copied CON");eq(16,a.GetStatDex(),"copied DEX");
        eq(14,a.GetStatInt(),"copied INT");eq(12,a.GetStatWis(),"copied WIS");
        eq(1000,a.getCurrentHp(),"initial HP");eq(200,a.getCurrentMp(),"initial MP");
        yes(a.isAgro() && a.isAgrocoi(),"copied NPC flags");
        a.setCurrentHp(123);a.setCurrentMp(45);a.SetStatStr(30);
        eq(1000,b.getCurrentHp(),"independent HP");eq(200,b.getCurrentMp(),"independent MP");
        eq(20,b.GetStatStr(),"independent STR");eq(20,t.get_str(),"unchanged template");
        DeferredNpc n=new DeferredNpc();
        n.apply(t);eq(1000,n.getCurrentHp(),"deferred construction");
        int hit=n.getHitup(),dmg=n.getDmgup();
        n.addHitup(3);n.addDmgup(4);
        L1Npc r=prototype(900002,50,2000,300,-35,70);
        r.set_randomlevel(51);
        for(int i=0;i<20;i++){
            n.apply(r);
            yes(n.getLevel()>=50 && n.getLevel()<=51,"random level");
            eq(21,n.GetStatStr(),"random template bonus");
            eq(hit+5,n.getHitup(),"template hit not accumulated");
            eq(dmg+6,n.getDmgup(),"template damage not accumulated");
            eq(2000,n.getCurrentHp(),"replacement HP");
            eq(300,n.getCurrentMp(),"replacement MP");
        }
        n.apply(t);
        eq(hit+3,n.getHitup(),"non-template hit retained");
        eq(dmg+4,n.getDmgup(),"non-template damage retained");
        yes(n.getNpcTemplate()==t,"replacement prototype");
        eq(40,n.getLevel(),"replacement level");
        eq(20,n.GetStatStr(),"replacement STR");
        System.out.println("PASS NPC prototype sharing, per-instance state, deferred creation and repeated replacement");
    }
    public static void main(String[] args)throws Exception{
        for(int t=0;t<8;t++){
            L1PcInstance p=create(t);
            int[] hp=new int[100],mp=new int[100];
            hp[1]=p.getBaseMaxHp();mp[1]=p.getBaseMaxMp();
            for(int l=2;l<=99;l++){
                level(p,l);verify(p);
                hp[l]=p.getBaseMaxHp();mp[l]=p.getBaseMaxMp();
                int dh=hp[l]-hp[l-1],dm=mp[l]-mp[l-1];
                yes(dh>=CPCParamDB.PureHp(t,25)&&dh<=CPCParamDB.PureHp(t,25)+1,"HP growth");
                yes(dm>=CPCParamDB.MinincreaseMp(t,25)&&dm<=CPCParamDB.MaxincreaseMp(t,25),"MP growth");
            }
            String history=p.encodeHpMpGrowth();
            p.restoreHpMpGrowth(history);
            p.setCurrentHp(p.getMaxHp());p.setCurrentMp(p.getMaxMp());
            for(int l=98;l>=1;l--){level(p,l);verify(p);eq(hp[l],p.getBaseMaxHp(),"HP level down");eq(mp[l],p.getBaseMaxMp(),"MP level down");}
            for(int l=2;l<=99;l++){level(p,l);verify(p);eq(hp[l],p.getBaseMaxHp(),"HP regain");eq(mp[l],p.getBaseMaxMp(),"MP regain");}
            eq(99,p.getLevel(),"level 99");
            for(int c=0;c<8;c++){p.setType(c);p.setClassId(IDS[c]);verify(p);}
            p.setType(t);p.setClassId(IDS[t]);verify(p);
            int[] before=snapshot(p);
            for(int k=0;k<100;k++){
                p.AddStatBonusStr(3);p.AddStatBonusDex(4);p.AddStatBonusInt(5);p.AddStatBonusWis(6);p.AddStatBonusCon(2);
                ItemStat.AC.apply(p,-10);ItemStat.MR.apply(p,15);ItemStat.MAX_HP.apply(p,100);
                ItemStat.MAX_MP.apply(p,50);ItemStat.SP.apply(p,3);ItemStat.MPR.apply(p,4);ItemStat.WEIGHT.apply(p,200);
                verify(p);eq(before[0]+3,p.GetTotalStatStr(),"gear STR");eq(before[1]+4,p.GetTotalStatDex(),"gear DEX");
                eq(before[8]+100,p.getMaxHp(),"gear HP");eq(before[9]+50,p.getMaxMp(),"gear MP");
                ItemStat.WEIGHT.apply(p,-200);ItemStat.MPR.apply(p,-4);ItemStat.SP.apply(p,-3);
                ItemStat.MAX_MP.apply(p,-50);ItemStat.MAX_HP.apply(p,-100);ItemStat.MR.apply(p,-15);ItemStat.AC.apply(p,10);
                p.AddStatBonusCon(-2);p.AddStatBonusWis(-6);p.AddStatBonusInt(-5);p.AddStatBonusDex(-4);p.AddStatBonusStr(-3);
                compare(before,snapshot(p),"equipment source round trip");
            }
            SkillEffectContainer effects=new SkillEffectContainer(p);
            for(int k=0;k<100;k++){
                effects.applySkillModifier(L1SkillId.힘3,0);
                effects.applySkillModifier(L1SkillId.덱3,0);
                effects.applySkillModifier(L1SkillId.위즈3,0);
                effects.applySkillModifier(L1SkillId.hp300,0);
                effects.applySkillModifier(L1SkillId.mp300,0);
                effects.applySkillModifier(L1SkillId.sp5,0);
                verify(p);
                eq(before[0]+3,p.GetTotalStatStr(),"skill STR");eq(before[1]+3,p.GetTotalStatDex(),"skill DEX");
                eq(before[4]+3,p.GetTotalStatWis(),"skill WIS");eq(before[8]+300,p.getMaxHp(),"skill HP");
                eq(before[9]+300,p.getMaxMp(),"skill MP");eq(before[7]+5,p.GetStatSp(),"skill SP");
                effects.applySkillModifier(L1SkillId.hp300,0);eq(before[8]+300,p.getMaxHp(),"duplicate skill");
                for(int id:new int[]{L1SkillId.힘3,L1SkillId.덱3,L1SkillId.위즈3,L1SkillId.hp300,L1SkillId.mp300,L1SkillId.sp5})
                    yes(effects.removeSkillModifier(id,false),"remove active modifier");
                compare(before,snapshot(p),"skill round trip");
            }
            p.setCurrentHp(p.getMaxHp());p.setCurrentMp(p.getMaxMp());
            p.ResetPrimaryStats(10,10,10,10,10,10);verify(p);
            eq(p.getMaxHp(),p.getCurrentHp(),"CON reset clamp");
            eq(p.getMaxMp(),p.getCurrentMp(),"WIS reset clamp");
            System.out.println("PASS actual PC type "+t+" level/growth/class/gear/skill/reset");
        }
        L1NpcInstance n=new L1NpcInstance(null);
        n.setLevel(60);n.ResetPrimaryStats(20,20,20,20,45,10);n.setMaxHp(1000);n.setMaxMp(500);n.setAc(-40);n.SetOrgMDefend(80);
        n.setCurrentMp(n.getMaxMp());
        n.SetStatWis(10);eq(n.getMaxMp(),n.getCurrentMp(),"NPC WIS MP clamp");
        for(int l=1;l<=99;l++){n.setLevel(l);eq(-40,n.getAc(),"NPC template AC");eq(80,n.GetOrgMDefend(),"NPC template MR");eq(0,n.getBaseMeleeDmgByLevelClass(),"NPC class isolation");}
        System.out.println("PASS actual NPC template isolation and vital clamp");
        verifyNpcCopy();
        System.out.println("PASS "+checks+" production object lifecycle assertions");
    }
}
