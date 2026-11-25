package l1j.server.server.model;

import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Warehouse.Warehouse;
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
    private Runnable failureAction;
    private int sqlCount;

    public static L1InventoryTransaction begin(L1PcInstance pc, int resourceKey) {
        return new L1InventoryTransaction(pc, null, resourceKey, false);
    }
    public static L1InventoryTransaction begin(L1PcInstance first, L1PcInstance second) {
        if (second == null || first == second) throw new IllegalArgumentException("Distinct participants required");
        return new L1InventoryTransaction(first, second, second.getId(), false);
    }
    /** All members' item rows commit in one InnoDB receipt or none commit. */
    public static L1InventoryTransaction beginParty(L1PcInstance[] participants) {
        if (participants == null || participants.length < 3 || participants.length > 6)
            throw new IllegalArgumentException("Dungeon party requires 3..6 owners");
        return new L1InventoryTransaction(participants);
    }
    private L1InventoryTransaction(L1PcInstance[] participants) {
        primary = Objects.requireNonNull(participants[0]);
        owners = new InventoryTransactionOwners(primary, participants);
        int second = 0;
        int[] more = new int[participants.length - 2];
        for (int i = 1; i < participants.length; i++) {
            final int id = participants[i].getId();
            if (i == 1) second = id;
            else more[i - 2] = id;
        }
        try {
            move = CacheD.beginEconomyMove(primary.getId(), second, more);
            move.onError(this);
        } catch (RuntimeException | Error failure) {
            owners.release();
            throw failure;
        }
    }

    static L1InventoryTransaction beginTrade(L1PcInstance first, L1PcInstance second) {
        if (second == null || first == second || first.getTradeID() != second.getId()
                || second.getTradeID() != first.getId() || !first.getTradeOk() || !second.getTradeOk())
            throw new IllegalStateException("Unconfirmed trade relation");
        return new L1InventoryTransaction(first, second, second.getId(), true);
    }
    private L1InventoryTransaction(L1PcInstance first, L1PcInstance second, int resourceKey, boolean trade) {
        primary = Objects.requireNonNull(first);
        owners = new InventoryTransactionOwners(first, second, trade);
        try {
            move = CacheD.beginEconomyMove(first.getId(), resourceKey);
            move.onError(this);
        } catch (RuntimeException failure) {
            owners.release();
            throw failure;
        }
    }
    private void open() {
        if (closed || submitted) throw new IllegalStateException("Inventory transaction is closed");
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
        if (pc.getInventory().getItem(item.getId()) != item || item.getCount() <= 0)
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
        Entry e = entry(pc,item);
        if (e.image.getCount() < count) return false;
        e.image.setCount(e.image.getCount()-count);
        return true;
    }
    private static boolean requiresEntityDeletion(int template) {
        return template == 40314 || template == 40316
                || (template >= 49016 && template <= 49025)
                || (template >= 41383 && template <= 41400);
    }
    public boolean debit(L1PcInstance pc,int templateId,int count) {
        open(); owns(pc);
        if (count <= 0 || requiresEntityDeletion(templateId)) return false;
        long available=0;
        for (L1ItemInstance item : pc.getInventory().getItems()) {
            if (item == null || item.isEquipped() || item.getItemId()!=templateId) continue;
            Entry e=findEntry(item.getId());
            if (e == null || e.owner == pc) available += e==null ? item.getCount() : e.image.getCount();
        }
        if (available < count) return false;
        int left=count;
        for (L1ItemInstance item : pc.getInventory().getItems()) {
            if (left==0) break;
            if (item == null || item.isEquipped() || item.getItemId()!=templateId) continue;
            Entry e=findEntry(item.getId());
            if (e!=null && e.owner!=pc) continue;
            int take=Math.min(left,e==null ? item.getCount() : e.image.getCount());
            if (take>0 && !debit(pc,item,take)) throw new IllegalStateException("Prepared debit changed");
            left-=take;
        }
        return left==0;
    }
    public L1ItemInstance grant(int template,int count) { return grant(primary,template,count); }
    public L1ItemInstance grant(L1PcInstance pc,int template,int count) {
        L1Item definition=ItemTable.getInstance().getTemplate(template);
        if (definition==null || count<=0) return null;
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
        if (image == null || image.getCount() <= 0) return null;
        // A grant is a NEW persistent row, never an already published world item.
        // Transferring an owned item must use transfer() with its preimage check.
        if (image.getId() > 0 && L1World.getInstance().findObject(image.getId()) != null)
            throw new IllegalStateException("Reward must be a detached item: " + image.getId());
        L1ItemInstance prepared=copy(image);initializeNewGrant(prepared);
        return grantCopyPrepared(primary,prepared,prepared.getCount(),true);
    }
    /** The existing storeItem charge/fuel/expiry initialization, on a detached
     * NEW reward only. Ownership transfers never reset a purchased lifetime.
     */
    private static void initializeNewGrant(L1ItemInstance item) {
        int itemId=item.getItemId();
        item.setChargeCount(itemId==20383 ? 50 : item.getItem().getMaxChargeCount());
		// 시간제 아이템
		switch (itemId) {
		// 호랑이/진돗개
		case 3000048: // 꼬마 요정의 마음
			newExpiry(item, 60000L, 1439); // 24시간 -1분
			break;
		case l1j.server.server.model.item.L1ItemId.KILLTON_CONTRACT:
		case l1j.server.server.model.item.L1ItemId.MERIN_CONTRACT:
			newExpiry(item, 60000L, 60);
			break;
		case 80500:
			newExpiry(item, 60000L, 120);
			break;
		case 555000:
		case 555001: // 마법인형: 레데그
		case 555002: // 마법인형: 엘레그
		case 555003: // 마법인형: 그레그
			newExpiry(item, 60000L, Config.영웅유물); // 5시간
			break;
		// case l1j.server.server.model.item.L1ItemId.MERIN_CONTRACT:
		// newExpiry(item, 60000L, 60);
		// break;
		case 100036:
		case 500210: // 테베,쿠쿨 제단 열쇠
		case l1j.server.server.model.item.L1ItemId.DRAGON_KEY: // 드래곤키
		case 490012:
		case 490013:
		case 490014:
		case 40312:
			newExpiry(item, 60000L, 180); // 3시간
			break;
		case 30022: // 마법인형: 블레그
		case 30023: // 마법인형: 레데그
		case 30024: // 마법인형: 엘레그
		case 30025: // 마법인형: 그레그
			newExpiry(item, 60000L, 300); // 5시간
			break;
		case 22328: // 베테르랑 방어구
		case 22329:
		case 22330:
		case 22331:
		case 22332:
		case 22333:
		case 22334:
		case 22335:
		case 1126: // 베테르랑 무기
		case 1127:
		case 1128:
		case 1129:
		case 1130:
		case 1131:
		case 1132:
		case 1133:
			newExpiry(item, 60000L, 4320); // 3일
			break;
		case 20282: // 수련자의 방어구
		case 7: // 수련자의 무기
		case 105:
		case 120:
		case 156:
		case 174:
		case 224:
		case 203012:
			newExpiry(item, 60000L, 4320); // 3일
			break;
		case 3000213:// 자동사냥 3일
			newExpiry(item, 60000L, 4320);// 자동사냥 3일
			break;
		case 45453: // 마법인형: 그레그
			newExpiry(item, 60000L, 360); // 5시간
			break;
		case 45451: // 마법인형: 그레그
			newExpiry(item, 60000L, 1440); // 5시간
			break;
		case 45452: // 마법인형: 그레그
			newExpiry(item, 60000L, 4320); // 5시간
			break;
		case 41922:
		case 41923:
		case 41924:
		case 41925:
		case 3000209: // 자동사냥 1일
		case 775:
		case 774:
		case 773:
		case 772:
			newExpiry(item, 60000L, 1440); // 1일
			break;
		case 3000214:// 자동사냥 7일
			newExpiry(item, 60000L, 8640); // 7일
			break;
		case 210095:
		case 2100950:
		case 2100951:
		case 2100952:
		case 2100953:// 쫄사냥
			newExpiry(item, 60000L, 180); // 3일
			break;
		/** 신규 초보자 무기 **/
		case 7000137:
		case 7000138:
		case 7000139:
		case 7000140:
		case 7000141:
		case 7000142:
		case 7000143:
		case 7000144:
		case 505001:
		case 505002:
		case 505003:
		case 505004:
		case 505005:
		case 505006:
		case 505007:
		case 505008:
			newExpiry(item, 20000L, 1440); // 3일
			break;
		default:
			break;
		}

		if (item.getItem().getType2() == 0 && item.getItem().getType() == 2) { // light
			item.setRemainingTime(item.getItem().getLightFuel());
		} else {
			item.setRemainingTime(item.getItem().getMaxUseTime());
		}
		
    }
    private static void newExpiry(L1ItemInstance item,long scale,int minutes) {
        item.setEndTime(new java.sql.Timestamp(Math.addExact(System.currentTimeMillis(),Math.multiplyExact(scale,(long)minutes))));
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
            for (L1ItemInstance item : pc.getInventory().getItems()) {
                if (findEntry(item.getId()) != null || !mergeable(item,prepared)) continue;
                Entry e=entry(pc,item);
                long total=(long)e.image.getCount()+count;
                if (total>CInventory.MAX_AMOUNT) return null;
                e.image.setCount((int)total);
                return e.image;
            }
        }
        if (prepared.getId()<=0 || findEntry(prepared.getId()) != null)
            prepared.setId(IdFactory.getInstance().nextId());
        Entry added=new Entry(pc,prepared,false);
        addEntry(added);
        return added.image;
    }
    public L1ItemInstance transfer(L1PcInstance from,L1ItemInstance item,int count,L1PcInstance to) {
        open(); owns(from); owns(to);
        if (from==to || count<=0 || item==null || item.isEquipped()
                || (!item.isStackable() && count!=1)) throw new IllegalArgumentException("Transfer item/count");
        Entry e=entry(from,item);
        if (e.image.getCount()<count) throw new IllegalStateException("Insufficient transfer quantity");
        if (count==e.image.getCount()) {
            // Preserve the original row ID when no merge is required.
            boolean merge=false;
            if (e.image.isStackable()) {
                for (L1ItemInstance other : to.getInventory().getItems())
                    if (mergeable(other,e.image)) { merge=true;break; }
                for (int index = 0; index < entryCount; index++) {
                    Entry other = entryAt(index);
                    if (other.owner==to && other.image.getCount()>0 && mergeable(other.image,e.image)) {merge=true;break;}
                }
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
    /** Change a weapon/armor enchant level in the very same durable receipt as
     * the consumed scroll. Equipped targets keep their equipment state; the
     * owner applies the corresponding AC/MR effects only after COMMIT.
     */
    public void changeEnchantLevel(L1ItemInstance item, int expectedLevel, int nextLevel) {
        Entry e = entry(primary, item);
        if (e.image.getCount() <= 0 || e.image.getEnchantLevel() != expectedLevel
                || item.getEnchantLevel() != expectedLevel
                || (item.getItem().getType2() != 1 && item.getItem().getType2() != 2)
                || nextLevel != expectedLevel + 1)
            throw new IllegalStateException("Invalid enchant receipt target/state");
        e.image.setEnchantLevel(nextLevel);
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
        Entry e=entry(pc,item);
        if (item.isEquipped()) throw new IllegalArgumentException("Unequip before changing persistent item fields");
        mutation.accept(e.image);
        if (e.image.getId()!=item.getId() || e.image.getItemId()!=item.getItemId()
                || e.image.getCount()<=0 || e.image.isEquipped()) throw new IllegalStateException("Invalid item field change");
    }
    public void sql(CTransaction.Mutation mutation) { open();move.stageWarehouseSql(Objects.requireNonNull(mutation));sqlCount++; }
    public boolean hasChanges() { return sqlCount != 0 || entryCount != 0; }
    public void requireTables(String... tables) {
        String[] names=tables.clone();sql(con->CTransaction.requireTransactionalTables(con,names));
    }
    public void onFailure(Runnable callback) { open();failureAction=callback; }

    public void commit(Runnable afterCommitted) {
        commitInternal(afterCommitted, false);
    }

    /** Committed item effects may change equipment, award items or save progression.
     * Its callback runs only AFTER the durable owner receipt is retired and the
     * participant gate released, still on the existing CIO owner.
     */
    public void commitAfterInventoryRelease(Runnable afterCommitted) {
        commitInternal(afterCommitted, true);
    }

    private void commitInternal(Runnable afterCommitted, boolean releaseBeforeEffects) {
        open();
        validateLive();validateCapacity();
        // Keep singleton receipts entirely inline. Multi-row receipts sort a
        // private view, preserving insertion order if SQL staging throws.
        final int orderedCount = entryCount;
        final Entry singleton = orderedCount == 1 ? (Entry) entries : null;
        final Entry[] ordered = orderedCount <= 1 ? NO_ENTRIES : Arrays.copyOf((Entry[]) entries, orderedCount);
        if (orderedCount > 1) Arrays.sort(ordered, ROW_ID_ORDER);

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
                if (ordered[i].before != null) { hasPreimage = true; break; }
            }
            if (hasPreimage)
                move.stageWarehouseSql(con -> {
                    for (int i = 0; i < orderedCount; i++) {
                        final Entry e = singleton != null ? singleton : ordered[i];
                        if (e.before != null) CTransaction.verifyItemPreimage(con, e.before);
                    }
                });
        }
        for (int i = 0; i < orderedCount; i++) {
            final Entry e = singleton != null ? singleton : ordered[i];
            if (e.image.getCount() == 0) {
                if (e.before != null) move.prepareOwnedDelete(e.originalOwner.getId(), e.image.getId());
            } else move.prepareOwnedWrite(e.owner.getId(), e.image, e.before == null ? 0 : e.originalOwner.getId());
        }
        // Preserve the DB-stage snapshot and the detached replay image barrier.
        for (int i = 0; i < orderedCount; i++) {
            final Entry e = singleton != null ? singleton : ordered[i];
            e.image = copy(e.image);
        }
        move.commit(()->post(()->{
            validateLive();
            CacheD.applyCommittedWarehouseMove(move,()->{
                for (int i = 0; i < orderedCount; i++) {
                    final Entry e = singleton != null ? singleton : ordered[i];
                    if (e.original != null && (e.owner != e.originalOwner || e.image.getCount() == 0))
                        e.originalOwner.getInventory().applyEconomyImage(e.original, null);
                }
                for (int i = 0; i < orderedCount; i++) {
                    final Entry e = singleton != null ? singleton : ordered[i];
                    if (e.image.getCount() > 0)
                        e.owner.getInventory().applyEconomyImage(e.originalOwner == e.owner ? e.original : null, e.image);
                }
            });
            if (releaseBeforeEffects) {
                releaseGates();
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
        },failure->{
            move.finishOwnerApply(failure);
            if(failure!=null) quarantine(failure);
            else if(releaseBeforeEffects && afterCommitted!=null) {
                // On success this completion executes on the same CIO owner,
                // AFTER CacheD has released its pending owner-apply barrier.
                // Otherwise onChangeExp()'s character save would be dropped.
                try {
                    if(!owners.current(primary))
                        throw new IllegalStateException("Stale inventory effect session");
                    synchronized(owners.monitor(primary)) { afterCommitted.run(); }
                } catch(Throwable effectFailure) {
                    quarantine(effectFailure); // Never retry a committed debit as if it rolled back.
                }
            }
        }));
        submitted=true;
    }
    private void validateLive() {
        for(int i=0;i<owners.size();i++) {
            L1PcInstance pc=owners.get(i);
            if (!owners.current(pc)) throw new IllegalStateException("Stale economy session");
        }
        for (int i = 0; i < entryCount; i++) {
            Entry e = entryAt(i);
            if (e.before == null) continue;
            if(e.originalOwner.getInventory().getItem(e.original.getId())!=e.original
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
            for (int i=0;i<owners.size();i++) {
                if (!owners.current(owners.get(i)))
                    throw new IllegalStateException("Stale economy completion participant");
            }
            if (owners.size() == 1) {
                synchronized (owners.monitor(coordinator)) { action.run(); }
            } else if (owners.size() == 2) {
                synchronized (owners.monitor(owners.get(0))) {
                    synchronized (owners.monitor(owners.get(1))) { action.run(); }
                }
            } else withOwnersLocked(0, action);
        },done);
    }
    private void withOwnersLocked(int index, Runnable action) {
        if (index == owners.size()) { action.run(); return; }
        synchronized (owners.monitor(owners.get(index))) {
            withOwnersLocked(index + 1, action);
        }
    }

    @Override public void accept(Throwable failure) { sqlFailed(failure); }

    private void sqlFailed(Throwable failure) {
        if(unknown(failure)) {post(()->quarantine(failure),ignored->{});return;}
        // Rollback was confirmed. No live item changed, so there is nothing to
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
        L1ItemInstance result=Warehouse.transferCopy(item,item.getId(),Math.max(1,item.getCount()),item.getChargeCount());
        result.setCount(item.getCount());result.setEquipped(item.isEquipped());return result;
    }
    private static boolean mergeable(L1ItemInstance a,L1ItemInstance b) {
        return a.isStackable() && b.isStackable() && a.getItemId()==b.getItemId()
                && a.getEnchantLevel()==b.getEnchantLevel() && a.getBless()==b.getBless()
                && a.getAttrEnchantLevel()==b.getAttrEnchantLevel() && a.getSpecialEnchant()==b.getSpecialEnchant()
                && a.getChargeCount()==b.getChargeCount() && a.getRemainingTime()==b.getRemainingTime()
                && a.get_bless_level()==b.get_bless_level() && a.get_bless_level2()==b.get_bless_level2()
                && a.get_item_level()==b.get_item_level() && a.isPackage()==b.isPackage()
                && Objects.equals(a.getEndTime(),b.getEndTime()) && Objects.equals(a.getLastUsed(),b.getLastUsed())
                && Objects.equals(a.getHotel_Town(),b.getHotel_Town());
    }
}
