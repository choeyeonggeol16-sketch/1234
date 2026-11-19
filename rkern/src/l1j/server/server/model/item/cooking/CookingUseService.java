package l1j.server.server.model.item.cooking;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.cooking.CookingRegistry.Def;
import l1j.server.server.model.item.cooking.CookingRegistry.Group;
import l1j.server.server.model.item.function.ItemTransactionActions;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

/**
 * 요리 사용 흐름만 담당한다.
 * 실제 매핑/효과 수치는 CookingRegistry가 단일 소스로 관리한다.
 */
public final class CookingUseService {

    private CookingUseService() {}

    private static final int COOKING_EFFECT_GFX = 6392;
    private static final Logger LOG = Logger.getLogger(CookingUseService.class.getName());

    public static void useCookingItem(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null || item.getItem() == null) return;

        final int itemId = item.getItem().getItemId();
        final Def def = CookingRegistry.byItemId(itemId);
        if (def == null) return;

        if (def.isRequireFood225() && pc.get_food() != 225) {
            SC_ServerMessage.send(pc, 74, item.getNumberedName(1));
            return;
        }

        // Keep the display name before the receipt may remove the last stack.
        final String itemName = item.getNumberedName(1);
        final Group group = def.getGroup();
        final int newSkillId = def.getSkillId();

        // No cooking buff, timer or presentation is published before the
        // item debit has committed. The existing item-use economy owner gate
        // serializes overlapping inventory receipts for this character.
        ItemTransactionActions.consume(pc, item, () -> {
            final int previousSkillId = CookingRegistry.currentSkill(pc, group);
            // Group.NONE has no other-group replacement.
            if (group != Group.NONE && previousSkillId != 0 && previousSkillId != newSkillId) {
                CookingRegistry.end(pc, previousSkillId);
                pc.removeSkillEffect(previousSkillId);
            }

            if (!CookingRegistry.ensureStarted(pc, newSkillId)) {
                // A post-commit runtime conflict must not be misreported as a
                // failed inventory debit or lead to an automatic second debit.
                LOG.severe("Committed cooking use could not start effect: pc="
                        + pc.getId() + " skill=" + newSkillId);
                return;
            }
            try {
                pc.setSkillEffect(newSkillId, def.getDurationSec() * 1000);
            } catch (RuntimeException | Error failure) {
                CookingRegistry.end(pc, newSkillId);
                throw failure;
            }

            // Visuals are optional after the committed debit + gameplay state.
            try {
                SC_EffectLocation.send(pc, pc.getX(), pc.getY(), COOKING_EFFECT_GFX);
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Committed cooking effect packet failed", failure);
            }
            try {
                SC_PacketBox.send(pc, 53, def.getIconType(), def.getDurationSec());
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Committed cooking icon packet failed", failure);
            }
            try {
                SC_ServerMessage.send(pc, 76, itemName);
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Committed cooking notice failed", failure);
            }
        });
    }

    /**
     * Registry에 등록된 skillId에 한해서 직접 시작한다.
     */
    public static boolean startCookingSkill(L1PcInstance pc, int skillId, int timeSec) {
        if (pc == null) return false;

        final Def def = CookingRegistry.bySkillId(skillId);
        if (def == null) return false;

        final int previousSkillId = CookingRegistry.currentSkill(pc, def.getGroup());
        if (def.getGroup() != Group.NONE && previousSkillId != 0 && previousSkillId != skillId) {
            CookingRegistry.end(pc, previousSkillId);
            pc.removeSkillEffect(previousSkillId);
        }

        if (!CookingRegistry.ensureStarted(pc, skillId)) return false;

        final int duration = timeSec > 0 ? timeSec : def.getDurationSec();

        try {
            pc.setSkillEffect(skillId, duration * 1000);
        } catch (RuntimeException e) {
            CookingRegistry.end(pc, skillId);
            throw e;
        } catch (Error e) {
            CookingRegistry.end(pc, skillId);
            throw e;
        }

        SC_PacketBox.send(pc, 53, def.getIconType(), duration);
        return true;
    }

    public static final class UseSpec {
        public final int skillId;
        public final int timeSec;
        public final int cookingType;
        public final Group group;
        public final boolean requireFood225;

        public UseSpec(int skillId, int timeSec, int cookingType,
                       Group group, boolean requireFood225) {
            this.skillId = skillId;
            this.timeSec = timeSec;
            this.cookingType = cookingType;
            this.group = group;
            this.requireFood225 = requireFood225;
        }

        static UseSpec from(Def def) {
            return def == null ? null : new UseSpec(
                    def.getSkillId(),
                    def.getDurationSec(),
                    def.getIconType(),
                    def.getGroup(),
                    def.isRequireFood225());
        }
    }
}
