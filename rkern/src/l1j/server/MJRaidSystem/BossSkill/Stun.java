package l1j.server.MJRaidSystem.BossSkill;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.MJRaidSystem.Loader.MJRaidLoadManager;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
/** shock stun. **/
public class Stun extends MJRaidBossSkill{
    @Override
    public String toString(){
        return Stun.class.getName();
    }

    @Override
    public void action(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
        if(own == null || pcs == null || pcs.size() <= 0 || own.isParalyzed())
            return;

        if(!own.tryBeginCast())
            return;

        L1PcInstance 	pc		= null;
        int 			size 	= pcs.size();
        try{
            own.setParalyzed(true);
            if(_actid >= 0)
                SC_DoActionGFX.broadcast(own, getSafeActId());

            if((_type & BSRTYPE_ONCE) > 0){
                pc = pcs.get(ThreadLocalRandom.current().nextInt(size));
                if(isSkillSet(own, pc))
                    processAction(own, pc);
            }else{
                for(int i=0; i<size; i++){
                    pc = pcs.get(i);

                    if(!isSkillSet(own, pc))
                        continue;

                    processAction(own, pc);
                }
            }
            sleepAction(own, _actid);
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            own.setParalyzed(false);
            own.endCast();
        }
    }

    @Override
    public void actionNoDelay(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
        if(own == null || pcs == null || pcs.size() <= 0)
            return;

        if(!own.tryBeginCast())
            return;

        L1PcInstance 	pc		= null;
        int 			size 	= pcs.size();
        try{
            if(_actid >= 0)
                SC_DoActionGFX.broadcast(own, getSafeActId());

            if((_type & BSRTYPE_ONCE) > 0){
                pc = pcs.get(ThreadLocalRandom.current().nextInt(size));
                if(isSkillSet(own, pc))
                    processAction(own, pc);
            }else{
                for(int i=0; i<size; i++){
                    pc = pcs.get(i);

                    if(!isSkillSet(own, pc))
                        continue;

                    processAction(own, pc);
                }
            }
            sleepAction(own, _actid);
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            own.endCast();
        }
    }

    private void processAction(L1NpcInstance own, L1PcInstance pc){
    	
        int time = calcStunTime();
        pc.setSkillEffect(L1SkillId.SHOCK_STUN, time);
        SC_Paralysis.send(pc, SC_Paralysis.TYPE_STUN, true);
        pc.receiveDamage(own, calcDamage(own, pc));
        SC_DoActionGFX.send(pc.getNetConnection(), pc.getId(), ActionCodes.ACTION_Damage);
        SC_DoActionGFX.broadcast(pc, ActionCodes.ACTION_Damage);
    }

    private int calcStunTime(){
        return ThreadLocalRandom.current().nextInt(Math.max(1, MJRaidLoadManager.MRS_BS_STUN_MAX - MJRaidLoadManager.MRS_BS_STUN_MIN)) + MJRaidLoadManager.MRS_BS_STUN_MIN;
    }
}
