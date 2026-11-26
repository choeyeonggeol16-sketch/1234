package l1j.server.server.model;

import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.datatables.NoTradable;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.companion.instance.CompanionInstanceCache;
import l1j.server.server.model.Warehouse.Warehouse;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ItemStatus;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_Trade;
import l1j.server.server.model.network.core.sc.packet.SC_TradeAddItem;
import l1j.server.server.model.network.core.sc.packet.SC_TradeStatus;
import l1j.server.server.monitor.LoggerInstance;

/** One active exchange owns both lists and both confirmation bits.
 * OFF CTrade lifecycle; L1's detached offer and durable CacheD settlement remain authoritative.
 */
public final class CTrade {
    private static final Logger LOG = Logger.getLogger(CTrade.class.getName());
    static final long TIMEOUT_MS = 300_000L;
    private static final long TIMEOUT_NS = TIMEOUT_MS * 1_000_000L;
    private static final int REQUESTED = 0, STARTED = 1, COMMITTING = 2, ENDED = 3;
    private final int id;
    private final L1PcInstance first, second;
    private final CUserSocket firstSocket, secondSocket;
    private final int firstStamp, secondStamp;
    private volatile int state = REQUESTED;
    private int doneMask;
    private boolean requestSent;
    private long lastActivity = System.nanoTime();
    // Allocated only when that participant actually offers an item.
    private ArrayList<L1ItemInstance> firstItems, secondItems;

    CTrade(int id, L1PcInstance first, L1PcInstance second,
            CUserSocket firstSocket, CUserSocket secondSocket, int firstStamp, int secondStamp) {
        this.id = id;
        this.first = first; this.second = second;
        this.firstSocket = firstSocket; this.secondSocket = secondSocket;
        this.firstStamp = firstStamp; this.secondStamp = secondStamp;
    }
    public int GetID() { return id; }
    public boolean IsResolving() { return state == COMMITTING; }
    public boolean IsEnded() { return state == ENDED; }
    L1PcInstance first() { return first; }
    L1PcInstance second() { return second; }

    private boolean member(L1PcInstance pc) { return pc == first || pc == second; }
    private boolean attached() { return first.GetTrade() == this && second.GetTrade() == this; }
    private boolean liveFirst() { return first.isItemTransferSession(firstSocket, firstStamp); }
    private boolean liveSecond() { return second.isItemTransferSession(secondSocket, secondStamp); }
    private boolean validPair() {
        return attached() && liveFirst() && liveSecond()
                && !first.isDead() && !second.isDead() && !first.isGhost() && !second.isGhost()
                && !first.isPrivateShop() && !second.isPrivateShop()
                && first.getMapId() == second.getMapId()
                && Math.abs((long) first.getX() - second.getX()) <= 1
                && Math.abs((long) first.getY() - second.getY()) <= 1;
    }
    private boolean busy() { return first.isItemTransferBusy() || second.isItemTransferBusy(); }
    private boolean expired() { return System.nanoTime() - lastActivity >= TIMEOUT_NS; }
    private void touch() { lastActivity = System.nanoTime(); }

    synchronized boolean SendRequest() {
        if (state != REQUESTED || requestSent) return false;
        if (!validPair() || busy() || expired()) { close(false, true); return false; }
        try {
            // Existing S_ASK/C_Attr idx field provides the identity of this dialog.
            if (!SC_MessageYN.send(second, id, 252, first.getName())) {
                close(false, true); return false;
            }
            requestSent = true;
            touch();
            return true;
        } catch (RuntimeException failure) {
            close(false, true);
            LOG.log(Level.WARNING, "Trade request send failed", failure);
            return false;
        }
    }

    /** Only the invited participant may answer the matching, published request. */
    public synchronized void Request(L1PcInstance replier, int token, int answer) {
        if (state != REQUESTED || !requestSent || replier != second || token != id
                || (answer != 0 && answer != 1)) return;
        if (!validPair() || busy() || expired()) { close(false, true); return; }
        if (answer == 0) {
            try { SC_ServerMessage.send(first, 253, second.getName()); }
            finally { close(false, true); }
            return;
        }
        try {
            if (!SC_Trade.send(firstSocket, second.getName()) || !SC_Trade.send(secondSocket, first.getName())) {
                close(false, true); return;
            }
            state = STARTED;
            touch();
        } catch (RuntimeException failure) {
            close(false, true);
            LOG.log(Level.WARNING, "Trade start send failed", failure);
        }
    }

    private static boolean characterToken(L1ItemInstance item) {
        return item.getItemId() == L1ItemId.HIGH_CHARACTER_TRADE || item.getItemId() == L1ItemId.LOW_CHARACTER_TRADE;
    }

    /** Revalidate live restrictions after both inventory gates are reserved. */
    private static boolean canTransferOffer(L1PcInstance from,L1ItemInstance source) {
        if(source==null || source.getItem()==null || source.isEquipped()
                || !source.getItem().isTradable() || source.getBless()>=128 || characterToken(source)
                || (!from.isGm() && NoTradable.getInstance().isNoTradable(source.getItemId()))
                || !CompanionInstanceCache.is_companion_oblivion(source.getId())) return false;
        for(Object value:from.getPetList().values())
            if(value instanceof L1PetInstance pet && pet.getItemObjId()==source.getId()) return false;
        for(Object value:from.getDollList().values())
            if(value instanceof L1DollInstance doll && doll.getItemObjId()==source.getId()) return false;
        return true;
    }

    private static boolean matchesOffer(L1ItemInstance source,L1ItemInstance offer) {
        return source!=null && offer!=null && source.getId()==offer.getId()
                && source.getItemId()==offer.getItemId() && offer.getCount()>0
                && source.getCount()>=offer.getCount() && (source.isStackable() || offer.getCount()==1)
                && source.getEnchantLevel()==offer.getEnchantLevel() && source.getBless()==offer.getBless()
                && source.getChargeCount()==offer.getChargeCount()
                && source.getAttrEnchantLevel()==offer.getAttrEnchantLevel()
                && source.getSpecialEnchant()==offer.getSpecialEnchant()
                && source.isPackage()==offer.isPackage()
                && source.isIdentified()==offer.isIdentified()
                && source.get_durability()==offer.get_durability()
                && source.getRemainingTime()==offer.getRemainingTime()
                && source.get_bless_level()==offer.get_bless_level()
                && source.get_bless_level2()==offer.get_bless_level2()
                && source.get_item_level()==offer.get_item_level()
                && source.getKeyId()==offer.getKeyId()
                && Objects.equals(source.getLastUsed(),offer.getLastUsed())
                && Objects.equals(source.getEndTime(),offer.getEndTime())
                && Objects.equals(source.getHotel_Town(),offer.getHotel_Town());
    }


    public synchronized void RequestAddItems(L1PcInstance player, int objectId, int requestedCount) {
        if (!member(player) || state != STARTED || requestedCount <= 0) return;
        if (!validPair() || expired()) { close(false, true); return; }
        if (busy()) return;
        final L1PcInstance partner = player == first ? second : first;
        if (doneMask != 0) {
            SC_SystemMessage.send(player, "올리기 불가능 : 한쪽이 완료를 누른 상태");
            SC_SystemMessage.send(partner, "올리기 불가능 : 한쪽이 완료를 누른 상태");
            return;
        }
        final L1ItemInstance source = player.getInventory().GetByServerID(objectId);
        if (source == null || source.getItem() == null || source.getCount() <= 0
                || (!source.isStackable() && requestedCount != 1)) return;
        if (characterToken(source)) {
            SC_SystemMessage.send(player, "캐릭터 거래는 안전한 계정 이전 처리가 준비될 때까지 이용할 수 없습니다.");
            return;
        }
        if (!canTransferOffer(player, source)) {
            if (source.isEquipped()) SC_ServerMessage.send(player, 906);
            else SC_ServerMessage.send(player, 210, source.getItem().getName());
            return;
        }
        int amount = Math.min(requestedCount, source.getCount());
        if (amount > CInventory.MAX_AMOUNT) return;
        ArrayList<L1ItemInstance> items = player == first ? firstItems : secondItems;
        L1ItemInstance previous = null;
        if (items != null) {
            for (int i = 0, size = items.size(); i < size; i++) {
                L1ItemInstance item = items.get(i);
                if (item.getId() == objectId) { previous = item; break; }
            }
        }
        if (previous != null && !matchesOffer(source, previous)) { close(false, true); return; }
        long offered = (long) amount + (previous == null ? 0 : previous.getCount());
        if (offered > source.getCount() || offered > CInventory.MAX_AMOUNT) return;
        if (partner.getInventory().CheckAddable(source, amount) != CInventory.OK) {
            SC_ServerMessage.send(partner, 270); SC_ServerMessage.send(player, 271); return;
        }
        final L1ItemInstance image;
        if (previous != null) {
            previous.setCount((int) offered);
            image = previous;
        } else {
            if (items == null) {
                items = new ArrayList<>(4);
                if (player == first) firstItems = items; else secondItems = items;
            }
            if (items.size() >= CUserInventory.MAX_SIZE) return;
            image = Warehouse.transferCopy(source, objectId, (int) offered, source.getChargeCount());
            items.add(image);
        }
        try {
            if (!SC_TradeAddItem.send(player == first ? firstSocket : secondSocket, image, amount, 0)
                    || !SC_TradeAddItem.send(player == first ? secondSocket : firstSocket, image, amount, 1)) {
                close(false, true);
                return;
            }
            touch();
        } catch (RuntimeException failure) {
            close(false, true);
            LOG.log(Level.WARNING, "Trade offer send failed", failure);
        }
    }

    private static void plan(L1InventoryTransaction tx, L1PcInstance from, L1PcInstance to,
            ArrayList<L1ItemInstance> items) {
        if (items == null) return;
        for (int i = 0, size = items.size(); i < size; i++) {
            L1ItemInstance offer = items.get(i);
            L1ItemInstance source = from.getInventory().GetByServerID(offer.getId());
            if (!canTransferOffer(from, source) || !matchesOffer(source, offer))
                throw new IllegalStateException("Trade offer preimage or eligibility changed");
            if (tx.transfer(from, source, offer.getCount(), to) == null)
                throw new IllegalStateException("Trade transfer rejected");
        }
    }
    private static boolean empty(ArrayList<L1ItemInstance> items) { return items == null || items.isEmpty(); }

    // Called only by the existing inventory transaction admission path.
    boolean ReadyForCommit(L1PcInstance a, L1PcInstance b) {
        return state == COMMITTING && attached()
                && ((a == first && b == second) || (a == second && b == first));
    }

    public void PressDone(L1PcInstance player) {
        final L1PcInstance partner;
        synchronized (this) {
            if (!member(player) || state != STARTED) return;
            if (!validPair() || expired()) { close(false, true); return; }
            if (busy()) return;
            final int bit = player == first ? 1 : 2;
            if ((doneMask & bit) != 0) return;
            doneMask |= bit;
            touch();
            if (doneMask != 3) return;
            if (empty(firstItems) && empty(secondItems)) { close(false, true); return; }
            state = COMMITTING;
            partner = player == first ? second : first;
        }
        // Never hold the exchange monitor across CacheD admission or replay.
        // COMMITTING freezes both lists, rejects duplicates/cancel and retains both PCs.
        try (L1InventoryTransaction tx = L1InventoryTransaction.beginTrade(this, player, partner)) {
            if (!ReadyForCommit(player, partner) || !validPair())
                throw new IllegalStateException("Trade relation changed during admission");
            plan(tx, first, second, firstItems);
            plan(tx, second, first, secondItems);
            tx.onTerminalFailure(this::terminalFailure);
            tx.commit(this::Done);
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Trade not admitted", failure);
            synchronized (this) { if (state == COMMITTING) close(false, true); }
        }
    }

    private static void logItems(L1PcInstance from, L1PcInstance to, ArrayList<L1ItemInstance> items) {
        if (items == null) return;
        for (int i = 0, size = items.size(); i < size; i++) {
            L1ItemInstance item = items.get(i);
            try {
                if (item.getItemId() == 400254) LoggerInstance.getInstance().수표(true, from, to, item, item.getCount());
                else LoggerInstance.getInstance().addTrade(true, from, to, item, item.getCount());
            } catch (RuntimeException failure) { LOG.log(Level.WARNING, "Committed trade audit failed", failure); }
        }
    }
    private synchronized void Done() {
        if (state != COMMITTING) return;
        try {
            logItems(first, second, firstItems);
            logItems(second, first, secondItems);
            if (liveFirst()) first.getLight().turnOnOffLight();
            if (liveSecond()) second.getLight().turnOnOffLight();
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Committed trade light refresh failed", failure);
        } finally { close(true, false); }
    }
    /** Rollback or uncertain COMMIT: retire registry even if owner replay is rejected.
     * No item mutation here. CacheD retains its quarantine/owner-gate responsibility.
     */
    private synchronized void terminalFailure() {
        if (state != COMMITTING) return;
        try {
            if (liveFirst()) SC_SystemMessage.send(first, "거래 DB 처리 결과 확인이 필요합니다.");
            if (liveSecond()) SC_SystemMessage.send(second, "거래 DB 처리 결과 확인이 필요합니다.");
        } catch (RuntimeException failure) { LOG.fine("Trade failure notification expired"); }
        finally { close(false, false); }
    }

    private static void resync(L1PcInstance pc, ArrayList<L1ItemInstance> items) {
        if (items == null) return;
        for (int i = 0, size = items.size(); i < size; i++) {
            L1ItemInstance source = pc.getInventory().GetByServerID(items.get(i).getId());
            if (source != null) {
                try { SC_ItemStatus.send(pc, source); }
                catch (RuntimeException failure) { LOG.fine("Trade resync send expired"); }
            }
        }
    }
    // Caller owns the trade monitor. Keep IDs attached until terminal packets
    // are admitted; a new exchange cannot interleave with this old result.
    private void close(boolean success, boolean resync) {
        if (state == ENDED) return;
        state = ENDED;
        try {
            if (liveFirst()) {
                if (resync) resync(first, firstItems);
                SC_TradeStatus.send(firstSocket, success ? 0 : 1);
            }
        } catch (RuntimeException failure) { LOG.fine("Trade result send expired"); }
        try {
            if (liveSecond()) {
                if (resync) resync(second, secondItems);
                SC_TradeStatus.send(secondSocket, success ? 0 : 1);
            }
        } catch (RuntimeException failure) { LOG.fine("Trade result send expired"); }
        finally {
            firstItems = null; secondItems = null;
            CTradeManager.getInstance().DeleteTrade(this);
        }
    }
    public synchronized void Canceled(L1PcInstance pc) {
        if (!member(pc) || state == COMMITTING || state == ENDED) return;
        close(false, true);
    }
    /** One deadline per active trade, refreshed on successful operations. */
    synchronized long CheckTradeTimeout() {
        if (state == COMMITTING || state == ENDED) return 0L;
        long remaining = TIMEOUT_NS - (System.nanoTime() - lastActivity);
        if (remaining <= 0 || !validPair()) { close(false, true); return 0L; }
        return Math.max(1L, (remaining + 999_999L) / 1_000_000L);
    }
}
