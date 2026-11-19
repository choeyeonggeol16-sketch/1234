package l1j.server.MJRaidSystem.BossSkill;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.utils.L1SpawnUtil;

public class Summon extends MJRaidBossSkill{
    @Override
    public String toString(){
        return Summon.class.getName();
    }


    @Override
    public void action(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
        if(own == null || own.isParalyzed())
            return;

        if(!own.tryBeginCast())
            return;

        try{
            own.setParalyzed(true);

            if(_actid >= 0)
                SC_DoActionGFX.broadcast(own, getSafeActId());

            int num = 0;
            if(_dmgMin == _dmgMax)
                num = _dmgMin;
            else
                num = ThreadLocalRandom.current().nextInt(_dmgMax - _dmgMin) + _dmgMin;

            for(int i=0; i<num; i++)
                L1SpawnUtil.spawn2(own.getX(), own.getY(), own.getMapId(), _effectId, _range, 0, 0);
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
        if(own == null)
            return;

        if(!own.tryBeginCast())
            return;

        try{
            if(_actid >= 0)
                SC_DoActionGFX.broadcast(own, getSafeActId());

            int num = 0;
            if(_dmgMin == _dmgMax)
                num = _dmgMin;
            else
                num = ThreadLocalRandom.current().nextInt(_dmgMax - _dmgMin) + _dmgMin;

            for(int i=0; i<num; i++)
                L1SpawnUtil.spawn2(own.getX(), own.getY(), own.getMapId(), _effectId, _range, 0, 0);

            sleepAction(own, _actid);
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            own.endCast();
        }
    }
}
