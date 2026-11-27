package l1j.server.server.model.world.instance.raid.boss.skill;

import java.util.ArrayList;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.shared.GameRandom;
/** 유저들의 위치를 바꾼다. **/
public class PosChange extends RaidBossSkill{
    @Override
    public String toString(){
        return PosChange.class.getName();
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
                pc = pcs.get(GameRandom.nextInt(size));
                if(isSkillSet(own, pc))
                    processAction(own, pc);
            }else{
                for(int i=0; i<size; i++){
                    pc = pcs.get(i);
                    if(pc == null || pc.isDead() || !isRange(own, pc) || GameRandom.nextInt(100) > _ratio)
                        continue;

                    processAction(own, pc);
                }
            }
            sleepAction(own, _actid);
        }catch(Exception e){
            System.out.println(pcs.size());
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
                pc = pcs.get(GameRandom.nextInt(size));
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
        // [TEST] S_OnlyEffect 임시 주석 처리
        /*if(_effectId > 0){
            S_OnlyEffect oe = new S_OnlyEffect(pc.getId(), _effectId);
            pc.sendPackets(oe, false);
            Broadcaster.broadcastPacket(pc, oe);
        }*/

        int div = _range / 2;
        int x 	= GameRandom.nextInt(div);
        int y	= GameRandom.nextInt(div);
        if(GameRandom.nextBoolean())
            x *= -1;
        if(GameRandom.nextBoolean())
            y *= -1;

        pc.start_teleport(own.getX() + x, own.getY() + y, pc.getMapId(), pc.getHeading(), 169, true, false);
    }
}
