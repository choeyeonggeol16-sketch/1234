package l1j.server.server.model.world.instance.raid.boss.skill;

import java.util.ArrayList;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.shared.GameRandom;

public class SpaceOut extends RaidBossSkill{
    @Override
    public String toString(){
        return SpaceOut.class.getName();
    }

    @Override
    public void action(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
        if(own == null || pcs == null || pcs.size() <= 0 || own.isParalyzed())
            return;

        if(!own.tryBeginCast())
            return;

        try{
            own.setParalyzed(true);
            Thread.sleep(GameRandom.nextIntOrFixed(_dmgMin, _dmgMax));
        }catch(Exception e){

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

        try {
            Thread.sleep(GameRandom.nextIntOrFixed(_dmgMin, _dmgMax));
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            own.endCast();
        }
    }
}