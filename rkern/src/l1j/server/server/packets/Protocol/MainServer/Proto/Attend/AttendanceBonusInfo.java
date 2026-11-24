package l1j.server.server.packets.Protocol.MainServer.Proto.Attend;

import l1j.server.server.datatables.CObjectDB;
import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;
import l1j.server.server.templates.L1Item;

/**
 * 출석 보상 아이템 정보 (0x0224의 반복 요소 내부)
 *  1 bonus_type enum, 2 item_id, 3 item_count, 4 item_name(string),
 *  5 item_interact_type, 6 item_icon, 7 item_bless, 8 item_desc(string),
 *  9 item_extra_desc(bytes), 10 item_attr — 전부 required
 */
public class AttendanceBonusInfo {

    public static AttendanceBonusInfo newInstance(ResultSet rs) throws SQLException{
        int itemId = rs.getInt("item_id");
        int count = rs.getInt("count");
        L1Item item = CObjectDB.GetItem(itemId);
        if(item == null){
            System.out.println(String.format("아이템 아이디를 찾을 수 없습니다. [%d]", itemId));
            return null;
        }

        AttendanceBonusInfo bInfo = new AttendanceBonusInfo();
        bInfo._bonusType = AttendanceBonusType.GiveItem;
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

    private AttendanceBonusType _bonusType;
    private int _itemId;
    private int _itemCount;
    private String _itemName;
    private int _itemInteractType;
    private int _itemIcon;
    private int _itemBless;
    private String _itemDesc;
    private byte[] _itemExtraDesc;
    private int _itemAttr;

    public AttendanceBonusType get_bonusType(){ return _bonusType; }
    public int get_itemId(){ return _itemId; }
    public int get_itemCount(){ return _itemCount; }
    public String get_itemName(){ return _itemName; }
    public int get_itemInteractType(){ return _itemInteractType; }
    public int get_itemIcon(){ return _itemIcon; }
    public int get_itemBless(){ return _itemBless; }
    public String get_itemDesc(){ return _itemDesc; }
    public byte[] get_itemExtraDesc(){ return _itemExtraDesc; }
    public int get_itemAttr(){ return _itemAttr; }

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