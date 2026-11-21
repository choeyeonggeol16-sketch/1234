package l1j.server.server.model.item.function;

import static l1j.server.server.model.character.skill.L1SkillId.COUNTER_MAGIC;
import static l1j.server.server.model.item.function.CreatureItemActions.*;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_TrueTargetNew;

/** Item-specific effect extracted without changing its original execution body. */
final class BattleCommandWandEffect {
    private BattleCommandWandEffect() {}

    public static void 전투지휘봉(L1PcInstance pc, int targetId, L1ItemInstance item) {
        SC_AttackPacket.send(pc.getNetConnection(), pc, 0, ActionCodes.ACTION_Wand);
        SC_AttackPacket.broadcast(pc, 0, ActionCodes.ACTION_Wand);
        L1Object target = L1World.getInstance().findObject(targetId);
        L1Party party = pc.getParty();
        if (target != null) {
            if (party != null) {
                if (target instanceof L1PcInstance) {
                    L1PcInstance member = (L1PcInstance)target;
                    if (pc.isInvisble()) {
                        SC_SystemMessage.send(pc, "투명상태에서는 불가능합니다.");
                        return;
                    }
                    if (pc.getId() == member.getId()) {
                        SC_SystemMessage.send(pc, "\\aH자기 자신에게는 사용할 수 없습니다");
                        return;
                    }
                    if (!(pc.is_combat_field()) && pc.getClanid() == member.getClanid()) {
                        SC_SystemMessage.send(pc, "\\aH같은 혈맹원에게는 사용할 수 없습니다.");
                        return;
                    }
                    for (L1Object obj : L1World.getInstance().getClusterGrid().queryNearby(pc, 18)) {
                        if (obj instanceof L1PcInstance) {
                            L1PcInstance other = (L1PcInstance)obj;
                            if (other.hasSkillEffect(L1SkillId.전투지휘)) {
                                other.removeSkillEffect(L1SkillId.전투지휘);
                            }
                        }
                    }
                    try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_SystemMessage.nativeWriter("\\f2[전투 지휘자: " + pc.getName() + "] 지목상대: \\fH[" + member.getName() + "] \\f2 지목!"),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
try(var targetShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
    SC_TrueTargetNew.nativeWriter(target.getId(),true),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance partymember : pc.getParty().getMembers()) {
                        if (partymember.getId() == member.getId()) {
                            SC_SystemMessage.send(pc, "\\aH파티원에게는 사용할 수 없습니다.");
                            return;
                        }
                        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(targetShared,partymember);
                        if (!(pc.is_combat_field())) {
                            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,partymember);
                        } else {
                            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,partymember);
                        }
                    }
}
}
                    member.setSkillEffect(L1SkillId.전투지휘, 10 * 1000);
                } else {
                    SC_SystemMessage.send(pc, "타겟이 올바르지않습니다.");
                }
            } else {
                SC_SystemMessage.send(pc, "파티상태에서만 전투지휘가 가능합니다.");
            }
        } else {
            SC_SystemMessage.send(pc, "타겟이 올바르지않습니다.");
        }
    }
}
