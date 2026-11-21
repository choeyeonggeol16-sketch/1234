package l1j.server.server.model.world.instance.raid.boss.skill;

import static l1j.server.server.model.character.skill.L1SkillId.COOKING_BEGIN;
import static l1j.server.server.model.character.skill.L1SkillId.COOKING_END;
import static l1j.server.server.model.character.skill.L1SkillId.SKILLS_BEGIN;
import static l1j.server.server.model.character.skill.L1SkillId.SKILLS_END;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_BEGIN;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CANCLEEND;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_UNDERWATER_BREATH;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_Invis;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconBlessOfEva;
import l1j.server.server.model.network.core.sc.packet.SC_Sound;
public class Cancellation extends RaidBossSkill{
    @Override
    public String toString(){
        return Cancellation.class.getName();
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
        for (int skillNum = SKILLS_BEGIN; skillNum <= SKILLS_END; skillNum++) {
            if (L1SkillUse.isNotCancelable(skillNum) && !pc.isDead()) {
                continue;
            }
            pc.removeSkillEffect(skillNum);
        }

        for (int skillNum = STATUS_BEGIN; skillNum <= STATUS_CANCLEEND; skillNum++) {
            if (L1SkillUse.isNotCancelable(skillNum) && !pc.isDead()) {
                continue;
            }
            pc.removeSkillEffect(skillNum);
        }

        pc.curePoison();
        pc.cureParalaysis();

        for (int skillNum = COOKING_BEGIN; skillNum <= COOKING_END; skillNum++) {
            if (L1SkillUse.isNotCancelable(skillNum) && !pc.isDead()) {
                continue;
            }
            pc.removeSkillEffect(skillNum);
        }

        L1PolyMorph.undoPoly(pc);
        final long cvu = SC_CharVisualUpdate.pack(pc);
        SC_CharVisualUpdate.sendPacked(pc, cvu);
        SC_CharVisualUpdate.broadcastPacked(pc, cvu);

        if (pc.getHasteItemEquipped() > 0) {
            pc.setMoveSpeed(0);
            SC_SkillHaste.send(pc, pc.getId(), 0, 0);
            SC_SkillHaste.broadcast(pc, pc.getId(), 0, 0);
        }
        if (pc.isInvisble()) {
        	if (pc.hasSkillEffect(L1SkillId.INVISIBILITY)) {
        		pc.killSkillEffectTimer(L1SkillId.INVISIBILITY);

        		SC_Invis.send(pc, pc.getId(), 0);
                SC_Invis.broadcast(pc, pc.getId(), 0);

        		SC_Sound.send(pc, 147);

        		SC_RemoveObject.send(
        				pc.getNetConnection(),
        				pc);

        		SC_RemoveObject.broadcast(pc);
        	}

        	if (pc.hasSkillEffect(L1SkillId.BLIND_HIDING)) {
        		pc.killSkillEffectTimer(L1SkillId.BLIND_HIDING);

        		SC_Invis.send(pc, pc.getId(), 0);
                SC_Invis.broadcast(pc, pc.getId(), 0);

        		SC_RemoveObject.send(
        				pc.getNetConnection(),
        				pc);

        		SC_RemoveObject.broadcast(pc);
        	}
        }
        if (pc.hasSkillEffect(STATUS_UNDERWATER_BREATH)) {
            int timeSec = pc.getSkillEffectTimeSec(STATUS_UNDERWATER_BREATH);
            SC_SkillIconBlessOfEva.send(pc, pc.getId(), timeSec);
        }
        pc.removeSkillEffect(STATUS_FREEZE);
    }
}
