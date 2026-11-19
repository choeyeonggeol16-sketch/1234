package l1j.server.server.model.npc.ai;  

import java.util.HashMap;

import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;

/**
 * 몹 조합(레시피) 테이블 — refineItem용.
 */
public final class NpcRefineRecipes {

    private NpcRefineRecipes() {}   

    // =========================================================
    // 조합 정의
    // =========================================================
    public static final class Recipe {
    	
    	
        public final int[] materials;
        public final int[] counts;
        public final int resultItem;
        public final int resultCount;
        public final boolean digestItem;  

        public Recipe(int[] materials, int[] counts, int resultItem, int resultCount, boolean digestItem) {
        	if (materials.length != counts.length) {
                throw new IllegalArgumentException("Recipe 재료와 갯수의 길이가 일치하지 않습니다! resultItem: " + resultItem);
            }
            this.materials = materials;
            this.counts = counts;
            this.resultItem = resultItem;
            this.resultCount = resultCount;
            this.digestItem = digestItem;
        }
    }
    // =========================================================
    // npcId → 조합 목록
    // =========================================================
    private static final HashMap<Integer, Recipe[]> RECIPES = new HashMap<>();

    static {
        // 45032 —
        RECIPES.put(45032, new Recipe[] {
            new Recipe(new int[]{40508, 40521, 40045}, new int[]{150, 3, 3}, 20, 1, false),
            new Recipe(new int[]{40494, 40521},        new int[]{150, 3},     19, 1, false),
            new Recipe(new int[]{40494, 40521},        new int[]{50, 1},      3,  1, false),
            new Recipe(new int[]{88, 40508, 40045},    new int[]{4, 80, 3},   100, 1, false),
            new Recipe(new int[]{88, 40494},           new int[]{2, 80},      89,  1, true),
        });

        // 81069
        RECIPES.put(81069, new Recipe[] {
            new Recipe(new int[]{40032}, new int[]{1}, 40542, 1, false),
        });

        // 45166 / 45167 
        final Recipe[] r45166 = {
            new Recipe(new int[]{40725}, new int[]{1}, 40726, 1, false)
        };
        RECIPES.put(45166, r45166);
        RECIPES.put(45167, r45166);
    }

    // =========================================================
    // 조회/실행
    // =========================================================
    /** npcId의 조합 목록 반환 — 없으면 null */
    public static Recipe[] get(int npcId) {
        return RECIPES.get(npcId);
    }

    /** 단일 조합 시도 — 성공하면 결과 아이템 반환, 스킵/실패 시 null */
    public static L1ItemInstance tryRefine(L1NpcInstance npc, L1Inventory inventory, Recipe r) {
        // 결과물 이미 보유 → 스킵
        if (inventory.hasItemDirect(r.resultItem)) {
            return null;
        }
        // 재료 부족 → 스킵
        if (!inventory.checkItem(r.materials, r.counts)) {
            return null;
        }

        // 재료 소모
        for (int i = 0; i < r.materials.length; i++) {
            inventory.consumeItem(r.materials[i], r.counts[i]);
        }

        // 결과물 생성
        L1ItemInstance item = null;
        item = inventory.storeItem(r.resultItem, r.resultCount);

        // digest 처리
        if (r.digestItem && item != null && npc.getNpcTemplate().get_digestitem() > 0) {
            npc.setDigestItem(item);
        }
        return item;
    }
}