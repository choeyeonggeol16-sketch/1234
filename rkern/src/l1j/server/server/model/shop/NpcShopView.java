package l1j.server.server.model.shop;

import java.util.Arrays;
import java.util.List;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightHistory;
import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightHistoryInfo;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.templates.L1ShopItem;

/** A server-issued NPC shop list, scoped to one character session. */
public final class NpcShopView {
    private enum Source { NPC, ADEN, GM_SEARCH, DOG_FIGHT }

    private final Source source;
    private final int ownerId;
    private final int sessionStamp;
    private final int dialogueVersion;
    private final L1Map map;
    private final long npcIdentity;
    private final int resultId;
    private final int shopId;
    private final boolean buying;
    private final boolean dynamic;
    private final long catalogRevision;
    private final L1ShopItem[] offers;
    private final int[] saleIds;
    private final DogFightHistoryInfo dogFightRound;
    private final int ticketOptions;

    private NpcShopView(Source source, CUserSocket client, L1PcInstance pc, L1NpcInstance npc,
            int resultId, L1Shop shop, boolean buying, L1ShopItem[] offers, int[] saleIds,
            DogFightHistoryInfo dogFightRound, int ticketOptions) {
        this.source = source;
        ownerId = pc.getId();
        sessionStamp = pc.getSessionStamp();
        dialogueVersion = client.npcDialogueVersion();
        map = pc.getMap();
        npcIdentity = npc == null ? 0L : CWorld.getInstance().smartIdOf(npc);
        this.resultId = resultId;
        shopId = shop == null ? (npc == null ? 0 : npc.getNpcId()) : shop.getNpcId();
        this.buying = buying;
        dynamic = npc instanceof L1NpcShopInstance;
        catalogRevision = shop == null ? 0 : shop.revision();
        this.offers = offers;
        this.saleIds = saleIds;
        if (saleIds != null) Arrays.sort(saleIds);
        this.dogFightRound = dogFightRound;
        this.ticketOptions = ticketOptions;
    }

    public static NpcShopView purchase(CUserSocket client, L1PcInstance pc, L1NpcInstance npc,
            int resultId, L1Shop shop, L1ShopItem[] offers) {
        return new NpcShopView(Source.NPC, client, pc, npc, resultId, shop, true, offers, null, null, 0);
    }

    public static NpcShopView sale(CUserSocket client, L1PcInstance pc, L1NpcInstance npc,
            L1Shop shop, int[] saleIds) {
        return new NpcShopView(Source.NPC, client, pc, npc, npc.getId(), shop, false, null, saleIds, null, 0);
    }

    public static NpcShopView aden(CUserSocket client, L1PcInstance pc, int resultId, L1Shop shop, L1ShopItem[] offers) {
        return new NpcShopView(Source.ADEN, client, pc, null, resultId, shop, true, offers, null, null, 0);
    }

    public static NpcShopView gmSearch(CUserSocket client, L1PcInstance pc, int resultId, L1Shop shop, L1ShopItem[] offers) {
        return new NpcShopView(Source.GM_SEARCH, client, pc, null, resultId, shop, true, offers, null, null, 0);
    }

    public static NpcShopView dogFight(CUserSocket client, L1PcInstance pc, L1NpcInstance npc,
            boolean buying, DogFightHistoryInfo round, int ticketOptions, int[] saleIds) {
        return new NpcShopView(Source.DOG_FIGHT, client, pc, npc, npc.getId(), null,
                buying, null, saleIds, round, ticketOptions);
    }

    public int resultId() { return resultId; }
    public int shopId() { return shopId; }
    public boolean buying() { return buying; }
    public boolean dynamic() { return dynamic; }
    public boolean dogFight() { return source == Source.DOG_FIGHT; }

    public L1NpcInstance npc() {
        return npcIdentity != 0L && CWorld.getInstance().findObjectBySmartId(npcIdentity) instanceof L1NpcInstance npc
                ? npc : null;
    }

    public boolean currentFor(CUserSocket client, L1PcInstance pc) {
        if (pc == null || pc.getId() != ownerId || pc.getSessionStamp() != sessionStamp
                || !client.ownsReadyGameSession() || client.getActiveChar() != pc || pc.getOnlineStatus() == 0
                || client.npcDialogueVersion() != dialogueVersion || pc.getMap() != map
                || pc.isDead() || pc.isGhost() || pc.get_teleport()
                || pc.isPrivateShop() || pc.is무인상점() || pc.isAutoClanjoin()) return false;
        if (source == Source.ADEN)
            return !Config.Arden && Config.아덴상점타입 != Config.아덴상점엔샵아덴샵설정;
        if (source == Source.GM_SEARCH) return pc.isGm();
        final L1NpcInstance npc = npc();
        return npc != null && !npc.isDead() && npc.getMap() == map
                && Math.abs((long) pc.getX() - npc.getX()) <= 12
                && Math.abs((long) pc.getY() - npc.getY()) <= 12;
    }

    public L1Shop currentShop() {
        final L1Shop current = dynamic ? NpcShopManager.getInstance().getShop(shopId) : ShopTable.getInstance().get(shopId);
        return current != null && current.revision() == catalogRevision ? current : null;
    }

    public boolean isCurrentDogFightRound() {
        return source == Source.DOG_FIGHT && dogFightRound == DogFightHistory.getInstance().get_current_history();
    }

    public boolean includesTicket(int row) { return row >= 0 && row < ticketOptions; }

    public boolean includesSale(int id) { return saleIds != null && Arrays.binarySearch(saleIds, id) >= 0; }

    public L1ShopItem offer(int row) {
        return offers != null && row >= 0 && row < offers.length ? offers[row] : null;
    }

    public int catalogIndex(List<L1ShopItem> items, int row, L1ShopItem offer) {
        if (row >= 0 && row < items.size() && items.get(row) == offer) return row;
        // Only the Aden packet numbers displayed rows densely when some catalog rows are omitted.
        return source == Source.ADEN ? items.indexOf(offer) : -1;
    }
}
