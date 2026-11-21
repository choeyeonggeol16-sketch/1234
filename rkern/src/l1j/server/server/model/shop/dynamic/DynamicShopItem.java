package l1j.server.server.model.shop.dynamic;

import java.sql.ResultSet;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.npc.shop.NpcShopManager;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1ShopItem;

/** 
 * DynamicShopItem
 * Dynamic Shop Item.
 * made by mjsoft, 2016.
 *  **/
/** 샵 아이템에 대한 정보를 가진다. **/
public class DynamicShopItem {
	public int 		objId;		// 오브젝트 ID
	public int 		itemId;		// 아이템 ID
	public String	name;
	public int 		invId;		// 인벤토리 ID
	public int 		count;		// 수량
	public int 		price;		// 가격
	public int		iden;		// 아이템의 상태
	public int 		enchant;	// 인챈트 수치
	public int 		attr;		// 속성
	public boolean 	isPurchase;	// 판매(false), 매입(true)
	
	public static DynamicShopItem create(NpcShopManager.ShopEntry e, int objId){
	    L1Item item = ItemTable.getInstance().getTemplate(e.itemId);
	    if(item == null){
	        // ★ 가격표에 등록된 아이템이 실제 템플릿에 없음 — NPE 대신 null 반환으로 안전하게
	        return null;
	    }
	    DynamicShopItem ditem = new DynamicShopItem();
	    ditem.objId		= objId;
	    ditem.itemId	= e.itemId;
	    ditem.name		= item.getName();
	    ditem.invId		= item.getGfxId();
	    ditem.count		= (int) e.count;   // long → int (기존 필드가 int라)
	    ditem.price		= (int) e.price;   // long → int
	    ditem.iden		= item.getBless();
	    ditem.enchant	= e.enchant;
	    ditem.attr		= 0;
	    ditem.isPurchase	= e.purchase;
	    return ditem;
	}
	
	public static DynamicShopItem create(L1ShopItem sitem, int i, boolean isPurchase){
		L1Item item			= ItemTable.getInstance().getTemplate(sitem.getItemId());
		DynamicShopItem ditem	= new DynamicShopItem();
		ditem.objId			= i;
		ditem.itemId		= sitem.getItemId();
		ditem.name			= item.getName();
		ditem.invId			= item.getGfxId();
		ditem.count			= sitem.get_count();
		ditem.price			= sitem.getPrice();
		ditem.iden			= item.getBless();
		ditem.enchant		= sitem.getEnchant();
		ditem.attr			= 0;
		ditem.isPurchase	= isPurchase;
		return ditem;
	}
	
	public static DynamicShopItem create(L1ItemInstance item, int count, int price, boolean isPurchase){
		DynamicShopItem ditem	= new DynamicShopItem();
		ditem.objId			= item.getId();
		ditem.itemId		= item.getItemId();
		ditem.name			= item.getName();
		ditem.invId			= item.get_gfxid();
		ditem.count			= count;
		ditem.price			= price;
		if(!item.isIdentified())	ditem.iden	= -1;
		else						ditem.iden	= item.getBless();
		ditem.enchant		= item.getEnchantLevel();
		ditem.attr			= item.getAttrEnchantLevel();
		ditem.isPurchase	= isPurchase;
		return ditem;
	}
	
	public static DynamicShopItem create(ResultSet rs) throws Exception{
		DynamicShopItem ditem	= new DynamicShopItem();
		ditem.objId 	= rs.getInt("item_objid");
		ditem.itemId	= rs.getInt("item_id");
		ditem.name		= rs.getString("Item_name");
		ditem.invId		= rs.getInt("invgfx");
		ditem.count		= rs.getInt("count");
		ditem.price		= rs.getInt("price");
		ditem.iden		= DynamicShopStorage.getAppIden2PackIden(rs.getInt("iden"));
		ditem.enchant	= rs.getInt("enchant");
		ditem.attr		= rs.getInt("attr");
		ditem.isPurchase= rs.getBoolean("type");
		return ditem;
	}
}
