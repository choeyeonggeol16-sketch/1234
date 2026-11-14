package LIN;

import java.sql.ResultSet;
import java.sql.SQLException;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.templates.L1Item;



/**
 * 출석 보상 아이템 정보 (0x0224의 반복 요소 내부)
 *  1 bonus_type enum, 2 item_id, 3 item_count, 4 item_name(string),
 *  5 item_interact_type, 6 item_icon, 7 item_bless, 8 item_desc(string),
 *  9 item_extra_desc(bytes), 10 item_attr — 전부 required
 */
public class AttendanceBonusInfo {

    public static LIN.AttendanceBonusInfo newInstance(ResultSet rs) throws SQLException{
        int itemId = rs.getInt("item_id");
        int count = rs.getInt("count");
        L1Item item = ItemTable.getInstance().getTemplate(itemId);
        if(item == null){
            System.out.println(String.format("아이템 아이디를 찾을 수 없습니다. [%d]", itemId));
            return null;
        }

        LIN.AttendanceBonusInfo bInfo = new LIN.AttendanceBonusInfo();
        bInfo._bonusType = LIN.AttendanceBonusType.GiveItem;
        bInfo._itemId = item.getItemId();
        bInfo._itemCount = count;
        bInfo._itemName = item.getNameId();
        bInfo._itemInteractType = 0x00;
        bInfo._itemIcon = item.getGfxId();
        bInfo._itemBless = item.getBless();
        bInfo._itemDesc = item.getNameId();

        L1ItemInstance inst = new L1ItemInstance();
        inst.setItem(item);
        bInfo._itemExtraDesc = inst.getStatusBytes();
        bInfo._itemAttr = 0x17;
        return bInfo;
    }

    private LIN.AttendanceBonusType _bonusType;
    private int _itemId;
    private int _itemCount;
    private String _itemName;
    private int _itemInteractType;
    private int _itemIcon;
    private int _itemBless;
    private String _itemDesc;
    private byte[] _itemExtraDesc;
    private int _itemAttr;

    public LIN.AttendanceBonusType bonusType(){ return _bonusType; }
    public int itemId(){ return _itemId; }
    public int itemCount(){ return _itemCount; }
    public String itemName(){ return _itemName; }
    public int itemInteractType(){ return _itemInteractType; }
    public int itemIcon(){ return _itemIcon; }
    public int itemBless(){ return _itemBless; }
    public String itemDesc(){ return _itemDesc; }
    public byte[] itemExtraDesc(){ return _itemExtraDesc; }
    public int itemAttr(){ return _itemAttr; }

    /** 신규 직렬화 — 전 필드 required, 항상 방출 */
    public void writeTo(ProtoWriter w){
        w.int32(1, _bonusType.toInt())
         .int32(2, _itemId)
         .int32(3, _itemCount)
         .string(4, _itemName)
         .int32(5, _itemInteractType)
         .int32(6, _itemIcon)
         .int32(7, _itemBless)
         .string(8, _itemDesc)
         .bytes(9, _itemExtraDesc)
         .int32(10, _itemAttr);
    }
}