package l1j.server.server.model.character.action;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CUserInventory;
import l1j.server.server.model.L1PinkName;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.animation.EActionCodes;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_UseAttackSkill;
import l1j.server.server.model.world.instance.InstanceEnums.InstStatus;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.shared.L1Type;
import l1j.server.server.utils.GameUtils;

/**
 * 현재는 기존 wand action을 GeneralActionType 하나로 수용한다.
 */
public final class CGeneralAction extends CNewAction {

    public static final int TYPE_WAND = 1;

    CGeneralAction(CCreatureController controller) {
        super(controller);
    }

    @Override
    public boolean OnStart() {
        switch (controller.flags()) {
        case TYPE_WAND:
            processWandNow(controller.actor());
            return true;
        default:
            return false;
        }
    }

    @Override
    public boolean OnTick(long nowMs) {
        return true;
    }

    private void processWandNow(L1PcInstance owner) {
        if (owner == null) {
            return;
        }

        final L1ItemInstance item = (L1ItemInstance) controller.o0();
        final int tId = controller.i0();
        final int tX = controller.i1();
        final int tY = controller.i2();
        final int spriteId = controller.i3();
        final double damageRevision = controller.d0();

        if (item == null
                || owner.is_non_action()
                || owner.isDead()
                || GameUtils.isNonAction(owner)
                || owner.isGhost()
                || owner.get_teleport()) {
            consumeWand(owner, item);
            return;
        }

        if (owner.isOnTargetEffect()) {
            final long cur = System.currentTimeMillis();
            if (cur - owner.getLastNpcClickMs() > 1000L) {
                owner.setLastNpcClickMs(cur);
                SC_SkillSound.send(owner, tId, 14486);
            }
        }

        final L1Object obj = L1World.getInstance().findObject(tId);

        if (obj == null
                || !obj.instanceOf(L1Type.L1TYPE_CHARACTER)
                || owner.getInstStatus() == InstStatus.INST_USERSTATUS_LFCINREADY
                || !owner.glanceCheck(tX, tY)) {

            consumeWand(owner, item);
            sendWandEffect(owner, 0, spriteId, tX, tY);
            return;
        }

        final L1Character target = (L1Character) obj;

        if (target.isDead()
                || GameUtils.isUnbeatable(target)
                || target.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER)) {

            consumeWand(owner, item);
            sendWandEffect(owner, target.getId(), spriteId, tX, tY);
            return;
        }

        if (target.instanceOf(L1Type.L1TYPE_PC)
                && (owner.getZoneType() == 1 || target.getZoneType() == 1)) {

            consumeWand(owner, item);
            sendWandEffect(owner, target.getId(), spriteId, tX, tY);
            return;
        }

        if (target.instanceOf(L1Type.L1TYPE_MONSTER)
                && target.getKarma() > 0) {

            consumeWand(owner, item);
            sendWandEffect(owner, target.getId(), spriteId, tX, tY);
            return;
        }

        consumeWand(owner, item);
        sendWandEffect(owner, target.getId(), spriteId, tX, tY);

        if (target.instanceOf(L1Type.L1TYPE_PC)) {
            int dmg = 0;

            if (!GameUtils.isCounterMagic(target)) {
                dmg = 60 - (target.GetMDefendAndConsumeErase() / 10);
                dmg = (int) (dmg * damageRevision);
                dmg += GameRandom.next(5);
            }

            dmg = Math.max(1, dmg);

            target.receiveDamage(owner, dmg);
            L1PinkName.onAction(target, owner);
            return;
        }

        if (target.instanceOf(L1Type.L1TYPE_MONSTER)) {
            int dmg = 10 + (owner.getLevel() / 4);
            dmg = (int) (dmg * damageRevision);
            dmg += GameRandom.next(5);

            target.receiveDamage(owner, dmg);
        }
    }

    private void consumeWand(L1PcInstance owner, L1ItemInstance item) {
        if (item == null || owner == null) {
            return;
        }

        if (item.getItemId() == 40412) {
            owner.getInventory().DeleteItemAmount(item, 1);
            return;
        }

        item.setChargeCount(item.getChargeCount() - 1);

        if (item.getChargeCount() <= 0) {
            owner.getInventory().DeleteItemAmount(item);
        } else {
            owner.getInventory().updateItem(
                    item,
                    CUserInventory.COL_CHARGE_COUNT);
        }
    }

    private void sendWandEffect(
            L1PcInstance owner,
            int targetId,
            int spriteId,
            int tX,
            int tY) {

        if (owner == null) {
            return;
        }

        if (targetId > 0) {
            SC_UseAttackSkill.sendAndBroadcast(
                    owner,
                    targetId,
                    spriteId,
                    tX,
                    tY,
                    EActionCodes.wand.toInt());

            final int num = EActionCodes.wand.toInt();

            if (owner.getCurrentSpriteId() >= 15000) {
                SC_DoActionGFX.send(
                        owner.getNetConnection(),
                        owner.getId(),
                        num);
            }

            return;
        }

        final int actionId =
                EActionCodes.wand.toInt();

        SC_DoActionGFX.send(
                owner.getNetConnection(),
                owner.getId(),
                actionId);

        SC_DoActionGFX.broadcast(
                owner,
                actionId);

        final long s = SC_EffectLocation.pack(
                        tX,
                        tY,
                        spriteId);

        SC_EffectLocation.sendPacked(owner, s);
        SC_EffectLocation.broadcastPacked(owner, s);

        if (owner.getCurrentSpriteId() >= 15000) {
            final int num = actionId;

            SC_DoActionGFX.send(
                    owner.getNetConnection(),
                    owner.getId(),
                    num);
        }
    }
}
