package l1j.server.server.model.character.Item.Drop;

import java.util.ArrayList;
import java.util.Random;
import l1j.server.Config;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;

public final class DropAcqSnapshot {

	public final L1Character[] acqs;
	public final int[] hates;
	public final int size;
	public final int totalHate;
	public final int maxIdx;
	public final int maxHate;
	public final CContributeData contribution;

	private DropAcqSnapshot(L1Character[] acqs, int[] hates, int size, int totalHate, int maxIdx, int maxHate, CContributeData contribution) {
		this.contribution = contribution;
		this.acqs = acqs;
		this.hates = hates;
		this.size = size;
		this.totalHate = totalHate;
		this.maxIdx = maxIdx;
		this.maxHate = maxHate;
	}

	public static boolean isSummonLike(Object o) {
		return (o instanceof L1SummonInstance) || (o instanceof L1PetInstance) || (o instanceof L1CompanionInstance);
	}

	public static DropAcqSnapshot build(L1NpcInstance npc, ArrayList<?> acquisitorList, ArrayList<?> hateList) {
	    return build(npc, acquisitorList, hateList, null);
	}

	public static DropAcqSnapshot build(L1NpcInstance npc, ArrayList<?> acquisitorList, ArrayList<?> hateList, ArrayList<?> damage) {
	    if (npc == null || acquisitorList == null || hateList == null) {
	        return empty();
	    }

	    final CContributeData contribution = CContributeData.Distribute(npc, acquisitorList, damage);
	    final int n = Math.min(acquisitorList.size(), hateList.size());
	    if (n <= 0) return empty();

	    L1Character[] bufC = new L1Character[n];
	    int[] bufH = new int[n];

	    int sz = 0;
	    int total = 0;
	    int maxIdx = -1;
	    int maxHate = -1;

	    final int mapId = npc.getMapId(); // 루프 밖에서 미리 선언
	    final L1Location npcLoc = npc.getLocation();

	    for (int i = 0; i < n; i++) {
	        Object o = acquisitorList.get(i);
	        Object hvObj = hateList.get(i);

	        if (!(o instanceof L1Character) || !(hvObj instanceof Integer)) {
	            continue;
	        }

	        L1Character c = (L1Character) o;
	        int hate = (Integer) hvObj;

	        if (c == null || hate <= 0) continue;

	        // 설정에 따른 펫/서먼 제외
	        if (Config.AUTO_LOOT == 0 && isSummonLike(c)) continue;

	        // 맵/거리 체크 
	        if (c.getMapId() != mapId) continue;
	        
	        L1Location cLoc = c.getLocation();
	        if (cLoc == null || cLoc.getTileLineDistance(npcLoc) > Config.PARTY_LOOT_RANGE) {
	            continue;
	        }

	        bufC[sz] = c;
	        bufH[sz] = hate;
	        total += hate;

	        if (hate > maxHate) {
	            maxHate = hate;
	            maxIdx = sz;
	        }
	        sz++;
	    }

	    if (sz <= 0) return new DropAcqSnapshot(new L1Character[0], new int[0], 0, 0, -1, -1, contribution);

	    if (sz == n) {
	        return new DropAcqSnapshot(bufC, bufH, sz, total, maxIdx, maxHate, contribution);
	    }

	    // 최종 결과물만 딱 맞는 크기로 잘라서 반환
	    L1Character[] outC = new L1Character[sz];
	    int[] outH = new int[sz];
	    System.arraycopy(bufC, 0, outC, 0, sz);
	    System.arraycopy(bufH, 0, outH, 0, sz);

	    return new DropAcqSnapshot(outC, outH, sz, total, maxIdx, maxHate, contribution);
	}

	public L1Character pickByWeight(Random rnd) {
		if (size <= 0 || totalHate <= 0) return null;

		int r = rnd.nextInt(totalHate);
		int acc = 0;
		for (int i = 0; i < size; i++) {
			acc += hates[i];
			if (acc > r) return acqs[i];
		}
		return acqs[size - 1];
	}

	private static DropAcqSnapshot empty() {
		return new DropAcqSnapshot(new L1Character[0], new int[0], 0, 0, -1, -1, null);
	}

	public static DropAcqSnapshot fromArrays(L1Character[] acqs, int[] hates, int size,
			int totalHate, int maxIdx, int maxHate) {
		return new DropAcqSnapshot(acqs, hates, size, totalHate, maxIdx, maxHate, null);
	}

	public static DropAcqSnapshot emptyPublic() {
		return empty();
	}

}