package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.transform.CTransform;
import l1j.server.server.model.character.transform.CTransformDB;
import l1j.server.server.model.character.transform.TransformType;
import l1j.server.server.model.rank.loader.RankUserLoader;

final class PolymorphScrollEffects {
    private PolymorphScrollEffects() {}

    record Selection(int sprite, int seconds, boolean ring) {
        boolean apply(L1PcInstance pc) {
            if (sprite == 0) {
                pc.CancelTransform();
                return true;
            }
            return pc.Transform(sprite, seconds, TransformType.ITEM_MAGIC, ring);
        }
    }

    static Selection select(L1PcInstance pc, int itemId, String name) {
        int seconds = switch (itemId) {
            case 40088, 40096 -> 1800;
            case 900075, 210112 -> 3600;
            case 140088 -> 2100;
            case 40008, 140008 -> 7200;
            default -> 0;
        };
        if (seconds == 0 || name == null) return null;
        if (name.startsWith("maple ")) name = name.substring("maple ".length());
        name = CTransformDB.resolveClassName(pc, name);
        if (name.startsWith("rangking ") && !RankUserLoader.getInstance().isRankPoly(pc)) return null;
        if (name.startsWith("maple") && !pc.isPolyRingMaster()) return null;
        if (name.equalsIgnoreCase("girtas") && (pc.getAccount() == null || pc.getAccount().getInfo_kir_poly() != 1))
            return null;
        if (name.isEmpty()) {
            int current = pc.getCurrentSpriteId();
            return current == 6034 || current == 6035 ? null : new Selection(0, seconds, false);
        }
        CTransform form = CTransformDB.getInstance().find(name);
        if (form == null || (form.getMinLevel() > pc.getLevel() && !pc.isGm())) return null;
        boolean ring = itemId == 900075 || ((itemId == 40008 || itemId == 140008) && pc.isPolyRingMaster());
        int sprite = form.getPolyId();
        return CTransform.CanTransform(pc, sprite, TransformType.ITEM_MAGIC, ring)
                ? new Selection(sprite, seconds, ring) : null;
    }

    static boolean usePolyScroll(L1PcInstance pc, int itemId, String name) {
        Selection selection = select(pc, itemId, name);
        return selection != null && selection.apply(pc);
    }
}
