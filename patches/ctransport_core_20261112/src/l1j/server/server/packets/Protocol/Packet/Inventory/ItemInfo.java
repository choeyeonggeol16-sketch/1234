package l1j.server.server.packets.Protocol.Packet.Inventory;

import l1j.server.MJCompanion.Basic.ClassInfo.MJCompanionClassInfo;
import l1j.server.MJCompanion.Instance.MJCompanionInstanceCache;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.templates.L1Item;

/**
 * 인벤토리 아이템 정보 서브메시지 — ItemInfo 와이어 계약 동일.
 * SC_ADD/UPDATE_INVENTORY_NOTI가 msg(1)/end()로 감싸 직렬화.
 * null = 미설정 = 미전송 (has-bit 동작 재현).
 */
public final class ItemInfo {

    private Integer objectId;               // 1  uint32
    private Integer nameId;                 // 2  int32
    private Integer dbId;                   // 3  int32
    private Integer count;                  // 4  int32
    private Integer interactType;           // 5  int32
    private Integer numberOfUse;            // 6  int32
    private Integer iconId;                 // 7  int32
    private Integer blessCodeForDisplay;    // 8  int32
    private Integer attributeBitSet;        // 9  int32
    private Long attributeBitSetEx;         // 10 uint64
    private Boolean isTimeout;              // 11 bool
    private Integer category;               // 12 int32
    private Integer enchant;                // 13 int32
    private Integer deposit;                // 14 uint32
    private Integer overlaySurfId;          // 15 int32
    private Integer elementalEnchantType;   // 16 int32
    private Integer elementalEnchantValue;  // 17 int32
    private byte[] description;             // 18 bytes (MS949 선인코딩)
    private byte[] extraDescription;        // 19 bytes (MS949 선인코딩)
    private Integer leftTimeForPreNotify;   // 20 int32
    private CompanionCard companionCard;    // 21 message
    private Integer blessCode;              // 22 int32
    private Integer realEnchant;            // 23 int32
    private Boolean isMerging;              // 24 bool
    private Integer weight;                 // 25 int32
    private Boolean isIdentified;           // 26 bool

    private ItemInfo() {}

    private static byte[] ms949(String s) {
        try { return s.getBytes("MS949"); }
        catch (java.io.UnsupportedEncodingException e) { throw new RuntimeException(e); }
    }

    // ── 팩토리: MJ 로직 그대로 이식 ──

    public static ItemInfo newInstance(L1ItemInstance item, int useType, byte[] viewName, int bless) {
        L1Item itemT = item.getItem();
        ItemInfo i = new ItemInfo();
        i.objectId = item.getId();
        i.dbId = item.getId();   // MJ 원본 퀘크 보존: db_id = object id
        i.nameId = itemT.getItemDescId();
        i.iconId = item.get_gfxid();
        i.blessCodeForDisplay = bless;

        int bit = item.isIdentified() ? 1 : 0;
        if (item.getBless() == 129) {
            bit = 2 | 4 | 8;
        } else {
            if (!itemT.isTradable())   bit |= 2;   // 교환불가
            if (itemT.isCantDelete())  bit |= 4;   // 삭제불가
            if (itemT.get_safeenchant() < 0) bit |= 8; // 강화불가
        }
        i.attributeBitSet = bit;

        if (itemT.getType2() == 1) {
            i.elementalEnchantType  = L1ItemInstance.attrEnchantToElementalType(item.getAttrEnchantLevel());
            i.elementalEnchantValue = L1ItemInstance.pureAttrEnchantLevel(item.getAttrEnchantLevel());
        }

        int category = 0;
        switch (itemT.getType2()) {
        case 0: category = 23; break;
        case 1: category = 1;  break;
        case 2: category = 19; break;
        }
        i.category = category;
        i.enchant      = item.getEnchantLevel();
        i.realEnchant  = item.getEnchantLevel();
        i.interactType = useType;
        i.count        = item.getCount();

        if (itemT.isEndedTimeMessage()) {
            i.attributeBitSetEx = 0x01L;
        } else {
            switch (itemT.getItemId()) {
            case 41297: case 41296: case 41301: case 41304: case 41303:
            case 600230: case 820018:
            case 49092: case 49093: case 49094: case 49095:
                i.attributeBitSetEx = 0x08L;
                break;
            default: break;
            }
        }

        i.isTimeout     = true;
        i.deposit       = itemT.isTradable() ? 7 : 0;
        i.weight        = item.getWeight();
        i.isIdentified  = item.isIdentified();
        i.numberOfUse   = 0;
        i.isMerging     = false;
        i.description   = viewName;
        if (item.isIdentified()) {
            if (itemT.getType2() == 0 && itemT.getItemId() >= 875640331 && itemT.getItemId() <= 875640341) {
                i.extraDescription = item.getStatusBytes2();
            } else if (itemT.getType2() == 0 && itemT.getItemId() >= 875640446 && itemT.getItemId() <= 875640448) {
                i.extraDescription = item.getStatusBytes2();
            } else {
                i.extraDescription = item.getStatusBytes();
            }
        }

        MJCompanionClassInfo cInfo = MJCompanionClassInfo.from_item_id(item.getItemId());
        if (cInfo == null) return i;

        CompanionCard card = CompanionCard.newInstance();
        MJCompanionInstanceCache cache = MJCompanionInstanceCache.get(item.getId());
        if (cache != null) {
            card.set_class_id(cInfo.get_class_id());
            card.set_is_dead(cache.get_is_dead());
            card.set_is_oblivion(cache.get_is_oblivion());
            card.set_is_summoned(cache.get_is_summoned());
            card.set_level(cache.get_level());
            card.set_name(cache.get_name());
            card.set_elixir_use_count(cache.get_elixir_use_count());
            i.description = ms949(String.format("펫 목걸이 (%s LV %d)", cInfo.get_class_npc_name_id(), cache.get_level()));
        } else {
            card.set_class_id(cInfo.get_class_id());
            card.set_is_dead(false);
            card.set_is_oblivion(true);
            card.set_is_summoned(false);
            card.set_level(1);
            card.set_name(cInfo.get_class_npc_name_id());
            card.set_elixir_use_count(0);
            i.description = ms949(String.format("펫 목걸이 (%s LV 1)", cInfo.get_class_npc_name_id()));
        }
        i.companionCard = card;
        return i;
    }

    public static ItemInfo newInstance(L1ItemInstance item, int useType) {
        return newInstance(item, useType, ms949(item.getViewName()), item.getBless());
    }

    public static ItemInfo newInstance(L1ItemInstance item) {
        return newInstance(item, item.getItem().getUseType());
    }

    public static ItemInfo newInstance(int itemObjectId, MJCompanionInstanceCache cache) {
        ItemInfo i = new ItemInfo();
        i.objectId = itemObjectId;
        CompanionCard card = CompanionCard.newInstance();
        card.set_is_summoned(cache.get_is_summoned());
        card.set_is_dead(cache.get_is_dead());
        card.set_is_oblivion(cache.get_is_oblivion());
        card.set_level(cache.get_level());
        card.set_name(cache.get_name());
        card.set_elixir_use_count(cache.get_elixir_use_count());
        i.companionCard = card;
        return i;
    }

    // ── 직렬화 ──

    public void body(ProtoWriter w) {
        if (objectId != null)               w.uint32(1, objectId);
        if (nameId != null)                 w.int32(2, nameId);
        if (dbId != null)                   w.int32(3, dbId);
        if (count != null)                  w.int32(4, count);
        if (interactType != null)           w.int32(5, interactType);
        if (numberOfUse != null)            w.int32(6, numberOfUse);
        if (iconId != null)                 w.int32(7, iconId);
        if (blessCodeForDisplay != null)    w.int32(8, blessCodeForDisplay);
        if (attributeBitSet != null)        w.int32(9, attributeBitSet);
        if (attributeBitSetEx != null)      w.uint64(10, attributeBitSetEx);
        if (isTimeout != null)              w.bool(11, isTimeout);
        if (category != null)               w.int32(12, category);
        if (enchant != null)                w.int32(13, enchant);
        if (deposit != null)                w.uint32(14, deposit);
        if (overlaySurfId != null)          w.int32(15, overlaySurfId);
        if (elementalEnchantType != null)   w.int32(16, elementalEnchantType);
        if (elementalEnchantValue != null)  w.int32(17, elementalEnchantValue);
        if (description != null)            w.bytes(18, description);
        if (extraDescription != null) {  
        	   w.bytes(19, extraDescription); 
//        	  System.out.println("[ITEMINFO] itemId=" + (nameId != null ? nameId : -1)
//        		        + " extraLen=" + extraDescription.length
//        		        + " first16=" + hexHead(extraDescription, 16));
        }
        if (leftTimeForPreNotify != null)   w.int32(20, leftTimeForPreNotify);
        if (companionCard != null) {
            w.msg(21);
            companionCard.body(w);
            w.end();
        }
        if (blessCode != null)              w.int32(22, blessCode);
        if (realEnchant != null)            w.int32(23, realEnchant);
        if (isMerging != null)              w.bool(24, isMerging);
        if (weight != null)                 w.int32(25, weight);
        if (isIdentified != null)           w.bool(26, isIdentified);
    }
    
    private static String hexHead(byte[] b, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(b.length, n); i++)
            sb.append(String.format("%02X ", b[i]));
        return sb.toString();
    }

    // ── 중첩 서브메시지 ──

    public static final class CompanionCard {
        private Boolean isOblivion;      // 1 bool
        private Boolean isDead;          // 2 bool
        private Boolean isSummoned;      // 3 bool
        private Integer classId;         // 4 uint32
        private Integer level;           // 5 uint32
        private String name;             // 6 string
        private Integer elixirUseCount;  // 7 uint32

        private CompanionCard() {}
        static CompanionCard newInstance() { return new CompanionCard(); }

        CompanionCard set_is_oblivion(boolean v)    { isOblivion = v; return this; }
        CompanionCard set_is_dead(boolean v)        { isDead = v; return this; }
        CompanionCard set_is_summoned(boolean v)    { isSummoned = v; return this; }
        CompanionCard set_class_id(int v)           { classId = v; return this; }
        CompanionCard set_level(int v)              { level = v; return this; }
        CompanionCard set_name(String v)            { name = v; return this; }
        CompanionCard set_elixir_use_count(int v)   { elixirUseCount = v; return this; }

        void body(ProtoWriter w) {
            if (isOblivion != null)     w.bool(1, isOblivion);
            if (isDead != null)         w.bool(2, isDead);
            if (isSummoned != null)     w.bool(3, isSummoned);
            if (classId != null)        w.uint32(4, classId);
            if (level != null)          w.uint32(5, level);
            if (name != null)           w.string(6, name);
            if (elixirUseCount != null) w.uint32(7, elixirUseCount);
        }
    }
    
    public ItemInfo set_extra_description(byte[] v) { extraDescription = v; return this; }
    
    /** 서브메시지 본문 선인코딩 (청크 전송/길이 산출용) */
    public byte[] encodeBody() {
        ProtoWriter w = ProtoWriter.newInstance(); 
        body(w);
        return w.bodyBytes();
    }

    /** field1 태그 포함 전체 와이어 크기: tag(1) + lenVarint + body */
    public static int encodedWireSize(byte[] encodedBody) {
        return 1 + ProtoWriter.varintLenOf(encodedBody.length) + encodedBody.length;
    }
    
    /** 외부 보정용 */
    public ItemInfo set_icon_id(int v) { iconId = v; return this; }

    /** Live item projection: no ItemInfo, CompanionCard or boxed numeric fields. */
    public static void writeItem(ProtoWriter writer, L1ItemInstance item) {
        writeOrSendItem(writer, null, item, false, 0, true);
    }

    public static byte[] encodeItemBody(L1ItemInstance item) {
        ProtoWriter writer = ProtoWriter.tl();
        writeItem(writer, item);
        return writer.bodyBytes();
    }

    public static boolean sendAddedItem(l1j.server.server.model.Instance.L1PcInstance recipient,
            L1ItemInstance item, boolean onStart, int ownerOid) {
        return sendAddedItem(recipient == null ? null : recipient.getNetConnection(), item, onStart, ownerOid);
    }

    public static boolean sendAddedItem(l1j.server.server.model.network.core.session.CIOClient recipient,
            L1ItemInstance item, boolean onStart, int ownerOid) {
        return writeOrSendItem(null, recipient, item, onStart, ownerOid, true);
    }

    private static boolean writeOrSendItem(ProtoWriter supplied,
            l1j.server.server.model.network.core.session.CIOClient recipient, L1ItemInstance item,
            boolean onStart, int ownerOid, boolean includeExtra) {
        // Take the same projection as newInstance(), before acquiring a SEND lease.
        int useType = item.getItem().getUseType();
        byte[] descriptionBytes = ms949(item.getViewName());
        int bless = item.getBless();
        L1Item template = item.getItem();
        int object = item.getId();
        int database = item.getId();
        int name = template.getItemDescId();
        int icon = item.get_gfxid();
        int attributes = item.isIdentified() ? 1 : 0;
        if (item.getBless() == 129) {
            attributes = 2 | 4 | 8;
        } else {
            if (!template.isTradable()) attributes |= 2;
            if (template.isCantDelete()) attributes |= 4;
            if (template.get_safeenchant() < 0) attributes |= 8;
        }
        boolean elemental = template.getType2() == 1;
        int elementType = elemental ? L1ItemInstance.attrEnchantToElementalType(item.getAttrEnchantLevel()) : 0;
        int elementValue = elemental ? L1ItemInstance.pureAttrEnchantLevel(item.getAttrEnchantLevel()) : 0;
        int itemCategory = switch (template.getType2()) {
            case 0 -> 23;
            case 1 -> 1;
            case 2 -> 19;
            default -> 0;
        };
        int enchantValue = item.getEnchantLevel();
        int realEnchantValue = item.getEnchantLevel();
        int itemCount = item.getCount();
        long extendedAttributes = 0;
        if (template.isEndedTimeMessage()) {
            extendedAttributes = 0x01L;
        } else {
            extendedAttributes = switch (template.getItemId()) {
                case 41297, 41296, 41301, 41304, 41303, 600230, 820018,
                     49092, 49093, 49094, 49095 -> 0x08L;
                default -> 0L;
            };
        }
        int itemDeposit = template.isTradable() ? 7 : 0;
        int itemWeight = item.getWeight();
        boolean identified = item.isIdentified();
        byte[] extra = null;
        if (item.isIdentified()) {
            if (template.getType2() == 0 && template.getItemId() >= 875640331 && template.getItemId() <= 875640341) {
                extra = item.getStatusBytes2();
            } else if (template.getType2() == 0 && template.getItemId() >= 875640446 && template.getItemId() <= 875640448) {
                extra = item.getStatusBytes2();
            } else {
                extra = item.getStatusBytes();
            }
        }
        MJCompanionClassInfo companion = MJCompanionClassInfo.from_item_id(item.getItemId());
        boolean oblivion = false, dead = false, summoned = false;
        int companionClass = 0, companionLevel = 0, elixirs = 0;
        String companionName = null;
        if (companion != null) {
            MJCompanionInstanceCache cache = MJCompanionInstanceCache.get(item.getId());
            companionClass = companion.get_class_id();
            if (cache != null) {
                dead = cache.get_is_dead();
                oblivion = cache.get_is_oblivion();
                summoned = cache.get_is_summoned();
                companionLevel = cache.get_level();
                companionName = cache.get_name();
                elixirs = cache.get_elixir_use_count();
                descriptionBytes = ms949(String.format("펫 목걸이 (%s LV %d)",
                        companion.get_class_npc_name_id(), cache.get_level()));
            } else {
                oblivion = true;
                companionLevel = 1;
                companionName = companion.get_class_npc_name_id();
                descriptionBytes = ms949(String.format("펫 목걸이 (%s LV 1)", companionName));
            }
        }
        boolean ownsFrame = supplied == null;
        ProtoWriter writer = supplied;
        if (ownsFrame) {
            long capacity = 512L + (descriptionBytes == null ? 0 : descriptionBytes.length)
                    + (extra == null ? 0 : extra.length)
                    + (companionName == null ? 0L : 2L * companionName.length());
            writer = l1j.server.server.packets.Protocol.Proto.begin(recipient,
                    l1j.server.server.packets.Protocol.ProtoMsg.SC_ADD_INVENTORY_NOTI,
                    l1j.server.server.packets.Protocol.Proto.capacityBound(capacity));
            if (writer == null) return false;
        }
        try {
            if (ownsFrame) writer.msg(1);
            writer.uint32(1, object).int32(2, name).int32(3, database).int32(4, itemCount)
                    .int32(5, useType).int32(6, 0).int32(7, icon).int32(8, bless)
                    .int32(9, attributes);
            if (extendedAttributes != 0) writer.uint64(10, extendedAttributes);
            writer.bool(11, true).int32(12, itemCategory).int32(13, enchantValue).uint32(14, itemDeposit);
            if (elemental) writer.int32(16, elementType).int32(17, elementValue);
            if (descriptionBytes != null) writer.bytes(18, descriptionBytes);
            if (includeExtra && extra != null) writer.bytes(19, extra);
            if (companion != null) {
                writer.msg(21).bool(1, oblivion).bool(2, dead).bool(3, summoned)
                        .uint32(4, companionClass).uint32(5, companionLevel);
                if (companionName != null) writer.string(6, companionName);
                writer.uint32(7, elixirs).end();
            }
            writer.int32(23, realEnchantValue).bool(24, false).int32(25, itemWeight).bool(26, identified);
            if (!ownsFrame) return true;
            writer.end().bool(2, onStart).uint32(3, ownerOid);
            return l1j.server.server.packets.Protocol.Proto.commit(writer);
        } finally {
            if (ownsFrame) l1j.server.server.packets.Protocol.Proto.abort(writer);
        }
    }

    /** Warehouse keeps its existing field-19 omission and length-measured snapshot. */
    public static byte[] encodeWarehouseBody(L1ItemInstance item) {
        ProtoWriter writer = ProtoWriter.tl();
        writer.int32(1, item.getId()).msg(2);
        writeOrSendItem(writer, null, item, false, 0, false);
        writer.end();
        return writer.bodyBytes();
    }
}
