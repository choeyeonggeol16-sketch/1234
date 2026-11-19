package l1j.server.server.model.npc.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;

/**
 * 몹 소화(보유 아이템 자동 삭제) 관리 — 
 */
public final class NpcDigestItems {

    private final ConcurrentHashMap<Integer, Long> _expiry = new ConcurrentHashMap<>();

    private final L1NpcInstance _npc;

    public NpcDigestItems(L1NpcInstance npc) {
        _npc = npc;
    }

    /** 소화 대기 아이템 등록 — 만료 = 현재 + digestitem 초 */
    public void add(L1ItemInstance item, int digestSeconds) {
        if (item == null || digestSeconds <= 0) return;
        _expiry.put(item.getId(),
                System.currentTimeMillis() + digestSeconds * 1000L);
    }

    /** 만료된 아이템 제거 — 주기 작업에서 호출  */
    public void checkExpired() {
        if (_expiry.isEmpty()) return;

        final long now = System.currentTimeMillis();
        for (Map.Entry<Integer, Long> e : _expiry.entrySet()) {
			if (now >= e.getValue()) {
				final int itemObjId = e.getKey();
				_expiry.remove(itemObjId); // 맵에서 즉시 삭제

				final L1ItemInstance item = _npc.getInventory().getItem(itemObjId);
				if (item != null) {
					_npc.getInventory().removeItem(item, item.getCount());
				}
			}
		}
    }

    public boolean isEmpty() {
        return _expiry.isEmpty();
    }
}