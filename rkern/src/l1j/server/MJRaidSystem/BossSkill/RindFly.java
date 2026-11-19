package l1j.server.MJRaidSystem.BossSkill;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.MJRaidSystem.Loader.MJRaidLoadManager;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;

public class RindFly extends MJRaidBossSkill{
    @Override
    public String toString(){
        return RindFly.class.getName();
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
            if(_actid >= 0){
                own.allTargetClear();
                own.setHiddenStatus(L1MonsterInstance.HIDDEN_STATUS_FLY);
                SC_DoActionGFX.broadcast(own, getSafeActId());
                own.setStatus(_actid);
                SC_WorldPutObject.broadcastNpc(own);
            }

            Thread.sleep(
                    ThreadLocalRandom.current().nextInt(
                            (int) (MJRaidLoadManager.MRS_BS_RINDFLY_MAX
                                    - MJRaidLoadManager.MRS_BS_RINDFLY_MIN))
                            + MJRaidLoadManager.MRS_BS_RINDFLY_MIN);

            int pending = 0;
            while(true){
                pc = pcs.get(ThreadLocalRandom.current().nextInt(size));
                if(pending++ > 50)
                    break;
                if(pc == null || pc.isDead() || pc.getMapId() != own.getMapId())
                    continue;

                own.setX(pc.getX());
                own.setY(pc.getY());
                pc = null;
                break;
            }

            SC_WorldPutObject.broadcastNpc(own);
            own.setHiddenStatus(L1MonsterInstance.HIDDEN_STATUS_NONE);
            own.setStatus(0);
            SC_WorldPutObject.broadcastNpc(own);

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

        try {
            if(_actid >= 0){
                own.allTargetClear();
                own.setHiddenStatus(L1MonsterInstance.HIDDEN_STATUS_FLY);
                SC_DoActionGFX.broadcast(own, getSafeActId());
                own.setStatus(_actid);
                SC_WorldPutObject.broadcastNpc(own);
            }

            Thread.sleep(
                    ThreadLocalRandom.current().nextInt(
                            (int) (MJRaidLoadManager.MRS_BS_RINDFLY_MAX
                                    - MJRaidLoadManager.MRS_BS_RINDFLY_MIN))
                            + MJRaidLoadManager.MRS_BS_RINDFLY_MIN);

            int pending = 0;
            while(true){
                pc = pcs.get(ThreadLocalRandom.current().nextInt(size));
                if(pending++ > 50)
                    break;
                if(pc == null || pc.isDead() || pc.getMapId() != own.getMapId())
                    continue;

                own.setX(pc.getX());
                own.setY(pc.getY());
                pc = null;
                break;
            }

            SC_WorldPutObject.broadcastNpc(own);
            own.setHiddenStatus(L1MonsterInstance.HIDDEN_STATUS_NONE);
            own.setStatus(0);
            SC_WorldPutObject.broadcastNpc(own);

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
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            own.endCast();
        }
    }

    private void processAction(L1NpcInstance own, L1PcInstance pc){
        pc.receiveDamage(own, calcDamage(own, pc));
        SC_DoActionGFX.send(pc.getNetConnection(), pc.getId(), ActionCodes.ACTION_Damage);
        SC_DoActionGFX.broadcast(pc, ActionCodes.ACTION_Damage);
    }
}
