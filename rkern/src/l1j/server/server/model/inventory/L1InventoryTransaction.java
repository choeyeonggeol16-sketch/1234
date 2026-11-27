package l1j.server.server.model.inventory;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.Instance.CItemList;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CTransaction;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage.ItemSnapshot;
import l1j.server.server.templates.L1Item;

/** Prepared inventory changes, using CacheD's existing receipt/FIFO machinery.
 * No live item, world object, packet or success effect is changed during prepare.
 * This class owns no thread, executor, queue, timer or database connection.
 */
public final class L1InventoryTransaction implements AutoCloseable, Consumer<Throwable> {
    private static final Logger LOG = Logger.getLogger(L1InventoryTransaction.class.getName());
    private static final Entry[] NO_ENTRIES = new Entry[0];
    private static final Comparator<Entry> ROW_ID_ORDER =
            (left, right) -> Integer.compare(left.image.getId(), right.image.getId());
    private static final class Entry {
        final L1PcInstance originalOwner;
        final L1ItemInstance original;
        final ItemSnapshot before;
        final int rowId;
        L1PcInstance owner;
        L1ItemInstance image;
        boolean retireEquipped;
        Entry(L1PcInstance pc, L1ItemInstance item, boolean existing) {
            originalOwner = existing ? pc : null;
            original = existing ? item : null;
            before = existing ? ItemSnapshot.capture(item, pc.getId()) : null;
            rowId = item.getId();
            owner = pc;
            // grantCopy already owns a detached new image; only live inputs need copying.
            image = existing ? copy(item) : item;
        }
    }
    private final L1PcInstance primary;
    private final InventoryTransactionOwners owners;
    // Most receipts touch one or two rows. Keep insertion order in a compact
    // reference array, avoiding LinkedHashMap nodes, key boxing and buckets.
    // A primitive index is added only for unusually large multi-row receipts.
    private static final int INDEX_THRESHOLD = 9;
    // Store a single Entry directly in this field. Only multi-row receipts
    // allocate Entry[], without adding a retained field to the transaction.
    // Type invariant: 0 rows -> null; 1 row -> Entry; 2+ rows -> Entry[].
    private Object entries;
    private int entryCount;
    private TIntObjectHashMap<Entry> entryIndex;

    private Entry entryAt(int index) {
        return entryCount == 1 ? (Entry) entries : ((Entry[]) entries)[index];
    }

    private Entry findEntry(int rowId) {
        if (entryCount == 1) {
            Entry e = (Entry) entries;
            return e.rowId == rowId ? e : null;
        }
        if (entryIndex != null) return entryIndex.get(rowId);
        Entry[] rows = (Entry[]) entries;
        for (int i = 0; i < entryCount; i++) {
            Entry e = rows[i];
            if (e.rowId == rowId) return e;
        }
        return null;
    }

    private void addEntry(Entry e) {
        if (findEntry(e.rowId) != null)
            throw new IllegalStateException("Duplicate prepared item row: " + e.rowId);
        if (entryCount == 0) {
            entries = e;
            entryCount = 1;
            return;
        }
        if (entryCount == 1) {
            entries = new Entry[] { (Entry) entries, e };
            entryCount = 2;
            return;
        }
        Entry[] rows = (Entry[]) entries;
        if (entryCount == rows.length) {
            rows = Arrays.copyOf(rows, entryCount << 1);
            entries = rows;
        }
        rows[entryCount++] = e;
        if (entryCount == INDEX_THRESHOLD) {
            entryIndex = new TIntObjectHashMap<>(16);
            for (int i = 0; i < entryCount; i++) entryIndex.put(rows[i].rowId, rows[i]);
        } else if (entryIndex != null) entryIndex.put(e.rowId, e);
    }
    private final CacheD.WarehouseMove move;
    private boolean submitted;
    private boolean closed;
    private boolean invalidated;
    private Runnable failureAction;
    private Runnable durableCommitAction;
    private Runnable terminalFailureAction;
    private Runnable confirmedRollbackAction;
    private Runnable beforeInventoryReplay;
    private volatile boolean durableCommitted;
    private volatile boolean uncertainFailure;
    private int sqlCount;

    public static L1InventoryTransaction beginCharacterStat(L1PcInstance pc) {
        return new L1InventoryTransaction(pc);
    }

    private L1InventoryTransaction(L1PcInstance pc) {
        primary = Objects.requireNonNull(pc);
        owners = new InventoryTransactionOwners(pc, null, false);
        try {
            var session = owners.session(pc);
            if (session == null || !session.ownsReadyGameSession())
                throw new IllegalStateException("Character stat owner is not in game");
            move = CacheD.beginCharacterStatMove(pc.getId(), pc.getCacheDSessionEpoch());
            move.onError(this);
        } catch (RuntimeException | Error failure) {
            owners.release();
            throw failure;
        }
    }

    public static L1InventoryTransaction begin(L1PcInstance pc, int resourceKey) {
        return new L1InventoryTransaction(pc, null, resourceKey, false);
    }
    /** Reserve one atomic mail receipt alongside all recipient mailbox FIFO lanes.
     * Mail recipients are resource keys, not additional inventory participants.
     */
    public static L1InventoryTransaction begin(L1PcInstance pc, int resourceKey, int[] recipientKeys) {
        return new L1InventoryTransaction(pc, null, resourceKey, false,
                Objects.requireNonNull(recipientKeys, "recipientKeys"), false);
    }

    /** Mail recipients are ordered DB resources, not inventory participants.
     * Independent senders may queue writes to the same mailbox.
     */
    public static L1InventoryTransaction beginMail(L1PcInstance sender, int mailboxKey, int[] recipients) {
        return new L1InventoryTransaction(sender, null, mailboxKey, false,
                Objects.requireNonNull(recipients, "recipients"), true);
    }
    public static L1InventoryTransaction begin(L1PcInstance first, L1PcInstance second) {
        if (second == null || first == second) throw new IllegalArgumentException("Distinct participants required");
        return new L1InventoryTransaction(first, second, second.getId(), false);
    }
    /** All members' item rows commit in one InnoDB receipt or none commit. */
    public static L1InventoryTransaction beginParty(L1PcInstance[] participants) {
        if (participants == null || participants.length < 3)
            throw new IllegalArgumentException("Dungeon party requires at least three owners");
        return beginParty(participants[0], participants);
    }
    public static L1InventoryTransaction beginParty(L1PcInstance coordinator, L1PcInstance[] participants) {
        return new L1InventoryTransaction(coordinator, participants);
    }
    private L1InventoryTransaction(L1PcInstance coordinator, L1PcInstance[] participants) {
        primary = Objects.requireNonNull(coordinator);
        owners = new InventoryTransactionOwners(primary, participants);
        int second = 0;
        int[] more = new int[participants.length - 2];
        int index = 0;
        for (L1PcInstance participant : participants) {
            if (participant == primary) continue;
            if (second == 0) second = participant.getId();
            else more[index++] = participant.getId();
        }
        try {
            move = CacheD.beginEconomyMove(primary.getId(), second, more);
            move.onError(this);
        } catch (RuntimeException | Error failure) {
            owners.release();
            throw failure;
        }
    }

    static L1InventoryTransaction beginTrade(CTrade trade, L1PcInstance first, L1PcInstance second) {
        if (trade == null || first == null || second == null || !trade.ReadyForCommit(first, second))
            throw new IllegalStateException("Unconfirmed trade relation");
        return new L1InventoryTransaction(first, second, second.getId(), true);
    }
    private L1InventoryTransaction(L1PcInstance first, L1PcInstance second, int resourceKey, boolean trade) {
        this(first, second, resourceKey, trade, null);
    }
    private L1InventoryTransaction(L1PcInstance first, L1PcInstance second, int resourceKey,
            boolean trade, int[] recipientKeys) {
        this(first, second, resourceKey, trade, recipientKeys, false);
    }
    private L1InventoryTransaction(L1PcInstance first, L1PcInstance second, int resourceKey,
            boolean trade, int[] recipientKeys, boolean mail) {
        primary = Objects.requireNonNull(first);
        owners = new InventoryTransactionOwners(first, second, trade);
        try {
            move = mail
                    ? CacheD.beginMailEconomyMove(first.getId(), resourceKey, recipientKeys)
                    : recipientKeys == null
                            ? CacheD.beginEconomyMove(first.getId(), resourceKey)
                            : CacheD.beginEconomyMove(first.getId(), resourceKey, recipientKeys);
            move.onError(this);
        } catch (RuntimeException failure) {
            owners.release();
            throw failure;
        }
    }
    private void open() {
        if (closed || submitted || invalidated)
            throw new IllegalStateException("Inventory transaction is closed or invalidated");
    }
    private void owns(L1PcInstance pc) {
        if (!owners.contains(pc)) throw new IllegalArgumentException("Unreserved inventory owner");
    }
    private Entry entry(L1PcInstance pc, L1ItemInstance item) {
        open(); owns(pc);
        Entry e = findEntry(item.getId());
        if (e != null) {
            if (e.owner != pc || (e.original != item && e.image != item))
                throw new IllegalStateException("Item owner/identity changed while preparing");
            return e;
        }
        if (pc.getInventory().GetByServerID(item.getId()) != item || item.getCount() <= 0)
            throw new IllegalStateException("Stale inventory object");
        e = new Entry(pc,item,true);
        addEntry(e);
        return e;
    }
    public boolean debit(int templateId, int count) { return debit(primary,templateId,count); }
    public boolean debit(L1ItemInstance item,int count) { return debit(primary,item,count); }
    public boolean debitItem(L1PcInstance pc,L1ItemInstance item,int count) { return debit(pc,item,count); }
    public boolean debit(L1PcInstance pc,L1ItemInstance item,int count) {
        if (item == null || count <= 0 || item.isEquipped()) return false;
        // These physical tokens own rows/world entities outside character_items.
        // Do not replace their dedicated removal path with an item-only debit.
        // Transfers preserve their ID/related entity and are still allowed.
        if (requiresEntityDeletion(item.getItemId())) return false;
        // A failed debit must not allocate a snapshot/copy or stage an
        // unchanged item row. Still enforce the same owner and identity checks
        // before returning insufficient quantity for a live input.
        open(); owns(pc);
        Entry e = findEntry(item.getId());
        if (e == null) {
            if (pc.getInventory().GetByServerID(item.getId()) != item || item.getCount() <= 0)
                throw new IllegalStateException("Stale inventory object");
            if (item.getCount() < count) return false;
        }
        e = entry(pc,item);
        if (e.image.getCount() < count) return false;
        e.image.setCount(e.image.getCount()-count);
        return true;
    }
    /**
     * Explicit client destruction only. Ordinary material debits still refuse linked
     * entities. Caller reserves the character's mailbox lane for physical letters.
     */
    public boolean StageDestroyItem(L1ItemInstance item, int count) {
        if (item == null || count <= 0 || item.isEquipped()) return false;
        if (!requiresEntityDeletion(item.getItemId())) return debit(item, count);
        open();
        if (primary.getInventory().GetByServerID(item.getId()) != item
                || count != item.getCount() || findEntry(item.getId()) != null) return false;
        final int id = item.getId();
        final int template = item.getItemId();
        if (template >= 49016 && template <= 49025) {
            final String receiver = primary.getName();
            requireTables("character_items", "letter", "characters");
            sql(con -> l1j.server.server.datatables.LetterTable.deleteOwnedItemLetter(con, id, receiver));
        } else {
            requireTables("character_items", "spawnlist_furniture");
            sql(con -> {
                try (java.sql.PreparedStatement ps = con.prepareStatement(
                        "SELECT item_obj_id FROM spawnlist_furniture WHERE item_obj_id=? FOR UPDATE")) {
                    ps.setInt(1, id);
                    try (java.sql.ResultSet rows = ps.executeQuery()) {
                        if (rows.next()) throw new java.sql.SQLException("Furniture still deployed: " + id);
                    }
                }
            });
        }
        // The normal receipt verifies the exact character item preimage and
        // deletes its row in the same transaction as the linked-row work.
        entry(primary, item).image.setCount(0);
        return true;
    }

    /** Read the quantity still available in this receipt without staging another row. */
    public int GetPreparedItemAmount(L1PcInstance pc, L1ItemInstance item) {
        open(); owns(pc);
        if (item == null || item.isEquipped() || requiresEntityDeletion(item.getItemId())) return 0;
        final Entry e = findEntry(item.getId());
        if (e != null) {
            if (e.owner != pc || (e.original != item && e.image != item))
                throw new IllegalStateException("Item owner/identity changed while preparing");
            return Math.max(0, e.image.getCount());
        }
        if (pc.getInventory().GetByServerID(item.getId()) != item)
            throw new IllegalStateException("Stale inventory object");
        return Math.max(0, item.getCount());
    }

    private static boolean requiresEntityDeletion(int template) {
        return (template >= 49016 && template <= 49025)
                || (template >= 41383 && template <= 41400);
    }
    public boolean debit(L1PcInstance pc,int templateId,int count) {
        return debitMatchingMaterial(pc, templateId, 0, false, count);
    }

    /** Recipe material selection shares the prepared-row debit and owner gate. */
    public boolean DebitRecipeMaterial(int templateId, int enchant, int count) {
        return debitMatchingMaterial(primary, templateId, enchant, true, count);
    }

    private boolean debitMatchingMaterial(L1PcInstance pc, int templateId,
            int enchant, boolean matchEnchant, int count) {
        open(); owns(pc);
        if (count <= 0 || requiresEntityDeletion(templateId)) return false;
        long available=0;
        L1ItemInstance first = null;
        int firstCount = 0;
        final L1ItemInstance[] candidates = pc.getInventory().GetUnequippedItemListByClassID(templateId);
        for (L1ItemInstance item : candidates) {
            if (item == null || item.isEquipped() || item.getItemId()!=templateId
                    || (matchEnchant && item.getEnchantLevel() != enchant)) continue;
            Entry e=findEntry(item.getId());
            if (e != null && e.owner != pc) continue;
            int remaining = e == null ? item.getCount() : e.image.getCount();
            if (remaining <= 0) continue;
            if (first == null) { first = item; firstCount = remaining; }
            available += remaining;
            if (available >= count) break;
        }
        if (available < count) return false;
        // Most consumables live in one stack: skip a second full inventory pass.
        if (firstCount >= count) return debit(pc, first, count);
        int left=count;
        for (L1ItemInstance item : candidates) {
            if (left==0) break;
            if (item == null || item.isEquipped() || item.getItemId()!=templateId
                    || (matchEnchant && item.getEnchantLevel() != enchant)) continue;
            Entry e=findEntry(item.getId());
            if (e!=null && e.owner!=pc) continue;
            final int remaining = e == null ? item.getCount() : e.image.getCount();
            if (remaining <= 0) continue;
            final int take = Math.min(left, remaining);
            if (!debit(pc,item,take)) throw new IllegalStateException("Prepared debit changed");
            left-=take;
        }
        return left==0;
    }
    public L1ItemInstance grant(int template,int count) { return grant(primary,template,count); }
    public L1ItemInstance grant(L1PcInstance pc,int template,int count) {
        L1Item definition=CObjectDB.GetItem(template);
        if (definition==null || count<=0 || count>CInventory.MAX_AMOUNT
                || (!definition.isStackable() && count>CUserInventory.MAX_SIZE)) return null;
        L1ItemInstance image=new L1ItemInstance(definition,count);
        image.setId(IdFactory.getInstance().nextId());
        image.setBless(definition.getBless());
        image.setIdentified(true);
        initializeNewGrant(image);
        // Created locally and not yet published: no need to clone this image.
        return grantCopyPrepared(pc,image,count,true);
    }
    public L1ItemInstance grant(L1ItemInstance image) {
        open();
        if (image == null || image.getCount() <= 0 || image.getCount()>CInventory.MAX_AMOUNT
                || (!image.isStackable() && image.getCount()>CUserInventory.MAX_SIZE)) return null;
        // A grant is a NEW persistent row, never an already published world item.
        // Transferring an owned item must use transfer() with its preimage check.
        CItemList.requireDetached(image);
        if (image.getId() > 0 && CWorld.getInstance().findObject(image.getId()) != null)
            throw new IllegalStateException("Reward must be a detached item: " + image.getId());
        L1ItemInstance prepared=copy(image);initializeNewGrant(prepared);
        return grantCopyPrepared(primary,prepared,prepared.getCount(),true);
    }
    /** The existing storeItem charge/fuel/expiry initialization, on a detached
     * NEW reward only. Ownership transfers never reset a purchased lifetime.
     */
    private static void initializeNewGrant(L1ItemInstance item) {
        item.setChargeCount(item.GetInitialChargeCount(item.getItemId()));
        L1ItemInstance.InitializeNewItemLifetimeAndFuel(item, true);
    }
    public L1ItemInstance grantCopy(L1PcInstance pc,L1ItemInstance image,int count) {
        // Public inputs may be held or mutated by a caller; always detach them.
        return grantCopyPrepared(pc,image,count,false);
    }
    private L1ItemInstance grantCopyPrepared(L1PcInstance pc,L1ItemInstance image,int count,boolean fresh) {
        open(); owns(pc);
        if (image==null || count<=0 || count>CInventory.MAX_AMOUNT) return null;
        if (!image.isStackable() && count!=1) {
            if (count > CUserInventory.MAX_SIZE) return null;
            L1ItemInstance last=null;
            for (int i=0;i<count;i++) {
                L1ItemInstance single=copy(image);single.setCount(1);
                single.setId(IdFactory.getInstance().nextId());
                // single is already a private detached copy for this row.
                last=grantCopyPrepared(pc,single,1,true);
                if (last==null) throw new IllegalStateException("Grant capacity exceeded");
            }
            return last;
        }
        L1ItemInstance prepared=fresh ? image : copy(image);
        prepared.setCount(count);prepared.setEquipped(false);
        if (prepared.get_bless_level()>0) prepared.setBless(0);
        if (prepared.isStackable()) {
            for (int index = 0; index < entryCount; index++) {
                Entry e = entryAt(index);
                if (e.owner==pc && e.image.getCount()>0 && mergeable(e.image,prepared)) {
                    long total=(long)e.image.getCount()+count;
                    if (total>CInventory.MAX_AMOUNT) return null;
                    e.image.setCount((int)total);
                    return e.image;
                }
            }
            final L1ItemInstance item = findLiveMergeTarget(pc, prepared);
            if (item != null) {
                final long total = (long) item.getCount() + count;
                if (total > CInventory.MAX_AMOUNT) return null;
                final Entry e = entry(pc, item);
                e.image.setCount((int) total);
                return e.image;
            }
        }
        if (prepared.getId()<=0 || findEntry(prepared.getId()) != null)
            prepared.setId(IdFactory.getInstance().nextId());
        Entry added=new Entry(pc,prepared,false);
        addEntry(added);
        return added.image;
    }
    /** Only unprepared, positive live rows may participate in a new merge. */
    private L1ItemInstance findLiveMergeTarget(L1PcInstance pc, L1ItemInstance image) {
        for (L1ItemInstance item : pc.getInventory().GetItemListByClassID(image.getItemId())) {
            if (item != null && item.getCount() > 0 && findEntry(item.getId()) == null
                    && mergeable(item, image)) return item;
        }
        return null;
    }

    public L1ItemInstance transfer(L1PcInstance from,L1ItemInstance item,int count,L1PcInstance to) {
        open(); owns(from); owns(to);
        if (from==to || count<=0 || item==null || item.isEquipped()
                || (!item.isStackable() && count!=1)) throw new IllegalArgumentException("Transfer item/count");
        // Reject insufficient full/partial transfers before allocating an Entry,
        // durable preimage and detached image. Preserve the original owner and
        // object-identity checks for both live and already staged rows.
        Entry prepared = findEntry(item.getId());
        if (prepared == null) {
            if (from.getInventory().GetByServerID(item.getId()) != item || item.getCount() <= 0)
                throw new IllegalStateException("Stale inventory object");
            if (item.getCount() < count) throw new IllegalStateException("Insufficient transfer quantity");
        } else {
            if (prepared.owner != from || (prepared.original != item && prepared.image != item))
                throw new IllegalStateException("Item owner/identity changed while preparing");
            if (prepared.image.getCount() < count)
                throw new IllegalStateException("Insufficient transfer quantity");
        }
        Entry e=entry(from,item);
        if (count==e.image.getCount()) {
            // Preserve the original row ID when no merge is required.
            boolean merge=false;
            if (e.image.isStackable()) {
                for (int index = 0; index < entryCount; index++) {
                    Entry other = entryAt(index);
                    if (other.owner==to && other.image.getCount()>0 && mergeable(other.image,e.image)) {merge=true;break;}
                }
                if (!merge) merge = findLiveMergeTarget(to, e.image) != null;
            }
            if (!merge) { e.owner=to;return e.image; }
        }
        L1ItemInstance carry=copy(e.image);carry.setCount(count);
        e.image.setCount(e.image.getCount()-count);
        carry.setId(IdFactory.getInstance().nextId());
        L1ItemInstance granted=grantCopyPrepared(to,carry,count,true);
        if (granted==null) throw new IllegalStateException("Transfer capacity exceeded");
        return granted;
    }
    /** A blessing-only economy mutation may keep an equipped target equipped.
     * The prepared copy changes only this durable field; equipped effects,
     * identity, item count and live inventory remain untouched before COMMIT.
     */
    public void changeBless(L1ItemInstance item, int newBless) {
        if (newBless < 0 || newBless > 255) throw new IllegalArgumentException("Invalid item blessing");
        Entry e = entry(primary, item);
        if (e.image.getCount() <= 0) throw new IllegalStateException("Item was consumed by this transaction");
        e.image.setBless(newBless);
    }
    /** Replace a cursed template with its corresponding normal template.
     * The item stays in the same inventory row (including its equipment flag).
     * The live template and blessing are changed only by committed replay.
     */
    public void uncursedTemplate(L1ItemInstance item, L1Item normal) {
        if (item == null || normal == null) throw new IllegalArgumentException("Missing curse conversion item");
        Entry e = entry(primary, item);
        L1Item cursed = e.image.getItem();
        if (cursed.getBless() != 2 || cursed.getItemId() - 200000 != normal.getItemId()
                || cursed.getType2() != normal.getType2() || cursed.getType() != normal.getType()
                || cursed.getType1() != normal.getType1()
                || e.image.getBless() < 1 || e.image.getCount() <= 0)
            throw new IllegalStateException("Invalid cursed item conversion");
        e.image.setItem(normal);
        e.image.setBless(e.image.getBless() - 1);
    }
    /** Persist charge-only changes even on a currently equipped item.
     * Does not mutate the live instance before the same receipt-backed COMMIT.
     */
    public void changeChargeCount(L1ItemInstance item, int charge) {
        if (charge < 0) throw new IllegalArgumentException("Negative charge count");
        Entry e = entry(primary, item);
        if (e.image.getCount() <= 0) throw new IllegalStateException("Consumed inventory item");
        e.image.setChargeCount(charge);
    }
    /** Stage the equipment change in the same receipt as its scroll. */
    public void changeEnchantLevel(L1ItemInstance item, int expectedLevel, int nextLevel) {
        final long delta = (long) nextLevel - expectedLevel;
        if (delta != -1 && (delta < 1 || delta > 3))
            throw new IllegalArgumentException("Invalid enchant adjustment " + delta);
        enchantTarget(item, expectedLevel).image.setEnchantLevel(nextLevel);
    }

    public void stageEnchantFailure(L1ItemInstance item, int expectedLevel, boolean reset) {
        final Entry target = enchantTarget(item, expectedLevel);
        target.retireEquipped = target.image.isEquipped();
        target.image.setEquipped(false);
        if (reset) {
            target.image.setEnchantLevel(0);
        } else {
            target.image.setCount(0);
        }
    }

    private Entry enchantTarget(L1ItemInstance item, int expectedLevel) {
        open();
        if (item == null || item.getItem() == null || item.getEnchantLevel() != expectedLevel
                || (item.getItem().getType2() != 1 && item.getItem().getType2() != 2)) {
            throw new IllegalStateException("Invalid enchant receipt target/state");
        }
        final Entry e = entry(primary, item);
        if (e.image.getCount() <= 0 || e.image.getEnchantLevel() != expectedLevel
                || e.image.getItemId() != item.getItemId()) {
            throw new IllegalStateException("Invalid prepared enchant target/state");
        }
        return e;
    }

    /** Identify an inventory target in the same durable receipt as a scroll debit.
     * Identification is legal for equipped equipment as well; unlike generic
     * change(), this changes only the identified bit on the detached image.
     */
    public void identify(L1ItemInstance item) {
        Entry e = entry(primary, item);
        if (e.image.getCount() <= 0)
            throw new IllegalStateException("Consumed identification target");
        e.image.setIdentified(true);
    }
    /** Repair is persisted in the same receipt as whetstone consumption.
     * Equipped equipment remains equipped and live durability is untouched
     * until the owner replays the committed item image.
     */
    public void repairDurability(L1ItemInstance item) {
        Entry e = entry(primary, item);
        if (e.image.getCount() <= 0 || e.image.getItem().getType2() == 0
                || e.image.get_durability() <= 0)
            throw new IllegalStateException("Item cannot be repaired");
        e.image.set_durability(e.image.get_durability() - 1);
    }
    /** Light-fuel persistence is part of the same receipt as oil consumption.
     * An equipped light remains equipped; only its persistent timer changes.
     */
    public void changeRemainingTime(L1ItemInstance item, int remaining) {
        if (remaining < 0) throw new IllegalArgumentException("Negative remaining time");
        Entry e = entry(primary, item);
        if (e.image.getCount() <= 0) throw new IllegalStateException("Consumed inventory item");
        e.image.setRemainingTime(remaining);
    }
    public void change(L1ItemInstance item,Consumer<L1ItemInstance> mutation) { change(primary,item,mutation); }
    public void change(L1PcInstance pc,L1ItemInstance item,Consumer<L1ItemInstance> mutation) {
        // Generic field mutation rejects equipped items. Avoid preparing a
        // snapshot/copy solely to reject an otherwise-valid equipped input.
        // Stale/wrong-owner references still go through entry() for validation.
        open(); owns(pc);
        if (item != null && item.isEquipped()) {
            Entry prepared = findEntry(item.getId());
            if ((prepared == null && pc.getInventory().GetByServerID(item.getId()) == item && item.getCount() > 0)
                    || (prepared != null && prepared.owner == pc
                            && (prepared.original == item || prepared.image == item)))
                throw new IllegalArgumentException("Unequip before changing persistent item fields");
        }
        Entry e=entry(pc,item);
        if (item.isEquipped()) throw new IllegalArgumentException("Unequip before changing persistent item fields");
        // The input may itself be the prepared image. Capture identity
        // BEFORE calling user code instead of comparing two aliases afterwards.
        final int template = e.image.getItemId();
        try {
            mutation.accept(e.image);
            if (e.image.getId() != e.rowId || e.image.getItemId() != template
                    || e.image.getCount() <= 0 || e.image.getCount() > CInventory.MAX_AMOUNT
                    || e.image.isEquipped())
                throw new IllegalStateException("Invalid item field change");
        } catch (RuntimeException | Error failure) {
            // A callback may have modified the image before throwing. Never
            // allow that partially mutated receipt to reach SQL staging.
            invalidated = true;
            throw failure;
        }
    }
    public void sql(CTransaction.Mutation mutation) { open();move.stageWarehouseSql(Objects.requireNonNull(mutation));sqlCount++; }
    public boolean hasChanges() { return sqlCount != 0 || entryCount != 0; }
    public void requireTables(String... tables) {
        String[] names=tables.clone();sql(con->CTransaction.requireTransactionalTables(con,names));
    }
    public void onFailure(Runnable callback) { open();failureAction=callback; }

    /** DB-worker callback after confirmed receipt COMMIT, before session-bound replay.
     * Only thread-safe, nonblocking cache publication is allowed here.
     */
    public void onDurableCommit(Runnable callback) {
        open();
        if (durableCommitAction != null) throw new IllegalStateException("Duplicate durable callback");
        durableCommitAction = Objects.requireNonNull(callback);
    }

    /** Release a world reservation even if no original session can receive a reply.
     * The callback must be thread-safe and nonblocking.
     */
    /** Ground reservation cleanup must also run when a confirmed rollback has no live session. */
    void onConfirmedRollback(Runnable callback) {
        open();
        confirmedRollbackAction = Objects.requireNonNull(callback);
    }

    /** Apply a reserved source after durable confirmation, before installing its inventory image. */
    void beforeInventoryReplay(Runnable callback) {
        open();
        beforeInventoryReplay = Objects.requireNonNull(callback);
    }

    boolean isDurablyCommitted() { return durableCommitted; }
    public boolean isUncertainFailure() { return uncertainFailure; }

    public void onTerminalFailure(Runnable callback) {
        open();
        terminalFailureAction = Objects.requireNonNull(callback);
    }

    public void commit(Runnable afterCommitted) {
        commitInternal(null, afterCommitted, false);
    }

    /** Committed item effects may change equipment, award items or save progression.
     * Its callback runs only AFTER the durable owner receipt is retired and the
     * participant gate released, still on the existing CIO owner.
     */
    public void commitAfterInventoryRelease(Runnable afterCommitted) {
        commitInternal(null, afterCommitted, true);
    }

    /** Publish quest state on the owner while its gate is held; only then
     * retire the receipt and resume the existing XML continuation. */
    public void commitAfterInventoryRelease(Runnable publish, Runnable afterCommitted) {
        commitInternal(Objects.requireNonNull(publish), afterCommitted, true);
    }

    private void commitInternal(Runnable publish, Runnable afterCommitted, boolean releaseBeforeEffects) {
        open();
        validatePreparedRows();
        validateLive();validateCapacity();
        // Keep singleton receipts entirely inline. Multi-row receipts sort a
        // private view, preserving insertion order if SQL staging throws.
        final int orderedCount = entryCount;
        final Entry singleton = orderedCount == 1 ? (Entry) entries : null;
        Entry[] ordered = NO_ENTRIES;
        if (orderedCount > 1) {
            Entry[] rows = (Entry[]) entries;
            // Insertion order is frequently already row-ID order. Once the
            // receipt is submitted, these references cannot be appended to;
            // a sorted view needs no separate allocation in that case.
            boolean sorted = true;
            for (int i = 1; i < orderedCount; i++) {
                if (ROW_ID_ORDER.compare(rows[i - 1], rows[i]) > 0) {
                    sorted = false;
                    break;
                }
            }
            if (sorted) ordered = rows;
            else {
                ordered = Arrays.copyOf(rows, orderedCount);
                Arrays.sort(ordered, ROW_ID_ORDER);
            }
        }
        final Entry[] commitOrder = ordered;

        // Row preimages must be locked/verified before ANY item write. For a
        // multi-row receipt, one SQL mutation does the same ordered checks,
        // avoiding one captured callback and one operation per preimage.
        // Keep the 1-row path as a single lightweight captured callback.
        if (orderedCount == 1) {
            final Entry e = singleton;
            if (e.before != null)
                move.stageWarehouseSql(con -> CTransaction.verifyItemPreimage(con, e.before));
        } else if (orderedCount > 1) {
            boolean hasPreimage = false;
            for (int i = 0; i < orderedCount; i++) {
                if (commitOrder[i].before != null) { hasPreimage = true; break; }
            }
            if (hasPreimage)
                move.stageWarehouseSql(con -> {
                    try (var check = CTransaction.prepareItemPreimageCheck(con)) {
                        for (int i = 0; i < orderedCount; i++) {
                            ItemSnapshot before = commitOrder[i].before;
                            if (before != null) CTransaction.verifyItemPreimage(check, before);
                        }
                    }
                });
        }
        for (int i = 0; i < orderedCount; i++) {
            final Entry e = singleton != null ? singleton : commitOrder[i];
            if (e.image.getCount() == 0) {
                if (e.before != null) move.prepareOwnedDelete(e.originalOwner.getId(), e.image.getId());
            } else move.prepareOwnedWrite(e.owner.getId(), e.image, e.before == null ? 0 : e.originalOwner.getId());
        }
        // Preserve the DB-stage snapshot and the detached replay image barrier.
        for (int i = 0; i < orderedCount; i++) {
            final Entry e = singleton != null ? singleton : commitOrder[i];
            e.image = copy(e.image);
        }
        move.commit(()->{
            durableCommitted = true;
            final Runnable durable = durableCommitAction;
            durableCommitAction = null;
            if (durable != null) durable.run();
            post(()->{
                validateLiveRows();
                if (beforeInventoryReplay != null) beforeInventoryReplay.run();
                CacheD.applyCommittedWarehouseMove(move,()->{
                    for (int i = 0; i < orderedCount; i++) {
                        final Entry e = singleton != null ? singleton : commitOrder[i];
                        if (e.original != null && (e.owner != e.originalOwner || e.image.getCount() == 0))
                            e.originalOwner.getInventory().applyEconomyImage(e.original, null);
                    }
                    for (int i = 0; i < orderedCount; i++) {
                        final Entry e = singleton != null ? singleton : commitOrder[i];
                        if (e.image.getCount() > 0)
                            e.owner.getInventory().applyEconomyImage(e.originalOwner == e.owner ? e.original : null, e.image);
                    }
                });
                if (releaseBeforeEffects) {
                    try {
                        if (publish != null) publish.run();
                        // Keep admitted actors published until the effect completes,
                        // including party members when the effect starts a dungeon.
                        if (afterCommitted != null && !owners.beginContinuations())
                            throw new IllegalStateException("Stale inventory effect session");
                    } finally {
                        releaseGates();
                    }
                } else {
                    // The durable item images are already published. Packet/effect
                    // callbacks must not strand the owner's item-transfer gate.
                    // Let failures propagate to the post-completion quarantine path.
                    try {
                        if (afterCommitted != null) afterCommitted.run();
                    } finally {
                        releaseGates();
                    }
                }
            }, failure -> finishReplay(releaseBeforeEffects ? afterCommitted : null, failure));
        });
        submitted=true;
    }

    private void finishReplay(Runnable afterCommitted, Throwable failure) {
        if (afterCommitted == null) {
            move.finishOwnerApply(failure);
            if (failure != null) failReplay(failure);
            return;
        }
        if (failure != null) {
            try { move.finishOwnerApply(failure); }
            finally { finishReleasedEffect(failure); }
            return;
        }
        try {
            move.finishOwnerApply(null, barrierFailure -> {
                if (barrierFailure != null) finishReleasedEffect(barrierFailure);
                else dispatchReleasedEffect(afterCommitted);
            });
        } catch (RuntimeException acknowledgementFailure) {
            finishReleasedEffect(acknowledgementFailure);
        }
    }

    /** The barrier can retire on the DB worker when CIO acknowledged early. */
    private void dispatchReleasedEffect(Runnable afterCommitted) {
        try {
            CIOThreadCallback.getInstance().completeWarehouseMove(primary, owners.stamp(primary), () -> {
                synchronized (owners.monitor(primary)) {
                    if (!owners.startContinuations())
                        throw new IllegalStateException("Stale inventory effect session");
                    afterCommitted.run();
                }
            }, this::finishReleasedEffect);
        } catch (RuntimeException rejected) {
            finishReleasedEffect(rejected);
        }
    }

    private void finishReleasedEffect(Throwable failure) {
        try {
            if (failure != null) failReplay(failure);
        } finally {
            owners.endContinuations();
        }
    }

    private void failReplay(Throwable failure) {
        try { notifyTerminalFailure(); }
        finally { quarantine(failure); }
    }
    /** The immutable row key and replay equipment policy must agree with
     * the detached image before any SQL mutation is staged.
     */
    static void ValidatePreparedRow(int rowId, L1ItemInstance image,
            boolean originallyEquipped, boolean sameOwner) {
        if (image == null || rowId <= 0 || image.getId() != rowId
                || image.getItem() == null || image.getCount() < 0
                || image.getCount() > CInventory.MAX_AMOUNT
                || (originallyEquipped && (!sameOwner || image.getCount() == 0))
                || image.isEquipped() != originallyEquipped)
            throw new IllegalStateException("Invalid prepared inventory row: " + rowId);
    }

    private void validatePreparedRows() {
        for (int i = 0; i < entryCount; i++) {
            final Entry e = entryAt(i);
            owns(e.owner);
            final boolean wasEquipped = e.before != null && e.before.equipped != 0;
            final boolean sameOwner = e.originalOwner == e.owner;
            if (e.retireEquipped && (!wasEquipped || !sameOwner
                    || (e.image.getCount() > 0 && e.image.getEnchantLevel() != 0))) {
                throw new IllegalStateException("Invalid equipped enchant failure: " + e.rowId);
            }
            ValidatePreparedRow(e.rowId, e.image, wasEquipped && !e.retireEquipped, sameOwner);
        }
    }

    private void validateLive() {
        for(int i=0;i<owners.size();i++) {
            L1PcInstance pc=owners.get(i);
            if (!owners.current(pc)) throw new IllegalStateException("Stale economy session");
        }
        validateLiveRows();
    }

    private void validateLiveRows() {
        for (int i = 0; i < entryCount; i++) {
            Entry e = entryAt(i);
            if (e.before == null) continue;
            if(e.originalOwner.getInventory().GetByServerID(e.original.getId())!=e.original
                    || !e.before.matchesCurrent(e.original,e.originalOwner.getId()))
                throw new IllegalStateException("Economy memory preimage changed: "+e.original.getId());
        }
    }
    private void validateCapacity() {
        // Capacity is an upper-bound check: when a receipt only decreases counts
        // of still-owned rows without changing item template/weight, it cannot
        // increase inventory slots, weight or per-template quantity. Avoid a full
        // inventory scan and a fresh TIntLongHashMap for the common debit path.
        // Grants, transfers, template changes and count increases take the
        // original complete projected-inventory validation below.
        if (capacityCannotIncrease()) return;
        for(int i=0;i<owners.size();i++) {
            L1PcInstance pc=owners.get(i);
            long slots=0,weight=0;
            // Count quantity, slots and weight in one inventory snapshot pass.
            TIntLongHashMap totals = new TIntLongHashMap();
            for(L1ItemInstance item:pc.getInventory().getItems()) {
                if(findEntry(item.getId()) != null) continue;
                slots++;weight+=weight(item);
                addProjectedAmount(totals,item.getItemId(),item.getCount());
            }
            for (int j = 0; j < entryCount; j++) {
                Entry e = entryAt(j);
                if (e.owner != pc || e.image.getCount() <= 0) continue;
                slots++;weight+=weight(e.image);
                addProjectedAmount(totals,e.image.getItemId(),e.image.getCount());
            }
            long scaled=(long)(weight/Math.max(1.0,Config.RATE_WEIGHT_LIMIT));
            if(slots>CUserInventory.MAX_SIZE || scaled>Integer.MAX_VALUE
                    || (scaled*100>=240L*Math.max(1,pc.getMaxWeight())))
                throw new IllegalStateException("Projected inventory capacity exceeded");
        }
    }
    private boolean capacityCannotIncrease() {
        for (int index = 0; index < entryCount; index++) {
            Entry e = entryAt(index);
            if (e.before == null || e.owner != e.originalOwner) return false;
            L1ItemInstance live = e.original;
            L1ItemInstance prepared = e.image;
            if (prepared.getCount() < 0 || prepared.getCount() > live.getCount()
                    || prepared.getItemId() != live.getItemId()
                    || prepared.getItem().getWeight() != live.getItem().getWeight())
                return false;
        }
        return true;
    }
    private static void addProjectedAmount(TIntLongHashMap totals, int itemId, int count) {
        if (totals.adjustOrPutValue(itemId, count, count) > CInventory.MAX_AMOUNT)
            throw new IllegalStateException("Inventory quantity limit");
    }
    private static long weight(L1ItemInstance item) {
        int w=item.getItem().getWeight();
        return w==0 ? 0 : Math.max(1L,(long)item.getCount()*w/1000L);
    }
    private void post(Runnable action,Consumer<Throwable> done) {
        // A persistent/unmanned shop has no active socket. Its inventory gate
        // is held, and the customer's existing CIO owner applies the prepared
        // pair. Never try to schedule a dead socket's owner/session timer.
        L1PcInstance coordinator=primary;
        CIOThreadCallback.getInstance().completeWarehouseMove(coordinator,owners.stamp(coordinator),()->{
            if (owners.size() == 1) {
                synchronized (owners.monitor(coordinator)) { runCurrentOwners(action); }
            } else if (owners.size() == 2) {
                synchronized (owners.monitor(owners.get(0))) {
                    synchronized (owners.monitor(owners.get(1))) { runCurrentOwners(action); }
                }
            } else withOwnersLocked(0, action);
        },done);
    }

    /** Session identity must be checked after the existing owner monitors
     * are acquired, not before potentially waiting for logout or a swap.
     */
    private void runCurrentOwners(Runnable action) {
        for (int i = 0; i < owners.size(); i++) {
            if (!owners.currentForReplay(owners.get(i)))
                throw new IllegalStateException("Stale economy completion participant");
        }
        action.run();
    }

    private void withOwnersLocked(int index, Runnable action) {
        if (index == owners.size()) { runCurrentOwners(action); return; }
        synchronized (owners.monitor(owners.get(index))) {
            withOwnersLocked(index + 1, action);
        }
    }

    @Override public void accept(Throwable failure) { sqlFailed(failure); }

    private void notifyTerminalFailure() {
        final Runnable callback = terminalFailureAction;
        terminalFailureAction = null;
        if (callback != null) callback.run();
    }

    private void sqlFailed(Throwable failure) {
        uncertainFailure = unknown(failure);
        try { notifyTerminalFailure(); }
        catch (RuntimeException callbackFailure) { failure.addSuppressed(callbackFailure); }
        if (uncertainFailure) {
            // Quarantine the still-connected participants even if the original
            // coordinator has already disappeared and CIO cannot admit replay.
            if (!owners.currentForReplay(primary)) quarantine(failure);
            else post(() -> quarantine(failure), rejected -> {
                if (rejected != null) quarantine(failure);
            });
            return;
        }
        // Only a confirmed rollback may make a reserved ground item pickable again.
        final Runnable rollback = confirmedRollbackAction;
        confirmedRollbackAction = null;
        if (rollback != null) {
            try { rollback.run(); }
            catch (RuntimeException callbackFailure) { failure.addSuppressed(callbackFailure); }
        }
        // Rollback was confirmed. No live inventory item changed, so there is nothing to
        // compensate. Release only on the original owner boundary.
        // completeWarehouseMove reports completion AFTER running the action.
        // A failure callback can start a new receipt as soon as we release the
        // old gate, so the terminal completion must never release it a second
        // time (which would unlock the new in-flight receipt).
        post(()->{
            releaseGates();
            if (failureAction != null) failureAction.run();
        },ignored->{
            releaseGates();
        });
    }
    private static boolean unknown(Throwable t) {
        for(;t!=null;t=t.getCause()) if(t instanceof CTransaction.OutcomeUnknownException
                || t instanceof CacheD.CommittedReplayException)return true;
        return false;
    }
    private void quarantine(Throwable failure) {
        LOG.log(Level.SEVERE,"Committed/uncertain economy result; owners quarantined",failure);
        owners.quarantine();
    }
    private void releaseGates() {
        owners.release();
    }
    @Override public void close() {
        if(closed)return;closed=true;
        move.close();
        if(!submitted)releaseGates();
    }
    static L1ItemInstance copy(L1ItemInstance item) {
        // A prepared new reward may not have its ID yet; grantCopyPrepared
        // reserves one only if a separate row is actually needed.
        L1ItemInstance result = item.CopyDetachedImage(item.getId(), item.getCount(), item.getChargeCount());
        result.setEquipped(item.isEquipped());
        return result;
    }
    private static boolean mergeable(L1ItemInstance a,L1ItemInstance b) {
        return L1ItemInstance.CanMergeStack(a,b);
    }
}
