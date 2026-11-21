package l1j.server.server.model.world.instance.raid.boss.skill;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.world.instance.raid.loader.RaidLoadManager;

public class DeathHeal extends RaidBossSkill{
    @Override
    public String toString(){
        return DeathHeal.class.getName();
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

    public void processAction(L1NpcInstance own, L1PcInstance pc){
        // [TEST] S_OnlyEffect 임시 주석 처리 - 3.8 미지원 effectId 크래시 원인 확인용
        /*if(_effectId > 0){
            S_OnlyEffect oe = new S_OnlyEffect(pc.getId(), _effectId);
            pc.sendPackets(oe, false);
            Broadcaster.broadcastPacket(pc, oe);
        }*/
        if(pc.hasSkillEffect(RaidLoadManager.MRS_BS_DEATHHEAL_ID)){
            pc.removeSkillEffect(RaidLoadManager.MRS_BS_DEATHHEAL_ID);
            SC_InventoryIcon.icoReset(pc, RaidLoadManager.MRS_BS_DEATHHEAL_ID, RaidLoadManager.MRS_BS_DEATHHEAL_TIME / 1000, false);
        }else
            SC_InventoryIcon.icoNew(pc, RaidLoadManager.MRS_BS_DEATHHEAL_ID, RaidLoadManager.MRS_BS_DEATHHEAL_TIME / 1000, false);
        pc.setSkillEffect(RaidLoadManager.MRS_BS_DEATHHEAL_ID, RaidLoadManager.MRS_BS_DEATHHEAL_TIME);
    }
}
