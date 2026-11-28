package l1j.server.server.model.character.transform;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.animation.loader.CSpriteTimingTable;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeShape;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_CloseList;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.npc.ai.CNPCServer;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Item;

/** Immutable definition and shared L1 transform transition policy. */
public final class CTransform {
    private static final Logger LOG = Logger.getLogger(CTransform.class.getName());
    private static final int SHAPE = L1SkillId.SHAPE_CHANGE;
    private static final int RING = L1SkillId.POLY_RING_MASTER;
    private static final int RACE_FORM = 5065;
    private static final int[] CLASS_FORMS = {11408, 11409, 11410, 11411, 11412, 11413, 11414, 11415,
            11416, 11417, 11418, 11419, 11420, 11421, 12613, 12614};

    private final CTransformCommonData common;
    private final CTransformCombatData combat;

    public CTransform(CTransformCommonData common, CTransformCombatData combat) {
        this.common = common;
        this.combat = combat;
    }

    public int getId() { return common.id(); }
    public String getName() { return common.name(); }
    public int getPolyId() { return common.sprite(); }
    public int getMinLevel() { return common.minimumLevel(); }
    public boolean canUseSkill() { return combat.canUseSkill(); }
    public boolean CanUseWeapon(int type) { return combat.canUseWeapon(type); }
    public boolean CanUseArmor(int type) { return combat.canUseArmor(type); }

    private boolean accepts(TransformType cause) {
        return cause == TransformType.LOGIN || (common.causeMask() & cause.mask) != 0;
    }

    public static boolean CanUseItem(int sprite, L1Item item) {
        if (item == null) return true;
        CTransform definition = CTransformDB.getInstance().find(sprite);
        if (definition == null) return true;
        return switch (item.getType2()) {
            case 1 -> definition.CanUseWeapon(item.getType());
            case 2 -> definition.CanUseArmor(item.getType());
            default -> true;
        };
    }

    public static boolean canAttackWithoutWeapon(int sprite) {
        CTransform definition = CTransformDB.getInstance().find(sprite);
        return definition == null || definition.combat.canAttackWithoutWeapon();
    }

    public static boolean IsKnownSprite(int sprite) {
        if (sprite <= 0) return false;
        var timing = CSpriteTimingTable.getInstance().get(sprite);
        // The timing table returns the default timing for an unknown sprite.
        return timing != null && timing.getSpriteId() == sprite;
    }

    private int resolveOption(L1PcInstance pc, TransformType cause, boolean ring) {
        if (pc.isGm() || cause != TransformType.ITEM_MAGIC) return common.sprite();
        int type = pc.getType();
        return switch (common.option()) {
            case 2 -> ring || pc.isPolyRingMaster() || hasMasterRingItem(pc) ? common.sprite() : 0;
            case 3 -> pc.getInventory().HaveItemByClassId(4100133) ? common.sprite() : 0;
            case 4 -> type >= 0 && type < CLASS_FORMS.length / 2
                    ? CLASS_FORMS[type * 2 + (pc.get_sex() == 0 ? 0 : 1)] : 0;
            case 5 -> pc.getInventory().HaveItemByClassId(900077) ? common.sprite() : 0;
            default -> common.sprite();
        };
    }

    private static boolean hasMasterRingItem(L1PcInstance pc) {
        for (int item = 4100023; item <= 4100032; item++)
            if (pc.getInventory().HaveItemByClassId(item)) return true;
        return false;
    }

    public static boolean CanTransform(L1PcInstance pc, int sprite, TransformType cause, boolean ring) {
        return pc != null && pc.상점변신 == 0 && CanTransform(CTransformDB.getInstance(), pc, sprite, cause, ring, false) != 0;
    }

    private static int CanTransform(CTransformDB data, L1PcInstance pc, int sprite,
            TransformType cause, boolean ring, boolean reply) {
        if (pc.isDead() || pc.isLogoutStarted()) return 0;
        int map = pc.getMapId();
        if (map == 5302 || map == 5490 || map == 5153) {
            if (reply) SC_ServerMessage.send(pc, 1170);
            return 0;
        }
        int current = pc.getCurrentSpriteId();
        CTransform requested = data.find(sprite);
        boolean legal = current != 6034 && current != 6035 && IsKnownSprite(sprite)
                && (requested == null || requested.accepts(cause));
        int resolved = legal ? (requested == null ? sprite : requested.resolveOption(pc, cause, ring)) : 0;
        CTransform target = data.find(resolved);
        if (!IsKnownSprite(resolved) || (target != null && !target.accepts(cause))) {
            if (reply) SC_ServerMessage.send(pc, 181);
            return 0;
        }
        return resolved;
    }

    /** All appearance and effect mutations run on the target's existing owner. */
    public static boolean SetTransformData(L1Character actor, int sprite, int seconds, TransformType cause,
            boolean ring, Object equipmentSource) {
        if (actor == null || actor.isDead()) return false;
        if (actor instanceof L1PcInstance pc) {
            var socket = pc.getNetConnection();
            if (socket != null && CIOThreadCallback.currentShardIndex() != socket.affinityShard()) {
                return CIOThreadCallback.getInstance().completeSession(socket, () -> {
                    if (socket.getActiveChar() == pc && !pc.isLogoutStarted())
                        _OnTransform(pc, sprite, cause, ring, equipmentSource);
                });
            }
            return _OnTransform(pc, sprite, cause, ring, equipmentSource);
        }
        if (!(actor instanceof L1MonsterInstance npc) || !IsKnownSprite(sprite) || npc.isDestroyed()) return false;
        if (CNPCServer.getInstance().isOwnerThread(npc)) return applyNpc(npc, sprite, seconds);
        return CIOThreadCallback.getInstance().postCharacterEffectOnOwner(npc, new NpcTransform(npc, sprite, seconds));
    }

    private record NpcTransform(L1MonsterInstance npc, int sprite, int seconds) implements CIOThreadCallback.WheelTask {
        public boolean isCancelled() { return npc.isDestroyed() || npc.isDead(); }
        public void run() { if (!isCancelled()) applyNpc(npc, sprite, seconds); }
    }

    private static boolean applyNpc(L1MonsterInstance npc, int sprite, int seconds) {
        if (npc.isDestroyed() || npc.isDead()) return false;
        int duration = seconds > 0 ? (int) Math.min(Integer.MAX_VALUE, seconds * 1000L) : -1;
        if (!npc.replaceNpcTransformEffectFromOwner(duration)) return false;
        npc.publishTransform(sprite);
        if (npc.getCurrentSpriteId() != sprite) {
            npc.setCurrentSprite(sprite);
            SC_ChangeShape.broadcast(npc, npc.getId(), sprite, 29);
        }
        return true;
    }

    private static boolean _OnTransform(L1PcInstance pc, int sprite, TransformType cause, boolean ring, Object source) {
        CTransformDB data = CTransformDB.getInstance();
        int resolved = CanTransform(data, pc, sprite, cause, ring, true);
        if (resolved == 0 || pc.상점변신 != 0 || !pc.beginTransformChange()) return false;
        boolean reserve = !CacheD.isItemTransferReplaying();
        try {
            if (source != null && pc.getTransformId() == resolved) return true;
            if (reserve && !pc.getInventory().tryBeginEquipmentChange()) return false;
            try {
                int before = pc.getCurrentSpriteId();
                int attackAction = 29;
                if (before != resolved) {
                    var equipment = pc.getEquipment();
                    attackAction = equipment.removeIncompatibleForTransform(data.find(resolved), source);
                    if (attackAction < 0) return false;
                }
                // PC transforms have an indefinite lifetime, including login restores.
                pc.skillEffectRemoveFromOwner(SHAPE, false);
                pc.skillEffectSetFromOwner(SHAPE, -1);
                pc.publishTransform(resolved);
                pc.setTransformEquipmentSource(source);
                pc.setCurrentSprite(resolved);
                pc.getEquipment().refreshTransformWeaponAction();
                boolean ringChanged = applyRing(pc, ring);
                if (before != resolved) sendShape(pc, resolved, attackAction, 29);
                if (ringChanged || ring) sendRingStats(pc);
                if (ring || cause != TransformType.LOGIN) SC_SkillIconGFX.send(pc, 35, -1, ring);
                return true;
            } finally {
                if (reserve) pc.getInventory().endEquipmentChange();
            }
        } finally {
            pc.endTransformChange();
        }
    }

    private static boolean applyRing(L1PcInstance pc, boolean active) {
        if (active) {
            pc.skillEffectSetFromOwner(RING, -1);
            pc.applySkillModifierFromOwner(RING, 0L);
            return true;
        }
        boolean hadEffect = pc.hasSkillEffect(RING);
        pc.skillEffectRemoveFromOwner(RING, false);
        return pc.removeSkillModifierFromOwner(RING) || hadEffect;
    }

    /** Called by the existing skill effect owner after it removes the current effect. */
    public static void PumpEndTransformSkill(L1Character actor, int skillId) {
        if (skillId == RING && actor instanceof L1PcInstance pc) {
            boolean changed = pc.removeSkillModifierFromOwner(RING);
            if (changed) sendRingStats(pc);
        }
        if (actor.getTransformId() != 0) _OnCancelTransform(actor);
    }

    public static void CancelTransform(L1Character actor) {
        if (actor == null) return;
        long expected = actor.getTransformKey();
        if (actor instanceof L1PcInstance pc) {
            var socket = pc.getNetConnection();
            if (socket != null && CIOThreadCallback.currentShardIndex() != socket.affinityShard()
                    && !pc.isLogoutStarted()) {
                CIOThreadCallback.getInstance().completeSession(socket, () -> {
                    if (socket.getActiveChar() == pc && pc.getTransformKey() == expected) _OnCancelTransform(pc);
                });
                return;
            }
        } else if (actor instanceof L1MonsterInstance npc && !CNPCServer.getInstance().isOwnerThread(npc)) {
            CIOThreadCallback.getInstance().postCharacterEffectOnOwner(npc, new CIOThreadCallback.WheelTask() {
                public boolean isCancelled() { return npc.isDestroyed() || npc.getTransformKey() != expected; }
                public void run() { if (!isCancelled()) _OnCancelTransform(npc); }
            });
            return;
        }
        _OnCancelTransform(actor);
    }

    private static void _OnCancelTransform(L1Character actor) {
        if (!actor.beginTransformChange()) return;
        try {
            int before = actor.getTransformId();
            if (before == 0 && !actor.hasSkillEffect(SHAPE) && !actor.hasSkillEffect(RING)) return;
            actor.skillEffectRemoveFromOwner(SHAPE, false);
            actor.publishTransform(0);
            if (actor instanceof L1PcInstance pc) {
                pc.setTransformEquipmentSource(null);
                boolean ringChanged = applyRing(pc, false);
                if (pc.상점변신 == 0) {
                    int base = pc.getClassId();
                    pc.setCurrentSprite(base);
                    pc.getEquipment().refreshTransformWeaponAction();
                    if (!pc.isDead()) {
                        int action = pc.getCurrentWeapon();
                        sendShape(pc, base, action, action);
                    }
                }
                if (ringChanged) sendRingStats(pc);
            } else if (actor instanceof L1MonsterInstance npc && npc.getNpcTemplate() != null) {
                int base = npc.getNpcTemplate().get_gfxid();
                npc.setCurrentSprite(base);
                if (!npc.isDead()) SC_ChangeShape.broadcast(npc, npc.getId(), base, 29);
            }
        } finally {
            actor.endTransformChange();
        }
        if (actor instanceof L1PcInstance pc && pc.getMapId() == 5143 && !pc.isDead()
                && !pc.isLogoutStarted() && pc.상점변신 == 0) {
            pc.Transform(RACE_FORM, 1000, TransformType.NPC, false);
        }
    }

    static boolean canBroadcastShape(L1PcInstance pc) {
        return !pc.isGmInvis() && !pc.isInvisble() && !pc.isGhost();
    }

    static void broadcastShape(L1PcInstance pc, int sprite, int action) {
        if (canBroadcastShape(pc)) {
            SC_ChangeShape.broadcast(pc, pc.getId(), sprite, action);
        }
    }

    private static void sendShape(L1PcInstance pc, int sprite, int action, int broadcastAction) {
        SC_ChangeShape.send(pc, pc.getId(), sprite, action);
        broadcastShape(pc, sprite, broadcastAction);
        var weapon = pc.getWeapon();
        if (weapon != null) {
            long visual = SC_CharVisualUpdate.pack(pc);
            SC_CharVisualUpdate.sendPacked(pc, visual);
            if (canBroadcastShape(pc)) SC_CharVisualUpdate.broadcastPacked(pc, visual);
        }
        SC_PacketBox.send(pc, SC_PacketBox.공격가능거리, pc, weapon);
    }

    private static void sendRingStats(L1PcInstance pc) {
        SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
        SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
        SC_OwnCharAttrDef.send(pc);
        SC_OwnCharStatus2.send(pc);
        SC_SPMR.send(pc);
        if (pc.isInParty()) pc.getParty().refreshPartyMemberStatus(pc);
    }

    public static void handleCommands(L1PcInstance pc, String name) {
        if (pc == null || pc.isDead() || name == null) return;
        if (name.equals("none")) {
            pc.CancelTransform();
            SC_CloseList.send(pc, pc.getId());
            return;
        }
        CTransform definition = CTransformDB.getInstance().find(name);
        if (definition == null) return;
        if (pc.getLevel() >= definition.getMinLevel() || pc.isGm()) {
            if (pc.Transform(definition.getPolyId(), 7200, TransformType.ITEM_MAGIC, false))
                SC_CloseList.send(pc, pc.getId());
        } else SC_ServerMessage.send(pc, 181);
    }

    public static void TransformFromBook(L1PcInstance pc, String name, int seconds) {
        if (pc == null) return;
        if ("none".equals(name)) {
            pc.setMagicItemId(0);
            pc.setMagicItem(false);
            pc.CancelTransform();
            SC_CloseList.send(pc, pc.getId());
            return;
        }
        CTransform definition = CTransformDB.getInstance().find(name);
        if (definition == null || (pc.getLevel() < definition.getMinLevel() && !pc.isGm())) return;
        int sprite = definition.getPolyId();
        if (CanTransform(CTransformDB.getInstance(), pc, sprite, TransformType.ITEM_MAGIC, false, true) == 0 || pc.상점변신 != 0) return;
        int itemId = pc.getMagicItemId();
        if (itemId == 0) return;
        TransformWithCost(pc, sprite, seconds, TransformType.ITEM_MAGIC, false, itemId, 1, () -> {
            if (pc.getMagicItemId() != itemId) return;
            pc.setMagicItemId(0);
            pc.setMagicItem(false);
            SC_CloseList.send(pc, pc.getId());
        });
    }

    public static void TransformWithCost(L1PcInstance pc, int sprite, int seconds, TransformType cause,
            boolean ring, int itemId, int count, Runnable onApplied) {
        if (pc == null || pc.상점변신 != 0
                || CanTransform(CTransformDB.getInstance(), pc, sprite, cause, ring, true) == 0) return;
        try (var tx = L1InventoryTransaction.begin(pc, CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(itemId, count)) return;
            tx.onFailure(() -> SC_SystemMessage.send(pc, "변신 비용을 저장하지 못했습니다."));
            tx.commitAfterInventoryRelease(() -> {
                if (pc.Transform(sprite, seconds, cause, ring) && onApplied != null) onApplied.run();
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Could not prepare transform cost for " + pc.getId(), failure);
        }
    }
}
