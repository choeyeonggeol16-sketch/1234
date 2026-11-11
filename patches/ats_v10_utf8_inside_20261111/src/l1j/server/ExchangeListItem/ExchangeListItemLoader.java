package l1j.server.ExchangeListItem;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.Template.LORnd;
import l1j.server.Template.ArrangeHelper.MJArrangeParseeFactory;
import l1j.server.Template.ArrangeHelper.MJArrangeParser;
import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;
import l1j.server.Templates.L1Item;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.ServerBasePacket;

public class ExchangeListItemLoader {
	private static ExchangeListItemLoader _instance;
	public static ExchangeListItemLoader getInstance() {
		if(_instance == null)
			_instance = new ExchangeListItemLoader();
		return _instance;
	}
	public static void reload() {
		if(_instance != null) {
			_instance = new ExchangeListItemLoader();
		}
	}
	
	
	private HashMap<Integer, ExchangeListItemInfo> _exchange_item;
	private ExchangeListItemLoader() {
		load();
	}
	
	private void load() {
		final HashMap<Integer, ExchangeListItemInfo> Exchange = new HashMap<Integer, ExchangeListItemInfo>(256);
		Selector.exec("select * from exchange_list_items", new FullSelectorHandler() {
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()) {
					ExchangeListItemInfo ExInfo = ExchangeListItemInfo.newInstance(rs);
					if(ExInfo == null)
						continue;

					Exchange.put(ExInfo.get_main_item_id(), ExInfo);
				}
			}
		});
		_exchange_item = Exchange;
	}
	
	public boolean getExchangeItemInfo(L1PcInstance pc, ItemInstance item) {
		ExchangeListItemInfo info = _exchange_item.get(item.getItemId());
		if (info != null) {
			String[] meterial_item_id = (String[])MJArrangeParser.parsing(info.get_meterial_item_id(), ",", MJArrangeParseeFactory.createStringArrange()).result();
			String[] meterial_item_count = (String[])MJArrangeParser.parsing(info.get_meterial_item_count(), ",", MJArrangeParseeFactory.createStringArrange()).result();
			String[] meterial_item_enchant = (String[])MJArrangeParser.parsing(info.get_meterial_item_enchant(), ",", MJArrangeParseeFactory.createStringArrange()).result();
			boolean success = true;
			if (meterial_item_id.length == meterial_item_count.length) {
				for(int i=0; i<meterial_item_id.length; ++i) {
					int itemid = Integer.parseInt(meterial_item_id[i]);
					int count = Integer.parseInt(meterial_item_count[i]);
					int enchant = Integer.parseInt(meterial_item_enchant[i]);
					
					int cnt = 0;
					
					for (ItemInstance items : pc.getInventory().getItems()) {
						if (items.getItemId() == itemid && items.getEnchantLevel() == enchant) {
							cnt += items.getCount();
						}
					}
					
					if (cnt < count) {
						success = false;
						continue;
					}
				}
				
				if (success) {
					for(int i=0; i<meterial_item_id.length; ++i) {
						int itemid = Integer.parseInt(meterial_item_id[i]);
						int count = Integer.parseInt(meterial_item_count[i]);
						int enchant = Integer.parseInt(meterial_item_enchant[i]);
						boolean check_remove = false;
						for (ItemInstance items : pc.getInventory().getItems()) {
							if (!check_remove) {
								if (items.getItemId() == itemid && items.getEnchantLevel() == enchant) {
									pc.getInventory().consumeItem(items, count);
									check_remove = true;
								}
							}
						}
					}
					
					if (LORnd.isWinning(1000000, info.calc_probability(pc))) {
						ItemInstance sss = pc.getInventory().storeItem(info.get_success_item_id(), info.get_success_item_count(), info.get_success_item_enchant());

						//inv.storeItem(info.get_success_item_id(), info.get_success_item_count(), info.get_success_item_enchant());

						
				//		pc.sendPackets(String.format("%s의 모습이 찬란히 빛나며 변환에 성공하여 %s를 제작 하였습니다.", info.get_main_item_name(), sss.getLogName()));
					
						if(info.is_broadcast()){  
							String broadcast_message = String.format("어느 아덴 용사 님께서 %s", sss.getLogName() +" 제작에 성공하였습니다.");
							L1World.getInstance().broadcastPacketToAll(new ServerBasePacket[]{
									new S_PacketBox(S_PacketBox.GREEN_MESSAGE, broadcast_message), new S_SystemMessage(broadcast_message),
							});
							 
						} 
					} else {
						pc.sendPackets(String.format("%s이(가)제작에 실패하여 증발 하였습니다.", info.get_main_item_name()));
					}
				} else {
					for(int i=0; i<meterial_item_id.length; ++i) {
						int itemid = Integer.parseInt(meterial_item_id[i]);
						int count = Integer.parseInt(meterial_item_count[i]);
						int enchant = Integer.parseInt(meterial_item_enchant[i]);
						L1Item temp = ItemTable.getInstance().getTemplate(itemid);
						if (temp != null) {
							String ment = String.format("재료:%s(%d)개가 필요합니다.", temp.getName(), count);
							if (enchant > 0)
								ment = String.format("재료:+%d %s(%d)개가 필요합니다.", enchant, temp.getName(), count);
							pc.sendPackets(ment);
						}
					}
				}
			}
			return true;
		}
		return false;
	}
}
