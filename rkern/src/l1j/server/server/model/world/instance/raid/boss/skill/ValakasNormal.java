package l1j.server.server.model.world.instance.raid.boss.skill;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;

public class ValakasNormal extends RaidBossSkill{
    @Override
    public String toString(){
        return ValakasNormal.class.getName();
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
                if(isSkillSet(own, pc)){
                    processAction(own, pc);
                }else{
                    if(isAbsoluteBarrier(pc)){
                        if(setAbsoluteBarrierDestroy(pc))
                            processAction(own, pc);
                    }
                }
            }else{
                for(int i=0; i<size; i++){
                    pc = pcs.get(i);

                    if(!isSkillSet(own, pc)){
                        if(isAbsoluteBarrier(pc)){
                            if(!setAbsoluteBarrierDestroy(pc))
                                continue;
                        }else
                            continue;
                    }
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
                if(isSkillSet(own, pc)){
                    processAction(own, pc);
                }else{
                    if(isAbsoluteBarrier(pc)){
                        if(setAbsoluteBarrierDestroy(pc))
                            processAction(own, pc);
                    }
                }
            }else{
                for(int i=0; i<size; i++){
                    pc = pcs.get(i);
                    if(!isSkillSet(own, pc)){
                        if(isAbsoluteBarrier(pc)){
                            if(!setAbsoluteBarrierDestroy(pc))
                                continue;
                        }else
                            continue;
                    }
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
        // [TEST] S_OnlyEffect 임시 주석 처리 - 3.8 미지원 effectId 크래시 원인 확인용
        /*if(_effectId > 0){
            S_OnlyEffect oe = new S_OnlyEffect(pc.getId(), _effectId);
            pc.sendPackets(oe, false);
            Broadcaster.broadcastPacket(pc, oe);
        }*/

        pc.receiveDamage(own, calcDamageForValakas(own, pc));
        SC_DoActionGFX.send(pc.getNetConnection(), pc.getId(), ActionCodes.ACTION_Damage);
        SC_DoActionGFX.broadcast(pc, ActionCodes.ACTION_Damage);
    }
}
