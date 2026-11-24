package l1j.server.server.model.character.Item.Drop;

import java.util.ArrayList;
import java.util.Arrays;
import l1j.server.Config;
import l1j.server.server.datatables.BossMonsterSpawnList;
import l1j.server.server.model.CParty;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;

/** Shared ground-loot policy per death; never retains characters or parties. */
public final class CContributeData {
    private static final long SECOND = 1_000_000_000L;
    private final long greatestContributor;
    private final int[] contributors;
    private final long extension;
    private volatile long droppedTick;

    private CContributeData(long greatest, int[] allowed, boolean boss) {
        greatestContributor = greatest;
        contributors = allowed;
        extension = boss ? 300L * SECOND : 0L;
    }

    public static CContributeData Distribute(L1NpcInstance victim, ArrayList<?> actors, ArrayList<?> damages) {
        if (victim == null || actors == null || damages == null) return null;
        final int n = Math.min(actors.size(), damages.size());
        if (n == 0) return null;
        final long[] groups = new long[n], totals = new long[n];
        int groupCount = 0, allowedCount = 0;
        int[] allowed = new int[n];
        final L1Location origin = victim.getLocation();
        for (int i = 0; i < n; i++) {
            if (!(actors.get(i) instanceof L1Character actor)
                    || !(damages.get(i) instanceof Integer amount) || amount <= 0) continue;
            final L1PcInstance owner = GetUserOrMaster(actor);
            if (!eligible(owner, origin)) continue;
            final CParty party = owner.getParty();
            final long group = party == null ? Integer.toUnsignedLong(owner.getId()) : -party.GetLootIdentity();
            if (group == 0) continue;
            int index = 0;
            while (index < groupCount && groups[index] != group) index++;
            if (index == groupCount) {
                groups[index] = group;
                groupCount++;
                if (party != null) {
                    for (L1PcInstance member : party.getMembers()) {
                        if (!eligible(member, origin) || member.getParty() != party) continue;
                        if (allowedCount == allowed.length) allowed = Arrays.copyOf(allowed, allowed.length * 2);
                        allowed[allowedCount++] = member.getId();
                    }
                }
            }
            totals[index] += amount;
            if (allowedCount == allowed.length) allowed = Arrays.copyOf(allowed, allowed.length * 2);
            allowed[allowedCount++] = owner.getId();
        }
        if (groupCount == 0) return null;
        int best = 0;
        for (int i = 1; i < groupCount; i++) {
            if (totals[i] > totals[best] || (totals[i] == totals[best] && groups[i] < groups[best])) best = i;
        }
        Arrays.sort(allowed, 0, allowedCount);
        int unique = 0;
        for (int i = 0; i < allowedCount; i++) {
            if (unique == 0 || allowed[i] != allowed[unique - 1]) allowed[unique++] = allowed[i];
        }
        return new CContributeData(groups[best], Arrays.copyOf(allowed, unique),
                BossMonsterSpawnList.find(victim.getNpcId()) != null);
    }

    private static boolean eligible(L1PcInstance pc, L1Location origin) {
        return pc != null && origin != null && pc.getMapId() == origin.getMapId()
                && pc.getLocation().getTileLineDistance(origin) <= Config.PARTY_LOOT_RANGE;
    }

    public static L1PcInstance GetUserOrMaster(L1Character creature) {
        if (creature instanceof L1PcInstance pc) return pc;
        if (creature instanceof L1CompanionInstance companion) return companion.get_master();
        if (creature instanceof L1PetInstance pet) {
            final L1Character master = pet.getMaster();
            return master instanceof L1PcInstance pc ? pc : null;
        }
        if (creature instanceof L1SummonInstance summon) {
            final L1Character master = summon.getMaster();
            return master instanceof L1PcInstance pc ? pc : null;
        }
        return null;
    }

    public synchronized void StartDropPeriod() {
        if (droppedTick == 0L) {
            final long now = System.nanoTime();
            droppedTick = now == 0L ? 1L : now;
        }
    }

    public boolean IsExpired(long now) {
        final long start = droppedTick;
        return start != 0L && now - start > extension + 15L * SECOND;
    }

    public boolean PickableIn5Sec(L1Character creature) {
        final L1PcInstance pc = GetUserOrMaster(creature);
        if (pc == null) return false;
        if (greatestContributor == Integer.toUnsignedLong(pc.getId())) return true;
        final CParty party = pc.getParty();
        return party != null && greatestContributor == -party.GetLootIdentity();
    }

    public boolean PickableIn15Sec(L1Character creature) {
        final L1PcInstance pc = GetUserOrMaster(creature);
        return pc != null && Arrays.binarySearch(contributors, pc.getId()) >= 0;
    }

    public boolean TryPickItem(L1Character creature, long now) {
        final long start = droppedTick;
        if (start == 0L) return false;
        final long elapsed = now - start - extension;
        if (elapsed > 15L * SECOND) return true;
        return elapsed > 5L * SECOND ? PickableIn15Sec(creature) : PickableIn5Sec(creature);
    }
}
