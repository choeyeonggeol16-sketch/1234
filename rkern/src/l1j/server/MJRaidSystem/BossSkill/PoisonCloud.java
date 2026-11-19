package l1j.server.MJRaidSystem.BossSkill;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.MJRaidSystem.Loader.MJRaidLoadManager;
import l1j.server.MJRaidSystem.Util.PoisonCludeSpawn;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;

public class PoisonCloud extends MJRaidBossSkill{
    @Override
    public String toString(){
        return PoisonCloud.class.getName();
    }

    @Override
    public void action(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
        if(own == null || pcs == null || pcs.size() <= 0 || own.isParalyzed())
            return;

        if(!own.tryBeginCast())
            return;

        L1PcInstance 	pc		= null;
        int 			size 	= pcs.size();
        int				pending	= 0;
        try{
            own.setParalyzed(true);
            if(_actid >= 0)
                SC_DoActionGFX.broadcast(own, getSafeActId());

            while(true){
                pc = pcs.get(ThreadLocalRandom.current().nextInt(size));
                if(pending++ > 10)
                    break;

                if(isSkillSet(own, pc))
                    break;
            }

            if(pending <= 10)
                ThreadPool.getInstance().execute(new PoisonCludeSpawn(
                        own, pc.getX(), pc.getY(), MJRaidLoadManager.MRS_SP_POISONCLOUD_ID, MJRaidLoadManager.MRS_SP_POISONCLOUD_TIME));
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
        int				pending	= 0;
        try{
            if(_actid >= 0)
                SC_DoActionGFX.broadcast(own, getSafeActId());

            while(true){
                pc = pcs.get(ThreadLocalRandom.current().nextInt(size));
                if(pending++ > 10)
                    break;

                if(isSkillSet(own, pc))
                    break;
            }
            if(pending <= 10)
                ThreadPool.getInstance().execute(new PoisonCludeSpawn(
                        own, pc.getX(), pc.getY(), MJRaidLoadManager.MRS_SP_POISONCLOUD_ID, MJRaidLoadManager.MRS_SP_POISONCLOUD_TIME));

            sleepAction(own, _actid);
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            own.endCast();
        }
    }
}
