package l1j.server.server.packets.Protocol.MainServer.Proto.Craft;

import java.util.LinkedList;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.inventory.CInventory;

/**
 * 유저가 제작 요청 시 보낸 재료 슬롯 정보 (CS_CRAFT_MAKE_REQ repeated slotInfo 항목).
 */
public class CraftInputItemSlotInfo {

    private int _input_slot_no;
    private int _item_name_id;
    private int _count;
    private int _enchant;
    private int _elemental_enchant_type;
    private int _elemental_enchant_value;
    private int _bless;
    private L1ItemInstance consumedItem;

    private LinkedList<TemporaryRemovedItemInstance> _temporaryRemovedItems;
    private int _temporaryRemovedCount;

    public int get_input_slot_no()            { return _input_slot_no; }
    public void set_input_slot_no(int val)    { _input_slot_no = val; }
    public int get_item_name_id()             { return _item_name_id; }
    public void set_item_name_id(int val)     { _item_name_id = val; }
    public int get_count()                    { return _count; }
    public void set_count(int val)            { _count = val; }
    public int get_enchant()                  { return _enchant; }
    public void set_enchant(int val)          { _enchant = val; }
    public int get_elemental_enchant_type()   { return _elemental_enchant_type; }
    public void set_elemental_enchant_type(int val)  { _elemental_enchant_type = val; }
    public int get_elemental_enchant_value()  { return _elemental_enchant_value; }
    public void set_elemental_enchant_value(int val) { _elemental_enchant_value = val; }
    public int get_bless()                    { return _bless; }
    public void set_bless(int val)            { _bless = val; }

    // [7번-룸티스] 룸티스 귀걸이 8종은 축복 여부와 무관하게 제작 재료로 인정
    private static boolean is룸티스귀걸이(int itemId){
        return (itemId >= 22229 && itemId <= 22231)     // 붉은/푸른/보랏빛(일반)
                || (itemId >= 222337 && itemId <= 222339) // 붉은/푸른/보랏빛(축복)
                || itemId == 222340 || itemId == 222341;  // 검은빛(일반/축복)
    }

    /** 인벤 아이템이 이 슬롯의 요구사항과 일치하는지 — server 원본 그대로 */
    public boolean isInput(L1ItemInstance item){
        if(_item_name_id != item.getItem().getItemDescId())
            return false;

        if(_bless != 3 && _bless != item.getBless()
                && !is룸티스귀걸이(item.getItemId())) // [7번] 룸티스는 축복검사 우회
            return false;

        if(_enchant != item.getEnchantLevel())
            return false;

        if(_elemental_enchant_type >= 1 && _elemental_enchant_type <= 4)
            return L1ItemInstance.equalsElement(item, _elemental_enchant_type, _elemental_enchant_value);
        return true;
    }

    public void addTemporaryRemovedItems(L1ItemInstance item, int count){
        if(_temporaryRemovedItems == null)
            _temporaryRemovedItems = new LinkedList<TemporaryRemovedItemInstance>();
        _temporaryRemovedItems.add(new TemporaryRemovedItemInstance(item, count));
        _temporaryRemovedCount += count;
    }

    public boolean hasRemoved(){
        return _temporaryRemovedCount >= _count;
    }

    public void doRemoved(CInventory inv){
        for(TemporaryRemovedItemInstance removed : _temporaryRemovedItems){
            if(_temporaryRemovedCount <= 0)
                break;

            int cnt = removed.item.getCount();
            if(removed.item.isStackable()){
                if(cnt > removed.count)
                    cnt = removed.count;
            }
            inv.DeleteItemAmount(removed.item, cnt);
            _temporaryRemovedCount -= cnt;
        }
        clearRemoved();
    }

    public void clearRemoved(){
        if(_temporaryRemovedItems != null)
            _temporaryRemovedItems.clear();
        _temporaryRemovedCount = 0;
    }

    boolean validAttributes() {
        return _elemental_enchant_type == 0 && _elemental_enchant_value == 0
                || L1ItemInstance.calculateElementalEnchant(_elemental_enchant_type, _elemental_enchant_value) != 0;
    }

    /** Match submitted selection fields without altering them while trying alternatives. */
    public boolean validation(CraftInputItem input) {
        if (input.get_nameId() != _item_name_id
                || input.get_bless() != 3 && input.get_bless() != _bless
                || !input.get_allEnchantsAllowed() && input.get_enchant() != _enchant) return false;
        final int type = input.get_elementalEnchantType();
        return type < 1 || type > 4
                || type == _elemental_enchant_type && input.get_elementalEnchantValue() == _elemental_enchant_value;
    }

    void applyDefinition(CraftInputItem input) {
        _count = input.get_count();
        _bless = input.get_bless();
    }

    public void resetConsumedItem() { consumedItem = null; }

    public void recordConsumption(L1ItemInstance item) {
        if (consumedItem == null) consumedItem = item;
    }

    public L1ItemInstance consumedItem() { return consumedItem; }

    public static class TemporaryRemovedItemInstance {
        public final L1ItemInstance item;
        public final int count;
        public TemporaryRemovedItemInstance(L1ItemInstance item, int count){
            this.item = item;
            this.count = count;
        }
    }
}