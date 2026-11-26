package l1j.server.server.model.npc.ai;

import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;

public final class CItemRefine {
    private CItemRefine() {}

    // Each row: result ID, result count, digest flag, material count, (ID, count)...
    private static final int[] RECIPE_45032 = {
        20, 1, 0, 3, 40508, 150, 40521, 3, 40045, 3,
        19, 1, 0, 2, 40494, 150, 40521, 3,
         3, 1, 0, 2, 40494, 50, 40521, 1,
       100, 1, 0, 3, 88, 4, 40508, 80, 40045, 3,
        89, 1, 1, 2, 88, 2, 40494, 80
    };
    private static final int[] RECIPE_81069 = {40542, 1, 0, 1, 40032, 1};
    private static final int[] RECIPE_45166 = {40726, 1, 0, 1, 40725, 1};

    public static void RefineItem(final L1NpcInstance npc) {
        if (npc.getExp() == 0) return;
        final int[] recipes = switch (npc.getNpcTemplate().get_npcId()) {
            case 45032 -> RECIPE_45032;
            case 81069 -> RECIPE_81069;
            case 45166, 45167 -> RECIPE_45166;
            default -> null;
        };
        if (recipes == null) return;
        for (int offset = 0; offset < recipes.length;) {
            final int end = offset + 4 + (recipes[offset + 3] << 1);
            Refine(npc, npc.getInventory(), recipes, offset, end);
            offset = end;
        }
    }

    private static void Refine(final L1NpcInstance npc, final CInventory inventory,
            final int[] recipes, final int offset, final int end) {
        final int resultId = recipes[offset];
        if (inventory.HaveItemAmount(resultId)) return;
        for (int i = offset + 4; i < end; i += 2) {
            if (!inventory.HaveItemByClassId(recipes[i], recipes[i + 1])) return;
        }
        for (int i = offset + 4; i < end; i += 2) {
            inventory.DeleteItemInInventory(recipes[i], recipes[i + 1]);
        }
        final L1ItemInstance item = inventory.CreateOrAddItem(resultId, recipes[offset + 1]);
        if (recipes[offset + 2] != 0 && item != null && npc.getNpcTemplate().get_digestitem() > 0) {
            npc.setDigestItem(item);
        }
    }
}
