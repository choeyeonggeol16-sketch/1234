package l1j.server.server.model.character.Item.Drop;

import java.util.ArrayDeque;
import java.util.HashSet;
import l1j.server.Config;
import l1j.server.server.datatables.AutoLoot;
import l1j.server.server.datatables.BossMonsterSpawnList;
import l1j.server.server.model.CParty;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public final class DropShareEngine {

    private DropShareEngine() {}

    public static void handleOneItem(L1NpcInstance npc, L1Inventory mobInv, L1ItemInstance item, DropAcqSnapshot snap, L1PcInstance lastAttackerPc) {
        handleOneItem_AllModes(npc, mobInv, item, snap, lastAttackerPc);
    }

    public static boolean handleOneItem_AllModes(L1NpcInstance npc, L1Inventory mobInv, L1ItemInstance item, DropAcqSnapshot snap, L1PcInstance lastAttackerPc) {
        return handleOneItem(npc, mobInv, item, snap, lastAttackerPc, null, null,
                npc == null ? null : npc.getLocation());
    }

    public static boolean handleOneItem(L1NpcInstance npc, L1Inventory mobInv, L1ItemInstance item,
            DropAcqSnapshot snap, L1PcInstance lastAttackerPc, CParty admittedParty,
            L1PcInstance[] admittedMembers, L1Location origin) {
        if (Config.STANDBY_SERVER) return true;
        if (npc == null || mobInv == null || item == null) return true;

        try {
            if (item.getItem().getType2() == 0 && item.getItem().getType() == 2) item.setNowLighting(false);
        } catch (Throwable ignored) {}
        
    	// 보스라면 오토루팅 무시하고 무조건 바닥 드랍!
        if (Config.BOSS_DROP_GROUND_ENABLE && 
        		BossMonsterSpawnList.find(npc.getNpcId()) != null) {
        	return handleNonAuto(npc, mobInv, item, snap, lastAttackerPc, origin);
        }
 
        final int itemId = item.getItemId();

        // 특정 아이템 강제 오토루팅
        if (itemId == 810008) return handleAutoLoot(npc, mobInv, item, snap, lastAttackerPc, admittedParty, admittedMembers, origin);

        // 오토루팅 여부 판별 (snap이 없어도 Config가 1이면 무조건 오토루팅 시도)
        if (isAutoLootItem(itemId, snap, lastAttackerPc)) {
            return handleAutoLoot(npc, mobInv, item, snap, lastAttackerPc, admittedParty, admittedMembers, origin);
        }

        // 위 조건 아니면 바닥 드랍
        return handleNonAuto(npc, mobInv, item, snap, lastAttackerPc, origin);
    }

    private static boolean isAutoLootItem(int itemId, DropAcqSnapshot snap, L1PcInstance lastAttacker) {
        // 글로벌 Config가 켜져 있으면 무조건 오토루팅
        if (Config.AUTO_LOOT != 0) {
            return true;
        }
        
        // 글로벌 Config가 꺼져 있어도, DB(AutoLoot 테이블)에 등록된 아이템(예: 아데나 등)이면 오토루팅
        try { 
            if (AutoLoot.getInstance().isAutoLoot(itemId)) {
                return true;
            }
        } catch (Throwable t) {}
        
        // 위의 두 조건에 해당하지 않으면 오토루팅이 아님 (바닥 드랍)
        return false;
    }

    private static boolean handleAutoLoot(L1NpcInstance npc, L1Inventory mobInv, L1ItemInstance item, DropAcqSnapshot snap, L1PcInstance lastAttackerPc,
            CParty admittedParty, L1PcInstance[] admittedMembers, L1Location origin) {
    	 if (snap == null || snap.size <= 0) {
             tradeSafely(mobInv, item, item.getCount(), L1World.getInstance().getInventory(npc.getX(), npc.getY(), npc.getMapId()));
             return true;
         }

         // 누적 대미지 1위(Max Hate)를 주인공으로 선정
         L1PcInstance topHater = null;
         if (snap.maxIdx >= 0 && snap.acqs[snap.maxIdx] instanceof L1PcInstance) {
             topHater = (L1PcInstance) snap.acqs[snap.maxIdx];
         }

         // 대미지 리스트가 꼬였을 때만 막타자를 보험으로 씀
         L1PcInstance primaryOwner = (topHater != null) ? topHater : lastAttackerPc;
         if (admittedParty != null) {
             // Never route to a different party joined after the death was admitted.
             if (admittedParty.Route(origin, mobInv, item, snap, primaryOwner, admittedMembers)) return true;
             return L1World.getInstance().PickNpcDrop(origin, mobInv, item, primaryOwner);
         }
         if (Config.PARTY_LOOT_ENABLE && primaryOwner != null) {
             final l1j.server.server.model.CParty party = primaryOwner.getParty();
             if (party != null && party.Route(npc, mobInv, item, snap, primaryOwner)) return true;
         }

         // No eligible party: keep the existing solo/ground route.
         L1Inventory dest = decideAutoLootTargetInventory(npc, item, primaryOwner);
         tradeSafely(mobInv, item, item.getCount(), dest);
         return true;
    }

    private static boolean handleNonAuto(L1NpcInstance npc, L1Inventory mobInv, L1ItemInstance item, DropAcqSnapshot snap, L1PcInstance lastAttackerPc, L1Location origin) {
        item.setDropMobId(npc.getNpcTemplate().get_npcId());
        L1PcInstance owner = (snap != null && snap.maxIdx >= 0 && snap.acqs[snap.maxIdx] instanceof L1PcInstance pc) ? pc : lastAttackerPc;
        if (owner != null) item.startItemOwnerTimer(owner);
        tradeSafely(mobInv, item, item.getCount(), pickGroundInventorySmart(origin));
        return true;
    }

    private static L1Inventory pickGroundInventorySmart(L1Location origin) {
        final int baseX = origin.getX(), baseY = origin.getY(), mapId = origin.getMapId();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{baseX, baseY});
        HashSet<Long> vis = new HashSet<>();
        vis.add((((long) baseX) << 32) | (baseY & 0xffffffffL));

        while (!q.isEmpty()) {
            int[] cur = q.poll();
            if (Math.max(Math.abs(baseX - cur[0]), Math.abs(baseY - cur[1])) > 3) continue;
            if (origin.getMap().isPassable(cur[0], cur[1])) return L1World.getInstance().getInventory(cur[0], cur[1], (short) mapId);
            for (int i = 0; i < 8; i++) {
                int nx = cur[0] + (i==1||i==2||i==3?1:i==5||i==6||i==7?-1:0);
                int ny = cur[1] + (i==0||i==1||i==7?-1:i==3||i==4||i==5?1:0);
                long key = (((long) nx) << 32) | (ny & 0xffffffffL);
                if (vis.add(key)) q.add(new int[]{nx, ny});
            }
        }
        return L1World.getInstance().getInventory(baseX, baseY, (short) mapId);
    }

    private static L1Inventory decideAutoLootTargetInventory(L1NpcInstance npc, L1ItemInstance item, L1Character winner) {
        if (winner instanceof L1PcInstance) {
            L1PcInstance pc = (L1PcInstance) winner;
            if (pc.getInventory().checkAddItem(item, item.getCount()) == L1Inventory.OK) return pc.getInventory();
            return L1World.getInstance().getInventory(pc.getX(), pc.getY(), pc.getMapId());
        }
        return L1World.getInstance().getInventory(npc.getX(), npc.getY(), npc.getMapId());
    }

    private static void tradeSafely(L1Inventory from, L1ItemInstance item, int count, L1Inventory to) {
        if (from != null && item != null && to != null && count > 0) {
            try { from.tradeItem(item, count, to); } catch (Throwable t) {}
        }
    }
}